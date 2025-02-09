package org.jobrunr.performance.storage;

import org.jobrunr.storage.StorageProvider;
import org.jobrunr.utils.reflection.ReflectionUtils;

import java.time.Instant;

import static org.jobrunr.performance.utils.StringUtils.isNullOrEmpty;
import static org.jobrunr.performance.utils.StringUtils.substringBeforeLast;

public interface DataStore {

    void start();

    void stop();

    StorageProvider getStorageProvider(boolean logQueries);

    Instant getUpdatedAtOfLastSucceededJob();

    default void updateStatistics() {
    }

    static DataStore loadDataStore(String name) {
        if (isNullOrEmpty(name)) throw new IllegalArgumentException("DataStore name must not be null or empty");
        String packageName = substringBeforeLast(DataStore.class.getName(), ".");
        String fullyQualifiedClassName = packageName + "." + name;
        return ReflectionUtils.newInstance(fullyQualifiedClassName);
    }

    static DataStore loadDataStore(DataStoreType dataStore) {
        return loadDataStore(dataStore.name());
    }


    enum DataStoreType {
        DB2DataStore,
        MariaDBDataStore,
        MongoDBDataStore,
        MySQLDataStore,
        OracleDataStore,
        PostgresDataStore,
        SQLServerDataStore;

        public static DataStoreType[] all() {
            return DataStoreType.values();
        }

        public static DataStoreType[] allButSlow() {
            return new DataStoreType[]{MariaDBDataStore, MongoDBDataStore, MySQLDataStore, OracleDataStore, PostgresDataStore, SQLServerDataStore};
        }
    }
}
