package org.wikibrain.atlasify;

import javax.ws.rs.*;
import javax.ws.rs.core.Feature;
import javax.ws.rs.core.Response;

import au.com.bytecode.opencsv.CSVReader;
import com.googlecode.concurrentlinkedhashmap.ConcurrentLinkedHashMap;
import com.googlecode.concurrentlinkedhashmap.Weighers;
import com.vividsolutions.jts.geom.Geometry;

import jersey.repackaged.com.google.common.cache.Weigher;
import org.apache.commons.collections15.map.LRUMap;
import com.vividsolutions.jts.geom.Point;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.lucene.search.Query;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.FeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.geojson.geom.GeometryJSON;
import org.json.JSONArray;
import org.json.JSONObject;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.WikiBrainException;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.dao.LocalLinkDao;
import org.wikibrain.core.dao.UniversalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.model.Title;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.core.lang.LocalId;
import org.wikibrain.lucene.QueryBuilder;
import org.wikibrain.lucene.WikiBrainScoreDoc;
import org.wikibrain.phrases.PhraseAnalyzer;

import org.wikibrain.sr.Explanation;
import org.wikibrain.sr.SRMetric;
import org.wikibrain.sr.disambig.Disambiguator;
import org.wikibrain.wikidata.WikidataDao;
import org.wikibrain.spatial.dao.SpatialDataDao;
import org.wikibrain.atlasify.LocalPageAutocompleteSqlDao;
import org.wikibrain.atlasify.AtlasifyLogger;
import org.wikibrain.lucene.LuceneSearcher;


import java.io.*;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;

import java.net.URLConnection;
import java.util.*;

import java.net.URL;

import org.apache.commons.codec.binary.Base64;
import sun.security.provider.certpath.Builder;

import java.util.*;

import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RunnableFuture;

// The Java class will be hosted at the URI path "/helloworld"
@Path("/wikibrain")
public class AtlasifyResource {

    private static class AtlasifyQuery{
        private String keyword;
        private String refSystem;
        private String[] featureIdList;
        private String[] featureNameList;

        public AtlasifyQuery(){

        }

        public AtlasifyQuery(String keyword, String refSystem, String[] featureIdList, String[] featureNameList){
            this.keyword = keyword;
            this.refSystem = refSystem;
            this.featureIdList = featureIdList;
            this.featureNameList = featureNameList;
        }

        public AtlasifyQuery(String keyword, String refSystem, List<String> featureIdList, List<String> featureNameList){
            this.keyword = keyword;
            this.refSystem = refSystem;
            this.featureIdList = featureIdList.toArray(new String[featureIdList.size()]);
            this.featureNameList = featureNameList.toArray(new String[featureNameList.size()]);
        }

        public String getKeyword(){
            return keyword;
        }

        public String getRefSystem(){
            return refSystem;
        }

        public String[] getFeatureIdList(){
            return featureIdList;
        }

        public String[] getFeatureNameList(){
            return featureNameList;
        }

    }

    private static SRMetric sr = null;
    private static PhraseAnalyzer pa = null;
    public static LocalPageDao lpDao = null;
    public static Language lang = Language.getByLangCode("en");
    private static LocalPageAutocompleteSqlDao lpaDao = null;
    public static LocalLinkDao llDao = null;
    private static WikidataMetric wdMetric = null;
    private static DBpeidaMetric dbMetric = null;
    private static WikidataDao wdDao = null;
    public static UniversalPageDao upDao = null;
    private static POIGenerator poiGenerator = null;
    private static AtlasifyLogger atlasifyLogger;
    private static boolean wikibrainLoadingInProcess = false;
    private static boolean loadWikibrainSR = false;
    public static Set<Integer> GADM01Concepts = new HashSet<Integer>();
    private static RealMatrix gameCorrelationMatrix;
    private static List<String> gameTitles;
    private static LuceneSearcher luceneSearcher;

