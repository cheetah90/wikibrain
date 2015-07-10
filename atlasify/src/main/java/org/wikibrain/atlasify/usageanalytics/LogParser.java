package org.wikibrain.atlasify.usageanalytics;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.apache.commons.lang.time.DateUtils;
import org.wikibrain.atlasify.usageanalytics.model.*;
import org.wikibrain.conf.ConfigurationException;

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

                if(rows.get(i)[5].contentEquals("LOAD-DATA") && (!loadDataFound)){
                    loadDataFound = true;
                    refSystem = rows.get(i)[2];
                    consumedLoadData.add(i);
                    continue;
                }

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

                if(rows.get(i)[5].contentEquals("REGULAR-LOAD-RANDOM") || rows.get(i)[5].contentEquals("REGULAR-AUTO-COMPLETION-PRESSED") || rows.get(i)[5].contentEquals("FEATURE_ARTICLE-SEARCH") || rows.get(i)[5].contentEquals("LOAD-DATA") || rows.get(startIndex)[5].contentEquals("GAME-SEARCH")){
                    //found the end of the session
                    endDate = dateFormat.parse(rows.get(i)[9]);
                    break;
                }






                //either explanation, zoom, or feedback at this point. should log it.
            }
            catch (Exception e){
                continue;
            }


        }
        return new AtlasifyQueryRecord(geoRecord, userId, queryType, keyWord, refSystem, startDate, endDate, zoomRecords, explanationRecords, rating, feedback);

    }
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
        rowWrite[10] = String.valueOf(((double)queryRecord.getQueryEndTime().getTime() -(double) queryRecord.getQueryStartTime().getTime())/1000);
        rowWrite[11] = queryRecord.getRefSystem();
        rowWrite[12] = queryRecord.getRating().toString();
        rowWrite[13] = queryRecord.getFeedback();
        rowWrite[14] = String.valueOf(calculator.getSRMean(calculator.getFilteredSRMap(calculator.countryMap.keySet(), queryRecord.getKeyWord(), true)));
        rowWrite[15] = String.valueOf(calculator.getSRRange(calculator.getFilteredSRMap(calculator.countryMap.keySet(), queryRecord.getKeyWord(), true)));
        rowWrite[16] = String.valueOf(calculator.getSRStdDev(calculator.getFilteredSRMap(calculator.countryMap.keySet(), queryRecord.getKeyWord(), true)));
        System.out.println(queryRecord.getKeyWord() + " mean: " + rowWrite[14] + " range: " + rowWrite[15] + " stdDev: " + rowWrite[16]);
        writer.writeNext(rowWrite);
        writer.flush();
    }


    public static void main(String args[])  throws IOException, ParseException, ConfigurationException {
        int count = 0;
        reader = new CSVReader(new FileReader(logFileName), ',');
        writer = new CSVWriter(new FileWriter("AtlasifyLogAnalysis.csv"), ',');
        calculator = new AtlasifyKeywordStatCalculator();
        String[] rowWrite = new String[17];
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
        writer.writeNext(rowWrite);
        writer.flush();

        List<String[]> rows = reader.readAll();
        //read query sessions
        for(int i = 0; i < rows.size(); i++){
            if(count++ % 1 == 0){
                System.out.println("Done with " + count + " out of " + rows.size());
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
