package org.jbackup.jbackup.compress;

import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.compress.utils.IOUtils;
import org.jbackup.jbackup.exception.JBackupException;
import org.jbackup.jbackup.service.BackupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Optional;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;

public class CompressZipApache implements CompressWalk {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompressZipApache.class);

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
//            var entry=createZipEntry(p,name);
            ZipArchiveEntry entry_1;
//            entry_1 = new ZipArchiveEntry(entry);
            entry_1 = new ZipArchiveEntry(p.toFile(),name);
            archive.putArchiveEntry(entry_1);
//            try (var input = Files.newInputStream(p)) {
//                IOUtils.copy(input, archive);
//            }
            var s=p.toString();
            if(s.startsWith("\\\\.\\")){
                s="\\\\?\\"+s.substring(4);
            }
            var f=new File(s);
            if(!f.exists()) {
                LOGGER.warn("file '{}' n'existe pas",f);
            }
//            try (var input = new FileInputStream(p.toFile())) {
            try (var input = new FileInputStream(f)) {
                IOUtils.copy(input, archive);
            }
            archive.closeArchiveEntry();
        } catch (IOException e) {
            throw new JBackupException("Error for add file " + name, e);
        }
    }

    private ZipEntry createZipEntry(Path p, String name) throws IOException {
        boolean isDirectory=Files.isDirectory(p);
        ZipEntry entry;
        if(isDirectory) {
            entry = new ZipEntry(name+"/");
        } else {
            entry = new ZipEntry(name);
        }
        boolean error=false;
        try {
            final BasicFileAttributes attributes = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class);
            if (attributes.isRegularFile()) {
                entry.setSize(attributes.size());
            }
            entry.setLastModifiedTime(attributes.lastModifiedTime());
            entry.setCreationTime(attributes.creationTime());
            entry.setLastAccessTime(attributes.lastAccessTime());
        }catch (IOException e){
            error=true;
            LOGGER.atError().log("Error for read attribut of '{}' : {}",p,e.getMessage());
        }
        if(error) {
            LOGGER.atWarn().log("get failover for last modified time");
//            entry.setLastAccessTime(Files.getLastModifiedTime(p));
            //entry.setCreationTime(Files.)
            entry.setLastModifiedTime(Files.getLastModifiedTime(p));
        }
        return entry;
    }

    @Override
    public void addDir(String name, Path p) {
        try {
            var entry=createZipEntry(p,name);
            ZipArchiveEntry entry_1;
//            entry_1 = new ZipArchiveEntry(entry);
            entry_1 = new ZipArchiveEntry(p.toFile(),name);
            archive.putArchiveEntry(entry_1);
            archive.closeArchiveEntry();
        } catch (IOException e) {
            throw new JBackupException("Error for add dir " + name, e);
        }
    }
}
