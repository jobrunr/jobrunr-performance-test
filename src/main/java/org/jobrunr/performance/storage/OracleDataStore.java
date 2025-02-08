package org.jobrunr.performance.storage;

import com.zaxxer.hikari.HikariDataSource;
import org.jobrunr.performance.utils.Memory;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.utility.DockerImageName;

import java.sql.Connection;
import java.sql.Statement;

import static org.jobrunr.performance.utils.Memory.Unit.gigabytes;

public class OracleDataStore extends AbstractSqlDataStore {

    public OracleDataStore() {
        super(new OracleContainer(DockerImageName
                        .parse("gvenzl/oracle-free:latest-faststart")
                        .asCompatibleSubstituteFor("gvenzl/oracle-xe"))
                        .withStartupTimeoutSeconds(900)
                        .withConnectTimeoutSeconds(500)
                        .withEnv("DB_SID", "ORCL")
                        .withEnv("DB_PASSWD", "oracle")
                        .withSharedMemorySize(Memory.of(2, gigabytes).toBytes()),
                "oracle.jdbc.driver.OracleDriver");
    }

    @Override
    protected HikariDataSource toHikariDataSource(String jdbcUrl, String userName, String password, String driverClassName) {
        return super.toHikariDataSource(jdbcUrl.replace("xepdb1", "FREEPDB1"), userName, password, driverClassName);
    }

    @Override
    public void updateStatistics() {
        try (Connection connection = getDataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("EXEC DBMS_STATS.GATHER_DATABASE_STATS;");
            LOGGER.info("UPDATED Oracle STATISTICS");
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