    // A cache which will keep the last 5000 autocomplete requests
    private static int maximumAutocompleteCacheSize = 5000;
    private static ConcurrentLinkedHashMap<String, Map<String, String>> autocompleteCache;
    // An SR cache which will keep the last 50000 request string pairs
    private static int maximumSRCacheSize = 50000;
    private static ConcurrentLinkedHashMap<String, String> srCache;
    // An explanations cache
    private static int maximumExplanationsSize = 1000;
    private static ConcurrentLinkedHashMap<String, JSONArray> northwesternExplanationsCache;
    private static ConcurrentLinkedHashMap<String, List<Explanation>> dbpeidaExplanationsCache;
    private static String pairSeperator = "%";
    //intialize all the DAOs we'll need to use
    private static void wikibrainSRinit(){

        try {
            wikibrainLoadingInProcess = true;
            System.out.println("START LOADING WIKIBRAIN");
            Env env = new EnvBuilder().build();
            Configurator conf = env.getConfigurator();
            lpDao = conf.get(LocalPageDao.class);
            System.out.println("FINISHED LOADING LOCALPAGE DAO");
            lpaDao = conf.get(LocalPageAutocompleteSqlDao.class);
            llDao = conf.get(LocalLinkDao.class);
            System.out.println("FINISHED LOADING LOCALLINK DAO");
            // Autocomplete cache creation
            autocompleteCache = new ConcurrentLinkedHashMap.Builder<String, Map<String, String>>()
                    .maximumWeightedCapacity(maximumAutocompleteCacheSize)
                    .initialCapacity(maximumAutocompleteCacheSize/10)
                    .build();
            // SR cache creation
            srCache = new ConcurrentLinkedHashMap.Builder<String, String>()
                    .maximumWeightedCapacity(maximumSRCacheSize)
                    .initialCapacity(maximumSRCacheSize / 10)
                    .build();
            // Explanations cache creation
            northwesternExplanationsCache = new ConcurrentLinkedHashMap.Builder<String, JSONArray>()
                    .maximumWeightedCapacity(maximumExplanationsSize)
                    .initialCapacity(maximumExplanationsSize/10)
                    .build();
            dbpeidaExplanationsCache = new ConcurrentLinkedHashMap.Builder<String, List<Explanation>>()
                    .maximumWeightedCapacity(maximumExplanationsSize)
                    .initialCapacity(maximumExplanationsSize/10)
                    .build();
            System.out.println("FINISHED LOADING CACHES");

            if(loadWikibrainSR){
                sr = conf.get(SRMetric.class, "ensemble", "language", lang.getLangCode());
                System.out.println("FINISHED LOADING SR");
            }
            if(loadWikibrainSR == false && useNorthWesternAPI == false){
                throw new Exception("Need to load Wikibrain SR if not using NU API!");
            }

            luceneSearcher = conf.get(LuceneSearcher.class);

            wdDao = conf.get(WikidataDao.class);
            System.out.println("FINISHED LOADING WIKIDATA DAO");
            HashMap parameters = new HashMap();
            parameters.put("language", lang.getLangCode());
            Disambiguator dis = conf.get(Disambiguator.class, "similarity", parameters);
            wdMetric = new WikidataMetric("wikidata", lang, lpDao, dis, wdDao);
            System.out.println("FINISHED LOADING WIKIDATA METRIC");
            dbMetric = new DBpeidaMetric("dbpedia", lang, lpDao, dis);
            System.out.println("FINISHED LOADING DBPEDIA METRIC");

            atlasifyLogger = new AtlasifyLogger("./log/AtlasifyLogin.csv", "./log/AtlasifyQuery.csv");
            System.out.println("FINISHED LOADING LOGGER");
            pa = conf.get(PhraseAnalyzer.class, "anchortext");
            System.out.println("FINISHED LOADING PHRASE ANALYZER");

            String gameTitlesFile = "game_page_titles.csv";
            CSVReader fileReader = new CSVReader(new FileReader(new File(gameTitlesFile)), ',');
            gameTitles = new ArrayList<String>();
            for (String[] array : fileReader.readAll()) {
                String s = array[0];
                gameTitles.add(s);
            }
            fileReader.close();
            String gameCorrelationFile = "game_corr_table.csv";
            fileReader = new CSVReader(new FileReader(new File((gameCorrelationFile))), ',');
            gameCorrelationMatrix = new BlockRealMatrix(gameTitles.size(), gameTitles.size());
            int i = 0;
            for (String[] array : fileReader.readAll()) {
                int j = 0;
                for (String s : array) {
                    gameCorrelationMatrix.setEntry(i, j, Double.parseDouble(s));
                    j++;
                }
                i++;
            }
            fileReader.close();

            upDao = conf.get(UniversalPageDao.class);
            System.out.println("FINISHED LOADING UNIVERSALPAGE DAO");
            System.out.println("STARTED LOADING POI GENERATOR");
            poiGenerator = new POIGenerator(conf);
            System.out.println("FINISHED LOADING POI GENERATOR");


            //construct black list for GADM0/1 in POI Generation
            CSVReader reader = new CSVReader(new FileReader("gadm_matched.csv"), ',');
            List<String[]> gadmList = reader.readAll();
            for(String[] gadmItem : gadmList){
                if(Integer.parseInt(gadmItem[2]) < 2)
                    GADM01Concepts.add(Integer.parseInt(gadmItem[0]));
            }



            System.out.println("FINISHED LOADING WIKIBRAIN");
            wikibrainLoadingInProcess = false;


            //sr = conf.get(
            //        SRMetric.class, "ensemble",
            //        "language", "simple");


        } catch (Exception e) {
            System.out.println("Exception when initializing WikiBrain: "+e.getMessage());
            wikibrainLoadingInProcess = false;
        }

    }

    /**
     *
     * @param title the title of wikipedia page to resolve
     * @return the localID of the result article
     * @throws Exception
     */
    public static LocalId wikibrainPhaseResolution(String title) throws Exception {
        /*Language language = lang;
        LinkedHashMap<LocalId, Float> resolution = pa.resolve(language, title, 1);
        for (LocalId p : resolution.keySet()) {
            return p;
        }

        throw new Exception("failed to resolve");

        // return new LocalId(lang, lpDao.getByTitle(lang, title).getLocalId());*/
        /*Language language = lang;
        LinkedHashMap<LocalId, Float> resolution = pa.resolve(language, title, 1);
        for (LocalId p : resolution.keySet()) {
            return p;
        }
        throw new Exception("failed to resolve"); */

        return new LocalId(lang, lpDao.getByTitle(lang, title).getLocalId());
    }

