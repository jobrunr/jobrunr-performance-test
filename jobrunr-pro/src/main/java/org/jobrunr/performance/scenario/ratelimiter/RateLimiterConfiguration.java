package org.jobrunr.performance.scenario.ratelimiter;

import org.jobrunr.storage.JobRunrMetadata;
import org.jobrunr.utils.reflection.ReflectionUtils;

import static org.jobrunr.performance.scenario.ratelimiter.BackwardsCompatibleMethodInvocation.backwardsCompatibleMethod;

public interface RateLimiterConfiguration {

    static JobRunrMetadata concurrentJobRateLimiter(String rateLimiterName, Integer amount) {
        Object concurrentJobRateLimiter = backwardsCompatibleMethod(
                "org.jobrunr.server.tasks.zookeeper.ratelimiters.ConcurrentJobRateLimiterConfiguration",
                "org.jobrunr.jobs.ratelimiters.ConcurrentJobRateLimiterConfiguration"
        ).invoke("concurrentJobRateLimiter", rateLimiterName, amount);
        return toMetadata(concurrentJobRateLimiter);
    }

    static JobRunrMetadata toMetadata(Object object) {
        try {
            Class<?> interfaceClass = object.getClass().getInterfaces()[0];
            return (JobRunrMetadata) ReflectionUtils.getMethod(interfaceClass, "toMetadata").invoke(object);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
