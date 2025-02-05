package org.jobrunr.performance.scenario;

import org.jobrunr.performance.scenario.jobs.PerformanceTestJob;
import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.storage.DataStore;

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

        PerformanceTestJob performanceTestJob = new PerformanceTestJob();
        Stream<Integer> jobStream = IntStream.range(0, totalAmountOfJobs).boxed();
        BackgroundJob.enqueue(jobStream, index -> performanceTestJob.testJob(totalAmountOfJobs, index));
        return totalAmountOfJobs;
    }
}
