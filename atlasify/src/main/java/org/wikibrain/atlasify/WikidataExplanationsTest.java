package org.wikibrain.atlasify;

import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.model.Title;
import org.wikibrain.sr.Explanation;
import org.wikibrain.sr.disambig.Disambiguator;
import org.wikibrain.atlasify.WikidataMetric;
import org.wikibrain.atlasify.DBpeidaMetric;
import org.wikibrain.wikidata.WikidataDao;

import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;

/**
 * Created by Josh on 2/4/15.
 */

public class WikidataExplanationsTest {
    static private LocalPageDao lpDao;
    static private WikidataDao wdDao;
    public static void main(String args[]) throws ConfigurationException, DaoException, ParserConfigurationException, SAXException, IOException {
        Env env = new EnvBuilder().build();
        Configurator conf = env.getConfigurator();
        lpDao = conf.get(LocalPageDao.class);
        wdDao = conf.get(WikidataDao.class);
        Language simple = Language.getByLangCode("simple");
        
        HashMap parameters = new HashMap();
        parameters.put("language", "simple");
        Disambiguator dis = conf.get(Disambiguator.class, "similarity", parameters);

        metric = new WikidataMetric("dbpedia", Language.SIMPLE, lpDao, dis, wdDao);
        metric2 = new DBpeidaMetric("wikidata", Language.SIMPLE, lpDao, dis);

        addExplanations("Minnesota", "Canada");
        addExplanations("Mexico", "United States");
        addExplanations("Minnesota", "United States");
        addExplanations("University of Minnesota", "Minnesota");

        addExplanations("Minneapolis", "Chile");
        addExplanations("Minneapolis", "Minnesota");
        addExplanations("Minneapolis", "United States");
        addExplanations("Minneapolis", "Canada");

        addExplanations("Benjamin Franklin", "Massachusetts");
        addExplanations("Michael Jackson", "California");
        addExplanations("Michael Jackson", "Indiana");

        printExplanations();
    }

    static private List<Explanation> explanations = new ArrayList<Explanation>();
    static private WikidataMetric metric;
    static private DBpeidaMetric metric2;

    private static void addExplanations(String itemOne, String itemTwo) throws DaoException {
        try {
            int page1 = lpDao.getIdByTitle(new Title(itemOne, Language.SIMPLE));
            int page2 = lpDao.getIdByTitle(new Title(itemTwo, Language.SIMPLE));
            explanations.addAll(metric.similarity(page1, page2, true).getExplanations());
        } catch (Exception e) {
            explanations.addAll(metric.similarity(itemOne, itemTwo, true).getExplanations());
        }
        try {
            int page1 = lpDao.getIdByTitle(new Title(itemOne, Language.SIMPLE));
            int page2 = lpDao.getIdByTitle(new Title(itemTwo, Language.SIMPLE));
            explanations.addAll(metric2.similarity(page1, page2, true).getExplanations());
        } catch (Exception e) {
            explanations.addAll(metric2.similarity(itemOne, itemTwo, true).getExplanations());
        }
    }

    private static void printExplanations() {
        for (Explanation e : explanations) {
            System.out.println(String.format(e.getFormat(), e.getInformation().toArray()));
        }
    }
}