    /**
     *
     * @param id the local id of the article to query
     * @param topN the number of top results needed (-1 for returning all the results)
     * @return a map contains <localID, SRValue>, each entry represents the sr value for a pair of articles
     * @throws Exception
     */
    public static Map<LocalId, Double> accessNorthwesternAPI(LocalId id, Integer topN, boolean spatialOnly) throws Exception {
        Language language = lang;
        String url = "";
        if(topN == -1 && spatialOnly){
            url = "http://downey-n2.cs.northwestern.edu:8080/wikisr/sr/sID/" + id.getId() + "/langID/" + language.getId() + "/spatial/true";
        }
        else if (topN == -1){
            url = "http://downey-n2.cs.northwestern.edu:8080/wikisr/sr/sID/" + id.getId() + "/langID/" + language.getId();
        }
        else {
            url = "http://downey-n2.cs.northwestern.edu:8080/wikisr/sr/sID/" + id.getId() + "/langID/" + language.getId()+ "/top/" + topN.toString();
        }
        System.out.println("NU QUERY " + url);

        URLConnection urlConnection = new URL(url).openConnection();
        urlConnection.setConnectTimeout(NorthwesternTimeout);
        urlConnection.setReadTimeout(NorthwesternTimeout);

        InputStream inputStream = urlConnection.getInputStream();

        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        StringBuilder stringBuilder = new StringBuilder();
        int currentChar;
        while ((currentChar = bufferedReader.read()) != -1) {
            stringBuilder.append((char) currentChar);
        }

        JSONObject jsonObject = new JSONObject(stringBuilder.toString());
        JSONArray jsonArray = jsonObject.getJSONArray("result");
        Map<LocalId, Double> result = new HashMap<LocalId, Double>();
        int length = jsonArray.length();

        for (int i = 0; i < length; i++) {
            try{
                JSONObject pageSRPair = jsonArray.getJSONObject(i);
                LocalId page = new LocalId(language, pageSRPair.getInt("wikiPageId"));
                Double sr = new Double(pageSRPair.getDouble("srMeasure"));
                result.put(page, sr);
            }
            catch (Exception e){
                continue;
            }
        }

        return result;
    }

    /**
     * "Hello World" function used to test internet connection
     * @return a http response contains "hello world"
     * @throws Exception
     */
    @GET
    @Path("/helloworld")
    @Produces("text/plain")
    public Response helloWorld() throws Exception{
        return Response.ok("hello world").build();
    }

    // The Java method will process HTTP GET requests
    @GET
    // The Java method will produce content identified by the MIME Media
    // type "text/plain"
    @Path("/SR/keyword={keyword}&feature=[{input}]")
    @Consumes("text/plain")
    @Produces("text/plain")
    public Response getClichedMessage(@PathParam("keyword") String keyword, @PathParam("input") String data) throws  DaoException{
        if(wikibrainLoadingInProcess == true){
            System.out.println("Waiting for Wikibrain Loading");
            return Response.serverError().entity("Wikibrain not ready").build();
        }
        if(lpDao == null){
            wikibrainSRinit();
        }
        String[] features = data.split(",");
        Map<String, String> srMap = new HashMap<String, String>();
        for(int i = 0; i < features.length; i++){
            srMap.put(features[i].toString(), getColorStringFromSR(sr.similarity(keyword, features[i].toString(), false).getScore()));
        }
        return Response.ok(new JSONObject(srMap).toString()).header("Access-Control-Allow-Origin", "*").build();
    }
    /*
        @POST
        @Path("/send")
        @Produces("text/plain")
        public Response nullResponse () {
            return Response.ok("success").build();
        }
    */


    static private boolean useNorthWesternAPI  = true;
    static private int     NorthwesternTimeout = 100000; // in milliseconds
    // The number of explanations to preemptively download and cache
    static private int     numberOfExplanationsToLoad = 10;

    /**
     * return a <name, color> map to the client
     * @param query AtlasifyQuery sent from the client
     * @return
     */
    @POST
    @Path("/send")
    @Consumes("application/json")
    @Produces("text/plain")

