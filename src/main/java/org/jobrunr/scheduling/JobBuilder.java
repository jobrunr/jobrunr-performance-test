package org.jobrunr.scheduling;

import org.jobrunr.jobs.BatchJob;
import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.JobDetails;
import org.jobrunr.jobs.details.JobDetailsGenerator;
import org.jobrunr.jobs.filters.DeleteFilter;
import org.jobrunr.jobs.lambdas.IocJobLambda;
import org.jobrunr.jobs.lambdas.JobLambda;
import org.jobrunr.jobs.lambdas.JobRequest;
import org.jobrunr.jobs.lambdas.JobRunrJob;
import org.jobrunr.jobs.queues.Queues;
import org.jobrunr.jobs.states.AwaitingState;
import org.jobrunr.jobs.states.EnqueuedState;
import org.jobrunr.jobs.states.InitialState;
import org.jobrunr.jobs.states.JobState;
import org.jobrunr.jobs.states.ScheduledState;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.utils.JobUtils;
import org.mockito.internal.util.reflection.Whitebox;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static java.util.Arrays.asList;

/**
 * This class is for better JobBuilding in v7.x but it will only be part of v8
 */
public class JobBuilder {

    private final boolean isBatchJob;
    private UUID jobId;
    private String jobName;
    private JobState state;
    private Integer retries;
    private List<String> labels;
    private JobRunrJob jobLambda;
    private JobRequest jobRequest;
    private String queue;
    private String serverTag;
    private String mutex;
    private String rateLimiter;
    private Duration processTimeOut;
    private String deleteOnSuccess;
    private String deleteOnFailure;

    private JobBuilder(boolean isBatchJob) {
        // why: builder pattern
        this.isBatchJob = isBatchJob;
        this.jobId = Job.newUUID();
        this.state = new EnqueuedState();
    }

    /**
     * Creates a new {@link JobBuilder} instance to create a {@link Job} using a builder pattern.
     *
     * @return a new {@link JobBuilder} instance
     */
    public static JobBuilder aJob() {
        return new JobBuilder(false);
    }

    /**
     * Creates a new {@link JobBuilder} instance to create a {@link BatchJob} using a builder pattern.
     *
     * @return a new {@link JobBuilder} instance
     */
    public static JobBuilder aBatchJob() {
        return new JobBuilder(true);
    }

