package org.jobrunrpro.performance.scenario.monitor;

import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.filters.ApplyStateFilter;
import org.jobrunr.jobs.states.JobState;
import org.jobrunr.jobs.states.SucceededState;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.storage.JobRunrMetadata;
import org.jobrunr.storage.StorageException;
import org.jobrunr.storage.StorageProvider;
import org.performance.scenario.JobActivityScenarioMonitor.JobActivity;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

import static org.jobrunr.storage.MetadataSearchRequestBuilder.aMetadataSearchRequest;

/**
 * What the jobs of this JVM are doing. Must be registered before the server starts.
 */
public class JobActivityTracker implements ApplyStateFilter, JobActivity {

    private static final String SUCCEEDED_JOBS_METADATA = "performance-test-succeeded-jobs";

    private final Supplier<BackgroundJobServer> backgroundJobServer;
    private final LongAdder jobStateChanges = new LongAdder();
    private final LongAdder succeededJobs = new LongAdder();
    private final AtomicLong lastSucceededAt = new AtomicLong();

    public JobActivityTracker(Supplier<BackgroundJobServer> backgroundJobServer) {
        this.backgroundJobServer = backgroundJobServer;
    }

    @Override
    public void onStateApplied(Job job, JobState oldState, JobState newState) {
        jobStateChanges.increment();
        if (!(newState instanceof SucceededState)) return;
        succeededJobs.increment();
        // after the state is saved, unlike JobServerFilter.onProcessingSucceeded
        if (job.getRecurringJobId().isEmpty()) lastSucceededAt.accumulateAndGet(System.currentTimeMillis(), Math::max);
    }

    @Override
    public int occupiedWorkers() {
        return backgroundJobServer.get().getJobSteward().getOccupiedWorkerCount();
    }

    @Override
    public long amountOfJobStateChanges() {
        return jobStateChanges.sum();
    }

    @Override
    public long amountOfSucceededJobs() {
        return succeededJobs.sum();
    }

    @Override
    public long publishSucceededJobsAndSumCluster() {
        BackgroundJobServer server = backgroundJobServer.get();
        StorageProvider storageProvider = server.getStorageProvider();
        try {
            storageProvider.saveMetadata(new JobRunrMetadata(SUCCEEDED_JOBS_METADATA, server.getId().toString(), amountOfSucceededJobs()));
            return storageProvider.getMetadata(aMetadataSearchRequest().withNameEquals(SUCCEEDED_JOBS_METADATA).build()).stream().mapToLong(JobRunrMetadata::getValueAsLong).sum();
        } catch (StorageException e) {
            return amountOfSucceededJobs();
        }
    }

    @Override
    public Optional<Instant> lastNonRecurringJobSucceededAt() {
        long millis = lastSucceededAt.get();
        return millis == 0 ? Optional.empty() : Optional.of(Instant.ofEpochMilli(millis));
    }
}
