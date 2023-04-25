package org.jbackup.jbackup.runner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
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
            boolean isWindows = System.getProperty("os.name")
                    .toLowerCase().startsWith("windows");
            ProcessBuilder builder = new ProcessBuilder();
            if (isWindows) {
                //builder.command("cmd.exe", "/c", "dir");
                //builder.command("powershell.exe", "-Command", "(gwmi -list win32_shadowcopy).Create('E:\\','ClientAccessible')");
//                builder.command("cmd.exe", "/c","powershell.exe", "-Command (gwmi -list win32_shadowcopy).Create('E:\\','ClientAccessible')");
                builder.command("powershell.exe", "-Command", "(gwmi -list win32_shadowcopy).Create('E:\\','ClientAccessible')");
            } else {
                builder.command("sh", "-c", "ls");
            }
            builder.directory(new File(System.getProperty("user.home")));
            Process process = builder.start();
            StreamGobbler streamGobbler =
                    new StreamGobbler(process.getInputStream(), System.out::println);
            StreamGobbler streamGobbler2 =
                    new StreamGobbler(process.getErrorStream(), System.err::println);
            Future<?> future = Executors.newSingleThreadExecutor().submit(streamGobbler);
            Future<?> future2 = Executors.newSingleThreadExecutor().submit(streamGobbler2);
            int exitCode = process.waitFor();
            Assert.isTrue(exitCode == 0,"code retour="+exitCode);
            future.get(10, TimeUnit.SECONDS);
            future2.get(10, TimeUnit.SECONDS);
        }catch (Exception e){
            LOGGER.error("Erreur",e);
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

}
