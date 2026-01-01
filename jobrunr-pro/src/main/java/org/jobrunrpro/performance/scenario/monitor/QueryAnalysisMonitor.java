package org.jobrunrpro.performance.scenario.monitor;

import org.jobrunr.storage.JobStats;
import org.jobrunr.storage.ThreadSafeStorageProvider;
import org.jobrunr.storage.ThreadSafeStorageProvider.MethodStatistics;
import org.jobrunr.storage.listeners.JobStatsChangeListener;
import org.performance.datastore.AnalysingDataStore;
import org.performance.datastore.DataStoreQuery;
import org.performance.datastore.DataStoreQueryAnalysis;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;

import static org.jobrunr.storage.ThreadSafeStorageProvider.Query;

public class QueryAnalysisMonitor implements JobStatsChangeListener {

    private final AnalysingDataStore analysingDataStore;
    private final Instant startTime;
    private final Duration maxScenarioDuration;
    private final List<Double> explainAnalysePercentages;
    private final Map<String, Map<Query, DataStoreQueryAnalysis>> dataStoreQueryAnalyses;
    private Double currentPercentage;

    public QueryAnalysisMonitor(AnalysingDataStore analysingDataStore, Instant startTime, Duration maxScenarioDuration, Double... explainAnalysePercentages) {
        this(analysingDataStore, startTime, maxScenarioDuration, Arrays.asList(explainAnalysePercentages));
    }

    public QueryAnalysisMonitor(AnalysingDataStore analysingDataStore, Instant startTime, Duration maxScenarioDuration, List<Double> explainAnalysePercentages) {
        this.analysingDataStore = analysingDataStore;
        this.startTime = startTime;
        this.maxScenarioDuration = maxScenarioDuration;
        this.explainAnalysePercentages = new ArrayList<>(explainAnalysePercentages);
        this.dataStoreQueryAnalyses = new HashMap<>();
        this.currentPercentage = this.explainAnalysePercentages.removeFirst();
    }

    public List<MethodStatistics> getMethodStatistics() {
        return ThreadSafeStorageProvider.getMethodStatistics();
    }

    public Collection<DataStoreQueryAnalysis> getQueryAnalyses() {
        return dataStoreQueryAnalyses.values().stream()
                .flatMap(m -> m.values().stream())
                .toList();
    }

    @Override
    public synchronized void onChange(JobStats jobStats) {
        double actualPercentage = getActualPercentage(jobStats);
        if (currentPercentage != null && actualPercentage >= currentPercentage) {
            List<MethodStatistics> allMethodStatistics = getMethodStatistics();
            for (MethodStatistics methodStatistics : allMethodStatistics) {
                methodStatistics.getQueries().forEach((queryIdentifier, q) -> getSummaryStatisticsForQuery(methodStatistics.getMethodIdentifier(), methodStatistics.getCount(), q));
            }
            if (explainAnalysePercentages.isEmpty()) {
                currentPercentage = null;
            } else {
                currentPercentage = explainAnalysePercentages.removeFirst();
            }
        }
    }

    private double getActualPercentage(JobStats jobStats) {
        double actualPercentageComplete = (double) jobStats.getSucceeded() / jobStats.getTotal();
        double actualTimePercentageComplete = (double) Duration.between(startTime, Instant.now()).toSeconds() / maxScenarioDuration.toSeconds();
        return Math.max(actualPercentageComplete, actualTimePercentageComplete);
    }

    private void getSummaryStatisticsForQuery(String storageProviderMethodNameAndArgs, long invocationCount, Query query) {
        try {
            JobRunrProDataStoreQuery dataStoreQuery = new JobRunrProDataStoreQuery(query);
            Map<Query, DataStoreQueryAnalysis> queryAnalyses = dataStoreQueryAnalyses.computeIfAbsent(storageProviderMethodNameAndArgs, k -> new HashMap<>());
            DataStoreQueryAnalysis dataStoreQueryAnalysis = queryAnalyses.computeIfAbsent(query, k -> new DataStoreQueryAnalysis(storageProviderMethodNameAndArgs, dataStoreQuery));
            dataStoreQueryAnalysis.addAnalysisAtPercentage(currentPercentage, invocationCount, analysingDataStore.explainQuery(dataStoreQuery));
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }

    public static class JobRunrProDataStoreQuery implements DataStoreQuery {

        private final Query query;

        public JobRunrProDataStoreQuery(Query query) {
            this.query = query;
        }

        @Override
        public String getQueryIdentifier() {
            return query.getQueryIdentifier();
        }

        @Override
        public String getQueryWithValues() {
            return query.getQueryWithValues();
        }

        public HashMap<String, LongSummaryStatistics> getQueryTimings() {
            return query.getQueryTimings();
        }
    }
}
