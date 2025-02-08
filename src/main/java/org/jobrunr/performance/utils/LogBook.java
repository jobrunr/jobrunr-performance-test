package org.jobrunr.performance.utils;

import de.siegmar.fastcsv.writer.CsvWriter;
import org.jobrunr.performance.scenario.ScenarioResult;
import org.jobrunr.server.BackgroundJobServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Locale;

import static java.util.Optional.ofNullable;
import static org.jobrunr.performance.utils.StringUtils.camelCaseToHumanReadable;

public class LogBook {

    public static final Logger LOGGER = LoggerFactory.getLogger(LogBook.class);

    public static void append(BackgroundJobServer backgroundJobServer, ScenarioResult scenarioResult) {
        Path logBookPath = findLogbooksFolder().resolve(camelCaseToHumanReadable(scenarioResult.getScenario()) + "-logbook.csv");
        boolean addHeader = !Files.exists(logBookPath);
        try (CsvWriter csv = CsvWriter.builder().build(logBookPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (addHeader)
                csv.writeRecord("Date & Time", "Host name", "Java version", "JobRunr type", "JobRunr version", "StorageProvider", "amount of created jobs", "amount of succeeded jobs", "creation duration", "processing duration", "job throughput (jobs / sec)");
            csv.writeRecord(Instant.now().toString(), InetAddress.getLocalHost().getHostName(), getJavaVersion(), getJobRunrType(backgroundJobServer), getJobRunrVersion(backgroundJobServer), backgroundJobServer.getStorageProvider().getStorageProviderInfo().getName(), String.valueOf(scenarioResult.getCreatedJobs()), String.valueOf(scenarioResult.getSucceededJobs()), scenarioResult.getCreationDuration().toString(), scenarioResult.getProcessingDuration().toString(), String.format(Locale.US, "%.2f", (double) scenarioResult.getCreatedJobs() / (scenarioResult.getProcessingDuration().toSeconds())));
        } catch (IOException e) {
            LOGGER.error("Could not create logbook", e);
        }
    }

    private static String getJobRunrVersion(BackgroundJobServer backgroundJobServer) {
        String version = JarUtils.getVersion(backgroundJobServer.getClass());
        String branch = JarUtils.getManifestAttributeValue(backgroundJobServer.getClass(), "Git-Branch");
        if (StringUtils.isNullOrEmpty(branch)) return version;
        return version + " (" + branch + ")";
    }

    private static String getJobRunrType(BackgroundJobServer backgroundJobServer) {
        return ofNullable(JarUtils.getManifestAttributeValue(backgroundJobServer.getClass(), "Implementation-Title"))
                .orElse("JobRunr");
    }

    static String getJavaVersion() throws IOException {
        return Runtime.version().toString();
    }

    public static Path findLogbooksFolder() {
        Path current = Path.of(".").toAbsolutePath().normalize();
        while (current != null) {
            Path logbooks = current.resolve("logbooks");
            if (Files.isDirectory(logbooks)) {
                return logbooks;
            }
            current = current.getParent();
        }
        throw new RuntimeException("Could not find logbooks folder");
    }
}
