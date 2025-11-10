package org.performance.utils;

import org.slf4j.Logger;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

public class ThroughputLogger {

    private final Logger logger;
    private final int totalAmountOfJobs;
    private final long startTime;
    private final AtomicLong lastLogTime = new AtomicLong(-1);

    public ThroughputLogger(Logger logger, int totalAmountOfJobs) {
        this.logger = logger;
        this.totalAmountOfJobs = totalAmountOfJobs;
        this.startTime = System.currentTimeMillis();
    }

    public void logThroughputAndEstimatedTimeLeft(long currentJob) {
        long currentTime = System.currentTimeMillis();
        if ((currentTime - lastLogTime.get()) >= 60_000) {
            long totalElapsedSeconds = (currentTime - startTime) / 1000;
            double jobsPerSecond = totalElapsedSeconds > 0 ? (double) currentJob / totalElapsedSeconds : 0;
            double estimatedTotalTime = jobsPerSecond > 0 ? totalAmountOfJobs / jobsPerSecond : 0;

            logger.info("Created {} jobs in {}. Estimated time to create {} jobs: {} seconds",
                    currentJob, Duration.ofSeconds(totalElapsedSeconds), totalAmountOfJobs, (int) estimatedTotalTime);
            lastLogTime.set(currentTime);
        }
    }
}
