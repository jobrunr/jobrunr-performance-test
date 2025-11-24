package org.jobrunrpro.performance.scenario;

import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunrpro.performance.scenario.jobs.BatchJob;
import org.performance.datastore.DataStore;

import static java.lang.Integer.parseInt;
import static org.jobrunr.scheduling.JobBuilder.aBatchJob;

public class Scenario04BatchJobs extends AbstractJobRunrProScenario {

    private final int batchJobs;

    public Scenario04BatchJobs(DataStore dataStore, String[] args) {
        this(dataStore, args, 10);
    }

    public Scenario04BatchJobs(DataStore dataStore, String[] args, int batchJobs) {
        super(dataStore, args);
        this.batchJobs = batchJobs;
    }

    @Override
    public long loadJobs() {
        int totalAmountOfJobs = parseInt(getArg("amount", "0").replace("_", ""));
        if (totalAmountOfJobs < 1) return 0;

        int jobsPerBatchJob = totalAmountOfJobs / batchJobs;
        for (int i = 0; i < batchJobs; i++) {
            int finalI = i;
            String batchJobName = getBatchJobName(finalI);
            BackgroundJob.create(aBatchJob()
                    .withName(batchJobName)
                    .withLabels("my-batch-job-" + i)
                    .<BatchJob>withDetails(x -> x.batchJob(jobsPerBatchJob, finalI)));
        }
        LOGGER.info("   Created {} batch jobs which will in turn each create {} child jobs", batchJobs, jobsPerBatchJob);
        return totalAmountOfJobs;
    }

    private static String getBatchJobName(int index) {
        return "batch job " + index;
    }
}
