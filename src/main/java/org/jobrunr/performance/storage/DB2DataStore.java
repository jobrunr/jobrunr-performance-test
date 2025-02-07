package org.jobrunr.performance.storage;

import org.testcontainers.containers.Db2Container;

import java.sql.Connection;
import java.sql.Statement;

public class DB2DataStore extends AbstractSqlDataStore {

    public DB2DataStore() {
        super(new Db2Container("icr.io/db2_community/db2:12.1.0.0")
                        .withPrivilegedMode(true)
                        .acceptLicense(),
                "com.ibm.db2.jcc.DB2Driver");
    }

    @Override
    public void updateStatistics() {
        try (Connection connection = getDataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("RUNSTATS ON TABLE jobrunr_jobs WITH DISTRIBUTION AND DETAILED INDEXES ALL;");
            LOGGER.info("UPDATED Oracle STATISTICS");
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
