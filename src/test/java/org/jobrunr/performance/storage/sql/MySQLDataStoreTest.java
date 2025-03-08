package org.jobrunr.performance.storage.sql;

import org.jobrunr.performance.storage.AnalysingDataStore.IndexDetails;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

class MySQLDataStoreTest {

    @Test
    void shouldGetIndexDetails() {
        MySQLDataStore dataStore = new MySQLDataStore();
        dataStore.start();
        StorageProvider storageProvider = SqlStorageProviderFactory.using(dataStore.getDataSource());
        List<IndexDetails> indexDetails = dataStore.getIndexDetails();
        indexDetails.forEach(System.out::println);
        dataStore.stop();
    }

}