package org.quartz.performance.scenario;

import org.performance.datastore.DataStore;
import org.performance.scenario.AbstractScenario;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.performance.QuartzTool;
import org.quartz.performance.scenario.jobs.QuartzPerformanceTestJob;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static java.lang.Integer.parseInt;

public class Scenario01ProcessJobs extends AbstractScenario<QuartzTool> {

    public Scenario01ProcessJobs(DataStore dataStore, String[] args) {
        super(new QuartzTool(), dataStore, args);
    }

    @Override
    protected long loadJobs() throws Exception {
        int totalAmountOfJobs = parseInt(getArg("amount", "0").replace("_", ""));
        if (totalAmountOfJobs < 1) return 0;

        Instant startTime = Instant.now();
        AtomicLong lastLogTime = new AtomicLong(System.currentTimeMillis());

        Scheduler scheduler = tool.getScheduler();
        IntStream.range(0, totalAmountOfJobs)
                .boxed()
                .peek(i -> {
                    long currentTime = System.currentTimeMillis();
                    long elapsedSinceLastLog = (currentTime - lastLogTime.get()) / 1000;

                    if (elapsedSinceLastLog >= 60) {
                        long totalElapsedSeconds = (currentTime - startTime.toEpochMilli()) / 1000;
                        double jobsPerSecond = totalElapsedSeconds > 0 ? (double) i / totalElapsedSeconds : 0;
                        double estimatedTotalTime = jobsPerSecond > 0 ? totalAmountOfJobs / jobsPerSecond : 0;

                        LOGGER.info("Created {} jobs in {}. Estimated time to create {} jobs: {} seconds",
                                i, Duration.ofSeconds(totalElapsedSeconds), totalAmountOfJobs, (int) estimatedTotalTime);
                        lastLogTime.set(currentTime);
                    }
                }).forEach(i -> {
                    try {
                        JobKey jobKey = new JobKey("job_" + i, "group");
                        JobDetail job = JobBuilder.newJob(QuartzPerformanceTestJob.class)
                                .withIdentity(jobKey)
                                .storeDurably()
                                .build();
                        scheduler.addJob(job, false);

                        // create the JobDataMap
                        JobDataMap jobDataMap = new JobDataMap();
                        jobDataMap.put("jobNumber", i);
                        jobDataMap.put("totalAmountOfJobs", totalAmountOfJobs);

                        // trigger the job using the JobKey and the JobDataMap
                        scheduler.triggerJob(jobKey, jobDataMap);
                    } catch (SchedulerException e) {
                        throw new RuntimeException("Failed to schedule job " + i, e);
                    }
                });
        // IMPORTANT: Give time for transactions to commit
        Thread.sleep(2000);
        return totalAmountOfJobs;
    }
}
