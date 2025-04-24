package org.jobrunr.performance.utils;

import org.jobrunr.performance.scenario.ScenarioResult;
import org.jobrunr.performance.storage.AnalysingDataStore;
import org.jobrunr.performance.storage.AnalysingDataStore.IndexDetails;
import org.jobrunr.performance.storage.AnalysingDataStore.IndexUsage;
import org.jobrunr.performance.storage.StorageProviderQueryAnalysis;
import org.jobrunr.performance.storage.StorageProviderQueryAnalysis.QueryAnalysisAtPercentage;
import org.jobrunr.server.BackgroundJobServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static org.jobrunr.performance.utils.ReportingUtils.findLogbooksFolder;
import static org.jobrunr.performance.utils.StringUtils.camelCaseToHumanReadable;

public class MarkdownReporter {

    protected static final Logger LOGGER = LoggerFactory.getLogger(MarkdownReporter.class);

    public static void render(BackgroundJobServer backgroundJobServer, AnalysingDataStore dataStore, ScenarioResult scenarioResult) {
        StringBuilder markdown = new StringBuilder();
        markdown.append("# JobRunr Scenario ").append(camelCaseToHumanReadable(scenarioResult.getScenario())).append(System.lineSeparator());
        markdown.append(System.lineSeparator());
        markdown.append("- Timestamp : ").append(scenarioResult.getTimestamp()).append(System.lineSeparator());
        markdown.append("- Java Version : ").append(JobRunrUtils.getJavaVersion()).append(System.lineSeparator());
        markdown.append("- JobRunr Type : ").append(JobRunrUtils.getJobRunrType(backgroundJobServer)).append(System.lineSeparator());
        markdown.append("- JobRunr Version  : ").append(JobRunrUtils.getJobRunrVersion(backgroundJobServer)).append(System.lineSeparator());
        markdown.append("- Storage Provider : ").append(backgroundJobServer.getStorageProvider().getStorageProviderInfo().getName()).append(System.lineSeparator());
        markdown.append("- Total time : ").append(scenarioResult.getProcessingDuration()).append(System.lineSeparator());
        markdown.append("- Jobs / sec : ").append(String.format("%.2f", (double) scenarioResult.getSucceededJobs() / scenarioResult.getProcessingDuration().toSeconds())).append(System.lineSeparator());
        markdown.append("- Jobs processed : ").append(scenarioResult.getSucceededJobs()).append(" / ").append(scenarioResult.getCreatedJobs()).append(" (").append((scenarioResult.getSucceededJobs() * 100) / scenarioResult.getCreatedJobs()).append("%)").append(System.lineSeparator());
        markdown.append(dataStore.getDataStoreSettings());
        markdown.append(System.lineSeparator()).append(System.lineSeparator());

        markdown.append("## DB Details & indexes: ").append(System.lineSeparator());
        Map<String, List<IndexDetails>> indexesByTable = dataStore.getIndexDetails().stream().collect(groupingBy(IndexDetails::table));
        indexesByTable.forEach((table, indexDetails) -> {
            markdown.append("#### Table ").append(table).append(System.lineSeparator());
            indexDetails.forEach(index -> {
                markdown.append("- index ").append(index.indexName()).append(" on ").append(index.table()).append(" using (").append(String.join(", ", index.columnNames())).append(")").append(System.lineSeparator());
                if (!(index.columnNames().stream().allMatch("id"::equals))) {
                    List<IndexUsage> indexUsages = dataStore.getIndexUsage(scenarioResult, index.indexName());
                    if (indexUsages.isEmpty()) {
                        markdown.append("  - ").append(index.usageDetails()).append(" (could not be found in query analysis)").append(System.lineSeparator());
                    } else {
                        markdown.append("  - ").append(index.usageDetails()).append(System.lineSeparator());
                        for (String indexUsageStrorageProviderMethodName : indexUsages.stream().map(IndexUsage::storageProviderMethodName).collect(Collectors.toSet())) {
                            markdown.append("  - by method ").append(indexUsageStrorageProviderMethodName).append(System.lineSeparator());
                        }
                    }
                }
                markdown.append(System.lineSeparator());
            });
        });

        markdown.append(System.lineSeparator()).append(System.lineSeparator());
        markdown.append("## Method summary: ").append(System.lineSeparator());
        scenarioResult.getMethodStatistics().forEach(mss -> {
            markdown.append("### ").append(mss.getMethodIdentifier()).append(System.lineSeparator());
            markdown.append("#### Timings").append(System.lineSeparator());
            markdown.append("  - invocations: ").append(mss.getCount()).append(System.lineSeparator());
            markdown.append("  - total duration: ").append(Duration.ofNanos(mss.getSum())).append(System.lineSeparator());
            markdown.append("  - avg duration: ").append(Duration.ofNanos((long) mss.getAverage())).append(System.lineSeparator());
            markdown.append("  - min duration: ").append(Duration.ofNanos(mss.getMin())).append(System.lineSeparator());
            markdown.append("  - max duration: ").append(Duration.ofNanos(mss.getMax())).append(System.lineSeparator()).append(System.lineSeparator());

            List<StorageProviderQueryAnalysis> queries = scenarioResult.getQueryAnalyses().stream().filter(qa -> qa.getStorageProviderMethodName().equals(mss.getMethodIdentifier())).toList();
            if (!queries.isEmpty()) {
                markdown.append("#### Queries").append(System.lineSeparator());
                for (int i = 0; i < queries.size(); i++) {
                    StorageProviderQueryAnalysis qa = queries.get(i);
                    markdown.append("##### Query ").append(i + 1).append(System.lineSeparator());
                    markdown.append(" -  Query identifier: `").append(qa.getQuery().getQueryIdentifier()).append("`").append(System.lineSeparator());
                    markdown.append(" - Query with values: `").append(qa.getQuery().getQueryWithValues()).append("`").append(System.lineSeparator());
                    qa.getQuery().getQueryTimings().forEach((key, timing) -> {
                        markdown.append("   - ").append(key).append(" query part timings: ")
                                .append("invocations: ").append(timing.getCount())
                                .append(" / total duration: ").append(Duration.ofNanos(timing.getSum()))
                                .append(" / avg duration: ").append(Duration.ofNanos((long) timing.getAverage()))
                                .append(" / min duration: ").append(Duration.ofNanos(timing.getMin()))
                                .append(" / max duration: ").append(Duration.ofNanos(timing.getMax()))
                                .append(System.lineSeparator());
                    });
                    for (QueryAnalysisAtPercentage queryAnalysisAtPercentage : qa.getAnalysisAtPercentage()) {
                        markdown.append("> At percentage: ").append(queryAnalysisAtPercentage.getPercentage()).append(" (").append(queryAnalysisAtPercentage.getInvocationCount()).append(" invocations)").append(System.lineSeparator());
                        markdown.append(System.lineSeparator()).append("```").append(System.lineSeparator()).append(queryAnalysisAtPercentage.getAnalysis()).append(queryAnalysisAtPercentage.getAnalysis().endsWith(System.lineSeparator()) ? "" : System.lineSeparator()).append("```").append(System.lineSeparator());
                    }
                    markdown.append(System.lineSeparator());
                }
                markdown.append(System.lineSeparator());
            } else {
                markdown.append(System.lineSeparator());
            }

        });

        Path fileName = findLogbooksFolder().resolve("details").resolve(scenarioResult.getTimestamp().toString() + ".md");
        try {
            Files.writeString(fileName, markdown.toString(), StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING);
            LOGGER.info("Successfully wrote details for scenario {} to {}", camelCaseToHumanReadable(scenarioResult.getScenario()), fileName);
        } catch (IOException e) {
            LOGGER.error("Error writing details for scenario {} to {}", camelCaseToHumanReadable(scenarioResult.getScenario()), fileName);
        }
    }
}
