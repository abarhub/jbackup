package org.jbackup.jbackup.compress;

import org.apache.commons.io.IOUtils;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.jbackup.jbackup.exception.JBackupException;
import org.jbackup.jbackup.service.RunService;
import org.jbackup.jbackup.shadowcopy.StreamGobbler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;

public class CompressZipApache implements CompressWalk {

    private static final Logger LOGGER = LoggerFactory.getLogger(CompressZipApache.class);

    private final String file;

    private ZipArchiveOutputStream archive;

    private Optional<Long> splitSize;

//    private ExecutorService executorService;

    private final RunService runService;

    private final int zipBuffer;

    private final byte[] buffer;

    public CompressZipApache(String file, Optional<Long> splitSize, RunService runService, int zipBuffer) {
        this.file = file;
        this.splitSize = splitSize;
        this.runService=Objects.requireNonNull(runService,"runService is null");
        this.zipBuffer=zipBuffer;
        if(zipBuffer>0){
            buffer=new byte[zipBuffer];
        } else {
            buffer=null;
        }
    }

    public void init() {
//        LOGGER.info("init executorService");
//        BasicThreadFactory factory = new BasicThreadFactory.Builder()
//                .namingPattern("substr-%d")
//                .uncaughtExceptionHandler((thread, exception) -> {
//                    LOGGER.error("Erreur (thread: {})", thread, exception);
//                })
//                .build();
//        executorService = Executors.newCachedThreadPool(factory);
    }

    public void terminate() {
//        LOGGER.info("fin du executorService");
//        executorService.shutdown();
//        LOGGER.info("fin du executorService OK");
    }

