package org.jobrunr.performance.storage.sql;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.zaxxer.hikari.HikariDataSource;
import org.jobrunr.performance.storage.AnalysingDataStore;
import org.jobrunr.storage.ThreadSafeStorageProvider;
import org.testcontainers.containers.MariaDBContainer;

import java.sql.Connection;
import java.sql.Statement;

public class MariaDBDataStore extends AbstractSqlDataStore implements AnalysingDataStore {

    public MariaDBDataStore() {
        super(
                new MariaDBContainer<>("mariadb:11.4")
                        .withCreateContainerCmdModifier(cmd ->
                                cmd.withHostConfig(cmd.getHostConfig().withPortBindings(
                                                new PortBinding(Ports.Binding.bindPort(33060), new ExposedPort(3306))
                                        )
                                ))
                        .withCommand("--innodb-buffer-pool-size=3G --innodb-log-file-size=717M --innodb-log-buffer-size=8M " +
                                "--tmp-table-size=256M --sort-buffer-size=256K --read-rnd-buffer-size=512K " +
                                "--max-connections=80 --thread-cache-size=80 --max-allowed-packet=128M " +
                                "--query-cache-type=0 --query-cache-size=0"),
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

    @Override
    public String explainQuery(ThreadSafeStorageProvider.Query query) {
        String explainQuery = "ANALYZE FORMAT=JSON " + query.getQueryWithValues();
        return explainQuery(explainQuery);
    }
}
