package org.jobrunr.performance;

import org.jobrunr.performance.scenario.Scenario;
import org.jobrunr.storage.DataStore;

import static util.ArgUtils.getArg;

public class Main {

    public static void main(String[] args) {
        DataStore dataStore = DataStore.loadDataStore(getArg(args, "datastore"));
        Scenario scenario = Scenario.loadScenario(getArg(args, "scenario"), dataStore, args);
        scenario.run();
    }
}
