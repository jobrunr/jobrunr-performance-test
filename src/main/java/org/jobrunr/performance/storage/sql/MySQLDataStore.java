package org.jobrunr.performance.storage.sql;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.zaxxer.hikari.HikariDataSource;
import org.jobrunr.performance.storage.AnalysingDataStore;
import org.jobrunr.storage.TimedStorageProvider;
import org.testcontainers.containers.MySQLContainer;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class MySQLDataStore extends AbstractSqlDataStore implements AnalysingDataStore {

    public MySQLDataStore() {
        super(new MySQLContainer<>("mysql:9.2")
                        .withCreateContainerCmdModifier(cmd ->
                                cmd.withHostConfig(cmd.getHostConfig().withPortBindings(
                                                new PortBinding(Ports.Binding.bindPort(33060), new ExposedPort(3306))
                                        )
                                ))
                        .withCommand("--innodb-buffer-pool-size=3G --innodb-log-file-size=717M --innodb-log-buffer-size=8M " +
                                "--tmp-table-size=256M --sort-buffer-size=256K --read-rnd-buffer-size=512K " +
                                "--max-connections=80 --thread-cache-size=80 --max-allowed-packet=128M"),
                "com.mysql.cj.jdbc.Driver");
    }

    @Override
    protected HikariDataSource toHikariDataSource(String jdbcUrl, String userName, String password, String driverClassName) {
        return super.toHikariDataSource(jdbcUrl + "?useServerPrepStmts=true&cachePrepStmts=true&prepStmtCacheSize=500&prepStmtCacheSqlLimit=1024", userName, password, driverClassName);
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

    public String explainQuery(TimedStorageProvider.Query query) {
        try (Connection connection = getDataSource().getConnection(); Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery("explain analyze " + query.getQueryWithValues());
            StringBuilder sb = new StringBuilder();
            while (resultSet.next()) {
                sb.append(resultSet.getString(1)).append(System.lineSeparator());
            }
            return sb.toString();
        } catch (java.sql.SQLException e) {
            return "Could not explain query plan for query '" + query.getQueryWithValues() + "' due to " + e.getMessage();
        }
    }
}
