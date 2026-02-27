package com.github.henrybrown123.repository;

import com.github.henrybrown123.database.DatabaseProvider;
import com.github.henrybrown123.repository.sql.CredentialDao;
import com.github.henrybrown123.repository.sql.ExecutionDao;
import com.github.henrybrown123.repository.sql.JobDao;
import com.github.henrybrown123.repository.sql.ScheduleDao;

import java.sql.Connection;

/**
 * Infrastructure: persistence layer.
 *
 * <p>Requires: {@link DatabaseProvider}.
 * <p>Owns: all DAOs, JobDataRepository.
 */
public class PersistenceModule {
    private final ExecutionDao executionDao;
    private final JobDataRepository jobDataRepo;

    public PersistenceModule(DatabaseProvider db) {
        Connection conn = db.getConnection();
        JobDao jobDao = new JobDao(conn);
        ScheduleDao scheduleDao = new ScheduleDao(conn);
        CredentialDao credentialDao = new CredentialDao(conn);
        this.executionDao = new ExecutionDao(conn);
        this.jobDataRepo = new JobDataRepository(jobDao, scheduleDao, executionDao, credentialDao);
    }

    public ExecutionDao executionDao() {
        return executionDao;
    }

    public JobDataRepository jobDataRepo() {
        return jobDataRepo;
    }
}
