package org.wikibrain.atlasify;

import au.com.bytecode.opencsv.CSVWriter;
import gnu.trove.map.TIntIntMap;
import org.apache.commons.math3.analysis.function.Min;
import org.apache.commons.math3.linear.BlockRealMatrix;
import org.apache.commons.math3.linear.RealMatrix;
import org.joda.time.DateTime;
import org.jooq.util.derby.sys.Sys;
import org.json.JSONArray;
import org.json.JSONObject;
import org.mockito.cglib.core.Local;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.lang.LocalId;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.core.model.NameSpace;
import org.wikibrain.core.model.Title;
import org.wikibrain.pageview.PageViewDao;
import org.wikibrain.sr.SRMetric;
import org.wikibrain.utils.WpCollectionUtils;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;
import org.wikibrain.wikidata.LocalWikidataStatement;
import org.wikibrain.wikidata.WikidataDao;

import javax.ws.rs.core.Response;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Created by Josh on 4/2/15.
 */
public class AtlasifyGameGenerator {
    public static void main(String args[]) throws ConfigurationException, DaoException, IOException {
        // Get the pageview dao
        Language lang = Language.SIMPLE;
        Env env = EnvBuilder.envFromArgs(args);
        Configurator conf = env.getConfigurator();
        PageViewDao viewDao = env.getConfigurator().get(PageViewDao.class);
        LocalPageDao pageDao = conf.get(LocalPageDao.class);
        SRMetric sr = conf.get(SRMetric.class, "ensemble", "language", lang.getLangCode());
        WikidataDao wdDao = conf.get(WikidataDao.class);
        LocalPageDao lpDao = conf.get(LocalPageDao.class);

        // Load the countries data
        Set<String> countries = new HashSet<String>();
        BufferedReader reader = new BufferedReader(new FileReader("country.json"));
        StringBuilder stringBuilder = new StringBuilder();
        String s;
        while ((s = reader.readLine()) != null) {
            stringBuilder.append(s);
        }
        JSONObject geoJSON = new JSONObject(stringBuilder.toString());
        JSONArray objects = geoJSON.getJSONArray("features");
        for (int i = 0; i < objects.length(); i++) {
            countries.add(objects.getJSONObject(i).getJSONObject("properties").getString("NAME"));
        }

        // Load the Periodic Table data
        Set<String> periodicTable = new HashSet<String>();
        reader = new BufferedReader(new FileReader("periodicTable.json"));
        stringBuilder = new StringBuilder();
        while ((s = reader.readLine()) != null) {
            stringBuilder.append(s);
        }
        geoJSON = new JSONObject(stringBuilder.toString());
        objects = geoJSON.getJSONArray("features");
        for (int i = 0; i < objects.length(); i++) {
            periodicTable.add(objects.getJSONObject(i).getJSONObject("properties").getString("NAME"));
        }

        // Load the Senate data
        Set<String> senate = new HashSet<String>();
        reader = new BufferedReader(new FileReader("senate.json"));
        stringBuilder = new StringBuilder();
        while ((s = reader.readLine()) != null) {
            stringBuilder.append(s);
        }
        geoJSON = new JSONObject(stringBuilder.toString());
        objects = geoJSON.getJSONArray("features");
        for (int i = 0; i < objects.length(); i++) {
            senate.add(objects.getJSONObject(i).getJSONObject("properties").getString("NAME"));
        }

        // Download and import pageview stats if necessary
        DateTime start = new DateTime(2015, 3, 30, 0, 0, 0);
        DateTime end   = new DateTime(2015, 4, 1, 0, 0, 0);
        viewDao.ensureLoaded(start, end, env.getLanguages());

        start = new DateTime(2015, 4, 2, 2, 0, 0);
        end   = new DateTime(2015, 4, 7, 23, 0, 0);
        viewDao.ensureLoaded(start, end, env.getLanguages());

        // Retrieve counts for all pageviews
        TIntIntMap allViews = viewDao.getAllViews(lang, start, end);
        int pageIds[] = WpCollectionUtils.sortMapKeys(allViews, true);
        ArrayList<LocalPage> topPages = new ArrayList<LocalPage>();
        DateFormat yearFormatter = new SimpleDateFormat("YYYY");
        DateFormat dateFormatter = new SimpleDateFormat("MMM D");
        DateFormat monthFormatter = new SimpleDateFormat("MMM");
        System.out.println("Top articles");

        int maximumArticleToProcess = 6500;
        mainLoop: for (int i = 0; i < Math.min(maximumArticleToProcess, allViews.size()); i++) {
            try {
                LocalPage page = pageDao.getById(lang, pageIds[i]);
                int n = allViews.get(pageIds[i]);

                String title = page.getTitle().getCanonicalTitle();

                // Make sure page isn't a main page or list page
                if (title.contains("Main Page") || title.contains("List of")) {
                    System.out.println("SKIP PAGE: " + title);
                    continue mainLoop;
                }
                // Make sure it isn't a date
                try {
                    dateFormatter.parse(title);
                    // Must be a date
                    System.out.println("SKIP DATE: " + title);
                    continue mainLoop;
                } catch (Exception e) {
                    // Not a date, do nothing
                }
                // Make sure it isn't a month
                try {
                    monthFormatter.parse(title);
                    // Must be a month
                    System.out.println("SKIP MONTH: " + title);
                    continue mainLoop;
                } catch (Exception e) {
                    // Not a month, do nothing
                }
                // Make sure it isn't a year
                try {
                    yearFormatter.parse(title);
                    // Must be a date
                    System.out.println("SKIP YEAR: " + title);
                    continue mainLoop;
                } catch (Exception e) {
                    // Not a date, do nothing
                }
                // Make sure it isn't a decade i.e. 1920s
                try {
                    yearFormatter.parse(title.substring(0, title.lastIndexOf('s')));
                    // Must be a date
                    System.out.println("SKIP CENTURY: " + title);
                    continue mainLoop;
                } catch (Exception e) {
                    // Not a date, do nothing
                }

                // We should try to keep this clean
                if (sr.similarity(title, "sex", false).getScore() > 0.65) {
                    System.out.println("SKIP SEX: " + title);
                    continue mainLoop;
                }

                // Make sure this isn't part of the counties
                for (String country : countries) {
                    if (title.contains(country)) {
                        System.out.println("SKIP COUNTRY: " + title);
                        continue mainLoop;
                    }
                }

                // Make sure this isn't part of the period table
                for (String element : periodicTable) {
                    if (title.contains(element)) {
                        System.out.println("SKIP ELEMENT: " + title);
                        continue mainLoop;
                    }
                }

                // Make sure this isn't part of the senate
                for (String senator : senate) {
                    if (title.contains(senator)) {
                        System.out.println("SKIP SENATOR: " + title);
                        continue mainLoop;
                    }
                }

                // Valid page
                System.out.format("%d. %s (nviews=%d)\n", (i + 1), title, n);
                topPages.add(page);
            } catch (Exception e) {
                e.printStackTrace();
                continue mainLoop;
            }
        }

        String countryPageTitle = "game_page_titles_country.csv";
        String periodicTablePageTitle = "game_page_titles_periodic_table.csv";
        String senatePageTitle = "game_page_titles_senate.csv";
        CSVWriter countryWriter = new CSVWriter(new FileWriter(new File(countryPageTitle), false), ',');
        CSVWriter periodicTableWrite = new CSVWriter(new FileWriter(new File(periodicTablePageTitle), false), ',');
        CSVWriter senateWriter = new CSVWriter(new FileWriter(new File(senatePageTitle), false), ',');

        System.out.println("Finished processing titles\n\tFound=" + Math.min(maximumArticleToProcess, allViews.size()) + ", Retained=" + topPages.size());
        System.out.println("Calculating SR");

        // Load all the SR Data while writing value to file
        ArrayList<double[]> countrySR = new ArrayList<double[]>();
        ArrayList<double[]> periodicTableSR = new ArrayList<double[]>();
        ArrayList<double[]> senateSR = new ArrayList<double[]>();
        int currentCount = 0;
        for (LocalPage p : topPages) {
            try {
                String[] line = new String[1];
                String title = p.getTitle().getCanonicalTitle();
                line[0] = title;

                System.out.println(title + "(" + currentCount + "/" + maximumArticleToProcess + ")");
                currentCount++;

                // Compute SR country
                System.out.println("\tCountry SR");
                int i = 0;
                double[] countrySRValues = new double[countries.size()];
                double highestCountrySR = 0.0;
                String highestCountry = "";
                LocalId id = new LocalId(lang, lpDao.getIdByTitle(title, lang, NameSpace.ARTICLE));
                Map<LocalId, Double> srMap = AtlasifyResource.accessNorthwesternAPI(id, -1, false);
                for (String country : countries) {
                    try {
                        LocalId countryId = new LocalId(lang, lpDao.getIdByTitle(country, lang, NameSpace.ARTICLE));
                        countrySRValues[i] = srMap.get(countryId);
                    } catch (Exception e) {
                        countrySRValues[i] = 0.0;
                        System.out.println("\tERROR: calculating sr between " + title + " and " + country +
                                "\n\tUse an SR Value of zero");
                        e.printStackTrace();
                    }
                    if (highestCountrySR < countrySRValues[i]) {
                        highestCountrySR = countrySRValues[i];
                        highestCountry = country;
                    }
                    i++;
                }

                // Periodic Table SR
                System.out.println("\tPeriodic Table SR");
                i = 0;
                double[] periodicTableSRValues = new double[periodicTable.size()];
                double highestPeriodicTableSR = 0.0;
                String highestElement = "";
                for (String element : periodicTable) {
                    try {
                        LocalId elementId = new LocalId(lang, lpDao.getIdByTitle(element, lang, NameSpace.ARTICLE));
                        periodicTableSRValues[i] = srMap.get(elementId);
                    } catch (Exception e) {
                        periodicTableSRValues[i] = 0.0;
                        System.out.println("\tERROR: calculating sr between " + title + " and " + element +
                                "\n\tUse an SR Value of zero");
                        e.printStackTrace();
                    }

                    if (highestPeriodicTableSR < periodicTableSRValues[i]) {
                        highestPeriodicTableSR = periodicTableSRValues[i];
                        highestElement = element;
                    }
                    i++;
                }

                // Senate SR
                System.out.println("\tSenate SR");
                i = 0;
                double[] senateSRValues = new double[periodicTable.size()];
                double highestSenateSR = 0.0;
                String highestSenator = "";
                for (String senator : senate) {
                    try {
                        LocalId senatorId = new LocalId(lang, lpDao.getIdByTitle(senator, lang, NameSpace.ARTICLE));
                        senateSRValues[i] = srMap.get(senatorId);
                    } catch (Exception e) {
                        senateSRValues[i] = 0.0;
                        System.out.println("\tERROR: calculating sr between " + title + " and " + senator +
                                "\n\tUse an SR Value of zero");
                        e.printStackTrace();
                    }

                    if (highestSenateSR < senateSRValues[i]) {
                        highestSenateSR = senateSRValues[i];
                        highestSenator = senator;
                    }
                    i++;
                }

                // Make sure that there is at least a high enough SR to make the game interesting
                if (highestCountrySR < 0.7) {
                    System.out.println("\tSKIP: Too low of country SR (" + highestCountry + " : " + highestCountrySR + ")");
                } else {
                    System.out.println("\tHighest country SR: " + highestCountry + " (" + highestCountrySR + ")");
                    countryWriter.writeNext(line);
                    countrySR.add(countrySRValues);
                }

                if (highestPeriodicTableSR < 0.7) {
                    System.out.println("\tSKIP: Too low of element SR (" + highestElement + " : " + highestPeriodicTableSR + ")");
                } else {
                    System.out.println("\tHighest element SR: " + highestElement + " (" + highestPeriodicTableSR + ")");
                    periodicTableWrite.writeNext(line);
                    periodicTableSR.add(periodicTableSRValues);
                }

                if (highestSenateSR < 0.7) {
                    System.out.println("\tSKIP: Too low of senate SR (" + highestSenator + " : " + highestSenateSR + ")");
                } else {
                    System.out.println("\tHighest senate SR: " + highestSenator + " (" + highestSenateSR + ")");
                    senateWriter.writeNext(line);
                    senateSR.add(senateSRValues);
                }
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Error occurred calculating SR");
            }
        }

        countryWriter.close();
        periodicTableWrite.close();
        senateWriter.close();

        System.out.println("Finished processing SR\n\tFound=" + topPages.size());
        System.out.println("\tCountries=" + countrySR.size());
        System.out.println("\tElements=" + periodicTableSR.size());
        System.out.println("\tSenators=" + senateSR.size());
        System.out.println("Calculating correlation");

        // Create correlation matrices
        PearsonsCorrelation corr = new PearsonsCorrelation();

        System.out.println("Country Correlation");
        RealMatrix countryCorrelation = new BlockRealMatrix(countrySR.size(), countrySR.size());
        for (int i = 0; i < countrySR.size(); i++) {
            for (int j = 0; j < countrySR.size(); j++) {
                countryCorrelation.setEntry(i, j, corr.correlation(countrySR.get(i), countrySR.get(j)));
            }

            if (i % 300 == 0 && i > 0) {
                System.out.println("\tFinished Processing Row " + i);
            }
        }

        System.out.println("Periodic Table Correlation");
        RealMatrix periodicTableCorrelation = new BlockRealMatrix(periodicTableSR.size(), periodicTableSR.size());
        for (int i = 0; i < periodicTableSR.size(); i++) {
            for (int j = 0; j < periodicTableSR.size(); j++) {
                periodicTableCorrelation.setEntry(i, j, corr.correlation(periodicTableSR.get(i), periodicTableSR.get(j)));
            }

            if (i % 300 == 0 && i > 0) {
                System.out.println("\tFinished Processing Row " + i);
            }
        }

        System.out.println("Senate Correlation");
        RealMatrix senateCorrelation = new BlockRealMatrix(senateSR.size(), senateSR.size());
        for (int i = 0; i < senateSR.size(); i++) {
            for (int j = 0; j < senateSR.size(); j++) {
                senateCorrelation.setEntry(i, j, corr.correlation(senateSR.get(i), senateSR.get(j)));
            }

            if (i % 300 == 0 && i > 0) {
                System.out.println("\tFinished Processing Row " + i);
            }
        }

        System.out.println("Saving Tables to Disk");

        // Write it to disk
        String countryCorrTable = "game_corr_table_country.csv";
        String periodicTableCorrTable = "game_corr_table_periodic_table.csv";
        String senateCorrTable = "game_corr_table_senate.csv";

        countryWriter = new CSVWriter(new FileWriter(new File(countryCorrTable), false), ',');
        for (int i = 0; i < countryCorrelation.getRowDimension(); i++) {
            String[] strings = new String[countryCorrelation.getColumnDimension()];
            for (int j = 0; j < countryCorrelation.getColumnDimension(); j++) {
                strings[j] = countryCorrelation.getEntry(i, j) + "";
            }

            countryWriter.writeNext(strings);
        }
        countryWriter.close();

        periodicTableWrite = new CSVWriter(new FileWriter(new File(periodicTableCorrTable), false), ',');
        for (int i = 0; i < periodicTableCorrelation.getRowDimension(); i++) {
            String[] strings = new String[periodicTableCorrelation.getColumnDimension()];
            for (int j = 0; j < periodicTableCorrelation.getColumnDimension(); j++) {
                strings[j] = periodicTableCorrelation.getEntry(i, j) + "";
            }

            periodicTableWrite.writeNext(strings);
        }
        periodicTableWrite.close();

        senateWriter = new CSVWriter(new FileWriter(new File(senateCorrTable), false), ',');
        for (int i = 0; i < senateCorrelation.getRowDimension(); i++) {
            String[] strings = new String[senateCorrelation.getColumnDimension()];
            for (int j = 0; j < senateCorrelation.getColumnDimension(); j++) {
                strings[j] = senateCorrelation.getEntry(i, j) + "";
            }

            senateWriter.writeNext(strings);
        }
        senateWriter.close();

        System.out.println("Finished Creating Game Correlation");
    }
}
