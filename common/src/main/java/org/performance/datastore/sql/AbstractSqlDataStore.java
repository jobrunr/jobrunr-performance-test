package org.performance.datastore.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.performance.datastore.AbstractDataStore;
import org.performance.datastore.AnalysingDataStore;
import org.performance.datastore.DataStore;
import org.performance.datastore.DataStoreQuery;
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

public abstract class AbstractSqlDataStore<T extends JdbcDatabaseContainer<T>> extends AbstractDataStore<T> implements DataStore, AnalysingDataStore {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private final String driverClassName;

    private final LongSummaryStatistics connectionStatistics = new LongSummaryStatistics();
    private HikariDataSource dataSource;

    public AbstractSqlDataStore(T sqlContainer) {
        this(sqlContainer, sqlContainer.getDriverClassName());
    }

    public AbstractSqlDataStore(T sqlContainer, String driverClassName) {
        super(sqlContainer);
        this.driverClassName = driverClassName;
    }

    @Override
    public void start() {
        Instant startTime = Instant.now();
        super.start();
        logSqlContainerDetails(container, Duration.between(startTime, now()));
        dataSource = toHikariDataSource(container, driverClassName);
    }

    @Override
    public void stop() {
        dataSource.close();
        super.stop();
    }

    public DataSource getDataSource() {
        return dataSource;
    }

    private void logSqlContainerDetails(JdbcDatabaseContainer<?> sqlContainer, Duration duration) {
        LOGGER.info("=========================================================");
        LOGGER.info(" java version: {}", System.getProperty("java.version"));
        LOGGER.info("   connection: {}", sqlContainer.getJdbcUrl());
        LOGGER.info("         user: {}", sqlContainer.getUsername());
        LOGGER.info("     password: {}", sqlContainer.getPassword());
        LOGGER.info(" startup time s: {}", duration.getSeconds());
        LOGGER.info("=========================================================");
    }

    @Override
    public String getDataStoreSettings() {
        return "- connection pool size: " + dataSource.getMinimumIdle() + " min idle / " + dataSource.getMaximumPoolSize() + " max" + System.lineSeparator() +
                "    connection timings (for analysis): " +
                "invocations: " + connectionStatistics.getCount() +
                " / total duration: " + Duration.ofNanos(connectionStatistics.getSum()) +
                " / avg duration: " + Duration.ofNanos((long) connectionStatistics.getAverage()) +
                " / min duration: " + Duration.ofNanos(connectionStatistics.getMin()) +
                " / max duration: " + Duration.ofNanos(connectionStatistics.getMax()) +
                System.lineSeparator();
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

    @Override
    public String explainQuery(DataStoreQuery query) {
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
        try (Connection connection = dataSource.getConnection()) {
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

    @Override
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
        } catch (SQLException e) {
            throw new RuntimeException("Could not get indexes for DB", e);
        }
    }

    protected IndexDetails toIndexDetails(String tableName, String indexName, List<String> columnNames) {
        return new IndexDetails(tableName, indexName, columnNames, "unknown");
    }
}
