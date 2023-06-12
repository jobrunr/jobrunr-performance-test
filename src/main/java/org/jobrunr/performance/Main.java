package org.jobrunr.performance;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jobrunr.configuration.JobRunrPro;
import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;

import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {

    public static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static void main(String[] args) throws InterruptedException {
        DataSource dataSource = getDataSource();

        JobRunrPro.configure()
                .useStorageProvider(SqlStorageProviderFactory.using(dataSource))
                .useBackgroundJobServer()
                .useDashboard()
                .initialize();


        PerformanceTestJob performanceTestJob = new PerformanceTestJob();

        Stream<Integer> jobStreamTenantA = IntStream.range(0, 1000000)
                .mapToObj(i -> i);

        Long startTime = System.currentTimeMillis();
        BackgroundJob.enqueue(jobStreamTenantA, i -> performanceTestJob.testJob( i, startTime));
        LOGGER.info("Enqueued all jobs");
    }

    protected static DataSource getDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://127.0.0.1:5432/postgres");
        config.setUsername("postgres");
        config.setPassword("postgres");
        config.setMinimumIdle(5);
        config.setMaximumPoolSize(105);
        return new HikariDataSource(config);
    }
}