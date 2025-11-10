package org.performance.scenario;


import org.performance.datastore.DataStore;

import java.lang.reflect.Constructor;

import static org.performance.utils.StringUtils.isNullOrEmpty;
import static org.performance.utils.StringUtils.substringBeforeLast;


public interface Scenario {

    void run() throws Exception;

    static Scenario loadScenario(String tool, String scenarioName, DataStore dataStore, String[] args) {
        if (isNullOrEmpty(scenarioName)) throw new IllegalArgumentException("Scenario name must not be null or empty");
        String packageName = substringBeforeLast(Scenario.class.getName(), ".");
        String fullyQualifiedClassName = (packageName + "." + scenarioName).replace("org.", "org." + tool + ".");
        try {
            Class<?> clazz = Class.forName(fullyQualifiedClassName);
            Constructor<?> constructor = clazz.getDeclaredConstructor(DataStore.class, String[].class);
            return (Scenario) constructor.newInstance(dataStore, args);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Unable to load scenario class: " + fullyQualifiedClassName, e);
        }
    }
}
