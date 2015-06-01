package org.wikibrain.atlasify;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;

import java.io.File;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by toby on 6/1/15.
 */
public class SRCacheDao {
    String dbName = "SRCacheDB";
    DB db;
    HTreeMap<Map.Entry<String, String>, Double> srMatrix;
    public SRCacheDao(){
        db = DBMaker.newFileDB(new File(dbName)).cacheSize(1000000).make();
        srMatrix = db.getHashMap("srSparseMatrix");
    }
    public void saveSR(String keyword, String feature, Double srValue){
        srMatrix.put(new AbstractMap.SimpleEntry<String, String>(keyword, feature), srValue);
        db.commit();
    }

    public Double getSR(String keyword, String feature) throws Exception{
        if(checkSRExist(keyword, feature)){
            return srMatrix.get(new AbstractMap.SimpleEntry<String, String>(keyword, feature));
        }
        else
            throw new Exception("keyword/feature pair not present");
    }

    public boolean checkSRExist(String keyword, String feature){
        return srMatrix.containsKey(new AbstractMap.SimpleEntry<String, String>(keyword, feature));
    }

}
