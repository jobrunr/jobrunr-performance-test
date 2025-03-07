package org.jobrunr.performance.storage.sql;

import org.testcontainers.containers.MSSQLServerContainer;
import org.testcontainers.containers.startupcheck.MinimumDurationRunningStartupCheckStrategy;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;

public class SQLServerDataStore extends AbstractSqlDataStore {

    public SQLServerDataStore() {
        super(new MSSQLServerContainer<>(DockerImageName
                        .parse("mcr.microsoft.com/azure-sql-edge")
                        .asCompatibleSubstituteFor("mcr.microsoft.com/mssql/server"))
                        .waitingFor(Wait.forListeningPort())
                        .withStartupCheckStrategy(new MinimumDurationRunningStartupCheckStrategy(Duration.ofSeconds(10))),
                "com.microsoft.sqlserver.jdbc.SQLServerDriver");
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
}
