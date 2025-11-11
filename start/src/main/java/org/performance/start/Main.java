package org.performance.start;


import org.performance.datastore.DataStore;
import org.performance.datastore.DataStore.DataStoreType;
import org.performance.scenario.Scenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Stream;

import static java.lang.Boolean.parseBoolean;
import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.joining;
import static org.performance.utils.ArgUtils.getArg;
import static org.performance.utils.StringUtils.substringBefore;

public class Main {

    private static final Logger log = LoggerFactory.getLogger(Main.class);
    public static String ARG_JVM_PER_DATASTORE = "jvm-per-datastore";
    public static String ARG_TOOL = "tool";
    public static String ARG_DATASTORE = "datastore";
    public static String ARG_SCENARIO = "scenario";

    public static void main(String[] args) throws Exception {
        boolean newJVMPerDataStore = parseBoolean(getArg(args, ARG_JVM_PER_DATASTORE, "false"));
        String tool = getArg(args, ARG_TOOL);
        String version = ofNullable(System.getProperty("tool.version")).orElse("1.0.0-SNAPSHOT");
        String datastore = getArg(args, ARG_DATASTORE);
        String scenario = getArg(args, ARG_SCENARIO);

        log.info("Running {} for {} (version: {}) with {}", scenario, tool, version, datastore);

        if (tool == null || datastore == null || scenario == null) {
            throw new IllegalArgumentException("Missing required arguments: " + ARG_TOOL + ", " + ARG_DATASTORE + ", " + ARG_SCENARIO);
        }

        if ("all".equals(datastore)) {
            for (DataStoreType dataStoreType : DataStoreType.all()) {
                runScenario(newJVMPerDataStore, tool, version, dataStoreType, scenario, args);
            }
        } else if ("allButSlow".equals(datastore)) {
            for (DataStoreType dataStoreType : DataStoreType.allButSlow()) {
                runScenario(newJVMPerDataStore, tool, version, dataStoreType, scenario, args);
            }
        } else {
            runScenario(newJVMPerDataStore, tool, version, DataStoreType.valueOf(datastore), scenario, args);
        }
    }

    public static void runScenario(boolean inNewJVM, String tool, String version, DataStoreType dataStoreType, String scenarioName, String[] args) throws Exception {
        if (inNewJVM) {
            runScenarioInNewJVM(tool, version, dataStoreType, scenarioName, args);
        } else {
            runScenarioInCurrentJVM(tool, dataStoreType, scenarioName, args);
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void runScenarioInCurrentJVM(String tool, DataStoreType dataStoreType, String scenarioName, String[] args) throws Exception {
        DataStore dataStore = DataStore.loadDataStore(dataStoreType);
        Scenario scenario = Scenario.loadScenario(tool, scenarioName, dataStore, args);
        scenario.run();
    }

    public static void runScenarioInNewJVM(String tool, String version, DataStoreType dataStoreType, String scenarioName, String[] args) {
        try {
            // Assume the project root is the current working directory.
            Path projectRoot = Paths.get(System.getProperty("user.dir"));
            String pomPath = projectRoot.resolve("pom.xml").toString();

            String execArgs = String.format("tool=%s datastore=%s scenario=%s system-exit=true %s", tool, dataStoreType.name(), scenarioName,
                    Stream.of(args).filter(x -> !Set.of(ARG_JVM_PER_DATASTORE, ARG_DATASTORE, ARG_SCENARIO).contains(substringBefore(x, "="))).collect(joining(" ")));

            String mavenCmd = createMavenCmd(pomPath, version, execArgs);
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", mavenCmd);
            pb.inheritIO();
            Process process = pb.start();
            process.waitFor();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static String createMavenCmd(String pomPath, String version, String execArgs) {
        return String.format("mvn -f \"%s\" compile exec:java@performance-test -Djobrunr.version=%s -Dexec.args=\"%s\"",
                pomPath, version, execArgs);
    }
}
