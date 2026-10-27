package org.performance.datastore.sql;

import org.performance.datastore.DataStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Autovacuum sooner and unthrottled, for tables with constant updates.
 */
public class PostgresAutovacuum {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresAutovacuum.class);

    static final String AGGRESSIVE_SETTINGS = "autovacuum_vacuum_cost_delay = 0, autovacuum_vacuum_scale_factor = 0.01, "
            + "autovacuum_vacuum_threshold = 10000, autovacuum_vacuum_insert_scale_factor = 0.01, autovacuum_analyze_scale_factor = 0.02";
    // server-wide: needs a superuser and stays set until ALTER SYSTEM RESET (done by aws/bin/run-cluster.sh)
    static final String NAPTIME = "10s";

    public static void makeAggressive(DataStore dataStore, String table) throws SQLException {
        if (!(dataStore instanceof AbstractSqlDataStore sqlDataStore)) {
            LOGGER.warn("pg_aggressive_autovacuum is only supported for Postgres, ignoring it");
            return;
        }
        try (Connection connection = sqlDataStore.dataSource().getConnection(); Statement statement = connection.createStatement()) {
            if (!"PostgreSQL".equals(connection.getMetaData().getDatabaseProductName())) {
                LOGGER.warn("pg_aggressive_autovacuum is only supported for Postgres, ignoring it");
                return;
            }
            statement.execute("ALTER TABLE " + table + " SET (" + AGGRESSIVE_SETTINGS + ")");
            LOGGER.info("Autovacuum on {}: {}", table, AGGRESSIVE_SETTINGS);
            try {
                statement.execute("ALTER SYSTEM SET autovacuum_naptime = '" + NAPTIME + "'");
                statement.execute("SELECT pg_reload_conf()");
                LOGGER.info("autovacuum_naptime: {}", NAPTIME);
            } catch (SQLException e) {
                LOGGER.warn("Could not set autovacuum_naptime, keeping the server's: {}", e.getMessage());
            }
        }
    }
}
