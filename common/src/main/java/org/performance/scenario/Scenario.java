package org.performance.scenario;


import org.performance.datastore.DataStore;

import java.lang.reflect.Constructor;

import static org.performance.utils.StringUtils.isNullOrEmpty;
import static org.performance.utils.StringUtils.substringBeforeLast;


public interface Scenario {

    void run() throws Exception;

    static Scenario loadScenario(String name, DataStore dataStore, String[] args) {
        if (isNullOrEmpty(name)) throw new IllegalArgumentException("Scenario name must not be null or empty");
        String packageName = substringBeforeLast(Scenario.class.getName(), ".");
        String fullyQualifiedClassName = packageName + "." + name;
        // FIX ME
        fullyQualifiedClassName = "org.quartz.performance.scenario.Scenario01ProcessJobs";
        try {
            Class<?> clazz = Class.forName(fullyQualifiedClassName);
            Constructor<?> constructor = clazz.getDeclaredConstructor(DataStore.class, String[].class);
            return (Scenario) constructor.newInstance(dataStore, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Unable to load scenario class: " + fullyQualifiedClassName, e);
        }
    }
}
