package org.performance.datastore.sql;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

public class MySQLExternalDataStore extends AbstractSqlExternalDataStore {

    private HikariDataSource dataSource;

    public MySQLExternalDataStore() {
        super("com.mysql.cj.jdbc.Driver", "jdbc:mysql://localhost:3306/test", "test", "test");
    }

    @Override
    protected HikariDataSource toHikariDataSource(String jdbcUrl, String userName, String password, String driverClassName) {
        return super.toHikariDataSource(jdbcUrl + "?rewriteBatchedStatements=true&cachePrepStmts=true&prepStmtCacheSize=500&prepStmtCacheSqlLimit=1024", userName, password, driverClassName);
    }

    @Override
    protected IndexDetails toIndexDetails(String tableName, String indexName, List<String> columnNames) {
        try (Connection connection = DriverManager.getConnection(dataSource().getJdbcUrl(), "root", dataSource().getPassword()); Statement statement = connection.createStatement()) {
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
    protected String getExternalDockerCommand() {
        return """
                docker run \\
                    --name mysql-test \\
                    --memory=4g \\
                    --memory-swap=6g \\
                    --cpus=4 \\
                    --shm-size=128m \\
                    -v /Volumes/T9/JobRunr/MySQL:/var/lib/mysql \\
                    -p 3306:3306 \\
                    -e MYSQL_ROOT_PASSWORD=test \\
                    -e MYSQL_DATABASE=test \\
                    -e MYSQL_USER=test \\
                    -e MYSQL_PASSWORD=test \\
                    -d mysql:8.4 \\
                    --innodb-buffer-pool-size=1536M \\
                    --innodb-redo-log-capacity=2G \\
                    --innodb-log-buffer-size=32M \\
                    --innodb-flush-log-at-trx-commit=1 \\
                    --sync-binlog=1 \\
                    --innodb-io-capacity=1000 \\
                    --innodb-io-capacity-max=3000 \\
                    --tmp-table-size=8M \\
                    --max-heap-table-size=8M \\
                    --sort-buffer-size=256K \\
                    --read-buffer-size=128K \\
                    --read-rnd-buffer-size=256K \\
                    --join-buffer-size=256K \\
                    --max-connections=50 \\
                    --thread-cache-size=16 \\
                    --table-open-cache=1000 \\
                    --table-definition-cache=500 \\
                    --max-allowed-packet=64M \\
                    --binlog-expire-logs-seconds=18000 \\
                    --performance-schema=ON \\
                    --performance-schema-consumer-events-waits-history=OFF
                """;
    }
}
