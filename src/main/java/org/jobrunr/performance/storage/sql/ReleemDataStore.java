package org.jobrunr.performance.storage.sql;

import com.zaxxer.hikari.HikariDataSource;
import org.jobrunr.performance.storage.DataStore;
import org.jobrunr.storage.StorageProvider;
import org.jobrunr.storage.sql.common.SqlStorageProviderFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Calendar;
import java.util.TimeZone;

import static org.jobrunr.performance.storage.sql.AbstractSqlDataStore.toHikariDataSource;

public class ReleemDataStore implements DataStore {

    private HikariDataSource dataSource;

    @Override
    public void start() {
        dataSource = toHikariDataSource("jdbc:mysql://localhost:3306/mysql",
                "root", "w41tebg99n1zf4k7ypT1OdUJ",
                "com.mysql.cj.jdbc.Driver");
    }

    @Override
    public void stop() {
        dataSource.close();
    }

    @Override
    public StorageProvider getStorageProvider(boolean logQueries) {
        return SqlStorageProviderFactory.using(dataSource);
    }

    public Instant getUpdatedAtOfLastSucceededJob() {
        try (Connection connection = dataSource.getConnection()) {
            Statement statement = connection.createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT max(updatedAt) AS last_updated_at from jobrunr_jobs where state = 'SUCCEEDED' AND recurringJobId IS NULL");
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
}
