package org.jobrunr.performance.storage.sql;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import com.zaxxer.hikari.HikariDataSource;
import org.jobrunr.performance.storage.AnalysingDataStore;
import org.jobrunr.storage.ThreadSafeStorageProvider;
import org.testcontainers.containers.MariaDBContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

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
                                "--query-cache-type=0 --query-cache-size=0 --performance-schema"),
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
        String actualQuery = query.getQueryWithValues().toLowerCase().trim();
        boolean isDeleteQuery = actualQuery.startsWith("delete ");
        if (isDeleteQuery) actualQuery = actualQuery.replace("delete ", "select ");
        boolean canAnalyze = actualQuery.startsWith("select ");
        if (canAnalyze) {
            String explainQuery = "ANALYZE FORMAT=JSON " + actualQuery;
            return isDeleteQuery
                    ? "-- delete replaced with select for query analysis" + System.lineSeparator() + explainQuery(explainQuery)
                    : explainQuery(explainQuery);
        } else {
            return "Not analyzing insert / update statements";
        }
    }

    @Override
    protected IndexDetails toIndexDetails(String tableName, String indexName, List<String> columnNames) {
        try (Connection connection = DriverManager.getConnection(sqlContainer.getJdbcUrl(), "root", sqlContainer.getPassword()); Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery(String.format("""
                        SELECT p.OBJECT_SCHEMA, p.OBJECT_NAME, p.INDEX_NAME, p.COUNT_READ
                        FROM performance_schema.table_io_waits_summary_by_index_usage p
                        INNER JOIN information_schema.STATISTICS i
                            ON
                                    p.OBJECT_SCHEMA = i.TABLE_SCHEMA
                                AND p.OBJECT_NAME   = i.TABLE_NAME
                                AND p.INDEX_NAME    = i.INDEX_NAME
                        WHERE i.TABLE_NAME = '%s'
                        AND i.INDEX_NAME = '%s'
                        ORDER BY p.object_schema, p.object_name, p.index_name;
                    """, tableName, indexName));
            while (resultSet.next()) {
                String nbrOfReads = resultSet.getString("COUNT_READ").trim().replaceAll(",", "");
                return new IndexDetails(tableName, indexName, columnNames, nbrOfReads.startsWith("0")
                        ? "** index not used!!**"
                        : "index has " + nbrOfReads + " read operations");
            }
            return super.toIndexDetails(tableName, indexName, columnNames);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public boolean isQueryUsingIndex(String analysis, String indexName) {
        return analysis.contains("\"key\": \"" + indexName + "\"");
    }
}
