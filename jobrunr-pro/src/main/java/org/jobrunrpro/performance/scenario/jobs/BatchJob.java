package org.jobrunrpro.performance.scenario.jobs;

import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.scheduling.JobBuilder;
import org.performance.jobs.PerformanceTestJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.jobrunr.scheduling.JobBuilder.aJob;

public class BatchJob extends PerformanceTestJob {

    private static final Logger LOGGER = LoggerFactory.getLogger(BatchJob.class);

    public void batchJob(int totalAmountOfChildJobs, int batchJob) {
        Stream<JobBuilder> jobBuilderStream = IntStream.range(0, totalAmountOfChildJobs)
                .boxed().map(i -> aJob()
                        .withName("child job " + i + " for batch job " + batchJob)
                        .<PerformanceTestJob>withDetails(x -> x.testJob(totalAmountOfChildJobs, i)));
        BackgroundJob.create(jobBuilderStream);
        LOGGER.info("Batch job {} finished", batchJob);
    }
}
