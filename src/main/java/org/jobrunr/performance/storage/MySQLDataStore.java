package org.jobrunr.performance.storage;

import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.Statement;

public class MySQLDataStore extends AbstractSqlDataStore {

    public MySQLDataStore() {
        super(new MySQLContainer<>("mysql:9.2")
                        .withCommand("--max-allowed-packet=128M"),
                "com.mysql.cj.jdbc.Driver");
    }

    @Override
    public void updateStatistics() {
        try (Connection connection = getDataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("OPTIMIZE TABLE jobrunr_jobs;");
            statement.execute("ANALYZE TABLE jobrunr_jobs;");
            LOGGER.info("OPTIMIZED AND ANALYZED MYSQL TABLES");
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
