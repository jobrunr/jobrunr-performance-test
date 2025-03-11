package org.jobrunr.performance.storage.sql;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.jobrunr.performance.storage.AnalysingDataStore;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.Statement;

public class PostgresDataStore extends AbstractSqlDataStore implements AnalysingDataStore {

    public PostgresDataStore() {
        super(new PostgreSQLContainer<>("postgres:15-alpine")
                .withCreateContainerCmdModifier(cmd ->
                        cmd.withHostConfig(cmd.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindPort(54320), new ExposedPort(5432)))
                        )).withCommand("-c max_wal_size=2GB"), "org.postgresql.Driver");
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