    public Response consumeJSON (AtlasifyQuery query) {
        if(wikibrainLoadingInProcess == true){
            System.out.println("Waiting for Wikibrain Loading");
            return Response.serverError().entity("Wikibrain not ready").build();
        }
        if(lpDao == null ){
            wikibrainSRinit();
        }

        List<String> featureIdList = new ArrayList<String>(Arrays.asList(query.getFeatureIdList()));
        List<String> featureNameList = new ArrayList<String>(Arrays.asList(query.getFeatureNameList()));
        String keyword = query.getKeyword();
        Map<String, String> srMap = new HashMap<String, String>();
        System.out.println("Receive featureId size of " + featureIdList.size() + " and featureName size of " + featureNameList.size());

        // Get values out of the cache
        for (int i = 0; i < featureNameList.size(); i++) {
            String pair = keyword + pairSeperator + featureNameList.get(i);
            if (srCache.containsKey(pair)) {
                srMap.put(featureNameList.get(i), srCache.get(pair));
                featureNameList.remove(i);
                featureIdList.remove(i);
                i--;
            }
        }

        boolean gotUsefulDataToCache = false;
        if (featureIdList.size() > 0) {
            if (useNorthWesternAPI) {
                LocalId queryID = new LocalId(lang, 0);
                try {
                    queryID = wikibrainPhaseResolution(query.getKeyword());
                } catch (Exception e) {
                    System.out.println("Failed to resolve keyword " + query.getKeyword());
                    return Response.ok(new JSONObject(srMap).toString()).build();
                }
                // LocalId queryID = new LocalId(Language.EN, 19908980);
                try {
                    Map<LocalId, Double> srValues = new HashMap<LocalId, Double>();
                    if(query.refSystem.contentEquals("state") || query.refSystem.contentEquals("country")){
                        srValues = accessNorthwesternAPI(queryID, -1, true);
                    }
                    else{
                        srValues = accessNorthwesternAPI(queryID, -1, false);
                    }

                    for (int i = 0; i < featureIdList.size(); i++) {
                        LocalId featureID = new LocalId(lang, 0);

                        try {
                            featureID = new LocalId(lang, Integer.parseInt(featureIdList.get(i)));
                        } catch (Exception e) {
                            System.out.println("Failed to resolve " + featureNameList.get(i));
                            continue;
                            //do nothing
                        }

                        try {
                            String color = getColorStringFromSR(srValues.get(featureID));
                            srMap.put(featureNameList.get(i).toString(), color);
                            System.out.println("SR Between " + lpDao.getById(queryID).getTitle().getCanonicalTitle() + " and " + lpDao.getById(featureID).getTitle().getCanonicalTitle() + " is " + srValues.get(featureID));
                            gotUsefulDataToCache = true;
                        } catch (Exception e) {
                            //put white for anything not present in the SR map
                            try {
                                System.out.println("NO SR Between " + lpDao.getById(queryID).getTitle().getCanonicalTitle() + " and " + lpDao.getById(featureID).getTitle().getCanonicalTitle());
                            } catch (Exception e1) {
                                System.out.println("Failed to get SR");
                            }
                            srMap.put(featureNameList.get(i).toString(), "#ffffff");
                            continue;
                            //do nothing
                        }
                    }
                    /*
                    // Find the top sr items to load
                    List<String> topPages = new ArrayList<String>();//(featureIdList);
                    for (String id : featureNameList) {
                        if (!srMap.containsKey(id) || compareSRColorStrings(srMap.get(id), "#ffffff") >= 0) {
                            continue;
                        }

                        // Find the new location inside the topPages array, searching from the bottom
                        int index = topPages.size();
                        for (int i = topPages.size() - 1; i >= 0; i--) {
                            String pageToCompare = topPages.get(i);
                            if (compareSRColorStrings(srMap.get(id), srMap.get(pageToCompare)) < 0) {
                                index = i;
                            }
                        }

                        if (index < numberOfExplanationsToLoad) {
                            topPages.add(index, id);
                        }
                        // Make sure the size is bounded
                        while (topPages.size() > numberOfExplanationsToLoad) {
                            topPages.remove(topPages.size() - 1);
                        }
                    }

                    class BackgroundExplanationLoader implements Runnable {
                        public LocalId featureID;
                        public String keyword;
                        public BackgroundExplanationLoader(String keyword, LocalId featureID) {
                            this.keyword = keyword;
                            this.featureID = featureID;
                        }

                        public void run() {
                            try {
                                String title = lpDao.getById(featureID).getTitle().getCanonicalTitle();
                                System.out.println("BACKGROUND loading explanations between " + keyword + " and " + title);
                                handleExplanation(keyword, title);
                            } catch (Exception e) {
                                System.out.println("ERROR: Unable to process explanation on background thread");
                                e.printStackTrace();
                            }
                        }
                    };

                    ExecutorService executor = Executors.newCachedThreadPool();
                    for (int i = 0; i < topPages.size(); i++) {
                        LocalId featureID = new LocalId(lang, lpDao.getIdByTitle(new Title(topPages.get(i), lang)));
                        BackgroundExplanationLoader loader = new BackgroundExplanationLoader(keyword, featureID);
                        executor.submit(loader);
                    }
                    */
                } catch (Exception e) {
                    System.out.println("Error when connecting to Northwestern Server ");
                    e.printStackTrace();

                    // Switch to wikibrain based SR when NU API fails
                    if (loadWikibrainSR) {
                        System.out.println("Defaulting to Wikibrain SR");
                        srMap = wikibrainSR(query, (String[]) featureNameList.toArray());
                    }
                }
            } else {
                srMap = wikibrainSR(query, (String[]) featureNameList.toArray());
            }

            // Cache all of the retrieved results
            if (gotUsefulDataToCache) {
                for (int i = 0; i < featureNameList.size(); i++) {
                    String feature = featureNameList.get(i);
                    srCache.put(keyword + pairSeperator + feature, srMap.get(feature));
                }
            }
        }

        return Response.ok(new JSONObject(srMap).toString()).build();
    }
    int compareSRColorStrings(String s1, String s2) {
        if (s1.contains("#")) {
            s1 = s1.substring(s1.lastIndexOf('#') + 1);
        }
        if (s2.contains("#")) {
            s2 = s2.substring(s2.lastIndexOf('#') + 1);
        }

        double brightness1 = 0.2126*Integer.valueOf(s1.substring(0, 2), 16) + 0.7152*Integer.valueOf(s1.substring(2, 4), 16) + 0.0722*Integer.valueOf(s1.substring(4), 16);
        double brightness2 = 0.2126*Integer.valueOf(s2.substring(0, 2), 16) + 0.7152*Integer.valueOf(s2.substring(2, 4), 16) + 0.0722*Integer.valueOf(s2.substring(4), 16);
        return ((Double)brightness1).compareTo(brightness2);
    }

    /**
     * return a <name, color> map to the client with WikiBrain SR
     * @param query
     * @param featureNameList
     * @return
     */
    private Map<String, String> wikibrainSR(AtlasifyQuery query, String[] featureNameList) {
        Map<String, String> srMap = new HashMap<String, String>();
        for (int i = 0; i < featureNameList.length; i++) {
            String color = "#ffffff";
            try {

                color = getColorStringFromSR(sr.similarity(query.getKeyword(), featureNameList[i].toString(), false).getScore());
            } catch (Exception e) {
                //do nothing
            }

            srMap.put(featureNameList[i].toString(), color);
        }

        return srMap;
    }

    /**
     *  Get the corresponding color code for a given SR value
     * @param SR
     * @return
     */
    private String getColorStringFromSR(double SR){

        if(SR < 0.3651)
            return "#ffffff";
        if(SR < 0.4500)
            return "#e5f5f9";
        if(SR < 0.5072)
            return "#ccece6";
        if(SR < 0.5670)
            return "#99d8c9";
        if(SR < 0.6137)
            return "#66c2a4";
        if(SR < 0.7000)
            return "#41ae76";
        if(SR < 0.7942)
            return "#238b45";
        return "#005824";
    }

    @POST
    @Path("logLogin")
    @Consumes("application/json")
    @Produces("text/plain")
    public Response processLogLogin(AtlasifyLogger.logLogin query) throws Exception{

        atlasifyLogger.LoginLogger(query, "");
        System.out.println("LOGIN LOGGED " + query.toString());
        return Response.ok("received").build();

    }

