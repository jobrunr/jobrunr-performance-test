package org.jobrunr.performance.storage;


import org.jobrunr.storage.ThreadSafeStorageProvider.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class StorageProviderQueryAnalysis {

    private final String storageProviderMethodName;

    private final Query query;

    private final List<QueryAnalysisAtPercentage> analysisAtPercentage;

    public StorageProviderQueryAnalysis(String storageProviderMethodName, Query query) {
        this.storageProviderMethodName = storageProviderMethodName;
        this.query = query;
        this.analysisAtPercentage = new ArrayList<>();
    }

    public String getStorageProviderMethodName() {
        return storageProviderMethodName;
    }

    public Query getQuery() {
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

    public static class QueryAnalysisAtPercentage {
        private final double percentage;
        private final long invocationCount;
        private final String analysis;

        public QueryAnalysisAtPercentage(double percentage, long invocationCount, String analysis) {
            this.percentage = percentage;
            this.invocationCount = invocationCount;
            this.analysis = analysis;
        }

        public double getPercentage() {
            return percentage;
        }

        public long getInvocationCount() {
            return invocationCount;
        }

        public String getAnalysis() {
            return analysis;
        }
    }
}
