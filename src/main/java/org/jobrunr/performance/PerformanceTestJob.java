package org.jobrunr.performance;

import org.jobrunr.jobs.annotations.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PerformanceTestJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(PerformanceTestJob.class);

    private static final int MAX_SECONDS = 10;
    private static final ConcurrentHashMap<Long, AtomicInteger> jobsCountMap = new ConcurrentHashMap<>(10, 0.9F, 100);


    public static final AtomicLong counter = new AtomicLong();

    public static final AtomicLong startTime = new AtomicLong(0L);

    @Job(name = "Job %0")
    public void testJob(int index, long startTime) throws InterruptedException {
        PerformanceTestJob.startTime.compareAndExchange(0L, System.currentTimeMillis());

        long executedJobsCounter = counter.incrementAndGet();
        long ms = System.currentTimeMillis() - PerformanceTestJob.startTime.get();
        long currentTimeSeconds = System.currentTimeMillis() / 1000;

        // Add a new job count for the current second if needed
        AtomicInteger newSecond = jobsCountMap.putIfAbsent(currentTimeSeconds, new AtomicInteger(0));
        jobsCountMap.get(currentTimeSeconds).incrementAndGet();

        if(newSecond == null || counter.get() == 1_000_000) {
            // Remove job counts that are more than MAX_SECONDS old
            long itemsToDeleteBefore = currentTimeSeconds - MAX_SECONDS;
            jobsCountMap.entrySet().removeIf(e -> e.getKey() < itemsToDeleteBefore);

            int totalJobs = jobsCountMap.values().stream().mapToInt(AtomicInteger::get).sum();
            double jobsPerSecond = totalJobs / (double) MAX_SECONDS;

            LOGGER.info(ms + "ms / " + Duration.ofMillis(ms) + " - processed " + executedJobsCounter + " jobs / " + index + " index | " + executedJobsCounter * 1000 / (ms) + " jobs/sec (overall) | " + jobsPerSecond + " jobs/sec (last " + MAX_SECONDS + " sec)");
        }
    }
}