    @POST
    @Path("logQuery")
    @Consumes("application/json")
    @Produces("text/plain")
    public Response processLogQuery(AtlasifyLogger.logQuery query) throws Exception{

        atlasifyLogger.QueryLogger(query, "");
        System.out.println("QUERY LOGGED " + query.toString());
        return Response.ok("received").build();
    }

    @POST
    @Path("/autocomplete")
    @Consumes("application/json")
    @Produces("text/plain")

    public Response autocompleteSearch(AtlasifyQuery query) throws Exception {
        if(wikibrainLoadingInProcess == true){
            System.out.println("Waiting for Wikibrain Loading");
            return Response.serverError().entity("Wikibrain not ready").build();
        }
        if (lpDao == null) {
            wikibrainSRinit();
        }

        Language language = lang;
        System.out.println("Received Auto Complete Query " + query.getKeyword());
        Map<String, String> autocompleteMap;

        if ((autocompleteMap = autocompleteCache.get(query.getKeyword())) != null) {
            System.out.println("Get Auto Complete Result from cache " + new JSONObject(autocompleteMap).toString());
            return Response.ok(new JSONObject(autocompleteMap).toString()).build();
        }

        autocompleteMap = new HashMap<String, String>();
        try {
            int i = 0;
            /* Phrase Analyzer */
            /*LinkedHashMap<LocalId, Float> resolution = pa.resolve(language, query.getKeyword(), 100);
            for (LocalId p : resolution.keySet()) {
                org.wikibrain.core.model.LocalPage page = lpDao.getById(p);
                autocompleteMap.put(i + "", page.getTitle().getCanonicalTitle());
                i++;
            }*/

            /* Page Titles that being/contain search term */
            /*Title title = new Title(query.getKeyword(), language);
            List<LocalPage> similarPages = lpaDao.getBySimilarTitle(title, NameSpace.ARTICLE, llDao);

            for (LocalPage p : similarPages) {
                autocompleteMap.put(i + "", p.getTitle().getCanonicalTitle());
                i++;
            } */

            /* Bing */
            String bingAccountKey = "Y+KqEsFSCzEzNB85dTXJXnWc7U4cSUduZsUJ3pKrQfs";
            byte[] bingAccountKeyBytes = Base64.encodeBase64((bingAccountKey + ":" + bingAccountKey).getBytes());
            String bingAccountKeyEncoded = new String(bingAccountKeyBytes);

            String bingQuery = query.getKeyword();
            URL bingQueryurl = new URL("https://api.datamarket.azure.com/Bing/SearchWeb/v1/Web?Query=%27"+java.net.URLEncoder.encode(bingQuery, "UTF-8")+"%20site%3Aen.wikipedia.org%27&$top=50&$format=json");

            HttpURLConnection connection = (HttpURLConnection)bingQueryurl.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Authorization", "Basic " + bingAccountKeyEncoded);
            connection.setRequestProperty("Accept", "application/json");
            BufferedReader br = new BufferedReader(new InputStreamReader((connection.getInputStream())));

            String output;
            StringBuilder sb = new StringBuilder();
            while ((output = br.readLine()) != null) {
                sb.append(output);
            }

            JSONObject bingResponse = new JSONObject(sb.toString());
            bingResponse = bingResponse.getJSONObject("d");
            JSONArray bingResponses = bingResponse.getJSONArray("results");
            JSONObject response;

            for (int j = 0; j < bingResponses.length() && i < 10; j++) {
                response = bingResponses.getJSONObject(j);
                URL url = new URL(response.getString("Url"));
                String path = url.getPath();
                String title = path.substring(path.lastIndexOf('/') + 1).replace('_', ' ');
                LocalPage page = new LocalPage(language, 0, "");
                try {
                    for (LocalId p : pa.resolve(language, title, 1).keySet()) {
                        page = lpDao.getById(p);
                    }
                    if (page != null && !autocompleteMap.values().contains(page.getTitle().getCanonicalTitle())) {
                        autocompleteMap.put(i + "", page.getTitle().getCanonicalTitle());
                        i++;
                    }
                } catch (Exception e) {
                    // There was an error, lets keep keep going
                }
            }

            /* Lucene */
            /*
            QueryBuilder luceneQuery = new QueryBuilder(luceneSearcher, lang)
                    .setPhraseQuery(query.getKeyword());
            WikiBrainScoreDoc[] scores = luceneQuery.search();

            for (int j = 0; j < scores.length && i < 10; j++) {
                try {
                    WikiBrainScoreDoc score = scores[i];
                    LocalPage page = lpDao.getById(lang, score.wpId);
                    if (page != null && !autocompleteMap.values().contains(page.getTitle().getCanonicalTitle())) {
                        autocompleteMap.put(i + "", page.getTitle().getCanonicalTitle());
                        i++;
                    }
                } catch (Exception e) {
                    // There was an error, lets keep keep going
                }
            }
            */
        } catch (Exception e) {
            autocompleteMap = new HashMap<String, String>();
        }

        // Cache the autocomplete
        if (autocompleteMap.size() > 0) {
            autocompleteCache.put(query.getKeyword(), autocompleteMap);
        }

        System.out.println("Get Auto Complete Result" + new JSONObject(autocompleteMap).toString());
        return Response.ok(new JSONObject(autocompleteMap).toString()).build();
    }
    public String getExplanation(String keyword, String feature) throws Exception{
        if (lpDao == null && wikibrainLoadingInProcess == false) {
            wikibrainSRinit();
        }

        JSONArray explanations = new JSONArray();
        JSONArray explanationSection = new JSONArray();

        System.out.println("Received query for explanation between " + keyword + " and " + feature);
        String keywordTitle;
        String featureTitle;
        int keywordPageId = wikibrainPhaseResolution(keyword).getId();
        int featurePageId = wikibrainPhaseResolution(feature).getId();
        try{

            try{
                keywordTitle = lpDao.getById(wikibrainPhaseResolution(keyword)).getTitle().getCanonicalTitle().replace(" ", "_");
                featureTitle = lpDao.getById(wikibrainPhaseResolution(feature)).getTitle().getCanonicalTitle().replace(" ", "_");
            }
            catch (Exception e){
                e.printStackTrace();
                throw  new Exception("failed to resolve titles for " + keyword + " and " + feature);
            }
            // Get Wikidata Explanations using the disambiguator
            try{
                for (Explanation exp : wdMetric.similarity(keywordPageId, featurePageId, true).getExplanations()) {
                    try {
                        String explanationString = String.format(exp.getFormat(), exp.getInformation().toArray());
                        if (containsExplanation(explanationSection, explanationString)) {
                            continue;
                        }

                        JSONObject jsonExplanation = new JSONObject();
                        jsonExplanation.put("explanation", explanationString);

                        JSONObject data = new JSONObject();
                        data.put("algorithm", "wikidata");
                        data.put("page-finder", "disambiguator");
                        data.put("keyword", keyword);
                        data.put("feature", feature);
                        jsonExplanation.put("data", data);

                        explanationSection.put(explanationSection.length(), jsonExplanation);
                    } catch (Exception e) {
                        System.out.println("ERROR: failed to get Wikidata Explanations using the disambiguator for "+ keyword + " and " + feature + "\n");
                        e.printStackTrace();
                    }
                }
            }
            catch (Exception e){
                System.out.println("ERROR: failed to get Wikidata Explanations using the disambiguator for "+ keyword + " and " + feature + "\n");
                e.printStackTrace();
            }

            // Get Wikidata Explanations using the LocalPageDao
            try{
                int keywordID = lpDao.getIdByTitle(new Title(keyword, Language.SIMPLE));
                int featureID = lpDao.getIdByTitle(new Title(feature, Language.SIMPLE));
                for (Explanation exp : wdMetric.similarity(keywordID, featureID, true).getExplanations()) {
                    try {
                        String explanationString = String.format(exp.getFormat(), exp.getInformation().toArray());
                        if (containsExplanation(explanationSection, explanationString)) {
                            continue;
                        }

                        JSONObject jsonExplanation = new JSONObject();
                        jsonExplanation.put("explanation", explanationString);

                        JSONObject data = new JSONObject();
                        data.put("algorithm", "wikidata");
                        data.put("page-finder", "local-page-dao");
                        data.put("keyword", keyword);
                        data.put("feature", feature);
                        jsonExplanation.put("data", data);

                        explanationSection.put(explanationSection.length(), jsonExplanation);
                    } catch (Exception e) {
                        System.out.println("ERROR: failed to get Wikidata Explanations using the localPageDao for "+ keyword + " and " + feature + "\n");
                        e.printStackTrace();
                    }
                }
            }
            catch (Exception e){
                System.out.println("ERROR: failed to get Wikidata Explanations using the localPageDao for "+ keyword + " and " + feature + "\n");
                e.printStackTrace();
            }

            // Get DBPedia Explanations using the disambiguator
            try{
                List<Explanation> explanationList;
                String pair = keywordTitle + pairSeperator + featureTitle;
                if (dbpeidaExplanationsCache.containsKey(pair)) {
                    explanationList = dbpeidaExplanationsCache.get(pair);
                } else {
                    explanationList = dbMetric.similarity(keywordPageId, featurePageId, true).getExplanations();
                    // Cache them
                    if (explanationList.size() > 0) {
                        dbpeidaExplanationsCache.put(pair, explanationList);
                    }
                }

                for (Explanation exp : explanationList) {
                    try {
                        String explanationString = String.format(exp.getFormat(), exp.getInformation().toArray());
                        if (containsExplanation(explanationSection, explanationString)) {
                            continue;
                        }

                        JSONObject jsonExplanation = new JSONObject();
                        jsonExplanation.put("explanation", explanationString);

                        JSONObject data = new JSONObject();
                        data.put("algorithm", "dbpedia");
                        data.put("page-finder", "disambiguator");
                        data.put("keyword", keyword);
                        data.put("feature", feature);
                        jsonExplanation.put("data", data);

                        explanationSection.put(explanationSection.length(), jsonExplanation);
                    } catch (Exception e) {
                        System.out.println("ERROR: failed to get DBPedia Explanations using the disambiguator for "+ keyword + " and " + feature + "\n");
                        e.printStackTrace();
                    }
                }
            }
            catch (Exception e){
                System.out.println("ERROR: failed to get DBPedia Explanations using the disambiguator for "+ keyword + " and " + feature + "\n");
                e.printStackTrace();
            }

            shuffleJSONArray(explanationSection);
            addElementesToArray(explanations, explanationSection);
            explanationSection = new JSONArray();

            // Check to see if the northwestern explanations are cached
            JSONArray northwesternExplanationList;
            String northwesternPair = keywordTitle + pairSeperator + featureTitle;
            if (northwesternExplanationsCache.containsKey(northwesternPair)) {
                northwesternExplanationList = northwesternExplanationsCache.get(northwesternPair);
            } else {
                String url = "http://downey-n1.cs.northwestern.edu:3030/api?concept1=" + keywordTitle + "&concept2=" + featureTitle;
                StringBuilder stringBuilder = new StringBuilder();
                try {
                    URLConnection urlConnection = new URL(url).openConnection();
                    urlConnection.setConnectTimeout(NorthwesternTimeout);
                    urlConnection.setReadTimeout(NorthwesternTimeout);

                    InputStream inputStream = urlConnection.getInputStream();

                    BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

                    int currentChar;
                    while ((currentChar = bufferedReader.read()) != -1) {
                        stringBuilder.append((char) currentChar);
                    }
                    //System.out.println("GOT REPLY\n" + stringBuilder.toString());
                } catch (Exception e) {
                    System.out.println("ERROR: failed to get NU Explanation for " + keyword + " and " + feature + "\n");
                    e.printStackTrace();
                }

                northwesternExplanationList = new JSONArray(stringBuilder.toString());

                // Cache the explanations
                if (northwesternExplanationList.length() > 0) {
                    northwesternExplanationsCache.put(northwesternPair, northwesternExplanationList);
                }
            }

            // Process the northwestern json
            try{
                for (int i = 0; i < northwesternExplanationList.length(); i++) {
                    JSONObject northwesternJSON = northwesternExplanationList.getJSONObject(i);
                    JSONArray northwesternExplanations = northwesternJSON.getJSONArray("paragraphs");
                    double srval = northwesternJSON.getDouble("c-score");
                    String title = northwesternJSON.getString("title");

                    for (int j = 0; j < northwesternExplanations.length(); j++) {
                        JSONObject northwesternExplanation = (JSONObject) northwesternExplanations.get(j);

                        String explanationString = northwesternExplanation.getString("curated");
                        // Load the complete content if content is unavailable
                        if (explanationString.equals("")) {
                            explanationString = northwesternExplanation.getString("complete");
                        }
                        // Make sure the string is still valid
                        if (explanationString.equals("") || explanationString.contains("Category:") || containsExplanation(explanationSection, explanationString)) {
                            continue;
                        }

                        JSONArray keywordArray = new JSONArray();
                        JSONArray featureArray = new JSONArray();
                        try {
                            keywordArray = northwesternExplanation.getJSONArray(keywordTitle.replace("_", " "));
                        } catch (Exception e) {
                            try {
                                keywordArray = northwesternExplanation.getJSONArray(keywordTitle);
                            } catch (Exception err) {

                            }
                        }
                        try {
                            featureArray = northwesternExplanation.getJSONArray(featureTitle.replace("_", " "));
                        } catch (Exception e) {
                            try {
                                featureArray = northwesternExplanation.getJSONArray(featureTitle);
                            } catch (Exception err) {

                            }
                        }

                        JSONObject jsonExplanation = new JSONObject();
                        jsonExplanation.put("explanation", explanationString);

                        JSONObject data = new JSONObject();
                        data.put("algorithm", "northwestern");
                        data.put("keyword", keyword);
                        data.put("keyword-data", keywordArray);
                        data.put("feature-data", featureArray);
                        data.put("feature", feature);
                        data.put("srval", srval);
                        data.put("title", title);
                        data.put("header-title", title);
                        jsonExplanation.put("data", data);

                        explanationSection.put(explanationSection.length(), jsonExplanation);
                    }
                }
            }
            catch (Exception e){
                System.out.println("ERROR: failed to process NU Explanation for "+ keyword + " and " + feature + "\n");
                e.printStackTrace();
            }

            addElementesToArray(explanations, explanationSection);
        }
        catch (Exception e){
            System.out.println("Failed to get explanation for " + keyword + " and " + feature);
            // return Response.ok("").header("Access-Control-Allow-Origin", "*").build();
        }

        JSONObject result = new JSONObject();
        result.put("explanations", explanations);
        result.put("keyword", keyword);
        result.put("feature", feature);

        System.out.println("REQUESTED explanation between " + keyword + " and " + feature + "\n\n" + explanations.toString());

        return result.toString();
    }

