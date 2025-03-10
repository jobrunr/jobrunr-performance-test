package org.jobrunr.performance.storage;


import java.util.List;

import static org.jobrunr.storage.ThreadSafeStorageProvider.Query;

public interface AnalysingDataStore {

    String explainQuery(Query query);

    List<IndexDetails> getIndexDetails();

    record IndexDetails(String table, String indexName, List<String> columnNames) {
    }
}
