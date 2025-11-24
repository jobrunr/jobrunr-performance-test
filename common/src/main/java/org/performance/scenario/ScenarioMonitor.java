package org.performance.scenario;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public abstract class ScenarioMonitor {

    protected final long totalAmountOfJobs;
    private final Duration maxDuration;
    protected final CountDownLatch countDownLatch;

    public ScenarioMonitor(long totalAmountOfJobs) {
        this(totalAmountOfJobs, Duration.ofDays(10));
    }

    public ScenarioMonitor(long totalAmountOfJobs, Duration maxDuration) {
        this.totalAmountOfJobs = totalAmountOfJobs;
        this.maxDuration = maxDuration;
        this.countDownLatch = new CountDownLatch(1);
    }

    /**
     * @return true if the scenario finished successfully, false otherwise
     */
    public boolean awaitForScenario() {
        try {
            return countDownLatch.await(maxDuration.getSeconds(), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Exception waiting for " + totalAmountOfJobs + " jobs to succeed", e);
        }
    }

    public abstract long getTotalAmountOfSucceededJobs();
}
