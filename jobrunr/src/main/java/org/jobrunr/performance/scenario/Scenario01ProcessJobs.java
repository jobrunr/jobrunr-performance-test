package org.jobrunr.performance.scenario;

import org.jobrunr.scheduling.BackgroundJob;
import org.performance.datastore.DataStore;
import org.performance.jobs.PerformanceTestJob;
import org.performance.utils.ThroughputLogger;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;

public class Scenario01ProcessJobs extends AbstractJobRunrScenario {

    public Scenario01ProcessJobs(DataStore dataStore, String[] args) {
        super(dataStore, args);
    }

    @Override
    protected long loadJobs() {
        int totalAmountOfJobs = parseInt(getArg("amount", "0").replace("_", ""));
        if (totalAmountOfJobs < 1) return 0;

        ThroughputLogger throughputLogger = new ThroughputLogger(LOGGER, totalAmountOfJobs);
        Stream<Integer> jobStream = IntStream.range(0, totalAmountOfJobs)
                .boxed()
                .peek(throughputLogger::logThroughputAndEstimatedTimeLeft);
        BackgroundJob.<PerformanceTestJob, Integer>enqueue(jobStream, (job, index) -> job.testJob(totalAmountOfJobs, index));
        return totalAmountOfJobs;
    }
}
