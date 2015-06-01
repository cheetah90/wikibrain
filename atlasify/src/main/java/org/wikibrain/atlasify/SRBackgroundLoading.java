package org.wikibrain.atlasify;

import org.wikibrain.sr.SRMetric;

/**
 * Created by toby on 6/1/15.
 */
public class SRBackgroundLoading implements Runnable {
    AtlasifyResource.AtlasifyQuery query;
    String[] featureNameList;
    SRMetric sr;
    SRCacheDao srCacheDao = new SRCacheDao();

    public SRBackgroundLoading(AtlasifyResource.AtlasifyQuery query, String[] featureNameList, SRMetric sr){
        this.query = query;
        this.featureNameList = featureNameList;
        this.sr = sr;
    }
    public void run(){
        System.out.println("Starting SR Background loading for keyword " + query.getKeyword());
        for (int i = 0; i < featureNameList.length; i++) {
            if(!srCacheDao.checkSRExist(query.getKeyword(), featureNameList[i])){
                try {
                    double value = sr.similarity(query.getKeyword(), featureNameList[i], false).getScore();
                    srCacheDao.saveSR(query.getKeyword(), featureNameList[i], value);
                }
                catch (Exception e){
                    e.printStackTrace();
                    continue;
                }
            }
        }
        System.out.println("Finished SR Background loading for keyword " + query.getKeyword());

    }
}
