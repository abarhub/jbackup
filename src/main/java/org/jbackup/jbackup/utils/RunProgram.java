package org.jbackup.jbackup.utils;

import com.google.common.base.Joiner;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
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

    private final ObservationRegistry observationRegistry;

    public RunProgram(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
        init();
    }

    public RunProgram(ExecutorService executorService, ObservationRegistry observationRegistry) {
        this.executorService = executorService;
        this.observationRegistry = observationRegistry;
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

    public int runCommand(Consumer<String> consumer, boolean stderrVersStdout, List<String> commandes, List<String> commandesShow) throws InterruptedException, IOException {
        ProcessBuilder builder = new ProcessBuilder();
        List<String> liste = new ArrayList<>();
        List<String> listeShow = new ArrayList<>();
        int pos = 0;
        for (String s : commandes) {
            var s2 = s;
            if (s.contains(" ")) {
                s2 = "\"" + s + "\"";
            }
            liste.add(s2);
            listeShow.add(commandesShow.get(pos));
            pos++;
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
                    if (stderrVersStdout) {
                        LOGGER.info("stderr: {}", x);
                    } else {
                        LOGGER.error("stderr: {}", x);
                    }
                });
        executorService.submit(streamGobblerErrur);
        LOGGER.info("run ...");
        var res = process.waitFor();
        LOGGER.info("run end");
        return res;
    }

    public int runCommand(boolean stderrVersStdout, List<String> commandes, List<String> commandesShow) throws InterruptedException, IOException {
        Consumer<String> consumer = (x) -> {
            LOGGER.info("stdout: {}", x);
        };
        return runCommand(consumer, stderrVersStdout, commandes, commandesShow);
    }

    public int runCommandObs(boolean stderrVersStdout, List<String> commandes, List<String> commandesShow) throws Exception {
        Integer res;
        res = Observation.createNotStarted("run", this.observationRegistry)
                .lowCardinalityKeyValue("action", "run")
                .highCardinalityKeyValue("command", Joiner.on(",").join(commandesShow))
                .observeChecked(() -> {
                    Consumer<String> consumer = (x) -> {
                        LOGGER.info("stdout: {}", x);
                    };
                    return runCommand(consumer, stderrVersStdout, commandes, commandesShow);
                });
        return res != null ? res : 0;
    }

    public int runCommand(List<String> commandes) throws InterruptedException, IOException {
        return runCommand(false, commandes, commandes);
    }
}
