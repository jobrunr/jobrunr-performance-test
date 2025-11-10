package org.jobrunr.performance.scenario.monitor;

import org.jobrunr.storage.JobStats;
import org.jobrunr.storage.listeners.JobStatsChangeListener;
import org.performance.scenario.ScenarioMonitor;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;

public class JobRunrScenarioMonitor extends ScenarioMonitor implements JobStatsChangeListener {

    private JobStats jobsStats;
    private int duplicateJobStatsCounter;

    public JobRunrScenarioMonitor(long totalAmountOfJobs, Duration maxDuration) {
        super(totalAmountOfJobs, maxDuration);
    }

    @Override
    public void onChange(JobStats jobStats) {
        if (jobStats.getSucceeded() >= totalAmountOfJobs) {
            LoggerFactory.getLogger(JobRunrScenarioMonitor.class).warn("All jobs were processed ({} / {})", jobStats.getSucceeded(), totalAmountOfJobs);
            countDownLatch.countDown();
        } else if (this.jobsStats != null
                && Objects.equals(this.jobsStats.getAwaiting(), jobStats.getAwaiting())
                && Objects.equals(this.jobsStats.getScheduled(), jobStats.getScheduled())
                && Objects.equals(this.jobsStats.getSucceeded(), jobStats.getSucceeded())
                && Objects.equals(this.jobsStats.getEnqueued(), jobStats.getEnqueued())) {
            // in case of failure
            if (duplicateJobStatsCounter++ > 20 && duplicateJobStatsCounter < 25) {
                LoggerFactory.getLogger(JobRunrScenarioMonitor.class).warn("Duplicate job stats received too many times - shutting down");
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

    @Override
    public long getTotalAmountOfSucceededJobs() {
        return jobsStats.getSucceeded() + jobsStats.getAllTimeSucceeded();
    }
}
