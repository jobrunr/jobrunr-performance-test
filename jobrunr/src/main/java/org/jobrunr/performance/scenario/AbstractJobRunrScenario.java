package org.jobrunr.performance.scenario;

import org.jobrunr.dashboard.JobRunrDashboardWebServerConfiguration;
import org.jobrunr.performance.JobRunrTool;
import org.jobrunr.server.BackgroundJobServerConfiguration;
import org.performance.datastore.DataStore;
import org.performance.scenario.AbstractScenario;
import org.performance.scenario.ScenarioResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.lang.Integer.parseInt;
import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;

public abstract class AbstractJobRunrScenario extends AbstractScenario<JobRunrTool> {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    protected AbstractJobRunrScenario(DataStore dataStore, String[] args) {
        super(new JobRunrTool(), ScenarioResult::new, dataStore, args);
    }

    public BackgroundJobServerConfiguration getBackgroundJobServerConfiguration() {
        return usingStandardBackgroundJobServerConfiguration().andPollIntervalInSeconds(5);
    }

    public JobRunrDashboardWebServerConfiguration getDashboardWebServerConfiguration() {
        int dashboardPort = parseInt(getArg("dashboard_port", "0"));
        if (dashboardPort == 0) return null;
        return JobRunrDashboardWebServerConfiguration.usingStandardDashboardConfiguration().andPort(dashboardPort);
    }
}
