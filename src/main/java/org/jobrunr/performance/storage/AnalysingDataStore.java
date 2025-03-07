package org.jobrunr.performance.storage;

import org.jobrunr.storage.TimedStorageProvider;

public interface AnalysingDataStore {

    String explainQuery(TimedStorageProvider.Query query);
}
