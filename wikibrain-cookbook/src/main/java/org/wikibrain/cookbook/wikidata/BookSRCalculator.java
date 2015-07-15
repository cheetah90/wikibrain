package org.wikibrain.cookbook.wikidata;

import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.dao.UniversalPageDao;
import org.wikibrain.core.lang.Language;
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
    private static SRMetric sr;
    private static UniversalPageDao upDao;
    private static LocalPageDao lpDao;
    public BookSRCalculator(Env env, Language lang) throws ConfigurationException{
        Configurator conf = env.getConfigurator();
        sr = conf.get(SRMetric.class, "ensemble", "language", lang.getLangCode());
        upDao =  conf.get(UniversalPageDao.class);
        lpDao = conf.get(LocalPageDao.class);
    }
    public double getSR(Integer universalId1, Integer universalId2, Language lang) throws DaoException{
        return sr.similarity(lpDao.getById(lang, upDao.getLocalId(lang, universalId1)).getTitle().getCanonicalTitle(), lpDao.getById(lang, upDao.getLocalId(lang, universalId2)).getTitle().getCanonicalTitle(), false).getScore();
    }

    public static void main(String args[]) throws ConfigurationException, DaoException {
        Language lang = Language.EN;
        Env env = EnvBuilder.envFromArgs(args);
        BookSRCalculator calculator = new BookSRCalculator(env, lang);
        calculator.getSR(89, 503, lang);
    }
}