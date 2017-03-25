//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package org.wikidata.wdtk.dumpfiles.wmf;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.wikidata.wdtk.dumpfiles.DumpContentType;
import org.wikidata.wdtk.dumpfiles.MwDumpFile;
import org.wikidata.wdtk.util.CompressionType;

public abstract class WmfDumpFile implements MwDumpFile {
    protected static final String DUMP_SITE_BASE_URL = "https://dumps.wikimedia.org/";
    static final Map<DumpContentType, String> WEB_DIRECTORY = new HashMap();
    static final Map<DumpContentType, String> POSTFIXES;
    static final Map<DumpContentType, CompressionType> COMPRESSION_TYPE;
    static final Map<DumpContentType, Boolean> REVISION_DUMP;
    protected final String dateStamp;
    protected final String projectName;
    Boolean isDone;

    public WmfDumpFile(String dateStamp, String projectName) {
        this.dateStamp = dateStamp;
        this.projectName = projectName;
    }

    public String getProjectName() {
        return this.projectName;
    }

    public String getDateStamp() {
        return this.dateStamp;
    }

    public boolean isAvailable() {
        if(this.isDone == null) {
            this.isDone = Boolean.valueOf(this.fetchIsDone());
        }

        return this.isDone.booleanValue();
    }

    public String toString() {
        return this.projectName + "-" + this.getDumpContentType().toString().toLowerCase() + "-" + this.dateStamp;
    }

    public BufferedReader getDumpFileReader() throws IOException {
        return new BufferedReader(new InputStreamReader(this.getDumpFileStream(), StandardCharsets.UTF_8));
    }

    protected abstract boolean fetchIsDone();

    public static String getDumpFilePostfix(DumpContentType dumpContentType) {
        if(POSTFIXES.containsKey(dumpContentType)) {
            return (String)POSTFIXES.get(dumpContentType);
        } else {
            throw new IllegalArgumentException("Unsupported dump type " + dumpContentType);
        }
    }

    public static String getDumpFileWebDirectory(DumpContentType dumpContentType, String projectName) {
        if(dumpContentType == DumpContentType.JSON) {
            if("wikidatawiki".equals(projectName)) {
                return DUMP_SITE_BASE_URL + (String)WEB_DIRECTORY.get(dumpContentType) + "wikidata" + "/";
            } else {
                throw new RuntimeException("Wikimedia Foundation uses non-systematic directory names for this type of dump file. I don\'t know where to find dumps of project " + projectName);
            }
        } else if(WEB_DIRECTORY.containsKey(dumpContentType)) {
            return DUMP_SITE_BASE_URL + (String)WEB_DIRECTORY.get(dumpContentType) + projectName + "/";
        } else {
            throw new IllegalArgumentException("Unsupported dump type " + dumpContentType);
        }
    }

    public static CompressionType getDumpFileCompressionType(DumpContentType dumpContentType) {
        if(COMPRESSION_TYPE.containsKey(dumpContentType)) {
            return (CompressionType)COMPRESSION_TYPE.get(dumpContentType);
        } else {
            throw new IllegalArgumentException("Unsupported dump type " + dumpContentType);
        }
    }

    public static String getDumpFileDirectoryName(DumpContentType dumpContentType, String dateStamp) {
        return dumpContentType.toString().toLowerCase() + "-" + dateStamp;
    }

    public static String getDateStampFromDumpFileDirectoryName(DumpContentType dumpContentType, String directoryName) {
        int prefixLength = dumpContentType.toString().length() + 1;
        return directoryName.substring(prefixLength);
    }

    public static String getDumpFileName(DumpContentType dumpContentType, String projectName, String dateStamp) {
        return dumpContentType == DumpContentType.JSON?dateStamp + getDumpFilePostfix(dumpContentType):projectName + "-" + dateStamp + getDumpFilePostfix(dumpContentType);
    }

    public static boolean isRevisionDumpFile(DumpContentType dumpContentType) {
        if(REVISION_DUMP.containsKey(dumpContentType)) {
            return ((Boolean)REVISION_DUMP.get(dumpContentType)).booleanValue();
        } else {
            throw new IllegalArgumentException("Unsupported dump type " + dumpContentType);
        }
    }

    static {
        WEB_DIRECTORY.put(DumpContentType.DAILY, "other/incr/");
        WEB_DIRECTORY.put(DumpContentType.CURRENT, "");
        WEB_DIRECTORY.put(DumpContentType.FULL, "");
        WEB_DIRECTORY.put(DumpContentType.SITES, "");
        WEB_DIRECTORY.put(DumpContentType.JSON, "other/");
        POSTFIXES = new HashMap();
        POSTFIXES.put(DumpContentType.DAILY, "-pages-meta-hist-incr.xml.bz2");
        POSTFIXES.put(DumpContentType.CURRENT, "-pages-meta-current.xml.bz2");
        POSTFIXES.put(DumpContentType.FULL, "-pages-meta-history.xml.bz2");
        POSTFIXES.put(DumpContentType.SITES, "-sites.sql.gz");
        POSTFIXES.put(DumpContentType.JSON, ".json.gz");
        COMPRESSION_TYPE = new HashMap();
        COMPRESSION_TYPE.put(DumpContentType.DAILY, CompressionType.BZ2);
        COMPRESSION_TYPE.put(DumpContentType.CURRENT, CompressionType.BZ2);
        COMPRESSION_TYPE.put(DumpContentType.FULL, CompressionType.BZ2);
        COMPRESSION_TYPE.put(DumpContentType.SITES, CompressionType.GZIP);
        COMPRESSION_TYPE.put(DumpContentType.JSON, CompressionType.GZIP);
        REVISION_DUMP = new HashMap();
        REVISION_DUMP.put(DumpContentType.DAILY, Boolean.valueOf(true));
        REVISION_DUMP.put(DumpContentType.CURRENT, Boolean.valueOf(true));
        REVISION_DUMP.put(DumpContentType.FULL, Boolean.valueOf(true));
        REVISION_DUMP.put(DumpContentType.SITES, Boolean.valueOf(false));
        REVISION_DUMP.put(DumpContentType.JSON, Boolean.valueOf(false));
    }
}
