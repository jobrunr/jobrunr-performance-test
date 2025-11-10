package org.jobrunr.performance.scenario;

import org.jobrunr.dashboard.JobRunrDashboardWebServerConfiguration;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.performance.JobRunrDistribution;
import org.jobrunr.performance.utils.ArgUtils;
import org.jobrunr.server.BackgroundJobServerConfiguration;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.performance.datastore.DataStore;
import org.performance.scenario.AbstractScenario;
import org.performance.tools.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;

public abstract class AbstractJobRunrScenario extends AbstractScenario<Tool> {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    protected StorageProvider storageProvider;

    protected AbstractJobRunrScenario(DataStore dataStore, String[] args) {
        super(null, JobRunrScenarioResult::new, dataStore, args);
    }

    protected BackgroundJobServerConfiguration getBackgroundJobServerConfiguration() {
        return usingStandardBackgroundJobServerConfiguration().andPollIntervalInSeconds(5);
    }

    protected JobRunrDashboardWebServerConfiguration getDashboardWebServerConfiguration() {
        int dashboardPort = parseInt(getArg("dashboard_port", "0"));
        if (dashboardPort == 0) return null;
        return JobRunrDashboardWebServerConfiguration.usingStandardDashboardConfiguration().andPort(dashboardPort);
    }

//    private void startDataStoreAndInitializeJobRunr() {
//        dataStore.start();
//        storageProvider = dataStore.getStorageProvider(getBooleanArg("log_queries"));
//
//        if (getBooleanArg("log_storage_provider_timings")) {
//            ThreadSafeStorageProvider.setMethodStatisticsConfiguration(DETAILED);
//            LOGGER.warn("Log storage provider timings enabled: {}", ThreadSafeStorageProvider.getMethodStatisticsConfiguration());
//        }
//
//        initializeJobRunr(storageProvider);
//        LOGGER.info("Started JobRunr with BackgroundJobServer paused");
//    }

//    private void processJobs() {
//        Instant startTime = startProcessingJobs();
//        Optional<QueryAnalysisMonitor> optionalQueryAnalysisMonitor = initQueryAnalysisIfPossible(startTime);
//        Instant endTime = waitForJobsToComplete(startTime);
//
//        scenarioResult.setProcessingDuration(Duration.between(startTime, endTime));
//        optionalQueryAnalysisMonitor.ifPresent(queryAnalysisMonitor -> {
//            scenarioResult.setMethodStatistics(queryAnalysisMonitor.getMethodStatistics());
//            scenarioResult.setQueryAnalyses(queryAnalysisMonitor.getQueryAnalyses());
//        });
//        LOGGER.info("Processed {} jobs in {}", scenarioResult.getSucceededJobs(), scenarioResult.getProcessingDuration());
//    }
//
//    private Optional<QueryAnalysisMonitor> initQueryAnalysisIfPossible(Instant startTime) {
//        if (getBooleanArg("log_storage_provider_timings") && dataStore instanceof AnalysingDataStore) {
//            QueryAnalysisMonitor queryAnalysisMonitor = new QueryAnalysisMonitor((AnalysingDataStore) dataStore, startTime, getMaxScenarioDuration(), 0.1, 0.25, 0.5, 0.75, 0.9);
//            storageProvider.addJobStorageOnChangeListener(queryAnalysisMonitor);
//            return Optional.of(queryAnalysisMonitor);
//        }
//        return Optional.empty();
//    }
//
//    protected void appendToLogbook(String... extraParams) {
//        LogBookReporter.append(JobRunrDistribution.current.backgroundJobServer(), dataStore, scenarioResult, extraParams);
//        if (getBooleanArg("log_storage_provider_timings") && dataStore instanceof AnalysingDataStore) {
//            MarkdownReporter.render(JobRunrDistribution.current.backgroundJobServer(), (AnalysingDataStore) dataStore, scenarioResult);
//        } else {
//            LOGGER.error("ERROR - Could not log results to Markdown: {}", dataStore.getClass().getSimpleName());
//        }
//    }
//
//    protected Instant startProcessingJobs() {
//        Instant startTime = now();
//        JobRunrDistribution.current.backgroundJobServer().start();
//        return startTime;
//    }
//
//    protected Instant waitForJobsToComplete(Instant startTime) {
//        ScenarioMonitor scenarioMonitor = new ScenarioMonitor(scenarioResult.getCreatedJobs(), startTime, getMaxScenarioDuration());
//        storageProvider.addJobStorageOnChangeListener(scenarioMonitor);
//        Long succeededJobs = scenarioMonitor.awaitAndGetSucceededJobs();
//        scenarioResult.setSucceededJobs(succeededJobs);
//        return now();
//    }

    private void stopJobRunrAndDataStore() {
        JobRunrDistribution.current.stop();
        //dataStore.stop();
    }

    private void exitJVMIfRequested() {
        if (parseBoolean(getArg("system-exit", "false"))) {
            System.exit(0);
        }
    }

    protected void initializeJobRunr(StorageProvider storageProvider) {
        BackgroundJobServerConfiguration backgroundJobServerConfiguration = getBackgroundJobServerConfiguration();
        JobRunrDashboardWebServerConfiguration dashboardWebServerConfiguration = getDashboardWebServerConfiguration();

        storageProvider.setJobMapper(new JobMapper(new JacksonJsonMapper()));

        JobRunrDistribution.current.saveLicense(storageProvider);
        JobRunrDistribution.current.getConfiguration()
                .useStorageProvider(storageProvider)
                .useBackgroundJobServer(backgroundJobServerConfiguration, false)
                .useDashboardIf(dashboardWebServerConfiguration != null, dashboardWebServerConfiguration)
                .initialize();
    }

    protected Duration getMaxScenarioDuration() {
        return getDurationArg("max_duration", Duration.ofHours(1));
    }

    protected String getArg(String key) {
        return ArgUtils.getArg(args, key, null);
    }

    protected boolean getBooleanArg(String key) {
        return Boolean.parseBoolean(ArgUtils.getArg(args, key, "false"));
    }

    protected Duration getDurationArg(String key, Duration defaultValue) {
        return Duration.parse(ArgUtils.getArg(args, key, defaultValue.toString()));
    }

    protected String getArg(String key, String defaultValue) {
        return ArgUtils.getArg(args, key, defaultValue);
    }
}
