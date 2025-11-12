<<<<<<<< HEAD:common/src/main/java/org/performance/datastore/sql/DB2DataStore.java
package org.performance.datastore.sql;
========
package org.jobrunr.performance.storage.sql;
>>>>>>>> main:common/src/main/java/org/performance/datastore/sql/sql/DB2DataStore.java

import org.testcontainers.db2.Db2Container;

import java.sql.Connection;
import java.sql.Statement;

public class DB2DataStore extends AbstractSqlDataStore<Db2Container> {

    public DB2DataStore() {
        super(new Db2Container("icr.io/db2_community/db2:12.1.0.0")
                        .withPrivilegedMode(true)
                        .acceptLicense(),
                "com.ibm.db2.jcc.DB2Driver");
    }

    @Override
    public void updateStatistics() {
        try (Connection connection = getDataSource().getConnection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("RUNSTATS ON TABLE jobrunr_jobs WITH DISTRIBUTION AND DETAILED INDEXES ALL;");
            LOGGER.info("UPDATED DB2 STATISTICS");
        } catch (java.sql.SQLException e) {
            throw new RuntimeException(e);
        }
    }
}
