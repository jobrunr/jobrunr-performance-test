package org.jobrunr.performance.scenario;

import org.jobrunr.dashboard.JobRunrDashboardWebServerConfiguration;
import org.jobrunr.jobs.queues.Queues;
import org.jobrunr.performance.scenario.jobs.PerformanceTestJob;
import org.jobrunr.performance.storage.DataStore;
import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.scheduling.JobBuilder;
import org.jobrunr.scheduling.JobProId;
import org.jobrunr.scheduling.cron.Cron;
import org.jobrunr.server.BackgroundJobServerConfiguration;
import org.jobrunr.server.configuration.RoundRobinDynamicQueuePolicy;
import org.jobrunr.server.tasks.zookeeper.ratelimiters.RateLimiterConfiguration;

import java.time.Duration;
import java.time.Instant;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;
import static java.time.temporal.ChronoUnit.MILLIS;
import static java.time.temporal.ChronoUnit.SECONDS;
import static org.jobrunr.scheduling.JobBuilder.aBatchJob;
import static org.jobrunr.scheduling.JobBuilder.aJob;
import static org.jobrunr.scheduling.RecurringJobBuilder.aRecurringJob;
import static org.jobrunr.server.tasks.zookeeper.ratelimiters.ConcurrentJobRateLimiterConfiguration.concurrentJobRateLimiter;

public class Scenario00CombinedScenario extends AbstractJobRunrProScenario {

    private static final String HIGH_PRIO = "High Prio";
    private static final String LOW_PRIO = "Low Prio";

    private final int amountOfDynamicQueues;
    private final int amountOfBatchJobs;
    private final int totalAmountOfRecurringJobs;

    public Scenario00CombinedScenario(DataStore dataStore, String[] args) {
        super(dataStore, args);
        amountOfDynamicQueues = 100;
        amountOfBatchJobs = 10;
        totalAmountOfRecurringJobs = 5000;
    }

    @Override
    protected BackgroundJobServerConfiguration getBackgroundJobServerConfiguration() {
        return super.getBackgroundJobServerConfiguration()
                .andDynamicQueuePolicy(new RoundRobinDynamicQueuePolicy("tenant"));
    }

    @Override
    protected JobRunrDashboardWebServerConfiguration getDashboardWebServerConfiguration() {
        return super.getDashboardWebServerConfiguration()
                .andDynamicQueueConfiguration("Tenants", "tenant: ");
    }

    @Override
    protected RateLimiterConfiguration[] getRateLimiterConfigurations() {
        return IntStream.range(0, 10).boxed()
                .map(Scenario00CombinedScenario::getRateLimiterName)
                .map(name -> concurrentJobRateLimiter(name, 5))
                .toArray(RateLimiterConfiguration[]::new);
    }

    @Override
    protected Queues getQueues() {
        return new Queues(Queues.DEFAULT_QUEUE, HIGH_PRIO, Queues.DEFAULT_QUEUE, LOW_PRIO);
    }

    @Override
    protected long loadJobs() {
        int totalAmountOfJobs = parseInt(getArg("amount", "0").replace("_", ""));
        if (totalAmountOfJobs < 1) return 0;

        createRecurringJobs();

        int amountOfJobsCreated = 0;
        amountOfJobsCreated += createJobsInDynamicQueues((int) (totalAmountOfJobs * 0.5));
        amountOfJobsCreated += createBatchJobs((int) (totalAmountOfJobs * 0.3));
        amountOfJobsCreated += createAwaitingJobs((int) (totalAmountOfJobs * 0.1));
        amountOfJobsCreated += createScheduledJobs((int) (totalAmountOfJobs * 0.1));
        return amountOfJobsCreated;
    }

    private int createJobsInDynamicQueues(int amountOfJobs) {
        int jobsPerDynamicQueue = amountOfJobs / amountOfDynamicQueues;
        for (int i = 0; i < amountOfDynamicQueues; i++) {
            // here we create jobs. The jobs between Tenant-010 and Tenant-020 will have rate limiters allowing max 5 jobs at the same time
            String dynamicQueue = String.format("Tenant-%03d", i); // Zero-padded queue names
            String label = "tenant: " + dynamicQueue;

            int finalI = i;
            Stream<JobBuilder> jobBuilderStream = IntStream.range(0, jobsPerDynamicQueue).boxed()
                    .map(j -> aJob()
                            .withName("Job " + j + " for " + label)
                            .<PerformanceTestJob>withDetails(x -> x.testJob(jobsPerDynamicQueue, j))
                            .withRateLimiter(finalI >= 10 && finalI < 20 ? getRateLimiterName(finalI - 10) : null)
                            .withLabels(label));
            BackgroundJob.create(jobBuilderStream);
            LOGGER.info("   Created {} jobs in total | {} for {}", (i + 1) * jobsPerDynamicQueue, jobsPerDynamicQueue, dynamicQueue);
        }
        return amountOfJobs;
    }

    private int createBatchJobs(int totalAmountOfChildJobs) {
        int childJobsPerBatchJob = totalAmountOfChildJobs / amountOfBatchJobs;
        for (int i = 0; i < amountOfBatchJobs; i++) {
            int finalI = i;
            String batchJobName = getBatchJobName(finalI);
            BackgroundJob.create(aBatchJob()
                    .withName(batchJobName)
                    .withLabels("my-batch-job-" + i)
                    .<PerformanceTestJob>withDetails(x -> x.batchJob(childJobsPerBatchJob, finalI)));
        }
        return amountOfBatchJobs + totalAmountOfChildJobs;
    }

