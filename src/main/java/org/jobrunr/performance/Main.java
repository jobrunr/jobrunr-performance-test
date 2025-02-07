package org.jobrunr.performance;

import org.jetbrains.annotations.NotNull;
import org.jobrunr.performance.scenario.Scenario;
import org.jobrunr.performance.storage.DataStore;
import org.jobrunr.performance.storage.DataStore.DataStoreType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Stream;

import static java.lang.Boolean.parseBoolean;
import static java.util.stream.Collectors.joining;
import static org.jobrunr.performance.utils.ArgUtils.getArg;

public class Main {

    public static String ARG_JVM_PER_DATASTORE = "jvm-per-datastore";
    public static String ARG_DATASTORE = "datastore";
    public static String ARG_SCENARIO = "scenario";

    public static void main(String[] args) {
        boolean newJVMPerDataStore = parseBoolean(getArg(args, ARG_JVM_PER_DATASTORE, "false"));
        String datastore = getArg(args, ARG_DATASTORE);
        String scenario = getArg(args, ARG_SCENARIO);

        if ("all".equals(datastore)) {
            for (DataStoreType dataStoreType : DataStoreType.all()) {
                runScenario(newJVMPerDataStore, dataStoreType, scenario, args);
            }
        } else if ("allButSlow".equals(datastore)) {
            for (DataStoreType dataStoreType : DataStoreType.allButSlow()) {
                runScenario(newJVMPerDataStore, dataStoreType, scenario, args);
            }
        } else {
            runScenario(newJVMPerDataStore, DataStoreType.valueOf(datastore), scenario, args);
        }
    }

    public static void runScenario(boolean inNewJVM, DataStoreType dataStoreType, String scenarioName, String[] args) {
        if (inNewJVM) {
            runScenarioInNewJVM(dataStoreType, scenarioName, args);
        } else {
            runScenarioInCurrentJVM(dataStoreType, scenarioName, args);
        }
    }

    public static void runScenarioInCurrentJVM(DataStoreType dataStoreType, String scenarioName, String[] args) {
        DataStore dataStore = DataStore.loadDataStore(dataStoreType);
        Scenario scenario = Scenario.loadScenario(scenarioName, dataStore, args);
        scenario.run();
    }

    public static void runScenarioInNewJVM(DataStoreType dataStoreType, String scenarioName, String[] args) {
        try {
            // Assume the project root is the current working directory.
            Path projectRoot = Paths.get(System.getProperty("user.dir"));
            String pomPath = projectRoot.resolve("pom.xml").toString();

            JobRunrDistribution jobRunrDistribution = JobRunrDistribution.current;
            String version = jobRunrDistribution.getVersion();
            String mavenProfile = jobRunrDistribution.getMavenProfile();
            String execArgs = String.format("datastore=%s scenario=%s %s", dataStoreType.name(), scenarioName,
                    Stream.of(args).filter(x -> !Set.of(ARG_JVM_PER_DATASTORE, ARG_DATASTORE, ARG_SCENARIO).contains(x)).collect(joining(" ")));

            String mavenCmd = createMavenCmd(pomPath, mavenProfile, version, execArgs);

            // Execute the Maven command using ProcessBuilder.
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", mavenCmd);
            pb.inheritIO();
            Process process = pb.start();
            process.waitFor();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static @NotNull String createMavenCmd(String pomPath, String mavenProfile, String version, String execArgs) {
        return String.format("mvn -f \"%s\" compile exec:java@performance-test -P %s -Djobrunr.version=%s -Dexec.args=\"%s\"",
                pomPath, mavenProfile, version, execArgs);
    }
}
