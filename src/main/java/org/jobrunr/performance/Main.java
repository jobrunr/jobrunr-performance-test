package org.jobrunr.performance;

import com.p6spy.engine.spy.P6DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jobrunr.configuration.JobRunrPro;
import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;

public class Main {

    public static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    public static final CountDownLatch countDownLatch = new CountDownLatch(250_000);


    public static void main(String[] args) throws InterruptedException {
        DataSource dataSource = getDataSource();

        System.out.println("=============================");
        System.out.println("======   " + JobRunrPro.class.getSimpleName() + "   =======");
        System.out.println("=============================");

        JobRunrPro.configure()
                .useStorageProvider(SqlStorageProviderFactory.using(dataSource))
                .useBackgroundJobServer(usingStandardBackgroundJobServerConfiguration().andPollIntervalInSeconds(5), false)
                .useDashboard()
                .initialize();


        PerformanceTestJob performanceTestJob = new PerformanceTestJob();

        Stream<Integer> jobStreamTenantA = IntStream.range(0, (int) countDownLatch.getCount())
                .boxed();

        BackgroundJob.enqueue(jobStreamTenantA, performanceTestJob::testJob);

        try(Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("VACUUM (VERBOSE, ANALYZE) jobrunr_jobs;");
        } catch(java.sql.SQLException e) {
            throw new RuntimeException(e);
        }

        LOGGER.info("Enqueued all jobs - starting processing");
        JobRunrPro.getBackgroundJobServer().start();
        long startTime = System.currentTimeMillis();
        LOGGER.info("Enqueued all jobs - processing started");

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
        config.setMinimumIdle(40);
        config.setMaximumPoolSize(80);
        return new P6DataSource(new HikariDataSource(config));
    }
}