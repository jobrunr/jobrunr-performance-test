package org.jobrunr.performance;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jobrunr.configuration.JobRunr;
import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;

public class Main {

    public static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static final MetricRegistry metrics = new MetricRegistry();
    public static final CountDownLatch countDownLatch = new CountDownLatch(500_000);


    public static void main(String[] args) throws InterruptedException {
        DataSource dataSource = getDataSource();

        JobRunr.configure()
                .useStorageProvider(SqlStorageProviderFactory.using(dataSource))
                .useBackgroundJobServer(usingStandardBackgroundJobServerConfiguration(), false)
                .useDashboard()
                .initialize();


        PerformanceTestJob performanceTestJob = new PerformanceTestJob();

        Stream<Integer> jobStreamTenantA = IntStream.range(0, (int) countDownLatch.getCount())
                .mapToObj(i -> i);

        BackgroundJob.enqueue(jobStreamTenantA, performanceTestJob::testJob);
        LOGGER.info("Enqueued all jobs - starting processing");
        JobRunr.getBackgroundJobServer().start();
        long startTime = System.currentTimeMillis();
        LOGGER.info("Enqueued all jobs - processing started");

        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.start(1, TimeUnit.SECONDS);

        countDownLatch.await();
        long endTime = System.currentTimeMillis();
        LOGGER.info("Processing took {}ms", (endTime - startTime));
        System.exit(0);
    }

    protected static DataSource getDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://127.0.0.1:5432/postgres");
        config.setUsername("postgres");
        config.setPassword("postgres");
        config.setMinimumIdle(80);
        config.setMaximumPoolSize(105);
        return new HikariDataSource(config);
    }
}