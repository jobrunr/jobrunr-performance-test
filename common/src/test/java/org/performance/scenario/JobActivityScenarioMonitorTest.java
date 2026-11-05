package org.performance.scenario;

import org.junit.jupiter.api.Test;
import org.performance.scenario.JobActivityScenarioMonitor.JobActivity;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class JobActivityScenarioMonitorTest {

    private static final Duration POLL_INTERVAL = Duration.ofMillis(10);
    private static final Duration MAX_DURATION = Duration.ofMillis(300);

    private final AtomicInteger occupiedWorkers = new AtomicInteger();
    private final AtomicLong jobStateChanges = new AtomicLong();
    private final AtomicLong succeededJobs = new AtomicLong();
    private final AtomicLong succeededJobsInCluster = new AtomicLong();

    private final JobActivity jobActivity = new JobActivity() {
        @Override
        public int occupiedWorkers() {
            return occupiedWorkers.get();
        }

        @Override
        public long amountOfJobStateChanges() {
            return jobStateChanges.get();
        }

        @Override
        public long amountOfSucceededJobs() {
            return succeededJobs.get();
        }

        @Override
        public long publishSucceededJobsAndSumCluster() {
            return succeededJobsInCluster.get();
        }

        @Override
        public Optional<Instant> lastNonRecurringJobSucceededAt() {
            return Optional.empty();
        }
    };

    @Test
    void stopsWhenTheClusterSucceededAllJobsWhileThisNodeIsStillBusy() {
        succeededJobs.set(2);
        succeededJobsInCluster.set(5);
        occupiedWorkers.set(1);

        assertThat(monitor(5).awaitForScenario()).isTrue();
    }

    @Test
    void stopsWhenWorkersStayIdle() {
        assertThat(monitor(5).awaitForScenario()).isTrue();
    }

    @Test
    void doesNotStopWhileAJobIsRunningWithoutStateChanges() {
        occupiedWorkers.set(1);

        assertThat(monitor(5).awaitForScenario()).isFalse();
    }

    @Test
    void doesNotStopWhileJobsChangeStateBetweenPolls() throws Exception {
        Thread stateChanges = Thread.ofVirtual().start(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                jobStateChanges.incrementAndGet();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    return;
                }
            }
        });

        assertThat(monitor(5).awaitForScenario()).isFalse();

        stateChanges.interrupt();
        stateChanges.join();
    }

    private JobActivityScenarioMonitor monitor(long totalAmountOfJobs) {
        return new JobActivityScenarioMonitor(totalAmountOfJobs, MAX_DURATION, jobActivity, POLL_INTERVAL, 3);
    }
}
