package org.jobrunr.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.JdbcDatabaseContainer;

import javax.sql.DataSource;
import java.time.Duration;
import java.time.Instant;

import static java.time.Instant.now;

public abstract class AbstractSqlDataStore implements DataStore {

    protected final Logger LOGGER = LoggerFactory.getLogger(getClass());

    private final JdbcDatabaseContainer<?> container;
    private HikariDataSource dataSource;

    public AbstractSqlDataStore(JdbcDatabaseContainer<?> container) {
        this.container = container;
    }

    @Override
    public void start() {
        Instant startTime = Instant.now();
        container.setShmSize(1024L * 1024L * 1024L * 1024L); // 1GB
        container.start();
        logSqlContainerDetails(container, Duration.between(startTime, now()));
        dataSource = toHikariDataSource(container);
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

    private static HikariDataSource toHikariDataSource(JdbcDatabaseContainer<?> container) {
        return toHikariDataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }

    private static HikariDataSource toHikariDataSource(String jdbcUrl, String userName, String password) {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(userName);
        config.setPassword(password);
        config.setAutoCommit(true);
        config.setMinimumIdle(40);
        config.setMaximumPoolSize(80);
        return new HikariDataSource(config);
    }
}
