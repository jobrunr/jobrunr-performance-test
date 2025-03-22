package org.jobrunr.performance.storage.sql;

import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.PortBinding;
import com.github.dockerjava.api.model.Ports;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

public class PostgresDataStore extends AbstractSqlDataStore {

    public PostgresDataStore() {
        super(new PostgreSQLContainer<>("postgres:17-alpine")
                .withCreateContainerCmdModifier(cmd ->
                        cmd.withHostConfig(cmd.getHostConfig().withPortBindings(new PortBinding(Ports.Binding.bindPort(54320), new ExposedPort(5432)))
                        )).withCommand("postgres", "-c", "max_wal_size=2GB", "-c", "random_page_cost=1.1"));
    }

    @Override
    public void updateStatistics() {
        try (Connection connection = getDataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE EXTENSION pg_stat_statements;");
            statement.executeUpdate("VACUUM (VERBOSE, ANALYZE) jobrunr_jobs;");
            LOGGER.info("VACUUMED POSTGRES TABLES");
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected IndexDetails toIndexDetails(String tableName, String indexName, List<String> columnNames) {
        try (Connection connection = DriverManager.getConnection(sqlContainer.getJdbcUrl(), sqlContainer.getUsername(), sqlContainer.getPassword()); Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery(String.format("""
                    SELECT
                         pt.tablename AS TableName ,t.indexname AS IndexName, to_char(pc.reltuples, '999,999,999,999') AS TotalRows, pg_size_pretty(pg_relation_size(quote_ident(pt.tablename)::text)) AS TableSize, pg_size_pretty(pg_relation_size(quote_ident(t.indexrelname)::text)) AS IndexSize, to_char(t.idx_scan, '999,999,999,999') AS TotalNumberOfScan, to_char(t.idx_tup_read, '999,999,999,999') AS TotalTupleRead, to_char(t.idx_tup_fetch, '999,999,999,999') AS TotalTupleFetched
                     FROM pg_tables AS pt
                     LEFT OUTER JOIN pg_class AS pc\s
                         ON pt.tablename=pc.relname
                     LEFT OUTER JOIN
                     (
                         SELECT pc.relname AS TableName, pc2.relname AS IndexName, psai.idx_scan, psai.idx_tup_read, psai.idx_tup_fetch, psai.indexrelname
                         FROM pg_index AS pi
                         JOIN pg_class AS pc ON pc.oid = pi.indrelid
                         JOIN pg_class AS pc2 ON pc2.oid = pi.indexrelid
                         JOIN pg_stat_all_indexes AS psai ON pi.indexrelid = psai.indexrelid
                     ) AS T
                         ON pt.tablename = T.TableName
                     WHERE pt.schemaname = 'public'
                       AND pt.tablename = '%s'
                       AND t.indexname = '%s'
                     ORDER BY 1;;
                    """, tableName, indexName));
            while (resultSet.next()) {
                String nbrOfScans = resultSet.getString("TotalNumberOfScan").trim();
                return new IndexDetails(tableName, indexName, columnNames, nbrOfScans.equals("0")
                        ? "** index not used!!**"
                        : "index was used " + nbrOfScans + " times");
            }
            return super.toIndexDetails(tableName, indexName, columnNames);
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
