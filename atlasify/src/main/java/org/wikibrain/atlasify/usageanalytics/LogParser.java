package org.wikibrain.atlasify.usageanalytics;

import au.com.bytecode.opencsv.CSVReader;
import org.apache.commons.lang.time.DateUtils;
import org.wikibrain.atlasify.usageanalytics.model.AtlasifyGeoRecord;
import org.wikibrain.atlasify.usageanalytics.model.AtlasifyQueryRecord;

import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: toby
 * Date: 6/29/15
 * Time: 4:12 PM
 * To change this template use File | Settings | File Templates.
 */
public class LogParser {
    private static String logFileName = "test.csv";
    private static CSVReader reader;
    private static Set<Integer> consumedLoadData = new HashSet<Integer>();
    private static AtlasifyQueryRecord searchSessionEnd(List<String[]> rows, Integer startIndex, boolean searchFromUserDefined) throws ParseException{
        Integer userId = Integer.parseInt(rows.get(startIndex)[0]);
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss.SSS");
        Date startDate = dateFormat.parse(rows.get(startIndex)[9]);
        AtlasifyGeoRecord geoRecord = new AtlasifyGeoRecord(rows.get(startIndex)[10], rows.get(startIndex)[11], rows.get(startIndex)[14], rows.get(startIndex)[6], rows.get(startIndex)[7], Double.parseDouble(rows.get(startIndex)[12]), Double.parseDouble(rows.get(startIndex)[13]));
        AtlasifyQueryRecord.QueryType queryType = null;
        String refSystem = "";
        String keyWord = "";

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
            String[] row = rows.get(i);

            if(Integer.parseInt(rows.get(i)[0]) != userId)
                continue;
            if(dateFormat.parse(rows.get(i)[9]).after(DateUtils.addHours(startDate, 1))){
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

            if(rows.get(i)[5].contentEquals("REGULAR-LOAD-RANDOM") || rows.get(i)[5].contentEquals("REGULAR-AUTO-COMPLETION-PRESSED") || rows.get(i)[5].contentEquals("FEATURE_ARTICLE-SEARCH") || rows.get(i)[5].contentEquals("LOAD-DATA") || rows.get(startIndex)[5].contentEquals("GAME-SEARCH")){
                //found the end of the session
                endDate = dateFormat.parse(rows.get(i)[9]);
            }

            //either explanation, zoom, or feedback at this point. should log it.


        }
        return new AtlasifyQueryRecord(geoRecord, userId, queryType, keyWord, refSystem, startDate, endDate);


    }
    public static void main(String args[])  throws IOException, ParseException {
        reader = new CSVReader(new FileReader(logFileName), ',');
        List<String[]> rows = reader.readAll();
        //read query sessions
        for(int i = 0; i < rows.size(); i++){
            String[] row = rows.get(i);
            if(row[5] == "REGULAR-LOAD-RANDOM"){
                searchSessionEnd(rows, i, false);
            }
            if(row[5] == "REGULAR-AUTO-COMPLETION-PRESSED"){
                searchSessionEnd(rows, i, false);
            }
            if(row[5] == "FEATURE_ARTICLE-SEARCH"){
                searchSessionEnd(rows, i, false);
            }
            if(row[5] == "GAME-SEARCH"){
                searchSessionEnd(rows, i, false);
            }
            if(row[5] == "LOAD-DATA" && (!consumedLoadData.contains(i))){
                consumedLoadData.add(i);
                searchSessionEnd(rows, i, true);
                //search query from a user-defined keyword
            }

        }


    }




}