    private int createAwaitingJobs(int totalAmountOfAwaitingJobs) {
        int totalAmountOfJobsBeingAwaited = totalAmountOfAwaitingJobs / 4;
        for (int i = 0; i < totalAmountOfJobsBeingAwaited; i++) {
            int finalI = i;
            JobProId anAwaitedJob1 = BackgroundJob.create(aJob()
                    .withName("Awaited Job " + i + " 1/4")
                    .<PerformanceTestJob>withDetails(x -> x.testJob(totalAmountOfAwaitingJobs, finalI))
                    .withLabels("an awaited job"));

            JobProId anAwaitedJob2 = BackgroundJob.create(aJob()
                    .runAfter(anAwaitedJob1)
                    .withName("Awaited Job " + i + " 2/4 awaiting " + anAwaitedJob1.asUUID() + " (1/4)")
                    .<PerformanceTestJob>withDetails(x -> x.testJob(totalAmountOfAwaitingJobs, finalI))
                    .withLabels("an awaited job"));

            JobProId anAwaitedJob3 = BackgroundJob.create(aJob()
                    .runAfter(anAwaitedJob2)
                    .withName("Awaited Job " + i + " 3/4 awaiting " + anAwaitedJob1.asUUID() + " (2/4)")
                    .<PerformanceTestJob>withDetails(x -> x.testJob(totalAmountOfAwaitingJobs, finalI))
                    .withLabels("an awaited job"));

            JobProId anAwaitedJob4 = BackgroundJob.create(aJob()
                    .runAfter(anAwaitedJob2)
                    .withName("Awaited Job " + i + " 4/4 awaiting " + anAwaitedJob1.asUUID() + " (2/4)")
                    .<PerformanceTestJob>withDetails(x -> x.testJob(totalAmountOfAwaitingJobs, finalI))
                    .withLabels("an awaited job"));

            anAwaitedJob1.<PerformanceTestJob>onFailure(x -> x.testJob(totalAmountOfAwaitingJobs, finalI));
        }
        return totalAmountOfAwaitingJobs;
    }

    private int createScheduledJobs(int amountOfJobs) {
        Stream<JobBuilder> jobBuilderStream = IntStream.range(0, amountOfJobs).boxed()
                .map(j -> aJob()
                        .scheduleAt(Instant.now().plus(30, SECONDS).plus(j, MILLIS))
                        .withName("Scheduled Job " + j)
                        .<PerformanceTestJob>withDetails(x -> x.testJob(amountOfJobs, j))
                        .withLabels("my scheduled job"));
        BackgroundJob.create(jobBuilderStream);
        return amountOfJobs;
    }

    private int createRecurringJobs() {
        int jobsEvery15Seconds = totalAmountOfRecurringJobs / 4;
        int jobsEvery30Seconds = totalAmountOfRecurringJobs / 4;
        int jobsEveryEveningAt8pm = totalAmountOfRecurringJobs / 4;
        int jobsEveryEveningWithDuration = totalAmountOfRecurringJobs / 4;

        for (int i = 0; i < jobsEvery15Seconds; i++) {
            int finalI = i;
            BackgroundJob.createRecurrently(aRecurringJob()
                    .withId("rec-job-15s-" + finalI)
                    .withName("Recurring Job " + finalI)
                    .withCron(Cron.every15seconds())
                    .withLabels("recurring job every 15 seconds " + i)
                    .withDeleteOnSuccess(Duration.ofSeconds(15))
                    .withQueue(HIGH_PRIO)
                    .withMaxConcurrentJobs(3)
                    .<PerformanceTestJob>withDetails(x -> x.testJob(jobsEvery15Seconds, finalI)));
        }
        for (int i = 0; i < jobsEvery30Seconds; i++) {
            int finalI = i;
            BackgroundJob.createRecurrently(aRecurringJob()
                    .withId("rec-job-30s-" + finalI)
                    .withName("Recurring Job " + finalI)
                    .withCron(Cron.every30seconds())
                    .withLabels("recurring job every 30 seconds " + i)
                    .<PerformanceTestJob>withDetails(x -> x.testJob(jobsEvery30Seconds, finalI)));
        }
        for (int i = 0; i < jobsEveryEveningAt8pm; i++) {
            int finalI = i;
            BackgroundJob.createRecurrently(aRecurringJob()
                    .withId("rec-job-8pm-" + finalI)
                    .withName("Recurring Job " + finalI)
                    .withCron(Cron.daily(20))
                    .withLabels("recurring job at 8pm " + i)
                    .<PerformanceTestJob>withDetails(x -> x.testJob(jobsEveryEveningAt8pm, finalI)));
        }
        for (int i = 0; i < jobsEveryEveningWithDuration; i++) {
            int finalI = i;
            BackgroundJob.createRecurrently(aRecurringJob()
                    .withId("rec-job-duration-1h-" + finalI)
                    .withName("Recurring Job " + finalI)
                    .withInterval(Duration.ofHours(1))
                    .withLabels("recurring job using duration, " + i)
                    .<PerformanceTestJob>withDetails(x -> x.testJob(jobsEveryEveningWithDuration, finalI)));
        }
        return 0;
    }

    private static String getRateLimiterName(int index) {
        return "concurrent-rate-limiter-" + index;
    }

    private static String getBatchJobName(int index) {
        return "batch job " + index;
    }
}
