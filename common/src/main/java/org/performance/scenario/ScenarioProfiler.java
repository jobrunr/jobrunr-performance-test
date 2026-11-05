package org.performance.scenario;

import one.profiler.AsyncProfiler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.performance.utils.ReportingUtils.findLogbooksFolder;

public class ScenarioProfiler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ScenarioProfiler.class);

    private final AsyncProfiler profiler;
    private final boolean enabled;
    private Path file;

    public ScenarioProfiler(boolean enabled) {
        this.profiler = AsyncProfiler.getInstance();
        this.enabled = enabled;
    }

    public void start() throws Exception {
        if (!enabled) return;
        Path folder = findLogbooksFolder().resolve("async-profiler");
        Files.createDirectories(folder);
        file = folder.resolve(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now()) + "-" + ProcessHandle.current().pid() + ".jfr");
        // macOS can't sample cpu together with wall
        String cpuEvent = System.getProperty("os.name").toLowerCase().contains("mac") ? "itimer" : "cpu";
        profiler.execute("start,event=" + cpuEvent + ",wall=20ms,lock=1ms,jfrsync=profile,file=" + file);
        LOGGER.info("Started async-profiler");
    }

    public void stop() throws Exception {
        if (!enabled) return;
        profiler.execute("stop");
        LOGGER.info("Stopped async-profiler, profile written to {}", file);
    }
}
