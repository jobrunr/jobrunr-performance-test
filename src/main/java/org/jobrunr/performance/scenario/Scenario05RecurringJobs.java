package org.jobrunr.performance.scenario;

import org.jobrunr.performance.storage.DataStore;

public class Scenario05RecurringJobs extends AbstractJobRunrProScenario {

    private final int batchJobs;

    public Scenario05RecurringJobs(DataStore dataStore, String[] args) {
        this(dataStore, args, 10);
    }

    public Scenario05RecurringJobs(DataStore dataStore, String[] args, int batchJobs) {
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
