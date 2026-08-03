package org.performance.start;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public final class RaidHealth {

    private static final Duration TIMEOUT = Duration.ofMinutes(30);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(10);

    private RaidHealth() {
    }

    public static void waitUntilReady() throws IOException, InterruptedException {
        if (!isLinux()) {
            return;
        }

        long deadline = System.nanoTime() + TIMEOUT.toNanos();

        while (!allRaidsReady()) {
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException(
                        "Timed out waiting for Linux RAID arrays to become clean"
                );
            }

            Thread.sleep(POLL_INTERVAL.toMillis());
        }
    }

    private static boolean allRaidsReady() throws IOException {
        try (var devices = Files.list(Path.of("/sys/block"))) {
            var raidDevices = devices
                    .filter(path -> path.getFileName().toString().matches("md\\d+"))
                    .toList();

            for (Path raidDevice : raidDevices) {
                Path md = raidDevice.resolve("md");

                int degraded = Integer.parseInt(read(md.resolve("degraded")));
                String syncAction = read(md.resolve("sync_action"));
                String arrayState = read(md.resolve("array_state"));

                boolean ready =
                        degraded == 0
                                && syncAction.equals("idle")
                                && arrayState.equals("clean");

                if (!ready) {
                    return false;
                }
            }

            return true;
        }
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path).trim();
    }

    private static boolean isLinux() {
        return System.getProperty("os.name")
                .toLowerCase()
                .contains("linux");
    }
}
