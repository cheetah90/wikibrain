package org.wikibrain.atlasify.usageanalytics.model;

import net.sf.cglib.core.Local;
import org.json.JSONObject;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.lang.LocalId;

import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

/**
 * Created by toby on 7/10/15.
 */
public class AtlasifyKeywordStatCalculator {
    private static Language lang = Language.EN;
    private static Integer NorthwesternTimeout = 100000;
    public static Map<Integer, String> countryMap;
    private static LocalPageDao lpDao;

    public AtlasifyKeywordStatCalculator() throws ConfigurationException, FileNotFoundException, IOException{
        Env env = new EnvBuilder().build();
        Configurator conf = env.getConfigurator();
        lpDao = conf.get(LocalPageDao.class);
        countryMap = new HashMap<Integer, String>();
        FileReader fileReader = new FileReader("countries.js");
        BufferedReader bufferedReader = new BufferedReader(fileReader);
        String s;
        while((s = bufferedReader.readLine()) != null){}
        fileReader.close();
        JSONObject jsonObject = new JSONObject(s);
        Iterator<String> nameItr = jsonObject.keys();
        while(nameItr.hasNext()){
            try {
                String name = nameItr.next();
                countryMap.put(jsonObject.getInt(name), name);
            }
            catch (Exception e){
                e.printStackTrace();
                continue;
            }
        }

    }

    private static Map<LocalId, Double> accessNorthwesternAPI(LocalId id, Integer topN, boolean spatialOnly) throws Exception {
        Language language = lang;
        String url = "";
        if(topN == -1 && spatialOnly){
            url = "http://downey-n2.cs.northwestern.edu:8080/wwsr/sr/q?sID=" + id.getId() + "&langID=" + language.getId() + "&spatial=true";
        }
        else if (topN == -1){
            url = "http://downey-n2.cs.northwestern.edu:8080/wwsr/sr/q?sID=" + id.getId() + "&langID=" + language.getId();
        }
        else {
            url = "http://downey-n2.cs.northwestern.edu:8080/wwsr/sr/q?sID=" + id.getId() + "&langID=" + language.getId()+ "&top=" + topN.toString();
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
        Iterator<String> nameItr = jsonObject.keys();
        Map<LocalId, Double> result = new HashMap<LocalId, Double>();

        while(nameItr.hasNext()) {
            try{
                String name = nameItr.next();
                LocalId page = new LocalId(language, Integer.parseInt(name));
                Double sr = new Double(jsonObject.getDouble(name));
                result.put(page, sr);
            }
            catch (Exception e){
                continue;
            }
        }

        return result;
    }

    public static Map<LocalId, Double> getFilteredSRMap(Set<Integer> filter, String title, boolean spatialOnly) throws DaoException, Exception{
        LocalId id =  new LocalId(lang, lpDao.getByTitle(lang, title).getLocalId());
        Map<LocalId, Double> nuResults = accessNorthwesternAPI(id, -1, spatialOnly);
        Map<LocalId, Double> returnVal = new HashMap<LocalId, Double>();
        for(Map.Entry<LocalId, Double> result : nuResults.entrySet()){
            if(filter.contains(result.getKey())){
                returnVal.put(result.getKey(), result.getValue());
            }
        }
        return returnVal;
    }

    public static Double getSRMean(Map<LocalId, Double> srMap){
        Double sum = 0.0;
        for(Double sr: srMap.values()){
            sum += sr;
        }
        return sum / srMap.size();
    }

    public static Double getSRRange(Map<LocalId, Double> srMap){
        Double max = 0.0, min = 1.0;
        for(Double sr: srMap.values()){
            max = sr > max ? sr : max;
            min = sr < min ? sr : min;
        }
        return max - min;
    }

    public static Double getSRStdDev(Map<LocalId, Double> srMap){
        Double mean = getSRMean(srMap);
        Double sumDiff = 0.0;
        for(Double sr: srMap.values()){
            sumDiff += (sr - mean);
        }
        return Math.sqrt(sumDiff / srMap.size());
    }







}
