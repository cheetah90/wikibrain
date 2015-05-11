package org.wikibrain.cookbook.core;

import au.com.bytecode.opencsv.CSVReader;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.core.dao.DaoException;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: toby
 * Date: 5/3/15
 * Time: 5:22 PM
 * To change this template use File | Settings | File Templates.
 */
public class ToblerValueCalculator {
    public static void main(String args[]) throws FileNotFoundException, IOException {

        String[] languages = {"en", "nl", "de", "sv", "fr", "it", "ru", "es", "pl", "ja", "vi", "pt", "zh", "ca", "no", "fi", "cs", "ko", "ar", "hu" };
        for(String lang : languages){
            Map<Integer, Double> distanceSRPercentileMap = new HashMap<Integer, Double>();
            Map<Integer, Integer> distanceSRCountMap = new HashMap<Integer, Integer>();
            Map<Integer, Double> distanceSRAvgPercentileMap = new HashMap<Integer, Double>();

            CSVReader reader = new CSVReader(new FileReader("./Basic TFL_bin/TFL-" + lang + "_bin.csv"), ',');
            List<String[]> readingResult = reader.readAll();
            for(String[] row : readingResult){
                Integer distance = 0;
                Double srPencentile = 0.0;
                try{
                    distance = Integer.parseInt(row[5]);
                    srPencentile = Double.parseDouble(row[7]);
                }
                catch (Exception e){
                    continue;
                }
                if(distanceSRPercentileMap.containsKey(distance)){
                    distanceSRPercentileMap.put(distance, distanceSRPercentileMap.get(distance) + srPencentile);
                    distanceSRCountMap.put(distance, distanceSRCountMap.get(distance) + 1);
                }
                else{
                    distanceSRPercentileMap.put(distance, srPencentile);
                    distanceSRCountMap.put(distance, 1);
                }
            }
            Integer mValue = 0;
            Integer kValue = 0;
            Double prevSRValue = 1.0;
            Double sumSR = 0.0;
            Integer sumCount = 0;
            Integer sumCountMedian = 0;
            Integer medianDistance = 0;
            for(Integer i = 0; i <= 25000; i += 50){
                if(distanceSRPercentileMap.containsKey(i)){
                    //System.out.println(i + ": " + (distanceSRPercentileMap.get(i) / distanceSRCountMap.get(i)) + " count: " + distanceSRCountMap.get(i));
                }
                else
                    continue;
                sumCountMedian += distanceSRCountMap.get(i);
                if(sumCountMedian > 50000 && medianDistance == 0)
                    medianDistance = i;

                sumSR = 0.0;
                sumCount = 0;
                for(Integer j = i - 300; j < i; j += 50){
                    if(distanceSRPercentileMap.containsKey(j)){
                        sumSR += distanceSRPercentileMap.get(j);
                        sumCount += distanceSRCountMap.get(j);
                    }
                }
                if(i > 100 && sumCount > 0 && sumSR / sumCount < distanceSRPercentileMap.get(i) / distanceSRCountMap.get(i) && mValue == 0)
                    mValue = i;

                sumSR = 0.0;
                sumCount = 0;
                for(Integer j = i + 50; j <= 25000; j += 50){
                    if(distanceSRPercentileMap.containsKey(j)){
                        sumSR += distanceSRPercentileMap.get(j);
                        sumCount += distanceSRCountMap.get(j);
                    }
                }
                if(sumSR / sumCount > distanceSRPercentileMap.get(i) / distanceSRCountMap.get(i) && kValue == 0)
                    kValue = i;

                prevSRValue = distanceSRPercentileMap.get(i) / distanceSRCountMap.get(i);
            }


            System.out.println(lang + " m value: " + mValue + "  k value: " + kValue + " median distance: " + medianDistance);
        }
    }


}