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
        addExplanations("Minneapolis", "St. Paul");
        addExplanations("Minneapolis", "United States");
        addExplanations("Minneapolis", "Canada");
        addExplanations("Minneapolis", "Minnesota");

        addExplanations("Benjamin Franklin", "Massachusetts");
        addExplanations("Michael Jackson", "California");
        addExplanations("Michael Jackson", "Indiana");

        printExplanations();
    }

    static private List<String> explanations = new ArrayList<String>();
    static private WikidataMetric metric;
    static private DBpeidaMetric metric2;

    private static void addExplanations(String itemOne, String itemTwo) throws DaoException {
        explanations.add("Wikidata: " + itemOne + " -> " + itemTwo);
        try {
            int page1 = lpDao.getIdByTitle(new Title(itemOne, Language.SIMPLE));
            int page2 = lpDao.getIdByTitle(new Title(itemTwo, Language.SIMPLE));
            for (Explanation e : metric.similarity(page1, page2, true).getExplanations()) {
                explanations.add(String.format(e.getFormat(), e.getInformation().toArray()));
            }
        } catch (Exception err) {
            for (Explanation e : metric.similarity(itemOne, itemTwo, true).getExplanations()) {
                explanations.add(String.format(e.getFormat(), e.getInformation().toArray()));
            }
        }
        explanations.add("\nDBPedia: " + itemOne + " -> " + itemTwo);
        try {
            int page1 = lpDao.getIdByTitle(new Title(itemOne, Language.SIMPLE));
            int page2 = lpDao.getIdByTitle(new Title(itemTwo, Language.SIMPLE));
            for (Explanation e : metric2.similarity(page1, page2, true).getExplanations()) {
                explanations.add(String.format(e.getFormat(), e.getInformation().toArray()));
            }
        } catch (Exception err) {
            for (Explanation e : metric2.similarity(itemOne, itemTwo, true).getExplanations()) {
                explanations.add(String.format(e.getFormat(), e.getInformation().toArray()));
            }
        }

        explanations.add("\n----------\n");
    }

    private static void printExplanations() {
        for (String e : explanations) {
            System.out.println(e);
        }
    }
}

