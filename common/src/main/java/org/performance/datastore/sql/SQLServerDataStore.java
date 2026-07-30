package org.performance.datastore.sql;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

public class SQLServerDataStore extends AbstractSqlContainerDataStore<MSSQLServerContainer> {

    public SQLServerDataStore() {
        super(new MSSQLServerContainer(DockerImageName
                .parse("mcr.microsoft.com/mssql/server:2025-latest"))
                .withCreateContainerCmdModifier(cmd ->
                        cmd.withHostConfig(cmd.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindPort(14330), new ExposedPort(1433)))))
                .waitingFor(Wait.forListeningPort())
                .withStartupCheckStrategy(new MinimumDurationRunningStartupCheckStrategy(Duration.ofSeconds(10))));
    }

    @Override
    public void updateStatistics() {
        try (Connection connection = dataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE STATISTICS jobrunr_jobs;");
            LOGGER.info("UPDATED SQLSERVER STATISTICS");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String explainQuery(String queryWithValues) {
        return explainAnalyseQuery(queryWithValues);
    }

    public String explainExecute(String queryWithValues) {
        return explainAnalyseQuery(queryWithValues);
    }

    public String explainAnalyseQuery(Connection connection, String analyzeQueryWithValues) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // Enable XML query plan mode; the query itself won't be executed
            statement.execute("SET SHOWPLAN_ALL ON");
            try {
                ResultSet resultSet = statement.executeQuery(analyzeQueryWithValues);
                StringBuilder sb = new StringBuilder();
                while (resultSet.next()) {
                    sb.append(resultSet.getString(1)).append(System.lineSeparator());
                }
                return sb.toString();
            } finally {
                statement.execute("SET SHOWPLAN_ALL OFF");
            }
        }
    }

    @Override
    public boolean isQueryUsingIndex(String analysis, String indexName) {
        return analysis.contains("Index=\"[" + indexName + "]\" IndexKind");
    }
}
