package org.jobrunr.performance.scenario;

import org.jobrunr.server.BackgroundJobServerConfiguration;
import org.jobrunr.storage.DataStore;
import org.jobrunr.storage.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.ArgUtils;

import static java.lang.Integer.parseInt;
import static org.jobrunr.performance.JobRunrFactory.backgroundJobServer;
import static org.jobrunr.performance.JobRunrFactory.jobRunr;
import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;

public abstract class AbstractScenario implements Scenario {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private final DataStore dataStore;
    protected final String[] args;

    public AbstractScenario(DataStore dataStore, String[] args) {
        this.dataStore = dataStore;
        this.args = args;
    }

    protected BackgroundJobServerConfiguration getBackgroundJobServerConfiguration() {
        return usingStandardBackgroundJobServerConfiguration().andPollIntervalInSeconds(5);
    }

    protected abstract long loadJobs();

    public void run() {
        startDataStoreAndInitializeJobRunr();
        long totalAmountOfJobsCreated = loadJobs();
        dataStore.updateStatistics();

        backgroundJobServer().start();
        long startTime = System.currentTimeMillis();
        LOGGER.info("Enqueued {} jobs - processing started", totalAmountOfJobsCreated);
        System.out.println("Todo: wait until jobs are done and create a CSV file for the scenario");
    }

    private void startDataStoreAndInitializeJobRunr() {
        dataStore.start();
        StorageProvider storageProvider = dataStore.getStorageProvider();
        initializeJobRunr(storageProvider);
    }

    protected void initializeJobRunr(StorageProvider storageProvider) {
        int dashboardPort = parseInt(getArg("dashboard_port", "0"));
        jobRunr()
                .useStorageProvider(storageProvider)
                .useBackgroundJobServer(getBackgroundJobServerConfiguration(), false)
                .useDashboardIf(dashboardPort > 0, dashboardPort)
                .initialize();
    }

    protected String getArg(String key) {
        return ArgUtils.getArg(args, key, null);
    }

    protected String getArg(String key, String defaultValue) {
        return ArgUtils.getArg(args, key, defaultValue);
    }
}
