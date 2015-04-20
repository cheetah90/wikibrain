package org.wikibrain.atlasify;

import au.com.bytecode.opencsv.CSVReader;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import org.geotools.data.DataUtilities;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.SchemaException;
import org.geotools.feature.simple.SimpleFeatureBuilder;
import org.geotools.feature.simple.SimpleFeatureTypeBuilder;
import org.geotools.geojson.feature.FeatureJSON;
import org.geotools.referencing.crs.DefaultGeographicCRS;
import org.json.JSONObject;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;
import org.wikibrain.conf.Configuration;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.WikiBrainException;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.dao.UniversalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.lang.LocalId;
import org.wikibrain.core.model.LocalLink;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.spatial.dao.SpatialDataDao;

import javax.ws.rs.core.Response;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * Created by toby on 3/4/15.
 * Generate POI results for Atalsify
 */
public class POIGenerator {
    private static Map<Integer, Geometry> geometryMap = null;
    private static SpatialDataDao sdDao = null;
    private static LocalPageDao lpDao = null;
    private static UniversalPageDao upDao = null;
    public static Set<Integer> GADM01Concepts = new HashSet<Integer>();
    private static final Logger LOG = Logger.getLogger(POIGenerator.class.getName());

    SimpleFeatureBuilder fb;
    FeatureJSON featureJSON = new FeatureJSON();


    POIGenerator(Configurator conf) throws DaoException, ConfigurationException, FileNotFoundException, IOException{
        sdDao = conf.get(SpatialDataDao.class);
        lpDao = conf.get(LocalPageDao.class);
        upDao = conf.get(UniversalPageDao.class);
        geometryMap = sdDao.getAllGeometriesInLayer("wikidata");

        //construct black list for GADM0/1 in POI Generation
        CSVReader reader = new CSVReader(new FileReader("gadm_matched.csv"), ',');
        List<String[]> gadmList = reader.readAll();
        for(String[] gadmItem : gadmList){
            if(Integer.parseInt(gadmItem[2]) < 2)
                GADM01Concepts.add(Integer.parseInt(gadmItem[0]));
        }

        SimpleFeatureTypeBuilder typeBuilder = new SimpleFeatureTypeBuilder();
        typeBuilder.setCRS(DefaultGeographicCRS.WGS84);
        typeBuilder.setName("Atlasify_POI");
        typeBuilder.add("geometry", Point.class);
        typeBuilder.add("name", String.class);
        typeBuilder.add("explanation", String.class);
        SimpleFeatureType featureType= typeBuilder.buildFeatureType();
        //System.out.println("FINISHED BUILDING FEATURETYPE");
        fb = new SimpleFeatureBuilder(featureType);
    }
    //Return POIs that have direct link to the keyword
    public String getDirectedLinkedPOI(String keyword, AtlasifyResource atlasifyResource) throws DaoException, WikiBrainException, SchemaException, IOException{

        LocalId queryID=new LocalId(atlasifyResource.lang,0);
        try{
            queryID=atlasifyResource.wikibrainPhaseResolution(keyword);
        }
        catch(Exception e){
            System.out.println("Failed to resolve keyword "+keyword);
            return "";
        }
        Map<Integer, Point> idGeomMap = new HashMap<Integer, Point>();
        Map<Integer, String> idTitleMap = new HashMap<Integer, String>();
        Map<Integer, String> idExplanationMap = new HashMap<Integer, String>();

        try {
            Iterable<LocalLink> outlinks = atlasifyResource.llDao.getLinks(atlasifyResource.lang, queryID.getId(), true);
            Iterable<LocalLink> inlinks = atlasifyResource.llDao.getLinks(atlasifyResource.lang, queryID.getId(), false);
            Iterator<LocalLink> outlinkIter = outlinks.iterator();
            Iterator<LocalLink> inlinkIter = inlinks.iterator();
            System.out.println("FINISHED GETTING LINKS FOR " + keyword);

            //TODO: optimize efficiency in processing links
            int count = 0;
            while(outlinkIter.hasNext()){
                count ++;
                LocalLink link = outlinkIter.next();
                int localId = link.getDestId();
                try{
                    int univId = upDao.getUnivPageId(Language.EN, localId);
                    if(GADM01Concepts.contains(univId))
                        continue;
                    if(geometryMap.containsKey(univId)){
                        String title = upDao.getById(univId).getBestEnglishTitle(lpDao, true).getCanonicalTitle();
                        idGeomMap.put(univId, (Point)geometryMap.get(univId));
                        idTitleMap.put(univId, title);
                        idExplanationMap.put(univId, title + " : " + keyword + " has a link to " + title);
                    }
                }
                catch (Exception e){
                    //do nothing
                    continue;
                }
            }
            System.out.println("FINISHED PROCESSING OUTLINKS FOR " + String.valueOf(count) + " " + keyword);
            count = 0;
            while(inlinkIter.hasNext()){
                count ++;
                LocalLink link = inlinkIter.next();
                int localId = link.getSourceId();
                try{
                    int univId = upDao.getUnivPageId(Language.EN, localId);
                    if(GADM01Concepts.contains(univId))
                        continue;
                    if(geometryMap.containsKey(univId)){
                        String title = upDao.getById(univId).getBestEnglishTitle(lpDao, true).getCanonicalTitle();
                        idGeomMap.put(univId, (Point)geometryMap.get(univId));
                        idTitleMap.put(univId, title);
                        idExplanationMap.put(univId, title + " : " + keyword + " is linked from " + title);
                    }
                }
                catch (Exception e){
                    //do nothing
                    continue;
                }
            }
            System.out.println("FINISHED PROCESSING INLINKS FOR " + String.valueOf(count) + " " + keyword);

        }


        catch (Exception e){
            e.printStackTrace();
            return "";
        }


        return geoJSONPacking(idGeomMap, idTitleMap, idExplanationMap);

    }



