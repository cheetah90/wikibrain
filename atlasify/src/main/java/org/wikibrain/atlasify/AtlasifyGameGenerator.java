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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by Josh on 4/2/15.
 */
public class AtlasifyGameGenerator {
    public static void main(String args[]) throws ConfigurationException, DaoException, IOException {
        // Get the pageview dao
        Language lang = Language.EN;
        Env env = EnvBuilder.envFromArgs(args);
        Configurator conf = env.getConfigurator();
        PageViewDao viewDao = env.getConfigurator().get(PageViewDao.class);
        LocalPageDao pageDao = conf.get(LocalPageDao.class);
        SRMetric sr = conf.get(SRMetric.class, "ensemble", "language", lang.getLangCode());

        // Download and import pageview stats if necessary
        DateTime start = new DateTime(2015, 4, 1, 0, 0, 0);
        DateTime end   = new DateTime(2015, 4, 1, 23, 0, 0);
        viewDao.ensureLoaded(start, end, env.getLanguages());

        // Retrieve counts for all pageviews
        TIntIntMap allViews = viewDao.getAllViews(lang, start, end);
        int pageIds[] = WpCollectionUtils.sortMapKeys(allViews, true);
        ArrayList<LocalPage> topPages = new ArrayList<LocalPage>();
        System.out.println("Top articles");
        for (int i = 0; i < Math.min(1000, allViews.size()); i++) {
            LocalPage page = pageDao.getById(lang, pageIds[i]);
            int n = allViews.get(pageIds[i]);
            topPages.add(page);

            System.out.format("%d. %s (nviews=%d)\n", (i + 1), page.getTitle(), n);
        }

        ArrayList<String> countries = new ArrayList<String>();
        byte[] encoded = Files.readAllBytes(Paths.get("country.json"));
        JSONObject geoJSON = new JSONObject(new String(encoded, StandardCharsets.UTF_8));
        JSONArray objects = geoJSON.getJSONArray("features");
        for (int i = 0; i < objects.length(); i++) {
            countries.add(objects.getJSONObject(i).getJSONObject("properties").getString("NAME"));
        }

        String pageTitle = "game_page_titles.csv";
        CSVWriter writer = new CSVWriter(new FileWriter(new File(pageTitle), false), ',');

        System.out.println("Calculating SR");

        // Load all the SR Data while writing value to file
        ArrayList<double[]> srValue = new ArrayList<double[]>();
        for (LocalPage p : topPages) {
            String[] line = new String[1];
            line[0] = p.getTitle().toString();
            writer.writeNext(line);

            System.out.println("\t" + p.getTitle().getCanonicalTitle() + " sr");

            // Compute SR
            double[] srValues = new double[countries.size()];
            for (int i = 0; i < countries.size(); i++) {
                srValues[i] = sr.similarity(p.getTitle().getCanonicalTitle(), countries.get(i), false).getScore();
            }
            srValue.add(srValues);
        }

        writer.close();

        System.out.println("Calculating correlation");

        // Create correlation matrix
        PearsonsCorrelation corr = new PearsonsCorrelation();
        RealMatrix correlation = new BlockRealMatrix(srValue.size(), srValue.size());
        for (int i = 0; i < srValue.size(); i++) {
            System.out.println("\tRow " + i);
            for (int j = 0; j < srValue.size(); j++) {
                correlation.setEntry(i, j, corr.correlation(srValue.get(i), srValue.get(j)));
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
    }
}
