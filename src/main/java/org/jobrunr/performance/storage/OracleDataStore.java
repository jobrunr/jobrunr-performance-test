package org.jobrunr.performance.storage;

import com.zaxxer.hikari.HikariDataSource;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.utility.DockerImageName;

public class OracleDataStore extends AbstractSqlDataStore {

    public OracleDataStore() {
        super(new OracleContainer(DockerImageName
                        .parse("gvenzl/oracle-free:latest-faststart")
                        .asCompatibleSubstituteFor("gvenzl/oracle-xe"))
                        .withStartupTimeoutSeconds(900)
                        .withConnectTimeoutSeconds(500)
                        .withEnv("DB_SID", "ORCL")
                        .withEnv("DB_PASSWD", "oracle"),
                "oracle.jdbc.OracleDriver");
    }

    @Override
    protected HikariDataSource toHikariDataSource(String jdbcUrl, String userName, String password, String driverClassName) {
        return super.toHikariDataSource(jdbcUrl.replace("xepdb1", "FREEPDB1"), userName, password, driverClassName);
    }

//    @Override
//    public void updateStatistics() {
//        try (Connection connection = getDataSource().getConnection();
//             CallableStatement statement = connection.prepareCall("{call DBMS_STATS.GATHER_DATABASE_STATS}")) {
//            statement.execute();
//            LOGGER.info("UPDATED Oracle STATISTICS");
//        } catch (java.sql.SQLException e) {
//            throw new RuntimeException(e);
//        }
//    }
}
