package org.jobrunr.storage;

import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.Statement;

public class PostgresDataStore extends AbstractSqlDataStore {

    public PostgresDataStore() {
        super(new PostgreSQLContainer<>("postgres:15-alpine"));
    }

    @Override
    public void updateStatistics() {
        try (Connection connection = getDataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("VACUUM (VERBOSE, ANALYZE) jobrunr_jobs;");
            LOGGER.info("VACUUMED POSTGRES TABLES");
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
