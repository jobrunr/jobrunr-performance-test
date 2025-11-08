package org.jobrunr.performance.scenario;

import org.jobrunr.dashboard.JobRunrDashboardWebServerConfiguration;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.jobs.queues.Queues;
import org.jobrunr.performance.JobRunrDistribution;
import org.jobrunr.server.BackgroundJobServerConfiguration;
import org.jobrunr.storage.JobRunrMetadata;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.performance.datastore.DataStore;

import static java.util.Arrays.stream;

public abstract class AbstractJobRunrProScenario extends AbstractJobRunrScenario {

    protected AbstractJobRunrProScenario(DataStore dataStore, String[] args) {
        super(dataStore, args);
    }

    protected void initializeJobRunr(StorageProvider storageProvider) {
        Queues queues = getQueues();
        BackgroundJobServerConfiguration backgroundJobServerConfiguration = getBackgroundJobServerConfiguration();
        JobRunrDashboardWebServerConfiguration dashboardWebServerConfiguration = getDashboardWebServerConfiguration();
        JobRunrMetadata[] rateLimiterConfigurations = getRateLimiterConfigurationsAsMetadata();

        storageProvider.setJobMapper(new JobMapper(new JacksonJsonMapper()));

        stream(rateLimiterConfigurations).forEach(storageProvider::saveMetadata);

        JobRunrDistribution.JobRunrPro.saveLicense(storageProvider);
        JobRunrDistribution.JobRunrPro.getConfiguration()
                .useQueues(Queues.DEFAULT_QUEUE, queues.getAllQueues().toArray(new String[0]))
                .useStorageProvider(storageProvider)
                .useBackgroundJobServer(backgroundJobServerConfiguration, false)
                .useDashboardIf(dashboardWebServerConfiguration != null, dashboardWebServerConfiguration)
                .initialize();
    }

    protected Queues getQueues() {
        return new Queues(Queues.DEFAULT_QUEUE, Queues.DEFAULT_QUEUE);
    }

    protected JobRunrMetadata[] getRateLimiterConfigurationsAsMetadata() {
        return new JobRunrMetadata[]{};
    }
}
