package org.jobrunrpro.performance.scenario;

import org.jobrunr.jobs.RecurringJob;
import org.jobrunr.jobs.details.JobDetailsAsmGenerator;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.performance.datastore.DataStore;
import org.performance.jobs.PerformanceTestJob;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.stream.IntStream;

import static java.lang.Integer.parseInt;
import static java.lang.String.format;
import static org.jobrunr.scheduling.ScheduleExpressionType.createScheduleFromString;

public class Scenario05RecurringJobs extends AbstractJobRunrProScenario {

    private static final JobDetailsAsmGenerator JOB_DETAILS_GENERATOR = new JobDetailsAsmGenerator();

    public Scenario05RecurringJobs(DataStore dataStore, String[] args) {
        super(dataStore, args);
    }

    @Override
    protected long loadJobs() {
        PerformanceTestJob performanceTestJob = new PerformanceTestJob();
        int totalAmountOfRecurringJobs = parseInt(getArg("amount", "50_000").replace("_", ""));
        List<RecurringJob> recurringJobs = IntStream.range(0, totalAmountOfRecurringJobs).boxed()
                .map(i -> recurringJob(format("rj-%05d", i), "*/2 * * * *", "every 2 min",
                        () -> performanceTestJob.testJob(totalAmountOfRecurringJobs, i)))
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
                        return recurringJob(format("sec-%d-%03d", finalI * 15, j), format("%d * * * * *", finalI * 15), format("%d * * * * *", finalI * 15),
                                () -> performanceTestJob.testJob(1000, index));
                    })
                    .toList();

            tool.getStorageProvider().saveRecurringJobs(recurringJobsEvery15Seconds);
        }

        List<RecurringJob> recurringJobsEvery15Seconds = IntStream.range(0, 1000).boxed()
                .map(j -> recurringJob(format("every-15-sec-%03d", j), "*/15 * * * * *", "*/15 * * * * *",
                        () -> performanceTestJob.testJob(1000, j)))
                .toList();

        tool.getStorageProvider().saveRecurringJobs(recurringJobsEvery15Seconds);

        return (5L * totalAmountOfRecurringJobs) // jobs scheduled ahead of time during 10 min
                + (4 * 1000 * 11) // jobs running every minute (15 * * * *, ...) during 10 min;
                + (4 * 1000 * 10); // jobs scheduled every 15 seconds during 10 min;
    }

    private static RecurringJob recurringJob(String id, String cronExpression, String label, JobLambda job) {
        RecurringJob recurringJob = new RecurringJob(id, JOB_DETAILS_GENERATOR.toJobDetails(job), createScheduleFromString(cronExpression), ZoneId.systemDefault(), RecurringJob.CreatedBy.API);
        recurringJob.setJobName("a recurring job");
        recurringJob.setLabels(List.of(label));
        return recurringJob;
    }
}
