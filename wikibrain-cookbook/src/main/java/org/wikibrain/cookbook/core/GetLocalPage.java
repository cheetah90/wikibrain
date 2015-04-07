package org.wikibrain.cookbook.core;

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

import java.io.FileWriter;
import java.util.Iterator;
import java.util.Map;

/**
 * Created by toby on 4/6/15.
 */
public class GetLocalPage {

    public static void main(String[] args) throws Exception {
        Env env = EnvBuilder.envFromArgs(args);
        Configurator conf = env.getConfigurator();
        SpatialDataDao sdDao = conf.get(SpatialDataDao.class);
        LocalPageDao lpDao_live = conf.get(LocalPageDao.class, "live");
        LocalPageDao lpDao_sql = conf.get(LocalPageDao.class, "sql");
        UniversalPageDao upDao = conf.get(UniversalPageDao.class);
        Map<Integer, Geometry> allGeomMap = sdDao.getAllGeometriesInLayer("wikidata");
        Language lang = Language.EN;
        CSVWriter writer = new CSVWriter(new FileWriter("talk_page_id.csv"), ',');
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
        for(Map.Entry<Integer, Geometry> entry : allGeomMap.entrySet()){
            if((i++) % 100 == 0)
                System.out.println("DONE " + i + " OUT OF " + allGeomMap.entrySet().size() + " error " + errorCount);
            try{
                Integer univId = entry.getKey();
                Integer localId = upDao.getLocalId(lang, univId);
                String title = lpDao_sql.getById(lang, localId).getTitle().getCanonicalTitle();
                String talkTitle = "Talk:" + title;
                Integer talkId = lpDao_live.getIdByTitle(talkTitle, lang, NameSpace.TALK);
                row[0] = localId.toString();
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
