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
        String s = new Scanner( new File("countries.js") ).useDelimiter("\\A").next();
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
        Map<LocalId, Double> result = new HashMap<LocalId, Double>();
        try{
            //hack
            Language language = Language.EN;
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
            //System.out.println("NU QUERY " + url);

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


            while(nameItr.hasNext()) {
                try{
                    String name = nameItr.next();
                    LocalId page = new LocalId(language, Integer.parseInt(name));
                    Double sr = new Double(jsonObject.getDouble(name));
                    result.put(page, sr);
                }
                catch (Exception e){
                    e.printStackTrace();
                    continue;
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
        }

        return result;
    }

    public static Map<LocalId, Double> getFilteredSRMap(Set<Integer> filter, String title, boolean spatialOnly) throws DaoException, Exception{
        Map<LocalId, Double> returnVal = new HashMap<LocalId, Double>();
        try{
            LocalId id =  new LocalId(lang, lpDao.getByTitle(lang, title).getLocalId());
            Map<LocalId, Double> nuResults = accessNorthwesternAPI(id, -1, spatialOnly);
            for(Map.Entry<LocalId, Double> result : nuResults.entrySet()){
                if(filter.contains(result.getKey().getId())){
                    returnVal.put(result.getKey(), result.getValue());
                }
            }
        }
        catch (Exception e){
            e.printStackTrace();
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
            sumDiff += (sr - mean) * (sr - mean);
        }
        return Math.sqrt(sumDiff / srMap.size());
    }

    public static Double getSRMedian(Map<LocalId, Double> srMap){
        ArrayList<Double> list = new ArrayList<Double>();
        for(Double sr: srMap.values()){
            list.add(sr);
        }
        Collections.sort(list);
        if(list.size() % 2 == 0)
            return (list.get(list.size()/2) + list.get(list.size()/2 - 1))/2;
        else
            return list.get(list.size()/2);
    }

    public static Double getSRMaxMedianDifference(Map<LocalId, Double> srMap){
        Double max = 0.0;
        for(Double sr : srMap.values()){
            max = sr > max ? sr : max;
        }
        return max - getSRMedian(srMap);
    }

    private static double[] getSRClassDistribution(Map<LocalId, Double> srMap){
        double[] retVal = new double[9];
        for(int i = 0; i < 9; i++)
            retVal[i] = 0.0;
        for(Double sr : srMap.values()){
            int category = 0;
            if(sr > 0.39)
                category = 1;
            if(sr > 0.42)
                category = 2;
            if(sr > 0.445)
                category = 3;
            if(sr > 0.475)
                category = 4;
            if(sr > 0.51)
                category = 5;
            if(sr > 0.58)
                category = 6;
            if(sr > 0.66)
                category = 7;
            if(sr > 0.75)
                category = 8;
            retVal[category] ++;
        }
        return retVal;
    }

    public static double getSRClassMean(Map<LocalId, Double> srMap){
        double[] distribution = getSRClassDistribution(srMap);
        double sum = 0.0;
        for(int i = 0; i < distribution.length; i++){
            sum += distribution[i];
        }
        return sum / distribution.length;
    }

    public static double getSRClassMedian(Map<LocalId, Double> srMap){
        double[] distribution = getSRClassDistribution(srMap);
        Arrays.sort(distribution);
        if(distribution.length % 2 == 0)
            return (distribution[distribution.length/2] + distribution[distribution.length/2 - 1])/2;
        else
            return distribution[distribution.length/2];
    }

    public static double getSRClassMaxMedianDifference(Map<LocalId, Double> srMap){
        double[] distribution = getSRClassDistribution(srMap);
        double max = 0.0;
        for(int i = 0; i < distribution.length; i++){
            max = distribution[i] > max ? distribution[i] : max;
        }
        return max - getSRClassMedian(srMap);
    }

    public static double getSRClassMaxMeanDifference(Map<LocalId, Double> srMap){
        double[] distribution = getSRClassDistribution(srMap);
        double max = 0.0;
        for(int i = 0; i < distribution.length; i++){
            max = distribution[i] > max ? distribution[i] : max;
        }
        return max - getSRClassMean(srMap);
    }

    public static double getSRClassStdDev(Map<LocalId, Double> srMap){
        double[] distribution = getSRClassDistribution(srMap);
        double mean = getSRClassMean(srMap);
        double sum = 0;
        for(int i = 0; i < distribution.length; i++){
            sum += (distribution[i] - mean) * (distribution[i] - mean);
        }
        return Math.sqrt(sum / distribution.length);
    }











}
