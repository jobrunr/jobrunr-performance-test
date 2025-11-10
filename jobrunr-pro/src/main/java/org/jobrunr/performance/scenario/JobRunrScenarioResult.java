package org.jobrunr.performance.scenario;

import org.jobrunr.storage.ThreadSafeStorageProvider.MethodStatistics;
import org.performance.scenario.Scenario;
import org.performance.scenario.ScenarioResult;

import java.util.List;

public class JobRunrScenarioResult extends ScenarioResult {

    private List<MethodStatistics> methodStatistics;

    public JobRunrScenarioResult(Scenario scenario) {
        super(scenario);
    }

    public List<MethodStatistics> getMethodStatistics() {
        return methodStatistics;
    }

    public void setMethodStatistics(List<MethodStatistics> methodSummaryStatistics) {
        this.methodStatistics = methodSummaryStatistics;
    }

}
