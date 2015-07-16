package org.wikibrain.atlasify.usageanalytics;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.lang.time.DateUtils;
import org.wikibrain.atlasify.usageanalytics.model.*;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.lang.LocalId;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: toby
 * Date: 6/29/15
 * Time: 4:12 PM
 * To change this template use File | Settings | File Templates.
 */
public class LogParser {
    private static String logFileName = "AtlasifyQuery.csv";
    private static CSVReader reader;
    private static CSVWriter writer;
    private static Set<Integer> consumedLoadData = new HashSet<Integer>();
    private static LocalPageDao lpDao;
    private static AtlasifyKeywordStatCalculator calculator;

    private static AtlasifyQueryRecord searchSessionEnd(List<String[]> rows, Integer startIndex, boolean searchFromUserDefined) throws ParseException{
        List<AtlasifyZoomRecord> zoomRecords = new ArrayList<AtlasifyZoomRecord>();
        List<AtlasifyExplanationRecord> explanationRecords = new ArrayList<AtlasifyExplanationRecord>();

        Integer userId = -1;
        try{
            userId = Integer.parseInt(rows.get(startIndex)[0]);
        }
        catch (Exception e){
            //do nothing
        }
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss.SSS");
        Date startDate = dateFormat.parse(rows.get(startIndex)[9]);
        AtlasifyGeoRecord geoRecord = new AtlasifyGeoRecord(rows.get(startIndex)[10], rows.get(startIndex)[11], rows.get(startIndex)[14], rows.get(startIndex)[6], rows.get(startIndex)[7], Double.parseDouble(rows.get(startIndex)[12]), Double.parseDouble(rows.get(startIndex)[13]));
        AtlasifyQueryRecord.QueryType queryType = null;
        String refSystem = "";
        String keyWord = "";
        Integer rating = -1;
        String feedback = " ";

        if(rows.get(startIndex)[5].contentEquals("REGULAR-AUTO-COMPLETION-PRESSED")){
            keyWord = rows.get(startIndex)[2];
            queryType = AtlasifyQueryRecord.QueryType.AUTO_COMPLETE_QUERY;
        }
        else
            keyWord = rows.get(startIndex)[4];

        if(rows.get(startIndex)[5].contentEquals("REGULAR-LOAD-RANDOM"))
            queryType = AtlasifyQueryRecord.QueryType.RANDOM_QUERY;
        if(rows.get(startIndex)[5].contentEquals("FEATURE_ARTICLE-SEARCH"))
            queryType = AtlasifyQueryRecord.QueryType.FEATURED_QUERY;
        if(rows.get(startIndex)[5].contentEquals("GAME-SEARCH"))
            queryType = AtlasifyQueryRecord.QueryType.GAME_QUERY;


        if(searchFromUserDefined){
            refSystem = rows.get(startIndex)[2];
            queryType = AtlasifyQueryRecord.QueryType.REGULAR_QUERY;
        }

        Date endDate = startDate;
        boolean loadDataFound = searchFromUserDefined;
        for(int i = startIndex + 1; i < rows.size(); i++){
            try{
                String[] row = rows.get(i);
                if(Integer.parseInt(rows.get(i)[0]) != userId)
                    continue;
                if(dateFormat.parse(rows.get(i)[9]).after(DateUtils.addMinutes(startDate, 30))){
                    //maximum session length 1hr
                    //assume the csv is sorted in chronological order
                    break;
                }

                if(rows.get(i)[5].contentEquals("REGULAR-LOAD-RANDOM") || rows.get(i)[5].contentEquals("REGULAR-AUTO-COMPLETION-PRESSED") || rows.get(i)[5].contentEquals("FEATURE_ARTICLE-SEARCH") || rows.get(i)[5].contentEquals("LOAD-DATA") || rows.get(startIndex)[5].contentEquals("GAME-SEARCH")){
                    //found the end of the session
                    if(((double)dateFormat.parse(rows.get(i)[9]).getTime() - (double)startDate.getTime())/1000 < 0.5){
                        //potential reversed order
                        if(rows.get(i)[5].contentEquals("REGULAR-LOAD-RANDOM"))
                            queryType = AtlasifyQueryRecord.QueryType.RANDOM_QUERY;
                        if(rows.get(i)[5].contentEquals("REGULAR-AUTO-COMPLETION-PRESSED"))
                            queryType = AtlasifyQueryRecord.QueryType.AUTO_COMPLETE_QUERY;
                        if(rows.get(i)[5].contentEquals("FEATURE_ARTICLE-SEARCH"))
                            queryType = AtlasifyQueryRecord.QueryType.FEATURED_QUERY;
                        if(rows.get(startIndex)[5].contentEquals("GAME-SEARCH"))
                            queryType = AtlasifyQueryRecord.QueryType.GAME_QUERY;
                        continue;
                    }
                    else{
                        endDate = dateFormat.parse(rows.get(i)[9]);
                        break;
                    }
                }
                if(rows.get(i)[5].contentEquals("LOAD-DATA") && loadDataFound){
                    endDate = dateFormat.parse(rows.get(i)[9]);
                    break;
                }
                if(rows.get(i)[5].contentEquals("LOAD-DATA") && (!loadDataFound)){
                    loadDataFound = true;
                    refSystem = rows.get(i)[2];
                    consumedLoadData.add(i);
                    continue;
                }
                //either explanation, zoom, or feedback at this point. should log it.
                if(rows.get(i)[5].contains("ZOOM")){
                    zoomRecords.add(new AtlasifyZoomRecord((rows.get(i)[5]), Double.parseDouble(rows.get(i)[12]), Double.parseDouble(rows.get(i)[13]), dateFormat.parse(rows.get(i)[9])));
                    continue;
                }

                if(rows.get(i)[5].contains("EXPLANATION")){
                    explanationRecords.add(new AtlasifyExplanationRecord(rows.get(i)[4], rows.get(i)[2], Double.parseDouble(rows.get(i)[12]), Double.parseDouble(rows.get(i)[13]), dateFormat.parse(rows.get(i)[9])));
                }

                if(rows.get(i)[5].contains("RATING")){
                    rating = Integer.parseInt(rows.get(i)[5].substring(rows.get(i)[5].indexOf("_") + 1));
                    continue;
                }

                if(rows.get(i)[5].contains("FEEDBACK")){
                    feedback = rows.get(i)[1];
                    continue;
                }


            }
            catch (Exception e){
                continue;
            }


        }
        return new AtlasifyQueryRecord(geoRecord, userId, queryType, keyWord, refSystem, startDate, endDate, zoomRecords, explanationRecords, rating, feedback);

    }
    private static double getWeightedSR(Map<LocalId, Double> fullSRMap){
        /*
        double srUSA = fullSRMap.get(new LocalId(Language.EN, 3434750)) * 0.2953;
        double srIndia = fullSRMap.get(new LocalId(Language.EN, 14533)) * 0.1827;
        double srGermany = fullSRMap.get(new LocalId(Language.EN, 11867)) * 0.1326;
        double srSpain = fullSRMap.get(new LocalId(Language.EN, 26667)) * 0.1232;
        double srMexico = fullSRMap.get(new LocalId(Language.EN, 3966054)) * 0.0501;
        double srUK = fullSRMap.get(new LocalId(Language.EN, 31717)) * 0.0426;
        double srCanada = fullSRMap.get(new LocalId(Language.EN, 5042916)) * 0.0425;
        double srGreece = fullSRMap.get(new LocalId(Language.EN, 12108)) * 0.0199;
        double srAus = fullSRMap.get(new LocalId(Language.EN, 4689264)) * 0.0182;
        double srArgentina = fullSRMap.get(new LocalId(Language.EN, 18951905)) * 0.0164;
        double srFrance = fullSRMap.get(new LocalId(Language.EN, 5843419)) * 0.0147;
        double srNet = fullSRMap.get(new LocalId(Language.EN, 21148)) * 0.0133;
        double srSwit = fullSRMap.get(new LocalId(Language.EN, 26748)) * 0.0129;
        double srChina = fullSRMap.get(new LocalId(Language.EN, 5405)) * 0.0128;
        double srSweden = fullSRMap.get(new LocalId(Language.EN, 5058739)) * 0.0118;
        double srIsrael = fullSRMap.get(new LocalId(Language.EN, 9282173)) * 0.0108;
        */

        List<Double> srList = new ArrayList<Double>();
        srList.add(fullSRMap.get(new LocalId(Language.EN, 3434750)) * 0.2953);
        srList.add(fullSRMap.get(new LocalId(Language.EN, 14533)) * 0.1827);
        srList.add(fullSRMap.get(new LocalId(Language.EN, 11867)) * 0.1326);
        srList.add(fullSRMap.get(new LocalId(Language.EN, 26667)) * 0.1232);
        srList.add(fullSRMap.get(new LocalId(Language.EN, 3966054)) * 0.0501);
        srList.add(fullSRMap.get(new LocalId(Language.EN, 31717)) * 0.0426);
        srList.add(fullSRMap.get(new LocalId(Language.EN, 5042916)) * 0.0425);
        srList.add(fullSRMap.get(new LocalId(Language.EN, 12108)) * 0.0199);
        srList.add(fullSRMap.get(new LocalId(Language.EN, 4689264)) * 0.0182);
        srList.add(fullSRMap.get(new LocalId(Language.EN, 18951905)) * 0.0164);
        srList.add(fullSRMap.get(new LocalId(Language.EN, 5843419)) * 0.0147);
        srList.add(fullSRMap.get(new LocalId(Language.EN, 21148)) * 0.0133);
        srList.add(fullSRMap.get(new LocalId(Language.EN, 26748)) * 0.0129);
        srList.add(fullSRMap.get(new LocalId(Language.EN, 5405)) * 0.0128);
        srList.add(fullSRMap.get(new LocalId(Language.EN, 5058739)) * 0.0118);
        srList.add(fullSRMap.get(new LocalId(Language.EN, 9282173)) * 0.0108);
        double sum = 0;
        for(Double d : srList){
            if(d == null)
                continue;
            sum += d;
        }
        return  sum;

    }