    /**
     *
     * @param keyword
     * @param feature
     * @return a response contains explanation for the given pair of keyword & feature
     * @throws DaoException
     * @throws MalformedURLException
     * @throws IOException
     * @throws Exception
     */
    @GET
    // The Java method will produce content identified by the MIME Media
    // type "text/plain"
    @Path("/SR/Explanation/keyword={keyword}&feature={feature}")
    @Consumes("text/plain")
    @Produces("text/plain")
    public Response handleExplanation(@PathParam("keyword") String keyword, @PathParam("feature") String feature) throws  DaoException, MalformedURLException, IOException, Exception{
        String result = getExplanation(keyword, feature);
        return Response.ok(result.toString()).build();
    }

    private Random randomSeedGenerator = new Random();
    private void shuffleJSONArray(JSONArray array) {
        int remainingItems = array.length() - 1;
        Random rand = new Random(randomSeedGenerator.nextInt());
        while (remainingItems > 0) {
            int index = rand.nextInt(remainingItems);

            // Swap index and the last item
            Object object = array.getJSONObject(index);
            array.put(index, array.get(remainingItems));
            array.put(remainingItems, object);

            remainingItems--;
        }
    }

    private boolean containsExplanation(JSONArray array, String explanation) {
        for (int i = 0; i < array.length(); i++) {
            if (array.getJSONObject(i).get("explanation").equals(explanation)) {
                return true;
            }
        }

        return false;
    }

