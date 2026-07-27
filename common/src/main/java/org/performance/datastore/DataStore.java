package org.performance.datastore;

import org.performance.datastore.nosql.MongoDBDataStore;
import org.performance.datastore.sql.DB2DataStore;
import org.performance.datastore.sql.MariaDBDataStore;
import org.performance.datastore.sql.MariaDBExternalDataStore;
import org.performance.datastore.sql.MySQLDataStore;
import org.performance.datastore.sql.MySQLExternalDataStore;
import org.performance.datastore.sql.OracleDataStore;
import org.performance.datastore.sql.PostgresDataStore;
import org.performance.datastore.sql.SQLServerDataStore;

import java.lang.reflect.Constructor;

public interface DataStore {

    static DataStore loadDataStore(DataStoreType dataStore) {
        try {
            Constructor<? extends DataStore> constructor = dataStore.clazz.getDeclaredConstructor();
            return constructor.newInstance();
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Unable to load data store: " + dataStore.name(), e);
        }
    }

    String getNameAndVersion();

    void start();

    void stop();

    default void updateStatistics() {
    }

    enum DataStoreType {
        DB2DataStore(DB2DataStore.class),
        MariaDBDataStore(MariaDBDataStore.class),
        MariaDBExternalDataStore(MariaDBExternalDataStore.class),
        MongoDBDataStore(MongoDBDataStore.class),
        MySQLDataStore(MySQLDataStore.class),
        MySQLExternalDataStore(MySQLExternalDataStore.class),
        OracleDataStore(OracleDataStore.class),
        PostgresDataStore(PostgresDataStore.class),
        SQLServerDataStore(SQLServerDataStore.class);

        private final Class<? extends DataStore> clazz;

        DataStoreType(Class<? extends DataStore> clazz) {
            this.clazz = clazz;
        }

        public static DataStoreType[] all() {
            return DataStoreType.values();
        }

        public static DataStoreType[] allButSlow() {
            return new DataStoreType[]{MariaDBDataStore, MongoDBDataStore, MySQLDataStore, OracleDataStore, PostgresDataStore, SQLServerDataStore};
        }
    }
}
