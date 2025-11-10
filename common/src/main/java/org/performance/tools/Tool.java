package org.performance.tools;

import org.performance.datastore.DataStore;
import org.performance.scenario.Scenario;
import org.performance.scenario.ScenarioMonitor;

import java.time.Duration;

public interface Tool {

    String getName();

    String getVersion();

    default String getTitleAndVersion() {
        return getName() + " (" + getVersion() + ")";
    }

    <T extends Scenario> void initialize(DataStore dataStore, T scenario) throws Exception;

    void start() throws Exception;

    ScenarioMonitor createScenarioMonitor(long createdJobs, Duration maxScenarioDuration) throws Exception;

    void stop() throws Exception;

}
