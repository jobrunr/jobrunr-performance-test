package org.jobrunrpro.performance;

import org.jobrunr.configuration.JobRunrConfiguration;
import org.jobrunr.configuration.JobRunrPro;
import org.jobrunr.dashboard.JobRunrDashboardWebServerConfiguration;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.jobs.queues.Queues;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.server.BackgroundJobServerConfiguration;
import org.jobrunr.storage.JobRunrMetadata;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.nosql.mongo.MongoDBStorageProvider;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.jobrunrpro.performance.scenario.AbstractJobRunrProScenario;
import org.jobrunrpro.performance.scenario.monitor.JobRunrProScenarioMonitor;
import org.performance.datastore.DataStore;
import org.performance.datastore.nosql.MongoDBDataStore;
import org.performance.datastore.sql.AbstractSqlDataStore;
import org.performance.scenario.Scenario;
import org.performance.scenario.ScenarioMonitor;
import org.performance.tools.Tool;
import org.performance.utils.JarUtils;

import java.time.Duration;

import static java.util.Arrays.stream;

public class JobRunrProTool implements Tool {

    private StorageProvider storageProvider;
    private JobRunrConfiguration.JobRunrConfigurationResult jobRunrPro;

    @Override
    public String getName() {
        return JarUtils.getToolName(JobScheduler.class);
    }

    @Override
    public String getVersion() {
        return JarUtils.getToolVersion(JobScheduler.class);
    }

    @Override
    public void initialize(DataStore dataStore, Scenario scenario) throws Exception {
        this.initialize(dataStore, (AbstractJobRunrProScenario) scenario);
    }

    private void initialize(DataStore dataStore, AbstractJobRunrProScenario scenario) throws Exception {
        if (dataStore instanceof AbstractSqlDataStore<?>) {
            storageProvider = SqlStorageProviderFactory.using(((AbstractSqlDataStore<?>) dataStore).getDataSource());
        } else if (dataStore instanceof MongoDBDataStore) {
            storageProvider = new MongoDBStorageProvider(((MongoDBDataStore) dataStore).mongoClient());
        } else {
            throw new IllegalArgumentException("DataStore must be an instance of AbstractSqlDataStore or MongoDBDataStore");
        }

        setupLicense();

        Queues queues = scenario.getQueues();
        BackgroundJobServerConfiguration backgroundJobServerConfiguration = scenario.getBackgroundJobServerConfiguration();
        JobRunrDashboardWebServerConfiguration dashboardWebServerConfiguration = scenario.getDashboardWebServerConfiguration();

        // save rate limiters
        JobRunrMetadata[] rateLimiterConfigurations = scenario.getRateLimiterConfigurationsAsMetadata();
        storageProvider.setJobMapper(new JobMapper(new JacksonJsonMapper()));
        stream(rateLimiterConfigurations).forEach(storageProvider::saveMetadata);

        jobRunrPro = JobRunrPro.configure()
                .useQueues(Queues.DEFAULT_QUEUE, queues.getAllQueues().toArray(new String[0]))
                .useStorageProvider(storageProvider)
                .useBackgroundJobServer(backgroundJobServerConfiguration, false)
                .useDashboardIf(dashboardWebServerConfiguration != null, dashboardWebServerConfiguration)
                .initialize();
    }

    private void setupLicense() {
        String license = getVersion().endsWith("-SNAPSHOT")
                ? "eyJhbGciOiJFQyIsImNydiI6ICJQLTI1NiIsInR5cCI6ICJKV1QifQ.eyJzdWJzY3JpcHRpb25JZCI6IjMxOTI5ZDYzLWRhMDItNDYwMi04ZjliLWYyMzk1YTY3YzUwOSIsImNvbXBhbnkiOiJSb3NvY28gQlYiLCJ0cmlhbCI6ZmFsc2UsInZhbGlkVW50aWwiOiIyMDI1LTA2LTMwIn0.zMBypBCY9n3qX4d7KXyGsHGLmfeauGVmOq5sz7Lszeo0sKXsoTyu4djKCcnWIbO9BOpye65i4QUJRwjT73Yu3w=="
                : "eyJhbGciOiJFQyIsImNydiI6ICJQLTI1NiIsInR5cCI6ICJKV1QifQ.eyJzdWJzY3JpcHRpb25JZCI6IjFmNDM3NzczLWI3Y2YtNGIyZC04NjQxLWEyNGI0ZWQzN2U0OSIsImNvbXBhbnkiOiJKb2JSdW5yIFBybyBQZXJmb3JtYW5jZSBUZXN0IExpY2Vuc2UiLCJ0cmlhbCI6ZmFsc2UsInZhbGlkVW50aWwiOiIyMDI1LTA3LTAyIn0.eYnjjfEnm-R_GdQ4f7EJ4AmBRCoZldkJFGI2Pgiq4mn7B0MdRBt5BbSJHHBvdazLc0b7QYeeX8B_RkQLpmW-HA==";

        storageProvider.saveLicense(license);
    }

    @Override
    public void start() throws Exception {
        JobRunrPro.getBackgroundJobServer().start();
    }

    @Override
    public ScenarioMonitor createScenarioMonitor(long createdJobs, Duration maxScenarioDuration) throws Exception {
        JobRunrProScenarioMonitor scenarioMonitor = new JobRunrProScenarioMonitor(createdJobs, maxScenarioDuration);
        storageProvider.addJobStorageOnChangeListener(scenarioMonitor);
        return scenarioMonitor;
    }

    @Override
    public void stop() throws Exception {
        JobRunrPro.destroy();
    }

    public StorageProvider getStorageProvider() {
        return storageProvider;
    }
}
