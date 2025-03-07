package org.jobrunr.performance.storage;

import org.jobrunr.storage.TimedStorageProvider.Query;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class StorageProviderQueryAnalysis {

    private final String storageProviderMethodName;

    private final Query query;

    private final Map<Double, String> analysisAtPercentage;

    public StorageProviderQueryAnalysis(String storageProviderMethodName, Query query) {
        this.storageProviderMethodName = storageProviderMethodName;
        this.query = query;
        this.analysisAtPercentage = new HashMap<>();
    }

    public String getStorageProviderMethodName() {
        return storageProviderMethodName;
    }

    public Query getQuery() {
        return query;
    }

    public void addAnalysisAtPercentage(Double percentage, String analysis) {
        analysisAtPercentage.put(percentage, analysis);
    }

    public Map<Double, String> getAnalysisAtPercentage() {
        return analysisAtPercentage;
    }

    @Override
    public boolean equals(Object object) {
        if (object == null || getClass() != object.getClass()) return false;
        StorageProviderQueryAnalysis that = (StorageProviderQueryAnalysis) object;
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
}
