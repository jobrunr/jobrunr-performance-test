package org.jobrunr.performance.storage;

import org.testcontainers.containers.OracleContainer;

import java.sql.Connection;
import java.sql.Statement;

public class OracleDataStore extends AbstractSqlDataStore {

    public OracleDataStore() {
        super(new OracleContainer("gvenzl/oracle-xe")
                        .withStartupTimeoutSeconds(900)
                        .withConnectTimeoutSeconds(500)
                        .withEnv("DB_SID", "ORCL")
                        .withEnv("DB_PASSWD", "oracle")
                        .withSharedMemorySize(4294967296L),
                "oracle.jdbc.driver.OracleDriver");
    }

    @Override
    public void updateStatistics() {
        try (Connection connection = getDataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("EXEC DBMS_STATS.GATHER_DATABASE_STATS;");
            LOGGER.info("UPDATED Oracle STATISTICS");
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
