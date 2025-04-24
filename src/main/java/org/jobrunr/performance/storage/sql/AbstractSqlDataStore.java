package org.jobrunr.performance.storage.sql;

import com.p6spy.engine.spy.P6DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jobrunr.performance.storage.AnalysingDataStore;
import org.jobrunr.performance.storage.DataStore;
import org.jobrunr.performance.utils.Memory;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.ThreadSafeStorageProvider.Query;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;

import static java.time.Instant.now;
import static org.jobrunr.performance.utils.Memory.Unit.gigabytes;

public abstract class AbstractSqlDataStore implements DataStore, AnalysingDataStore {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    protected final JdbcDatabaseContainer<?> sqlContainer;
    private final String driverClassName;

    private HikariDataSource dataSource;
    private LongSummaryStatistics connectionStatistics = new LongSummaryStatistics();

    public AbstractSqlDataStore(JdbcDatabaseContainer<?> sqlContainer) {
        this(sqlContainer, sqlContainer.getDriverClassName());
    }

    public AbstractSqlDataStore(JdbcDatabaseContainer<?> sqlContainer, String driverClassName) {
        this.sqlContainer = sqlContainer;
        this.driverClassName = driverClassName;
    }

    @Override
    public void start() {
        Instant startTime = Instant.now();
        sqlContainer.setShmSize(Memory.of(2, gigabytes).toBytes());
        sqlContainer.start();
        logSqlContainerDetails(sqlContainer, Duration.between(startTime, now()));
        dataSource = toHikariDataSource(sqlContainer, driverClassName);
    }

    @Override
    public void stop() {
        dataSource.close();
        sqlContainer.stop();
    }

    @Override
    public StorageProvider getStorageProvider(boolean logQueries) {
        if (logQueries) {
            return SqlStorageProviderFactory.using(new P6DataSource(dataSource));
        }
        return SqlStorageProviderFactory.using(dataSource);
    }

    protected DataSource getDataSource() {
        return dataSource;
    }

    private void logSqlContainerDetails(JdbcDatabaseContainer<?> sqlContainer, Duration duration) {
        LOGGER.info("=========================================================");
        LOGGER.info(" java version: " + System.getProperty("java.version"));
        LOGGER.info("   connection: " + sqlContainer.getJdbcUrl());
        LOGGER.info("         user: " + sqlContainer.getUsername());
        LOGGER.info("     password: " + sqlContainer.getPassword());
        LOGGER.info(" startup time: " + duration.getSeconds());
        LOGGER.info("=========================================================");
    }

    @Override
    public String getDataStoreSettings() {
        StringBuilder sb = new StringBuilder();
        sb.append("- connection pool size: ").append(dataSource.getMinimumIdle()).append(" min idle / ").append(dataSource.getMaximumPoolSize()).append(" max").append(System.lineSeparator());
        sb.append("   ").append(" connection timings (for analysis): ")
                .append("invocations: ").append(connectionStatistics.getCount())
                .append(" / total duration: ").append(Duration.ofNanos(connectionStatistics.getSum()))
                .append(" / avg duration: ").append(Duration.ofNanos((long) connectionStatistics.getAverage()))
                .append(" / min duration: ").append(Duration.ofNanos(connectionStatistics.getMin()))
                .append(" / max duration: ").append(Duration.ofNanos(connectionStatistics.getMax()))
                .append(System.lineSeparator());
        return sb.toString();

    }

    protected HikariDataSource toHikariDataSource(JdbcDatabaseContainer<?> container, String driverClassName) {
        return toHikariDataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword(), driverClassName);
    }

    protected HikariDataSource toHikariDataSource(String jdbcUrl, String userName, String password, String driverClassName) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(userName);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setMinimumIdle(20);
        config.setMaximumPoolSize(40);
        return new HikariDataSource(config);
    }

    public String explainQuery(Query query) {
        String actualQuery = query.getQueryWithValues();
        boolean isDeleteQuery = actualQuery.startsWith("delete ") || actualQuery.startsWith("DELETE ");
        if (isDeleteQuery) {
            actualQuery = actualQuery
                    .replace("delete ", "select * ")
                    .replace("DELETE ", "select * ");
        }
        boolean canAnalyze = actualQuery.toLowerCase().startsWith("select ");
        if (canAnalyze) {
            return isDeleteQuery
                    ? "-- delete replaced with select for query analysis" + System.lineSeparator() + explainQuery(actualQuery)
                    : explainQuery(actualQuery);
        } else {
            return explainExecute(actualQuery);
        }
    }

    public String explainQuery(String queryWithValues) {
        return explainAnalyseQuery("explain analyze " + queryWithValues);
    }

    public String explainExecute(String queryWithValues) {
        return explainAnalyseQuery("explain analyze " + queryWithValues);
    }

    public String explainAnalyseQuery(String analyzeQueryWithValues) {
        long startTime = System.nanoTime();
        try (Connection connection = dataSource.getConnection();) {
            long endTime = System.nanoTime();
            connectionStatistics.accept(endTime - startTime);
            return explainAnalyseQuery(connection, analyzeQueryWithValues);
        } catch (SQLException e) {
            return "Could not explain query plan for query '" + analyzeQueryWithValues + "' due to " + e.getMessage();
        }
    }

    public String explainAnalyseQuery(Connection connection, String analyzeQueryWithValues) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            ResultSet resultSet = statement.executeQuery(analyzeQueryWithValues);
            StringBuilder sb = new StringBuilder();
            while (resultSet.next()) {
                sb.append(resultSet.getString(1)).append(System.lineSeparator());
            }
            return sb.toString();
        }
    }

    public List<IndexDetails> getIndexDetails() {
        try (Connection connection = getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            List<IndexDetails> indexDetailsList = new ArrayList<>();

            try (ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");

                    if (!tableName.toLowerCase().startsWith("jobrunr_")) continue;

                    // Map to group column names by index name for the current table.
                    Map<String, List<String>> indexMap = new HashMap<>();
                    // Retrieve index info for the current table.
                    try (ResultSet indexes = metaData.getIndexInfo(null, null, tableName, false, false)) {
                        while (indexes.next()) {
                            String indexName = indexes.getString("INDEX_NAME");
                            String columnName = indexes.getString("COLUMN_NAME");

                            // Skip if index or column name is null.
                            if (indexName == null || columnName == null) {
                                continue;
                            }
                            indexMap.computeIfAbsent(indexName, k -> new ArrayList<>()).add(columnName);
                        }
                    }
                    // Convert the grouped index info into IndexDetails records.
                    for (Map.Entry<String, List<String>> entry : indexMap.entrySet()) {
                        indexDetailsList.add(toIndexDetails(tableName, entry.getKey(), entry.getValue()));
                    }
                }
            }
            return indexDetailsList;
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Could not get indexes for DB", e);
        }
    }

    protected IndexDetails toIndexDetails(String tableName, String indexName, List<String> columnNames) {
        return new IndexDetails(tableName, indexName, columnNames, "unknown");
    }
}
