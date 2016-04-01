package org.wikibrain.cookbook.sr;

import de.tudarmstadt.ukp.wikipedia.parser.mediawiki.MediaWikiParser;
import de.tudarmstadt.ukp.wikipedia.parser.mediawiki.MediaWikiParserFactory;
import org.apache.commons.cli.*;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.*;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.lang.LanguageInfo;
import org.wikibrain.core.model.*;
import org.wikibrain.core.nlp.StringTokenizer;
import org.wikibrain.parser.wiki.ParsedLink;
import org.wikibrain.parser.wiki.SubarticleParser;
import org.wikibrain.sr.SRMetric;
import org.wikibrain.sr.SRResult;
import de.tudarmstadt.ukp.wikipedia.parser.*;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by rodney-user on 12/7/15.
 */
public class ComputeFeatures {
    public LocalPageDao lpDao;
    public LocalLinkDao llDao;
    public RawPageDao rpDao;
    public ArrayList<List<String>> pages;
    public Configurator conf;
    public UniversalPageDao conceptDao;

    public ArrayList<List<String>> readCsv2Array(String fileName){
        BufferedReader br = null;
        String line;
        String cvsSplitBy = ",";

        ArrayList<List<String>> pagesPair = new ArrayList<List<String>>();

        try {

            br = new BufferedReader(new FileReader(fileName));

            //Skip the first header line
            br.readLine();
            while ((line = br.readLine()) != null) {

                // use comma as separator
                List<String> record = new ArrayList<String>(Arrays.asList(line.split(cvsSplitBy)));
                pagesPair.add(record);
            }

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return pagesPair;
    }

    public ComputeFeatures(String fileName, CommandLine cmd){
        //initialize the WikiBrain environment
        try {
            // Initialize the WikiBrain environment and get the local page dao
            Env env = new EnvBuilder(cmd).build();
            conf = env.getConfigurator();
            lpDao = conf.get(LocalPageDao.class);
            llDao = conf.get(LocalLinkDao.class);
            conceptDao = conf.get(UniversalPageDao.class);
            rpDao = conf.get(RawPageDao.class);

        } catch (ConfigurationException configEx){
            configEx.printStackTrace();
        }

        pages = readCsv2Array(fileName);
    }

    public void writeToFile(String fileName, ArrayList<Double> results){
        try {
            PrintWriter writer = new PrintWriter(fileName, "UTF-8");
            for (Double result : results){
                writer.println(result);
            }
            writer.close();
        } catch (UnsupportedEncodingException UEE){
            UEE.printStackTrace();
        } catch (FileNotFoundException FFE){
            FFE.printStackTrace();
        }

    }

    public ArrayList<Double> ComputeSR(String SR_algorithm, String lang_code){

        ArrayList<Double> SR_results = new ArrayList<Double>();

        try {

            SRMetric sr = conf.get(
                    SRMetric.class, SR_algorithm,
                    "language", lang_code);


            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(lang_code);

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));

                if (lp_mainArticle != null && lp_subArticle != null){
                    SRResult similarity = sr.similarity(lp_mainArticle.getLocalId(), lp_subArticle.getLocalId(), false);
                    SR_results.add(similarity.getScore());
                }
                else {
                    //Missing value = 0
                    SR_results.add(0.0);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                }
            }
            System.out.println("finish computing " + SR_algorithm);
            return SR_results;

        } catch (ConfigurationException e){
            e.printStackTrace();
        } catch (DaoException e){
            e.printStackTrace();
        }

        return SR_results;
    }

    public ArrayList<Double> Compute_PageRankRatio(){
        ArrayList<Double> result = new ArrayList<Double>();

        try {
            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));


                if (lp_mainArticle != null && lp_subArticle != null){
                    //double pageRankRatio = llDao.getPageRank(language, lp_mainArticle.getLocalId())/llDao.getPageRank(language,lp_subArticle.getLocalId());
                    //result.add(pageRankRatio);
                }
                else {
                    result.add(-100.00);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                }
            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }

        return result;

    }

    public ArrayList<Double> Compute_NumLangsRatio(){
        ArrayList<Double> result = new ArrayList<Double>();


        try {
            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));
                if (lp_mainArticle == null || lp_subArticle == null){
                    result.add(-100.00);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }


                UniversalPage up_main = conceptDao.getByLocalPage(lp_mainArticle);
                UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

                if (up_main == null || up_sub == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                int main_NumLang = 0;
                for (Language lang : up_main.getLanguageSet()) {
                    LocalPage tp_page = lpDao.getById(lang, up_main.getLocalId(lang));
                    if (tp_page != null){
                        main_NumLang++;
                        System.out.println("main: "+lp_mainArticle.getTitle()+" in " + lang.getEnLangName());
                        System.out.println("local page title: " + tp_page.getTitle());
                    }

                }

                int sub_NumLang = 0;
                for (Language lang : up_sub.getLanguageSet()) {
                    LocalPage tp_page = lpDao.getById(lang, up_sub.getLocalId(lang));
                    if (tp_page != null){
                        sub_NumLang++;
                        System.out.println("sub: "+lp_subArticle.getTitle()+" in " + lang.getEnLangName());
                    }
                }

                if (sub_NumLang != 0 ){
                    double NumLangsRatio = (double) main_NumLang / (double) sub_NumLang ;
                    result.add(NumLangsRatio);
                }
                else {
                    result.add(Double.MAX_VALUE);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                }
            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }


        return result;
    }

    public ArrayList<Double> Compute_PotSubLangsRatio(){
        MediaWikiParserFactory pf = new MediaWikiParserFactory();
        pf.setCalculateSrcSpans(true);
        MediaWikiParser jwpl = pf.createParser();


        ArrayList<Double> result = new ArrayList<Double>();

        try {

            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));


                if (lp_mainArticle == null || lp_subArticle == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                int numLangCoexist = 0;
                int numLangPot = 0;

                UniversalPage up_main = conceptDao.getByLocalPage(lp_mainArticle);
                UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

                if (up_main == null || up_sub == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                for (Language lang : up_main.getLanguageSet()) {
                    if (up_sub.getLanguageSet().containsLanguage(lang)){
                        boolean flagPot = false;

                        numLangCoexist++;

                        LanguageInfo localLangInfo= LanguageInfo.getByLanguage(lang);
                        SubarticleParser subarticleParser = new SubarticleParser(localLangInfo);
                        LocalPage lang_main = lpDao.getById(lang, up_main.getLocalId(lang));
                        LocalPage lang_sub = lpDao.getById(lang, up_sub.getLocalId(lang));

                        RawPage rp_main = rpDao.getById(lang, lang_main.getLocalId());

                        ParsedPage pp_main = jwpl.parse(rp_main.getBody());
                        for (Section curSection: pp_main.getSections()){
                            ParsedLink.SubarticleType secSubType = subarticleParser.isSeeAlsoHeader(localLangInfo, curSection.getTitle());
                            if (secSubType != null){
                                if (curSection.getText().contains(lang_sub.getTitle().getCanonicalTitle())){
                                    flagPot = true;
                                }
                            }
                            for (Content curContent : curSection.getContentList()){
                                for (Template t : curContent.getTemplates()){
                                    List<String> tp_subarticleSet = getSubArticle(rp_main, t, subarticleParser);
                                    String sub_title = lang_sub.getTitle().getCanonicalTitle();
                                    if (tp_subarticleSet != null){
                                        if (lang.getLangCode().equals("zh")){
                                            ZhTradition2Simplified(tp_subarticleSet);
                                            sub_title = ZhTradition2Simplified(sub_title);
                                        }

                                        if(tp_subarticleSet.contains(sub_title)) {
                                            flagPot = true;
                                        }
                                    }
                                }
                            }
                        }

                        if (flagPot){
                            numLangPot++;
                        }
                    }
                }

                if (numLangCoexist != 0 ){
                    double PotSubRatio = (double) numLangPot/ (double) numLangCoexist;
                    result.add(PotSubRatio);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + ": " + PotSubRatio);
                }
                else {
                    result.add(-100.00);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                }
            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }

        return result;
    }

    /**
     *
     * @param rp_main: the rawpage of the main article
     * @param t:
     * @param subarticleParser
     * @return
     */
    private List<String> getSubArticle(RawPage rp_main, Template t, SubarticleParser subarticleParser){

        ParsedLink.SubarticleType tempSubType = null;

        /**
         * Hack from WikiTextParser.java
         */
        boolean errorWithSrcLocation = t.getSrcSpan().getEnd() < 0; // this checks for what seems to be when parsing fails in JWPL
        String templateTextOrig;
        if (!errorWithSrcLocation){
            templateTextOrig = rp_main.getBody().substring(t.getSrcSpan().getStart(), t.getSrcSpan().getEnd());
        }else{ // this makes up for errors in JWPL (or bad script, but it mostly looks like erros)
            int estimatedLength = t.getPos().getEnd() - t.getPos().getStart();
            templateTextOrig = rp_main.getBody().substring(t.getSrcSpan().getStart(), t.getSrcSpan().getStart() + estimatedLength + 1);
        }
        String templateText;
        if (templateTextOrig.length() >= 5){
            templateText = templateTextOrig.substring(2, templateTextOrig.length()-2);
        }else{
            return null;
        }

        String templateName = new Title(t.getName(), false, LanguageInfo.getByLanguage(rp_main.getLanguage())).getCanonicalTitle();
        tempSubType = subarticleParser.isTemplateSubarticle(templateName, templateText);

        if (tempSubType != null){
            return subarticleParser.getContentsOfTemplatePipe(templateText);
        }

        return null;

    }

    public ArrayList<Double> Compute_NumPotSubarticleRatio(){
        MediaWikiParserFactory pf = new MediaWikiParserFactory();
        pf.setCalculateSrcSpans(true);
        MediaWikiParser jwpl = pf.createParser();

        ArrayList<Double> result = new ArrayList<Double>();

        try {

            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));

                if (lp_mainArticle == null || lp_subArticle == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                int numMain = 0;
                int numSub = 0;

                UniversalPage up_main = conceptDao.getByLocalPage(lp_mainArticle);
                UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

                if (up_main == null || up_sub == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                for (Language lang : up_main.getLanguageSet()) {
                    LanguageInfo localLangInfo= LanguageInfo.getByLanguage(lang);
                    SubarticleParser subarticleParser = new SubarticleParser(localLangInfo);

                    LocalPage lang_main = lpDao.getById(lang, up_main.getLocalId(lang));

                    RawPage rp_main = rpDao.getById(lang, lang_main.getLocalId());

                    ParsedPage pp_main = jwpl.parse(rp_main.getBody());
                    for (Section curSection: pp_main.getSections()){
                        ParsedLink.SubarticleType secSubType = subarticleParser.isSeeAlsoHeader(localLangInfo, curSection.getTitle());
                        if (secSubType != null){
                            numMain+=curSection.getNestedLists().size();
                        }

                        for (Content curContent : curSection.getContentList()){
                            for (Template t : curContent.getTemplates()){
                                List<String> tp_subarticleSet = getSubArticle(rp_main, t, subarticleParser);
                                if (tp_subarticleSet != null){
                                    numMain+= tp_subarticleSet.size();
                                }
                            }
                        }
                    }
                }

                for (Language lang : up_sub.getLanguageSet()) {
                    LanguageInfo localLangInfo= LanguageInfo.getByLanguage(lang);
                    SubarticleParser subarticleParser = new SubarticleParser(localLangInfo);

                    LocalPage lang_sub = lpDao.getById(lang, up_sub.getLocalId(lang));

                    RawPage rp_main = rpDao.getById(lang, lang_sub.getLocalId());

                    ParsedPage pp_main = jwpl.parse(rp_main.getBody());
                    for (Section curSection: pp_main.getSections()){
                        ParsedLink.SubarticleType secSubType = subarticleParser.isSeeAlsoHeader(localLangInfo, curSection.getTitle());
                        if (secSubType != null){
                            numSub+= curSection.getNestedLists().size();
                        }

                        for (Content curContent : curSection.getContentList()){
                            for (Template t : curContent.getTemplates()){
                                List<String> tp_subarticleSet = getSubArticle(rp_main, t, subarticleParser);
                                if (tp_subarticleSet != null){
                                    numSub+= tp_subarticleSet.size();
                                }
                            }
                        }
                    }
                }


                if (numSub != 0 ){
                    double PotSubRatio = (double) numMain/ (double) numSub;
                    result.add(PotSubRatio);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + ": " +PotSubRatio);
                }
                else {
                    result.add(Double.MAX_VALUE);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                }
            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }


        return result;
    }

    public ArrayList<Double> Compute_SeeAlsoSectionPct(){
        MediaWikiParserFactory pf = new MediaWikiParserFactory();
        pf.setCalculateSrcSpans(true);
        MediaWikiParser jwpl = pf.createParser();

        ArrayList<Double> result = new ArrayList<Double>();

        try {

            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));

                if (lp_mainArticle == null || lp_subArticle == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                int numSeeAlso = 0;
                int numLangPot = 0;

                UniversalPage up_main = conceptDao.getByLocalPage(lp_mainArticle);
                UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

                if (up_main == null || up_sub == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                for (Language lang : up_main.getLanguageSet()) {
                    if (up_sub.getLanguageSet().containsLanguage(lang)){
                        boolean potential = false;
                        boolean seeAlso = false;

                        LanguageInfo localLangInfo= LanguageInfo.getByLanguage(lang);
                        SubarticleParser subarticleParser = new SubarticleParser(localLangInfo);

                        LocalPage lang_main = lpDao.getById(lang, up_main.getLocalId(lang));
                        LocalPage lang_sub = lpDao.getById(lang, up_sub.getLocalId(lang));

                        RawPage rp_main = rpDao.getById(lang, lang_main.getLocalId());

                        ParsedPage pp_main = jwpl.parse(rp_main.getBody());
                        for (Section curSection: pp_main.getSections()){
                            ParsedLink.SubarticleType secSubType = subarticleParser.isSeeAlsoHeader(localLangInfo, curSection.getTitle());
                            if (secSubType != null){
                                if (curSection.getText().contains(lang_sub.getTitle().getCanonicalTitle())){
                                    seeAlso = true;
                                    potential = true;
                                }
                            }
                            for (Content curContent : curSection.getContentList()){
                                for (Template t : curContent.getTemplates()){
                                    List<String> tp_subarticleSet = getSubArticle(rp_main, t, subarticleParser);
                                    String sub_title = lang_sub.getTitle().getCanonicalTitle();
                                    if (tp_subarticleSet != null){
                                        if (lang.getLangCode().equals("zh")){
                                            ZhTradition2Simplified(tp_subarticleSet);
                                            sub_title = ZhTradition2Simplified(sub_title);
                                        }

                                        if(tp_subarticleSet.contains(sub_title)) {
                                            potential = true;
                                        }
                                    }
                                }
                            }
                        }

                        if (potential){
                            numLangPot++;
                        }
                        if (seeAlso){
                            numSeeAlso++;
                        }
                    }
                }

                if (numLangPot != 0 ){
                    double PotSubRatio = (double) numSeeAlso/ (double) numLangPot;
                    result.add(PotSubRatio);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + ": "+ PotSubRatio);
                }
                else {
                    result.add(-100.00);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                }
            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }


        return result;
    }

    private String ZhTradition2Simplified(String inputZh){
        ZHConverter converter = ZHConverter.getInstance(ZHConverter.SIMPLIFIED);

        return converter.convert(inputZh);
    }

    private void ZhTradition2Simplified(List<String> inputZH){
        ZHConverter converter = ZHConverter.getInstance(ZHConverter.SIMPLIFIED);
        for (int i = 0; i<inputZH.size(); i++){
            String simplifiedStr = converter.convert(inputZH.get(i));
            inputZH.set(i, simplifiedStr);
        }
    }

    public ArrayList<Double> Compute_ReferenceRatio(){


        ArrayList<Double> result = new ArrayList<Double>();

        try {

            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));

                if (lp_mainArticle == null || lp_subArticle == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                int numRef_main = 0;
                int numRef_sub = 0;

                UniversalPage up_main = conceptDao.getByLocalPage(lp_mainArticle);
                UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

                if (up_main == null || up_sub == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                for (Language lang : up_main.getLanguageSet()) {
                    if (up_sub.getLanguageSet().containsLanguage(lang)){

                        LocalPage lang_main = lpDao.getById(lang, up_main.getLocalId(lang));
                        LocalPage lang_sub = lpDao.getById(lang, up_sub.getLocalId(lang));

                        RawPage rp_main = rpDao.getById(lang, lang_main.getLocalId());
                        RawPage rp_sub = rpDao.getById(lang, lang_sub.getLocalId());
                        numRef_main += StringUtils.countMatches(rp_main.getBody(), "<ref>");
                        numRef_sub += StringUtils.countMatches(rp_sub.getBody(), "<ref>");

                    }
                }

                if (numRef_sub !=0){
                    double PotSubRatio = (double) numRef_main/ (double) numRef_sub;
                    result.add(PotSubRatio);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + ":" + PotSubRatio);
                }
                else {
                    result.add(Double.MAX_VALUE);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                }
            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }


        return result;
    }

    // num(common token in sub)/num(token in main)
    private double countTokenOverlap(List<String> main_tokens, List<String> sub_tokens){
        int overlap = 0;
        for (String tk : main_tokens){
            if (sub_tokens.contains(tk)){
                overlap++;
            }
        }
        return (double) overlap/ (double) main_tokens.size();
    }

    public ArrayList<Double> Compute_SectionTokenOverlap(){
        MediaWikiParserFactory pf = new MediaWikiParserFactory();
        pf.setCalculateSrcSpans(true);
        MediaWikiParser jwpl = pf.createParser();

        ArrayList<Double> result = new ArrayList<Double>();

        try {

            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));

                if (lp_mainArticle == null || lp_subArticle == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                double max_overlap = 0;

                UniversalPage up_main = conceptDao.getByLocalPage(lp_mainArticle);
                UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

                if (up_main == null || up_sub == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }


                for (Language lang : up_main.getLanguageSet()) {
                    if (up_sub.getLanguageSet().containsLanguage(lang)){

                        LocalPage lang_main = lpDao.getById(lang, up_main.getLocalId(lang));
                        LocalPage lang_sub = lpDao.getById(lang, up_sub.getLocalId(lang));

                        RawPage rp_main = rpDao.getById(lang, lang_main.getLocalId());

                        ParsedPage pp_main = jwpl.parse(rp_main.getBody());
                        for (Section curSection: pp_main.getSections()){
                            for (Content curContent : curSection.getContentList()){
                                for (Template t : curContent.getTemplates()){
                                    for (String parameter: t.getParameters()){
                                        if (parameter.contains(lang_sub.getTitle().getCanonicalTitle())){
                                            List<String> sub_tokens;
                                            List<String> main_sec_tokens;

                                            if (lang.getLangCode().equals("zh")){
                                                String tp_sub_title = lang_sub.getTitle().getCanonicalTitle();
                                                sub_tokens = new ArrayList<String>(Arrays.asList(tp_sub_title.split("")));
                                                if(sub_tokens.get(0).equals(""))
                                                    sub_tokens.remove(0);
                                                ZhTradition2Simplified(sub_tokens);

                                                if (curContent instanceof SectionContent){
                                                    String tp_main_sec_title = lang_main.getTitle().getCanonicalTitle()+((SectionContent) curContent).getTitle();
                                                    main_sec_tokens = new ArrayList<String>(Arrays.asList(tp_main_sec_title.split("")));
                                                } else {
                                                    String tp_main_sec_title = lang_main.getTitle().getCanonicalTitle() + curSection.getTitle();
                                                    main_sec_tokens = new ArrayList<String>(Arrays.asList(tp_main_sec_title.split("")));
                                                }
                                                if (main_sec_tokens.get(0).equals(""))
                                                    main_sec_tokens.remove(0);
                                                ZhTradition2Simplified(main_sec_tokens);
                                            } else {
                                                StringTokenizer tokenizer = new StringTokenizer();
                                                sub_tokens = tokenizer.getWords(lang,lang_sub.getTitle().getCanonicalTitle());
                                                if (curContent instanceof SectionContent){
                                                    main_sec_tokens = tokenizer.getWords(lang,lang_main.getTitle().getCanonicalTitle() + " " + ((SectionContent) curContent).getTitle());

                                                } else {
                                                    main_sec_tokens = tokenizer.getWords(lang,lang_main.getTitle().getCanonicalTitle() + " " + curSection.getTitle());
                                                }
                                            }

                                            System.out.println("language: "+ lang  +" main tokens: "+ main_sec_tokens + " sub tokens: " + sub_tokens);

                                            double curTokenOverlap = countTokenOverlap(main_sec_tokens, sub_tokens);
                                            if (curTokenOverlap > max_overlap){
                                                max_overlap = curTokenOverlap;
                                            }
                                        }

                                        /**
                                         * Another route
                                         */
                                    }
                                }
                            }
                        }
                    }
                }

                result.add(max_overlap);
                System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + ":" + max_overlap);
            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }

        return result;
    }



    public ArrayList<Double> Compute_MaxTokenOverlap(){
        ArrayList<Double> result = new ArrayList<Double>();

        try {

            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));

                if (lp_mainArticle == null || lp_subArticle == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                double max_overlap = 0;

                UniversalPage up_main = conceptDao.getByLocalPage(lp_mainArticle);
                UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

                if ( up_main == null || up_sub == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }


                for (Language lang : up_main.getLanguageSet()) {
                    if (up_sub.getLanguageSet().containsLanguage(lang)){
                        List<String> main_tokens;
                        List<String> sub_tokens;

                        LocalPage lang_main = lpDao.getById(lang, up_main.getLocalId(lang));
                        LocalPage lang_sub = lpDao.getById(lang, up_sub.getLocalId(lang));

                        if (lang.getLangCode().equals("zh")){
                            main_tokens = new ArrayList<String>(Arrays.asList(lang_main.getTitle().getCanonicalTitle().split("")));
                            if(main_tokens.get(0).equals(""))
                                main_tokens.remove(0);

                            ZhTradition2Simplified(main_tokens);
                            System.out.println(main_tokens);
                            sub_tokens = new ArrayList<String>(Arrays.asList(lang_sub.getTitle().getCanonicalTitle().split("")));
                            if(sub_tokens.get(0).equals(""))
                                sub_tokens.remove(0);
                            ZhTradition2Simplified(sub_tokens);
                        } else {
                            StringTokenizer tokenizer = new StringTokenizer();
                            sub_tokens = tokenizer.getWords(lang,lang_sub.getTitle().getCanonicalTitle());
                            main_tokens = tokenizer.getWords(lang, lang_main.getTitle().getCanonicalTitle());
                        }

                        double curTokenOverlap = countTokenOverlap(main_tokens, sub_tokens);
                        if (curTokenOverlap > max_overlap){
                            max_overlap = curTokenOverlap;
                        }
                    }
                }

                result.add(max_overlap);
                System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + ":" + max_overlap);
            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }

        return result;

    }

    public ArrayList<Double> Compute_MaxMainTFInSub(){
        MediaWikiParserFactory pf = new MediaWikiParserFactory();
        pf.setCalculateSrcSpans(true);
        MediaWikiParser jwpl = pf.createParser();

        ArrayList<Double> result = new ArrayList<Double>();

        try {

            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));

                if (lp_mainArticle == null || lp_subArticle == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }



                UniversalPage up_main = conceptDao.getByLocalPage(lp_mainArticle);
                UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

                if ( up_main == null || up_sub == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                double maxTF = 0;
                for (Language lang : up_main.getLanguageSet()) {
                    if (up_sub.getLanguageSet().containsLanguage(lang)){

                        int curTF = 0;

                        LocalPage lang_main = lpDao.getById(lang, up_main.getLocalId(lang));
                        LocalPage lang_sub = lpDao.getById(lang, up_sub.getLocalId(lang));

                        RawPage rp_sub = rpDao.getById(lang, lang_sub.getLocalId());

                        //Get the token of the main titile
//                        List<String> main_tokens;
//
//                        if (lang.getLangCode().equals("zh")){
//                            main_tokens = new ArrayList<String>(Arrays.asList(lang_main.getTitle().getCanonicalTitle().split("")));
//                            if(main_tokens.get(0).equals(""))
//                                main_tokens.remove(0);
//
//                        } else {
//                            StringTokenizer tokenizer = new StringTokenizer();
//                            main_tokens = tokenizer.getWords(lang, lang_main.getTitle().getCanonicalTitle());
//                        }

                        ParsedPage pp_main = jwpl.parse(rp_sub.getBody());


                        String summary = "";
                        String main_title = lang_main.getTitle().getCanonicalTitle();
                        for (Content curContent : pp_main.getSection(0).getContentList()){
                            if (curContent.getText().startsWith("TEMPLATE")){
                                continue;
                            }

                            summary += curContent.getText();
                        }

                        if (lang.getLangCode().equals("zh")){
                            summary = ZhTradition2Simplified(summary);
                            main_title = ZhTradition2Simplified(main_title);
                        }
                        if (StringUtils.containsIgnoreCase(summary, main_title)){
                            summary = summary.toLowerCase();
                            main_title = main_title.toLowerCase();
                            curTF += StringUtils.countMatches(summary, main_title);
                        }

                        StringTokenizer st = new StringTokenizer();
                        List<String> summaryTokens = st.getWords(lang, summary);
                        curTF = curTF / summaryTokens.size();

                        maxTF = curTF > maxTF ? curTF : maxTF;
                    }
                }

                if (maxTF != 0){
                    result.add(maxTF);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + ":" + maxTF);
                }
                else {
                    result.add(-100.00);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");

                }

            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }

        return result;

    }

    public ArrayList<Double> Compute_InlinkRatio(){
        ArrayList<Double> result = new ArrayList<Double>();

        try {
            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));
                if (lp_mainArticle == null || lp_subArticle == null){
                    result.add(-100.00);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }


                UniversalPage up_main = conceptDao.getByLocalPage(lp_mainArticle);
                UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

                if (up_main == null || up_sub == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                int main_NumInlink = 0;
                int sub_NumInlink = 0;



                for (Language lang : up_main.getLanguageSet()) {
                    LocalPage tp_page = lpDao.getById(lang, up_main.getLocalId(lang));
                    if(tp_page != null){
                        DaoFilter dFilter = new DaoFilter()
                                .setLanguages(language)
                                .setDestIds(tp_page.getLocalId());
                        main_NumInlink += llDao.getCount(dFilter);
                        System.out.println("Main page: " + tp_page.getTitle() + "Inlink: " + main_NumInlink);
                    } else {
                        System.out.println("The local page does not exist!");
                    }
                }

                for (Language lang : up_sub.getLanguageSet()) {
                    LocalPage tp_page = lpDao.getById(lang, up_main.getLocalId(lang));
                    if(tp_page != null){
                        DaoFilter dFilter = new DaoFilter()
                                .setLanguages(language)
                                .setDestIds(tp_page.getLocalId());
                        sub_NumInlink += llDao.getCount(dFilter);
                        System.out.println("Sub page: " + tp_page.getTitle() + "Inlink: " + sub_NumInlink);
                    } else {
                        System.out.println("The local page is empty");
                    }
                }

                if (sub_NumInlink != 0 ){
                    double NumLangRatio = (double) main_NumInlink/ (double) sub_NumInlink;
                    result.add(NumLangRatio);
                }
                else {
                    result.add(Double.MAX_VALUE);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                }


            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }


        return result;
    }

    public ArrayList<Double> Compute_MainTemplatePct(){
        MediaWikiParserFactory pf = new MediaWikiParserFactory();
        pf.setCalculateSrcSpans(true);
        MediaWikiParser jwpl = pf.createParser();

        ArrayList<Double> result = new ArrayList<Double>();

        try {

            for (List<String> pagePair : pages){

                Language language = Language.getByLangCode(pagePair.get(1));

                LocalPage lp_mainArticle = lpDao.getByTitle(language, pagePair.get(2));
                LocalPage lp_subArticle = lpDao.getByTitle(language, pagePair.get(3));

                if (lp_mainArticle == null || lp_subArticle == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                int numLangMainTemplates = 0;
                int numLangPot = 0;

                UniversalPage up_main = conceptDao.getByLocalPage(lp_mainArticle);
                UniversalPage up_sub = conceptDao.getByLocalPage(lp_subArticle);

                if (up_main == null || up_sub == null){
                    result.add(-100.00);
                    System.out.println(pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                    continue;
                }

                for (Language lang : up_main.getLanguageSet()) {
                    if (up_sub.getLanguageSet().containsLanguage(lang)){
                        boolean potential = false;
                        boolean mainTemplate = false;

                        LanguageInfo localLangInfo= LanguageInfo.getByLanguage(lang);
                        SubarticleParser subarticleParser = new SubarticleParser(localLangInfo);

                        LocalPage lang_main = lpDao.getById(lang, up_main.getLocalId(lang));
                        LocalPage lang_sub = lpDao.getById(lang, up_sub.getLocalId(lang));

                        RawPage rp_main = rpDao.getById(lang, lang_main.getLocalId());

                        ParsedPage pp_main = jwpl.parse(rp_main.getBody());
                        //This checks if the sub title is in the main template. If yes, this pair has both main template and potential relationships
                        for (Template template: pp_main.getTemplates()){

                            String templateName = new Title(template.getName(), false, LanguageInfo.getByLanguage(rp_main.getLanguage())).getCanonicalTitle();
                            ParsedLink.SubarticleType subarticleType = subarticleParser.isTemplateSubarticle(templateName, template.toString());
                            //If this template is a subarticle template and contains the potential subarticle title, change the flag
                            if (subarticleType != null && template.toString().contains(lang_sub.getTitle().getCanonicalTitle())){
                                potential = true;
                                //additionally, if it's main template, change the flag
                                if (subarticleType.equals(ParsedLink.SubarticleType.MAIN_TEMPLATE) ){
                                    mainTemplate = true;
                                    //No need to go further since we've already decided that this pair is potential subarticle.
                                    break;
                                }
                            }
                        }

                        //if the pair is not template subarticle, then check if it is a see also section subarticle.
                        if (!potential){
                            for (Section curSection: pp_main.getSections()){
                                ParsedLink.SubarticleType secSubType = subarticleParser.isSeeAlsoHeader(localLangInfo, curSection.getTitle());
                                if (secSubType != null){
                                    if (curSection.getText().contains(lang_sub.getTitle().getCanonicalTitle())){
                                        potential = true;
                                    }
                                }
                            }
                        }

                        if (potential){
                            numLangPot++;
                        }
                        if (mainTemplate){
                            numLangMainTemplates++;
                        }
                    }
                }

                if (numLangPot != 0 ){
                    double mainTemplatePct = (double) numLangMainTemplates/ (double) numLangPot;
                    result.add(mainTemplatePct);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + ": "+ mainTemplatePct);
                }
                else {
                    result.add(-100.00);
                    System.out.println("similarity between "+ pagePair.get(2) + " and " + pagePair.get(3) + " does not exist.");
                }
            }
        } catch (DaoException daoException){
            daoException.printStackTrace();
        }


        return result;
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


        ComputeFeatures tp_computeFeature = new ComputeFeatures("trainingdata_all.csv", cmd);

//        ArrayList<Double> InlinksRatio = tp_computeFeature.Compute_InlinkRatio();
//        tp_computeFeature.writeToFile("InlinksRatio.csv", InlinksRatio);

//        ArrayList<Double> MaxMainTFInSub = tp_computeFeature.Compute_MaxMainTFInSub();
//        tp_computeFeature.writeToFile("MaxMainTFInSub.csv", MaxMainTFInSub);

        ArrayList<Double> MainTagPct = tp_computeFeature.Compute_MainTemplatePct();
        tp_computeFeature.writeToFile("MainTagPct.csv", MainTagPct);

//        ArrayList<Double> MaxTokenOverlap = tp_computeFeature.Compute_MaxTokenOverlap();
//        tp_computeFeature.writeToFile("MaxTokenOverlap.csv", MaxTokenOverlap);

//        ArrayList<Double> SectionTokenOverlap = tp_computeFeature.Compute_SectionTokenOverlap();
//        tp_computeFeature.writeToFile("SectionTokenOverlap.csv", SectionTokenOverlap);

//        ArrayList<Double> ReferenceRatio = tp_computeFeature.Compute_ReferenceRatio();
//               tp_computeFeature.writeToFile("ReferenceRatio.csv", ReferenceRatio);

//        ArrayList<Double> seeAlsoSectionPct = tp_computeFeature.Compute_SeeAlsoSectionPct();
//        tp_computeFeature.writeToFile("seeAlsoSectionPct.csv", seeAlsoSectionPct);

//        ArrayList<Double> NumPotSubarticleRatio = tp_computeFeature.Compute_NumPotSubarticleRatio();
//        tp_computeFeature.writeToFile("NumPotSubarticleRatio.csv", NumPotSubarticleRatio);

//        ArrayList<Double> PotSubLangsRatio = tp_computeFeature.Compute_PotSubLangsRatio();
//        tp_computeFeature.writeToFile("PotSubLangsRatio.csv", PotSubLangsRatio);

//        ArrayList<Double> NumLangRatio = tp_computeFeature.Compute_NumLangsRatio();
//        tp_computeFeature.writeToFile("NumLangRatio.csv", NumLangRatio);

        //ArrayList<Double> PageRankRatio = tp_computeFeature.Compute_PageRankRatio();
        //tp_computeFeature.writeToFile("pageRank.csv", PageRankRatio);

//        ArrayList<Double> milnewitten = tp_computeFeature.ComputeSR("milnewitten", "en");
//        ArrayList<Double> category = tp_computeFeature.ComputeSR("category");
//
//        tp_computeFeature.writeToFile("milnewitten.csv", milnewitten);
//        //tp_computeFeature.writeToFile("category.csv", category);

    }

}
