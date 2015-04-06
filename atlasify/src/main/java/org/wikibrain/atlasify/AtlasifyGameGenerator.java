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
import org.wikibrain.pageview.PageViewDao;
import org.wikibrain.sr.SRMetric;
import org.wikibrain.utils.WpCollectionUtils;

import org.apache.commons.math3.stat.correlation.PearsonsCorrelation;

import javax.ws.rs.core.Response;
import java.io.*;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.Month;
import java.time.MonthDay;
import java.time.Year;
import java.time.format.DateTimeFormatter;
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

        // Download and import pageview stats if necessary
        DateTime start = new DateTime(2015, 3, 30, 0, 0, 0);
        DateTime end   = new DateTime(2015, 4, 3, 23, 0, 0);
        viewDao.ensureLoaded(start, end, env.getLanguages());

        // Load the countries data
        Set<String> countries = new HashSet<String>();
        byte[] encoded = Files.readAllBytes(Paths.get("country.json"));
        JSONObject geoJSON = new JSONObject(new String(encoded, StandardCharsets.UTF_8));
        JSONArray objects = geoJSON.getJSONArray("features");
        for (int i = 0; i < objects.length(); i++) {
            countries.add(objects.getJSONObject(i).getJSONObject("properties").getString("NAME"));
        }

        // Retrieve counts for all pageviews
        TIntIntMap allViews = viewDao.getAllViews(lang, start, end);
        int pageIds[] = WpCollectionUtils.sortMapKeys(allViews, true);
        ArrayList<LocalPage> topPages = new ArrayList<LocalPage>();
        DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("MMMM d");
        DateTimeFormatter yearFormat = DateTimeFormatter.ofPattern("yyyy");
        System.out.println("Top articles");
        for (int i = 0; i < Math.min(5000, allViews.size()); i++) {
            LocalPage page = pageDao.getById(lang, pageIds[i]);
            int n = allViews.get(pageIds[i]);

            String title = page.getTitle().getCanonicalTitle();

            // Make sure page isn't a main page or list page
            if (title.contains("Main Page") || title.contains("List of")) {
                System.out.println("SKIP PAGE: " + title);
                continue;
            }
            // Make sure it isn't a date
            try {
                MonthDay.parse(title, dateFormat);
                // Must be a date
                System.out.println("SKIP DATE: " + title);
                continue;
            } catch (Exception e) {
                // Not a date, do nothing
            }
            // Make sure it isn't a month
            try {
                Month.valueOf(title);
                // Must be a month
                System.out.println("SKIP MNTH: " + title);
                continue;
            } catch (Exception e) {
                // Not a month, do nothing
            }
            // Make sure it isn't a year
            try {
                Year.parse(title, yearFormat);
                // Must be a date
                System.out.println("SKIP YEAR: " + title);
                continue;
            } catch (Exception e) {
                // Not a date, do nothing
            }
            // Make sure it isn't a decade i.e. 1920s
            try {
                Year.parse(title.substring(0, title.lastIndexOf('s')), yearFormat);
                // Must be a date
                System.out.println("SKIP CNRY: " + title);
                continue;
            } catch (Exception e) {
                // Not a date, do nothing
            }
            // Make sure it isn't a country in our list
            if (countries.contains(title)) {
                System.out.println("SKIP CONT: " + title);
                continue;
            }

            // We should try to keep this clean
            if (sr.similarity(title, "sex", false).getScore() > 0.65) {
                System.out.println("SKIP SEX:  " + title);
                continue;
            }

            // Valid page
            System.out.format("%d. %s (nviews=%d)\n", (i + 1), title, n);
            topPages.add(page);
        }

        String pageTitle = "game_page_titles.csv";
        CSVWriter writer = new CSVWriter(new FileWriter(new File(pageTitle), false), ',');

        System.out.println("Finished processing titles\n\tFound=" + Math.min(3000, allViews.size()) + ", Retained=" + topPages.size());
        System.out.println("Calculating SR");

        // Load all the SR Data while writing value to file
        ArrayList<double[]> srValue = new ArrayList<double[]>();
        for (LocalPage p : topPages) {
            String[] line = new String[1];
            line[0] = p.getTitle().getCanonicalTitle();

            // Compute SR
            double[] srValues = new double[0];
            int i = 0;
            srValues = new double[countries.size()];
            double highestSR = 0.0;
            String highestCountry = "";
            for (String country : countries) {
                try {
                    srValues[i] = sr.similarity(p.getTitle().getCanonicalTitle(), country, false).getScore();
                } catch (Exception e) {
                    srValues[i] = 0.0;
                    System.out.println("ERROR: calculating sr between " + p.getTitle().getCanonicalTitle() + " and " + country +
                                        "\n\tUse an SR Value of zero");
                    e.printStackTrace();
                }
                if (highestSR < srValues[i]) {
                    highestSR = srValues[i];
                    highestCountry = country;
                }
                i++;
            }

            // Make sure that there is at least a high enough SR to make the game interesting
            if (highestSR < 0.7) {
                System.out.println(p.getTitle().getCanonicalTitle() + " : SKIP: Too low of SR (" + highestCountry + " : " + highestSR + ")");
                continue;
            } else {
                System.out.println(p.getTitle().getCanonicalTitle() + " : Highest SR: " + highestCountry + " (" + highestSR + ")");
            }

            writer.writeNext(line);
            srValue.add(srValues);
        }

        writer.close();
        System.out.println("Finished processing SR\n\tFound=" + topPages.size() + ", Retained=" + srValue.size());
        System.out.println("Calculating correlation");

        // Create correlation matrix
        PearsonsCorrelation corr = new PearsonsCorrelation();
        RealMatrix correlation = new BlockRealMatrix(srValue.size(), srValue.size());
        for (int i = 0; i < srValue.size(); i++) {
            for (int j = 0; j < srValue.size(); j++) {
                correlation.setEntry(i, j, corr.correlation(srValue.get(i), srValue.get(j)));
            }
            if (i % 200 == 0 && i > 0) {
                System.out.println("Finished Processing Row " + i);
            }
        }

        System.out.println("Saving Table to Disk");

        // Write it to disk
        String corrTable = "game_corr_table.csv";
        writer = new CSVWriter(new FileWriter(new File(corrTable), false), ',');
        for (int i = 0; i < correlation.getRowDimension(); i++) {
            String[] strings = new String[correlation.getColumnDimension()];
            for (int j = 0; j < correlation.getColumnDimension(); j++) {
                strings[j] = correlation.getEntry(i, j) + "";
            }

            writer.writeNext(strings);
        }
        writer.close();

        System.out.println("Finished Creating Game Correlation");
    }
}
