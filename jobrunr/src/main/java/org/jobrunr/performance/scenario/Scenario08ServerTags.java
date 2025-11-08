package org.jobrunr.performance.scenario;

import org.performance.datastore.DataStore;

public class Scenario08ServerTags extends AbstractJobRunrProScenario {

    private final int batchJobs;

    public Scenario08ServerTags(DataStore dataStore, String[] args) {
        this(dataStore, args, 10);
    }

    public Scenario08ServerTags(DataStore dataStore, String[] args, int batchJobs) {
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
