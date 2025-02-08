package org.jobrunr.performance.scenario;

import org.jobrunr.dashboard.JobRunrDashboardWebServerConfiguration;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.performance.JobRunrDistribution;
import org.jobrunr.performance.storage.DataStore;
import org.jobrunr.performance.utils.ArgUtils;
import org.jobrunr.performance.utils.LogBook;
import org.jobrunr.performance.utils.StringUtils;
import org.jobrunr.server.BackgroundJobServerConfiguration;
import org.jobrunr.storage.JobStats;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.listeners.JobStatsChangeListener;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

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
        storageProvider = dataStore.getStorageProvider();
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
        Instant endTime = waitForJobsToComplete();
        scenarioResult.setProcessingDuration(Duration.between(startTime, endTime));
        LOGGER.info("Processed {} jobs in {}", scenarioResult.getCreatedJobs(), scenarioResult.getProcessingDuration());
    }

    private void appendToLogbook() {
        LogBook.append(JobRunrDistribution.current.backgroundJobServer(), scenarioResult);
    }

    private Instant startProcessingJobs() {
        Instant startTime = Instant.now();
        JobRunrDistribution.current.backgroundJobServer().start();
        return startTime;
    }

    private Instant waitForJobsToComplete() {
        AllJobsSucceededLatch latch = new AllJobsSucceededLatch(scenarioResult.getCreatedJobs());
        storageProvider.addJobStorageOnChangeListener(latch);
        Long succeededJobs = latch.awaitForAllJobsToBeProcessed();
        scenarioResult.setSucceededJobs(succeededJobs);
        return dataStore.getUpdatedAtOfLastSucceededJob();
    }

    private void stopJobRunrAndDataStore() {
        JobRunrDistribution.current.stop();
        dataStore.stop();
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

    protected String getArg(String key) {
        return ArgUtils.getArg(args, key, null);
    }

    protected String getArg(String key, String defaultValue) {
        return ArgUtils.getArg(args, key, defaultValue);
    }

    private static class AllJobsSucceededLatch implements JobStatsChangeListener {

        private final long totalAmountOfJobs;
        private final CountDownLatch countDownLatch;
        private JobStats jobsStats;
        private int duplicateJobStatsCounter;

        public AllJobsSucceededLatch(long totalAmountOfJobs) {
            this.totalAmountOfJobs = totalAmountOfJobs;
            this.countDownLatch = new CountDownLatch(1);
        }

        @Override
        public void onChange(JobStats jobStats) {
            if (jobStats.getSucceeded() >= totalAmountOfJobs) {
                countDownLatch.countDown();
            } else if (this.jobsStats != null
                    && Objects.equals(this.jobsStats.getSucceeded(), jobStats.getSucceeded())
                    && Objects.equals(this.jobsStats.getEnqueued(), jobStats.getEnqueued())) {
                // in case of failure
                if (duplicateJobStatsCounter++ > 3) {
                    countDownLatch.countDown();
                }
            }
            this.jobsStats = jobStats;
        }

        public Long awaitForAllJobsToBeProcessed() {
            try {
                countDownLatch.await();
                return jobsStats.getSucceeded();
            } catch (InterruptedException e) {
                throw new RuntimeException("Exception waiting for " + totalAmountOfJobs + " jobs to succeed", e);
            }
        }
    }
}
