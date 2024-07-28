package org.jbackup.jbackup.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.jbackup.jbackup.exception.JBackupException;
import org.jbackup.jbackup.shadowcopy.StreamGobbler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class RunService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RunService.class);


    private ExecutorService executorService;

    @PostConstruct
    public void init() {
        LOGGER.info("init executorService");
        BasicThreadFactory factory = new BasicThreadFactory.Builder()
                .namingPattern("substr-%d")
                .uncaughtExceptionHandler((thread, exception) -> {
                    LOGGER.error("Erreur (thread: {})", thread, exception);
                })
                .build();
        executorService = Executors.newCachedThreadPool(factory);
    }

    @PreDestroy
    public void terminate() {
        LOGGER.info("fin du executorService");
        executorService.shutdown();
        LOGGER.info("fin du executorService OK");
    }


    public int runCommand(Consumer<String> consumer, List<String> commandes, Duration duree) throws InterruptedException, IOException {
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
        int res;
        if(duree!=null){
            var ok = process.waitFor(duree.toMillis(), TimeUnit.MILLISECONDS);
            if(ok) {
                res = process.exitValue();
            } else {
                throw new JBackupException("Erreur pour la commande");
            }
        } else {
            res = process.waitFor();
        }
        LOGGER.info("run end");
        return res;
    }


}


