package org.jobrunr.performance;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jobrunr.configuration.JobRunrConfiguration;
import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.jobrunr.storage.sql.postgres.PostgresStorageProvider;
import org.jobrunr.storage.sql.sqlserver.SQLServerStorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.JarUtils;
import util.LogBook;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;
import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;

public class Main {

    public static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    public static CountDownLatch countDownLatch;


    public static void main(String[] args) throws InterruptedException {
        DataSource dataSource = getPostgresDataSource(); // new P6DataSource(getPostgresDataSource());
        StorageProvider storageProvider = SqlStorageProviderFactory.using(dataSource);

        int dashboardPort = parseInt(getArg("dashboard_port", args, "0"));
        jobRunr()
                .useStorageProvider(storageProvider)
                .useBackgroundJobServer(usingStandardBackgroundJobServerConfiguration().andPollIntervalInSeconds(5), false)
                .useDashboardIf(dashboardPort > 0, dashboardPort)
                .initialize();

        String jobRunrProSourceDir = getArg("jobRunrProSourceDir", args);
        if(jobRunrProSourceDir != null && !Files.exists(Path.of(jobRunrProSourceDir, "core"))) {
            throw new IllegalStateException("Cannot find JobRunr Pro Source Dir at " + Path.of(jobRunrProSourceDir) + " for logbook");
        }

        int totalAmountOfJobs = createJobs(args, storageProvider, dataSource);

        backgroundJobServer().start();
        long startTime = System.currentTimeMillis();
        LOGGER.info("Enqueued all jobs - processing started");

        if(totalAmountOfJobs > 0) {
            countDownLatch.await();
            long endTime = System.currentTimeMillis();
            LOGGER.info("Processing took {}ms", (endTime - startTime));
            LogBook.append(jobRunrProSourceDir, Instant.now(), totalAmountOfJobs, startTime, endTime, JarUtils.getManifestAttributeValue(backgroundJobServer().getClass(), "Implementation-Title"), backgroundJobServer());
            System.exit(0);
        } else {
            Thread.currentThread().join();
        }
    }

