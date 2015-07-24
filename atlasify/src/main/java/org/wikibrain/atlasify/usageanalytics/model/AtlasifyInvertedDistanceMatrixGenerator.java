package org.wikibrain.atlasify.usageanalytics.model;

import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;
import org.geotools.referencing.GeodeticCalculator;
import org.json.JSONObject;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.dao.UniversalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.lang.LocalId;
import org.wikibrain.spatial.dao.SpatialDataDao;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.*;

/**
 * Created by toby on 7/23/15.
 */
public class AtlasifyInvertedDistanceMatrixGenerator {
    public static Map<Integer, String> countryLocalIdNameMap;
    public static Map<Integer, String> countryUniIdNameMap;
    public static Map<Integer, Geometry> countryUniIdGeomMap;
    public static Map<Integer, Geometry> countryLocalIdGeomMap;
    public static Map<Map.Entry<Integer, Integer>, Double> countryLocalIdLocalIdDistanceMap;
    private static UniversalPageDao upDao;
    private static LocalPageDao lpDao;
    private static SpatialDataDao sdDao;
    public AtlasifyInvertedDistanceMatrixGenerator() throws ConfigurationException, FileNotFoundException, DaoException{
        Env env = new EnvBuilder().build();
        Configurator conf = env.getConfigurator();
        countryLocalIdNameMap = new HashMap<Integer, String>();
        countryUniIdGeomMap = new HashMap<Integer, Geometry>();
        countryUniIdNameMap = new HashMap<Integer, String>();
        countryLocalIdGeomMap = new HashMap<Integer, Geometry>();
        countryLocalIdLocalIdDistanceMap = new HashMap<Map.Entry<Integer, Integer>, Double>();
        upDao = conf.get(UniversalPageDao.class);
        lpDao = conf.get(LocalPageDao.class);
        sdDao = conf.get(SpatialDataDao.class);
        String s = new Scanner( new File("countries.js") ).useDelimiter("\\A").next();
        JSONObject jsonObject = new JSONObject(s);
        Iterator<String> nameItr = jsonObject.keys();
        while(nameItr.hasNext()){
            try {
                String name = nameItr.next();
                countryLocalIdNameMap.put(jsonObject.getInt(name), name);
            }
            catch (Exception e){
                e.printStackTrace();
                continue;
            }
        }
        for(Integer localId : countryLocalIdNameMap.keySet()){
            try{
                Integer uniId = upDao.getByLocalPage(lpDao.getById(Language.EN, localId)).getUnivId();
                Geometry geom = sdDao.getGeometry(uniId, "country");
                countryUniIdNameMap.put(uniId, countryLocalIdNameMap.get(localId));
                countryUniIdGeomMap.put(uniId, geom);
                countryLocalIdGeomMap.put(localId, geom);
            }
            catch (Exception e){
                System.out.println("Can't get geometry for " + countryLocalIdGeomMap.get(localId));
            }
        }
        System.out.println("Finished constructing index maps");
        GeodeticCalculator geoCalc = new GeodeticCalculator();
        for(Integer localId1 : countryLocalIdGeomMap.keySet()){
            for(Integer localId2 : countryLocalIdGeomMap.keySet()){
                Point p1 = countryLocalIdGeomMap.get(localId1).getCentroid();
                Point p2 = countryLocalIdGeomMap.get(localId2).getCentroid();
                geoCalc.setStartingGeographicPoint(p1.getX(), p1.getY());
                geoCalc.setDestinationGeographicPoint(p2.getX(), p2.getY());
                countryLocalIdLocalIdDistanceMap.put(new AbstractMap.SimpleEntry<Integer, Integer>(localId1, localId2), geoCalc.getOrthodromicDistance()/1000);
            }
        }
        System.out.println("Finished constructing distance matrix");

    }
    public double CalculateMoransI(String keyword) throws DaoException, Exception{
        LocalId keywordId = lpDao.getByTitle(Language.EN, keyword).toLocalId();
        Map<LocalId, Double> nuResults = AtlasifyKeywordStatCalculator.accessNorthwesternAPI(keywordId, -1, true);
        Map<Integer, Double> countryLocalIdSRMap = new HashMap<Integer, Double>();
        for(Integer countryLocalId : countryLocalIdSRMap.keySet()){
            try{
                countryLocalIdSRMap.put(countryLocalId, nuResults.get(new LocalId(Language.EN, countryLocalId)));
            }
            catch (Exception e){
                //Consider SR to be 0 if we have no SR
                countryLocalIdSRMap.put(countryLocalId, 0.0);
            }
        }





    }





}
