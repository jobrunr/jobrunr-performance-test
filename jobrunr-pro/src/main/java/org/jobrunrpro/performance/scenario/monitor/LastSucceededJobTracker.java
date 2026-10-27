package org.jobrunrpro.performance.scenario.monitor;

import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.filters.ApplyStateFilter;
import org.jobrunr.jobs.states.JobState;
import org.jobrunr.jobs.states.SucceededState;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * When the last job succeeded on this BackgroundJobServer. Must be registered before the server starts.
 */
public class LastSucceededJobTracker implements ApplyStateFilter {

    private final AtomicLong lastSucceededAt = new AtomicLong();

    @Override
    public void onStateApplied(Job job, JobState oldState, JobState newState) {
        // after the state is saved, unlike JobServerFilter.onProcessingSucceeded
        if (!(newState instanceof SucceededState) || job.getRecurringJobId().isPresent()) return;
        lastSucceededAt.accumulateAndGet(System.currentTimeMillis(), Math::max);
    }

    public Optional<Instant> lastSucceededAt() {
        long millis = lastSucceededAt.get();
        return millis == 0 ? Optional.empty() : Optional.of(Instant.ofEpochMilli(millis));
    }
}
