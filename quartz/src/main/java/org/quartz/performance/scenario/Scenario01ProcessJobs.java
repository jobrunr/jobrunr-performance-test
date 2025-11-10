package org.quartz.performance.scenario;

import org.performance.datastore.DataStore;
import org.performance.scenario.AbstractScenario;
import org.performance.utils.ThroughputLogger;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.SchedulerException;
import org.quartz.performance.QuartzTool;
import org.quartz.performance.scenario.jobs.QuartzPerformanceTestJob;

import java.util.stream.IntStream;

import static java.lang.Integer.parseInt;

public class Scenario01ProcessJobs extends AbstractScenario<QuartzTool> {

    public Scenario01ProcessJobs(DataStore dataStore, String[] args) {
        super(new QuartzTool(), dataStore, args);
    }

    @Override
    protected long loadJobs() throws Exception {
        int totalAmountOfJobs = parseInt(getArg("amount", "0").replace("_", ""));
        if (totalAmountOfJobs < 1) return 0;

        JobKey jobKey = createOneJob();

        ThroughputLogger throughputLogger = new ThroughputLogger(LOGGER, totalAmountOfJobs);
        IntStream.range(0, totalAmountOfJobs)
                .boxed()
                .peek(throughputLogger::logThroughputAndEstimatedTimeLeft)
                .forEach(i -> createTrigger(jobKey, i, totalAmountOfJobs));
        return totalAmountOfJobs;
    }

    private JobKey createOneJob() throws SchedulerException {
        JobKey jobKey = new JobKey("job_with_multiple_tiggers", "group");
        JobDetail job = JobBuilder.newJob(QuartzPerformanceTestJob.class)
                .withIdentity(jobKey)
                .storeDurably()
                .build();
        tool.getScheduler().addJob(job, false);
        return jobKey;
    }

    protected void createTrigger(JobKey jobKey, Integer currentJob, Integer totalAmountOfJobs) {
        try {
            JobDataMap jobDataMap = new JobDataMap();
            jobDataMap.put("jobNumber", currentJob);
            jobDataMap.put("totalAmountOfJobs", totalAmountOfJobs);
            tool.getScheduler().triggerJob(jobKey, jobDataMap);
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to schedule job " + currentJob, e);
        }
    }
}
