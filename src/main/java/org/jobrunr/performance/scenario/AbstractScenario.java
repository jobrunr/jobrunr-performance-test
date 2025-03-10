package org.jobrunr.performance.scenario;

import org.jobrunr.dashboard.JobRunrDashboardWebServerConfiguration;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.performance.JobRunrDistribution;
import org.jobrunr.performance.scenario.monitor.QueryAnalysisMonitor;
import org.jobrunr.performance.scenario.monitor.ScenarioMonitor;
import org.jobrunr.performance.storage.AnalysingDataStore;
import org.jobrunr.performance.storage.DataStore;
import org.jobrunr.performance.utils.ArgUtils;
import org.jobrunr.performance.utils.LogBookReporter;
import org.jobrunr.performance.utils.MarkdownReporter;
import org.jobrunr.performance.utils.StringUtils;
import org.jobrunr.server.BackgroundJobServerConfiguration;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.ThreadSafeStorageProvider;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Integer.parseInt;
import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;

public abstract class AbstractScenario implements Scenario {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private final DataStore dataStore;
    protected final String[] args;
    protected final ScenarioResult scenarioResult;
    private StorageProvider storageProvider;

    protected AbstractScenario(DataStore dataStore, String[] args) {
        this.scenarioResult = new ScenarioResult(this);
        this.dataStore = dataStore;
        this.args = args;
        logTitle();
    }

    protected BackgroundJobServerConfiguration getBackgroundJobServerConfiguration() {
        return usingStandardBackgroundJobServerConfiguration().andPollIntervalInSeconds(5);
    }

    protected JobRunrDashboardWebServerConfiguration getDashboardWebServerConfiguration() {
        int dashboardPort = parseInt(getArg("dashboard_port", "0"));
        if (dashboardPort == 0) return null;
        return JobRunrDashboardWebServerConfiguration.usingStandardDashboardConfiguration().andPort(dashboardPort);
    }

    protected abstract long loadJobs();

    public void run() {
        startDataStoreAndInitializeJobRunr();
        createJobsAndUpdateStatistics();
        processJobs();
        appendToLogbook();
        stopJobRunrAndDataStore();
        exitJVMIfRequested();
    }

    private void logTitle() {
        String scenario = StringUtils.camelCaseToHumanReadable(this);
        String distributionWithVersion = JobRunrDistribution.current.getTitle();
        String scenarioWithMarkup = "======    " + scenario + "    =======";
        String distributionWithMarkup = "======    " + distributionWithVersion + "    =======";
        LOGGER.info("=".repeat(distributionWithMarkup.length()));
        LOGGER.info(scenarioWithMarkup);
        LOGGER.info(distributionWithMarkup);
        LOGGER.info("=".repeat(distributionWithMarkup.length()));
    }

    private void startDataStoreAndInitializeJobRunr() {
        dataStore.start();
        storageProvider = dataStore.getStorageProvider(getBooleanArg("log_queries"));

        if (getBooleanArg("log_storage_provider_timings")) {
            ThreadSafeStorageProvider.enableMethodTimings();
        }

        initializeJobRunr(storageProvider);
        LOGGER.info("Started JobRunr with BackgroundJobServer paused");
    }

    private void createJobsAndUpdateStatistics() {
        LOGGER.info("Creating jobs");
        Instant startTime = Instant.now();
        long totalAmountOfJobsCreated = loadJobs();
        Instant endTime = Instant.now();
        scenarioResult.setAmountOfJobsCreated(totalAmountOfJobsCreated, Duration.between(startTime, endTime));
        LOGGER.info("Successfully created {} jobs in {}. Updating database statistics", totalAmountOfJobsCreated, scenarioResult.getCreationDuration());
        dataStore.updateStatistics();
        LOGGER.info("Successfully updated database statistics");
    }

    private void processJobs() {
        Instant startTime = startProcessingJobs();
        Optional<QueryAnalysisMonitor> optionalQueryAnalysisMonitor = initQueryAnalysisIfPossible(startTime);
        Instant endTime = waitForJobsToComplete(startTime);

        scenarioResult.setProcessingDuration(Duration.between(startTime, endTime));
        optionalQueryAnalysisMonitor.ifPresent(queryAnalysisMonitor -> {
            scenarioResult.setMethodSummaryStatistics(queryAnalysisMonitor.getMethodSummaryStatistics());
            scenarioResult.setQueryAnalyses(queryAnalysisMonitor.getQueryAnalyses());
        });
        LOGGER.info("Processed {} jobs in {}", scenarioResult.getSucceededJobs(), scenarioResult.getProcessingDuration());
    }

    private Optional<QueryAnalysisMonitor> initQueryAnalysisIfPossible(Instant startTime) {
        if (ThreadSafeStorageProvider.isMethodTimingEnabled() && dataStore instanceof AnalysingDataStore) {
            QueryAnalysisMonitor queryAnalysisMonitor = new QueryAnalysisMonitor((AnalysingDataStore) dataStore, startTime, getMaxScenarioDuration(), 0.1, 0.25, 0.5, 0.75, 0.9);
            storageProvider.addJobStorageOnChangeListener(queryAnalysisMonitor);
            return Optional.of(queryAnalysisMonitor);
        }
        return Optional.empty();
    }

    private void appendToLogbook() {
        LogBookReporter.append(JobRunrDistribution.current.backgroundJobServer(), scenarioResult);
        if (ThreadSafeStorageProvider.isMethodTimingEnabled() && dataStore instanceof AnalysingDataStore) {
            MarkdownReporter.render(JobRunrDistribution.current.backgroundJobServer(), (AnalysingDataStore) dataStore, scenarioResult);
        } else {
            LOGGER.error("ERROR - not an instance of TimedStorageProvider {}", storageProvider.getClass().getSimpleName());
        }
    }

    private Instant startProcessingJobs() {
        Instant startTime = Instant.now();
        JobRunrDistribution.current.backgroundJobServer().start();
        return startTime;
    }

    private Instant waitForJobsToComplete(Instant startTime) {
        ScenarioMonitor scenarioMonitor = new ScenarioMonitor(scenarioResult.getCreatedJobs(), startTime, getMaxScenarioDuration());
        storageProvider.addJobStorageOnChangeListener(scenarioMonitor);
        Long succeededJobs = scenarioMonitor.awaitAndGetSucceededJobs();
        scenarioResult.setSucceededJobs(succeededJobs);
        return dataStore.getUpdatedAtOfLastSucceededJob();
    }

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

    private Duration getMaxScenarioDuration() {
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
