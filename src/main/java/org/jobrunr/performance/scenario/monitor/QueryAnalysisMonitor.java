package org.jobrunr.performance.scenario.monitor;

import org.jobrunr.performance.storage.AnalysingDataStore;
import org.jobrunr.performance.storage.StorageProviderQueryAnalysis;
import org.jobrunr.storage.JobStats;
import org.jobrunr.storage.ThreadSafeStorageProvider;
import org.jobrunr.storage.ThreadSafeStorageProvider.MethodStatistics;
import org.jobrunr.storage.listeners.JobStatsChangeListener;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jobrunr.storage.ThreadSafeStorageProvider.Query;

public class QueryAnalysisMonitor implements JobStatsChangeListener {

    private final AnalysingDataStore analysingDataStore;
    private final Instant startTime;
    private final Duration maxScenarioDuration;
    private final List<Double> explainAnalysePercentages;
    private final Map<Query, StorageProviderQueryAnalysis> queryAnalyses;
    private Double currentPercentage;

    public QueryAnalysisMonitor(AnalysingDataStore analysingDataStore, Instant startTime, Duration maxScenarioDuration, Double... explainAnalysePercentages) {
        this(analysingDataStore, startTime, maxScenarioDuration, Arrays.asList(explainAnalysePercentages));
    }

    public QueryAnalysisMonitor(AnalysingDataStore analysingDataStore, Instant startTime, Duration maxScenarioDuration, List<Double> explainAnalysePercentages) {
        this.analysingDataStore = analysingDataStore;
        this.startTime = startTime;
        this.maxScenarioDuration = maxScenarioDuration;
        this.explainAnalysePercentages = new ArrayList<>(explainAnalysePercentages);
        this.queryAnalyses = new HashMap<>(explainAnalysePercentages.size());
        this.currentPercentage = this.explainAnalysePercentages.remove(0);
    }

    public List<MethodStatistics> getMethodStatistics() {
        return ThreadSafeStorageProvider.getMethodStatistics();
    }

    public Collection<StorageProviderQueryAnalysis> getQueryAnalyses() {
        return queryAnalyses.values();
    }

    @Override
    public synchronized void onChange(JobStats jobStats) {
        double actualPercentage = getActualPercentage(jobStats);
        if (currentPercentage != null && actualPercentage >= currentPercentage) {
            List<MethodStatistics> methodSummaryStatistics = getMethodStatistics().subList(0, 10);
            for (MethodStatistics summaryStatistics : methodSummaryStatistics) {
                summaryStatistics.getQueries().keySet().forEach(q -> getSummaryStatisticsForQuery(summaryStatistics.getMethodIdentifier(), summaryStatistics.getCount(), q));
            }
            if (explainAnalysePercentages.isEmpty()) {
                currentPercentage = null;
            } else {
                currentPercentage = explainAnalysePercentages.remove(0);
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
            StorageProviderQueryAnalysis storageProviderQueryAnalysis = queryAnalyses.computeIfAbsent(query, k -> new StorageProviderQueryAnalysis(storageProviderMethodNameAndArgs, query));
            storageProviderQueryAnalysis.addAnalysisAtPercentage(currentPercentage, invocationCount, analysingDataStore.explainQuery(query));
        } catch (Exception e) {
            System.out.println(e.getMessage());
        }
    }
}
