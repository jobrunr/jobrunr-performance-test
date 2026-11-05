package org.performance.scenario;

import org.performance.datastore.DataStore;
import org.performance.datastore.sql.AbstractSqlDataStore;
import org.performance.datastore.sql.HikariConnectionTimings;
import org.performance.jobs.PerformanceTestJob;
import org.performance.tools.Tool;
import org.performance.utils.ArgUtils;
import org.performance.utils.LogBookReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import static java.lang.Boolean.parseBoolean;
import static java.lang.Long.parseLong;
import static java.time.Instant.now;
import static org.performance.utils.StringUtils.camelCaseToHumanReadable;

public abstract class AbstractScenario<T extends Tool> implements Scenario {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    protected final T tool;
    protected final ScenarioProfiler scenarioProfiler;
    protected final DataStore dataStore;
    protected final String[] args;
    protected final ScenarioResult scenarioResult;

    protected AbstractScenario(T tool, DataStore dataStore, String[] args) {
        this(tool, ScenarioResult::new, dataStore, args);
    }

    protected AbstractScenario(T tool, Function<Scenario, ScenarioResult> scenarioResultCreator, DataStore dataStore, String[] args) {
        this.tool = tool;
        this.args = args;
        this.scenarioProfiler = new ScenarioProfiler(getBooleanArg("enable_profiling"));
        this.scenarioResult = scenarioResultCreator.apply(this);
        this.dataStore = dataStore;
        logTitle();
    }

    protected abstract long loadJobs() throws Exception;

    public void run() throws Exception {
        scenarioProfiler.start();
        startDataStoreAndInitializeTool();
        if (isClusterWorker()) {
            scenarioResult.setAmountOfJobsCreated(tool.getAmountOfExpectedJobs(), null);
            waitUntilStartTime();
        } else {
            createJobsAndUpdateStatistics();
            if (isClusterLoader()) tool.saveAmountOfExpectedJobs(scenarioResult.getCreatedJobs());
        }
        if (!isClusterLoader()) {
            processJobs();
            if (isClusterWorker()) {
                // parsed by aws/bin/run-cluster.sh
                LOGGER.info("First job started at epoch millis: {}", PerformanceTestJob.startTime.get());
            }
            appendToLogbook();
        }
        stopToolAndDataStore();
        scenarioProfiler.stop();
        exitJVMIfRequested();
    }

    protected boolean isClusterLoader() {
        return "loader".equals(getClusterRole());
    }

    protected boolean isClusterWorker() {
        return "worker".equals(getClusterRole());
    }

    private String getClusterRole() {
        String clusterRole = getArg("cluster_role", "standalone");
        if (!List.of("standalone", "loader", "worker").contains(clusterRole)) {
            throw new IllegalArgumentException("Unknown cluster_role '" + clusterRole + "'. Valid values: standalone, loader, worker");
        }
        return clusterRole;
    }

    private void waitUntilStartTime() throws InterruptedException {
        Instant startAt = Instant.ofEpochSecond(parseLong(getArg("start_at")));
        Duration waitTime = Duration.between(now(), startAt);
        if (waitTime.isNegative()) {
            throw new IllegalStateException("Worker was initialized " + waitTime.negated() + " after start_at; increase the start delay");
        }
        LOGGER.info("Waiting {} until {} to start processing jobs", waitTime, startAt);
        Thread.sleep(waitTime.toMillis());
    }

    private void logTitle() {
        String scenario = camelCaseToHumanReadable(this);
        int toolOrTitleLength = Math.max(scenario.length(), tool.getTitleAndVersion().length());
        String scenarioWithMarkup = "======    " + scenario + " ".repeat(toolOrTitleLength - scenario.length()) + "    ======";
        String toolWithMarkup = "======    " + tool.getTitleAndVersion() + " ".repeat(toolOrTitleLength - tool.getTitleAndVersion().length()) + "    ======";
        int titleLength = toolOrTitleLength + 20;
        LOGGER.info("=".repeat(titleLength));
        LOGGER.info(scenarioWithMarkup);
        LOGGER.info(toolWithMarkup);
        LOGGER.info("=".repeat(toolWithMarkup.length()));
    }

