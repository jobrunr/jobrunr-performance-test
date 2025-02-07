package org.jobrunr.performance.scenario;

import java.time.Duration;

public class ScenarioResult {

    private final String scenarioName;
    private long amountOfJobs;
    private Duration creationDuration, processingDuration;

    public ScenarioResult(String scenarioName) {
        this.scenarioName = scenarioName;
    }

    public void setAmountOfJobsCreated(long amountOfJobs, Duration creationDuration) {
        this.amountOfJobs = amountOfJobs;
        this.creationDuration = creationDuration;
    }
    
    public void setProcessingDuration(Duration processingDuration) {
        this.processingDuration = processingDuration;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public long getAmountOfJobs() {
        return amountOfJobs;
    }

    public Duration getCreationDuration() {
        return creationDuration;
    }

    public Duration getProcessingDuration() {
        return processingDuration;
    }
}
