package org.jobrunr.performance.scenario;

import org.jobrunr.dashboard.JobRunrDashboardWebServerConfiguration;
import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.scheduling.JobBuilder;
import org.jobrunr.server.BackgroundJobServerConfiguration;
import org.jobrunr.server.configuration.RoundRobinDynamicQueuePolicy;
import org.performance.datastore.DataStore;
import org.performance.jobs.PerformanceTestJob;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;
import static org.jobrunr.scheduling.JobBuilder.aJob;

public class Scenario02ProcessJobsInDynamicQueues extends AbstractJobRunrProScenario {

    public Scenario02ProcessJobsInDynamicQueues(DataStore dataStore, String[] args) {
        super(dataStore, args);
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
    protected long loadJobs() {
        int totalAmountOfJobs = parseInt(getArg("amount", "0").replace("_", ""));
        if (totalAmountOfJobs < 1) return 0;

        int dynamicQueueCount = 1000;
        int jobsPerDynamicQueue = totalAmountOfJobs / dynamicQueueCount;
        for (int i = 0; i < dynamicQueueCount; i++) {
            String dynamicQueue = String.format("Tenant-%03d", i); // Zero-padded queue names
            String label = "tenant: " + dynamicQueue;
            Stream<JobBuilder> jobBuilderStream = IntStream.range(0, jobsPerDynamicQueue).boxed()
                    .map(j -> aJob()
                            .withName("Job " + j + " for " + label)
                            .<PerformanceTestJob>withDetails(x -> x.testJob(jobsPerDynamicQueue, j))
                            .withLabels(label));
            BackgroundJob.create(jobBuilderStream);
            LOGGER.info("   Created {} jobs in total | {} for {}", (i + 1) * jobsPerDynamicQueue, jobsPerDynamicQueue, dynamicQueue);
        }
        return totalAmountOfJobs;
    }
}
