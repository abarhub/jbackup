package org.jbackup.jbackup.shadowcopy;

import org.jbackup.jbackup.exception.JBackupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.*;

public class ShadowCopy implements AutoCloseable {

    private static final Logger LOGGER = LoggerFactory.getLogger(ShadowCopy.class);

    private final Map<Character, ShadowPath> map = new ConcurrentHashMap<>();

    public Path getPath(Path path) {
        Assert.isTrue(path.isAbsolute(),"Path '"+path+"' must be absolute");
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
                    LOGGER.info("{} -> {}",path,p2);
                    path = p2;
                }
            }
        }
        return path;
    }

    private Path convertPath(ShadowPath p, Path path) {
        var s=path.toString();
        var s2=p.path().toString();
        s=s.substring(2);
        var s3=s2+s;
        if(s3.startsWith("\\\\?\\")){
            s3="\\\\.\\"+s3.substring(4);
        }
        return Path.of(s3);
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
        for (var entry : map.entrySet()) {
            deleteShadowCopy(entry.getKey(), entry.getValue().shadowId());
        }
    }

    private Optional<String> createShadowCopy(char volume) {
        try {
            LOGGER.info("create shadow copy for {} ...", volume);
            List<String> liste;
            liste=run(List.of("powershell.exe", "-Command",
                    "(gwmi -list win32_shadowcopy).Create('" + volume + ":\\','ClientAccessible')"),
                    true);
//            ProcessBuilder builder = new ProcessBuilder();
//            builder.command("powershell.exe", "-Command", "(gwmi -list win32_shadowcopy).Create('" + volume + ":\\','ClientAccessible')");
//            builder.directory(new File(System.getProperty("user.home")));
//            TeeList liste = new TeeList();
//            Process process = builder.start();
//            StreamGobbler streamGobbler =
//                    new StreamGobbler(process.getInputStream(), liste::add);
//            StreamGobbler streamGobbler2 =
//                    new StreamGobbler(process.getErrorStream(), LOGGER::error);
//            Future<?> future = Executors.newSingleThreadExecutor().submit(streamGobbler);
//            Future<?> future2 = Executors.newSingleThreadExecutor().submit(streamGobbler2);
//            int exitCode = process.waitFor();
//            Assert.isTrue(exitCode == 0, "code retour=" + exitCode);
//            future.get(10, TimeUnit.SECONDS);
//            future2.get(10, TimeUnit.SECONDS);
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
//            List<String> liste;
//            ProcessBuilder builder = new ProcessBuilder();
            if (StringUtils.hasText(shadowId)) {
//                builder.command("cmd.exe", "/c", "vssadmin", "Delete", "Shadows", "/Shadow=" + shadowId, "/Quiet");
                run(List.of("cmd.exe", "/c", "vssadmin", "Delete", "Shadows", "/Shadow=" + shadowId, "/Quiet"),
                        true);
            } else {
//                builder.command("cmd.exe", "/c", "vssadmin", "Delete", "Shadows", "/For=" + volume + ":", "/Quiet");
                run(List.of("cmd.exe", "/c", "vssadmin", "Delete", "Shadows", "/For=" + volume + ":", "/Quiet"),
                        true);
            }
//            builder.directory(new File(System.getProperty("user.home")));
//            Process process = builder.start();
//            StreamGobbler streamGobbler =
//                    new StreamGobbler(process.getInputStream(), LOGGER::info);
//            StreamGobbler streamGobbler2 =
//                    new StreamGobbler(process.getErrorStream(), LOGGER::error);
//            Future<?> future = Executors.newSingleThreadExecutor().submit(streamGobbler);
//            Future<?> future2 = Executors.newSingleThreadExecutor().submit(streamGobbler2);
//            int exitCode = process.waitFor();
//            Assert.isTrue(exitCode == 0, "code retour=" + exitCode);
//            future.get(10, TimeUnit.SECONDS);
//            future2.get(10, TimeUnit.SECONDS);
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
//            ProcessBuilder builder = new ProcessBuilder();
            if (StringUtils.hasText(shadowId)) {
//                builder.command("cmd.exe", "/c", "vssadmin", "List", "Shadows", "/For=" + volume + ":", "/Shadow=" + shadowId);
//                builder.command("powershell.exe", "Get-WmiObject Win32_ShadowCopy | Where-Object { $_.ID -eq '" + shadowId + "' } | Select DeviceObject");
                liste=run(List.of("powershell.exe",
                        "Get-WmiObject Win32_ShadowCopy | Where-Object { $_.ID -eq '" + shadowId + "' } | Select DeviceObject"),
                        true);
            } else {
//                builder.command("cmd.exe", "/c", "vssadmin", "List", "Shadows", "/For=" + volume + ":");
                liste=run(List.of("cmd.exe", "/c", "vssadmin", "List", "Shadows", "/For=" + volume + ":"),
                        true);
            }
//            builder.directory(new File(System.getProperty("user.home")));
//            TeeList liste = new TeeList();
//            Process process = builder.start();
//            StreamGobbler streamGobbler =
//                    new StreamGobbler(process.getInputStream(), liste::add);
//            StreamGobbler streamGobbler2 =
//                    new StreamGobbler(process.getErrorStream(), LOGGER::error);
//            Future<?> future = Executors.newSingleThreadExecutor().submit(streamGobbler);
//            Future<?> future2 = Executors.newSingleThreadExecutor().submit(streamGobbler2);
//            int exitCode = process.waitFor();
//            Assert.isTrue(exitCode == 0, "code retour=" + exitCode);
//            future.get(10, TimeUnit.SECONDS);
//            future2.get(10, TimeUnit.SECONDS);
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
        ProcessBuilder builder = new ProcessBuilder();
        builder.command(commands);
        builder.directory(new File(System.getProperty("user.home")));
        TeeList liste = new TeeList(logInfo);
        Process process = builder.start();
        StreamGobbler streamGobbler =
                new StreamGobbler(process.getInputStream(), liste::add);
        StreamGobbler streamGobbler2 =
                new StreamGobbler(process.getErrorStream(), LOGGER::error);
        Future<?> future = Executors.newSingleThreadExecutor().submit(streamGobbler);
        Future<?> future2 = Executors.newSingleThreadExecutor().submit(streamGobbler2);
        int exitCode = process.waitFor();
        Assert.isTrue(exitCode == 0, "code retour=" + exitCode);
        future.get(10, TimeUnit.SECONDS);
        future2.get(10, TimeUnit.SECONDS);
        return liste.getList();
    }
}
