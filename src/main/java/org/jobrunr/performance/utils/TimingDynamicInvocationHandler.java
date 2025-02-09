package org.jobrunr.performance.utils;

import org.jobrunr.storage.StorageProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.util.LongSummaryStatistics;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TimingDynamicInvocationHandler implements InvocationHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(TimingDynamicInvocationHandler.class);

    private final StorageProvider storageProvider;

    private final ConcurrentHashMap<String, LongSummaryStatistics> methodSummary = new ConcurrentHashMap<>();

    public TimingDynamicInvocationHandler(StorageProvider storageProvider) {
        this.storageProvider = storageProvider;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        long start = System.nanoTime();
        Object result = method.invoke(storageProvider, args);
        long elapsed = System.nanoTime() - start;
        methodSummary.compute(getMethodSignature(method), (k, s) -> timed(k, s, elapsed));
        return result;
    }

    public ConcurrentHashMap<String, LongSummaryStatistics> getMethodSummary() {
        return methodSummary;
    }

    private LongSummaryStatistics timed(String k, LongSummaryStatistics statistics, long elapsed) {
        if (statistics == null) return new LongSummaryStatistics(1, elapsed, elapsed, elapsed);
        statistics.accept(elapsed);
        return statistics;
    }

    private String getMethodSignature(Method method) {
        return method.getDeclaringClass().getSimpleName() + "." + method.getName() + "(" +
                Stream.of(method.getParameterTypes()).map(Class::getSimpleName).collect(Collectors.joining(", "))
                + ")";
    }
}
