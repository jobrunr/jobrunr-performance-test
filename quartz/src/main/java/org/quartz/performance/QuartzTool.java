package org.quartz.performance;

import org.performance.datastore.DataStore;
import org.performance.datastore.sql.AbstractSqlDataStore;
import org.performance.datastore.sql.DB2DataStore;
import org.performance.datastore.sql.MariaDBDataStore;
import org.performance.datastore.sql.MySQLDataStore;
import org.performance.datastore.sql.OracleDataStore;
import org.performance.datastore.sql.PostgresDataStore;
import org.performance.datastore.sql.SQLServerDataStore;
import org.performance.scenario.Scenario;
import org.performance.scenario.ScenarioMonitor;
import org.performance.tools.Tool;
import org.performance.utils.JarUtils;
import org.quartz.Scheduler;
import org.quartz.impl.StdSchedulerFactory;
import org.quartz.performance.scenario.QuartzScenarioMonitor;

import java.time.Duration;
import java.util.Properties;

public class QuartzTool implements Tool {

    private Scheduler scheduler;

    @Override
    public String getName() {
        return JarUtils.getToolName(Scheduler.class);
    }

    @Override
    public String getVersion() {
        return JarUtils.getToolVersion(Scheduler.class);
    }

    @Override
    public void initialize(DataStore dataStore, Scenario scenario) throws Exception {
        if (!(dataStore instanceof AbstractSqlDataStore<?>)) {
            throw new IllegalArgumentException("Quartz DataStore must be an instance of AbstractSqlDataStore");
        }
        this.initialize((AbstractSqlDataStore<?>) dataStore);
    }

    public void initialize(AbstractSqlDataStore<?> dataStore) throws Exception {
        Properties props = new Properties();

        // Basic scheduler settings
        props.setProperty("org.quartz.scheduler.instanceName", "BulkScheduler");
        props.setProperty("org.quartz.scheduler.instanceId", "AUTO");

        // Thread pool
        props.setProperty("org.quartz.threadPool.class", "org.quartz.simpl.SimpleThreadPool");
        props.setProperty("org.quartz.threadPool.threadCount", "50");
        props.setProperty("org.quartz.threadPool.threadPriority", "5");

        // Install and use JDBC JobStoreTX (persistent)
        installSchemaAndConfigureDataSource(props, dataStore);

        // Optional: better performance with batch triggers
        props.setProperty("org.quartz.scheduler.batchTriggerAcquisitionMaxCount", "100");

        // Create scheduler programmatically
        StdSchedulerFactory factory = new StdSchedulerFactory(props);
        this.scheduler = factory.getScheduler();
    }

    public Scheduler getScheduler() {
        return scheduler;
    }

    @Override
    public ScenarioMonitor createScenarioMonitor(long createdJobs, Duration maxScenarioDuration) throws Exception {
        QuartzScenarioMonitor quartzScenarioMonitor = new QuartzScenarioMonitor(createdJobs, maxScenarioDuration);
        scheduler.getListenerManager().addJobListener(quartzScenarioMonitor);
        return quartzScenarioMonitor;
    }

    @Override
    public void start() throws Exception {
        scheduler.start();
    }

    @Override
    public void stop() throws Exception {
        scheduler.shutdown(true);
    }

    private void installSchemaAndConfigureDataSource(Properties props, AbstractSqlDataStore<?> dataStore) throws Exception {
        props.setProperty("org.quartz.jobStore.class", "org.quartz.impl.jdbcjobstore.JobStoreTX");
        props.setProperty("org.quartz.jobStore.useProperties", "false");
        props.setProperty("org.quartz.jobStore.dataSource", "myDS");
        props.setProperty("org.quartz.jobStore.tablePrefix", "QRTZ_");
        props.setProperty("org.quartz.jobStore.isClustered", "true");
        props.setProperty("org.quartz.jobStore.misfireThreshold", "6000000");
        props.setProperty("org.quartz.dataSource.myDS.provider", "hikaricp");
        props.setProperty("org.quartz.dataSource.myDS.driver", dataStore.getContainer().getDriverClassName());
        props.setProperty("org.quartz.dataSource.myDS.URL", dataStore.getContainer().getJdbcUrl());
        props.setProperty("org.quartz.dataSource.myDS.user", dataStore.getContainer().getUsername());
        props.setProperty("org.quartz.dataSource.myDS.password", dataStore.getContainer().getPassword());
        props.setProperty("org.quartz.dataSource.myDS.maxConnections", "20");

        switch (dataStore) {
            case DB2DataStore db2DataStore:
                props.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
                QuartzDatabaseCreator.install(dataStore.getDataSource(), "org/quartz/impl/jdbcjobstore/tables_db2_v95.sql");
                break;
            case MariaDBDataStore mariaDBDataStore:
                props.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
                QuartzDatabaseCreator.install(dataStore.getDataSource(), "org/quartz/impl/jdbcjobstore/tables_mysql_innodb.sql");
                break;
            case MySQLDataStore mySQLDataStore:
                props.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.StdJDBCDelegate");
                QuartzDatabaseCreator.install(dataStore.getDataSource(), "org/quartz/impl/jdbcjobstore/tables_mysql_innodb.sql");
                break;
            case OracleDataStore oracleDataStore:
                props.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.OracleDelegate");
                QuartzDatabaseCreator.install(dataStore.getDataSource(), "org/quartz/impl/jdbcjobstore/tables_oracle23.sql");
                break;
            case PostgresDataStore postgres:
                props.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.PostgreSQLDelegate");
                QuartzDatabaseCreator.install(dataStore.getDataSource(), "org/quartz/impl/jdbcjobstore/tables_postgres.sql");
                break;
            case SQLServerDataStore sqlServerDataStore:
                props.setProperty("org.quartz.jobStore.driverDelegateClass", "org.quartz.impl.jdbcjobstore.MSSQLDelegate");
                QuartzDatabaseCreator.runSQLScript(
                        dataStore.getDataSource(),
                        QuartzDatabaseCreator.readSQLScript("org/quartz/impl/jdbcjobstore/tables_sqlServer.sql")
                                .replace("GO", ";")
                                .replace("enter_db_name_here", "master")
                );
                break;
            default:
                throw new IllegalArgumentException("Quartz DataStore must be an instance of PostgresDataStore");
        }
    }
}
