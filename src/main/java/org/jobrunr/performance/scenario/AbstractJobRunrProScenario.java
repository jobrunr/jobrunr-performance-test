package org.jobrunr.performance.scenario;

import org.jobrunr.dashboard.JobRunrDashboardWebServerConfiguration;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.performance.JobRunrDistribution;
import org.jobrunr.performance.storage.DataStore;
import org.jobrunr.server.BackgroundJobServerConfiguration;
import org.jobrunr.server.tasks.zookeeper.ratelimiters.RateLimiterConfiguration;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;

public abstract class AbstractJobRunrProScenario extends AbstractScenario {

    protected AbstractJobRunrProScenario(DataStore dataStore, String[] args) {
        super(dataStore, args);
    }

    protected void initializeJobRunr(StorageProvider storageProvider) {
        BackgroundJobServerConfiguration backgroundJobServerConfiguration = getBackgroundJobServerConfiguration();
        JobRunrDashboardWebServerConfiguration dashboardWebServerConfiguration = getDashboardWebServerConfiguration();
        RateLimiterConfiguration[] rateLimiterConfigurations = getRateLimiterConfigurations();

        storageProvider.setJobMapper(new JobMapper(new JacksonJsonMapper()));

        JobRunrDistribution.JobRunrPro.saveLicense(storageProvider);
        JobRunrDistribution.JobRunrPro.getConfiguration()
                .useStorageProvider(storageProvider)
                .useRateLimiter(rateLimiterConfigurations)
                .useBackgroundJobServer(backgroundJobServerConfiguration, false)
                .useDashboardIf(dashboardWebServerConfiguration != null, dashboardWebServerConfiguration)
                .initialize();
    }

    protected RateLimiterConfiguration[] getRateLimiterConfigurations() {
        return new RateLimiterConfiguration[]{};
    }
}
