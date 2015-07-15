package org.wikibrain.atlasify.usageanalytics;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import org.wikibrain.atlasify.usageanalytics.model.AtlasifyKeywordStatCalculator;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.lang.LocalId;

import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Created by toby on 7/15/15.
 */
public class ExplanationParser {
    private static String logFileName = "AtlasifyExplanationQuery.csv";
    private static CSVReader reader;
    private static CSVWriter writer;
    private static AtlasifyKeywordStatCalculator calculator;
    private static LocalPageDao lpDao;


    public static void main(String args[])  throws IOException, ParseException, ConfigurationException, Exception {
        Configurator conf = EnvBuilder.builderFromArgs(args).build().getConfigurator();
        lpDao = conf.get(LocalPageDao.class);
        reader = new CSVReader(new FileReader(logFileName), ',');
        writer = new CSVWriter(new FileWriter("AtlasifyExplanationAnalysis.csv"), ',');

        List<String[]> rows = reader.readAll();
        calculator = new AtlasifyKeywordStatCalculator();

        String[] writeRow = new String[15];
        writeRow[0] = "userId";
        writeRow[1] = "clickLatLon";
        writeRow[2] = "feature";
        writeRow[3] = "keyword";
        writeRow[4] = "type";
        writeRow[5] = "time";
        writeRow[6] = "country";
        writeRow[7] = "city";
        writeRow[8] = "provider";
        writeRow[9] = "userLat";
        writeRow[10] = "userLon";
        writeRow[11] = "clickedFeatureSR";
        writeRow[12] = "clickedFeatureSRClass";
        writeRow[13] = "clikedFeatureSRPercentile";
        writeRow[14] = "medianClass";
        writer.writeNext(writeRow);
        writer.flush();
        int count = 0;
        for(int i = 0; i < rows.size(); i++){
            if(count++ % 1 == 0){
                System.out.println("Done with " + count + "rows out of " + rows.size());
            }
            String[] row = rows.get(i);
            String keyword = row[4];
            String feature = row[2];

            writeRow[0] = row[0];
            writeRow[1] = row[1];
            writeRow[2] = row[2];
            writeRow[3] = row[4];
            writeRow[4] = row[5];
            writeRow[5] = row[9];
            writeRow[6] = row[10];
            writeRow[7] = row[11];
            writeRow[8] = row[14];
            writeRow[9] = row[12];
            writeRow[10] = row[13];
            writeRow[11] = "";
            writeRow[12] = "";
            writeRow[13] = "";
            writeRow[14] = "";
            Map<LocalId, Double> filteredSRMap = calculator.getFilteredSRMap(calculator.countryMap.keySet(), keyword, true);
            Map<LocalId, Double> fullSRMap = calculator.getFilteredSRMap(null, keyword, true);
            LocalId id;
            try{
                id =  new LocalId(Language.EN, lpDao.getByTitle(Language.EN, feature).getLocalId());
            }
            catch (Exception e){
                writer.writeNext(writeRow);
                writer.flush();
                continue;
            }
            Double featureSRValue = fullSRMap.get(id);
            if(featureSRValue == null){
                writer.writeNext(writeRow);
                writer.flush();
                continue;
            }

            writeRow[11] = String.valueOf(featureSRValue);
            List<Double> srList = new ArrayList<Double>();
            for(Double d : filteredSRMap.values()){
                srList.add(d);
            }
            Collections.sort(srList);
            for(int j = 0; j < srList.size(); j ++){
                if(featureSRValue < srList.get(j)){
                    writeRow[13] = String.valueOf((double)j / (double)srList.size());
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
            writeRow[12] = String.valueOf(category);

            writeRow[14] = String.valueOf(calculator.getSRMedianClass(filteredSRMap));
            writer.writeNext(writeRow);
            writer.flush();







        }


    }
}