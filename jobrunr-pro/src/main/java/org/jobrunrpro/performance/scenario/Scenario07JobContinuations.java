package org.jobrunrpro.performance.scenario;

import org.performance.datastore.DataStore;

public class Scenario07JobContinuations extends AbstractJobRunrProScenario {

    private final int batchJobs;

    public Scenario07JobContinuations(DataStore dataStore, String[] args) {
        this(dataStore, args, 10);
    }

    public Scenario07JobContinuations(DataStore dataStore, String[] args, int batchJobs) {
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