    /**
     * Allows to set the id of the job.
     * <p>
     * If the job will be created by {@link AbstractJobScheduler#create(JobBuilder)} and a job with that id already exists, JobRunr will not save it again.
     * If the job will be created by {@link AbstractJobScheduler#createOrReplace(JobBuilder)} and a job with that id already exists, JobRunr will replace the existing job.
     *
     * @param jobId the id of the job
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder withId(UUID jobId) {
        this.jobId = jobId;
        return this;
    }

    /**
     * Allows to set the name of the job for the dashboard.
     *
     * @param jobName the name of the job for the dashboard
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder withName(String jobName) {
        this.jobName = jobName;
        return this;
    }

    /**
     * Allows to specify the duration after which the job should be enqueued.
     * Cannot be combined with {@link #scheduleAt(Instant)} or {@link #runAfterSuccessOf(JobProId)}
     *
     * @param duration the duration after which the job should be enqueued
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder scheduleIn(Duration duration) {
        return setInitialState(new ScheduledState(Instant.now().plus(duration)), "scheduleAt or runAfter has already been provided. Only one of these options is allowed.");
    }

    /**
     * Allows to specify the instant on which the job will be enqueued.
     * Cannot be combined with {@link #scheduleIn(Duration)}} or {@link #runAfterSuccessOf(JobProId)}
     *
     * @param scheduleAt the instant on which the job will be enqueued
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder scheduleAt(Instant scheduleAt) {
        return setInitialState(new ScheduledState(scheduleAt), "scheduleIn or runAfter has already been provided. Only one of these options is allowed.");
    }

    /**
     * Allows to specify that this job should run after the given job succeeded.
     * Cannot be combined with {@link #scheduleIn(Duration)}} or {@link #scheduleAt(Instant)}}
     *
     * @param jobId the id of the job that first must succeed before this job will run.
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder runAfterSuccessOf(JobProId jobId) {
        runAfterSuccessOf(jobId.asUUID());
        return this;
    }

    /**
     * Allows to specify that this job should run after the given job succeeded and the given durationToWait.
     * Cannot be combined with {@link #scheduleIn(Duration)}} or {@link #scheduleAt(Instant)}}
     *
     * @param jobId          the id of the job that first must succeed before this job will run.
     * @param durationToWait the duration to wait before the job will be enqueued after the other job succeeded
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder runAfterSuccessOf(JobProId jobId, Duration durationToWait) {
        runAfterSuccessOf(jobId.asUUID(), durationToWait);
        return this;
    }

    /**
     * Allows to specify that this job should run after the given job succeeded.
     * Cannot be combined with {@link #scheduleIn(Duration)}} or {@link #scheduleAt(Instant)}}
     *
     * @param jobId the id of the job that first must succeed before this job will run.
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder runAfterSuccessOf(UUID jobId) {
        runAfterSuccessOf(jobId, null);
        return this;
    }

    /**
     * Allows to specify that this job should run after the given job succeeded.
     * Cannot be combined with {@link #scheduleIn(Duration)}} or {@link #scheduleAt(Instant)}}
     *
     * @param jobId          the id of the job that first must succeed before this job will run.
     * @param durationToWait the duration to wait before the job will be enqueued after the other job succeeded
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder runAfterSuccessOf(UUID jobId, Duration durationToWait) {
        return setInitialState(new AwaitingState(jobId, durationToWait), "scheduleIn or scheduleAt has already been provided. Only one of these options is allowed.");
    }

    /**
     * Allows to specify that this job should run after the given job failed.
     * Cannot be combined with {@link #scheduleIn(Duration)}} or {@link #scheduleAt(Instant)}}
     *
     * @param jobId the id of the job that first must fail before this job will run.
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder runAfterFailureOf(JobProId jobId) {
        runAfterFailureOf(jobId.asUUID());
        return this;
    }

    /**
     * Allows to specify that this job should run after the given job failed and the given durationToWait.
     * Cannot be combined with {@link #scheduleIn(Duration)}} or {@link #scheduleAt(Instant)}}
     *
     * @param jobId          the id of the job that first must fail before this job will run.
     * @param durationToWait the duration to wait before the job will be enqueued after the other job failed
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder runAfterFailureOf(JobProId jobId, Duration durationToWait) {
        runAfterFailureOf(jobId.asUUID(), durationToWait);
        return this;
    }

    /**
     * Allows to specify that this job should run after the given job failed.
     * Cannot be combined with {@link #scheduleIn(Duration)}} or {@link #scheduleAt(Instant)}}
     *
     * @param jobId the id of the job that first must fail before this job will run.
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder runAfterFailureOf(UUID jobId) {
        runAfterFailureOf(jobId, null);
        return this;
    }

    /**
     * Allows to specify that this job should run after the given job failed.
     * Cannot be combined with {@link #scheduleIn(Duration)}} or {@link #scheduleAt(Instant)}}
     *
     * @param jobId          the id of the job that first must fail before this job will run.
     * @param durationToWait the duration to wait before the job will be enqueued after the other job failed
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder runAfterFailureOf(UUID jobId, Duration durationToWait) {
        return setInitialState(new AwaitingState(jobId, StateName.FAILED, durationToWait), "scheduleIn or scheduleAt has already been provided. Only one of these options is allowed.");
    }

    /**
     * Allows to specify the amount of retries for a job when it fails
     *
     * @param amountOfRetries the amount of retries that JobRunr will perform in case the job fails
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder withAmountOfRetries(int amountOfRetries) {
        this.retries = amountOfRetries;
        return this;
    }

    /**
     * Allows to provide a set of labels to be shown in the dashboard.
     * A maximum of 3 labels can be provided per job. Each label has a max length of 45 characters.
     *
     * @param labels an array of labels to be added to the recurring job
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder withLabels(String... labels) {
        return withLabels(asList(labels));
    }

    /**
     * Allows to provide a set of labels to be shown in the dashboard.
     * A maximum of 3 labels can be provided per job. Each label has a max length of 45 characters.
     *
     * @param labels a list of labels to be added to the recurring job
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder withLabels(List<String> labels) {
        this.labels = labels;
        return this;
    }

    /**
     * Allows to provide the job details by means of Java 8 lambda.
     *
     * @param jobLambda the lambda which defines the job
     * @return the same builder instance that can be given to the {@link JobScheduler#create(JobBuilder)} method
     */
    public JobBuilder withDetails(JobLambda jobLambda) {
        if (this.jobRequest != null) {
            throw new IllegalArgumentException("withJobRequest() is already called, only 1 of [withDetails(), withJobRequest()] should be called.");
        }
        this.jobLambda = jobLambda;
        return this;
    }

