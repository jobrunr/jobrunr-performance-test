package org.quartz.performance.scenario.jobs;

import org.performance.jobs.PerformanceTestJob;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

public class QuartzPerformanceTestJob extends PerformanceTestJob implements Job {

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        int jobNumber = context.getMergedJobDataMap().getInt("jobNumber");
        int totalAmountOfJobs = context.getMergedJobDataMap().getInt("totalAmountOfJobs");
        testJob(totalAmountOfJobs, jobNumber);
    }
}
