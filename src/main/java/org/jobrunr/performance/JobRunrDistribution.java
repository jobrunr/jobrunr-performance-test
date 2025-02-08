package org.jobrunr.performance;

import org.jobrunr.configuration.JobRunrConfiguration;
import org.jobrunr.performance.utils.JarUtils;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.storage.StorageProvider;

import java.lang.reflect.Method;

import static org.jobrunr.utils.reflection.ReflectionUtils.classExists;

public enum JobRunrDistribution {

    JobRunrOSS("JobRunr", "OSS"),
    JobRunrPro("JobRunrPro", "PRO") {
        @Override
        public void saveLicense(StorageProvider storageProvider) {
            String license = "1.0.0-SNAPSHOT".equals(getVersion())
                    ? "eyJhbGciOiJFQyIsImNydiI6ICJQLTI1NiIsInR5cCI6ICJKV1QifQ.eyJzdWJzY3JpcHRpb25JZCI6IjMxOTI5ZDYzLWRhMDItNDYwMi04ZjliLWYyMzk1YTY3YzUwOSIsImNvbXBhbnkiOiJSb3NvY28gQlYiLCJ0cmlhbCI6ZmFsc2UsInZhbGlkVW50aWwiOiIyMDI1LTAzLTAyIn0.Rhpzb8IotGK4ejni83CAllJ-VLwi3CNMo9UA-gl2swRisqyTeLxAjC-ESyJR0j_OdYzRB09L6kY8cG-w88n8_w=="
                    : "eyJhbGciOiJFQyIsImNydiI6ICJQLTI1NiIsInR5cCI6ICJKV1QifQ.eyJzdWJzY3JpcHRpb25JZCI6IjY5ODU5YTllLTQyNTAtNDQ4Zi04MmZjLTVkZWU1MDdlYjBlMyIsImNvbXBhbnkiOiJMaWZlLkNodXJjaCIsInRyaWFsIjpmYWxzZSwidmFsaWRVbnRpbCI6IjIwMjUtMDMtMTgifQ.XqwPVgcaFhV4_me9MwqHKOOgzpN-opR-jM0TndZyq5M4mBA0MTrbcY55VsnghkaVRFuuwFyZJWkPEg7pbopsng==";

            // To be compatible with v6
            try {
                Method m = storageProvider.getClass().getMethod("saveLicense", String.class);
                m.invoke(storageProvider, license);
            } catch (ReflectiveOperationException e) {
                // it will fail when starting if the license is not set
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
    }

    private final String configurationClassName;
    private final String mavenProfile;
    private final JobRunrConfiguration jobRunrConfiguration;

    JobRunrDistribution(String configurationClassName, String mavenProfile) {
        this.configurationClassName = "org.jobrunr.configuration." + configurationClassName;
        this.mavenProfile = mavenProfile;
        this.jobRunrConfiguration = jobRunrConfiguration();
    }

    public boolean isAvailable() {
        return classExists(configurationClassName);
    }

    public String getTitle() {
        return name() + " (" + getVersion() + ")";
    }

    public String getMavenProfile() {
        return mavenProfile;
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

    public void stop() {
        try {
            Class<?> jobRunrClass = Class.forName(configurationClassName);
            Method configureMethod = jobRunrClass.getMethod("destroy");
            configureMethod.invoke(jobRunrClass);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Unable to stop JobRunr", e);
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
