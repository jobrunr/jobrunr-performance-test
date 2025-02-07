package org.jobrunr.performance.storage;

import com.zaxxer.hikari.HikariDataSource;
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
    protected HikariDataSource toHikariDataSource(String jdbcUrl, String userName, String password, String driverClassName) {
        return super.toHikariDataSource(jdbcUrl + "?useServerPrepStmts=true", userName, password, driverClassName);
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
