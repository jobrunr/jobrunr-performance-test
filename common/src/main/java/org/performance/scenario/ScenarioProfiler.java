package org.performance.scenario;

import one.profiler.AsyncProfiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.performance.utils.ReportingUtils.findLogbooksFolder;

public class ScenarioProfiler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScenarioProfiler.class);

    private final AsyncProfiler profiler;
    private final boolean enabled;

    public ScenarioProfiler() {
        this.profiler = AsyncProfiler.getInstance();
        this.enabled = "true".equalsIgnoreCase(System.getProperty("enable.profiling"));
    }

    public void start() throws Exception {
        if (enabled) {
            Path logBookPath = findLogbooksFolder().resolve("async-profiler");
            Files.createDirectories(logBookPath);
            String filename = logBookPath+"/%t-%p.jfr";
            profiler.execute("start,event=cpu,lock=10ms,file="+filename);
            LOGGER.info("Started async-profiler, output saved at {}", filename);
        }
    }

    public void stop() throws Exception {
        if (enabled) {
            profiler.execute("stop");
            LOGGER.info("Stopped async-profiler");
        }
    }
}
