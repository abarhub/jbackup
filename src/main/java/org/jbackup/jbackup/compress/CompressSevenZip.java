package org.jbackup.jbackup.compress;

import org.apache.commons.lang3.StringUtils;
import org.jbackup.jbackup.properties.GlobalProperties;
import org.jbackup.jbackup.config.SaveProperties;
import org.jbackup.jbackup.exception.JBackupException;
import org.jbackup.jbackup.utils.SevenZipUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class CompressSevenZip implements CompressTask {

    private final String path;
    private final SevenZipUtils sevenZipUtils;

    private final GlobalProperties global;
    private final SaveProperties save;

    public CompressSevenZip(String filename, GlobalProperties global, SaveProperties save) {
        this.path = filename;
        this.global = global;
        this.save = save;
        this.sevenZipUtils=new SevenZipUtils(global.getPath7zip(), null);
    }

    @Override
    public void start() {
        sevenZipUtils.init();
    }

    @Override
    public void close() {
        sevenZipUtils.terminate();
    }

    @Override
    public void task(Path p) {
        try {
            Path dest = Path.of(path);
            List<String> exclude=new ArrayList<>();
            for(var s:save.getExclude()){
                if(StringUtils.isNotBlank(s)) {
                    s = s.replaceAll("\\*\\*", "*");
                    exclude.add(s);
                }
            }
            List<String> include=new ArrayList<>();
            for(var s:save.getInclude()){
                if(StringUtils.isNotBlank(s)) {
                    s = s.replaceAll("\\*\\*", "*");
                    include.add(s);
                }
            }
            sevenZipUtils.compression(p, dest, exclude, include,global.isCrypt(), global.getPassword());
        } catch (IOException | InterruptedException e) {
            throw new JBackupException("Error for compress " + save.getPath(), e);
        }
    }
}
