package org.jbackup.jbackup.service;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.jbackup.jbackup.compress.*;
import org.jbackup.jbackup.config.CompressType;
import org.jbackup.jbackup.properties.GlobalProperties;
import org.jbackup.jbackup.properties.JBackupProperties;
import org.jbackup.jbackup.config.SaveProperties;
import org.jbackup.jbackup.exception.JBackupException;
import org.jbackup.jbackup.shadowcopy.ShadowCopy;
import org.jbackup.jbackup.utils.AESCrypt;
import org.jbackup.jbackup.utils.PathUtils;
import org.jbackup.jbackup.utils.SevenZipUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
//import org.springframework.util.StringUtils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


public class BackupService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BackupService.class);

    private final JBackupProperties jBackupProperties;

    private final BackupGithubService backupGithubService;

    public BackupService(JBackupProperties jBackupProperties,
                         BackupGithubService backupGithubService) {
        this.jBackupProperties = jBackupProperties;
        this.backupGithubService=backupGithubService;
    }

    public void backup() {
        try {
            LOGGER.info("backup ...");
            if (jBackupProperties.getDir() != null && !jBackupProperties.getDir().isEmpty()) {

                for (var entry : jBackupProperties.getDir().entrySet()) {
                    var save = entry.getValue();
                    if (save.isDisabled()) {
                        LOGGER.info("backup {} disabled", entry.getKey());
                    } else {
                        try (ShadowCopy shadowCopyUtils = new ShadowCopy()) {
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
                                            LOGGER.atInfo().log("compress {} ...", p);
                                            save3(compressWalk, p, "", save);
                                            LOGGER.atInfo().log("compress {} OK", p);
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
            if(jBackupProperties.getGithub()!=null&& StringUtils.isNotBlank(jBackupProperties.getGithub().getUser())
                &&!jBackupProperties.getGithub().isDisabled()){
                backupGithubService.backup(jBackupProperties.getGithub());
            }
            LOGGER.info("backup OK");
        } catch (Exception e) {
            LOGGER.error("Error", e);
        }
    }

    private void terminate(String file) {
        var listFiles = crypt(file);
        var fileHash = calculateHash(listFiles, file);
        checkFiles(file, listFiles, fileHash);
    }

    private void checkFiles(String file, List<String> listFiles, Path fileHash) {
        LOGGER.atInfo().log("Check files ...");
        Map<String, String> hash = new HashMap<>();
        try {
            var list = Files.readAllLines(fileHash);
            if (!CollectionUtils.isEmpty(list)) {
                int noline = 0;
                for (var s : list) {
                    var i = s.indexOf(' ');
                    if (i < 0) {
                        throw new JBackupException("has file invalid (i=" + i + ",noline=" + noline + ")");
                    }
                    var hashHexa = s.substring(0, i);
                    var filename = s.substring(i + 1);
                    hash.put(filename, hashHexa);
                    noline++;
                }
            }
        } catch (IOException e) {
            throw new JBackupException("Error for read hash", e);
        }
        for (var p : listFiles) {
            if (p.endsWith(".crp")) {
                Path fileNotCrypted = Path.of(p.substring(0, p.length() - 4));
                if (Files.notExists(fileNotCrypted)) {
                    throw new JBackupException("File '" + fileNotCrypted + "' not exist");
                }
                try {
                    Path fileDecrypted = null;
                    try {
                        fileDecrypted = Files.createTempFile("jbackup", "tmp");
                        AESCrypt crypt = new AESCrypt(false, jBackupProperties.getGlobal().getPassword());
                        crypt.decrypt(p, fileDecrypted.toString());
                        var len = Files.mismatch(fileDecrypted, fileDecrypted);
                        if (len != -1) {
                            throw new JBackupException("file not same (" + fileDecrypted + "," + p + ")");
                        } else {
                            LOGGER.atInfo().log("file {} decrypt ok", p);
                        }

                        var s = fileNotCrypted.getFileName().toString();
                        if (hash.containsKey(s)) {
                            var hashRef = hash.get(s);
                            String sha3Hex = new DigestUtils("SHA-256").digestAsHex(fileNotCrypted);
                            if (Objects.equals(hashRef, sha3Hex)) {
                                LOGGER.info("hash ok for {}", s);
                            } else {
                                LOGGER.atError().log("Invalide hash for {} ('{}'<>'{}')", s, hashRef, sha3Hex);
                                throw new JBackupException("Invalide hash for " + fileNotCrypted + " ( '" + hashRef + "','" + sha3Hex + "'");
                            }
                        }

                        if (fileNotCrypted.toString().endsWith(".zip")) {
                            LOGGER.atInfo().log("Check zip file {} ...", fileNotCrypted);
                            checkZip(fileNotCrypted);
                            LOGGER.atInfo().log("Check zip file {} OK", fileNotCrypted);
                        } else {
                            LOGGER.atWarn().log("Check file {} ignored", fileNotCrypted);
                        }

                    } finally {
                        if (fileDecrypted != null) {
                            Files.delete(fileDecrypted);
                        }
                    }
                } catch (IOException | GeneralSecurityException e) {
                    throw new JBackupException("Error for decrypt file " + p, e);
                }
                var p2 = Path.of(p);
                var s = p2.getFileName().toString();
                if (hash.containsKey(s)) {
                    try {
                        var hashRef = hash.get(s);
                        String sha3Hex = new DigestUtils("SHA-256").digestAsHex(p2);
                        if (Objects.equals(hashRef, sha3Hex)) {
                            LOGGER.info("hash ok for {}", p);
                        } else {
                            LOGGER.atError().log("Invalide hash for {} ('{}'<>'{}') (size={})", p, hashRef, sha3Hex, Files.size(p2));
                            throw new JBackupException("Invalide hash for " + p + " ( '" + hashRef + "','" + sha3Hex + "'");
                        }
                    } catch (IOException e) {
                        throw new JBackupException("Error for calculate hash for file " + s);
                    }
                } else {
                    throw new JBackupException("Hash not found for file " + s + " in " + fileHash);
                }
            } else {
                LOGGER.atWarn().log("file {} ignored", p);
            }

        }
        LOGGER.atInfo().log("Check files OK");
    }

    private void checkZip(Path zip) {
        try {
            SevenZipUtils sevenZipUtils = new SevenZipUtils(jBackupProperties.getGlobal().getPath7zip(), null);
            try {
                sevenZipUtils.init();
                sevenZipUtils.verifieFichier(zip, true, jBackupProperties.getGlobal().getPassword());
            } finally {
                sevenZipUtils.terminate();
            }
        } catch (IOException | InterruptedException e) {
            throw new JBackupException("Error for check of file " + zip, e);
        }
    }

    private Path calculateHash(List<String> listFiles, String file) {
        var name = FilenameUtils.removeExtension(file);
        var f = Path.of(name + ".sha256");
        List<String> liste = new ArrayList<>();
        try {
            DigestUtils digest = new DigestUtils("SHA-256");
            for (var p : listFiles) {
                LOGGER.atInfo().log("calculate hash for file {}", p);
                Path p2 = Path.of(p);
                String sha3Hex = digest.digestAsHex(p2);
                var s = sha3Hex + " " + p2.getFileName();
                LOGGER.info("hash {} for {} ({})", sha3Hex, p2.getFileName(), Files.size(p2));
                liste.add(s);
            }
            Files.write(f, liste);
        } catch (IOException e) {
            throw new JBackupException("error for calculate hash");
        }
        LOGGER.atInfo().log("file {} created", f);
        return f;
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
                    LOGGER.info("ignore {}", x);
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
                            LOGGER.info("not include {}", x);
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
        return jBackupProperties.getGlobal().isShadowCopy() && isWindows;
    }

    private void save2(ZipFile zipOut, Path p, String directory, SaveProperties save) {
        try (var listFiles = Files.list(p)) {
            listFiles.forEach(x -> {
                if (exclude(x, save)) {
                    LOGGER.debug("ignore {}", x);
                } else {
                    if (Files.isDirectory(x)) {
                        var dir = "";
                        if (StringUtils.isNotBlank(directory)) {
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
                                if (StringUtils.isNotBlank(directory)) {
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
                        if (StringUtils.isNotBlank(directory)) {
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
                if (StringUtils.isNotBlank(directory)) {
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
