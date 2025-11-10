package org.jobrunr.performance.scenario.monitor;

import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.storage.ThreadSafeStorageProvider;
import org.jobrunr.stubs.Mocks;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.performance.datastore.sql.MySQLDataStore;

//@Testcontainers
class QueryAnalysisMonitorTest {

    MySQLDataStore mySQLDataStore = new MySQLDataStore();

    BackgroundJobServer backgroundJobServerMock = Mocks.ofBackgroundJobServer();

    ThreadSafeStorageProvider storageProvider;
    //QueryAnalysisMonitor queryAnalysisMonitor;

    @BeforeEach
    void setUp() {
        mySQLDataStore.start();
//        this.storageProvider = new ThreadSafeStorageProvider(mySQLDataStore.getStorageProvider(false));
//        ThreadSafeStorageProvider.setMethodStatisticsConfiguration(DETAILED);
//        this.storageProvider.setJobMapper(new JobMapper(new JacksonJsonMapper()));
//        queryAnalysisMonitor = new QueryAnalysisMonitor(mySQLDataStore, Instant.now(), Duration.ofSeconds(10), 0.2);
//        this.storageProvider.addJobStorageOnChangeListener(queryAnalysisMonitor);
    }

    @Test
    void testThreadSafeStorageProviderMethodMetrics() {
//        storageProvider.save(anEnqueuedJobWithName().build());
//
//        DynamicAmountRequest dynamicAmountRequest = new DynamicAmountRequest(order -> FIELD_DYNAMIC_QUEUE + ":ASC_NULLS_FIRST," + order, ascOnPriorityAndUpdatedAt(10));
//        List<Job> jobsToProcess = storageProvider.getJobsToProcess(backgroundJobServerMock, null, dynamicAmountRequest);
//
//        queryAnalysisMonitor.onChange(storageProvider.getJobStats());
//
//        List<ThreadSafeStorageProvider.MethodStatistics> allMethodsStatistics = ThreadSafeStorageProvider.getMethodStatistics();
//        assertThat(allMethodsStatistics).hasSize(3);
//        ThreadSafeStorageProvider.MethodStatistics methodStatistics = allMethodsStatistics.stream().filter(x -> "getJobsToProcess()".equals(x.getMethodIdentifier())).findFirst().get();
//        assertThat(methodStatistics.getQueries()).hasSize(2);
    }


}