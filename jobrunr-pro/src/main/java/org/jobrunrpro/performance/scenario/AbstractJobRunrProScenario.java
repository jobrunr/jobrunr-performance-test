package org.jobrunrpro.performance.scenario;

import org.jobrunr.dashboard.JobRunrDashboardWebServerConfiguration;
import org.jobrunr.jobs.queues.Queues;
import org.jobrunr.server.BackgroundJobServerConfiguration;
import org.jobrunr.storage.JobRunrMetadata;
import org.jobrunr.storage.ThreadSafeStorageProvider;
import org.jobrunrpro.performance.JobRunrProTool;
import org.jobrunrpro.performance.scenario.monitor.QueryAnalysisMonitor;
import org.jobrunrpro.performance.utils.MarkdownReporter;
import org.performance.datastore.AnalysingDataStore;
import org.performance.datastore.DataStore;
import org.performance.scenario.AbstractScenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static java.lang.Integer.parseInt;
import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;
import static org.jobrunr.storage.ThreadSafeStorageProvider.MethodStatisticsConfiguration.DETAILED;

public abstract class AbstractJobRunrProScenario extends AbstractScenario<JobRunrProTool> {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private QueryAnalysisMonitor queryAnalysisMonitor;

    protected AbstractJobRunrProScenario(DataStore dataStore, String[] args) {
        super(new JobRunrProTool(), JobRunrProScenarioResult::new, dataStore, args);
    }

    public BackgroundJobServerConfiguration getBackgroundJobServerConfiguration() {
        return usingStandardBackgroundJobServerConfiguration().andPollIntervalInSeconds(5);
    }

    public JobRunrDashboardWebServerConfiguration getDashboardWebServerConfiguration() {
        int dashboardPort = parseInt(getArg("dashboard_port", "0"));
        if (dashboardPort == 0) return null;
        return JobRunrDashboardWebServerConfiguration.usingStandardDashboardConfiguration().andPort(dashboardPort);
    }

    @Override
    protected Instant startProcessingJobs() throws Exception {
        Instant startTime = super.startProcessingJobs();
        if (isDataStoreQueryTimeLoggingEnabled()) {
            ThreadSafeStorageProvider.setMethodStatisticsConfiguration(DETAILED);
            initQueryAnalysisMonitorIfSupportedByDataStore(startTime);
            LOGGER.warn("DataStore query time logging enabled: {}", ThreadSafeStorageProvider.getMethodStatisticsConfiguration());
        }
        return startTime;
    }

    @Override
    protected void appendToLogbook(String... extraParams) {
        super.appendToLogbook(extraParams);
        if (queryAnalysisMonitor != null) {
            JobRunrProScenarioResult scenarioResult = (JobRunrProScenarioResult) this.scenarioResult;
            scenarioResult.setMethodStatistics(queryAnalysisMonitor.getMethodStatistics());
            scenarioResult.setQueryAnalyses(queryAnalysisMonitor.getQueryAnalyses());
            MarkdownReporter.render(tool.getBackgroundJobServer(), (AnalysingDataStore) dataStore, scenarioResult);
        }
    }

    private void initQueryAnalysisMonitorIfSupportedByDataStore(Instant startTime) {
        if (dataStore instanceof AnalysingDataStore) {
            this.queryAnalysisMonitor = new QueryAnalysisMonitor((AnalysingDataStore) dataStore, startTime, getMaxScenarioDuration(), 0.1, 0.25, 0.5, 0.75, 0.9);
            tool.getStorageProvider().addJobStorageOnChangeListener(queryAnalysisMonitor);
        }
    }

    private boolean isDataStoreQueryTimeLoggingEnabled() {
        return getBooleanArg("log_data_store_timings");
    }

    public Queues getQueues() {
        return new Queues(Queues.DEFAULT_QUEUE, Queues.DEFAULT_QUEUE);
    }

    public JobRunrMetadata[] getRateLimiterConfigurationsAsMetadata() {
        return new JobRunrMetadata[]{};
    }
}
