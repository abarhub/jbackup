package org.jbackup.jbackup.compress;

import org.jbackup.jbackup.config.GlobalProperties;
import org.jbackup.jbackup.config.SaveProperties;
import org.jbackup.jbackup.exception.JBackupException;
import org.jbackup.jbackup.utils.SevenZipUtils;

import java.io.IOException;
import java.nio.file.Path;
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
            sevenZipUtils.compression(p, dest, List.of(), global.isCrypt(), global.getPassword());
        } catch (IOException | InterruptedException e) {
            throw new JBackupException("Error for compress " + save.getPath(), e);
        }
    }
}
