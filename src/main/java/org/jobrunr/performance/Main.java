package org.jobrunr.performance;

import org.jobrunr.performance.scenario.Scenario;
import org.jobrunr.performance.storage.DataStore;
import org.jobrunr.performance.storage.DataStore.DataStoreType;

import static org.jobrunr.performance.utils.ArgUtils.getArg;

public class Main {

    public static void main(String[] args) {
        String datastore = getArg(args, "datastore");
        String scenario = getArg(args, "scenario");

        if ("all".equals(datastore)) {
            for (DataStoreType dataStoreType : DataStoreType.values()) {
                runScenario(dataStoreType, scenario, args);
            }
        } else {
            runScenario(DataStoreType.valueOf(datastore), scenario, args);
        }
    }

    public static void runScenario(DataStoreType dataStoreType, String scenarioName, String[] args) {
        DataStore dataStore = DataStore.loadDataStore(dataStoreType);
        Scenario scenario = Scenario.loadScenario(scenarioName, dataStore, args);
        scenario.run();
    }
}
