package org.jbackup.jbackup.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import java.io.*;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Service
public class RunnerApp implements ApplicationRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunnerApp.class);

    @Override
    public void run(ApplicationArguments args) throws Exception {
        LOGGER.info("run");
        run2();
        System.exit(0);
    }

    private void run2() {
        try {
//            boolean isWindows = System.getProperty("os.name")
//                    .toLowerCase().startsWith("windows");
//            ProcessBuilder builder = new ProcessBuilder();
//            if (isWindows) {
//                //builder.command("cmd.exe", "/c", "dir");
//                //builder.command("powershell.exe", "-Command", "(gwmi -list win32_shadowcopy).Create('E:\\','ClientAccessible')");
////                builder.command("cmd.exe", "/c","powershell.exe", "-Command (gwmi -list win32_shadowcopy).Create('E:\\','ClientAccessible')");
//                builder.command("powershell.exe", "-Command", "(gwmi -list win32_shadowcopy).Create('E:\\','ClientAccessible')");
//            } else {
//                builder.command("sh", "-c", "ls");
//            }
//            builder.directory(new File(System.getProperty("user.home")));
//            Process process = builder.start();
//            StreamGobbler streamGobbler =
//                    new StreamGobbler(process.getInputStream(), System.out::println);
//            StreamGobbler streamGobbler2 =
//                    new StreamGobbler(process.getErrorStream(), System.err::println);
//            Future<?> future = Executors.newSingleThreadExecutor().submit(streamGobbler);
//            Future<?> future2 = Executors.newSingleThreadExecutor().submit(streamGobbler2);
//            int exitCode = process.waitFor();
//            Assert.isTrue(exitCode == 0,"code retour="+exitCode);
//            future.get(10, TimeUnit.SECONDS);
//            future2.get(10, TimeUnit.SECONDS);
            var shadowVolume = createShadowCopy('E');
            if (shadowVolume.isPresent()) {
                var shadowVolume2=getShadowCopy('E', shadowVolume.get());
                LOGGER.info("shadowVolume={}",shadowVolume2);
                if(shadowVolume2.isPresent()) {
                    copy0(shadowVolume2.get());
                    copy2(shadowVolume2.get());
                }
                deleteShadowCopy('E', shadowVolume.get());
            }
        } catch (Exception e) {
            LOGGER.error("Erreur", e);
        }
    }

    private void copy2(String s) {
        try {
            LOGGER.info("copy2 from {}", s);
//            var p = Path.of(s, "res02_11_2021.txt");
//            var p2 = Path.of("d:/temp/mon_fichier_" + Instant.now().getEpochSecond() + ".txt");
//            Files.copy(p, p2);
            File f1=new File(s, "res02_11_2021.txt");
            File f2=new File("d:/temp/mon_fichier_" + Instant.now().getEpochSecond() + ".7z");

    var src=f1.toString();
    var zip="C:\\Program Files\\7-Zip\\7z";

            ProcessBuilder builder = new ProcessBuilder();

                //builder.command("cmd.exe", "/c", "dir");
                //builder.command("powershell.exe", "-Command", "(gwmi -list win32_shadowcopy).Create('E:\\','ClientAccessible')");
//                builder.command("cmd.exe", "/c","powershell.exe", "-Command (gwmi -list win32_shadowcopy).Create('E:\\','ClientAccessible')");
//                builder.command("powershell.exe", "-Command", "(gwmi -list win32_shadowcopy).Create('" + volume + ":\\','ClientAccessible')");
            builder.command("cmd.exe", "/c",zip,"a",f2.toString(),src);

            builder.directory(new File(System.getProperty("user.home")));
            TeeList liste = new TeeList();
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

//            try{
////                var p=f1.toPath();
//                var p=f1.toURI();
////                var p2=Path.of(f1.toURI());
//                LOGGER.info("p={}",p);
////                LOGGER.info("p2={}",p2);
//            }catch(Exception e){
//                LOGGER.error("Erreur",e);
//            }
////            try{
////               var input=new FileInputStream(f1);
//            try (
//                    InputStream in = new BufferedInputStream(
//                            new FileInputStream(f1));
//                    OutputStream out = new BufferedOutputStream(
//                            new FileOutputStream(f2))) {
//
//                byte[] buffer = new byte[1024];
//                int lengthRead;
//                while ((lengthRead = in.read(buffer)) > 0) {
//                    out.write(buffer, 0, lengthRead);
//                    out.flush();
//                }
//            }
//            }
            LOGGER.info("copy2 from {} to {} OK", s,f2);
        }catch(Exception e){
            LOGGER.error("Erreur",e);
        }
    }

    private void copy0(String s) {
        try {
            LOGGER.info("copy from {}", s);
//            var p = Path.of(s, "res02_11_2021.txt");
//            var p2 = Path.of("d:/temp/mon_fichier_" + Instant.now().getEpochSecond() + ".txt");
//            Files.copy(p, p2);
            File f1=new File(s, "res02_11_2021.txt");
            File f2=new File("d:/temp/mon_fichier_" + Instant.now().getEpochSecond() + ".txt");
            try{
//                var p=f1.toPath();
                var p=f1.toURI();
//                var p2=Path.of(f1.toURI());
                LOGGER.info("p={}",p);
//                LOGGER.info("p2={}",p2);
                //LOGGER.info("p3={}",Path.of(URI.create(f1.toString())));
//                LOGGER.info("p3={}",Path.of(f1.toString()));
                var s2=f1.toString();
                if(s2.startsWith("\\\\?\\")){
                    s2="\\\\.\\"+s2.substring(4);
                }
//                s2=s2.replace('?','.');
                LOGGER.info("p3={}",Path.of(s2));
                var dest=Path.of("d:/temp/mon_fichier_" + Instant.now().getEpochSecond() + "_bis.txt");
                Files.copy(Path.of(s2),dest);
                Assert.isTrue(Files.exists(dest),"dest="+dest);
                LOGGER.info("copy path ok (dest={})",dest);
            }catch(Exception e){
                LOGGER.error("Erreur",e);
            }
//            try{
//               var input=new FileInputStream(f1);
                try (
                        InputStream in = new BufferedInputStream(
                                new FileInputStream(f1));
                        OutputStream out = new BufferedOutputStream(
                                new FileOutputStream(f2))) {

                    byte[] buffer = new byte[1024];
                    int lengthRead;
                    while ((lengthRead = in.read(buffer)) > 0) {
                        out.write(buffer, 0, lengthRead);
                        out.flush();
                    }
                }
//            }
            LOGGER.info("copy from {} to {} OK", s,f2);
        }catch(Exception e){
            LOGGER.error("Erreur",e);
        }
    }

    private Optional<String> createShadowCopy(char volume) throws IOException {
        try {
            LOGGER.info("create shadow copy for {} ...", volume);
            boolean isWindows = System.getProperty("os.name")
                    .toLowerCase().startsWith("windows");
            ProcessBuilder builder = new ProcessBuilder();
            if (isWindows) {
                //builder.command("cmd.exe", "/c", "dir");
                //builder.command("powershell.exe", "-Command", "(gwmi -list win32_shadowcopy).Create('E:\\','ClientAccessible')");
//                builder.command("cmd.exe", "/c","powershell.exe", "-Command (gwmi -list win32_shadowcopy).Create('E:\\','ClientAccessible')");
                builder.command("powershell.exe", "-Command", "(gwmi -list win32_shadowcopy).Create('" + volume + ":\\','ClientAccessible')");
            } else {
                builder.command("sh", "-c", "ls");
            }
            builder.directory(new File(System.getProperty("user.home")));
            TeeList liste = new TeeList();
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
            LOGGER.info("create shadow copy for {} OK", volume);
            return liste.getList().stream()
                    .filter(x -> x != null)
                    .filter(x -> x.contains("ShadowID"))
                    .map(x -> x.substring(x.indexOf(':') + 1).trim())
                    .findAny();
        } catch (Exception e) {
            LOGGER.error("Erreur", e);
            throw new IOException("Erreur pour creer le shadow copy", e);
        }
    }

    private void deleteShadowCopy(char volume, String shadowId) throws IOException {
        try {
            LOGGER.info("delete shadow copy for {} ...", volume);
            ProcessBuilder builder = new ProcessBuilder();
            //builder.command("cmd.exe", "/c", "dir");
            //builder.command("powershell.exe", "-Command", "(gwmi -list win32_shadowcopy).Create('E:\\','ClientAccessible')");
//                builder.command("cmd.exe", "/c","powershell.exe", "-Command (gwmi -list win32_shadowcopy).Create('E:\\','ClientAccessible')");
            if (StringUtils.hasText(shadowId)) {
                builder.command("cmd.exe", "/c", "vssadmin", "Delete", "Shadows", "/Shadow=" + shadowId, "/Quiet");
            } else {
                builder.command("cmd.exe", "/c", "vssadmin", "Delete", "Shadows", "/For=" + volume + ":", "/Quiet");
            }
            builder.directory(new File(System.getProperty("user.home")));
            Process process = builder.start();
            StreamGobbler streamGobbler =
                    new StreamGobbler(process.getInputStream(), LOGGER::info);
            StreamGobbler streamGobbler2 =
                    new StreamGobbler(process.getErrorStream(), LOGGER::error);
            Future<?> future = Executors.newSingleThreadExecutor().submit(streamGobbler);
            Future<?> future2 = Executors.newSingleThreadExecutor().submit(streamGobbler2);
            int exitCode = process.waitFor();
            Assert.isTrue(exitCode == 0, "code retour=" + exitCode);
            future.get(10, TimeUnit.SECONDS);
            future2.get(10, TimeUnit.SECONDS);
            LOGGER.info("delete shadow copy for {} OK", volume);
        } catch (Exception e) {
            LOGGER.error("Erreur", e);
            throw new IOException("Erreur pour creer le shadow copy", e);
        }
    }


    private Optional<String> getShadowCopy(char volume, String shadowId) throws IOException {
        try {
            LOGGER.info("list shadow copy for {}({}) ...", volume, shadowId);
            boolean isWindows = true;
            ProcessBuilder builder = new ProcessBuilder();
            //builder.command("cmd.exe", "/c", "dir");
            //builder.command("powershell.exe", "-Command", "(gwmi -list win32_shadowcopy).Create('E:\\','ClientAccessible')");
//                builder.command("cmd.exe", "/c","powershell.exe", "-Command (gwmi -list win32_shadowcopy).Create('E:\\','ClientAccessible')");
            if (StringUtils.hasText(shadowId)) {
//                builder.command("cmd.exe", "/c", "vssadmin", "List", "Shadows", "/For=" + volume + ":", "/Shadow=" + shadowId);
                builder.command("powershell.exe", "Get-WmiObject Win32_ShadowCopy | Where-Object { $_.ID -eq '"+shadowId+"' } | Select DeviceObject");
            } else {
                builder.command("cmd.exe", "/c", "vssadmin", "List", "Shadows", "/For=" + volume + ":");
            }
            builder.directory(new File(System.getProperty("user.home")));
            TeeList liste = new TeeList();
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
            LOGGER.info("list shadow copy for {} OK", volume);
            LOGGER.info("list shadow copy for {} :", liste.getList());
//            var s0="Volume de cliché instantané :";

            return liste.getList().stream()
//                    .filter(x->x.contains(s0))
//                    .map(x->x.substring(x.indexOf(':')+1).trim())
                    .filter(x->x.startsWith("\\\\"))
                    .map(x->x.trim())
                    .findAny();
        } catch (Exception e) {
            LOGGER.error("Erreur", e);
            throw new IOException("Erreur pour creer le shadow copy", e);
        }
    }


    private static class StreamGobbler implements Runnable {
        private InputStream inputStream;
        private Consumer<String> consumer;

        public StreamGobbler(InputStream inputStream, Consumer<String> consumer) {
            this.inputStream = inputStream;
            this.consumer = consumer;
        }

        @Override
        public void run() {
            new BufferedReader(new InputStreamReader(inputStream)).lines()
                    .forEach(consumer);
        }
    }

    private static class TeeList {
        private static final Logger LOGGER = LoggerFactory.getLogger(TeeList.class);

        private List<String> list = new CopyOnWriteArrayList<>();

        public void add(String s) {
            list.add(s);
            LOGGER.info(s);
        }

        public List<String> getList() {
            return list;
        }
    }

}
