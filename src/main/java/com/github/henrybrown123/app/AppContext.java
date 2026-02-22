package com.github.henrybrown123.app;

import com.github.henrybrown123.configuration.ConfigModule;
import com.github.henrybrown123.database.DatabaseProvider;
import com.github.henrybrown123.repository.PersistenceModule;
import com.github.henrybrown123.scheduling.SchedulingModule;
import com.github.henrybrown123.security.SecurityModule;

/**
 * Composes all application modules.
 * Infrastructure modules have no cross-module dependencies (except ConfigModule).
 * Feature modules depend on infrastructure.
 */
public class AppContext implements AutoCloseable {

    private final DatabaseProvider db;

    // infrastructure
    private final PersistenceModule persistence;
    private final SecurityModule security;
    private final ConfigModule config;

    // features
    private final SchedulingModule scheduling;

    public AppContext() throws Exception {
        // database
        this.db = new DatabaseProvider();

        // infrastructure
        this.persistence = new PersistenceModule(db);
        this.security = new SecurityModule();
        this.config = new ConfigModule(persistence);

        // features
        this.scheduling = new SchedulingModule(persistence, security, config);
    }

    public void start() {
        scheduling.start();
    }

    @Override
    public void close() {
        db.close();
    }
}