    private void addElementesToArray(JSONArray array, JSONArray elementsToAppend) {
        for (int i = 0; i < elementsToAppend.length(); i++) {
            array.put(array.length(), elementsToAppend.get(i));
        }
    }

    // This method is used to progress the explanations information from Atlasify
    @POST
    @Path("/explanationsData")
    @Consumes("application/json")
    @Produces("text/plain")


    public Response processesExplanations(String json) throws DaoException {
        JSONObject explanationsData = new JSONObject(json);
        int id = explanationsData.getInt("id");
        int sessionID = explanationsData.getInt("sessionID");
        String keyword = explanationsData.getString("keyword");
        String feature = explanationsData.getString("feature");

        JSONArray dataArray = explanationsData.getJSONArray("data");
        JSONObject data = new JSONObject();
        data.put("data", dataArray);
        data.put("time", new Date().getTime());
        data.put("id", id);
        data.put("sessionID", sessionID);
        data.put("keyword", keyword);
        data.put("feature", feature);

        // See if log file exists
        String file = "explanation-logs/" + id + ".json";
        File f = new File(file);
        if (f.isFile()) {
            // Append to the file
            try {
                PrintWriter writer = new PrintWriter(new BufferedWriter(
                        new FileWriter(file, true)));
                writer.print("\n");
                writer.print(data.toString());
                writer.close();
            } catch (IOException e) {

            }
        } else {
            // Create it
            try {
                PrintWriter writer = new PrintWriter(new BufferedWriter(
                        new FileWriter(file, true)));
                writer.print(data.toString());
                writer.close();
            } catch (IOException e) {

            }
        }
        return Response.ok("").header("Access-Control-Allow-Origin", "*").build();
    }

