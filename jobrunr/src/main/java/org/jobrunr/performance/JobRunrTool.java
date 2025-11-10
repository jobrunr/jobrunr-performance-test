package org.jobrunr.performance;

import org.jobrunr.configuration.JobRunr;
import org.jobrunr.configuration.JobRunrConfiguration;
import org.jobrunr.performance.scenario.AbstractJobRunrScenario;
import org.jobrunr.performance.scenario.monitor.JobRunrScenarioMonitor;
import org.jobrunr.scheduling.JobScheduler;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.nosql.mongo.MongoDBStorageProvider;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.performance.datastore.DataStore;
import org.performance.datastore.nosql.MongoDBDataStore;
import org.performance.datastore.sql.AbstractSqlDataStore;
import org.performance.scenario.Scenario;
import org.performance.scenario.ScenarioMonitor;
import org.performance.tools.Tool;
import org.performance.utils.JarUtils;

import java.time.Duration;

public class JobRunrTool implements Tool {

    private StorageProvider storageProvider;
    private JobRunrConfiguration.JobRunrConfigurationResult jobRunr;

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
        this.initialize(dataStore, (AbstractJobRunrScenario) scenario);
    }

    private void initialize(DataStore dataStore, AbstractJobRunrScenario scenario) throws Exception {
        if (dataStore instanceof AbstractSqlDataStore<?>) {
            storageProvider = SqlStorageProviderFactory.using(((AbstractSqlDataStore<?>) dataStore).getDataSource());
        } else if (dataStore instanceof MongoDBDataStore) {
            storageProvider = new MongoDBStorageProvider(((MongoDBDataStore) dataStore).mongoClient());
        } else {
            throw new IllegalArgumentException("DataStore must be an instance of AbstractSqlDataStore or MongoDBDataStore");
        }

        jobRunr = JobRunr.configure()
                .useStorageProvider(storageProvider)
                .useBackgroundJobServer(scenario.getBackgroundJobServerConfiguration(), false)
                .useDashboard(scenario.getDashboardWebServerConfiguration())
                .initialize();
    }

    @Override
    public void start() throws Exception {
        JobRunr.getBackgroundJobServer().start();
    }

    @Override
    public ScenarioMonitor createScenarioMonitor(long createdJobs, Duration maxScenarioDuration) throws Exception {
        JobRunrScenarioMonitor scenarioMonitor = new JobRunrScenarioMonitor(createdJobs, maxScenarioDuration);
        storageProvider.addJobStorageOnChangeListener(scenarioMonitor);
        return scenarioMonitor;
    }

    @Override
    public void stop() throws Exception {
        JobRunr.destroy();
    }
}
