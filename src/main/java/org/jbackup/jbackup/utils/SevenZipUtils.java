package org.jbackup.jbackup.utils;

import io.github.abarhub.vfs.core.api.VFS4JDefaultFileManager;
import io.github.abarhub.vfs.core.api.VFS4JFiles;
import io.github.abarhub.vfs.core.api.path.VFS4JPathName;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Hex;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.jbackup.jbackup.shadowcopy.StreamGobbler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

//@Service
public class SevenZipUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(SevenZipUtils.class);

    //    @Value("${path-sevenzip:}")
    private String pathSevenZip;

    //    @Value("${volume-size:}")
    private String volumeSize;

    private ExecutorService executorService;

    public SevenZipUtils(String pathSevenZip, String volumeSize) {
        this.pathSevenZip = pathSevenZip;
        this.volumeSize = volumeSize;
    }

    public void init() {
        BasicThreadFactory factory = new BasicThreadFactory.Builder()
                .namingPattern("seven-zip-%d")
                .uncaughtExceptionHandler((thread, exception) -> {
                    LOGGER.error("Erreur (thread: {})", thread, exception);
                })
                .build();
        executorService = Executors.newCachedThreadPool(factory);
    }

    public void terminate() {
        LOGGER.info("fin du executorService");
        executorService.shutdown();
        LOGGER.info("fin du executorService OK");
    }

    public void compress(VFS4JPathName source, VFS4JPathName destination, List<String> exclude,
                         List<String> include, boolean crypt, String password) throws IOException, InterruptedException, DecoderException {

        if (!StringUtils.hasText(pathSevenZip)) {
            throw new IllegalArgumentException("config 'path-sevenzip' empty");
        }

        var filemanager = VFS4JDefaultFileManager.get();
        Path sourceFile = filemanager.getRealFile(source);
        Path destinationFile = filemanager.getRealFile(destination);

        var dest = compression(sourceFile, destinationFile, exclude, include, crypt, password);

        verifieFichier(dest, crypt, password);

        var dest2 = filemanager.convertFromRealPath(dest);
        if (dest2.isEmpty()) {
            throw new IOException("Impossible de traiter le fichier " + dest);
        }

        LOGGER.info("calcul du hash ...");
        construitHash(dest2.get());
        LOGGER.info("calcul du hash ok");

        LOGGER.info("vérification des hash ...");
        verifieHash(dest2.get());
        LOGGER.info("vérification des hash ok");
    }

    public Path compression(Path sourceFile, Path destinationFile, List<String> exclude,
                            List<String> include, boolean crypt, String password) throws IOException, InterruptedException {

        final List<String> listeResultat = new Vector<>();
        Consumer<String> consumer = (x) -> {
            LOGGER.debug("stdout compress: {}", x);
            listeResultat.add(x);
        };
        List<String> listParameter = new ArrayList<>();
        listParameter.add(pathSevenZip);
        listParameter.add("a");
        if (!CollectionUtils.isEmpty(exclude)) {
            for (var ex : exclude) {
                if (StringUtils.hasText(ex)) {
                    listParameter.add("-xr!" + ex);
                }
            }
        }
        if (!CollectionUtils.isEmpty(include)) {
            for (var inc : include) {
                if (StringUtils.hasText(inc)) {
                    listParameter.add("-ir!" + inc);
                }
            }
        }
        if (crypt) {
            if (StringUtils.hasText(password)) {
                listParameter.add("-p" + password);
            } else {
                throw new IllegalArgumentException("Empty password");
            }
        }
        if (StringUtils.hasText(volumeSize)) {
            listParameter.add("-v" + volumeSize);
        }
        listParameter.add(destinationFile.toString());
        var s = sourceFile.toString();
        if (!s.endsWith("/") && !s.endsWith("\\")) {
            s += "\\.";
        }
        if(s.startsWith("\\\\.\\")){
            s="\\\\?\\"+s.substring(4);
        }
        listParameter.add(s);
        int res = runCommand(consumer, listParameter.toArray(new String[0]));
        LOGGER.info("res exec: {}", res);
        if (res != 0) {
            LOGGER.error("Erreur pour compresser {}", sourceFile);
            throw new IOException("Erreur pour créer le fichier " + destinationFile);
        } else {
            if (!Files.exists(destinationFile)) {
                var tmp = destinationFile.getParent().resolve(destinationFile.getFileName().toString() + ".001");
                if (Files.exists(tmp)) {
                    destinationFile = tmp;
                } else {
                    throw new IOException("Erreur pour la création du fichier : " + destinationFile);
                }
            }
            LOGGER.info("Fichier {} créé", destinationFile);
        }
        return destinationFile;
    }

    public void verifieFichier(Path destinationFile, boolean crypt, String password) throws IOException, InterruptedException {
        if (destinationFile == null) {
            throw new IllegalArgumentException("path null");
        }
        final List<String> listeResultat = new Vector<>();
        Consumer<String> consumer = (x) -> {
            LOGGER.info("stdout verif: {}", x);
            listeResultat.add(x);
        };
        List<String> listParameter = new ArrayList<>();
        listParameter.add(pathSevenZip);
        listParameter.add("t");
        if (crypt) {
            if (StringUtils.hasText(password)) {
                listParameter.add("-p" + password);
            } else {
                throw new IllegalArgumentException("Empty password");
            }
        }
        listParameter.add(destinationFile.toString());
        int res = runCommand(consumer, listParameter.toArray(new String[0]));
        LOGGER.info("res exec: {}", res);
        if (res != 0) {
            LOGGER.error("Erreur pour fichier le fichier {}", destinationFile);
            throw new IOException("Erreur pour tester le fichier " + destinationFile);
        } else {
            //VFS4JFiles.write(f, listeResultat, StandardCharsets.UTF_8);
            LOGGER.info("Fichier {} testé", destinationFile);
        }
    }

    private int runCommand(Consumer<String> consumer, String... commandes) throws InterruptedException, IOException {
        ProcessBuilder builder = new ProcessBuilder();
        List<String> liste = new ArrayList<>();
        List<String> listeShow = new ArrayList<>();
        for (String s : commandes) {
            var s2 = s;
            if (s.contains(" ")) {
                s2 = "\"" + s + "\"";
            }
            liste.add(s2);
            if (s.startsWith("-p")) {
                listeShow.add("-pXXX");
            } else {
                listeShow.add(s2);
            }
        }
        LOGGER.info("run {}", listeShow);
        LOGGER.trace("run {}", liste);
        builder.command(liste);
        Process process = builder.start();
        StreamGobbler streamGobbler =
                new StreamGobbler(process.getInputStream(), consumer::accept);
        executorService.submit(streamGobbler);
        StreamGobbler streamGobblerErrur =
                new StreamGobbler(process.getErrorStream(), (x) -> {
                    LOGGER.error("error: {}", x);
                });
        executorService.submit(streamGobblerErrur);
        LOGGER.info("run ...");
        var res = process.waitFor();
        LOGGER.info("run end");
        return res;
    }

    private void construitHash(VFS4JPathName dest) throws IOException {
        try (var f = VFS4JFiles.list(dest.getParent())) {
            var list = f.toList();
            for (var f2 : list) {
                if (VFS4JFiles.isDirectory(f2)) {
                    // on ne fait rien
                } else {
                    var filename = f2.getFilename();
                    if (filename != null && !filename.endsWith(".sha256")) {
                        var f3 = f2.getParent().resolve(filename + ".sha256");
                        creerHash(f2, f3);
                    }
                }
            }
        }
    }

    private void verifieHash(VFS4JPathName dest) throws IOException, DecoderException {
        try (var f = VFS4JFiles.list(dest.getParent())) {
            var list = f.toList();
            for (var f2 : list) {
                if (VFS4JFiles.isDirectory(f2)) {
                    // on ne fait rien
                } else {
                    var filename = f2.getFilename();
                    if (filename != null && !filename.endsWith(".sha256")) {
                        var f3 = f2.getParent().resolve(filename + ".sha256");
                        verifieHash(f2, f3);
                    }
                }
            }
        }
    }

    private void creerHash(VFS4JPathName fichierTeste, VFS4JPathName fichierHash) throws IOException {
        if (!VFS4JFiles.exists(fichierHash)) {
            byte[] sha256digest = DigestUtils.digest(DigestUtils.getSha256Digest(), VFS4JFiles.newInputStream(fichierTeste));
            List<String> liste = new ArrayList<>();
            liste.add(Hex.encodeHexString(sha256digest));
            VFS4JFiles.write(fichierHash, liste, StandardCharsets.UTF_8);
            LOGGER.info("hash hash256 '{}' OK", fichierHash);
        }
    }

    private void verifieHash(VFS4JPathName fichierTeste, VFS4JPathName fichierHash) throws IOException, DecoderException {
        if (!VFS4JFiles.exists(fichierHash)) {
            throw new IllegalArgumentException("Le fichier '" + fichierHash + "' n'existe pas");
        } else {
            byte[] sha256digest = DigestUtils.digest(DigestUtils.getSha256Digest(), VFS4JFiles.newInputStream(fichierTeste));
            List<String> liste = VFS4JFiles.readAllLines(fichierHash);
            if (CollectionUtils.isEmpty(liste)) {
                LOGGER.error("Le fichier hash '{}' est vide", fichierHash);
            } else if (sha256digest == null || sha256digest.length == 0) {
                LOGGER.error("Erreur pour calculer le hash du fichier '{}'", fichierHash);
            } else {
                String hashFichier = liste.get(0);
                if (Arrays.equals(sha256digest, Hex.decodeHex(hashFichier))) {
                    LOGGER.info("Vérification hash hash256 '{}' OK", fichierHash);
                } else {
                    LOGGER.info("Hash hash256 '{}' différent KO", fichierHash);
                }
            }
        }
    }

}
