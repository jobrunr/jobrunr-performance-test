package org.jobrunr.performance.storage.sql;

import com.p6spy.engine.spy.P6DataSource;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jobrunr.performance.storage.AnalysingDataStore;
import org.jobrunr.performance.storage.DataStore;
import org.jobrunr.performance.utils.Memory;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.ThreadSafeStorageProvider;
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
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import static java.time.Instant.now;
import static org.jobrunr.performance.utils.Memory.Unit.gigabytes;

public abstract class AbstractSqlDataStore implements DataStore {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    protected final JdbcDatabaseContainer<?> sqlContainer;
    private final String driverClassName;

    private HikariDataSource dataSource;

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

    public Instant getUpdatedAtOfLastSucceededJob() {
        try (Connection connection = dataSource.getConnection()) {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT max(updatedAt) AS last_updated_at from jobrunr_jobs where state = 'SUCCEEDED'");
            if (resultSet.next()) {
                Timestamp lastUpdatedAt = resultSet.getTimestamp("last_updated_at", Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC)));
                if (lastUpdatedAt == null) return Instant.EPOCH;
                return lastUpdatedAt.toInstant();
            }
            throw new RuntimeException("Unable to find last updated at");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public String explainQuery(ThreadSafeStorageProvider.Query query) {
        try (Connection connection = getDataSource().getConnection(); Statement statement = connection.createStatement()) {
            boolean canAnalyze = query.getQueryWithValues().toLowerCase().trim().startsWith("select ");
            ResultSet resultSet = statement.executeQuery((canAnalyze ? "explain analyze " : "explain ") + query.getQueryWithValues());
            StringBuilder sb = new StringBuilder();
            while (resultSet.next()) {
                sb.append(resultSet.getString(1)).append(System.lineSeparator());
            }
            return sb.toString();
        } catch (java.sql.SQLException e) {
            return "Could not explain query plan for query '" + query.getQueryWithValues() + "' due to " + e.getMessage();
        }
    }

    public List<AnalysingDataStore.IndexDetails> getIndexDetails() {
        try (Connection connection = getDataSource().getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            List<AnalysingDataStore.IndexDetails> indexDetailsList = new ArrayList<>();

            // Retrieve all tables (adjust catalog, schema, and tablePattern as needed)
            try (ResultSet tables = metaData.getTables(null, null, "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String tableName = tables.getString("TABLE_NAME");

                    if (!tableName.startsWith("jobrunr_")) continue;

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
                        indexDetailsList.add(new AnalysingDataStore.IndexDetails(tableName, entry.getKey(), entry.getValue()));
                    }
                }
            }
            return indexDetailsList;
        } catch (java.sql.SQLException e) {
            throw new RuntimeException("Could not get indexes for DB", e);
        }
    }
}
