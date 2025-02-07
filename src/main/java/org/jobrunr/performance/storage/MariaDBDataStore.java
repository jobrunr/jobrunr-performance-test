package org.jobrunr.performance.storage;

import org.testcontainers.containers.MariaDBContainer;

import java.sql.Connection;
import java.sql.Statement;

public class MariaDBDataStore extends AbstractSqlDataStore {

    public MariaDBDataStore() {
        super(
                new MariaDBContainer<>("mariadb:11.4")
                        .withCommand("--max-allowed-packet=128M"),
                "org.mariadb.jdbc.Driver");
    }

    @Override
    public void updateStatistics() {
        try (Connection connection = getDataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("OPTIMIZE TABLE jobrunr_jobs;");
            statement.execute("ANALYZE TABLE jobrunr_jobs;");
            LOGGER.info("OPTIMIZED AND ANALYZED MARIADB TABLES");
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