    /**
     * Allows to provide the job details by means of Java 8 lambda. The IoC container will be used to resolve an actual instance of the requested service.
     *
     * @param jobLambda the lambda which defines the job
     * @return the same builder instance that can be given to the {@link JobScheduler#create(JobBuilder)} method
     */
    public <S> JobBuilder withDetails(IocJobLambda<S> jobLambda) {
        if (this.jobRequest != null) {
            throw new IllegalArgumentException("withJobRequest() is already called, only 1 of [withDetails(), withJobRequest()] should be called.");
        }
        this.jobLambda = jobLambda;
        return this;
    }

    /**
     * Allows to provide the job details by means of {@link JobRequest}.
     *
     * @param jobRequest the jobRequest which defines the job.
     * @return the same builder instance that can be given to the {@link JobRequestScheduler#create(JobBuilder)} method
     */
    public JobBuilder withJobRequest(JobRequest jobRequest) {
        if (this.jobLambda != null) {
            throw new IllegalArgumentException("withJobLambda() is already called, only 1 of [withDetails(), withJobRequest()] should be called.");
        }
        this.jobRequest = jobRequest;
        return this;
    }

    /**
     * Allows to specify the server tag of the job.
     *
     * @param serverTag the tag of the server this job should run on.
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder withServerTag(String serverTag) {
        this.serverTag = serverTag;
        return this;
    }

    /**
     * Allows to specify the mutex of the job to limit concurrent executions for the specified mutex. The mutex cannot be used together with the {@link JobBuilder#withRateLimiter(String)}.
     *
     * @param mutex the mutex of the job
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder withMutex(String mutex) {
        this.mutex = mutex;
        return this;
    }

    /**
     * Allows to specify the rate limiter for the job to limit the amount of concurrent executions for the job. The rate limiter cannot be used together with the {@link JobBuilder#withMutex(String)} (String)}.
     *
     * @param rateLimiter the name of the rate limiter to limit the amount of concurrent executions.
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder withRateLimiter(String rateLimiter) {
        this.rateLimiter = rateLimiter;
        return this;
    }

    /**
     * Allows to specify the queue where this job should be executed.
     *
     * @param queue the queue of the job
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder withQueue(String queue) {
        this.queue = queue;
        return this;
    }

    /**
     * Allows to specify the queue where this job should be executed.
     *
     * @param queue the queue of the job
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder withQueue(Enum queue) {
        this.queue = queue.name();
        return this;
    }

    /**
     * Allows to specify the maximum process duration after which the job will be interrupted and move to the FAILED state. This time-out duration represents the time the Job is in the PROCESSING state.
     *
     * @param processTimeOut duration after which the job that is still in PROCESSING state will be interrupted and transition to the FAILED state
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder withProcessTimeOut(Duration processTimeOut) {
        this.processTimeOut = processTimeOut;
        return this;
    }

    /**
     * Allows to specify the duration after which to delete succeeded jobs in the following format:
     * <code>duration1((!)duration2)</code>
     * where
     * - duration 1 is the duration after which the succeeded job will move to the DELETED state
     * - duration 2 is the duration after which the job in the DELETED state will be permanently deleted.
     */
    public JobBuilder withDeleteOnSuccess(String deleteOnSuccess) {
        this.deleteOnSuccess = deleteOnSuccess;
        return this;
    }

    /**
     * Allows to specify the duration after which the successful job should go to the DELETED state
     *
     * @param deleteAfter duration after which the job should move from SUCCEEDED to DELETED.
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder withDeleteOnSuccess(Duration deleteAfter) {
        this.deleteOnSuccess = DeleteFilter.toParseableString(deleteAfter, null);
        return this;
    }

    /**
     * Allows to specify the duration after which the successful job should be (permanently) deleted
     *
     * @param deleteAfter            duration after which the job should move from SUCCEEDED to DELETED.
     * @param permanentlyDeleteAfter after which the job in the DELETED state will be permanently deleted. If null is passed the server default will be used.
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder withDeleteOnSuccess(Duration deleteAfter, Duration permanentlyDeleteAfter) {
        this.deleteOnSuccess = DeleteFilter.toParseableString(deleteAfter, permanentlyDeleteAfter);
        return this;
    }

    /**
     * Allows to specify the duration after which to delete failed jobs in the following format:
     * <code>duration1((!)duration2)</code>
     * where
     * - duration 1 is the duration after which the failed job will move to the DELETED state
     * - duration 2 is the duration after which the job in the DELETED state will be permanently deleted.
     */
    public JobBuilder withDeleteOnFailure(String deleteOnFailure) {
        this.deleteOnFailure = deleteOnFailure;
        return this;
    }

