package org.performance.utils;

import de.siegmar.fastcsv.writer.CsvWriter;
import org.performance.datastore.DataStore;
import org.performance.scenario.ScenarioResult;
import org.performance.tools.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Locale;

import static org.performance.utils.JarUtils.getJavaVersion;
import static org.performance.utils.ReportingUtils.findLogbooksFolder;
import static org.performance.utils.StringUtils.camelCaseToHumanReadable;

public class LogBookReporter {

    public static final Logger LOGGER = LoggerFactory.getLogger(LogBookReporter.class);

    public static void append(Tool tool, DataStore dataStore, ScenarioResult scenarioResult, String... extraParams) {
        Path logBookPath = findLogbooksFolder().resolve(camelCaseToHumanReadable(scenarioResult.getScenario()) + "-logbook.csv");
        boolean addHeader = !Files.exists(logBookPath);
        try (CsvWriter csv = CsvWriter.builder().build(logBookPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (addHeader)
                csv.writeRecord("Date & Time", "Host name", "Java version", "Tool", "Tool version", "StorageProvider", "amount of created jobs", "amount of succeeded jobs", "creation duration", "processing duration", "job throughput (jobs / sec)");
            String[] defaultParams = new String[]{scenarioResult.getTimestamp().toString(), InetAddress.getLocalHost().getHostName(), getJavaVersion(), tool.getName(), tool.getVersion(), dataStore.getNameAndVersion(), String.valueOf(scenarioResult.getCreatedJobs()), String.valueOf(scenarioResult.getSucceededJobs()), scenarioResult.getCreationDuration().toString(), scenarioResult.getProcessingDuration().toString(), String.format(Locale.US, "%.2f", (double) scenarioResult.getSucceededJobs() / (scenarioResult.getProcessingDuration().toSeconds()))};
            String[] allParams = concat(defaultParams, extraParams);
            csv.writeRecord(allParams);
        } catch (IOException e) {
            LOGGER.error("Could not create logbook", e);
        }
    }

    private static <T> T[] concat(T[] array1, T[] array2) {
        T[] result = Arrays.copyOf(array1, array1.length + array2.length);
        System.arraycopy(array2, 0, result, array1.length, array2.length);
        return result;
    }
}
