package org.jobrunr.performance;

import org.jobrunr.configuration.JobRunrConfiguration;
import org.jobrunr.performance.utils.JarUtils;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.storage.StorageProvider;

import java.lang.reflect.Method;

import static org.jobrunr.utils.reflection.ReflectionUtils.classExists;

public enum JobRunrDistribution {

    JobRunrOSS("JobRunr"),
    JobRunrPro("JobRunrPro") {
        @Override
        public void saveLicense(StorageProvider storageProvider) {
            if ("1.0.0-SNAPSHOT".equals(getVersion())) {
                storageProvider.saveLicense("eyJhbGciOiJFQyIsImNydiI6ICJQLTI1NiIsInR5cCI6ICJKV1QifQ.eyJzdWJzY3JpcHRpb25JZCI6IjMxOTI5ZDYzLWRhMDItNDYwMi04ZjliLWYyMzk1YTY3YzUwOSIsImNvbXBhbnkiOiJSb3NvY28gQlYiLCJ0cmlhbCI6ZmFsc2UsInZhbGlkVW50aWwiOiIyMDI1LTAzLTAyIn0.Rhpzb8IotGK4ejni83CAllJ-VLwi3CNMo9UA-gl2swRisqyTeLxAjC-ESyJR0j_OdYzRB09L6kY8cG-w88n8_w==");
            } else {
                storageProvider.saveLicense("eyJhbGciOiJFQyIsImNydiI6ICJQLTI1NiIsInR5cCI6ICJKV1QifQ.eyJzdWJzY3JpcHRpb25JZCI6IjY5ODU5YTllLTQyNTAtNDQ4Zi04MmZjLTVkZWU1MDdlYjBlMyIsImNvbXBhbnkiOiJMaWZlLkNodXJjaCIsInRyaWFsIjpmYWxzZSwidmFsaWRVbnRpbCI6IjIwMjUtMDMtMTgifQ.XqwPVgcaFhV4_me9MwqHKOOgzpN-opR-jM0TndZyq5M4mBA0MTrbcY55VsnghkaVRFuuwFyZJWkPEg7pbopsng==");
            }
        }
    };

    public static final JobRunrDistribution current;

    static {
        if (JobRunrOSS.isAvailable()) {
            current = JobRunrOSS;
        } else if (JobRunrPro.isAvailable()) {
            current = JobRunrPro;
        } else {
            throw new IllegalStateException("JobRunr or JobRunr Pro not found on classpath");
        }
        current.printTitleHeader();
    }

    private final String configurationClassName;
    private final JobRunrConfiguration jobRunrConfiguration;

    JobRunrDistribution(String configurationClassName) {
        this.configurationClassName = "org.jobrunr.configuration." + configurationClassName;
        this.jobRunrConfiguration = jobRunrConfiguration();
    }

    public boolean isAvailable() {
        return classExists(configurationClassName);
    }

    public String getTitle() {
        return name() + " (" + getVersion() + ")";
    }

    public void printTitleHeader() {
        String title = getTitle();
        String titleWithMarkup = "======    " + title + "    =======";
        System.out.println("=".repeat(titleWithMarkup.length()));
        System.out.println(titleWithMarkup);
        System.out.println("=".repeat(titleWithMarkup.length()));
    }

    public String getVersion() {
        return JarUtils.getVersion(getConfiguration().getClass());
    }

    public JobRunrConfiguration getConfiguration() {
        return jobRunrConfiguration;
    }

    public void saveLicense(StorageProvider storageProvider) {
        // nothing to do for OSS
    }

    public BackgroundJobServer backgroundJobServer() {
        try {
            Class<?> jobRunrClass = Class.forName(configurationClassName);
            Method configureMethod = jobRunrClass.getMethod("getBackgroundJobServer");
            return (BackgroundJobServer) configureMethod.invoke(jobRunrClass);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private JobRunrConfiguration jobRunrConfiguration() {
        try {
            Class<?> jobRunrClass = Class.forName(configurationClassName);
            Method configureMethod = jobRunrClass.getMethod("configure");
            return (JobRunrConfiguration) configureMethod.invoke(jobRunrClass);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
