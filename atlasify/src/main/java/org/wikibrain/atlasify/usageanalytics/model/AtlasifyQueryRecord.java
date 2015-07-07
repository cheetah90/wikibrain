package org.wikibrain.atlasify.usageanalytics.model;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.Point;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: toby
 * Date: 6/29/15
 * Time: 11:13 PM
 * To change this template use File | Settings | File Templates.
 */
public class AtlasifyQueryRecord {


    private static AtlasifyGeoRecord geoRecord;
    private static Integer userCookieId;
    public static enum QueryType {
        REGULAR_QUERY, RANDOM_QUERY, FEATURED_QUERY, AUTO_COMPLETE_QUERY, GAME_QUERY
    };
    private static QueryType queryType;
    private static String keyWord;
    private static String refSystem;
    private static Date queryStartTime;
    private static Date queryEndTime;
    private static List<AtlasifyZoomRecord> zoomRecords;
    private static List<AtlasifyExplanationRecord> explanationRecords;
    private static Integer rating;
    private static String feedback;


    //private static

    public AtlasifyQueryRecord(AtlasifyGeoRecord geoRecord, Integer userCookieId, QueryType queryType, String keyWord, String refSystem, Date queryStartTime, Date queryEndTime, List<AtlasifyZoomRecord> zoomRecords, List<AtlasifyExplanationRecord> explanationRecords, Integer rating, String feedback){
        this.geoRecord = geoRecord;
        this.userCookieId = userCookieId;
        this.queryType = queryType;
        this.keyWord = keyWord;
        this.refSystem = refSystem;
        this.queryStartTime = queryStartTime;
        this.queryEndTime = queryEndTime;
        this.zoomRecords = zoomRecords;
        this.explanationRecords = explanationRecords;
        this.rating = rating;
        this.feedback = feedback;
    }


    @Override public String toString(){
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH:mm:ss.SSS");
        return new String(userCookieId + " " + queryType + " " + keyWord + " " + refSystem + " " + dateFormat.format(queryStartTime) + " " + dateFormat.format(queryEndTime) + " " + geoRecord.getCity() + " " +  zoomRecords.size() + " zoomRecords " + explanationRecords.size() + " explanationRecords " + ((double)queryEndTime.getTime() - (double) queryStartTime.getTime())/1000 + "s");
    }

    public AtlasifyGeoRecord getGeoRecord(){
        return geoRecord;
    }

    public Integer getUserCookieId(){
        return userCookieId;
    }

    public QueryType queryType(){
        return queryType;
    }

    public String getKeyWord(){
        return keyWord;
    }

    public String getRefSystem(){
        return refSystem;
    }

    public Date getQueryStartTime(){
        return queryStartTime;
    }

    public Date getQueryEndTime(){
        return queryEndTime;
    }

    public List<AtlasifyZoomRecord> getZoomRecords(){
        return zoomRecords;
    }

    public List<AtlasifyExplanationRecord> getExplanationRecords(){
        return explanationRecords;
    }

    public Integer getRating(){
        return rating;
    }

    public String getFeedback(){
        return feedback;
    }




}
