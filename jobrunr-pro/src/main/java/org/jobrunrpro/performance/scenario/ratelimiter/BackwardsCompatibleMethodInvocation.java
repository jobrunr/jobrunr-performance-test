package org.jobrunrpro.performance.scenario.ratelimiter;

import org.jobrunr.configuration.JobRunrPro;
import org.jobrunr.utils.reflection.ReflectionUtils;
import org.performance.utils.JarUtils;

import java.lang.reflect.Method;
import java.util.Arrays;

import static org.jobrunr.utils.reflection.ReflectionUtils.findMethod;

public class BackwardsCompatibleMethodInvocation {

    private final String classV7;
    private final String lastest;

    private BackwardsCompatibleMethodInvocation(String classV7, String latest) {
        this.classV7 = classV7;
        this.lastest = latest;
    }

    public <T> T invoke(String methodName, Object... args) {
        try {
            String version = JarUtils.getVersion(JobRunrPro.class);
            Class<?> rateLimiterClass;
            if (version.startsWith("7")) {
                rateLimiterClass = ReflectionUtils.loadClass(classV7);
            } else {
                rateLimiterClass = ReflectionUtils.loadClass(lastest);
            }
            Method method = findMethod(rateLimiterClass, methodName, Arrays.stream(args).map(Object::getClass).toArray(Class<?>[]::new)).orElseThrow(() -> new ReflectiveOperationException("Could not find method " + methodName));
            method.setAccessible(true);
            return (T) method.invoke(rateLimiterClass, args);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }

    public static BackwardsCompatibleMethodInvocation backwardsCompatibleMethod(String classV7, String latest) {
        return new BackwardsCompatibleMethodInvocation(classV7, latest);
    }
}
