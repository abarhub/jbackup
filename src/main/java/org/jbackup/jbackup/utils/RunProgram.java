package org.jbackup.jbackup.utils;

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.jbackup.jbackup.shadowcopy.StreamGobbler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class RunProgram {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunProgram.class);

    private ExecutorService executorService;

    public RunProgram() {
        init();
    }

    public RunProgram(ExecutorService executorService) {
        this.executorService = executorService;
    }

    public void init() {
        BasicThreadFactory factory = new BasicThreadFactory.Builder()
                .namingPattern("exec-%d")
                .uncaughtExceptionHandler((thread, exception) -> {
                    LOGGER.error("Erreur (thread: {})", thread, exception);
                })
                .build();
        executorService = Executors.newCachedThreadPool(factory);
    }

    public int runCommand(Consumer<String> consumer, String... commandes) throws InterruptedException, IOException {
        ProcessBuilder builder = new ProcessBuilder();
        List<String> liste = new ArrayList<>();
        List<String> listeShow = new ArrayList<>();
        for (String s : commandes) {
            var s2 = s;
            if (s.contains(" ")) {
                s2 = "\"" + s + "\"";
            }
            liste.add(s2);
            listeShow.add(s2);
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
                    LOGGER.error("stderr: {}", x);
                });
        executorService.submit(streamGobblerErrur);
        LOGGER.info("run ...");
        var res = process.waitFor();
        LOGGER.info("run end");
        return res;
    }

    public int runCommand(String... commandes) throws InterruptedException, IOException {
        Consumer<String> consumer = (x) -> {
            LOGGER.info("stdout: {}", x);
        };
        return runCommand(consumer,commandes);
    }
}
