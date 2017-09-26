package org.wikibrain.cookbook;

import org.apache.commons.cli.*;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.*;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.core.model.UniversalPage;
import org.wikibrain.sr.SRMetric;
import org.wikibrain.sr.SRResult;
import org.wikibrain.sr.SRResultList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Created by allenlin on 6/2/16.
 */
public class TopSRArticles {
    public LocalPageDao lpDao;
    public ArrayList<List<String>> pages;
    public Configurator conf;
    private UniversalPageDao conceptDao;
    private HashMap<String, SRMetric> sr_metrics;


    public TopSRArticles(CommandLine cmd){
        try {
            // Initialize the WikiBrain environment and get the local page dao
            Env env = new EnvBuilder(cmd).build();
            conf = env.getConfigurator();
            lpDao = conf.get(LocalPageDao.class);
            conceptDao = conf.get(UniversalPageDao.class);

            sr_metrics = new HashMap<String, SRMetric>();

            SRMetric sr_en = conf.get(
                    SRMetric.class, "milnewitten",
                    "language", "en");
            SRMetric sr_es = conf.get(
                    SRMetric.class, "milnewitten",
                    "language", "es");
            SRMetric sr_zh = conf.get(
                    SRMetric.class, "milnewitten",
                    "language", "zh");

            sr_metrics.put("en", sr_en);
            sr_metrics.put("zh", sr_zh);
            sr_metrics.put("es", sr_es);

        } catch (ConfigurationException configEx){
            configEx.printStackTrace();
        }
    }

    public void getTopSRArticles(String article_title)
    {
        try {
            Language original_language = Language.getByLangCode("en");
            LocalPage lp_original_mainArticle = lpDao.getByTitle(original_language, article_title);
            UniversalPage up_article = conceptDao.getByLocalPage(lp_original_mainArticle);

            for(String lang_code: sr_metrics.keySet()){
                Language current_lang = Language.getByLangCode(lang_code);
                LocalPage current_lp = lpDao.getById(current_lang, up_article.getLocalId(current_lang));
                SRMetric tp_sr = sr_metrics.get(lang_code);
                SRResultList result_list = tp_sr.mostSimilar(current_lp.getLocalId(), 50);


                System.out.println("The top 50 most similar articles for "+ article_title+ " in " + lang_code);
                for (SRResult result: result_list){
                    int result_id = result.getId();
                    double result_score = result.getScore();
                    LocalPage result_page = lpDao.getById(current_lang, result_id);

                    System.out.println(result_page.getTitle().getCanonicalTitle() + ": " + result_score);
                }
            }

        } catch (DaoException daoExc){
            daoExc.printStackTrace();
        }
    }

    public static void main(String[] args){
        Options options = new Options();
        EnvBuilder.addStandardOptions(options);

        CommandLineParser parser = new PosixParser();
        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println("Invalid option usage: " + e.getMessage());
            new HelpFormatter().printHelp("SRBuilder", options);
            return;
        }

        String article_title = args[0];
        TopSRArticles tp_topSRarticles = new TopSRArticles(cmd);

        tp_topSRarticles.getTopSRArticles(article_title);
    }
}
