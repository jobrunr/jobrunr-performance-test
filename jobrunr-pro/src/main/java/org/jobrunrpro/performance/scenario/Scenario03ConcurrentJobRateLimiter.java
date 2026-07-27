package org.jobrunrpro.performance.scenario;

import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.scheduling.JobBuilder;
import org.jobrunr.storage.JobRunrMetadata;
import org.performance.datastore.DataStore;
import org.performance.jobs.PerformanceTestJob;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;
import static org.jobrunr.scheduling.JobBuilder.aJob;
import static org.jobrunrpro.performance.scenario.ratelimiter.RateLimiterConfiguration.concurrentJobRateLimiter;

public class Scenario03ConcurrentJobRateLimiter extends AbstractJobRunrProScenario {

    private final int rateLimiters;

    public Scenario03ConcurrentJobRateLimiter(DataStore dataStore, String[] args) {
        this(dataStore, args, 10);
    }

    public Scenario03ConcurrentJobRateLimiter(DataStore dataStore, String[] args, int rateLimiters) {
        super(dataStore, args);
        this.rateLimiters = rateLimiters;
    }

    @Override
    public JobRunrMetadata[] getRateLimiterConfigurationsAsMetadata() {
        int totalAmountOfThreads = Runtime.getRuntime().availableProcessors() * 8;
        int amountOfThreadsPerRateLimiter = totalAmountOfThreads / rateLimiters;

        return IntStream.range(0, rateLimiters).boxed()
                .map(Scenario03ConcurrentJobRateLimiter::getRateLimiterName)
                .map(name -> concurrentJobRateLimiter(name, amountOfThreadsPerRateLimiter))
                .toArray(JobRunrMetadata[]::new);
    }

    @Override
    protected long loadJobs() {
        int totalAmountOfJobs = parseInt(getArg("amount", "0").replace("_", ""));
        if (totalAmountOfJobs < 1) return 0;

        int jobsPerRateLimiter = totalAmountOfJobs / rateLimiters;
        for (int i = 0; i < rateLimiters; i++) {
            String rateLimiter = getRateLimiterName(i);
            Stream<JobBuilder> jobBuilderStream = IntStream.range(0, jobsPerRateLimiter).boxed()
                    .map(j -> aJob()
                            .withName("Job " + j + " with concurrent rate limiter '" + rateLimiter + "'")
                            .withRateLimiter(rateLimiter)
                            .<PerformanceTestJob>withJobLambda(x -> x.testJob(jobsPerRateLimiter, j))
                            .withLabels(rateLimiter));
            BackgroundJob.create(jobBuilderStream);
            LOGGER.info("   Created {} jobs in total | {} for {}", (i + 1) * jobsPerRateLimiter, jobsPerRateLimiter, rateLimiter);
        }
        return totalAmountOfJobs;
    }

    private static String getRateLimiterName(int index) {
        return "my-concurrent-rate-limiter-" + index;
    }
}
