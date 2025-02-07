package org.jobrunr.performance;

import org.jobrunr.configuration.JobRunrConfiguration;
import org.jobrunr.performance.utils.JarUtils;
import org.jobrunr.server.BackgroundJobServer;

import java.lang.reflect.Method;

import static java.util.Optional.ofNullable;
import static org.jobrunr.utils.reflection.ReflectionUtils.classExists;

public class JobRunrFactory {

    public static JobRunrConfiguration jobRunr() {
        JobRunrConfiguration jobRunrConfiguration = jobRunrConfiguration();
        String title = getTitle(jobRunrConfiguration.getClass()) + " (" + JarUtils.getVersion(jobRunrConfiguration.getClass()) + ")";
        String titleWithMarkup = "======    " + title + "    =======";

        System.out.println("=".repeat(titleWithMarkup.length()));
        System.out.println(titleWithMarkup);
        System.out.println("=".repeat(titleWithMarkup.length()));
        return jobRunrConfiguration;
    }

    public static BackgroundJobServer backgroundJobServer() {
        if (classExists("org.jobrunr.configuration.JobRunrPro")) {
            return backgroundJobServer("org.jobrunr.configuration.JobRunrPro");
        } else if (classExists("org.jobrunr.configuration.JobRunr")) {
            return backgroundJobServer("org.jobrunr.configuration.JobRunr");
        }
        throw new IllegalStateException("JobRunr or JobRunr Pro not found on classpath");
    }

    private static JobRunrConfiguration jobRunrConfiguration() {
        if (classExists("org.jobrunr.configuration.JobRunrPro")) {
            return jobRunrConfiguration("org.jobrunr.configuration.JobRunrPro");
        } else if (classExists("org.jobrunr.configuration.JobRunr")) {
            return jobRunrConfiguration("org.jobrunr.configuration.JobRunr");
        }
        throw new IllegalStateException("JobRunr or JobRunr Pro not found on classpath");
    }

    private static JobRunrConfiguration jobRunrConfiguration(String className) {
        try {
            Class<?> jobRunrClass = Class.forName(className);
            Method configureMethod = jobRunrClass.getMethod("configure");
            return (JobRunrConfiguration) configureMethod.invoke(jobRunrClass);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    static BackgroundJobServer backgroundJobServer(String className) {
        try {
            Class<?> jobRunrClass = Class.forName(className);
            Method configureMethod = jobRunrClass.getMethod("getBackgroundJobServer");
            return (BackgroundJobServer) configureMethod.invoke(jobRunrClass);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static String getTitle(Class<?> clazz) {
        return ofNullable(JarUtils.getManifestAttributeValue(clazz, "Implementation-Title"))
                .orElse("JobRunr");
    }
}
