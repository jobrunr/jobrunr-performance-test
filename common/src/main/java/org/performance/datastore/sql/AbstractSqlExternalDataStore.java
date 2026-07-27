package org.performance.datastore.sql;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;

public abstract class AbstractSqlExternalDataStore extends AbstractSqlDataStore {

    private final HikariDataSource dataSource;

    public AbstractSqlExternalDataStore(String driverClassName, String jdbcUrl, String userName, String password) {
        try {
            this.dataSource = this.toHikariDataSource(jdbcUrl, userName, password, driverClassName);
            try (Connection connection = dataSource.getConnection()) {
                connection.isValid(5);
            }
        } catch (Exception e) {
            LOGGER.error("{} expects a running database with:", this.getClass().getSimpleName());
            LOGGER.error("  - jdbcUrl: {}", jdbcUrl);
            LOGGER.error("  - username: {}", userName);
            LOGGER.error("  - password: {}", password);
            LOGGER.error("  - driver: {}", driverClassName);
            LOGGER.error(" Example to start externally via Docker: {}", getExternalDockerCommand());
            throw new IllegalStateException("External database not reachable", e);
        }
    }

    protected abstract String getExternalDockerCommand();

    @Override
    public HikariDataSource dataSource() {
        return dataSource;
    }

    @Override
    public void stop() {
        dataSource.close();
    }
}
