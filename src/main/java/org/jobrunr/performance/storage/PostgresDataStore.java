package org.jobrunr.performance.storage;

import org.jobrunr.performance.utils.Memory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.Statement;

import static org.jobrunr.performance.utils.Memory.Unit.gigabytes;

public class PostgresDataStore extends AbstractSqlDataStore {

    public PostgresDataStore() {
        super(new PostgreSQLContainer<>("postgres:15-alpine")
                .withSharedMemorySize(Memory.of(2, gigabytes).toBytes()), "org.postgresql.Driver");
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
