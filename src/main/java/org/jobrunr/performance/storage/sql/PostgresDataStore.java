package org.jobrunr.performance.storage.sql;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.jobrunr.performance.storage.AnalysingDataStore;
import org.jobrunr.storage.TimedStorageProvider.Query;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class PostgresDataStore extends AbstractSqlDataStore implements AnalysingDataStore {

    public PostgresDataStore() {
        super(new PostgreSQLContainer<>("postgres:15-alpine")
                .withCreateContainerCmdModifier(cmd ->
                        cmd.withHostConfig(cmd.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindPort(54320), new ExposedPort(5432)))
                        )), "org.postgresql.Driver");
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

    public String explainQuery(Query query) {
        try (Connection connection = getDataSource().getConnection(); Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery("explain analyze " + query.getQueryWithValues());
            StringBuilder sb = new StringBuilder();
            while (resultSet.next()) {
                sb.append(resultSet.getString(1)).append(System.lineSeparator());
            }
            return sb.toString();
        } catch (java.sql.SQLException e) {
            return "Could not explain query plan for query '" + query.getQueryWithValues() + "' due to " + e.getMessage() + System.lineSeparator();
        }
    }
}
