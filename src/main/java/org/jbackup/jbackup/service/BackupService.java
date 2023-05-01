package org.jbackup.jbackup.service;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.jbackup.jbackup.compress.*;
import org.jbackup.jbackup.config.CompressType;
import org.jbackup.jbackup.config.GlobalProperties;
import org.jbackup.jbackup.config.JBackupProperties;
import org.jbackup.jbackup.config.SaveProperties;
import org.jbackup.jbackup.exception.JBackupException;
import org.jbackup.jbackup.shadowcopy.ShadowCopy;
import org.jbackup.jbackup.utils.AESCrypt;
import org.jbackup.jbackup.utils.PathUtils;
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
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


@Service
public class BackupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupService.class);

    private final JBackupProperties jBackupProperties;

    public BackupService(JBackupProperties jBackupProperties) {
        this.jBackupProperties = jBackupProperties;
    }

    public void backup() {
        try {
            LOGGER.info("backup ...");
            if (jBackupProperties.getDir() != null && !jBackupProperties.getDir().isEmpty()) {
                try (ShadowCopy shadowCopyUtils = new ShadowCopy()) {
                    for (var entry : jBackupProperties.getDir().entrySet()) {
                        var save = entry.getValue();
                        if (save.isDisabled()) {
                            LOGGER.info("backup {} disabled", entry.getKey());
                        } else {
                            Instant debut = Instant.now();
                            LOGGER.info("backup {} ...", entry.getKey());
                            LOGGER.info("backup from {} to {}", save.getPath(), save.getDest());
                            for (var path : save.getPath()) {
                                Path p = Path.of(path);
                                if (isShadowCopy()) {
                                    p = shadowCopyUtils.getPath(p.toAbsolutePath());
                                }

                                if (false) {
                                    var pathZip = save.getDest() + "/" + entry.getKey() + "_" + Instant.now().getEpochSecond() + ".zip";
                                    char[] password = null;
                                    if (jBackupProperties.getGlobal().isCrypt()) {
                                        password = jBackupProperties.getGlobal().getPassword().toCharArray();
                                    }
                                    try (ZipFile zipFile = new ZipFile(pathZip, password)) {

                                        save2(zipFile, p, "", save);

                                    }

                                } else if (false) {
                                    try (FileOutputStream fos = new FileOutputStream(save.getDest() + "/" + entry.getKey() + "_" + Instant.now().getEpochSecond() + ".zip")) {
                                        ZipOutputStream zipOut = new ZipOutputStream(fos);

                                        save(zipOut, p, "", save);

                                        zipOut.close();
                                    }
                                } else {
                                    String filename;
                                    String extension = getExtension(jBackupProperties.getGlobal());
                                    filename = PathUtils.getPath(save.getDest(), entry.getKey() + "_" + Instant.now().getEpochSecond() + extension);
                                    try (Compress compress = buildCompress(filename, save, jBackupProperties.getGlobal())) {
                                        compress.start();
                                        if (compress instanceof CompressWalk compressWalk) {
                                            save3(compressWalk, p, "", save);
                                        } else {
                                            var compressTask = (CompressTask) compress;
                                            compressTask.task(p);
                                        }
                                    }
                                    terminate(filename);
                                }
                            }
                            LOGGER.info("backup {} ok ({})", entry.getKey(), Duration.between(debut, Instant.now()));
                        }
                    }
                }
            }
            LOGGER.info("backup OK");
        } catch (Exception e) {
            LOGGER.error("Error", e);
        }
    }

    private void terminate(String file) {
        var listFiles = crypt(file);
        calculateHash(listFiles, file);
    }

    private void calculateHash(List<String> listFiles, String file) {
        var name = FilenameUtils.removeExtension(file);
        var f = Path.of(name + ".sha256");
        List<String> liste = new ArrayList<>();
        try {
            for (var p : listFiles) {
                Path p2 = Path.of(p);
                String sha3Hex = new DigestUtils("SHA-256").digestAsHex(p2);
                var s = sha3Hex + " " + p2.getFileName();
                liste.add(s);
            }
            Files.write(f, liste);
        } catch (IOException e) {
            throw new JBackupException("error for calculate hash");
        }

    }

    private List<String> crypt(String file) {
        List<String> listeFiles = new ArrayList<>();
        listeFiles.add(file);
        var p = Path.of(file);
        if (Files.exists(p)) {
            try {
                var fileCrypt = p + ".crp";
                listeFiles.add(fileCrypt);
                AESCrypt crypt = new AESCrypt(false, jBackupProperties.getGlobal().getPassword());
                crypt.encrypt(2, p.toString(), fileCrypt);
            } catch (IOException | GeneralSecurityException e) {
                throw new JBackupException("Error for crypt", e);
            }
        }
        List<Path> liste;
        try {
            liste = Files.list(p.getParent())
                    .filter(x -> {
                        var x1 = x.getFileName().toString();
                        var x2 = p.getFileName().toString();
                        return x1.startsWith(x2 + ".z") || x1.startsWith(x2 + ".Z");
                    })
                    .toList();
        } catch (IOException e) {
            throw new JBackupException("Error for list files", e);
        }
        for (var p3 : liste) {
            try {
                listeFiles.add(p3.toString());
                var fileCrypt = p3 + ".crp";
                listeFiles.add(fileCrypt);
                AESCrypt crypt = new AESCrypt(false, jBackupProperties.getGlobal().getPassword());
                crypt.encrypt(2, p3.toString(), fileCrypt);
            } catch (IOException | GeneralSecurityException e) {
                throw new JBackupException("Error for crypt", e);
            }
        }
        LOGGER.info("liste files={}", listeFiles);
        return listeFiles;
    }

    private String getExtension(GlobalProperties global) {
        if (global.getCompress() == CompressType.SEVENZIP) {
            return ".7z";
        } else {
            return ".zip";
        }
    }

    private Compress buildCompress(String filename, SaveProperties save, GlobalProperties global) {
        Compress compress;
        if (global.getCompress() == null || global.getCompress() == CompressType.ZIP) {
            compress = new CompressZip(filename);
        } else if (global.getCompress() == CompressType.ZIP4J) {
            compress = new CompressZip4j(filename,
                    jBackupProperties.getGlobal().isCrypt(),
                    jBackupProperties.getGlobal().getPassword());
        } else if (global.getCompress() == CompressType.SEVENZIP) {
            compress = new CompressSevenZip(filename, jBackupProperties.getGlobal(), save);
        } else if (global.getCompress() == CompressType.ZIPAPACHE) {
            Optional<Long> splitSize;
            if (global.getSplitSize() != null) {
                splitSize = Optional.of(global.getSplitSize().toBytes());
            } else {
                splitSize = Optional.empty();
            }
            compress = new CompressZipApache(filename, splitSize);
        } else {
            throw new JBackupException("Invalid value for compress: " + global.getCompress());
        }
        return compress;
    }

    private void save3(CompressWalk compress, Path p, String directory, SaveProperties save) {
        try (var listFiles = Files.list(p)) {
            listFiles.forEach(x -> {
                if (exclude(x, save)) {
                    LOGGER.debug("ignore {}", x);
                } else {
                    if (Files.isDirectory(x)) {
                        var dir = PathUtils.getPath(directory, x.getFileName().toString());
                        compress.addDir(dir, x);
                        save3(compress, x, dir, save);
                    } else {
                        if (include(x, save)) {
                            var dir = PathUtils.getPath(directory, x.getFileName().toString());
                            compress.addFile(dir, x);
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

    private boolean isShadowCopy() {
        boolean isWindows = System.getProperty("os.name")
                .toLowerCase().startsWith("windows");
        return isWindows;
    }

    private void save2(ZipFile zipOut, Path p, String directory, SaveProperties save) {
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
                        save2(zipOut, x, dir, save);
                    } else {
                        if (include(x, save)) {
                            //addFile(zipOut, directory, x);
                            try (var input = Files.newInputStream(x)) {
                                ZipParameters zipParameters = new ZipParameters();
                                var dir = "";
                                if (StringUtils.hasText(directory)) {
                                    dir = directory + "/" + x.getFileName();
                                } else {
                                    dir = x.getFileName().toString();
                                }
                                zipParameters.setFileNameInZip(dir);
                                var lastModified = Files.getLastModifiedTime(x);
                                zipParameters.setLastModifiedFileTime(lastModified.toMillis());
                                zipParameters.setCompressionLevel(CompressionLevel.MAXIMUM);
                                zipParameters.setCompressionMethod(CompressionMethod.STORE);
                                if (jBackupProperties.getGlobal().isCrypt()) {
                                    zipParameters.setEncryptFiles(true);
                                    zipParameters.setEncryptionMethod(EncryptionMethod.AES);
                                    zipParameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
                                }
                                zipOut.addStream(input, zipParameters);
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
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

    private void save(ZipOutputStream zipOut, Path p, String directory, SaveProperties save) {
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

    private boolean exclude(Path path, SaveProperties saveProperties) {
        if (!CollectionUtils.isEmpty(saveProperties.getExclude())) {
            for (var glob : saveProperties.getExclude()) {
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

    private boolean include(Path path, SaveProperties saveProperties) {
        if (!CollectionUtils.isEmpty(saveProperties.getInclude())) {
            for (var glob : saveProperties.getInclude()) {
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
