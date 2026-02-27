package com.github.henrybrown123.app;

import com.github.henrybrown123.configuration.ConfigModule;
import com.github.henrybrown123.database.DatabaseProvider;
import com.github.henrybrown123.repository.PersistenceModule;
import com.github.henrybrown123.scheduling.SchedulingModule;
import com.github.henrybrown123.security.SecurityModule;

/**
 * Composes all application modules.
 * Infrastructure first, features second.
 *
 * <p>Construction order matters — each module depends only
 * on modules constructed before it:
 * db → persistence → config (syncs jobs) → security (verifies credentials) → scheduling
 */
public class AppContext implements AutoCloseable {

    private final DatabaseProvider db;

    // infrastructure
    private final PersistenceModule persistence;
    private final ConfigModule config;
    private final SecurityModule security;

    // features
    private final SchedulingModule scheduling;

    public AppContext() {
        // database
        this.db = new DatabaseProvider();

        // infrastructure
        this.persistence = new PersistenceModule(db);
        this.config = new ConfigModule(persistence);
        this.security = new SecurityModule(persistence);

        // features
        this.scheduling = new SchedulingModule(persistence, security);
    }

    public void start() {
        config.start();
        scheduling.start();
    }

    @Override
    public void close() {
        db.close();
    }
}
