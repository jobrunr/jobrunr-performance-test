package org.jobrunr.performance;

import com.codahale.metrics.Meter;
import org.jobrunr.jobs.annotations.Job;

public class PerformanceTestJob {

    private final Meter jobs = Main.metrics.meter("jobs");

    @Job(name = "Job %0")
    public void testJob(int index) throws InterruptedException {
        jobs.mark();
        Main.countDownLatch.countDown();
    }
}
