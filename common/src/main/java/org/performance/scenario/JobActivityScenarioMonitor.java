package org.performance.scenario;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Stops when all jobs have succeeded or the workers of this JVM did nothing for a number of consecutive polls.
 */
public class JobActivityScenarioMonitor extends ScenarioMonitor {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobActivityScenarioMonitor.class);

    public interface JobActivity {

        int occupiedWorkers();

        long amountOfJobStateChanges();

        long amountOfSucceededJobs();

        long publishSucceededJobsAndSumCluster();

        Optional<Instant> lastNonRecurringJobSucceededAt();
    }

    private final JobActivity jobActivity;
    private final Duration pollInterval;
    private final int idlePollsToStop;

    public JobActivityScenarioMonitor(long totalAmountOfJobs, Duration maxDuration, JobActivity jobActivity, Duration pollInterval, int idlePollsToStop) {
        super(totalAmountOfJobs, maxDuration);
        this.jobActivity = jobActivity;
        this.pollInterval = pollInterval;
        this.idlePollsToStop = idlePollsToStop;
    }

    @Override
    public boolean awaitForScenario() {
        long deadline = System.nanoTime() + maxDuration.toNanos();
        long previousStateChanges = jobActivity.amountOfJobStateChanges();
        int idlePolls = 0;
        try {
            // stopping during the server's heartbeat fails it with "No servers available"
            Thread.sleep(pollInterval.toMillis() / 2);
            while (System.nanoTime() < deadline) {
                Thread.sleep(pollInterval.toMillis());
                long succeededJobs = jobActivity.publishSucceededJobsAndSumCluster();
                if (succeededJobs >= totalAmountOfJobs) {
                    LOGGER.warn("All jobs were processed ({} / {})", succeededJobs, totalAmountOfJobs);
                    return true;
                }
                long stateChanges = jobActivity.amountOfJobStateChanges();
                idlePolls = stateChanges == previousStateChanges && jobActivity.occupiedWorkers() == 0 ? idlePolls + 1 : 0;
                previousStateChanges = stateChanges;
                if (idlePolls >= idlePollsToStop) {
                    LOGGER.warn("Workers idle for {} polls of {} - stopping ({} / {} jobs succeeded)", idlePolls, pollInterval, succeededJobs, totalAmountOfJobs);
                    return true;
                }
            }
            return false;
        } catch (InterruptedException e) {
            throw new RuntimeException("Exception waiting for " + totalAmountOfJobs + " jobs to succeed", e);
        }
    }

    @Override
    public long getTotalAmountOfSucceededJobs() {
        return jobActivity.amountOfSucceededJobs();
    }

    @Override
    public Optional<Instant> getLastJobSucceededAt() {
        return jobActivity.lastNonRecurringJobSucceededAt();
    }
}
