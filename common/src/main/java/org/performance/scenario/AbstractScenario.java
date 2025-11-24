package org.performance.scenario;

import org.performance.datastore.DataStore;
import org.performance.tools.Tool;
import org.performance.utils.ArgUtils;
import org.performance.utils.LogBookReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.function.Function;

import static java.lang.Boolean.parseBoolean;
import static java.time.Instant.now;
import static org.performance.utils.StringUtils.camelCaseToHumanReadable;

public abstract class AbstractScenario<T extends Tool> implements Scenario {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    protected final T tool;
    protected final DataStore dataStore;
    protected final String[] args;
    protected final ScenarioResult scenarioResult;

    protected AbstractScenario(T tool, DataStore dataStore, String[] args) {
        this(tool, ScenarioResult::new, dataStore, args);
    }

    protected AbstractScenario(T tool, Function<Scenario, ScenarioResult> scenarioResultCreator, DataStore dataStore, String[] args) {
        this.tool = tool;
        this.scenarioResult = scenarioResultCreator.apply(this);
        this.dataStore = dataStore;
        this.args = args;
        logTitle();
    }

    protected abstract long loadJobs() throws Exception;

    public void run() throws Exception {
        startDataStoreAndInitializeTool();
        createJobsAndUpdateStatistics();
        processJobs();
        appendToLogbook();
        stopToolAndDataStore();
        exitJVMIfRequested();
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
        Instant startTime = startProcessingJobs();
        Instant endTime = waitForJobsToComplete();
        scenarioResult.setProcessingDuration(Duration.between(startTime, endTime));
        LOGGER.info("Processed {} jobs in {}", scenarioResult.getSucceededJobs(), scenarioResult.getProcessingDuration());
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
        ScenarioMonitor scenarioMonitor = tool.createScenarioMonitor(scenarioResult.getCreatedJobs(), getMaxScenarioDuration());
        scenarioMonitor.awaitForScenario();
        scenarioResult.setSucceededJobs(scenarioMonitor.getTotalAmountOfSucceededJobs());
        return now();
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
