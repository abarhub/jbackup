package org.jbackup.jbackup.shadowcopy;

import org.jbackup.jbackup.exception.JBackupException;
import org.jbackup.jbackup.service.RunService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ShadowCopy implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShadowCopy.class);

    private final Map<Character, ShadowPath> map = new ConcurrentHashMap<>();

    private final Map<Path, Path> mapLink = new ConcurrentHashMap<>();

    private final AtomicInteger counter;

    private final RunService runService;

    public ShadowCopy(RunService runService,AtomicInteger compteur) {
        this.runService = runService;
        this.counter=compteur;
    }

    public Path getPath(Path path) {
        Assert.isTrue(path.isAbsolute(), "Path '" + path + "' must be absolute");
        var root = path.getRoot();
        LOGGER.info("root={},path={}", root, path);
        var s = root.toString();
        if (s.length() > 2) {
            if (s.charAt(1) == ':' && Character.isAlphabetic(s.charAt(0))) {
                var c = s.charAt(0);
                c = Character.toUpperCase(c);
                ShadowPath p;
                if (map.containsKey(c)) {
                    p = map.get(c);
                } else {
                    p = initPath(c);
                }
                var p2 = convertPath(p, path);
                if (p2 != null) {
                    LOGGER.info("{} -> {}", path, p2);
                    path = p2;
                }
            }
        }
        return path;
    }

    private Path convertPath(ShadowPath p, Path path) {
        var s = path.toString();
        var s2 = p.path().toString();
        s = s.substring(2);
        var s3 = s2 + s;
//        if(s3.startsWith("\\\\?\\")){
//            s3="\\\\.\\"+s3.substring(4);
//        }
        Path p3;
        if (!mapLink.containsKey(path)) {
            var linkDebut = p.volume() + ":/linkjb";
            var link = linkDebut;
            var i = counter.getAndIncrement();
            if (i > 1) {
                linkDebut += i;
            }
            while (Files.exists(Path.of(link))) {
                i = counter.getAndIncrement();
                link = linkDebut + i;
            }
            var linkp = Path.of(link);
            var cible = Path.of(s3).getParent();
            LOGGER.info("création du lien '{}' -> '{}'", linkp, cible);
            try {
                Files.createSymbolicLink(linkp, cible);
            } catch (IOException e) {
                throw new JBackupException("Erreur pour creer le lien '" + linkp + "' -> '" + cible + "' : " + e.getMessage(), e);
            }
            p3 = linkp.resolve(Path.of(s3).getFileName());
            mapLink.put(path, p3);
        } else {
            p3 = mapLink.get(path);
        }
//        return Path.of(s3);
        return p3;
    }

    private ShadowPath initPath(char volume) {
        var vOpt = createShadowCopy(volume);
        if (vOpt.isPresent()) {
            var pathOpt = getShadowCopy(volume, vOpt.get());
            if (pathOpt.isPresent()) {
                var s2 = pathOpt.get();
                if (s2.startsWith("\\\\?\\")) {
                    s2 = "\\\\.\\" + s2.substring(4);
                }
                var shadowPath = new ShadowPath(volume, Path.of(s2), vOpt.get());
                map.put(volume, shadowPath);
                return shadowPath;
            }
        }
        throw new JBackupException("Can't create shadow copy for " + volume);
    }

    public void close() {
        for (var entry : mapLink.entrySet()) {
            var path = entry.getValue().getParent();
            try {
                LOGGER.info("Suppression du link {} ...", path);
                //var deletedIfExists = Files.deleteIfExists(entry.getValue());
//                Files.delete(path);
                var deletedIfExists = Files.deleteIfExists(entry.getValue());
                LOGGER.info("Suppression du link {} OK (deleted={})", path, deletedIfExists);
            } catch (IOException e) {
                LOGGER.error("Can't delete link for {}", path, e);
            }
        }
        for (var entry : map.entrySet()) {
            deleteShadowCopy(entry.getKey(), entry.getValue().shadowId());
        }
    }

    private Optional<String> createShadowCopy(char volume) {
        try {
            LOGGER.info("create shadow copy for {} ...", volume);
            List<String> liste;
            var s = "";
            s = "(gwmi -list win32_shadowcopy).Create('" + volume + ":\\','ClientAccessible')";
            //s = "(Get-WmiObject -List Win32_ShadowCopy).Create('" + volume + ":\\', 'ClientAccessible')";
            liste = run(List.of("powershell.exe", "-Command", s),
                    true);
            LOGGER.info("create shadow copy for {} OK", volume);
            return liste.stream()
                    .filter(Objects::nonNull)
                    .filter(x -> x.contains("ShadowID"))
                    .map(x -> x.substring(x.indexOf(':') + 1).trim())
                    .findAny();
        } catch (Exception e) {
            LOGGER.error("Erreur", e);
            throw new JBackupException("Erreur pour creer le shadow copy", e);
        }
    }

    private void deleteShadowCopy(char volume, String shadowId) {
        try {
            LOGGER.info("delete shadow copy for {} ...", volume);
            if (StringUtils.hasText(shadowId)) {
                run(List.of("cmd.exe", "/c", "vssadmin", "Delete", "Shadows", "/Shadow=" + shadowId, "/Quiet"),
                        true);
            } else {
                run(List.of("cmd.exe", "/c", "vssadmin", "Delete", "Shadows", "/For=" + volume + ":", "/Quiet"),
                        true);
            }
            LOGGER.info("delete shadow copy for {} OK", volume);
        } catch (Exception e) {
            LOGGER.error("Erreur", e);
            throw new JBackupException("Erreur pour creer le shadow copy", e);
        }
    }


    private Optional<String> getShadowCopy(char volume, String shadowId) {
        try {
            LOGGER.info("list shadow copy for {}({}) ...", volume, shadowId);
            List<String> liste;
            if (StringUtils.hasText(shadowId)) {
                liste = run(List.of("powershell.exe",
                                "Get-WmiObject Win32_ShadowCopy | Where-Object { $_.ID -eq '" + shadowId + "' } | Select DeviceObject"),
                        true);
            } else {
                liste = run(List.of("cmd.exe", "/c", "vssadmin", "List", "Shadows", "/For=" + volume + ":"),
                        true);
            }
            LOGGER.info("list shadow copy for {} OK", volume);
            LOGGER.info("list shadow copy for {} :", liste);

            return liste.stream()
                    .filter(x -> x.startsWith("\\\\"))
                    .map(String::trim)
                    .findAny();
        } catch (Exception e) {
            LOGGER.error("Erreur", e);
            throw new JBackupException("Erreur pour creer le shadow copy", e);
        }
    }

    private List<String> run(List<String> commands, boolean logInfo) throws IOException, InterruptedException, ExecutionException, TimeoutException {
        LOGGER.atInfo().log("run of : {}", commands);
        TeeList liste = new TeeList(logInfo);
        runService.runCommand(liste::add,commands,null);
        return liste.getList();
    }

//    private List<String> run2(List<String> commands, boolean logInfo) throws IOException, InterruptedException, ExecutionException, TimeoutException {
//        LOGGER.atInfo().log("run of : {}", commands);
//        ProcessBuilder builder = new ProcessBuilder();
//        builder.command(commands);
//        builder.directory(new File(System.getProperty("user.home")));
//        TeeList liste = new TeeList(logInfo);
//        Process process = builder.start();
//        StreamGobbler streamGobbler =
//                new StreamGobbler(process.getInputStream(), liste::add);
//        StreamGobbler streamGobbler2 =
//                new StreamGobbler(process.getErrorStream(), LOGGER::error);
//        Future<?> future = Executors.newSingleThreadExecutor().submit(streamGobbler);
//        Future<?> future2 = Executors.newSingleThreadExecutor().submit(streamGobbler2);
//        int exitCode = process.waitFor();
//        if (exitCode == 0) {
//            LOGGER.debug("Execution reussi");
//        } else {
//            throw new JBackupException("Erreur pour l'execution (code retour=" + exitCode + ")");
//        }
//        future.get(10, TimeUnit.SECONDS);
//        future2.get(10, TimeUnit.SECONDS);
//        return liste.getList();
//    }
}