    //Return POIs in TopN algorithm
    public String getTopNPOI(String keyword) throws SchemaException, IOException{
        Map<String, String>srMap=new HashMap<String, String>();
        LocalId queryID=new LocalId(Language.EN,0);
        try{
            queryID=AtlasifyResource.wikibrainPhaseResolution(keyword);
        }
        catch(Exception e){
            System.out.println("Failed to resolve keyword "+keyword);
            return "";
        }
        // LocalId queryID = new LocalId(Language.EN, 19908980);
        Map<Integer, Point> idGeomMap = new HashMap<Integer, Point>();
        Map<Integer, String> idTitleMap = new HashMap<Integer, String>();
        Map<Integer, String> idExplanationMap = new HashMap<Integer, String>();
        try{
            Map<LocalId, Double>srValues=AtlasifyResource.accessNorthwesternAPI(queryID, 400, false);
            LOG.info("Finished getting SR info for POI calculation for keyword " + keyword);
            for(Map.Entry<LocalId, Double>e:srValues.entrySet()){
                try{
                    LocalPage localPage = lpDao.getById(e.getKey());
                    int univId = upDao.getByLocalPage(localPage).getUnivId();
                    if(GADM01Concepts.contains(univId))
                        continue;
                    if(geometryMap.containsKey(univId)){
                        idGeomMap.put(univId, geometryMap.get(univId).getCentroid());
                        idTitleMap.put(univId, localPage.getTitle().getCanonicalTitle());
                        idExplanationMap.put(univId, localPage.getTitle().getCanonicalTitle() + " : " + localPage.getTitle().getCanonicalTitle() + " is a top related article to " + keyword);
                    }
                }
                catch(Exception e1){
                    continue;
                }

            }

        }
        catch(Exception e){
            System.out.println("Error when connecting to Northwestern Server ");
            e.printStackTrace();
            // do nothing

        }
        //System.out.println("START PACKING JSON FOR POI REQUEST " + keyword + " MAP SIZE " + idGeomMap.size() + " " + idTitleMap.size() + " " + idExplanationMap.size());
        String result = "";
        LOG.info("Finished poi data construction for keyword " + keyword);
        try{
            result = geoJSONPacking(idGeomMap, idTitleMap, idExplanationMap);
        }
        catch (Exception e){
            e.printStackTrace();
        }
        return result;

    }

    //Takes in an idGeomMap, an idTitleMap and an idExplanationMap, returns a geoJSON with "geometry, "name" and "explanation"
    private String geoJSONPacking(Map<Integer, Point> idGeomMap, Map<Integer, String> idTitleMap, Map<Integer, String> idExplanationMap) throws IOException, SchemaException{
        List<SimpleFeature> featureList = new ArrayList<SimpleFeature>();
        for(Map.Entry<Integer, Point> entry: idGeomMap.entrySet()){
            fb.set("geometry", entry.getValue());
            fb.set("name", idTitleMap.get(entry.getKey()));
            fb.set("explanation", idExplanationMap.get(entry.getKey()));
            SimpleFeature feature = fb.buildFeature(null);
            featureList.add(feature);
        }
        SimpleFeatureCollection featureCollection = DataUtilities.collection(featureList);
        String jsonResult = featureJSON.toString(featureCollection);
        return jsonResult;
    }

}
