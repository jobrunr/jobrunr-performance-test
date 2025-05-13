package org.jobrunr.performance.util;

import org.jobrunr.performance.JobRunrDistribution;
import org.jobrunr.utils.reflection.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.jobrunr.utils.reflection.ReflectionUtils.findMethod;

public class BackwardsCompatibleMethodInvocation {

    private final String classV7;
    private final String classV1;

    private BackwardsCompatibleMethodInvocation(String classV7, String classV1) {
        this.classV7 = classV7;
        this.classV1 = classV1;
    }

    public <T> T invoke(String methodName, Object... args) {
        try {
            String version = JobRunrDistribution.current.getVersion();
            Class<?> rateLimiterClass;
            if (version.startsWith("1")) {
                rateLimiterClass = ReflectionUtils.loadClass(classV1);
            } else {
                rateLimiterClass = ReflectionUtils.loadClass(classV7);
            }
            Method method = findMethod(rateLimiterClass, methodName, Arrays.stream(args).map(Object::getClass).toArray(Class<?>[]::new)).orElseThrow(() -> new ReflectiveOperationException("Could not find method " + methodName));
            method.setAccessible(true);
            return (T) method.invoke(rateLimiterClass, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static BackwardsCompatibleMethodInvocation backwardsCompatibleMethod(String classV7, String classV1) {
        return new BackwardsCompatibleMethodInvocation(classV7, classV1);
    }
}
