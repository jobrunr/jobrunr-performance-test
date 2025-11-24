package org.quartz.performance.scenario;

import org.performance.scenario.ScenarioMonitor;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

public class QuartzScenarioMonitor extends ScenarioMonitor implements JobListener {

    private final AtomicLong completedJobs = new AtomicLong(0);

    public QuartzScenarioMonitor(long totalAmountOfJobs, Duration maxDuration) {
        super(totalAmountOfJobs, maxDuration);
    }

    @Override
    public String getName() {
        return "QuartzScenarioMonitor";
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext jobExecutionContext) {
    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext jobExecutionContext) {
    }

    @Override
    public void jobWasExecuted(JobExecutionContext context, JobExecutionException jobException) {
        long count = completedJobs.incrementAndGet();
        if (count >= totalAmountOfJobs) {
            countDownLatch.countDown();
        }
    }

    @Override
    public long getTotalAmountOfSucceededJobs() {
        return completedJobs.get();
    }
}
