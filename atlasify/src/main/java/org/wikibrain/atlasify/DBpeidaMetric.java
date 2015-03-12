package org.wikibrain.atlasify;

import org.apache.commons.lang.WordUtils;
import org.jooq.util.derby.sys.Sys;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.sr.BaseSRMetric;
import org.wikibrain.sr.disambig.Disambiguator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.*;

/**
 * Created by Josh on 3/2/15.
 */
public class DBpeidaMetric extends DataMetric {
    public DBpeidaMetric(String name, Language language, LocalPageDao pageHelper, Disambiguator disambiguator) {
        super(name, language, pageHelper, disambiguator);
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
            // First get the isValueOf properties
            String searchString = WordUtils.capitalize(s).replace(' ', '_');
            String propertyString = WordUtils.capitalize(prop).replace(' ', '_');
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

            URL url = new URL(location);
            URLConnection connection = url.openConnection();
            StringBuilder stringBuilder = new StringBuilder();
            BufferedReader rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            String line;
            while ((line = rd.readLine()) != null) {
                stringBuilder.append(line);
            }
            rd.close();

            List<Tuple<String, List<String>>> results = processJSON(stringBuilder.toString());

            // Now process the get the hasValue properties
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

            url = new URL(location);
            connection = url.openConnection();
            stringBuilder = new StringBuilder();
            rd = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            while ((line = rd.readLine()) != null) {
                stringBuilder.append(line);
            }
            rd.close();

            results.addAll(processJSON(stringBuilder.toString()));

            return results;
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }

        return new ArrayList<Tuple<String, List<String>>>();
    }

    private List<Tuple<String, List<String>>> processJSON(String string) {
        List<Tuple<String, List<String>>> statements = new ArrayList<Tuple<String, List<String>>>();

        JSONObject json = new JSONObject(string);
        JSONArray results = json.getJSONObject("results").getJSONArray("bindings");
        for (int i = 0; i < results.length(); i++) {
            try {
                JSONObject result = results.getJSONObject(i);
                JSONObject property = result.getJSONObject("property");
                JSONObject value;
                if (result.has("isValueOf")) {
                    value = result.getJSONObject("isValueOf");
                } else if (result.has("hasValue")) {
                    value = result.getJSONObject("hasValue");
                } else {
                    continue;
                }

                String propertyString = property.getString("value");
                propertyString = propertyString.substring(propertyString.lastIndexOf('/') + 1);
                if (propertyString.contains("#")) {
                    propertyString = propertyString.substring(propertyString.lastIndexOf('#') + 1);
                }
                String valueString = value.getString("value");
                valueString = valueString.substring(valueString.lastIndexOf('/') + 1).replace('_', ' ');

                List<String> values = new ArrayList<String>();
                values.add(valueString);
                statements.add(new Tuple<String, List<String>>(propertyString, values));
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
