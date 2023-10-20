package org.jobrunr.performance;

import com.codahale.metrics.Meter;
import org.jobrunr.jobs.annotations.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PerformanceTestJob {

    private final Meter jobs = Main.metrics.meter("jobs");

    @Job(name = "Job %0")
    public void testJob(int index) throws InterruptedException {
        jobs.mark();
    }
}