    @Override
    public void start() {
        try {
            init();
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
            if(archive!=null) {
                archive.finish();
                archive.close();
            }
            terminate();
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
            entry_1 = new ZipArchiveEntry(p.toFile(), name);
            archive.putArchiveEntry(entry_1);
//            try (var input = Files.newInputStream(p)) {
//                IOUtils.copy(input, archive);
//            }
            var s = p.toString();
            if (s.startsWith("\\\\.\\")) {
                s = "\\\\?\\" + s.substring(4);
            }
            var f = new File(s);
            if (!f.exists()) {
                LOGGER.warn("file '{}' n'existe pas", f);
            }
            var simlink = false;
            Path link = null;
            var drive = false;
            String lettreTrouve = null;
            if (false) {
                if (s.length() > 250) {
                    LOGGER.atInfo().log("create symlink({}) '{}' ...", s.length(), s);
                    int i = 1;
                    var debut = "c:\\link";
                    link = Paths.get(debut);
                    while (Files.exists(link, LinkOption.NOFOLLOW_LINKS)) {
                        link = Paths.get(debut + i);
                        i++;
                    }
                    LOGGER.atInfo().log("create symlink '{}' -> '{}'", link, s);
                    Files.createSymbolicLink(link, p);
                    f = link.toFile();
                    LOGGER.atInfo().log("create symlink ok");
                }
            } else if(true) {
                if (s.length() > 250) {
                    var list = Arrays.stream(File.listRoots())
                            .map(x -> x.getName())
                            .map(x -> x.toUpperCase())
                            .sorted()
                            .collect(Collectors.toList());
                    LOGGER.atInfo().log("letter:{}", list);
                    for (char c = 'Z'; c > 'C'; c--) {
                        if (!list.contains("" + c)) {
                            lettreTrouve = "" + c;
                            break;
                        }
                    }
                    if (lettreTrouve != null) {
                        final List<String> listeResultat = new Vector<>();
                        Consumer<String> consumer = (x) -> {
                            LOGGER.debug("stdout compress: {}", x);
                            listeResultat.add(x);
                        };
                        String s2 = "";
                        if (Files.isDirectory(p)) {
//                            s2 = p.toString();
                            throw new JBackupException("Erreur:" + p);
                        } else {
                            s2 = p.getParent().toString();
                            f = new File(lettreTrouve + ":" + p.getFileName());
                            LOGGER.info("long file name : {}", f);
                        }
                        List<String> listParameter = new ArrayList<>();
                        listParameter.add("subst");
                        listParameter.add(lettreTrouve + ":");
                        listParameter.add(s2);
                        try {
                            int res = runService.runCommand(consumer, listParameter,null);
                            LOGGER.info("res exec: {}", res);
                            if (res != 0) {
                                LOGGER.error("Erreur pour creer le drive {}", lettreTrouve);
                                throw new IOException("Erreur pour créer le drive " + lettreTrouve);
                            } else {
                                drive = true;
                                LOGGER.atInfo().log("driver {} cree", lettreTrouve);
                            }
                        } catch (IOException | InterruptedException e) {
                            throw new JBackupException("Erreur pour crer le drive " + lettreTrouve, e);
                        }
                    }
                }
            }

            var f2=f;
            if(f.getName().contains("+++5gyzk7t89hc-496ff2e9c6d22116")){
                //f2=new File("\""+f+"\"");
                LOGGER.info("f2={}",f2);
            }
//            try (var input = new FileInputStream(p.toFile())) {
            try (var input = new FileInputStream(f2)) {
                if(zipBuffer>0){
                    IOUtils.copyLarge(input, archive, buffer);
                } else {
                    IOUtils.copy(input, archive);
                }
            } catch (FileNotFoundException e) {
                LOGGER.atError().log("Erreur pour lire le fichier {}",f, e);
                throw e;
            }
            if (link != null) {
                LOGGER.atInfo().log("delete symlink {}", link);
                Files.delete(link);
            }
            if (drive&&lettreTrouve != null) {
                final List<String> listeResultat = new Vector<>();
                Consumer<String> consumer = (x) -> {
                    LOGGER.debug("stdout compress: {}", x);
                    listeResultat.add(x);
                };
                List<String> listParameter = new ArrayList<>();
                listParameter.add("subst");
                listParameter.add(lettreTrouve + ":");
                listParameter.add("/D");
                try {
                    int res =runService.runCommand(consumer, listParameter,null);
//                    int res = runCommand(consumer, listParameter.toArray(new String[0]));
                    LOGGER.info("res exec: {}", res);
                    if (res != 0) {
                        LOGGER.error("Erreur pour supprimer le drive {}", lettreTrouve);
                        throw new IOException("Erreur pour supprimer le drive {} " + lettreTrouve);
                    } else {
                        drive = true;
                        LOGGER.atInfo().log("driver {} supprimé", lettreTrouve);
                    }
                } catch (IOException | InterruptedException e) {
                    throw new JBackupException("Erreur pour supprimer le drive", e);
                }
            }
            archive.closeArchiveEntry();
        } catch (IOException e) {
            throw new JBackupException("Error for add file " + name, e);
        }finally {

        }
    }


//    private int runCommand(Consumer<String> consumer, String... commandes) throws InterruptedException, IOException {
//        ProcessBuilder builder = new ProcessBuilder();
//        List<String> liste = new ArrayList<>();
//        List<String> listeShow = new ArrayList<>();
//        for (String s : commandes) {
//            var s2 = s;
//            if (s.contains(" ")) {
//                s2 = "\"" + s + "\"";
//            }
//            liste.add(s2);
//            if (s.startsWith("-p")) {
//                listeShow.add("-pXXX");
//            } else {
//                listeShow.add(s2);
//            }
//        }
//        LOGGER.info("run {}", listeShow);
//        LOGGER.trace("run {}", liste);
//        builder.command(liste);
//        Process process = builder.start();
//        StreamGobbler streamGobbler =
//                new StreamGobbler(process.getInputStream(), consumer::accept);
//        executorService.submit(streamGobbler);
//        StreamGobbler streamGobblerErrur =
//                new StreamGobbler(process.getErrorStream(), (x) -> {
//                    LOGGER.error("error: {}", x);
//                });
//        executorService.submit(streamGobblerErrur);
//        LOGGER.info("run ...");
//        var res = process.waitFor();
//        LOGGER.info("run end");
//        return res;
//    }

    private ZipEntry createZipEntry(Path p, String name) throws IOException {
        boolean isDirectory = Files.isDirectory(p);
        ZipEntry entry;
        if (isDirectory) {
            entry = new ZipEntry(name + "/");
        } else {
            entry = new ZipEntry(name);
        }
        boolean error = false;
        try {
            final BasicFileAttributes attributes = Files.readAttributes(p, java.nio.file.attribute.BasicFileAttributes.class);
            if (attributes.isRegularFile()) {
                entry.setSize(attributes.size());
            }
            entry.setLastModifiedTime(attributes.lastModifiedTime());
            entry.setCreationTime(attributes.creationTime());
            entry.setLastAccessTime(attributes.lastAccessTime());
        } catch (IOException e) {
            error = true;
            LOGGER.atError().log("Error for read attribut of '{}' : {}", p, e.getMessage());
        }
        if (error) {
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
//            var entry = createZipEntry(p, name);
            ZipArchiveEntry entry_1;
//            entry_1 = new ZipArchiveEntry(entry);
            entry_1 = new ZipArchiveEntry(p.toFile(), name);
            archive.putArchiveEntry(entry_1);
            archive.closeArchiveEntry();
        } catch (IOException e) {
            throw new JBackupException("Error for add dir " + name, e);
        }
    }
}
