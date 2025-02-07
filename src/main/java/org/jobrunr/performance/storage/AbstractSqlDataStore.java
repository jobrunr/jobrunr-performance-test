package org.jobrunr.performance.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.TimeZone;

import static java.time.Instant.now;

public abstract class AbstractSqlDataStore implements DataStore {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private final JdbcDatabaseContainer<?> container;
    private final String driverClassName;

    private HikariDataSource dataSource;

    public AbstractSqlDataStore(JdbcDatabaseContainer<?> container, String driverClassName) {
        this.container = container;
        this.driverClassName = driverClassName;
    }

    @Override
    public void start() {
        Instant startTime = Instant.now();
        container.setShmSize(1024L * 1024L * 1024L * 1024L); // 1GB
        container.start();
        logSqlContainerDetails(container, Duration.between(startTime, now()));
        dataSource = toHikariDataSource(container, driverClassName);
    }

    @Override
    public void stop() {
        container.stop();
    }

    @Override
    public StorageProvider getStorageProvider() {
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

    private static HikariDataSource toHikariDataSource(JdbcDatabaseContainer<?> container, String driverClassName) {
        return toHikariDataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword(), driverClassName);
    }

    private static HikariDataSource toHikariDataSource(String jdbcUrl, String userName, String password, String driverClassName) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(userName);
        config.setPassword(password);
        config.setDriverClassName(driverClassName);
        config.setAutoCommit(true);
        config.setMinimumIdle(40);
        config.setMaximumPoolSize(80);
        return new HikariDataSource(config);
    }

    public Instant getUpdatedAtOfLastSucceededJob() {
        try (Connection connection = dataSource.getConnection()) {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT max(updatedAt) AS last_updated_at from jobrunr_jobs where state = 'SUCCEEDED'");
            if (resultSet.next()) {
                return resultSet.getTimestamp("last_updated_at", Calendar.getInstance(TimeZone.getTimeZone(ZoneOffset.UTC))).toInstant();
            }
            throw new RuntimeException("Unable to find last updated at");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
