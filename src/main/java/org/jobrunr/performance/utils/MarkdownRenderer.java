package org.jobrunr.performance.utils;

import org.jobrunr.performance.scenario.ScenarioResult;
import org.jobrunr.performance.storage.StorageProviderQueryAnalysis;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.storage.TimedStorageProvider.MethodSummaryStatistics;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.jobrunr.performance.utils.StringUtils.camelCaseToHumanReadable;

public class MarkdownRenderer {

    public static String render(BackgroundJobServer backgroundJobServer, ScenarioResult scenarioResult, List<MethodSummaryStatistics> methodSummaryStatistics, Collection<StorageProviderQueryAnalysis> queryAnalyses) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# JobRunr Scenario ").append(camelCaseToHumanReadable(scenarioResult.getScenario())).append(System.lineSeparator());
        markdown.append(System.lineSeparator());
        markdown.append("- Timestamp : ").append(scenarioResult.getTimestamp()).append(System.lineSeparator());
        markdown.append("- Java Version : ").append(JobRunrUtils.getJavaVersion()).append(System.lineSeparator());
        markdown.append("- JobRunr Type : ").append(JobRunrUtils.getJobRunrType(backgroundJobServer)).append(System.lineSeparator());
        markdown.append("- JobRunr Version  : ").append(JobRunrUtils.getJobRunrVersion(backgroundJobServer)).append(System.lineSeparator());
        markdown.append("- Storage Provider : ").append(backgroundJobServer.getStorageProvider().getStorageProviderInfo().getName()).append(System.lineSeparator());
        markdown.append("- Total time : ").append(scenarioResult.getProcessingDuration()).append(System.lineSeparator());
        markdown.append(System.lineSeparator()).append(System.lineSeparator());

        markdown.append("## Method summary: ").append(System.lineSeparator());
        methodSummaryStatistics.forEach(mss -> {
            markdown.append("### ").append(mss.getMethodName()).append(System.lineSeparator());
            markdown.append("#### Timings").append(System.lineSeparator());
            markdown.append("  - invocations: ").append(mss.getCount()).append(System.lineSeparator());
            markdown.append("  - total duration: ").append(Duration.ofNanos(mss.getSum())).append(System.lineSeparator());
            markdown.append("  - avg duration: ").append(Duration.ofNanos((long) mss.getAverage())).append(System.lineSeparator());
            markdown.append("  - min duration: ").append(Duration.ofNanos(mss.getMin())).append(System.lineSeparator());
            markdown.append("  - max duration: ").append(Duration.ofNanos(mss.getMax())).append(System.lineSeparator());

            List<StorageProviderQueryAnalysis> queries = queryAnalyses.stream().filter(qa -> qa.getStorageProviderMethodName().equals(mss.getMethodName())).toList();
            if (!queries.isEmpty()) {
                markdown.append("#### Queries").append(System.lineSeparator());
                for (int i = 0; i < queries.size(); i++) {
                    StorageProviderQueryAnalysis qa = queries.get(i);
                    markdown.append("##### Query ").append(i + 1).append(System.lineSeparator());
                    markdown.append(" -  Query identifier: `").append(qa.getQuery().getQueryIdentifier()).append("`").append(System.lineSeparator());
                    markdown.append(" - Query with values: `").append(qa.getQuery().getQueryWithValues()).append("`").append(System.lineSeparator());
                    TreeMap<Double, String> sortedMap = new TreeMap<>(qa.getAnalysisAtPercentage());
                    for (Map.Entry<Double, String> entrySet : sortedMap.entrySet()) {
                        markdown.append("> At percentage: ").append(entrySet.getKey()).append(System.lineSeparator());
                        markdown.append(System.lineSeparator()).append("```").append(System.lineSeparator()).append(entrySet.getValue()).append("```").append(System.lineSeparator());
                    }
                    markdown.append(System.lineSeparator());
                }
            }

        });
        System.out.println(markdown.toString());
        return markdown.toString();
    }
}
