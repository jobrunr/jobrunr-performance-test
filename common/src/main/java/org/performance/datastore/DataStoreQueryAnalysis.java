package org.performance.datastore;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DataStoreQueryAnalysis {

    private final String storageProviderMethodName;

    private final DataStoreQuery query;

    private final List<QueryAnalysisAtPercentage> analysisAtPercentage;

    public DataStoreQueryAnalysis(String storageProviderMethodName, DataStoreQuery query) {
        this.storageProviderMethodName = storageProviderMethodName;
        this.query = query;
        this.analysisAtPercentage = new ArrayList<>();
    }

    public String getStorageProviderMethodName() {
        return storageProviderMethodName;
    }

    public DataStoreQuery getQuery() {
        return query;
    }

    public void addAnalysisAtPercentage(Double percentage, long invocationCount, String analysis) {
        analysisAtPercentage.add(new QueryAnalysisAtPercentage(percentage, invocationCount, analysis));
    }

    public List<QueryAnalysisAtPercentage> getAnalysisAtPercentage() {
        return analysisAtPercentage;
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || getClass() != object.getClass()) return false;
        DataStoreQueryAnalysis that = (DataStoreQueryAnalysis) object;
        return Objects.equals(query, that.query);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(query);
    }

    @Override
    public String toString() {
        return "StorageProviderQueryAnalysis{" +
                "storageProviderMethodName='" + storageProviderMethodName + '\'' +
                ", query=" + query +
                ", analysisAtPercentage=" + analysisAtPercentage +
                '}';
    }

    public record QueryAnalysisAtPercentage(double percentage, long invocationCount, String analysis) {
    }
}
