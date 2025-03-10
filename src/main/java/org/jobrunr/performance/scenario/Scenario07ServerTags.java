package org.jobrunr.performance.scenario;

import org.jobrunr.performance.storage.DataStore;

public class Scenario07ServerTags extends AbstractJobRunrProScenario {

    private final int batchJobs;

    public Scenario07ServerTags(DataStore dataStore, String[] args) {
        this(dataStore, args, 10);
    }

    public Scenario07ServerTags(DataStore dataStore, String[] args, int batchJobs) {
        super(dataStore, args);
        this.batchJobs = batchJobs;
    }

    @Override
    protected long loadJobs() {
        throw new RuntimeException("Not implemented yet");
    }

    private static String getBatchJobName(int index) {
        return "batch job " + index;
    }
}
