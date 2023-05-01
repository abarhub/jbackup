package org.jbackup.jbackup.compress;

import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.AesKeyStrength;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import net.lingala.zip4j.model.enums.EncryptionMethod;
import org.jbackup.jbackup.exception.JBackupException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class CompressZip4j implements CompressWalk {

    private final String path;

    private final boolean crypt;

    private final String password;
    private ZipFile zipFile;

    public CompressZip4j(String path, boolean crypt, String password) {
        this.path = path;
        this.crypt = crypt;
        this.password = password;
    }

    @Override
    public void start() {
        char[] password = null;
        if (crypt) {
            password = this.password.toCharArray();
        }
        zipFile = new ZipFile(path, password);
    }

    @Override
    public void close() {
        try {
            zipFile.close();
        } catch (IOException e) {
            throw new JBackupException("Error for close", e);
        }
    }

    @Override
    public void addFile(String name, Path p) {
        try (var input = Files.newInputStream(p)) {
            ZipParameters zipParameters = new ZipParameters();
            var dir = name;
//            if (StringUtils.hasText(directory)) {
//                dir = directory + "/" + x.getFileName();
//            } else {
//                dir = x.getFileName().toString();
//            }
            zipParameters.setFileNameInZip(dir);
            var lastModified = Files.getLastModifiedTime(p);
            zipParameters.setLastModifiedFileTime(lastModified.toMillis());
            zipParameters.setCompressionLevel(CompressionLevel.MAXIMUM);
            zipParameters.setCompressionMethod(CompressionMethod.STORE);
            if (crypt) {
                zipParameters.setEncryptFiles(true);
                zipParameters.setEncryptionMethod(EncryptionMethod.AES);
                zipParameters.setAesKeyStrength(AesKeyStrength.KEY_STRENGTH_256);
            }
            zipFile.addStream(input, zipParameters);
        } catch (Exception e) {
            throw new JBackupException("Error for add file " + p, e);
        }
    }

    @Override
    public void addDir(String name, Path p) {

    }
}
