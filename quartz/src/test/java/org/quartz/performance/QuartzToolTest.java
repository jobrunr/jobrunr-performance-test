package org.quartz.performance;


import org.junit.jupiter.api.Test;
import org.performance.datastore.sql.SQLServerDataStore;

import static org.assertj.core.api.Assertions.assertThatCode;

class QuartzToolTest {

    @Test
    void canInitializeSQLServer() throws Exception {
        SQLServerDataStore sqlServerDataStore = new SQLServerDataStore();
        sqlServerDataStore.start();
        QuartzTool tool = new QuartzTool();
        assertThatCode(() -> tool.initialize(sqlServerDataStore)).doesNotThrowAnyException();
    }

}