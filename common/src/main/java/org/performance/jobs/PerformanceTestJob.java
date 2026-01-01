package org.performance.jobs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class PerformanceTestJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(PerformanceTestJob.class);

    private static final int MAX_SECONDS = 10;
    private static final ConcurrentHashMap<Long, AtomicInteger> jobsCountMap = new ConcurrentHashMap<>();

    public static final AtomicLong counter = new AtomicLong();
    public static final AtomicLong startTime = new AtomicLong(-1L);

    public void testJob(int totalAmountOfJobs, int index) {
        long currentTime = System.currentTimeMillis();
        startTime.compareAndSet(-1L, currentTime);

        long executedJobsCounter = counter.incrementAndGet();

        long currentTimeSeconds = currentTime / 1000;
        AtomicInteger newSecond = jobsCountMap.putIfAbsent(currentTimeSeconds, new AtomicInteger(0));
        jobsCountMap.get(currentTimeSeconds).incrementAndGet();

        if (newSecond == null || totalAmountOfJobs == index) {
            cleanUpOldEntries(currentTimeSeconds);

            long elapsedTime = currentTime - startTime.get();
            int totalJobs = jobsCountMap.values().stream().mapToInt(AtomicInteger::get).sum();
            double jobsPerSecond = (double) totalJobs / MAX_SECONDS;

            LOGGER.info("{}ms / {} - processed {} jobs / {} index | {} jobs/sec (overall) | {} jobs/sec (last " + MAX_SECONDS + " sec)",
                    elapsedTime,
                    Duration.ofMillis(elapsedTime),
                    executedJobsCounter,
                    index,
                    String.format("%.2f", executedJobsCounter * 1000.0 / elapsedTime),
                    jobsPerSecond
            );
        }
    }

    private void cleanUpOldEntries(long currentTimeSeconds) {
        long threshold = currentTimeSeconds - MAX_SECONDS;
        jobsCountMap.keySet().removeIf(key -> key < threshold);
    }
}
