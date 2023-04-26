package org.jbackup.jbackup.service;

import org.jbackup.jbackup.config.JBackupConfig;
import org.jbackup.jbackup.config.SaveConfig;
import org.jbackup.jbackup.exception.JBackupException;
import org.jbackup.jbackup.shadowcopy.ShadowCopy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.time.Instant;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class BackupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupService.class);

    private final JBackupConfig jBackupConfig;

    public BackupService(JBackupConfig jBackupConfig) {
        this.jBackupConfig = jBackupConfig;
    }

    public void backup() {
        try {
            LOGGER.info("backup ...");
            if (jBackupConfig.getDir() != null && !jBackupConfig.getDir().isEmpty()) {
                try (ShadowCopy shadowCopyUtils = new ShadowCopy()) {
                    for (var entry : jBackupConfig.getDir().entrySet()) {
                        var save = entry.getValue();
                        if (save.isDisabled()) {
                            LOGGER.info("backup {} disabled", entry.getKey());
                        } else {
                            LOGGER.info("backup {} ...", entry.getKey());
                            LOGGER.info("backup from {} to {}", save.getPath(), save.getDest());
                            for (var path : save.getPath()) {
                                Path p = Path.of(path);
                                if(isShadowCopy()) {
                                    p = shadowCopyUtils.getPath(p.toAbsolutePath());
                                }

                                try (FileOutputStream fos = new FileOutputStream(save.getDest() + "/" + entry.getKey() + "_" + Instant.now().getEpochSecond() + ".zip")) {
                                    ZipOutputStream zipOut = new ZipOutputStream(fos);

                                    save(zipOut, p, "", save);

                                    zipOut.close();
                                }
                            }
                            LOGGER.info("backup {} ok", entry.getKey());
                        }
                    }
                }
            }
            LOGGER.info("backup OK");
        } catch (Exception e) {
            LOGGER.error("Error", e);
        }
    }

    private boolean isShadowCopy() {
        boolean isWindows = System.getProperty("os.name")
                    .toLowerCase().startsWith("windows");
        return isWindows;
    }

    private void save(ZipOutputStream zipOut, Path p, String directory, SaveConfig save) {
        try (var listFiles = Files.list(p)) {
            listFiles.forEach(x -> {
                if (exclude(x, save)) {
                    LOGGER.debug("ignore {}", x);
                } else {
                    if (Files.isDirectory(x)) {
                        var dir = "";
                        if (StringUtils.hasText(directory)) {
                            dir = directory + "/" + x.getFileName();
                        } else {
                            dir = x.getFileName().toString();
                        }
                        save(zipOut, x, dir, save);
                    } else {
                        if (include(x, save)) {
                            addFile(zipOut, directory, x);
                        } else {
                            LOGGER.debug("not include {}", x);
                        }
                    }
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("Error for save", e);
        }
    }

    private static void addFile(ZipOutputStream zipOut, String directory, Path x) {
        try {
            try (var fis = Files.newInputStream(x)) {
                var dir = "";
                if (StringUtils.hasText(directory)) {
                    dir = directory + "/" + x.getFileName();
                } else {
                    dir = x.getFileName().toString();
                }
                ZipEntry zipEntry = new ZipEntry(dir);
                zipOut.putNextEntry(zipEntry);

                byte[] bytes = new byte[1024];
                int length;
                while ((length = fis.read(bytes)) >= 0) {
                    zipOut.write(bytes, 0, length);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean exclude(Path path, SaveConfig saveConfig) {
        if (!CollectionUtils.isEmpty(saveConfig.getExclude())) {
            for (var glob : saveConfig.getExclude()) {
                try {
                    PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
                    if (pathMatcher.matches(path)) {
                        return true;
                    }
                } catch (Exception e) {
                    throw new JBackupException("Error for glob : " + glob, e);
                }
            }
        }
        return false;
    }

    private boolean include(Path path, SaveConfig saveConfig) {
        if (!CollectionUtils.isEmpty(saveConfig.getInclude())) {
            for (var glob : saveConfig.getInclude()) {
                try {
                    PathMatcher pathMatcher = FileSystems.getDefault().getPathMatcher("glob:" + glob);
                    if (pathMatcher.matches(path)) {
                        return true;
                    }
                } catch (Exception e) {
                    throw new JBackupException("Error for glob : " + glob, e);
                }
            }
            return false;
        }
        return true;
    }

}
