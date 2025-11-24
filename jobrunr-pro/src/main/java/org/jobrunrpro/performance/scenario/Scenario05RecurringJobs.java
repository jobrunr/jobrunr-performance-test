package org.jobrunrpro.performance.scenario;

import org.jobrunr.jobs.RecurringJob;
import org.performance.datastore.DataStore;
import org.performance.jobs.PerformanceTestJob;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static org.jobrunr.jobs.RecurringJobTestBuilder.aDefaultRecurringJob;

public class Scenario05RecurringJobs extends AbstractJobRunrProScenario {

    public Scenario05RecurringJobs(DataStore dataStore, String[] args) {
        super(dataStore, args);
    }

    @Override
    protected long loadJobs() {
        PerformanceTestJob performanceTestJob = new PerformanceTestJob();
        int totalAmountOfRecurringJobs = parseInt(getArg("amount", "50_000").replace("_", ""));
        List<RecurringJob> recurringJobs = IntStream.range(0, totalAmountOfRecurringJobs).boxed()
                .map(i -> aDefaultRecurringJob()
                        .withId(format("rj-%05d", i))
                        .withCronExpression("*/2 * * * *")
                        .withLabels("every 2 min")
                        .withJobDetails(() -> performanceTestJob.testJob(totalAmountOfRecurringJobs, i))
                        .build())
                .toList();

        long startTime = System.nanoTime();
        tool.getStorageProvider().saveRecurringJobs(recurringJobs);
        long endTime = System.nanoTime();
        LOGGER.info("Saved {} recurring jobs in {}", recurringJobs.size(), Duration.ofNanos(endTime - startTime));

//        List<RecurringJob> allJobs = new ArrayList<>(recurringJobs);
//        while (!allJobs.isEmpty()) {
//            List<RecurringJob> jobsToSave = allJobs.subList(0, Math.min(2048, allJobs.size()));
//            storageProvider.saveRecurringJobs(jobsToSave);
//            LOGGER.warn("Saved {} RecurringJobs (still need to save {} out of {})", jobsToSave.size(), allJobs.size(), recurringJobs.size());
//            jobsToSave.clear();
//        }

        for (int i = 0; i < 4; i++) {
            int finalI = i;
            List<RecurringJob> recurringJobsEvery15Seconds = IntStream.range(0, 1000).boxed()
                    .map(j -> {
                        int index = finalI * j;
                        return aDefaultRecurringJob()
                                .withId(format("sec-%d-%03d", finalI * 15, j))
                                .withCronExpression(format("%d * * * * *", finalI * 15))
                                .withLabels(format("%d * * * * *", finalI * 15))
                                .withJobDetails(() -> performanceTestJob.testJob(1000, index))
                                .build();
                    })
                    .toList();

            tool.getStorageProvider().saveRecurringJobs(recurringJobsEvery15Seconds);
        }

        List<RecurringJob> recurringJobsEvery15Seconds = IntStream.range(0, 1000).boxed()
                .map(j -> aDefaultRecurringJob()
                        .withId(format("every-15-sec-%03d", j))
                        .withCronExpression("*/15 * * * * *")
                        .withLabels("*/15 * * * * *")
                        .withJobDetails(() -> performanceTestJob.testJob(1000, j))
                        .build())
                .toList();

        tool.getStorageProvider().saveRecurringJobs(recurringJobsEvery15Seconds);

        return (5L * totalAmountOfRecurringJobs) // jobs scheduled ahead of time during 10 min
                + (4 * 1000 * 11) // jobs running every minute (15 * * * *, ...) during 10 min;
                + (4 * 1000 * 10); // jobs scheduled every 15 seconds during 10 min;
    }
}