    private static Map<String, String> statCache = new HashMap<String, String>();
    private static void printQueryRecord(String[] rowWrite, CSVWriter writer, AtlasifyQueryRecord queryRecord) throws IOException, Exception{
        rowWrite[0] = queryRecord.getUserCookieId().toString();
        rowWrite[1] = queryRecord.queryType().toString();
        rowWrite[2] = queryRecord.getKeyWord();
        rowWrite[3] = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss.SSS").format(queryRecord.getQueryStartTime());
        rowWrite[4] = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss.SSS").format(queryRecord.getQueryEndTime());
        rowWrite[5] = queryRecord.getGeoRecord().getCity();
        rowWrite[6] = queryRecord.getGeoRecord().getCountry();
        rowWrite[7] = queryRecord.getGeoRecord().getProvider();
        rowWrite[8] = String.valueOf(queryRecord.getExplanationRecords().size());
        rowWrite[9] = String.valueOf(queryRecord.getZoomRecords().size());
        rowWrite[10] = String.valueOf(((double) queryRecord.getQueryEndTime().getTime() - (double) queryRecord.getQueryStartTime().getTime()) / 1000);
        rowWrite[11] = queryRecord.getRefSystem();
        rowWrite[12] = queryRecord.getRating().toString();
        rowWrite[13] = queryRecord.getFeedback();
        String keyword = queryRecord.getKeyWord();
        if(statCache.containsKey(queryRecord.getKeyWord())){
            rowWrite[14] = statCache.get(keyword + "SRMean");
            rowWrite[15] = statCache.get(keyword + "SRRange");
            rowWrite[16] = statCache.get(keyword + "SRStdDev");
            rowWrite[17] = statCache.get(keyword + "SRMaxMedianDiff");
            rowWrite[18] = statCache.get(keyword + "SRClassMean");
            rowWrite[19] = statCache.get(keyword + "SRClassMedian");
            rowWrite[20] = statCache.get(keyword + "SRClassMaxMedianDiff");
            rowWrite[21] = statCache.get(keyword + "SRClassStdDev");

        }
        else{
            Map<LocalId, Double> srMap = calculator.getFilteredSRMap(calculator.countryMap.keySet(), queryRecord.getKeyWord(), true);
            rowWrite[14] = String.valueOf(calculator.getSRMean(srMap));
            statCache.put(keyword + "SRMean", rowWrite[14]);
            rowWrite[15] = String.valueOf(calculator.getSRRange(srMap));
            statCache.put(keyword + "SRRange", rowWrite[15]);
            rowWrite[16] = String.valueOf(calculator.getSRStdDev(srMap));
            statCache.put(keyword + "SRStdDev", rowWrite[16]);
            rowWrite[17] = String.valueOf(calculator.getSRMaxMedianDifference(srMap));
            statCache.put(keyword + "SRMaxMedianDiff", rowWrite[17]);
            rowWrite[18] = String.valueOf(calculator.getSRClassMean(srMap));
            statCache.put(keyword + "SRClassMean", rowWrite[18]);
            rowWrite[19] = String.valueOf(calculator.getSRClassMedian(srMap));
            statCache.put(keyword + "SRClassMedian", rowWrite[19]);
            rowWrite[20] = String.valueOf(calculator.getSRClassMaxMedianDifference(srMap));
            statCache.put(keyword + "SRClassMaxMedianDiff", rowWrite[20]);
            rowWrite[21] = String.valueOf(calculator.getSRClassStdDev(srMap));
            statCache.put(keyword + "SRClassStdDev", rowWrite[21]);
        }
        System.out.println(queryRecord.getKeyWord() + " mean: " + rowWrite[14] + " range: " + rowWrite[15] + " stdDev: " + rowWrite[16]);
        rowWrite[22] = "";
        rowWrite[23] = "";
        rowWrite[24] = "";
        rowWrite[25] = "";
        rowWrite[26] = "";
        rowWrite[27] = "";
        rowWrite[28] = "";
        rowWrite[29] = "";
        rowWrite[30] = "";
        rowWrite[31] = "";


        Map<LocalId, Double> filteredSRMap = calculator.getFilteredSRMap(calculator.countryMap.keySet(), keyword, true);
        Map<LocalId, Double> fullSRMap = calculator.getFilteredSRMap(null, keyword, true);
        LocalId id = new LocalId(Language.EN, -1);
        try{
            id =  new LocalId(Language.EN, lpDao.getByTitle(Language.EN, queryRecord.getGeoRecord().getCountry()).getLocalId());
        }
        catch (Exception e){
            writer.writeNext(rowWrite);
            writer.flush();
        }
        Double featureSRValue = fullSRMap.get(id);
        if(featureSRValue == null){
            writer.writeNext(rowWrite);
            writer.flush();
        }

        rowWrite[22] = String.valueOf(featureSRValue);

        List<Double> srList = new ArrayList<Double>();
        for(Double d : filteredSRMap.values()){
            srList.add(d);
        }
        if(srList.size() == 0){
            writer.writeNext(rowWrite);
            writer.flush();
        }
        Collections.sort(srList);
        rowWrite[26] = String.valueOf(srList.get(srList.size()/4));
        rowWrite[27] = String.valueOf(srList.get(srList.size()/2));
        rowWrite[28] = String.valueOf(srList.get((srList.size() * 3)/4));
        for(int j = 0; j < srList.size(); j ++){
            if(featureSRValue < srList.get(j)){
                rowWrite[23] = String.valueOf((double)j / (double)srList.size());
                break;
            }
        }
        int category = 0;
        if(featureSRValue > 0.39)
            category = 1;
        if(featureSRValue > 0.42)
            category = 2;
        if(featureSRValue > 0.445)
            category = 3;
        if(featureSRValue > 0.475)
            category = 4;
        if(featureSRValue > 0.51)
            category = 5;
        if(featureSRValue > 0.58)
            category = 6;
        if(featureSRValue > 0.66)
            category = 7;
        if(featureSRValue > 0.75)
            category = 8;
        rowWrite[24] = String.valueOf(category);

        rowWrite[25] = String.valueOf(calculator.getSRMedianClass(filteredSRMap));


        Double baselineSR = getWeightedSR(fullSRMap);
        rowWrite[29] = String.valueOf(baselineSR);
        for(int j = 0; j < srList.size(); j ++){
            if(baselineSR < srList.get(j)){
                rowWrite[30] = String.valueOf((double)j / (double)srList.size());
                break;
            }
        }
        category = 0;
        if(baselineSR > 0.39)
            category = 1;
        if(baselineSR > 0.42)
            category = 2;
        if(baselineSR > 0.445)
            category = 3;
        if(baselineSR > 0.475)
            category = 4;
        if(baselineSR > 0.51)
            category = 5;
        if(baselineSR > 0.58)
            category = 6;
        if(baselineSR > 0.66)
            category = 7;
        if(baselineSR > 0.75)
            category = 8;
        rowWrite[31] = String.valueOf(category);


        writer.writeNext(rowWrite);
        writer.flush();
    }


