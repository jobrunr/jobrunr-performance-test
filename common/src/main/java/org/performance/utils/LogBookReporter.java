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
import java.util.Objects;

import static org.performance.utils.JarUtils.getJavaVersion;
import static org.performance.utils.ReportingUtils.findLogbooksFolder;
import static org.performance.utils.StringUtils.camelCaseToHumanReadable;

public class LogBookReporter {

    public static final Logger LOGGER = LoggerFactory.getLogger(LogBookReporter.class);

    private static final String[] HEADERS = {"Date & Time", "Host name", "Java version", "Tool", "Tool version", "DataStore", "amount of created jobs", "amount of succeeded jobs", "creation duration", "processing duration", "job throughput (jobs / sec)"};
    private static final String[] CLUSTER_HEADERS = {"run id", "cluster size", "node instance type", "node vCPUs", "db instance type"};

    public static void append(Tool tool, DataStore dataStore, ScenarioResult scenarioResult, String... extraParams) {
        String clusterRunId = System.getenv("CLUSTER_RUN_ID");
        if (clusterRunId != null) {
            String[] clusterParams = {clusterRunId, System.getenv("CLUSTER_SIZE"), System.getenv("NODE_INSTANCE_TYPE"), String.valueOf(Runtime.getRuntime().availableProcessors()), System.getenv("DB_INSTANCE_TYPE")};
            append("-cluster-logbook.csv", concat(HEADERS, CLUSTER_HEADERS), tool, dataStore, scenarioResult, concat(clusterParams, extraParams));
        } else {
            append("-logbook.csv", HEADERS, tool, dataStore, scenarioResult, extraParams);
        }
    }

    private static void append(String logBookSuffix, String[] headers, Tool tool, DataStore dataStore, ScenarioResult scenarioResult, String... extraParams) {
        Path logBookPath = findLogbooksFolder().resolve(camelCaseToHumanReadable(scenarioResult.getScenario()) + logBookSuffix);
        boolean addHeader = !Files.exists(logBookPath);
        try (CsvWriter csv = CsvWriter.builder().build(logBookPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            if (addHeader)
                csv.writeRecord(headers);
            String[] defaultParams = new String[]{scenarioResult.getTimestamp().toString(), InetAddress.getLocalHost().getHostName(), getJavaVersion(), tool.getName(), tool.getVersion(), dataStore.getNameAndVersion(), String.valueOf(scenarioResult.getCreatedJobs()), String.valueOf(scenarioResult.getSucceededJobs()), Objects.toString(scenarioResult.getCreationDuration(), "n/a"), scenarioResult.getProcessingDuration().toString(), String.format(Locale.US, "%.2f", scenarioResult.getSucceededJobs() / (scenarioResult.getProcessingDuration().toMillis() / 1000.0))};
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
