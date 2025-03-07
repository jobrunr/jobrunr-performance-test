package org.jobrunr.performance.utils;

import org.jobrunr.server.BackgroundJobServer;

import static java.util.Optional.ofNullable;

public class JobRunrUtils {

    public static String getJobRunrVersion(BackgroundJobServer backgroundJobServer) {
        String version = JarUtils.getVersion(backgroundJobServer.getClass());
        String branch = JarUtils.getManifestAttributeValue(backgroundJobServer.getClass(), "Git-Branch");
        String commitId = ofNullable(JarUtils.getManifestAttributeValue(backgroundJobServer.getClass(), "Git-Commit")).map(x -> "@" + x).orElse("");
        if (StringUtils.isNullOrEmpty(branch)) return version;
        return version + " (" + branch + commitId + ")";
    }

    public static String getJobRunrType(BackgroundJobServer backgroundJobServer) {
        return ofNullable(JarUtils.getManifestAttributeValue(backgroundJobServer.getClass(), "Implementation-Title"))
                .orElse("JobRunr");
    }

    public static String getJavaVersion() {
        return Runtime.version().toString();
    }
}
