package org.jobrunr.performance.scenario;

import org.jobrunr.performance.scenario.jobs.PerformanceTestJob;
import org.jobrunr.performance.storage.DataStore;
import org.jobrunr.scheduling.BackgroundJob;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;

public class Scenario01ProcessJobs extends AbstractScenario {

    public Scenario01ProcessJobs(DataStore dataStore, String[] args) {
        super(dataStore, args);
    }

    @Override
    protected long loadJobs() {
        int totalAmountOfJobs = parseInt(getArg("amount", "0").replace("_", ""));
        if (totalAmountOfJobs < 1) return 0;

        int batchSize = 5000;
        int amountOfBatches = totalAmountOfJobs / batchSize;
        for (int i = 0; i < amountOfBatches; i++) {
            int finalI = i;
            PerformanceTestJob performanceTestJob = new PerformanceTestJob();
            Stream<Integer> jobStream = IntStream.range(0, batchSize).boxed();
            BackgroundJob.enqueue(jobStream, index -> {
                final int jobIndex = (finalI * batchSize) + index;
                performanceTestJob.testJob(totalAmountOfJobs, jobIndex);
            });
            LOGGER.info("   Created {} jobs", (finalI + 1) * batchSize);
        }
        return totalAmountOfJobs;
    }
}
