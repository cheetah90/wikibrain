package org.wikibrain.cookbook.wikidata;

import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.dao.UniversalPageDao;
import org.wikibrain.sr.SRMetric;
import org.wikibrain.wikidata.WikidataDao;
import org.wikibrain.wikidata.WikidataValue;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Set;

/**
 * Created by toby on 7/15/15.
 */
public class BookSRCalculator {

    public static void main(String args[]) throws FileNotFoundException, IOException, ConfigurationException, DaoException {
        Env env = new EnvBuilder().build();
        Configurator conf = env.getConfigurator();
        UniversalPageDao upDao = conf.get(UniversalPageDao.class);
        LocalPageDao lpDao = conf.get(LocalPageDao.class);
        SRMetric sr = conf.get(SRMetric.class, "ensemble","language", "en");
        WikidataDao wdDao = conf.get(WikidataDao.class);
        Set<Integer> conceptSet = wdDao.conceptsWithValue("instance of", WikidataValue.forItem(5));
        int count = 0;
        for(Integer id : conceptSet){
            System.out.println(id);
            if(count ++ > 50)
                break;
        }

    }
}
