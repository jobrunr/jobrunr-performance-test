package org.jobrunr.performance;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.siegmar.fastcsv.writer.CsvWriter;
import org.jobrunr.configuration.JobRunrPro;
import org.jobrunr.scheduling.BackgroundJob;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.server.strategy.WorkDistributionStrategy;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.jobrunr.storage.sql.postgres.PostgresStorageProvider;
import org.jobrunr.storage.sql.sqlserver.SQLServerStorageProvider;
import org.jobrunr.utils.metadata.VersionRetriever;
import org.jobrunr.utils.reflection.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import util.Zipper;

import javax.sql.DataSource;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.lang.Integer.parseInt;
import static org.jobrunr.server.BackgroundJobServerConfiguration.usingStandardBackgroundJobServerConfiguration;
import static org.jobrunr.utils.StringUtils.substringBefore;
import static org.jobrunr.utils.reflection.ReflectionUtils.objectContainsFieldOrProperty;

public class Main {

    public static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
    public static CountDownLatch countDownLatch;


    public static void main(String[] args) throws InterruptedException {
        int totalAmountOfJobs = parseInt(getArg("amount", args, "500_000").replace("_", ""));
        String jobRunrProSourceDir = getArg("jobRunrProSourceDir", args, "../../JobRunrPro");
        if (!Files.exists(Path.of(jobRunrProSourceDir, "core"))) throw new IllegalStateException("Cannot find JobRunr Pro Source Dir for logbook");

        countDownLatch = new CountDownLatch(totalAmountOfJobs);
        DataSource dataSource = getPostgresDataSource(); // new P6DataSource(getPostgresDataSource());
        StorageProvider storageProvider = SqlStorageProviderFactory.using(dataSource);

        System.out.println("=============================");
        System.out.println("======   " + JobRunrPro.class.getSimpleName() + " (" + VersionRetriever.getVersion(JobRunrPro.class) + ")   =======");
        System.out.println("=============================");

        JobRunrPro.configure()
                .useStorageProvider(storageProvider)
                .useBackgroundJobServer(usingStandardBackgroundJobServerConfiguration().andPollIntervalInSeconds(5), false)
                .useDashboard(8010)
                .initialize();


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
        JobRunrPro.getBackgroundJobServer().start();
        long startTime = System.currentTimeMillis();
        LOGGER.info("Enqueued all jobs - processing started");

        countDownLatch.await();
        long endTime = System.currentTimeMillis();
        LOGGER.info("Processing took {}ms", (endTime - startTime));

        appendToLogbook(jobRunrProSourceDir, Instant.now(), totalAmountOfJobs, startTime, endTime, JobRunrPro.getBackgroundJobServer());

        System.exit(0);

    }

    private static void appendToLogbook(String jobRunrProSourceDir, Instant instant, int totalJobs, long startTime, long endTime, BackgroundJobServer backgroundJobServer) {
        Path jobProSourceLogBook = Path.of("./jobrunr-pro-source/");

        Path logBookPath = Path.of("./logbook.csv");
        boolean addHeader = !Files.exists(logBookPath);
        try (CsvWriter csv = CsvWriter.builder().build(logBookPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (!Files.exists(jobProSourceLogBook)) Files.createDirectories(jobProSourceLogBook);
            new Zipper(Path.of(jobRunrProSourceDir, "core"), jobProSourceLogBook.resolve(substringBefore(instant.toString().replace(":", ""), ".")  + ".zip"))
                    .excludeFolders("bin", "build", "node_modules")
                    .zip();

            if (addHeader) csv.writeRecord("Date & Time", "Host name", "amount of jobs", "duration", "duration in millis", "jobs / sec");
            csv.writeRecord(instant.toString(), InetAddress.getLocalHost().getHostName(), String.valueOf(totalJobs), Duration.ofMillis(endTime - startTime).toString(),
                    String.valueOf(endTime - startTime), String.format(Locale.US, "%.2f", (double) totalJobs / ((endTime - startTime) / 1000.0)),
                    getJavaVersion(), getBranch(jobRunrProSourceDir), getJobQueue(backgroundJobServer));
        } catch (IOException e) {
            LOGGER.error("Could not create logbook", e);
        }
    }

    static String getJavaVersion() throws IOException {
        return Runtime.version().toString();
    }

    static String getBranch(String jobRunrProSourceDir) throws IOException {
        Path path = Path.of(jobRunrProSourceDir, ".git/HEAD");
        return Files.readAllLines(path).get(0);
    }

    static String getJobQueue(BackgroundJobServer backgroundJobServer) {
        WorkDistributionStrategy workDistributionStrategy = backgroundJobServer.getWorkDistributionStrategy();
        if(objectContainsFieldOrProperty(workDistributionStrategy, "queue")) {
            return ReflectionUtils.getValueFromFieldOrProperty(workDistributionStrategy, "queue").getClass().getSimpleName();
        }
        return null;
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

    private static String getArg(String key, String[] args, String defaultValue) {
        return Stream.of(args)
                .filter(x -> x.startsWith(key))
                .map(x -> x.replace(key + "=", ""))
                .findFirst()
                .orElse(defaultValue);
    }
}