package com.github.henrybrown123.configuration;

import com.github.henrybrown123.repository.PersistenceModule;

/**
 * Infrastructure: configuration file watching and sync.
 *
 * <p>Depends on: {@link PersistenceModule}.
 * <p>Owns: JobConfigSync.
 * <p>Calls forceSync on construction to seed the database.
 * Call {@link #start()} to begin watching for file changes.
 */
public record ConfigModule(JobConfigSync configSync) {
    public ConfigModule(PersistenceModule persistence) {
        this(createAndSync(persistence));
    }

    private static JobConfigSync createAndSync(PersistenceModule persistence) {
        JobConfigSync sync = new JobConfigSync(persistence.jobDataRepo());
        sync.forceSync();
        return sync;
    }

    public void start() {
        configSync.startWatching();
    }
}
