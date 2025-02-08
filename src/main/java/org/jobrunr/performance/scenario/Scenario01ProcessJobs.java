package org.jobrunr.performance.scenario;

import org.jobrunr.performance.scenario.jobs.PerformanceTestJob;
import org.jobrunr.performance.storage.DataStore;
import org.jobrunr.scheduling.BackgroundJob;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
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

        Instant startTime = Instant.now();
        AtomicLong lastLogTime = new AtomicLong(System.currentTimeMillis());
        PerformanceTestJob performanceTestJob = new PerformanceTestJob();
        Stream<Integer> jobStream = IntStream.range(0, totalAmountOfJobs)
                .boxed()
                .peek(i -> {
                    long currentTime = System.currentTimeMillis();
                    long elapsedSinceLastLog = (currentTime - lastLogTime.get()) / 1000;

                    if (elapsedSinceLastLog >= 60) {
                        long totalElapsedSeconds = (currentTime - startTime.toEpochMilli()) / 1000;
                        double jobsPerSecond = totalElapsedSeconds > 0 ? (double) i / totalElapsedSeconds : 0;
                        double estimatedTotalTime = jobsPerSecond > 0 ? totalAmountOfJobs / jobsPerSecond : 0;

                        LOGGER.info("Created {} jobs in {}. Estimated time to create {} jobs: {} seconds",
                                i, Duration.ofSeconds(totalElapsedSeconds), totalAmountOfJobs, (int) estimatedTotalTime);
                        lastLogTime.set(currentTime);
                    }
                });
        BackgroundJob.enqueue(jobStream, index -> performanceTestJob.testJob(totalAmountOfJobs, index));
        return totalAmountOfJobs;
    }
}
