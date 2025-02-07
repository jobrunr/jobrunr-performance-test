package org.jobrunr.performance.scenario;

import org.jobrunr.performance.storage.DataStore;
import org.jobrunr.utils.reflection.ReflectionUtils;

import static org.jobrunr.performance.utils.StringUtils.isNullOrEmpty;
import static org.jobrunr.performance.utils.StringUtils.substringBeforeLast;

public interface Scenario {

    void run();

    static Scenario loadScenario(String name, DataStore dataStore, String[] args) {
        if (isNullOrEmpty(name)) throw new IllegalArgumentException("Scenario name must not be null or empty");
        String packageName = substringBeforeLast(Scenario.class.getName(), ".");
        String fullyQualifiedClassName = packageName + "." + name;
        return ReflectionUtils.newInstance(fullyQualifiedClassName, dataStore, args);
    }
}
