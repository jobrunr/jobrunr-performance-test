package org.jobrunrpro.performance.scenario;

import org.jobrunr.dashboard.JobRunrDashboardWebServerConfiguration;
import org.jobrunr.jobs.queues.Queues;
import org.jobrunr.server.BackgroundJobServerConfiguration;
import org.jobrunr.storage.JobRunrMetadata;
import org.jobrunrpro.performance.JobRunrProTool;
import org.performance.datastore.DataStore;
import org.performance.scenario.AbstractScenario;
import org.performance.scenario.ScenarioResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Integer.parseInt;
import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;

public abstract class AbstractJobRunrProScenario extends AbstractScenario<JobRunrProTool> {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    protected AbstractJobRunrProScenario(DataStore dataStore, String[] args) {
        super(new JobRunrProTool(), ScenarioResult::new, dataStore, args);
    }

    public BackgroundJobServerConfiguration getBackgroundJobServerConfiguration() {
        return usingStandardBackgroundJobServerConfiguration().andPollIntervalInSeconds(5);
    }

    public JobRunrDashboardWebServerConfiguration getDashboardWebServerConfiguration() {
        int dashboardPort = parseInt(getArg("dashboard_port", "0"));
        if (dashboardPort == 0) return null;
        return JobRunrDashboardWebServerConfiguration.usingStandardDashboardConfiguration().andPort(dashboardPort);
    }

    public Queues getQueues() {
        return new Queues(Queues.DEFAULT_QUEUE, Queues.DEFAULT_QUEUE);
    }

    public JobRunrMetadata[] getRateLimiterConfigurationsAsMetadata() {
        return new JobRunrMetadata[]{};
    }
}
