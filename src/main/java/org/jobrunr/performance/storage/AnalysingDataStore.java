package org.jobrunr.performance.storage;


import org.jobrunr.performance.scenario.ScenarioResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.jobrunr.storage.ThreadSafeStorageProvider.Query;

public interface AnalysingDataStore {

    default String getDataStoreSettings() {
        return "";
    }

    String explainQuery(Query query);

    List<IndexDetails> getIndexDetails();

    record IndexDetails(String table, String indexName, List<String> columnNames, String usageDetails) {
    }

    default List<IndexUsage> getIndexUsage(ScenarioResult scenarioResult, String indexName) {
        List<IndexUsage> indexUsage = new ArrayList<>();
        Collection<StorageProviderQueryAnalysis> queryAnalyses = scenarioResult.getQueryAnalyses();
        for (StorageProviderQueryAnalysis queryAnalysis : queryAnalyses) {
            for (StorageProviderQueryAnalysis.QueryAnalysisAtPercentage analysisAtPercentage : queryAnalysis.getAnalysisAtPercentage()) {
                if (isQueryUsingIndex(analysisAtPercentage.getAnalysis(), indexName)) {
                    indexUsage.add(new IndexUsage(indexName, queryAnalysis.getStorageProviderMethodName(), queryAnalysis.getQuery().getQueryIdentifier()));
                    break;
                }
            }
        }
        return indexUsage;
    }

    default boolean isQueryUsingIndex(String analysis, String indexName) {
        return analysis.contains(indexName);
    }

    record IndexUsage(String indexName, String storageProviderMethodName, String queryIdentifier) {
    }
}
