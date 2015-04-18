package org.wikibrain.atlasify;

import au.com.bytecode.opencsv.CSVWriter;


import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Logger;

/**
 * Created by toby on 4/18/15.
 */
public class AtlasifyLauncher {

    private static final Logger LOG = Logger.getLogger(AtlasifyLauncher.class.getName());
    private static CSVWriter serverLogWriter;
    private static String baseURL = new String("http://spatialization.cs.umn.edu/");
    private static int portNo = 8080;
    private static class checkAtlasifyStatus extends TimerTask{
        @Override
        public void run(){
            LOG.info("Checking the status of Atlasify server...");
            int retryCounter = 0;
            while(retryCounter ++ < 5){
                try{
                    URL helloWorldUrl = new URL(baseURL + "/wikibrain/helloworld");
                    HttpURLConnection connection = (HttpURLConnection)helloWorldUrl.openConnection();
                    connection.setRequestMethod("GET");
                    BufferedReader br = new BufferedReader(new InputStreamReader((connection.getInputStream())));
                    String output;
                    StringBuilder sb = new StringBuilder();
                    while ((output = br.readLine()) != null) {
                        sb.append(output);
                    }

                    if(sb.toString().contains("hello world")){
                        //serverLog("Check Status", "Good");
                        LOG.info("Server is good! Next check in 2 minutes");
                        return;
                    }
                    else {
                        //serverLog("Check Status", "Bad - Try again");
                        LOG.info("Server is bad! Will check again");
                        Thread.sleep(1000);
                    }
                }
                catch (Exception e){
                    try {
                        Thread.sleep(3000);
                    }
                    catch (Exception e2){}
                    continue;
                    //will retry accessing hello world
                }
            }
            try{
                LOG.warning("Server is not responsive! Attemping restarting Atlasify");
                restartAtlasify();
                LOG.info("Atlasify server restarted");
                serverLog("Check Status", "Bad - Restart successfully");
            }
            catch (Exception e){
                LOG.warning("Failed to restart Atlasify");
                serverLog("Check Status", "Bad - Restart failed");
            }

        }
    }

    private static AtlasifyServer server;



    private static void restartAtlasify() throws InterruptedException, IOException{
        server.stopAtlasify();
        Thread.sleep(30000);
        server.startAtlasify();
    }

    private static void serverLog(String operation, String status){
        try{
            String[] row = new String[3];
            row[0] = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
            row[1] = operation;
            row[2] = status;
            serverLogWriter.writeNext(row);
            serverLogWriter.flush();
        }
        catch (Exception e){
            //do nothing
        }
    }

    public static void main(String args[])  throws IOException{
        server = new AtlasifyServer(baseURL, portNo);
        serverLogWriter = new CSVWriter(new FileWriter(new File("atlasifyServerLog.csv"), true), ',');
        LOG.info("Starting Atlasify server...");
        server.startAtlasify();
        LOG.info("Atlasify server started! Hit enter to stop");
        serverLog("Start", "Successful");
        TimerTask atlasifyTask = new checkAtlasifyStatus();
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(atlasifyTask, 120000, 120000);
        System.in.read();
        LOG.info("Stopping Atlasfiy...");
        server.stopAtlasify();
        LOG.info("Atlasify stopped.");
        serverLog("Stop", "Successful");
        return;
    }

}