    @POST
    // The Java method will produce content identified by the MIME Media
    // type "text/plain"
    @Path("/SR/CrowdSource/keyword={keyword}&feature={feature}&sr={sr}&explanation={explanation}")
    @Consumes("text/plain")
    public void processCrowdSourcedData(@PathParam("keyword") String keyword, @PathParam("feature") String feature, @PathParam("sr") double sr, @PathParam("explanation") String explanation) throws  DaoException, MalformedURLException, IOException, Exception {
        String srLocation = "crowd-source-data/sr.csv";
        String expLocation = "crowd-source-data/explanations.csv";

        System.out.println("RECEIVED crowd sourced data between " + keyword + " and " + feature + "\nSR=" + sr + "\nExplanation=" + explanation);

        if (sr > 0.0) {
            // Valid SR was provided
            PrintWriter writer = new PrintWriter(new BufferedWriter(
                    new FileWriter(srLocation, true)));
            writer.println("\"" + keyword + "\",\"" + feature + "\",\"" + sr + "\"");
            writer.close();
        }

        if (explanation != null && explanation != "") {
            // Valid explanation was provided
            PrintWriter writer = new PrintWriter(new BufferedWriter(
                    new FileWriter(expLocation, true)));
            writer.println("\"" + keyword + "\",\"" + feature + "\",\"" + explanation + "\"");
            writer.close();
        }
    }

        //return the list of all spatial objects in the top 100 most realted articles
    @GET
    @Path("/getpoi/id={keyword}")
    @Consumes("text/plain")
    @Produces("text/plain")

    public Response getPOIs (@PathParam("keyword") String keyword) throws SchemaException, IOException, WikiBrainException, DaoException{
        if(lpDao==null){
            wikibrainSRinit();
        }
        System.out.println("REQUESTED POI "+keyword);
        //System.out.println("GOT JSON RESULT " + jsonResult);
        String result = poiGenerator.getTopNPOI(keyword, this);
        System.out.println("FINISHED GETTING POI FOR "+keyword);
        return Response.ok(result).build();
    }


    private static Random numGenerator = new Random();

    // Returns two random titles which should be used for the game
    // Inputs a string indicating the difficulty {hard, medium, easy}
    @GET
    @Path("/game/diff={difficulty}")
    @Consumes("text/plain")
    @Produces("text/plain")

    public Response generateGameTitles(@PathParam("difficulty") String difficulty) {
        if (gameCorrelationMatrix == null) {
            wikibrainSRinit();
        }

        double minCorrelation = 0.0;
        double maxCorrelation = 1.0;

        if (difficulty.equals("hard")) {
            minCorrelation = 0.5;
            maxCorrelation = 0.75;
        } else if (difficulty.equals("medium")) {
            minCorrelation = 0.25;
            maxCorrelation = 0.5;
        } else {
            minCorrelation = 0.0;
            maxCorrelation = 0.25;
        }

        int index = numGenerator.nextInt(gameTitles.size());
        String articleOne = gameTitles.get(index);

        int nextIndex = -1;

        // Search for a random page
        double[] correlationRow = gameCorrelationMatrix.getRow(index);
        ArrayList<Integer> gameTitleIndicies = new ArrayList<Integer>();
        for (int i = 0; i < gameTitles.size(); i++) {
            gameTitleIndicies.add(i);
        }
        List<Integer> shuffledList = new ArrayList<Integer>(gameTitleIndicies);
        Collections.shuffle(shuffledList);
        for(Integer i : shuffledList) {
            if (i.equals(index)) {
                continue;
            }

            if (minCorrelation <= correlationRow[i] && correlationRow[i] <= maxCorrelation) {
                nextIndex = i;
                break;
            }
        }

        if (nextIndex == -1) {
            // Just get another page, doesn't really matter what it is
            // We hope this doesn't happen
            for (Integer i : shuffledList) {
                if (i.equals(index)) {
                    continue;
                }

                nextIndex = i;
                break;
            }
        }

        String articleTwo = gameTitles.get(nextIndex);

        // Randomly shuffle articles
        if (numGenerator.nextInt(2) == 1) {
            String temp = articleTwo;
            articleOne = articleOne;
            articleTwo = temp;
        }

        JSONObject result = new JSONObject();
        result.put("one", articleOne);
        result.put("two", articleTwo);

        return Response.ok(result.toString()).build();
    }

    // A logging method called by the god mode of Atlasify to check the status of the system
   /* @POST
    @Path("/status")
    @Produces("application/json")

    public Response getLog () throws DaoException{
        ByteArrayOutputStream output = AtlasifyServer.logger;
        String s = output.toString();*/

        /* In order to support multiple god modes running the console
         * output cannot be cleared. This functionality could change
         * in the future if there are performance problems.
         */
        // output.reset();

        /*Map<String, String> result = new HashMap<String, String>();
        result.put("log", s);

        return Response.ok(new JSONObject(result).toString()).header("Access-Control-Allow-Origin", "*").build();
    }
    */
}
