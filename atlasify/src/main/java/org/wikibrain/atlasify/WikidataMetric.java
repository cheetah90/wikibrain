package org.wikibrain.atlasify;

import gnu.trove.set.TIntSet;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.sr.*;
import org.wikibrain.sr.disambig.Disambiguator;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.wikidata.LocalWikidataStatement;
import org.wikibrain.wikidata.WikidataDao;
import org.apache.commons.lang.WordUtils;

import java.io.IOException;
import java.util.*;

import org.wikibrain.wikidata.WikidataEntity;
import org.xml.sax.*;

import javax.xml.parsers.*;
import org.w3c.dom.*;

/**
 * Created by Josh on 2/2/15.
 */
public class WikidataMetric extends DataMetric {
    private WikidataDao wdDao;
    private String defaultExplanation;
    // These maps keep track of various property ids and their corresponding explanation
    private Map<Integer, String> explanations;
    private Map<Integer, String> media;

    public WikidataMetric(String name, Language language, LocalPageDao pageHelper, Disambiguator disambiguator, WikidataDao wikidataDao) throws ConfigurationException, ParserConfigurationException, SAXException, IOException {
        super(name, language, pageHelper, disambiguator);
        wdDao = wikidataDao;

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder document = factory.newDocumentBuilder();
        Document dom = document.parse("dat/wikidata/WikidataExplanationPhrases.xml");
        org.w3c.dom.Element xml = dom.getDocumentElement();

        explanations = new HashMap<Integer, String>();
        media = new HashMap<Integer, String>();

        NodeList list = xml.getElementsByTagName("Phrase");
        for (int i = 0; i < list.getLength(); i++) {
            org.w3c.dom.Element phrase = (org.w3c.dom.Element) list.item(i);

            String id = phrase.getElementsByTagName("ID").item(0).getFirstChild().getNodeValue();

            Node mediaList;
            if ((mediaList = phrase.getElementsByTagName("Media").item(0)) != null) {
                String mediaType = mediaList.getFirstChild().getNodeValue();
                Integer intID = Integer.parseInt(id);
                media.put(intID, mediaType);
            } else {
                String text = phrase.getElementsByTagName("Text").item(0).getFirstChild().getNodeValue();
                if (id.equals("default")) {
                    defaultExplanation = text;
                } else {
                    Integer intID = Integer.parseInt(id);
                    explanations.put(intID, text);
                }
            }
        }
    }

    @Override
    public LocalPage convertToLocalPageTitle(String s) {
        try {
            return getLocalPageDao().getByTitle(getLanguage(), s);
        } catch (DaoException e) {
            return null;
        }
    }

    @Override
    public String convertFromLocalPageTitle(LocalPage l) {
        return l.getTitle().getCanonicalTitle();
    }

    @Override
    public List<Tuple<String, List<String>>> statmentsForQuery(String s, String prop) {
        try {
            LocalPage page = convertToLocalPageTitle(s);
            Map<String, List<LocalWikidataStatement>> statements = wdDao.getLocalStatements(page);

            List<Tuple<String, List<String>>> results = new ArrayList<Tuple<String, List<String>>>();

            for (String statement : statements.keySet()) {
                List<LocalWikidataStatement> wikiStatements = statements.get(statement);

                for (LocalWikidataStatement wikiStatement : wikiStatements) {
                    if (media.containsKey(wikiStatement.getStatement().getProperty().getId())) {
                        continue;
                    }
                    if (wikiStatement.getStatement().getProperty().getId() == 20) {
                        int x = 1;
                    }
                    String valueString = wikiStatement.getValue();
                    try {
                        // This is a hacky way to search for spatial relationships to states or counties
                        Language lang = wikiStatement.getLang(); // lws.getLang
                        int cityPageId = wikiStatement.getStatement().getValue().getIntValue();
                        Map<String, List<LocalWikidataStatement>> cityStatements = wdDao.getLocalStatements(lang, WikidataEntity.Type.ITEM, cityPageId);

                        LocalWikidataStatement citylws = cityStatements.get("Commons category").get(0);
                        if (citylws != null && (citylws.getStatement().getValue().getStringValue().contains(valueString) || valueString=="unknown")) {
                            valueString = citylws.getValue();
                        }

                        // Try to get country
                        if (cityStatements.containsKey("country")) {
                            valueString = valueString + ", " + cityStatements.get("country").get(0).getValue();
                        }
                    } catch (Exception e) {
                        // It isn't a city
                        valueString = wikiStatement.getValue();
                    }

                    List<String> localStatements = new ArrayList<String>();
                    localStatements.add(valueString);



                    // The property is just a string containing the property ID
                    // This will get resolved when the explanations are generated
                    String property = wikiStatement.getStatement().getProperty().getId() + ";" + wikiStatement.getProperty();
                    results.add(new Tuple<String, List<String>>(property, localStatements));
                }
            }

            return results;
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return new ArrayList<Tuple<String, List<String>>>();
    }

    @Override
    public String formatStringForProperty(String s) {
        int seperatorIndex = s.indexOf(';');
        String idString = s.substring(0, seperatorIndex);
        String property = s.substring(seperatorIndex + 1);
        int id = Integer.parseInt(idString);
        String format = explanations.get(id);
        if (format == null) {
            if (property != null && property != "" && property != "null") {
                format = "%s has a " + property + " relationship with %s";
            } else {
                format = "%s has a relationship with %s";
            }
        }
        return format;
    }

    @Override
    public SRConfig getConfig() { return new SRConfig(); }
}
