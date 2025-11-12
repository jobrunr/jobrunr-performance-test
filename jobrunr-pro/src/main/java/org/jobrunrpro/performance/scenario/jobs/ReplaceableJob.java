package org.jobrunrpro.performance.scenario.jobs;

import org.performance.jobs.PerformanceTestJob;

public class ReplaceableJob extends PerformanceTestJob {

    public void slowJob(int totalAmountOfJobs, int index, long sleepDuration) throws InterruptedException {
        Thread.sleep(sleepDuration);
        this.testJob(totalAmountOfJobs, index);
    }
}
