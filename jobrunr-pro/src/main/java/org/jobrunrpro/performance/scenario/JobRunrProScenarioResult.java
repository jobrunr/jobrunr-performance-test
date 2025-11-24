package org.jobrunrpro.performance.scenario;

import org.jobrunr.storage.ThreadSafeStorageProvider.MethodStatistics;
import org.performance.scenario.Scenario;
import org.performance.scenario.ScenarioResult;

import java.util.List;

public class JobRunrProScenarioResult extends ScenarioResult {

    private List<MethodStatistics> methodStatistics;

    public JobRunrProScenarioResult(Scenario scenario) {
        super(scenario);
    }

    public List<MethodStatistics> getMethodStatistics() {
        return methodStatistics;
    }

    public void setMethodStatistics(List<MethodStatistics> methodSummaryStatistics) {
        this.methodStatistics = methodSummaryStatistics;
    }

}
