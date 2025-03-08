package org.jobrunr.performance.scenario;

import org.jobrunr.performance.storage.StorageProviderQueryAnalysis;
import org.jobrunr.storage.TimedStorageProvider;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

public class ScenarioResult {

    private final Scenario scenario;
    private final Instant timestamp;
    private long createdJobs;
    private long succeededJobs;
    private Duration creationDuration, processingDuration;
    private List<TimedStorageProvider.MethodSummaryStatistics> methodSummaryStatistics;
    private Collection<StorageProviderQueryAnalysis> queryAnalyses;

    public ScenarioResult(Scenario scenario) {
        this.scenario = scenario;
        this.timestamp = Instant.now();
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setAmountOfJobsCreated(long amountOfJobs, Duration creationDuration) {
        this.createdJobs = amountOfJobs;
        this.creationDuration = creationDuration;
    }

    public void setProcessingDuration(Duration processingDuration) {
        this.processingDuration = processingDuration;
    }

    public Scenario getScenario() {
        return scenario;
    }

    public long getCreatedJobs() {
        return createdJobs;
    }

    public Duration getCreationDuration() {
        return creationDuration;
    }

    public Duration getProcessingDuration() {
        return processingDuration;
    }

    public void setSucceededJobs(Long succeededJobs) {
        this.succeededJobs = succeededJobs;
    }

    public long getSucceededJobs() {
        return succeededJobs;
    }

    public List<TimedStorageProvider.MethodSummaryStatistics> getMethodSummaryStatistics() {
        return methodSummaryStatistics;
    }

    public void setMethodSummaryStatistics(List<TimedStorageProvider.MethodSummaryStatistics> methodSummaryStatistics) {
        this.methodSummaryStatistics = methodSummaryStatistics;
    }

    public Collection<StorageProviderQueryAnalysis> getQueryAnalyses() {
        return queryAnalyses;
    }

    public void setQueryAnalyses(Collection<StorageProviderQueryAnalysis> queryAnalyses) {
        this.queryAnalyses = queryAnalyses;
    }
}
