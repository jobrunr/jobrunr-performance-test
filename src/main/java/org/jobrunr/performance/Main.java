package org.jobrunr.performance;

import org.jobrunr.performance.scenario.Scenario;
import org.jobrunr.performance.storage.DataStore;

import static org.jobrunr.performance.utils.ArgUtils.getArg;

public class Main {

    public static void main(String[] args) throws Exception {
        DataStore dataStore = DataStore.loadDataStore(getArg(args, "datastore"));
        Scenario scenario = Scenario.loadScenario(getArg(args, "scenario"), dataStore, args);
        scenario.run();
    }
}
