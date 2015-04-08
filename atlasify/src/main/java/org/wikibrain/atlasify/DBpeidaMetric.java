package org.wikibrain.atlasify;

import org.apache.commons.lang.WordUtils;
import org.jooq.util.derby.sys.Sys;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.sr.BaseSRMetric;
import org.wikibrain.sr.disambig.Disambiguator;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

/**
 * Created by Josh on 3/2/15.
 */
public class DBpeidaMetric extends DataMetric {
    private Map<String, String> explanations;
    private Map<String, String> media;
    public DBpeidaMetric(String name, Language language, LocalPageDao pageHelper, Disambiguator disambiguator)throws ConfigurationException, ParserConfigurationException, SAXException, IOException {
        super(name, language, pageHelper, disambiguator);

        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        DocumentBuilder document = factory.newDocumentBuilder();
        Document dom = document.parse("dat/wikidata/WikidataExplanationPhrases.xml");
        org.w3c.dom.Element xml = dom.getDocumentElement();

        explanations = new HashMap<String, String>();
        media = new HashMap<String, String>();

        NodeList list = xml.getElementsByTagName("Phrase");
        for (int i = 0; i < list.getLength(); i++) {
            org.w3c.dom.Element phrase = (org.w3c.dom.Element) list.item(i);

            NodeList elements = phrase.getElementsByTagName("DBPediaID");
            for (int j = 0; j < elements.getLength(); j++) {
                String id = elements.item(j).getFirstChild().getNodeValue();

                Node mediaList;
                if ((mediaList = phrase.getElementsByTagName("Media").item(0)) != null) {
                    String mediaType = mediaList.getFirstChild().getNodeValue();
                    media.put(id, mediaType);
                } else {
                    String text = phrase.getElementsByTagName("Text").item(0).getFirstChild().getNodeValue();
                    explanations.put(id, text);
                }
            }
        }
    }

    @Override
    public String formatStringForProperty(String s) {
        if (explanations.containsKey(s)) {
            return explanations.get(s);
        } else {
            return super.formatStringForProperty(s);
        }
    }

    @Override
    public String convertFromLocalPageTitle(LocalPage l) {
        return l.getTitle().getCanonicalTitle();
    }

