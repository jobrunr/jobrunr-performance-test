package org.jobrunr.performance.scenario.monitor;

import org.jobrunr.storage.JobStats;
import org.jobrunr.storage.listeners.JobStatsChangeListener;
import org.performance.scenario.ScenarioMonitor;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Objects;

public class JobRunrScenarioMonitor extends ScenarioMonitor implements JobStatsChangeListener {

    private JobStats jobStats;
    private int duplicateJobStatsCounter;

    public JobRunrScenarioMonitor(long totalAmountOfJobs, Duration maxDuration) {
        super(totalAmountOfJobs, maxDuration);
    }

    @Override
    public void onChange(JobStats jobStats) {
        if ((jobStats.getSucceeded() + jobStats.getAllTimeSucceeded()) >= totalAmountOfJobs) {
            LoggerFactory.getLogger(JobRunrScenarioMonitor.class).warn("All jobs were processed ({} / {})", jobStats.getSucceeded(), totalAmountOfJobs);
            countDownLatch.countDown();
        } else if (this.jobStats != null
                && Objects.equals(this.jobStats.getAwaiting(), jobStats.getAwaiting())
                && Objects.equals(this.jobStats.getScheduled(), jobStats.getScheduled())
                && Objects.equals(this.jobStats.getSucceeded(), jobStats.getSucceeded())
                && Objects.equals(this.jobStats.getEnqueued(), jobStats.getEnqueued())) {
            // in case of failure
            if (duplicateJobStatsCounter++ > 20 && duplicateJobStatsCounter < 25) {
                LoggerFactory.getLogger(JobRunrScenarioMonitor.class).warn("Duplicate job stats received too many times - shutting down");
                countDownLatch.countDown();
            }
        } else {
            duplicateJobStatsCounter = 0;
        }
        this.jobStats = jobStats;
    }

    @Override
    public long getTotalAmountOfSucceededJobs() {
        return jobStats.getSucceeded() + jobStats.getAllTimeSucceeded();
    }
}