    private static int createJobs(String[] args, StorageProvider storageProvider, DataSource dataSource) {
        int totalAmountOfJobs = parseInt(getArg("amount", args, "0").replace("_", ""));

        if(totalAmountOfJobs < 1) return 0;

        countDownLatch = new CountDownLatch(totalAmountOfJobs);
        PerformanceTestJob performanceTestJob = new PerformanceTestJob();
        Stream<Integer> jobStreamTenantA = IntStream.range(0, totalAmountOfJobs).boxed();
        BackgroundJob.enqueue(jobStreamTenantA, performanceTestJob::testJob);
        if (storageProvider instanceof PostgresStorageProvider) {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("VACUUM (VERBOSE, ANALYZE) jobrunr_jobs;");
                LOGGER.info("VACUUMED POSTGRES TABLES");
            } catch (java.sql.SQLException e) {
                throw new RuntimeException(e);
            }
        } else if (storageProvider instanceof SQLServerStorageProvider) {
            try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("UPDATE STATISTICS jobrunr_jobs;");
                LOGGER.info("UPDATED SQLSERVER STATISTICS");
            } catch (java.sql.SQLException e) {
                throw new RuntimeException(e);
            }
        }
        LOGGER.info("Enqueued all jobs - starting processing");
        return totalAmountOfJobs;
    }

    static JobRunrConfiguration jobRunr() {
        JobRunrConfiguration jobRunrConfiguration;

        jobRunrConfiguration = jobRunrConfiguration("org.jobrunr.configuration.JobRunrPro");
        if(jobRunrConfiguration != null) {
            String title = "JobRunrPro (" + JarUtils.getVersion(jobRunrConfiguration.getClass()) + ")";
            System.out.println("=".repeat(20 + title.length()));
            System.out.println("======    " + title + "    =======");
            System.out.println("=".repeat(20 + title.length()));
            return jobRunrConfiguration;
        }

        jobRunrConfiguration = jobRunrConfiguration("org.jobrunr.configuration.JobRunr");
        if(jobRunrConfiguration != null) {
            String title = "JobRunr (" + JarUtils.getVersion(jobRunrConfiguration.getClass()) + ")";
            System.out.println("=".repeat(20 + title.length()));
            System.out.println("======    " + title + "    =======");
            System.out.println("=".repeat(20 + title.length()));
            return jobRunrConfiguration;
        }

        throw new IllegalStateException("JobRunr (Pro) not found on classpath");
    }

    public static void countDown() {
        if(countDownLatch != null) {
            countDownLatch.countDown();
        }
    }

    public static boolean hasPerformanceTestFinished() {
        if(countDownLatch != null) {
            return countDownLatch.getCount() == 0;
        }
        return false;
    }

    static BackgroundJobServer backgroundJobServer() {
        BackgroundJobServer backgroundJobServer;

        backgroundJobServer = backgroundJobServer("org.jobrunr.configuration.JobRunrPro");
        if(backgroundJobServer != null) return backgroundJobServer;

        backgroundJobServer = backgroundJobServer("org.jobrunr.configuration.JobRunr");
        if(backgroundJobServer != null) return backgroundJobServer;

        throw new IllegalStateException("JobRunr (Pro) not found on classpath");
    }

    static JobRunrConfiguration jobRunrConfiguration(String className) {
        try {
            Class<?> jobRunrClass = Class.forName(className);
            Method configureMethod = jobRunrClass.getMethod("configure");
            return (JobRunrConfiguration) configureMethod.invoke(jobRunrClass);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    static BackgroundJobServer backgroundJobServer(String className) {
        try {
            Class<?> jobRunrClass = Class.forName(className);
            Method configureMethod = jobRunrClass.getMethod("getBackgroundJobServer");
            return (BackgroundJobServer) configureMethod.invoke(jobRunrClass);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    protected static DataSource getDataSource(String[] args) {
        String database = getArg("database", args, "postgres");
        if("postgres".equals(database)) {
            return getPostgresDataSource();
        } else if("mysql".equals(database)) {
            return getMySQLDataSource();
        } else if("sqlserver".equals(database)) {
            return getSQLServerDataSource();
        }
        throw new IllegalStateException("Unknown database: " + database);
    }

    protected static DataSource getPostgresDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:postgresql://127.0.0.1:5432/postgres");
        config.setUsername("postgres");
        config.setPassword("oTsMa6h61BOFYTpIVvLs");
        config.setMinimumIdle(40);
        config.setMaximumPoolSize(80);
        return new HikariDataSource(config);
    }

    protected static DataSource getSQLServerDataSource() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlserver://localhost:1433;databaseName=tempdb;encrypt=true;trustServerCertificate=true;");
        config.setUsername("sa");
        config.setPassword("sqlServer(!)");
        config.setMinimumIdle(40);
        config.setMaximumPoolSize(80);
        return new HikariDataSource(config);
    }

    protected static DataSource getMySQLDataSource() {
        try {
            Runtime.getRuntime().exec(new String[] { "/bin/bash", "-c", "sh run-db-mysql.sh" });
        } catch (IOException e) {
            throw new IllegalStateException("Could not start MySQL", e);
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:mysql://localhost:3306/mysql");
        config.setUsername("mysql-jobrunr");
        config.setPassword("7UAZ5ZAt46QqxQrwyjL64gXp");
        config.setMinimumIdle(40);
        config.setMaximumPoolSize(80);
        return new HikariDataSource(config);
    }

    private static String getArg(String key, String[] args) {
        return getArg(key, args, null);
    }

    private static String getArg(String key, String[] args, String defaultValue) {
        return Stream.of(args)
                .filter(x -> x.startsWith(key))
                .map(x -> x.replace(key + "=", ""))
                .findFirst()
                .orElse(defaultValue);
    }
}