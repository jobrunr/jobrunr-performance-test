package org.jobrunrpro.performance.scenario;

import org.jobrunr.jobs.Job;
import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunrpro.performance.scenario.jobs.ReplaceableJob;
import org.performance.datastore.DataStore;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static java.lang.Integer.parseInt;

public class Scenario09ReplaceJobs extends AbstractJobRunrProScenario {

    private Duration initialCreationDuration;
    private Duration replacingDuration;
    private List<UUID> jobsIds;

    public Scenario09ReplaceJobs(DataStore dataStore, String[] args) {
        this(dataStore, args, 10);
    }

    public Scenario09ReplaceJobs(DataStore dataStore, String[] args, int batchJobs) {
        super(dataStore, args);
    }

    @Override
    protected long loadJobs() {
        int totalAmountOfJobs = parseInt(getArg("amount", "0").replace("_", ""));
        if (totalAmountOfJobs < 1) return 0;

        jobsIds = IntStream.range(0, totalAmountOfJobs).boxed().map(x -> Job.newUUID()).toList();

        initialCreationDuration = createOrReplaceJobs(jobsIds);
        return totalAmountOfJobs;
    }

    @Override
    protected Instant waitForJobsToComplete() throws Exception {
        replacingDuration = createOrReplaceJobs(jobsIds);
        return super.waitForJobsToComplete();
    }

    @Override
    protected long getSucceededJobsToAwait() {
        // replaced jobs can succeed twice, so only idle workers end this scenario
        return Long.MAX_VALUE;
    }

    @Override
    protected void appendToLogbook(String... extraParams) {
        super.appendToLogbook(initialCreationDuration.toString(), replacingDuration.toString());
    }

    Duration createOrReplaceJobs(List<UUID> jobsIds) {
        ReplaceableJob performanceTestJob = new ReplaceableJob();
        long sleepDuration = 350;

        AtomicInteger counter = new AtomicInteger(0);
        Instant startTime = Instant.now();
        final int amount = jobsIds.size();
        jobsIds.forEach(x -> {
            if (counter.get() % 1000 == 0) {
                LOGGER.info("Created / Replaced {} jobs in {}", counter.get(), Duration.between(startTime, Instant.now()));
            }
            BackgroundJob.enqueueOrReplace(x, () -> performanceTestJob.slowJob(counter.incrementAndGet(), amount, sleepDuration));
        });
        Instant endTime = Instant.now();

        return Duration.between(startTime, endTime);
    }
}
