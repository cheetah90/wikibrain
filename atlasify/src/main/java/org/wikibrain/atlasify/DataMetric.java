package org.wikibrain.atlasify;

import com.thoughtworks.xstream.mapper.Mapper;
import de.fau.cs.osr.ptk.common.ast.Tuple;
import gnu.trove.set.TIntSet;
import org.apache.commons.lang.WordUtils;
import org.wikibrain.core.dao.DaoException;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.lang.LocalId;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.sr.BaseSRMetric;
import org.wikibrain.sr.Explanation;
import org.wikibrain.sr.SRResult;
import org.wikibrain.sr.SRResultList;
import org.wikibrain.sr.disambig.Disambiguator;
import org.wikibrain.wikidata.LocalWikidataStatement;
import org.wikibrain.wikidata.WikidataEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by Josh on 3/2/15.
 */
public abstract class DataMetric extends BaseSRMetric {
    static class Tuple<X, Y> {
        public final X x;
        public final Y y;
        public Boolean reversed = false;
        public Tuple(X x, Y y) {
            this.x = x;
            this.y = y;
        }
    }

    public DataMetric(String name, Language language, LocalPageDao dao, Disambiguator disambig) {
        super(name, language, dao, disambig);
    }

    // This function will return a map of all the statements of the input s
    // The keys of the map should be the properites and the value should be
    // a list of the values associated with the property
    // The property string can be ignored, since the underlying implementation
    // will still check the entire list, it can be used for better performance
    public abstract List<Tuple<String, List<String>>> statmentsForQuery(String query, String property);

    // This should convert the input string into a format string where the arguments will be:
    // 1 - Item's name as a String
    // 2 - Value's name as a String
    // The strings will be converted to canonical page titles
    public String formatStringForProperty(String s) {
        return "%s " + s + " %s";
    }

    // These methods will be used to convert between a local page
    // And the implementation's page strings
    // This class works in the LocalPage space
    public abstract LocalPage convertToLocalPageTitle(String s);
    public abstract String convertFromLocalPageTitle(LocalPage l);

    // Whether to search both directions, a -> b and b -> a
    public boolean bidirectional = false;
    // How far into the explanations tree the algorithm should search
    // for explanations
    // Change this at your own risk... It will cause a computational explosion
    public int searchDepth = 1;

    // Should return true if the two titles are similar
    // Strings will be in the Metric's space
    public boolean similarTitles(String s1, String s2) {
        return s1.contains(s2) || s2.contains(s1);
    }

    // This doesn't correctly implements this function, but is sufficient for now
    @Override
    public SRResultList mostSimilar(int pageId, int maxResults, TIntSet validIds) throws DaoException {
        SRResultList list = new SRResultList(maxResults);
        int i = 0;
        for (int id : validIds.toArray()) {
            if (i > maxResults) {
                break;
            }
            list.set(i, similarity(pageId, id, true));
            i++;
        }
        return list;
    }

    @Override
    public SRResult similarity(int pageId1, int pageId2, boolean explanations) throws DaoException {
        SRResult result = new SRResult();
        result.setId(pageId2);
        result.setScore(0.0);

        LocalPage page1 = getLocalPageDao().getById(new LocalId(getLanguage(), pageId1));
        LocalPage page2 = getLocalPageDao().getById(new LocalId(getLanguage(), pageId2));

        if(page1 == null || page2 == null){
            if(page1 == null)
                System.out.println("WARNING: null page id " + pageId1);
            else
                System.out.println("WARNING: null page id " + pageId2);
            return new SRResult(0);
        }
        String title1 = convertFromLocalPageTitle(page1);
        String title2 = convertFromLocalPageTitle(page2);

        if (title1.equals("") || title2.equals("")) {
            return new SRResult(0);
        }

        result = combineResults(result, recursiveSimilarity(title1, title2, explanations, 1, searchDepth));
        if (bidirectional) {
            result = combineResults(result, recursiveSimilarity(title2, title1, explanations, 1, searchDepth));
        }

        return result;
    }

    // This will not correctly set the ID of the SRResult
    private SRResult recursiveSimilarity(String page1, String page2, boolean explanations, int currentDepth, int totalDepth) throws DaoException {
        assert(currentDepth > 1); // The depth should start and 1 and increase
        if (currentDepth > totalDepth) {
            return null;
        }

        double score = 1.0/(double)(currentDepth);

        SRResult result = new SRResult();
        result.setScore(0.0);

        // Perform the search
        List<Tuple<String, List<String>>> statements = statmentsForQuery(page1, page2);
        for (Tuple<String, List<String>> pair : statements) {
            for (String item : pair.y) {
                if (similarTitles(page2, item)) {
                    // Update the score
                    result.setScore(score);

                    if (explanations) {
                        // Add the explanation
                        String format = formatStringForProperty(pair.x);
                        String itemString = WordUtils.capitalize(convertToLocalPageTitle(page1).getTitle().getCanonicalTitle());
                        String valueString = WordUtils.capitalize(item); //convertToLocalPageTitle(page2).getTitle().getCanonicalTitle();
                        Explanation e = new Explanation(format, itemString, valueString);

                        if (pair.reversed) {
                            String temp = valueString;
                            valueString = itemString;
                            itemString = temp;
                        }

                        // Not a very useful explanation
                        if (format.contains("unknown") || itemString.contains("unknown") || valueString.contains("unknown")) {
                            continue;
                        }

                        // Check that the explanation is not a duplicate
                        boolean duplicate = false;
                        for (int i = 0; i < result.getExplanations().size(); i++) {
                            Explanation exp = result.getExplanations().get(i);
                            if (exp.getFormat().equals(format)) {
                                // Make sure that it isn't an exact duplicate
                                if (exp.getInformation().get(1).equals(valueString)) {
                                    duplicate = true;
                                    break;
                                }

                                // Make sure that one isn't a substring of the other
                                // i.e. Minnesota vs Minnesota, United States
                                // We will go with the longest one, since it should be the more informative
                                if (((String)exp.getInformation().get(1)).contains(valueString)) {
                                    duplicate = true;
                                    break;
                                }
                                // The explanation we already added could be the less informative one
                                if (valueString.contains((String)exp.getInformation().get(1))) {
                                    result.getExplanations().remove(i);
                                    i--;
                                }
                            }
                        }

                        if (!duplicate) {
                            result.addExplanation(e);
                        }
                    }
                }
            }
        }

        /*
        for (String property : statements.keySet()) {
            result = combineResults(result, recursiveSimilarity(page1, property, explanations, currentDepth + 1, totalDepth));
        }
        */

        return result;
    }

    private SRResult combineResults(SRResult r1, SRResult r2) {
        if (r1 == null) {
            return r2;
        }
        if (r2 == null) {
            return r1;
        }

        // Both are valid, so combine the metrics
        SRResult s = r1;
        // Combine the "probabilities"
        double score = r1.getScore() + r2.getScore() - r1.getScore() * r2.getScore();
        s.setScore(score);
        // Combine the explanations
        s.getExplanations().addAll(r2.getExplanations());
        return s;
    }
}
