package org.jobrunr.performance;

import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.Callable;

@Command(name = "performance-test", mixinStandardHelpOptions = true, version = "performance-test 1.0",
        description = "Allows to run JobRunr performance tests")
public class PerformanceTest implements Callable<Integer> {

    public enum JobRunrDistribution {
        OSS,
        Pro
    }

    public enum DataStore {
        MariaDBDataStore,
        MySQLDataStore,
        PostgresDataStore,
        SQLServerDataStore,
    }

    public enum Scenario {
        Scenario01ProcessJobs
    }

    @Parameters(index = "0", description = "The JobRunr Distribution to use (OSS or Pro)")
    private JobRunrDistribution distribution;

    @Parameters(index = "1", description = "The JobRunr Version to use (e.g. 1.0.0-SNAPSHOT or v7.1.0)", defaultValue = "1.0.0-SNAPSHOT")
    private String version;

    @Parameters(index = "2", description = "The DataStore to use. Valid values: ${COMPLETION-CANDIDATES}.")
    private DataStore dataStore;

    @Parameters(index = "3", description = "The Scenario to run. Valid values: ${COMPLETION-CANDIDATES}.")
    private Scenario scenario;

    @Override
    public Integer call() throws Exception { // your business logic goes here...
        // Assume the project root is the current working directory.
        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        String pomPath = projectRoot.resolve("pom.xml").toString();

        // Normalize the jobRunr version: if it starts with 'v', remove the leading 'v'
        String version = this.version.startsWith("v") ? this.version.substring(1) : this.version;

        // Convert distribution to uppercase for the Maven profile.
        String mavenProfile = distribution.toString().toUpperCase();

        // Build the exec.args value: you may add more options as needed.
        String execArgs = String.format("datastore=%s scenario=%s amount=500_000", dataStore.toString(), scenario.toString());

        // Build the full Maven command.
        // For example:
        //   mvn -f "<projectRoot>/pom.xml" compile exec:java -P PRO -Djobrunr.version=7.4.0 -Dexec.args="datastore=PostgresDataStore scenario=Scenario01ProcessJobs"
        String mavenCmd = String.format("mvn -f \"%s\" compile exec:java -P %s -Djobrunr.version=%s -Dexec.args=\"%s\"",
                pomPath, mavenProfile, version, execArgs);

        // Execute the Maven command using ProcessBuilder.
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", mavenCmd);
        // Inherit I/O so that output appears in the console.
        pb.inheritIO();
        Process process = pb.start();

        return process.waitFor();
    }

    public static void main(String[] args) {
        new CommandLine(new PerformanceTest()).execute(args);
    }
}
