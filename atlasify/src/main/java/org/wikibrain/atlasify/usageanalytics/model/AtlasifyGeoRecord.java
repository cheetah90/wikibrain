package org.wikibrain.atlasify.usageanalytics.model;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

/**
 * Created with IntelliJ IDEA.
 * User: toby
 * Date: 6/29/15
 * Time: 11:15 PM
 * To change this template use File | Settings | File Templates.
 */
public class AtlasifyGeoRecord {
    private static String country;
    private static String city;
    private static String provider;
    private static Point geometry;
    private static Double lat;
    private static Double lon;

    public AtlasifyGeoRecord(String country, String city, String provider, Double lat, Double lon){
        this.country = country;
        this.city = city;
        this.provider = provider;
        this.lat = lat;
        this.lon = lon;
        this.geometry = new GeometryFactory().createPoint(new Coordinate(lat, lon));
    }

    public String getCountry(){
        return country;
    }

    public String getCity(){
        return city;
    }

    public String getProvider(){
        return provider;
    }
    public Point getGeometry(){
        return geometry;
    }

}
