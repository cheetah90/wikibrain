package org.wikibrain.cookbook.core;

import com.vividsolutions.jts.geom.Geometry;
import org.jooq.util.derby.sys.Sys;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.*;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.model.LocalPage;
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
    static private Map<Integer, Geometry> countryMap;
    static private Map<Integer, Geometry> geometryMap;

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

    public static void main(String args[]) throws ConfigurationException, IOException, DaoException {
        Env env = EnvBuilder.envFromArgs(args);
        Configurator c = env.getConfigurator();
        //String[] languages = {"en", "nl", "de", "sv", "fr", "it", "ru", "es", "pl", "ja", "vi", "pt", "zh", "ca", "no", "fi", "cs", "ko", "ar", "hu" };
        String[] languages = {"simple"};
        sdDao = c.get(SpatialDataDao.class);
        upDao = c.get(UniversalPageDao.class);
        lpDao = c.get(LocalPageDao.class);
        sdDao = c.get(SpatialDataDao.class);
        upDao = c.get(UniversalPageDao.class);
        lpDao = c.get(LocalPageDao.class);
        System.out.println("Getting all countries");
        countryMap = sdDao.getAllGeometriesInLayer("country");
        System.out.println("Finished getting all countries");
        System.out.println("Getting all wikidata points");
        geometryMap = sdDao.getAllGeometriesInLayer("wikidata");
        System.out.println("Finished getting all wikidata points");
        for(String lauange : languages){
            Language lang = Language.getByLangCode(lauange);
            DaoFilter daoFilter = new DaoFilter();
            daoFilter.setLanguages(lang);
            System.out.println("Getting all local pages");
            Iterable<LocalPage> localPageIterable = lpDao.get(daoFilter);
            List<LocalPage> localPageList = new ArrayList<LocalPage>();
            Iterator<LocalPage> localPageIterator = localPageIterable.iterator();
            while (localPageIterator.hasNext()){
                localPageList.add(localPageIterator.next());
            }
            System.out.println("Finished getting all local pages");
            int count = 0;

            while(count ++ < 10){
                getRandomGeometry(localPageList);
            }

        }

    }
}
