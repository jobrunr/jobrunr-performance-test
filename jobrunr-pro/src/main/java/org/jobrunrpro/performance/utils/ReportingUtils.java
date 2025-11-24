package org.jobrunrpro.performance.utils;

import java.nio.file.Files;
import java.nio.file.Path;

public class ReportingUtils {

    public static Path findLogbooksFolder() {
        Path current = Path.of(".").toAbsolutePath().normalize();
        while (current != null) {
            Path logbooks = current.resolve("logbooks");
            if (Files.isDirectory(logbooks)) {
                return logbooks;
            }
            current = current.getParent();
        }
        throw new RuntimeException("Could not find logbooks folder");
    }
}
