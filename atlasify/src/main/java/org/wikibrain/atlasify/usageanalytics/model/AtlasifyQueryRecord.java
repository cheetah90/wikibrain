package org.wikibrain.atlasify.usageanalytics.model;

import java.util.Date;

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

    //private static

    public AtlasifyQueryRecord(AtlasifyGeoRecord geoRecord, Integer userCookieId, QueryType queryType, String keyWord, String refSystem, Date queryStartTime, Date queryEndTime){
        this.geoRecord = geoRecord;
        this.userCookieId = userCookieId;
        this.queryType = queryType;
        this.keyWord = keyWord;
        this.refSystem = refSystem;
        this.queryStartTime = queryStartTime;
        this.queryEndTime = queryEndTime;
    }





}
