package org.performance.datastore.sql;

import com.zaxxer.hikari.metrics.IMetricsTracker;
import com.zaxxer.hikari.metrics.MetricsTrackerFactory;
import com.zaxxer.hikari.metrics.PoolStats;

import java.util.Locale;
import java.util.concurrent.atomic.LongAccumulator;
import java.util.concurrent.atomic.LongAdder;

/**
 * Hikari's connection wait and hold times, summed until read.
 */
public class HikariConnectionTimings implements MetricsTrackerFactory, IMetricsTracker {

    public record Interval(long acquired, long acquireNanos, long maxAcquireNanos, long usageMillis, long timeouts) {

        @Override
        public String toString() {
            long divisor = Math.max(acquired, 1);
            return String.format(Locale.ROOT, "%d acquired, %.2f ms average wait, %.1f ms max wait, %.2f ms average hold, %d timeouts",
                    acquired, acquireNanos / 1e6 / divisor, maxAcquireNanos / 1e6, (double) usageMillis / divisor, timeouts);
        }
    }

    private final LongAdder acquired = new LongAdder();
    private final LongAdder acquireNanos = new LongAdder();
    private final LongAccumulator maxAcquireNanos = new LongAccumulator(Math::max, 0);
    private final LongAdder usageMillis = new LongAdder();
    private final LongAdder timeouts = new LongAdder();

    @Override
    public IMetricsTracker create(String poolName, PoolStats poolStats) {
        return this;
    }

    @Override
    public void recordConnectionAcquiredNanos(long elapsedAcquiredNanos) {
        acquired.increment();
        acquireNanos.add(elapsedAcquiredNanos);
        maxAcquireNanos.accumulate(elapsedAcquiredNanos);
    }

    @Override
    public void recordConnectionUsageMillis(long elapsedBorrowedMillis) {
        usageMillis.add(elapsedBorrowedMillis);
    }

    @Override
    public void recordConnectionTimeout() {
        timeouts.increment();
    }

    public Interval sinceLastRead() {
        return new Interval(acquired.sumThenReset(), acquireNanos.sumThenReset(), maxAcquireNanos.getThenReset(), usageMillis.sumThenReset(), timeouts.sumThenReset());
    }
}
