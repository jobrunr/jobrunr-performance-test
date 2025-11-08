package org.performance.tools;

import org.performance.datastore.DataStore;
import org.performance.scenario.ScenarioMonitor;

import java.time.Duration;

public interface Tool {

    String getName();

    String getVersion();

    default String getTitleAndVersion() {
        return getName() + " (" + getVersion() + ")";
    }

    void initialize(DataStore dataStore) throws Exception;

    void start() throws Exception;

    ScenarioMonitor createScenarioMonitor(long createdJobs, Duration maxScenarioDuration) throws Exception;

    void stop() throws Exception;

}
