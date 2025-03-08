package org.jobrunr.performance.storage.sql;

import org.jobrunr.performance.storage.AnalysingDataStore;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

class PostgresDataStoreTest {

    @Test
    void shouldGetIndexDetails() {
        PostgresDataStore dataStore = new PostgresDataStore();
        dataStore.start();
        StorageProvider storageProvider = SqlStorageProviderFactory.using(dataStore.getDataSource());
        List<AnalysingDataStore.IndexDetails> indexDetails = dataStore.getIndexDetails();
        indexDetails.forEach(System.out::println);
        dataStore.stop();
    }
}