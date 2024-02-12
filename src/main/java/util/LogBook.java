package util;

import de.siegmar.fastcsv.writer.CsvWriter;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.server.strategy.WorkDistributionStrategy;
import org.jobrunr.utils.reflection.ReflectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

import static org.jobrunr.utils.StringUtils.substringBefore;
import static org.jobrunr.utils.reflection.ReflectionUtils.objectContainsFieldOrProperty;

public class LogBook {

    public static final Logger LOGGER = LoggerFactory.getLogger(LogBook.class);

    public static void append(String jobRunrProSourceDir, Instant instant, int totalJobs, long startTime, long endTime, String jobRunrType, BackgroundJobServer backgroundJobServer) {
        Path jobProSourceLogBook = Path.of("./jobrunr-pro-source/");

        Path logBookPath = Path.of("./logbook.csv");
        boolean addHeader = !Files.exists(logBookPath);
        try (CsvWriter csv = CsvWriter.builder().build(logBookPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (!Files.exists(jobProSourceLogBook)) Files.createDirectories(jobProSourceLogBook);

            if(jobRunrProSourceDir != null) {
                new Zipper(Path.of(jobRunrProSourceDir, "core"), jobProSourceLogBook.resolve(substringBefore(instant.toString().replace(":", ""), ".") + ".zip"))
                        .excludeFolders("bin", "build", "node_modules")
                        .zip();
            }

            if (addHeader) csv.writeRecord("Date & Time", "Host name", "amount of jobs", "duration", "duration in millis", "jobs / sec", "JobRunr type", "JobRunr version", "Java version", "StorageProvider", "Git Branch", "");
            csv.writeRecord(instant.toString(), InetAddress.getLocalHost().getHostName(), String.valueOf(totalJobs), Duration.ofMillis(endTime - startTime).toString(),
                    String.valueOf(endTime - startTime), String.format(Locale.US, "%.2f", (double) totalJobs / ((endTime - startTime) / 1000.0)),
                    jobRunrType, JarUtils.getVersion(backgroundJobServer.getClass()), getJavaVersion(), backgroundJobServer.getStorageProvider().getStorageProviderInfo().getName(), getBranch(jobRunrProSourceDir), getJobQueue(backgroundJobServer));
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
        if (objectContainsFieldOrProperty(workDistributionStrategy, "queue")) {
            return ReflectionUtils.getValueFromFieldOrProperty(workDistributionStrategy, "queue").getClass().getSimpleName();
        }
        return null;
    }
}
