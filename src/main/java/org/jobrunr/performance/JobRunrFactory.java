package org.jobrunr.performance;

import org.jobrunr.configuration.JobRunrConfiguration;
import org.jobrunr.server.BackgroundJobServer;
import util.JarUtils;

import java.lang.reflect.Method;

import static org.jobrunr.utils.reflection.ReflectionUtils.classExists;

public class JobRunrFactory {

    public static JobRunrConfiguration jobRunr() {
        JobRunrConfiguration jobRunrConfiguration = jobRunrConfiguration();
        String title = JarUtils.getManifestAttributeValue(jobRunrConfiguration.getClass(), "Implementation-Title") + " (" + JarUtils.getVersion(jobRunrConfiguration.getClass()) + ")";
        System.out.println("=".repeat(20 + title.length()));
        System.out.println("======    " + title + "    =======");
        System.out.println("=".repeat(20 + title.length()));
        return jobRunrConfiguration;
    }

    public static BackgroundJobServer backgroundJobServer() {
        return backgroundJobServer(jobRunrConfiguration().getClass());
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

    static BackgroundJobServer backgroundJobServer(Class<?> jobRunrClass) {
        try {
            Method configureMethod = jobRunrClass.getMethod("getBackgroundJobServer");
            return (BackgroundJobServer) configureMethod.invoke(jobRunrClass);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }
}
