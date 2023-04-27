package org.jbackup.jbackup.compress;

import org.jbackup.jbackup.exception.JBackupException;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class CompressZip implements CompressWalk {

    private final String path;
    private FileOutputStream fos;
    private ZipOutputStream zipOut;

    public CompressZip(String path) {
        this.path = path;
    }

    @Override
    public void start() {
        try {
            fos = new FileOutputStream(this.path);
            zipOut = new ZipOutputStream(fos);
        } catch (IOException e) {
            throw new JBackupException("Error for open for write " + path, e);
        }
    }

    @Override
    public void close() {
        try {
            if (zipOut != null) {
                zipOut.close();
            }
            if (fos != null) {
                fos.close();
            }
        } catch (IOException e) {
            throw new JBackupException("Error for close " + path, e);
        }
    }

    @Override
    public void addFile(String name, Path p) {
        try {
            try (var fis = Files.newInputStream(p)) {
                var dir = name;
//                if (StringUtils.hasText(directory)) {
//                    dir = directory + "/" + x.getFileName();
//                } else {
//                    dir = x.getFileName().toString();
//                }
                ZipEntry zipEntry = new ZipEntry(dir);
                zipOut.putNextEntry(zipEntry);

                byte[] bytes = new byte[1024];
                int length;
                while ((length = fis.read(bytes)) >= 0) {
                    zipOut.write(bytes, 0, length);
                }
            }
        } catch (IOException e) {
            throw new JBackupException("error for add file " + p, e);
        }
    }
}
