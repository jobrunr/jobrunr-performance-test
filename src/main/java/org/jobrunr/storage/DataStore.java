package org.jobrunr.storage;

import org.jobrunr.utils.StringUtils;
import org.jobrunr.utils.reflection.ReflectionUtils;

public interface DataStore {

    void start();

    void stop();

    StorageProvider getStorageProvider();

    default void updateStatistics() {
    }

    static DataStore loadDataStore(String name) {
        if (StringUtils.isNullOrEmpty(name)) throw new IllegalArgumentException("DataStore name must not be null or empty");
        String packageName = StringUtils.substringBeforeLast(DataStore.class.getName(), ".");
        String fullyQualifiedClassName = packageName + "." + name;
        return ReflectionUtils.newInstance(fullyQualifiedClassName);
    }
}
