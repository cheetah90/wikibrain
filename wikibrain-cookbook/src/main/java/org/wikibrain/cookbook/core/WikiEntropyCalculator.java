package org.wikibrain.cookbook.core;

import au.com.bytecode.opencsv.CSVWriter;
import com.vividsolutions.jts.geom.Geometry;
import gnu.trove.iterator.TIntIterator;
import gnu.trove.set.TIntSet;
import org.wikibrain.conf.ConfigurationException;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.WikiBrainException;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.*;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.model.LocalLink;
import org.wikibrain.spatial.dao.SpatialContainmentDao;
import org.wikibrain.spatial.dao.SpatialDataDao;

import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * Created by toby on 5/13/15.
 */
public class WikiEntropyCalculator {
    static private SpatialDataDao sdDao;
    static private UniversalPageDao upDao;
    static private LocalPageDao lpDao;
    static private SpatialContainmentDao scDao;
    static private LocalLinkDao llDao;

    public static <T> List<T> getRandomSubList(List<T> input, int subsetSize)
    {
        Random r = new Random();
        int inputSize = input.size();
        for (int i = 0; i < subsetSize; i++)
        {
            int indexToSwap = i + r.nextInt(inputSize - i);
            T temp = input.get(i);
            input.set(i, input.get(indexToSwap));
            input.set(indexToSwap, temp);
        }
        return input.subList(0, subsetSize);
    }

    public static void main(String args[]) throws FileNotFoundException, IOException, ConfigurationException, DaoException, WikiBrainException {
        Env env = EnvBuilder.envFromArgs(args);
        Configurator c = env.getConfigurator();
        sdDao = c.get(SpatialDataDao.class);
        upDao = c.get(UniversalPageDao.class);
        lpDao = c.get(LocalPageDao.class);
        scDao = c.get(SpatialContainmentDao.class);
        llDao = c.get(LocalLinkDao.class);

        Language lang = Language.SIMPLE;
        CSVWriter writer = new CSVWriter(new FileWriter(lang.getLangCode() + "_countyEntropy.csv"), ',');
        String[] row = new String[3];
        row[0] = "Universal ID";
        row[1] = "Name";
        row[2] = "Entropy";
        writer.writeNext(row);
        writer.flush();

        Map<Integer, Geometry> countyMap = sdDao.getAllGeometriesInLayer("counties");
        for(Map.Entry<Integer, Geometry> countyEntry : countyMap.entrySet()){
            Set<String> layerSet = new HashSet<String>();
            layerSet.add("wikidata");
            List<LocalLink> allCountyLinks = new ArrayList<LocalLink>();
            TIntSet resultSet = scDao.getContainedItemIds(countyEntry.getValue(), "earth", layerSet, SpatialContainmentDao.ContainmentOperationType.CONTAINMENT);
            TIntIterator resultIterator = resultSet.iterator();
            Double infoEntropy = 0.0;
            while (resultIterator.hasNext()){
                Integer univId = resultIterator.next();
                Integer localId = upDao.getLocalId(lang, univId);
                if(localId == -1)
                    //no corresponding local page in the given language
                    continue;
                Iterable<LocalLink> localLinks = llDao.getLinks(lang, localId, true);
                Iterator<LocalLink> localLinkIterator = localLinks.iterator();
                while (localLinkIterator.hasNext()){
                    allCountyLinks.add(localLinkIterator.next());
                }
            }
            //calculate entropy
            List<LocalLink> sampleList = getRandomSubList(allCountyLinks, allCountyLinks.size() > 1000 ? 1000 : allCountyLinks.size());
            Map<Integer, Integer> linkDestinationCountMap = new HashMap<Integer, Integer>();
            for(LocalLink l : sampleList){
                if(linkDestinationCountMap.containsKey(l.getDestId())){
                    linkDestinationCountMap.put(l.getDestId(), linkDestinationCountMap.get(l.getDestId()) + 1);
                }
                else{
                    linkDestinationCountMap.put(l.getDestId(), 1);
                }
            }
            for(Map.Entry<Integer, Integer> linkDestinationCountEntry : linkDestinationCountMap.entrySet()){
                Double selfInformation = -(Math.log(linkDestinationCountEntry.getValue().doubleValue() / sampleList.size()) / Math.log(2));
                infoEntropy += (selfInformation * (linkDestinationCountEntry.getValue().doubleValue() / sampleList.size()));
            }
            row[0] = countyEntry.getKey().toString();
            row[1] = upDao.getById(countyEntry.getKey()).getBestEnglishTitle(lpDao, true).getCanonicalTitle();
            row[2] = infoEntropy.toString();
            writer.writeNext(row);
            writer.flush();


        }
        writer.close();

    }
}