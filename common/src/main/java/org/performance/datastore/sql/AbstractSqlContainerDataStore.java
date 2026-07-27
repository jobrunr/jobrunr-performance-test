package org.performance.datastore.sql;

import com.zaxxer.hikari.HikariDataSource;
import org.testcontainers.containers.JdbcDatabaseContainer;

import java.time.Duration;
import java.time.Instant;

import static java.time.Instant.now;

public class AbstractSqlContainerDataStore<T extends JdbcDatabaseContainer<T>> extends AbstractSqlDataStore {

    private final T sqlContainer;
    private final String driverClassName;
    private HikariDataSource dataSource;

    public AbstractSqlContainerDataStore(T sqlContainer) {
        this(sqlContainer, sqlContainer.getDriverClassName());
    }

    public AbstractSqlContainerDataStore(T sqlContainer, String driverClassName) {
        this.sqlContainer = sqlContainer;
        this.driverClassName = driverClassName;
    }

    @Override
    public void start() {
        Instant startTime = Instant.now();
        sqlContainer.start();
        dataSource = toHikariDataSource(sqlContainer, driverClassName);
        logSqlDataStoreDetails(Duration.between(startTime, now()));
    }

    @Override
    public void stop() {
        dataSource.close();
        sqlContainer.stop();
    }

    @Override
    public HikariDataSource dataSource() {
        return dataSource;
    }

    protected HikariDataSource toHikariDataSource(JdbcDatabaseContainer<?> container, String driverClassName) {
        return toHikariDataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword(), driverClassName);
    }
}
