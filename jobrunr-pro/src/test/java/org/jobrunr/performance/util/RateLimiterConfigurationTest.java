package org.jobrunr.performance.util;

import org.jobrunr.storage.JobRunrMetadata;
import org.junit.jupiter.api.Test;

import static org.jobrunr.performance.scenario.ratelimiter.RateLimiterConfiguration.concurrentJobRateLimiter;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RateLimiterConfigurationTest {

    @Test
    void shouldCreateRateLimiterConfiguration() {
        JobRunrMetadata jobRunrMetadata = concurrentJobRateLimiter("my-name", 5);
        assertNotNull(jobRunrMetadata);
    }

}