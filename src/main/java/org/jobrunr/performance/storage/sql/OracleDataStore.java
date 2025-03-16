package org.jobrunr.performance.storage.sql;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.zaxxer.hikari.HikariDataSource;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import java.sql.CallableStatement;
import java.sql.Connection;

public class OracleDataStore extends AbstractSqlDataStore {

    public OracleDataStore() {
        super(new OracleContainer("gvenzl/oracle-free:latest-faststart")
                        .withCreateContainerCmdModifier(cmd ->
                                cmd.withHostConfig(cmd.getHostConfig().withPortBindings(
                                                new PortBinding(Ports.Binding.bindPort(15210), new ExposedPort(1521))
                                        )
                                ))
                        .withStartupTimeoutSeconds(900)
                        .withConnectTimeoutSeconds(500)
                        .withCopyFileToContainer(MountableFile.forClasspathResource("/oracle/container-entrypoint-initdb.d"), "/container-entrypoint-initdb.d")
                        .withEnv("DB_SID", "ORCL")
                        .withEnv("DB_PASSWD", "oracle"),
                "oracle.jdbc.OracleDriver");
    }

    @Override
    protected HikariDataSource toHikariDataSource(String jdbcUrl, String userName, String password, String driverClassName) {
        return super.toHikariDataSource(jdbcUrl.replace("xepdb1", "FREEPDB1"), userName, password, driverClassName);
    }

    @Override
    public void updateStatistics() {
        try (Connection connection = getDataSource().getConnection();
             CallableStatement statement = connection.prepareCall("BEGIN dbms_stats.gather_schema_stats(ownname => 'TEST', cascade => TRUE); END;")) {
            statement.execute();
            LOGGER.info("UPDATED Oracle STATISTICS");
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