    /**
     * Allows to specify the duration after which the failed job should go to the DELETED state
     *
     * @param deleteAfter duration after which the job should move from FAILED to DELETED.
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder withDeleteOnFailure(Duration deleteAfter) {
        this.deleteOnFailure = DeleteFilter.toParseableString(deleteAfter, null);
        return this;
    }

    /**
     * Allows to specify the duration after which the failed job should be (permanently) deleted
     *
     * @param deleteAfter            duration after which the job should move from FAILED to DELETED.
     * @param permanentlyDeleteAfter duration after which the job in the DELETED state will be permanently deleted. If null is passed the server default will be used.
     * @return the same builder instance which provides a fluent api
     */
    public JobBuilder withDeleteOnFailure(Duration deleteAfter, Duration permanentlyDeleteAfter) {
        this.deleteOnFailure = DeleteFilter.toParseableString(deleteAfter, permanentlyDeleteAfter);
        return this;
    }

    JobBuilder setInitialState(InitialState initialState) {
        return setInitialState(initialState, "Expected an initial EnqueuedState but it was " + state.getClass().getSimpleName());
    }

    JobBuilder setInitialState(InitialState initialState, String exceptionMessageIfInitialStateAlreadySet) {
        if (!(this.state instanceof EnqueuedState)) {
            throw new IllegalArgumentException(exceptionMessageIfInitialStateAlreadySet);
        }
        this.state = initialState;
        return this;
    }

    /**
     * Not publicly visible as it will be used by the {@link JobScheduler} only.
     *
     * @param queues the {@link Queues} the queues on which jobs can be run
     * @return the actual {@link Job} to create
     */
    protected Job build(JobDetailsGenerator jobDetailsGenerator, Queues queues) {
        if (jobLambda == null) {
            throw new IllegalArgumentException("If using a lambda, you must use the JobScheduler.");
        }
        JobDetails jobDetails = jobDetailsGenerator.toJobDetails(jobLambda);
        return build(jobDetails, queues);
    }

    /**
     * Not publicly visible as it will be used by the {@link JobRequestScheduler} only.
     *
     * @param queues the {@link Queues} the queues on which jobs can be run
     * @return the actual {@link Job} to create
     */
    protected Job build(Queues queues) {
        if (jobRequest == null) {
            throw new IllegalArgumentException("If using a JobRequest, you must use the JobRequestScheduler.");
        }
        JobDetails jobDetails = new JobDetails(jobRequest);
        return build(jobDetails, queues);
    }

    private Job build(JobDetails jobDetails, Queues queues) {
        if (JobUtils.getJobAnnotation(jobDetails).isPresent()) {
            throw new IllegalStateException("You are combining the JobBuilder with the Job annotation which is not allowed. You can only use one of them.");
        }

        Job job;
        if (isBatchJob) {
            job = new BatchJob(jobId, jobDetails, state);
        } else {
            job = new Job(jobId, jobDetails, state);
        }
        setOptionalFields(job, queues);
        return job;
    }

    private void setOptionalFields(Job job, Queues queues) {
        setJobName(job);
        setAmountOfRetries(job);
        setLabels(job);
        setServerTag(job);
        setMutex(job);
        setRateLimiter(job);
        setQueue(job, queues);
        setDeleteOnSuccess(job);
        setDeleteOnFailure(job);
        setProcessTimeOut(job);
    }

    private void setJobName(Job job) {
        if (jobName != null) {
            job.setJobName(jobName);
        }
    }

    private void setAmountOfRetries(Job job) {
        if (retries != null) {
            job.setAmountOfRetries(retries);
        }
    }

    private void setLabels(Job job) {
        if (labels != null) {
            Whitebox.setInternalState(job, "labels", new HashSet<>(labels));
        }
    }

    private void setServerTag(Job job) {
        if (serverTag != null) {
            job.setServerTag(serverTag);
        }
    }

    private void setMutex(Job job) {
        if (mutex != null) {
            job.setMutex(mutex);
        }
    }

    private void setRateLimiter(Job job) {
        if (rateLimiter != null) {
            job.setRateLimiter(rateLimiter);
        }
    }

    private void setQueue(Job job, Queues queues) {
        if (queue != null) {
            job.setPriority(queues.mapQueueToPriority(queue));
        } else {
            job.setPriority(queues.getDefaultQueuePriority());
        }
    }

    private void setDeleteOnSuccess(Job job) {
        if (deleteOnSuccess != null) {
            job.setDeleteOnSuccess(deleteOnSuccess);
        }
    }

    private void setDeleteOnFailure(Job job) {
        if (deleteOnFailure != null) {
            job.setDeleteOnFailure(deleteOnFailure);
        }
    }

    private void setProcessTimeOut(Job job) {
        if (processTimeOut != null) {
            job.setProcessTimeOut(processTimeOut);
        }
    }
}