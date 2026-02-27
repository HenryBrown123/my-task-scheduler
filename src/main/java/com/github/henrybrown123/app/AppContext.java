package com.github.henrybrown123.app;

import com.github.henrybrown123.configuration.ConfigModule;
import com.github.henrybrown123.database.DatabaseProvider;
import com.github.henrybrown123.monitoring.MonitoringModule;
import com.github.henrybrown123.repository.PersistenceModule;
import com.github.henrybrown123.scheduling.SchedulingModule;
import com.github.henrybrown123.security.SecurityModule;

/**
 * Composes all application modules.
 * Infrastructure first, features second.
 *
 * <p>Construction order matters — each module depends only
 * on modules constructed before it:
 * db → persistence → config → security → monitoring → scheduling
 */
public class AppContext implements AutoCloseable {

    private final DatabaseProvider db;

    // infrastructure
    private final PersistenceModule persistence;
    private final ConfigModule config;
    private final SecurityModule security;
    private final MonitoringModule monitoring;

    // features
    private final SchedulingModule scheduling;

    public AppContext() {
        // database
        this.db = new DatabaseProvider();

        // infrastructure
        this.persistence = new PersistenceModule(db);
        this.config = new ConfigModule(persistence);
        this.security = new SecurityModule(persistence);
        this.monitoring = new MonitoringModule(persistence.executionDao());

        // features
        this.scheduling = new SchedulingModule(persistence, security);
    }

    public void start() {
        config.start();
        scheduling.start();
        monitoring.start();
    }

    @Override
    public void close() {
        db.close();
    }
}
