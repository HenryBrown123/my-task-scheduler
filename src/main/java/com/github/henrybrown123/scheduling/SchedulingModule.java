package com.github.henrybrown123.scheduling;

import com.github.henrybrown123.configuration.ConfigModule;
import com.github.henrybrown123.security.SecurityModule;
import com.github.henrybrown123.repository.PersistenceModule;
import com.github.henrybrown123.scheduling.execution.JobExecutor;

/**
 * Feature: job scheduling and execution.
 *
 * <p>Depends on: {@link PersistenceModule}, {@link SecurityModule}, {@link ConfigModule}.
 * <p>Owns: JobExecutor, SchedulerService.
 */
public class SchedulingModule {
    private final SchedulerService scheduler;
    private final PersistenceModule persistence;
    private final SecurityModule security;

    public SchedulingModule(PersistenceModule persistence, SecurityModule security,
                            ConfigModule config) {
        this.persistence = persistence;
        this.security = security;

        var executor = new JobExecutor(
                persistence.executionDao(),
                security.credentialService());

        this.scheduler = new SchedulerService(
                persistence.jobDataRepo(),
                executor,
                config.configSync());
    }

    public void start() {
        reportMissingCredentials();
        scheduler.start();
    }

    private void reportMissingCredentials() {
        var missing = security.credentialService()
                .scanMissing(persistence.jobDataRepo().getAll());
        if (missing.isEmpty()) return;

        System.out.println("\nMissing credentials:");
        for (var m : missing) {
            System.out.println("  " + m.name() + " (" + m.type() + ") — blocks: " + m.jobIds());
        }
        System.out.println();
    }
}
