package org.jobrunr.performance.storage.sql;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

public class SQLServerDataStore extends AbstractSqlDataStore {

    public SQLServerDataStore() {
        super(new MSSQLServerContainer<>(DockerImageName
                .parse("mcr.microsoft.com/mssql/server:2022-latest"))
                .withCreateContainerCmdModifier(cmd ->
                        cmd.withHostConfig(cmd.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindPort(14330), new ExposedPort(1433)))))
                .waitingFor(Wait.forListeningPort())
                .withStartupCheckStrategy(new MinimumDurationRunningStartupCheckStrategy(Duration.ofSeconds(10))));
    }

    @Override
    public void updateStatistics() {
        try (Connection connection = getDataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE STATISTICS jobrunr_jobs;");
            LOGGER.info("UPDATED SQLSERVER STATISTICS");
        } catch (java.sql.SQLException e) {
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
            statement.execute("SET SHOWPLAN_XML ON");
            ResultSet resultSet = statement.executeQuery(analyzeQueryWithValues);
            //statement.getMoreResults();
            //ResultSet explainPlanResultSet = statement.getResultSet();
            StringBuilder sb = new StringBuilder();
            while (resultSet.next()) {
                sb.append(resultSet.getString(1)).append(System.lineSeparator());
            }
            statement.execute("SET SHOWPLAN_XML OFF");
            return sb.toString();
        }
    }
}
