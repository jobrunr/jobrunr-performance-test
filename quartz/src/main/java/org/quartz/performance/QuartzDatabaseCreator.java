package org.quartz.performance;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class QuartzDatabaseCreator {

    public static void install(DataSource dataSource, String quartzSqlFile) throws SQLException, IOException {
        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(quartzSqlFile)) {
                if (in == null) {
                    throw new IllegalStateException("Cannot find " + quartzSqlFile + " on the classpath. Is quartz on the runtime classpath?");
                }

                String sql = new String(in.readAllBytes(), StandardCharsets.UTF_8);

                // Very simple split: good enough for the Quartz schema file
                for (String raw : sql.split(";")) {
                    String statementSql = raw.trim();
                    if (statementSql.isEmpty()) {
                        continue;
                    }

                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute(statementSql);
                    }
                }

                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                throw e;
            }
        }
    }
}
