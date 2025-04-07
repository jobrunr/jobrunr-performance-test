package org.jobrunr.performance.storage.sql;

import org.jobrunr.jobs.Job;
import org.jobrunr.jobs.mappers.JobMapper;
import org.jobrunr.jobs.states.StateName;
import org.jobrunr.performance.scenario.monitor.QueryAnalysisMonitor;
import org.jobrunr.performance.storage.AnalysingDataStore;
import org.jobrunr.performance.storage.StorageProviderQueryAnalysis;
import org.jobrunr.server.BackgroundJobServer;
import org.jobrunr.storage.JobSearchRequest;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.ThreadSafeStorageProvider;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.jobrunr.stubs.Mocks;
import org.jobrunr.utils.SleepUtils;
import org.jobrunr.utils.mapper.jackson.JacksonJsonMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jobrunr.jobs.JobTestBuilder.anEnqueuedJobWithName;
import static org.jobrunr.storage.Paging.OffsetBasedPage.ascOnPriorityAndUpdatedAt;
import static org.jobrunr.storage.ThreadSafeStorageProvider.MethodStatisticsConfiguration.DETAILED;

class MySQLDataStoreTest {

    BackgroundJobServer backgroundJobServer = Mocks.ofBackgroundJobServer();

    MySQLDataStore dataStore;
    StorageProvider storageProvider;

    @BeforeEach
    void setUp() {
        dataStore = new MySQLDataStore();
        dataStore.start();
        storageProvider = new ThreadSafeStorageProvider(SqlStorageProviderFactory.using(dataStore.getDataSource()));
        storageProvider.setJobMapper(new JobMapper(new JacksonJsonMapper()));
        ThreadSafeStorageProvider.setMethodStatisticsConfiguration(DETAILED);
    }

    @AfterEach
    void tearDown() {
        dataStore.stop();
    }

    @Test
    void shouldGetIndexDetails() {
        List<AnalysingDataStore.IndexDetails> indexDetails = dataStore.getIndexDetails();
        indexDetails.forEach(System.out::println);
        dataStore.stop();
    }

    @Test
    void shouldGetQueryAnalysis() {
        QueryAnalysisMonitor queryAnalysisMonitor = new QueryAnalysisMonitor(dataStore, Instant.now(), Duration.ofMinutes(1), 0.5);
        storageProvider.addJobStorageOnChangeListener(queryAnalysisMonitor);

        Job savedJob = storageProvider.save(anEnqueuedJobWithName().build());
        storageProvider.getJobs(new JobSearchRequest(StateName.ENQUEUED), ascOnPriorityAndUpdatedAt(10));

        savedJob.startProcessingOn(backgroundJobServer);
        storageProvider.save(savedJob);
        savedJob.succeeded();
        storageProvider.save(savedJob);

        SleepUtils.sleep(3500);
        Collection<StorageProviderQueryAnalysis> queryAnalyses = queryAnalysisMonitor.getQueryAnalyses();
        assertThat(queryAnalyses).hasSize(5);
    }
}