    @Override
    public LocalPage convertToLocalPageTitle(String s) {
        try {
            return getLocalPageDao().getByTitle(getLanguage(), s);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public List<Tuple<String, List<String>>> statmentsForQuery(String s, String prop) {
        try {
            // Convert titles to DBPedia titles
            String searchString = WordUtils.capitalize(s).replace(' ', '_');
            String propertyString = WordUtils.capitalize(prop).replace(' ', '_');
            // First get the isValueOf properties
            // Create the appropriate URL for
            // the first query
            String location = "http://dbpedia.org/sparql?query=" +
                    URLEncoder.encode("PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
                            "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
                            "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
                            "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                            "PREFIX foaf: <http://xmlns.com/foaf/0.1/>\n" +
                            "PREFIX dc: <http://purl.org/dc/elements/1.1/>\n" +
                            "PREFIX : <http://dbpedia.org/resource/>\n" +
                            "PREFIX dbpedia2: <http://dbpedia.org/property/>\n" +
                            "PREFIX dbpedia: <http://dbpedia.org/>\n" +
                            "PREFIX skos: <http://www.w3.org/2004/02/skos/core#>" +
                            "SELECT ?property ?hasValue ?isValueOf\n" +
                            "WHERE {\n" +
                            "  { ?isValueOf ?property <http://dbpedia.org/resource/" + searchString + "> }\n" +
                            "  FILTER regex(?isValueOf, \"" + propertyString + "\") \n" +
                            "}", "UTF-8") + "&format=json";

            // Get connection to the server and download the file
            URL url = new URL(location);
            URLConnection connection = url.openConnection();
            StringBuilder stringBuilder = new StringBuilder();
            BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            while ((line = rd.readLine()) != null) {
                stringBuilder.append(line);
            }
            rd.close();

            // process the results and add all of it to the results
            List<Tuple<String, List<String>>> results = processJSON(stringBuilder.toString());

            // Now process the get the hasValue properties
            // Again, create the URL
            location = "http://dbpedia.org/sparql?query=" +
                    URLEncoder.encode("PREFIX owl: <http://www.w3.org/2002/07/owl#>\n" +
                            "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" +
                            "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" +
                            "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
                            "PREFIX foaf: <http://xmlns.com/foaf/0.1/>\n" +
                            "PREFIX dc: <http://purl.org/dc/elements/1.1/>\n" +
                            "PREFIX : <http://dbpedia.org/resource/>\n" +
                            "PREFIX dbpedia2: <http://dbpedia.org/property/>\n" +
                            "PREFIX dbpedia: <http://dbpedia.org/>\n" +
                            "PREFIX skos: <http://www.w3.org/2004/02/skos/core#>" +
                            "SELECT ?property ?hasValue ?isValueOf\n" +
                            "WHERE {\n" +
                            "  { <http://dbpedia.org/resource/" + searchString + "> ?property ?hasValue }\n" +
                            "  FILTER regex(?hasValue, \"" + propertyString + "\")\n" +
                            "}", "UTF-8") + "&format=json";

            // Connect to the server and download the file
            url = new URL(location);
            connection = url.openConnection();
            stringBuilder = new StringBuilder();
            rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            while ((line = rd.readLine()) != null) {
                stringBuilder.append(line);
            }
            rd.close();

            // Process all the data, and add it to the results
            results.addAll(processJSON(stringBuilder.toString()));

            return results;
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return new ArrayList<Tuple<String, List<String>>>();
    }

    private List<Tuple<String, List<String>>> processJSON(String string) {
        List<Tuple<String, List<String>>> statements = new ArrayList<Tuple<String, List<String>>>();

        // Start breaking down the input and start to iterate over all the properties
        JSONObject json = new JSONObject(string);
        JSONArray results = json.getJSONObject("results").getJSONArray("bindings");
        for (int i = 0; i < results.length(); i++) {
            try {
                // Get the property and the value out of the JSON
                // If there is a problem we will continue with the next
                // iteration
                JSONObject result = results.getJSONObject(i);
                JSONObject property = result.getJSONObject("property");
                JSONObject value;
                boolean isValueOf = false;
                if (result.has("isValueOf")) {
                    value = result.getJSONObject("isValueOf");
                    isValueOf = true;
                } else if (result.has("hasValue")) {
                    value = result.getJSONObject("hasValue");
                } else {
                    continue;
                }

                // Do some processing to the property to remove any urls or # in the string
                String propertyString = property.getString("value");
                propertyString = propertyString.substring(propertyString.lastIndexOf('/') + 1);
                if (propertyString.contains("#")) {
                    propertyString = propertyString.substring(propertyString.lastIndexOf('#') + 1);
                }
                String valueString = value.getString("value");
                valueString = valueString.substring(valueString.lastIndexOf('/') + 1).replace('_', ' ');

                // Looks up property in a list to make sure it is useful to us
                // And not an image, abstract, etc.
                if (media.containsKey(propertyString)) {
                    continue;
                }

                // Will fix this later, these two properties produce a lot
                // of generally useless explanations, since they do not include
                // the candidate's name
                // TODO: Extract political candidate name
                if (valueString.contains("election") || valueString.contains("Court")) {
                    continue;
                }

                // Seperate value into words if it "SomethingLikeThis"
                // to "Something Like This"
                if (!valueString.contains(" ")) {
                    for (int j = 1; j < valueString.length() - 1; j++) {
                        char previousChar = valueString.charAt(j - 1);
                        char c = valueString.charAt(j);
                        char nextChar = valueString.charAt(j + 1);
                        boolean isBeginingOfWord = Character.isUpperCase(c) && Character.isLowerCase(nextChar);
                        boolean isNotAlreadyWord = !Character.isWhitespace(previousChar) && !Character.isUpperCase(previousChar);
                        if (isBeginingOfWord && isNotAlreadyWord) {
                            valueString = valueString.substring(0, j) + " " + valueString.substring(j, valueString.length());
                            j++;
                        }
                    }
                }

                /*boolean duplicate = false;
                if (valueString.contains(",")) {
                    String postfix = valueString.substring(valueString.indexOf(','));
                    for (Tuple<String, List<String>> otherExp : statements) {
                        String otherValueString = otherExp.y.get(0);
                        if (otherValueString.contains(",") && otherExp.x.equals(propertyString)) {
                            if (postfix.equals(otherValueString.substring(otherValueString.indexOf(',')))) {
                                duplicate = true;
                                break;
                            }
                        }
                    }
                }

                if (duplicate) {
                    // continue;
                }*/

                // Remove any parenthesized information from
                // i.e. Minneapolis (Minnesota) -> Minneapolis
                /*if (valueString.matches(".*\\(.*\\)")) {
                    String simplifiedString = valueString.substring(0, valueString.indexOf('(')).trim();
                    if (simplifiedString.length() > 0) {
                        valueString = simplifiedString;
                    }
                }*/

                // Wrap all the data into the data structure
                List<String> values = new ArrayList<String>();
                values.add(valueString);
                Tuple<String, List<String>> tuple = new Tuple<String, List<String>>(propertyString, values);
                if (isValueOf) {
                    tuple.reversed = true;
                }
                statements.add(tuple);
            } catch (Exception err) {
                // System.out.println(err.getMessage());
            }
        }

        return statements;
    }



    @Override
    public SRConfig getConfig() {
        return new SRConfig();
    }
}
