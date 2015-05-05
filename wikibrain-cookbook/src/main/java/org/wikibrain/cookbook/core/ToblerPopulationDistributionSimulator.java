package org.wikibrain.cookbook.core;

import com.vividsolutions.jts.geom.Geometry;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.TIntSet;
import org.jooq.util.derby.sys.Sys;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.WikiBrainException;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.*;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.spatial.dao.SpatialContainmentDao;
import org.wikibrain.spatial.dao.SpatialDataDao;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * Created by toby on 5/5/15.
 */
public class ToblerPopulationDistributionSimulator {
    static private SpatialDataDao sdDao;
    static private UniversalPageDao upDao;
    static private LocalPageDao lpDao;
    static private SpatialContainmentDao scDao;
    static private Map<Integer, Geometry> countryMap;
    static private Map<Integer, Geometry> geometryMap;
    static private Map<Integer, TIntSet> countryContainedMap = new HashMap<Integer, TIntSet>();

    static public Geometry getRandomGeometry(List<LocalPage> localPageList) throws DaoException{
        Random rand = new Random();
        while(true){
            int randomNum = rand.nextInt(localPageList.size());
            LocalPage localPage = localPageList.get(randomNum);
            int univId = 0;
            try{
                univId = upDao.getByLocalPage(localPage).getUnivId();
            }
            catch (Exception e){
                continue;
            }
            if(geometryMap.containsKey(univId)){
                System.out.println("Get random geometry " + localPage.getTitle().getCanonicalTitle() + "  " + geometryMap.get(univId));
                return geometryMap.get(univId);
            }
            else
                continue;
        }

    }

    public static void main(String args[]) throws ConfigurationException, IOException, DaoException, WikiBrainException {
        Env env = EnvBuilder.envFromArgs(args);
        Configurator c = env.getConfigurator();
        //String[] languages = {"en", "nl", "de", "sv", "fr", "it", "ru", "es", "pl", "ja", "vi", "pt", "zh", "ca", "no", "fi", "cs", "ko", "ar", "hu" };
        String[] languages = {"simple", "zh", "ja"};
        sdDao = c.get(SpatialDataDao.class);
        upDao = c.get(UniversalPageDao.class);
        lpDao = c.get(LocalPageDao.class);
        sdDao = c.get(SpatialDataDao.class);
        upDao = c.get(UniversalPageDao.class);
        lpDao = c.get(LocalPageDao.class);
        scDao = c.get(SpatialContainmentDao.class);
        System.out.println("Getting all countries");
        countryMap = sdDao.getAllGeometriesInLayer("country");
        System.out.println("Finished getting all countries");
        System.out.println("Getting all wikidata points");
        geometryMap = sdDao.getAllGeometriesInLayer("wikidata");
        System.out.println("Finished getting all wikidata points");
        System.out.println("Getting contained POIs for countries");
        for(Map.Entry<Integer, Geometry> countryEntry : countryMap.entrySet()){
            Set<String> layerSet = new HashSet<String>();
            layerSet.add("wikidata");
            TIntSet resultSet = scDao.getContainedItemIds(countryEntry.getValue(), "earth", layerSet, SpatialContainmentDao.ContainmentOperationType.CONTAINMENT);
            countryContainedMap.put(countryEntry.getKey(), resultSet);
        }
        System.out.println("Finished getting contained POIs for countries");
        for(String lauange : languages){
            Map<Integer, Integer> countryPageCountMap = new HashMap<Integer, Integer>();
            Language lang = Language.getByLangCode(lauange);
            DaoFilter daoFilter = new DaoFilter();
            daoFilter.setLanguages(lang);
            System.out.println("\n\nGetting all local pages for " + lang);
            Iterable<LocalPage> localPageIterable = lpDao.get(daoFilter);
            List<Integer> localPageList = new ArrayList<Integer>();
            Iterator<LocalPage> localPageIterator = localPageIterable.iterator();
            while (localPageIterator.hasNext()){
                localPageList.add(localPageIterator.next().getLocalId());
            }
            System.out.println("Finished getting all local pages for " + lang);
            for(Map.Entry<Integer, Geometry> countryEntry : countryMap.entrySet()){
                int counter = 0;
                TIntIterator intIterator = countryContainedMap.get(countryEntry.getKey()).iterator();
                while(intIterator.hasNext()){
                    int univId = intIterator.next();
                    if(localPageList.contains(upDao.getLocalId(lang, univId)))
                        counter++;
                }
                countryPageCountMap.put(countryEntry.getKey(), counter);
            }
            for(Map.Entry<Integer, Integer> countryEntry : countryPageCountMap.entrySet()){
                System.out.println(upDao.getById(countryEntry.getKey()).getBestEnglishTitle(lpDao, true).getCanonicalTitle() + ": " + countryEntry.getValue());
            }

        }

    }
}
