package org.wikibrain.atlasify.usageanalytics.model;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import java.util.Date;

/**
 * Created by toby on 7/2/15.
 */
public class AtlasifyZoomRecord{
    private static Integer zoomLevel;
    private static Point geometry;
    private static Date time;

    public AtlasifyZoomRecord(String zoomLevel, Double lat, Double lon, Date time){
        this.zoomLevel = Integer.parseInt(zoomLevel.substring(13));
        this.geometry = new GeometryFactory().createPoint(new Coordinate(lat, lon));
        this.time = time;
    }
    public Integer getZoomLevel(){
        return zoomLevel;
    }
    public Point getGeometry(){
        return geometry;
    }
    public Date getTime(){
        return time;
    }

}

