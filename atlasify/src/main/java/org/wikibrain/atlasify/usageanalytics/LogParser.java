package org.wikibrain.atlasify.usageanalytics;

import au.com.bytecode.opencsv.CSVReader;

import java.io.FileReader;
import java.io.IOException;

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
    public static void main(String args[])  throws IOException {
        reader = new CSVReader(new FileReader(logFileName), ',');
    }


}
