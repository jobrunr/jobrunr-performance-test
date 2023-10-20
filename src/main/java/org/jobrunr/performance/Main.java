package org.jobrunr.performance;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jobrunr.configuration.JobRunr;
import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;

public class Main {

    public static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws InterruptedException {
        DataSource dataSource = getDataSource();

        JobRunr.configure()
                .useStorageProvider(SqlStorageProviderFactory.using(dataSource))
                .useBackgroundJobServer(usingStandardBackgroundJobServerConfiguration(), false)
                .useDashboard()
                .initialize();


        PerformanceTestJob performanceTestJob = new PerformanceTestJob();

        Stream<Integer> jobStreamTenantA = IntStream.range(0, 1_000_000)
                .mapToObj(i -> i);

        Long startTime = System.currentTimeMillis();
        BackgroundJob.enqueue(jobStreamTenantA, i -> performanceTestJob.testJob( i, startTime));
        LOGGER.info("Enqueued all jobs - starting processing");
        JobRunr.getBackgroundJobServer().start();
        LOGGER.info("Enqueued all jobs - processing started");
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