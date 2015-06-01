package org.wikibrain.atlasify;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

/**
 * Created by toby on 6/1/15.
 */
public class ExplanationPreComputing implements Runnable {
    AtlasifyResource.AtlasifyQuery query;
    String explanationsLoadingRefSys;
    String url = "http://downey-n1.cs.northwestern.edu:3030/precompute?concept=" + query.getKeyword().replace(' ', '_') + "&reference=" + explanationsLoadingRefSys;
    int NorthwesternTimeout;
    public ExplanationPreComputing(AtlasifyResource.AtlasifyQuery query, String explanationsLoadingRefSys, int NorthwesternTimeout){
        this.query = query;
        this.explanationsLoadingRefSys = explanationsLoadingRefSys;
        this.NorthwesternTimeout = NorthwesternTimeout;
    }
    public void run(){
        System.out.println("NU Explanations Precompute " + url);
        try {
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
            System.out.println("NU Explanations Precompute status " + jsonObject.get("status"));
        } catch (Exception e) {
            System.out.println("Error Unable to Precompute Explanations");
            e.printStackTrace();
        }

    }
}
