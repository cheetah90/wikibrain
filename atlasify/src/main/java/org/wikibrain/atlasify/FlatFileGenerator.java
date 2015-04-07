package org.wikibrain.atlasify;

import au.com.bytecode.opencsv.CSVWriter;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.DaoFilter;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.sr.SRMetric;

import java.io.FileWriter;
import java.util.*;
import java.util.logging.Logger;

/**
 * Created by toby on 4/3/15.
 */
public class FlatFileGenerator {

    //set parameters
    private static boolean printTitle = true;
    private static Language language = Language.EN;
    private static String algorithm = "milnewitten";

    private static final Logger LOG = Logger.getLogger(FlatFileGenerator.class.getName());

    public static void main(String[] args) throws Exception {
        Env env = EnvBuilder.envFromArgs(args);
        Configurator conf = env.getConfigurator();
        LocalPageDao lpDao = conf.get(LocalPageDao.class);
        DaoFilter daoFilter = new DaoFilter().setLanguages(language);
        Iterable<LocalPage> allPages = lpDao.get(daoFilter);
        Iterator<LocalPage> localPageIterator = allPages.iterator();
        Set<LocalPage> localPageSet = new HashSet<LocalPage>();

        //get all local pages
        int[] startingPageIdArray = new int[]{690, 1967554, 51336, 737, 701, 1217, 738, 860, 600, 69328, 18951905, 10918072, 20611195, 18959138, 1227, 10747, 4689264, 26964606, 746, 3708, 21490998, 3459, 3470, 38582368, 3454, 3415, 3463, 18933277, 8366550, 347422, 30307259, 3457, 3458, 3460, 3462, 3383, 3455, 3466, 2421391, 3464, 99721, 5478, 5042916, 5520, 26748, 5489, 5405, 57744, 5447, 76762, 19599929, 7067, 5222, 5403, 18962637, 5551, 5042481, 65205, 5500, 5468, 271409, 5593, 5321, 11867, 17207794, 11898195, 76972, 8060, 358, 9334, 8087628, 9316, 17238590, 26667, 28222445, 187749, 10577, 10707, 43586126, 19231, 5843419, 12027, 3067136, 48768, 12166, 12067, 12176, 12343, 18714469, 12186, 9366, 12108, 17238662, 12118, 17238567, 21350970, 11974, 17238691, 13404, 13383, 13394, 5573, 13373, 13275, 14579, 14727, 14533, 18950601, 147575, 14653, 7515928, 14531, 9282173, 14532, 15660, 15693, 7515964, 15573, 1673550, 16642, 188171, 170131, 334751, 16674, 57292, 16749, 17391, 7515890, 17752, 17771, 17791, 17633, 27208, 17810, 26750, 17781, 17675, 17515, 17514, 19502, 314168, 19291, 19261, 19260, 18964, 19117, 3966054, 29189464, 23564616, 19127, 19137, 19457, 20760, 19271, 16644, 19301, 18778516, 19281, 19169, 19201, 19088, 3607937, 19211, 21292, 21342, 21373, 21422, 21383, 21362, 21265, 21228, 14056, 21148, 27203221, 21241, 171166, 21302, 25231, 4913064, 22316, 23235, 22997, 3226, 35672, 170691, 23440, 23372, 19898, 6622784, 22943, 22936, 23041, 21255, 23033, 23420, 30971, 8238258, 35899345, 25445, 25391, 25645, 33220, 349303, 26994, 27421, 32350676, 27278, 27318, 35086, 26945, 10960558, 27308, 9356, 27248, 51425, 27358, 93876, 29265, 176801, 500057, 26828, 26830, 27338, 5058739, 27451, 9529005, 27288, 7515849, 18221, 5488, 30138, 30128, 30108, 30148, 198149, 14323695, 30158, 3565457, 30188, 11125639, 25734, 30118, 34414, 31816, 31750, 31843, 3434750, 3434750, 31853, 25683645, 27228, 32374, 3469, 32135, 202354, 32443, 33209, 35555, 69894, 27238, 350939, 17416221, 34415, 34399, 1645518, 19590, 21651, 13270, 14607, 13015878, 21883824, 5407, 5399, 21216, 21649, 26811621, 31716, 33611, 1930, 26810748, 16716, 19571, 21647, 22489, 26746, 18130, 29810, 6466, 21134, 25410, 32578, 303, 48830, 16949861, 27956, 14849, 21883857, 16846, 21650, 22199, 30395, 32432, 33127, 32905, 7930, 8543, 21648, 8210131, 23332, 19977, 18859, 624, 18933066, 18858, 152814, 173818, 183525, 173815, 171752, 18603746, 88585, 105059, 65109, 31516, 25862547, 166411, 183529, 155520, 81262, 83319, 166409, 65185, 166410, 72664, 166408, 155516, 105032, 50821, 173817, 166412, 27643, 96602, 173797, 55064, 173816, 18926, 26840, 717, 3392, 7129693, 21186, 34230, 22218, 7954867, 21182, 21184, 21980, 23071, 199793, 199801, 160592, 23962301, 222234, 222251, 83750, 18952846, 222247, 413170, 222254, 412275, 224013, 16020434, 195805, 222245, 412307, 156896, 413436, 194150, 413555, 412827, 195816, 224021, 18987, 195827, 222250, 63125, 6787, 193550, 37964};
        int  length = 0;
        LOG.info("start counting");
        while (localPageIterator.hasNext()){
            LocalPage localPage = localPageIterator.next();
            localPageSet.add(localPage);
        }
        LOG.info("Finished counting, got " + localPageSet.size() + "elements");

        //adding all local pages to a set
        int[] localPageIdArray = new int[localPageSet.size()];
        int i = 0;
        for(LocalPage lp : localPageSet){
            localPageIdArray[i++] = lp.getLocalId();
        }

        System.out.println("done with adding " + i + " elements");

        //calculating sr
        SRMetric sr = conf.get(
                SRMetric.class, algorithm,
                "language", language.getLangCode());
        double[][] srResult = sr.cosimilarity(startingPageIdArray, localPageIdArray);

        //printing to flatfiles
        String[] row = new String[5];
        CSVWriter writer = new CSVWriter(new FileWriter("test.csv"), ',');
        int x_count = 0;
        for(int x = 0; x < startingPageIdArray.length; x ++){
            LOG.info("finished " + x_count + " out of " + startingPageIdArray.length + " counties");
            x_count++;
            i = 0;
            writer = new CSVWriter(new FileWriter("SRFlatFile/" + language.getLangCode() + "/" + algorithm + "/" + startingPageIdArray[x] + ".csv"), ',');
            for(int y = 0; y < localPageSet.size(); y ++){
                i ++;
                if(i % 10000 == 0){
                    LOG.info("Finished " + i + " out of " + localPageSet.size());
                }
                try{
                    //don't record the "white-colored" result
                    if(srResult[x][y] < 0.2873)
                        continue;
                    row[0] = String.valueOf(startingPageIdArray[x]);
                    if(printTitle)
                        row[1] = lpDao.getById(language, startingPageIdArray[x]).getTitle().getCanonicalTitle();
                    else
                        row[1] = "";
                    row[2] = String.valueOf(localPageIdArray[y]);
                    if(printTitle)
                        row[3] = lpDao.getById(language, localPageIdArray[y]).getTitle().getCanonicalTitle();
                    else
                        row[3] = "";
                    row[4] = String.valueOf(srResult[x][y]);
                    writer.writeNext(row);
                    writer.flush();
                }
                catch (Exception e){
                    continue;
                    //do nothing
                }
            }
        }
        writer.flush();
        writer.close();




    }
}
