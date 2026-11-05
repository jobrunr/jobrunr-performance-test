package org.jobrunr.performance;

import org.jobrunr.configuration.JobRunr;
import org.jobrunr.configuration.JobRunrConfiguration.JobRunrConfigurationResult;
import org.jobrunr.dashboard.JobRunrDashboardWebServerConfiguration;
import org.jobrunr.performance.scenario.AbstractJobRunrScenario;
import org.jobrunr.performance.scenario.monitor.JobActivityTracker;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.server.BackgroundJobServerConfiguration;
import org.jobrunr.server.BackgroundJobServerConfigurationReader;
import org.jobrunr.storage.JobRunrMetadata;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.nosql.mongo.MongoDBStorageProvider;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.performance.datastore.DataStore;
import org.performance.datastore.nosql.MongoDBDataStore;
import org.performance.datastore.sql.AbstractSqlDataStore;
import org.performance.scenario.Scenario;
import org.performance.scenario.ScenarioMonitor;
import org.performance.scenario.JobActivityScenarioMonitor;
import org.performance.tools.Tool;
import org.performance.utils.JarUtils;

import java.time.Duration;

public class JobRunrTool implements Tool {

    private static final String EXPECTED_JOBS_METADATA = "performance-test-expected-jobs";
    private static final String EXPECTED_JOBS_METADATA_OWNER = "cluster";

    private StorageProvider storageProvider;
    private final JobActivityTracker jobActivityTracker = new JobActivityTracker(JobRunr::getBackgroundJobServer);
    private JobRunrConfigurationResult jobRunr;

    @Override
    public String getName() {
        return JarUtils.getToolName(JobScheduler.class);
    }

    @Override
    public String getVersion() {
        return JarUtils.getToolVersion(JobScheduler.class);
    }

    @Override
    public void initialize(DataStore dataStore, Scenario scenario) {
        this.initialize(dataStore, (AbstractJobRunrScenario) scenario);
    }

    private void initialize(DataStore dataStore, AbstractJobRunrScenario scenario) {
        if (dataStore instanceof AbstractSqlDataStore) {
            storageProvider = SqlStorageProviderFactory.using(((AbstractSqlDataStore) dataStore).dataSource());
        } else if (dataStore instanceof MongoDBDataStore) {
            storageProvider = new MongoDBStorageProvider(((MongoDBDataStore) dataStore).mongoClient());
        } else {
            throw new IllegalArgumentException("DataStore must be an instance of AbstractSqlDataStore or MongoDBDataStore");
        }

        BackgroundJobServerConfiguration backgroundJobServerConfiguration = scenario.getBackgroundJobServerConfiguration();
        JobRunrDashboardWebServerConfiguration dashboardWebServerConfiguration = scenario.getDashboardWebServerConfiguration();

        jobRunr = JobRunr.configure()
                .useJsonMapper(scenario.getJsonMapper())
                .useStorageProvider(storageProvider)
                .useBackgroundJobServer(backgroundJobServerConfiguration, false)
                .useDashboardIf(dashboardWebServerConfiguration != null, dashboardWebServerConfiguration)
                .withJobFilter(jobActivityTracker)
                .initialize();
    }

    @Override
    public void start() {
        JobRunr.getBackgroundJobServer().start();
    }

    @Override
    public ScenarioMonitor createScenarioMonitor(long createdJobs, Duration maxScenarioDuration) {
        BackgroundJobServerConfigurationReader configuration = JobRunr.getBackgroundJobServer().getConfiguration();
        return new JobActivityScenarioMonitor(createdJobs, maxScenarioDuration, jobActivityTracker, configuration.getPollInterval(), configuration.getServerTimeoutPollIntervalMultiplicand());
    }

    @Override
    public void stop() {
        JobRunr.destroy();
    }

    @Override
    public void saveAmountOfExpectedJobs(long amountOfExpectedJobs) {
        storageProvider.saveMetadata(new JobRunrMetadata(EXPECTED_JOBS_METADATA, EXPECTED_JOBS_METADATA_OWNER, amountOfExpectedJobs));
    }

    @Override
    public long getAmountOfExpectedJobs() {
        JobRunrMetadata amountOfExpectedJobs = storageProvider.getMetadata(EXPECTED_JOBS_METADATA, EXPECTED_JOBS_METADATA_OWNER);
        if (amountOfExpectedJobs == null) {
            throw new IllegalStateException("No expected amount of jobs found, the loader must finish before the workers start");
        }
        return amountOfExpectedJobs.getValueAsLong();
    }

    public StorageProvider getStorageProvider() {
        return storageProvider;
    }
}
