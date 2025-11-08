package org.performance.start;


import org.jobrunr.performance.JobRunrDistribution;
import org.performance.datastore.DataStore;
import org.performance.datastore.DataStore.DataStoreType;
import org.performance.scenario.Scenario;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;
import java.util.stream.Stream;

import static java.lang.Boolean.parseBoolean;
import static java.util.stream.Collectors.joining;
import static org.jobrunr.performance.utils.ArgUtils.getArg;
import static org.performance.utils.StringUtils.substringBefore;

public class Main {

    public static String ARG_JVM_PER_DATASTORE = "jvm-per-datastore";
    public static String ARG_DATASTORE = "datastore";
    public static String ARG_SCENARIO = "scenario";

    public static void main(String[] args) throws Exception {
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

    public static void runScenario(boolean inNewJVM, DataStoreType dataStoreType, String scenarioName, String[] args) throws Exception {
        if (inNewJVM) {
            runScenarioInNewJVM(dataStoreType, scenarioName, args);
        } else {
            runScenarioInCurrentJVM(dataStoreType, scenarioName, args);
            try {
                Thread.currentThread().join();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void runScenarioInCurrentJVM(DataStoreType dataStoreType, String scenarioName, String[] args) throws Exception {
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
            String execArgs = String.format("datastore=%s scenario=%s system-exit=true %s", dataStoreType.name(), scenarioName,
                    Stream.of(args).filter(x -> !Set.of(ARG_JVM_PER_DATASTORE, ARG_DATASTORE, ARG_SCENARIO).contains(substringBefore(x, "="))).collect(joining(" ")));

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

    private static String createMavenCmd(String pomPath, String mavenProfile, String version, String execArgs) {
        return String.format("mvn -f \"%s\" compile exec:java@performance-test -P %s -Djobrunr.version=%s -Dexec.args=\"%s\"",
                pomPath, mavenProfile, version, execArgs);
    }
}
