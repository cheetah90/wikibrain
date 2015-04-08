package org.wikibrain.cookbook.core;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import com.vividsolutions.jts.geom.Geometry;
import org.wikibrain.conf.Configurator;
import org.wikibrain.core.cmd.Env;
import org.wikibrain.core.cmd.EnvBuilder;
import org.wikibrain.core.dao.DaoFilter;
import org.wikibrain.core.dao.LocalPageDao;
import org.wikibrain.core.dao.UniversalPageDao;
import org.wikibrain.core.lang.Language;
import org.wikibrain.core.model.LocalPage;
import org.wikibrain.core.model.NameSpace;
import org.wikibrain.spatial.dao.SpatialDataDao;

import java.io.FileReader;
import java.io.FileWriter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by toby on 4/6/15.
 */
public class GetLocalPage {

    public static void main(String[] args) throws Exception {
        Env env = EnvBuilder.envFromArgs(args);
        Configurator conf = env.getConfigurator();
        LocalPageDao lpDao_live = conf.get(LocalPageDao.class, "live");
        LocalPageDao lpDao_sql = conf.get(LocalPageDao.class, "sql");
        SpatialDataDao sdDao = conf.get(SpatialDataDao.class);

        UniversalPageDao upDao = conf.get(UniversalPageDao.class);
        Map<Integer, Geometry> allGeomMap = sdDao.getAllGeometriesInLayer("wikidata");
        Language lang = Language.EN;
        CSVReader reader = new CSVReader(new FileReader("talk_page_id.csv"), ',');
        CSVWriter writer = new CSVWriter(new FileWriter("talk_page_id_3.csv"), ',');
        String row[] = new String[5];
        row[0] = "LOCAL_ID";
        row[1] = "TITLE";
        row[2] = "TALK_ID";
        row[3] = "TALK_TITLE";
        row[4] = "GEOMETRY";
        writer.writeNext(row);
        writer.flush();


        int i = 0;
        int errorCount = 0;
        for(Map.Entry<Integer, Geometry> entry: allGeomMap.entrySet()){
            if((i++) % 100 == 0)
                System.out.println("DONE " + i + " OUT OF " + allGeomMap.size() + " error " + errorCount);
            try{
                int univId = entry.getKey();
                int localId = upDao.getLocalId(lang, univId);
                String title = lpDao_sql.getById(lang, localId).getTitle().getCanonicalTitle();
                String talkTitle = "Talk:" + title;
                int failCount = 0;
                boolean failed = true;
                Integer talkId = -1;
                while(failCount++ < 5){
                    talkId = lpDao_live.getByTitle(lang, NameSpace.TALK, talkTitle).getLocalId();
                    if(talkId != -1){
                        failed = false;
                        break;
                    }
                }
                if(failed){
                    System.out.println("Failed to get talk page for  " + title);
                    //System.out.println("GOT: " + lpDao_live.getByTitle(Language.EN, NameSpace.TALK, talkTitle));
                    throw  new Exception();
                }
                row[0] = String.valueOf(localId);
                row[1] = title;
                row[2] = talkId.toString();
                row[3] = talkTitle;
                row[4] = entry.getValue().toString();
                writer.writeNext(row);
                writer.flush();
            }
            catch (Exception e){
                errorCount ++;
                continue;
            }
        }


        writer.flush();
        writer.close();

    }
}
