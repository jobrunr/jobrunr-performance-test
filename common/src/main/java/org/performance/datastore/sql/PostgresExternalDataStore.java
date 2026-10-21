package org.performance.datastore.sql;

import static java.util.Optional.ofNullable;

/**
 * Postgres configured via DB_JDBC_URL, DB_USER and DB_PASSWORD.
 */
public class PostgresExternalDataStore extends AbstractSqlExternalDataStore {

    public PostgresExternalDataStore() {
        super("org.postgresql.Driver",
                env("DB_JDBC_URL", "jdbc:postgresql://localhost:5432/jobrunr_perf"),
                env("DB_USER", "jobrunr"),
                env("DB_PASSWORD", "jobrunr"));
    }

    @Override
    protected String getExternalDockerCommand() {
        return """
                docker run \\
                    --name postgres-test \\
                    -p 5432:5432 \\
                    -e POSTGRES_DB=jobrunr_perf \\
                    -e POSTGRES_USER=jobrunr \\
                    -e POSTGRES_PASSWORD=jobrunr \\
                    -d postgres:18.6 \\
                    -c max_wal_size=2GB \\
                    -c random_page_cost=1.1 \\
                    -c max_connections=500
                """;
    }

    private static String env(String name, String defaultValue) {
        return ofNullable(System.getenv(name)).filter(v -> !v.isBlank()).orElse(defaultValue);
    }
}
