package org.jobrunr.performance.storage;

import org.jobrunr.storage.TimedStorageProvider;

import java.util.List;

public interface AnalysingDataStore {

    String explainQuery(TimedStorageProvider.Query query);

    List<IndexDetails> getIndexDetails();

    record IndexDetails(String table, String indexName, List<String> columnNames) {
    }
}
