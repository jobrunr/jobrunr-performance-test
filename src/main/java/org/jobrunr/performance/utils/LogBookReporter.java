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
import java.util.Locale;

import static org.jobrunr.performance.utils.JobRunrUtils.getJavaVersion;
import static org.jobrunr.performance.utils.JobRunrUtils.getJobRunrType;
import static org.jobrunr.performance.utils.JobRunrUtils.getJobRunrVersion;
import static org.jobrunr.performance.utils.ReportingUtils.findLogbooksFolder;
import static org.jobrunr.performance.utils.StringUtils.camelCaseToHumanReadable;

public class LogBookReporter {

    public static final Logger LOGGER = LoggerFactory.getLogger(LogBookReporter.class);

    public static void append(BackgroundJobServer backgroundJobServer, ScenarioResult scenarioResult) {
        Path logBookPath = findLogbooksFolder().resolve(camelCaseToHumanReadable(scenarioResult.getScenario()) + "-logbook.csv");
        boolean addHeader = !Files.exists(logBookPath);
        try (CsvWriter csv = CsvWriter.builder().build(logBookPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (addHeader)
                csv.writeRecord("Date & Time", "Host name", "Java version", "JobRunr type", "JobRunr version", "StorageProvider", "amount of created jobs", "amount of succeeded jobs", "creation duration", "processing duration", "job throughput (jobs / sec)");
            csv.writeRecord(scenarioResult.getTimestamp().toString(), InetAddress.getLocalHost().getHostName(), getJavaVersion(), getJobRunrType(backgroundJobServer), getJobRunrVersion(backgroundJobServer), backgroundJobServer.getStorageProvider().getStorageProviderInfo().getName(), String.valueOf(scenarioResult.getCreatedJobs()), String.valueOf(scenarioResult.getSucceededJobs()), scenarioResult.getCreationDuration().toString(), scenarioResult.getProcessingDuration().toString(), String.format(Locale.US, "%.2f", (double) scenarioResult.getSucceededJobs() / (scenarioResult.getProcessingDuration().toSeconds())));
        } catch (IOException e) {
            LOGGER.error("Could not create logbook", e);
        }
    }
}
