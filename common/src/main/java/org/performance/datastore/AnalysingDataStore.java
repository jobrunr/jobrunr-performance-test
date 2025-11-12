package org.performance.datastore;


import org.performance.datastore.DataStoreQueryAnalysis.QueryAnalysisAtPercentage;
import org.performance.scenario.ScenarioResult;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public interface AnalysingDataStore {

    default String getDataStoreSettings() {
        return "";
    }

    String explainQuery(DataStoreQuery query);

    List<IndexDetails> getIndexDetails();

    record IndexDetails(String table, String indexName, List<String> columnNames, String usageDetails) {
    }

    default List<IndexUsage> getIndexUsage(ScenarioResult scenarioResult, String indexName) {
        List<IndexUsage> indexUsage = new ArrayList<>();
        Collection<DataStoreQueryAnalysis> queryAnalyses = scenarioResult.getQueryAnalyses();
        for (DataStoreQueryAnalysis queryAnalysis : queryAnalyses) {
            for (QueryAnalysisAtPercentage analysisAtPercentage : queryAnalysis.getAnalysisAtPercentage()) {
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
