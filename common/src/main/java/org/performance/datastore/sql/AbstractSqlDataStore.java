package org.performance.datastore.sql;

import com.zaxxer.hikari.HikariDataSource;
import org.performance.datastore.AnalysingDataStore;
import org.performance.datastore.DataStore;
import org.performance.datastore.DataStoreQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;

public abstract class AbstractSqlDataStore implements DataStore, AnalysingDataStore {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private final LongSummaryStatistics connectionStatistics = new LongSummaryStatistics();

    @Override
    public void start() {
        logSqlDataStoreDetails(Duration.ZERO);
    }

    public abstract HikariDataSource dataSource();

    protected void logSqlDataStoreDetails(Duration duration) {
        logSqlDataStoreDetails(dataSource(), Duration.ZERO);
    }

    protected void logSqlDataStoreDetails(HikariDataSource dataSource, Duration duration) {
        LOGGER.info("=========================================================");
        LOGGER.info(" java version: {}", System.getProperty("java.version"));
        LOGGER.info("   connection: {}", dataSource.getJdbcUrl());
        LOGGER.info("         user: {}", dataSource.getUsername());
        LOGGER.info("     password: {}", dataSource.getPassword());
        if (!duration.isZero()) {
            LOGGER.info(" startup time s: {}", duration.getSeconds());
        }
        LOGGER.info("=========================================================");
    }

    @Override
    public String getDataStoreSettings() {
        HikariDataSource dataSource = dataSource();
        return "- connection pool size: " + dataSource.getMinimumIdle() + " min idle / " + dataSource.getMaximumPoolSize() + " max" + System.lineSeparator() +
                "    connection timings (for analysis): " +
                "invocations: " + connectionStatistics.getCount() +
                " / total duration: " + Duration.ofNanos(connectionStatistics.getSum()) +
                " / avg duration: " + Duration.ofNanos((long) connectionStatistics.getAverage()) +
                " / min duration: " + Duration.ofNanos(connectionStatistics.getMin()) +
                " / max duration: " + Duration.ofNanos(connectionStatistics.getMax()) +
                System.lineSeparator();
    }

    @Override
    public String getNameAndVersion() {
        try (Connection connection = dataSource().getConnection()) {
            DatabaseMetaData databaseMetaData = connection.getMetaData();
            String productName = databaseMetaData.getDatabaseProductName();
            String productVersion = databaseMetaData.getDatabaseProductVersion();
            String driverName = databaseMetaData.getDriverName();
            String driverVersion = databaseMetaData.getDriverVersion();
            return productName + ":" + productVersion + " connecting via (" + driverName + ":" + driverVersion + ")";
        } catch (SQLException e) {
            return "could not query database product name";
        }
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
        try (Connection connection = dataSource().getConnection()) {
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
        try (Connection connection = dataSource().getConnection()) {
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
