package com.github.henrybrown123.scheduling;

import com.github.henrybrown123.security.SecurityModule;
import com.github.henrybrown123.repository.PersistenceModule;
import com.github.henrybrown123.scheduling.execution.JobExecutor;

/**
 * Feature: job scheduling and execution.
 *
 * <p>Depends on: {@link PersistenceModule}, {@link SecurityModule}.
 * <p>Owns: JobExecutor, SchedulerService.
 */
public class SchedulingModule {
    private final SchedulerService scheduler;

    public SchedulingModule(PersistenceModule persistence, SecurityModule security) {
        var executor = new JobExecutor(
                persistence.executionDao(),
                security.credentialService());

        this.scheduler = new SchedulerService(
                persistence.jobDataRepo(),
                persistence.executionDao(),
                executor);
    }

    public void start() {
        scheduler.start();
    }
}
