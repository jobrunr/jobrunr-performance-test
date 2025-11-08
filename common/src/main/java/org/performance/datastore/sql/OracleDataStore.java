package org.performance.datastore.sql;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.zaxxer.hikari.HikariDataSource;
import org.testcontainers.oracle.OracleContainer;
import org.testcontainers.utility.MountableFile;

import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

public class OracleDataStore extends AbstractSqlDataStore<OracleContainer> {

    public OracleDataStore() {
        super(new OracleContainer("gvenzl/oracle-free:latest-faststart")
                .withCreateContainerCmdModifier(cmd ->
                        cmd.withHostConfig(cmd.getHostConfig().withPortBindings(
                                        new PortBinding(Ports.Binding.bindPort(15210), new ExposedPort(1521))
                                )
                        ))
                .withSharedMemorySize(4294967296L)
                .withStartupTimeoutSeconds(900)
                .withConnectTimeoutSeconds(500)
                .withCopyFileToContainer(MountableFile.forClasspathResource("/oracle/container-entrypoint-initdb.d"), "/container-entrypoint-initdb.d")
                .withEnv("DB_SID", "ORCL")
                .withEnv("DB_PASSWD", "oracle"));
    }

    protected HikariDataSource toHikariDataSource(String jdbcUrl, String userName, String password, String driverClassName) {
        return super.toHikariDataSource(jdbcUrl.replace("xepdb1", "FREEPDB1"), userName, password, driverClassName);
    }

    @Override
    public void updateStatistics() {
        try (Connection connection = getDataSource().getConnection();
             CallableStatement statement = connection.prepareCall("BEGIN dbms_stats.gather_schema_stats(ownname => 'TEST', cascade => TRUE); END;")) {
            statement.execute();
            LOGGER.info("UPDATED Oracle STATISTICS");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String explainQuery(String queryWithValues) {
        return explainAnalyseQuery(queryWithValues);
    }

    @Override
    public String explainExecute(String queryWithValues) {
        return explainAnalyseQuery(queryWithValues);
    }

    @Override
    public String explainAnalyseQuery(Connection connection, String analyzeQueryWithValues) {
        try (Statement statement = connection.createStatement()) {
            String uuid = UUID.randomUUID().toString();
            String sqlStatement = analyzeQueryWithValues.replaceFirst(" ", " /*+ gather_plan_statistics*/ /* " + uuid + " */");

            statement.execute(sqlStatement);

            String sqlId;
            int childNumber;
            String lookup = "SELECT sql_id, child_number FROM v$sql WHERE sql_text LIKE ? AND rownum = 1";
            try (PreparedStatement ps = connection.prepareStatement(lookup)) {
                ps.setString(1, "%" + uuid + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("Could not locate SQL_ID/child for UUID=" + uuid + " and query=" + analyzeQueryWithValues);
                    }
                    sqlId = rs.getString("sql_id");
                    childNumber = rs.getInt("child_number");
                }
            }

            try (PreparedStatement ps = connection.prepareStatement("SELECT * FROM TABLE(DBMS_XPLAN.DISPLAY_CURSOR(?, ?, 'LAST ALLSTATS ALL +COST'))")) {
                ps.setString(1, sqlId);
                ps.setInt(2, childNumber);
                try (ResultSet rs = ps.executeQuery()) {
                    StringBuilder sb = new StringBuilder();
                    while (rs.next()) {
                        sb.append(rs.getString(1)).append(System.lineSeparator());
                    }
                    return sb.toString();
                }
            }
        } catch (SQLException e) {
            return "Could not explain query plan for query '" + analyzeQueryWithValues + "' due to " + e.getMessage();
        }
    }
}
