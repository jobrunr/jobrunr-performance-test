package org.jobrunr.performance.scenario.monitor;

import org.jobrunr.storage.JobStats;
import org.jobrunr.storage.listeners.JobStatsChangeListener;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

public class ScenarioMonitor implements JobStatsChangeListener {

    private final long totalAmountOfJobs;
    private final Instant startedAt;
    private final Duration maxDuration;
    private final CountDownLatch countDownLatch;
    private JobStats jobsStats;
    private int duplicateJobStatsCounter;

    public ScenarioMonitor(long totalAmountOfJobs, Instant startedAt) {
        this(totalAmountOfJobs, startedAt, Duration.ofDays(10));
    }

    public ScenarioMonitor(long totalAmountOfJobs, Instant startedAt, Duration maxDuration) {
        this.totalAmountOfJobs = totalAmountOfJobs;
        this.startedAt = startedAt;
        this.maxDuration = maxDuration;
        this.countDownLatch = new CountDownLatch(1);
    }

    @Override
    public void onChange(JobStats jobStats) {
        if (jobStats.getSucceeded() >= totalAmountOfJobs) {
            LoggerFactory.getLogger(ScenarioMonitor.class).warn("All jobs were processed ({} / {})", jobStats.getSucceeded(), totalAmountOfJobs);
            countDownLatch.countDown();
        } else if (Duration.between(startedAt, Instant.now()).compareTo(maxDuration) > 0) {
            LoggerFactory.getLogger(ScenarioMonitor.class).warn("Test duration exceeded: {}", maxDuration);
            countDownLatch.countDown();
        } else if (this.jobsStats != null
                && Objects.equals(this.jobsStats.getAwaiting(), jobStats.getAwaiting())
                && Objects.equals(this.jobsStats.getScheduled(), jobStats.getScheduled())
                && Objects.equals(this.jobsStats.getSucceeded(), jobStats.getSucceeded())
                && Objects.equals(this.jobsStats.getEnqueued(), jobStats.getEnqueued())
                && Duration.between(startedAt, Instant.now()).compareTo(Duration.ofMinutes(2)) > 0) {
            // in case of failure
            if (duplicateJobStatsCounter++ > 20 && duplicateJobStatsCounter < 25) {
                LoggerFactory.getLogger(ScenarioMonitor.class).warn("Duplicate job stats received too many times - shutting down");
                countDownLatch.countDown();
            }
        } else {
            duplicateJobStatsCounter = 0;
        }
        this.jobsStats = jobStats;
    }

    public Long awaitAndGetSucceededJobs() {
        try {
            countDownLatch.await();
            return jobsStats.getSucceeded() + jobsStats.getAllTimeSucceeded();
        } catch (InterruptedException e) {
            throw new RuntimeException("Exception waiting for " + totalAmountOfJobs + " jobs to succeed", e);
        }
    }
}
