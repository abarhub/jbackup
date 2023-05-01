package org.jbackup.jbackup.compress;

import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.jbackup.jbackup.exception.JBackupException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;

public class CompressZipApache implements CompressWalk {

    private final String file;

    private ZipArchiveOutputStream archive;

    private Optional<Long> splitSize;

    public CompressZipApache(String file, Optional<Long> splitSize) {
        this.file = file;
        this.splitSize = splitSize;
    }

    @Override
    public void start() {
        try {
            var path = Path.of(file);
            if (splitSize.isPresent()) {
                long size = splitSize.get();
                archive = new ZipArchiveOutputStream(path, size);
            } else {
                archive = new ZipArchiveOutputStream(path);
            }
            archive.setLevel(Deflater.BEST_COMPRESSION);
            archive.setMethod(ZipEntry.DEFLATED);
            archive.setComment("Created using Java program.");
            archive.setUseZip64(Zip64Mode.Always);
        } catch (IOException e) {
            throw new JBackupException("Error for create archive " + file, e);
        }
    }

    @Override
    public void close() {
        try {
            archive.finish();
            archive.close();
        } catch (IOException e) {
            throw new JBackupException("Error for close", e);
        }
    }

    @Override
    public void addFile(String name, Path p) {
        try {
//        File file_1 = p.toFile();//new File("compress-me/file-to-compress-1.txt");
            ZipArchiveEntry entry_1 = new ZipArchiveEntry(p, name);
            archive.putArchiveEntry(entry_1);
            try (var input = Files.newInputStream(p)) {
                IOUtils.copy(input, archive);
            }
            archive.closeArchiveEntry();
        } catch (IOException e) {
            throw new JBackupException("Error for add file " + name, e);
        }
    }
}
