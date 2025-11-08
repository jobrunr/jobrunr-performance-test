package org.quartz.performance;

import org.performance.datastore.sql.PostgresDataStore;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;

import java.time.Instant;

import static org.quartz.JobBuilder.newJob;

public class Bla {


    public static void main(String[] args) throws Exception {
        Scheduler scheduler = startUsingPostgres();

        // create a JobKey so we can trigger it instantly
        JobKey jobKey = new JobKey("job1", "group1");

        System.out.println("Test Ronald: " + jobKey);

        // define the job and tie it to our HelloJob class
        JobDetail jobDetail = newJob(HelloJob.class)
                .withIdentity(jobKey)
                .storeDurably() // otherwise it cannot be triggered immediately
                .build();

        // store the job in the job store
        scheduler.addJob(jobDetail, true);

        // create the JobDataMap
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put("framework", "Quartz");
        jobDataMap.put("createdAt", Instant.now());

        // trigger the job using the JobKey and the JobDataMap
        scheduler.triggerJob(jobKey, jobDataMap);

        // and start it off
        scheduler.start();

        // keep the main thread running
        Thread.currentThread().join();
        scheduler.shutdown();
    }

    private static Scheduler startUsingPostgres() throws Exception {
        PostgresDataStore dataStore = new PostgresDataStore();
        dataStore.start();


        QuartzTool tool = new QuartzTool();
        tool.initialize(dataStore);

        // Grab the Scheduler instance from the Factory
        return tool.getScheduler();
    }

    private static Scheduler startUsingDefaultScheduler() throws Exception {
        // Grab the Scheduler instance from the Factory
        return StdSchedulerFactory.getDefaultScheduler();
    }

    public static class HelloJob implements Job {
        @Override
        public void execute(JobExecutionContext jobExecutionContext) throws JobExecutionException {
            // get the variables from the JobDataMap
            String framework = jobExecutionContext.getMergedJobDataMap().getString("framework");
            Instant createdAt = (Instant) jobExecutionContext.getMergedJobDataMap().get("createdAt");

            // run the actual business code
            System.out.println(framework + " says Hello at " + createdAt);
        }
    }
}
