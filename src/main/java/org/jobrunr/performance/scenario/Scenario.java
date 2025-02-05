package org.jobrunr.performance.scenario;

import org.jobrunr.storage.DataStore;
import org.jobrunr.utils.StringUtils;
import org.jobrunr.utils.reflection.ReflectionUtils;

public interface Scenario {

    void run();

    static Scenario loadScenario(String name, DataStore dataStore, String[] args) {
        if (StringUtils.isNullOrEmpty(name)) throw new IllegalArgumentException("Scenario name must not be null or empty");
        String packageName = StringUtils.substringBeforeLast(Scenario.class.getName(), ".");
        String fullyQualifiedClassName = packageName + "." + name;
        return ReflectionUtils.newInstance(fullyQualifiedClassName, dataStore, args);
    }
}