    private void startDataStoreAndInitializeTool() throws Exception {
        dataStore.start();
        initializeTool();
        LOGGER.info("Started {} with BackgroundJobServer paused", tool.getTitleAndVersion());
    }

    protected void initializeTool() throws Exception {
        tool.initialize(dataStore, this);
    }

    private void createJobsAndUpdateStatistics() throws Exception {
        LOGGER.info("Creating jobs");
        Instant startTime = now();
        long totalAmountOfJobsCreated = loadJobs();
        Instant endTime = now();
        scenarioResult.setAmountOfJobsCreated(totalAmountOfJobsCreated, Duration.between(startTime, endTime));
        LOGGER.info("Successfully created {} jobs in {}. Updating database statistics", totalAmountOfJobsCreated, scenarioResult.getCreationDuration());
        //dataStore.updateStatistics(); // need to find a way to do this also for quartz. Skip for now
        //LOGGER.info("Successfully updated database statistics");
    }

    protected void processJobs() throws Exception {
        HikariConnectionTimings connectionTimings = getConnectionTimings();
        if (connectionTimings != null) connectionTimings.sinceLastRead();
        Instant startTime = startProcessingJobs();
        Instant endTime = waitForJobsToComplete();
        scenarioResult.setProcessingDuration(Duration.between(startTime, endTime));
        LOGGER.info("Processed {} jobs in {}", scenarioResult.getSucceededJobs(), scenarioResult.getProcessingDuration());
        if (connectionTimings != null) {
            // parsed by aws/bin/run-cluster.sh
            LOGGER.info("Connections while processing (pool of {}): {}", ((AbstractSqlDataStore) dataStore).dataSource().getMaximumPoolSize(), connectionTimings.sinceLastRead());
        }
        LOGGER.info("Average job execution time on this node: {} µs", PerformanceTestJob.executionNanos.sum() / 1000.0 / Math.max(PerformanceTestJob.counter.get(), 1));
    }

    private HikariConnectionTimings getConnectionTimings() {
        if (!(dataStore instanceof AbstractSqlDataStore sqlDataStore)) return null;
        return sqlDataStore.dataSource().getMetricsTrackerFactory() instanceof HikariConnectionTimings timings ? timings : null;
    }

    protected void appendToLogbook(String... extraParams) {
        LogBookReporter.append(tool, dataStore, scenarioResult, extraParams);
    }

    protected Instant startProcessingJobs() throws Exception {
        Instant startTime = now();
        tool.start();
        return startTime;
    }

    protected Instant waitForJobsToComplete() throws Exception {
        ScenarioMonitor scenarioMonitor = tool.createScenarioMonitor(getSucceededJobsToAwait(), getMaxScenarioDuration());
        boolean finished = scenarioMonitor.awaitForScenario();
        Instant detectedAt = now();
        scenarioResult.setSucceededJobs(scenarioMonitor.getTotalAmountOfSucceededJobs());
        if (isClusterWorker()) {
            // parsed by aws/bin/run-cluster.sh
            LOGGER.info("Jobs succeeded on this node: {}", scenarioResult.getSucceededJobs());
        }
        if (!finished) return detectedAt;
        Optional<Instant> lastJobSucceededAt = scenarioMonitor.getLastJobSucceededAt();
        if (isClusterWorker()) {
            // parsed by aws/bin/run-cluster.sh
            LOGGER.info("Last job succeeded at epoch millis: {}", lastJobSucceededAt.map(Instant::toEpochMilli).orElse(-1L));
        }
        return lastJobSucceededAt.orElse(detectedAt);
    }

    protected long getSucceededJobsToAwait() {
        return scenarioResult.getCreatedJobs();
    }

    private void stopToolAndDataStore() throws Exception {
        tool.stop();
        //dataStore.stop();
    }

    private void exitJVMIfRequested() {
        if (parseBoolean(getArg("system_exit", "false"))) {
            System.exit(0);
        }
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
