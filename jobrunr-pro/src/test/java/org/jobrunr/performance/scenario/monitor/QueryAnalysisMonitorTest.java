package org.jobrunr.performance.scenario.monitor;

import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.storage.ThreadSafeStorageProvider;
import org.jobrunr.storage.navigation.DynamicAmountRequest;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.jobrunr.stubs.Mocks;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.jobrunrpro.performance.scenario.monitor.QueryAnalysisMonitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.performance.datastore.sql.MySQLDataStore;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jobrunr.jobs.JobTestBuilder.anEnqueuedJobWithName;
import static org.jobrunr.storage.Paging.AmountBasedList.ascOnPriorityAndUpdatedAt;
import static org.jobrunr.storage.StorageProviderUtils.Jobs.FIELD_DYNAMIC_QUEUE;
import static org.jobrunr.storage.ThreadSafeStorageProvider.MethodStatisticsConfiguration.DETAILED;

class QueryAnalysisMonitorTest {

    MySQLDataStore mySQLDataStore = new MySQLDataStore();

    BackgroundJobServer backgroundJobServerMock = Mocks.ofBackgroundJobServer();

    ThreadSafeStorageProvider storageProvider;
    QueryAnalysisMonitor queryAnalysisMonitor;

    @BeforeEach
    void setUp() {
        mySQLDataStore.start();
        this.storageProvider = new ThreadSafeStorageProvider(SqlStorageProviderFactory.using(mySQLDataStore.getDataSource()));
        this.storageProvider.setJobMapper(new JobMapper(new JacksonJsonMapper()));
        ThreadSafeStorageProvider.setMethodStatisticsConfiguration(DETAILED);
        queryAnalysisMonitor = new QueryAnalysisMonitor(mySQLDataStore, Instant.now(), Duration.ofSeconds(10), 0.2);
        this.storageProvider.addJobStorageOnChangeListener(queryAnalysisMonitor);
    }

    @AfterEach
    void tearDown() {
        storageProvider.close();
        mySQLDataStore.stop();
    }


    @Test
    void testThreadSafeStorageProviderMethodMetrics() {
        storageProvider.save(anEnqueuedJobWithName().build());

        DynamicAmountRequest dynamicAmountRequest = new DynamicAmountRequest(order -> FIELD_DYNAMIC_QUEUE + ":ASC," + order, ascOnPriorityAndUpdatedAt(10));
        List<Job> jobsToProcess = storageProvider.getJobsToProcess(backgroundJobServerMock, null, dynamicAmountRequest);

        queryAnalysisMonitor.onChange(storageProvider.getJobStats());

        List<ThreadSafeStorageProvider.MethodStatistics> allMethodsStatistics = ThreadSafeStorageProvider.getMethodStatistics();
        assertThat(allMethodsStatistics).hasSizeGreaterThan(3);
        ThreadSafeStorageProvider.MethodStatistics methodStatistics = allMethodsStatistics.stream().filter(x -> "getJobsToProcess()".equals(x.getMethodIdentifier())).findFirst().get();
        assertThat(methodStatistics.getQueries()).hasSize(2);
    }


}