    public static void main(String args[])  throws IOException, ParseException, ConfigurationException {
        Env env = EnvBuilder.envFromArgs(args);
        lpDao = env.getConfigurator().get(LocalPageDao.class);
        int count = 0;
        reader = new CSVReader(new FileReader(logFileName), ',');
        writer = new CSVWriter(new FileWriter("AtlasifyLogAnalysis_withUserLocationSR.csv"), ',');
        calculator = new AtlasifyKeywordStatCalculator();
        String[] rowWrite = new String[32];
        rowWrite[0] = "userId";
        rowWrite[1] = "queryType";
        rowWrite[2] = "keyword";
        rowWrite[3] = "startTime";
        rowWrite[4] = "endTime";
        rowWrite[5] = "city";
        rowWrite[6] = "country";
        rowWrite[7] = "provider";
        rowWrite[8] = "numberOfZooms";
        rowWrite[9] = "numberOfExplanations";
        rowWrite[10] = "timeSpent";
        rowWrite[11] = "refSystem";
        rowWrite[12] = "rating";
        rowWrite[13] = "feedback";
        rowWrite[14] = "STAT_MEAN";
        rowWrite[15] = "STAT_RANGE";
        rowWrite[16] = "STAT_STDDEV";
        rowWrite[17] = "STAT_MAX_MEDIAN_DIFFERENCE";
        rowWrite[18] = "STAT_CLASS_MEAN";
        rowWrite[19] = "STAT_CLASS MEDIAN";
        rowWrite[20] = "STAT_CLASS_MAX_MEDIAN_DIFFERENCE";
        rowWrite[21] = "STAT_CLASS_STD_DEV";
        rowWrite[22] = "userLocationSR";
        rowWrite[23] = "userLocationSRPercentile";
        rowWrite[24] = "userLocationSRClass";
        rowWrite[25] = "medianClass";
        rowWrite[26] = "25th Percentile";
        rowWrite[27] = "50th Percentile";
        rowWrite[28] = "75th Percentile";
        rowWrite[29] = "WeightedMeanSR";
        rowWrite[30] = "WeightedMeanSRPercentile";
        rowWrite[31] = "WeightedSRClass";
        writer.writeNext(rowWrite);
        writer.flush();

        List<String[]> rows = reader.readAll();
        //read query sessions
        for(int i = 0; i < rows.size(); i++){
            if(count++ % 1 == 0){
                System.out.println("Done with " + count + "rows out of " + rows.size());
            }
            String[] row = rows.get(i);

            //Filter out developers' sessions
            try{
                if(row[11].contentEquals("Minneapolis") || row[11].contentEquals("Evanston") || row[11].contentEquals("Madison"))
                    continue;

                if(row[5].contentEquals("REGULAR-LOAD-RANDOM")){
                    //System.out.println(searchSessionEnd(rows, i, false));
                    printQueryRecord(rowWrite, writer, searchSessionEnd(rows, i, false));
                }
                if(row[5].contentEquals("REGULAR-AUTO-COMPLETION-PRESSED")){
                    //System.out.println(searchSessionEnd(rows, i, false));
                    printQueryRecord(rowWrite, writer, searchSessionEnd(rows, i, false));
                }
                if(row[5].contentEquals("FEATURE_ARTICLE-SEARCH")){
                    //System.out.println(searchSessionEnd(rows, i, false));
                    printQueryRecord(rowWrite, writer, searchSessionEnd(rows, i, false));
                }
                if(row[5].contentEquals("GAME-SEARCH")){
                    //System.out.println(searchSessionEnd(rows, i, false));
                    printQueryRecord(rowWrite, writer, searchSessionEnd(rows, i, false));
                }
                if(row[5].contentEquals("LOAD-DATA") && (!consumedLoadData.contains(i))){
                    consumedLoadData.add(i);
                    //System.out.println(searchSessionEnd(rows, i, true));
                    printQueryRecord(rowWrite, writer, searchSessionEnd(rows, i, true));
                    //search query from a user-defined keyword
                }
            }
            catch (Exception e){
                continue;
            }

        }


    }




}
