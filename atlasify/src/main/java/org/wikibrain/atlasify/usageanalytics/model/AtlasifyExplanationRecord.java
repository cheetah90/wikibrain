package org.wikibrain.atlasify.usageanalytics.model;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;
import org.wikibrain.atlasify.AtlasifyLauncher;

import java.util.Date;

/**
 * Created by toby on 7/2/15.
 */
public class AtlasifyExplanationRecord {
    private static String keyWord;
    private static String explanationFeature;
    private static Point geometry;
    private static Date time;

    public AtlasifyExplanationRecord(String keyWord, String explanationFeature, Double lat, Double lon, Date time){
        this.keyWord = keyWord;
        this.explanationFeature = explanationFeature;
        this.geometry = new GeometryFactory().createPoint(new Coordinate(lat, lon));
        this.time = time;
    }

    public String getKeyWord(){
        return keyWord;
    }

    public String getExplanationFeature(){
        return explanationFeature;
    }


}
