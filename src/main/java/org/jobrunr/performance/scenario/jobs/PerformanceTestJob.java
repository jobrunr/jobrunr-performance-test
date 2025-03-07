package org.jobrunr.performance.scenario.jobs;

import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.scheduling.JobBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.jobrunr.scheduling.JobBuilder.aJob;

public class PerformanceTestJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(PerformanceTestJob.class);

    private static final int MAX_SECONDS = 10;
    private static final ConcurrentHashMap<Long, AtomicInteger> jobsCountMap = new ConcurrentHashMap<>();

    public static final AtomicLong counter = new AtomicLong();
    public static final AtomicLong startTime = new AtomicLong(-1L);

    public void batchJob(int totalAmountOfChildJobs, int batchJob) {
        Stream<JobBuilder> jobBuilderStream = IntStream.range(0, totalAmountOfChildJobs)
                .boxed().map(i -> aJob()
                        .withName("child job " + i + " for batch job " + batchJob)
                        .<PerformanceTestJob>withDetails(x -> x.testJob(totalAmountOfChildJobs, i)));
        BackgroundJob.create(jobBuilderStream);
        LOGGER.info("Batch job {} finished", batchJob);
    }

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

            LOGGER.info(elapsedTime + "ms / " + Duration.ofMillis(elapsedTime) +
                    " - processed " + executedJobsCounter + " jobs / " + index +
                    " index | " + String.format("%.2f", executedJobsCounter * 1000.0 / elapsedTime) +
                    " jobs/sec (overall) | " + jobsPerSecond + " jobs/sec (last " +
                    MAX_SECONDS + " sec)");
        }
    }

    private void cleanUpOldEntries(long currentTimeSeconds) {
        long threshold = currentTimeSeconds - MAX_SECONDS;
        jobsCountMap.keySet().removeIf(key -> key < threshold);
    }
}
