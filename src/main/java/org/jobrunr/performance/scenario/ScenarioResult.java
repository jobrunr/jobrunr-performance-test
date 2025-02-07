package org.jobrunr.performance.scenario;

import java.time.Duration;

public class ScenarioResult {

    private final Scenario scenario;
    private long amountOfJobs;
    private Duration creationDuration, processingDuration;

    public ScenarioResult(Scenario scenario) {
        this.scenario = scenario;
    }

    public void setAmountOfJobsCreated(long amountOfJobs, Duration creationDuration) {
        this.amountOfJobs = amountOfJobs;
        this.creationDuration = creationDuration;
    }

    public void setProcessingDuration(Duration processingDuration) {
        this.processingDuration = processingDuration;
    }

    public Scenario getScenario() {
        return scenario;
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
