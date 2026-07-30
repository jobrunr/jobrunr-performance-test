package org.jobrunrpro.performance;

import org.jobrunr.configuration.JobRunrConfiguration;
import org.jobrunr.configuration.JobRunrPro;
import org.jobrunr.dashboard.JobRunrDashboardWebServerConfiguration;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.jobs.queues.Queues;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.server.BackgroundJobServerConfiguration;
import org.jobrunr.storage.DatabaseOptions;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.temporal.ChronoUnit;

import static java.util.Arrays.stream;
import static org.jobrunr.utils.resilience.RateLimiter.Builder.rateLimit;
import static org.performance.utils.StringUtils.isNullOrEmpty;

public class JobRunrProTool implements Tool {

    private static final Logger LOGGER = LoggerFactory.getLogger(JobRunrProTool.class);

    private StorageProvider storageProvider;
    private JobRunrConfiguration.JobRunrConfigurationResult jobRunrPro;
    private final String jobRunrProLicenseFromEnv;

    public JobRunrProTool() {
        jobRunrProLicenseFromEnv = System.getenv("JOBRUNRPRO_LICENSE");
        if (isNullOrEmpty(jobRunrProLicenseFromEnv)) {
            throw new IllegalStateException("JobRunr Pro license is missing. Please set the JOBRUNRPRO_LICENSE environment variable.");
        }
    }

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
        this.initialize(dataStore, (AbstractJobRunrProScenario) scenario);
    }

    private void initialize(DataStore dataStore, AbstractJobRunrProScenario scenario) {
        if (dataStore instanceof AbstractSqlDataStore) {
            storageProvider = SqlStorageProviderFactory.using(((AbstractSqlDataStore) dataStore).dataSource());
        } else if (dataStore instanceof MongoDBDataStore) {
            storageProvider = new MongoDBStorageProvider(((MongoDBDataStore) dataStore).mongoClient());
        } else {
            throw new IllegalArgumentException("DataStore must be an instance of AbstractSqlDataStore or MongoDBDataStore");
        }

        storageProvider.setJobMapper(new JobMapper(new JacksonJsonMapper()));

        setupLicense();

        Queues queues = scenario.getQueues();
        BackgroundJobServerConfiguration backgroundJobServerConfiguration = scenario.getBackgroundJobServerConfiguration();
        JobRunrDashboardWebServerConfiguration dashboardWebServerConfiguration = scenario.getDashboardWebServerConfiguration();

        // save rate limiters
        JobRunrMetadata[] rateLimiterConfigurations = scenario.getRateLimiterConfigurationsAsMetadata();
        stream(rateLimiterConfigurations).forEach(storageProvider::saveMetadata);

        jobRunrPro = JobRunrPro.configure()
                .usePriorityQueues(Queues.DEFAULT_QUEUE, queues.getAllQueues().toArray(new String[0]))
                .useStorageProvider(storageProvider)
                .useBackgroundJobServer(backgroundJobServerConfiguration, false)
                .useDashboardIf(dashboardWebServerConfiguration != null, dashboardWebServerConfiguration)
                .initialize();
    }

    private void setupLicense() {
        LOGGER.info("Setting up JobRunr Pro license for JobRunr Pro {} from environment variable", JarUtils.getVersion(storageProvider));
        storageProvider.saveLicense(jobRunrProLicenseFromEnv);
    }

    @Override
    public void start() {
        JobRunrPro.getBackgroundJobServer().start();
    }

    @Override
    public ScenarioMonitor createScenarioMonitor(long createdJobs, Duration maxScenarioDuration) throws Exception {
        JobRunrProScenarioMonitor scenarioMonitor = new JobRunrProScenarioMonitor(createdJobs, maxScenarioDuration);
        storageProvider.addJobStorageOnChangeListener(scenarioMonitor);
        return scenarioMonitor;
    }

    @Override
    public void stop() {
        JobRunrPro.destroy();
    }

    public StorageProvider getStorageProvider() {
        return storageProvider;
    }

    public BackgroundJobServer getBackgroundJobServer() {
        return JobRunrPro.getBackgroundJobServer();
    }
}
