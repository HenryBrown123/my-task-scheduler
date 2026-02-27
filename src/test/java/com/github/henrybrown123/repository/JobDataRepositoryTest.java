package com.github.henrybrown123.repository;

import com.github.henrybrown123.configuration.JobConfig;
import com.github.henrybrown123.database.DatabaseProvider;
import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.model.job.Interpreter;
import com.github.henrybrown123.model.job.JobMeta;
import com.github.henrybrown123.model.job.command.JobCommandData;
import com.github.henrybrown123.model.job.execution.ExecutionType;
import com.github.henrybrown123.model.job.schedule.SimpleSchedule;
import com.github.henrybrown123.repository.sql.CredentialDao;
import com.github.henrybrown123.repository.sql.ExecutionDao;
import com.github.henrybrown123.repository.sql.JobDao;
import com.github.henrybrown123.repository.sql.ScheduleDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link JobDataRepository} cache behavior.
 * Uses in-memory database.
 */
class JobDataRepositoryTest {

    private DatabaseProvider db;
    private JobDataRepository repo;
    private ExecutionDao execDao;

    @BeforeEach
    void setUp() {
        db = new DatabaseProvider();
        Connection conn = db.getConnection();

        JobDao jobDao = new JobDao(conn);
        ScheduleDao scheduleDao = new ScheduleDao(conn);
        CredentialDao credentialDao = new CredentialDao(conn);
        execDao = new ExecutionDao(conn);
        repo = new JobDataRepository(jobDao, scheduleDao, execDao, credentialDao);
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void shouldCacheConfigAfterSync() {
        // Arrange
        var configs = Map.of(
                1, createConfig("job-1", "echo 1"),
                2, createConfig("job-2", "echo 2")
        );

        // Act
        repo.sync(configs);
        List<JobData> first = repo.getAll();
        List<JobData> second = repo.getAll();

        // Assert — both calls return same data
        assertEquals(2, first.size());
        assertEquals(2, second.size());
        assertEquals(first.get(0).meta().id(), second.get(0).meta().id());
    }

    @Test
    void shouldInvalidateCacheOnResync() {
        // Arrange — initial sync
        var configA = Map.of(
                1, createConfig("job-a", "echo a")
        );
        repo.sync(configA);

        List<JobData> beforeResync = repo.getAll();
        assertEquals(1, beforeResync.size());
        assertEquals("job-a", beforeResync.get(0).meta().id());

        // Act — sync with different config
        var configB = Map.of(
                1, createConfig("job-b", "echo b")
        );
        repo.sync(configB);

        // Assert — cache should reflect new config
        List<JobData> afterResync = repo.getAll();
        assertEquals(1, afterResync.size());
        assertEquals("job-b", afterResync.get(0).meta().id());
    }

    @Test
    void shouldMergeFreshExecutionWithCachedConfig() {
        // Arrange — sync config and create an execution
        var configs = Map.of(1, createConfig("exec-job", "echo exec"));
        repo.sync(configs);

        // Simulate running the job
        long execId = execDao.createQueuedExecution("exec-job", "test");
        execDao.updateExecutionStatus(execId, "running");
        execDao.setExecutionAsCompleted(execId, "complete", 0);

        // Act
        List<JobData> jobs = repo.getAll();

        // Assert — execution data should be merged with cached config
        assertEquals(1, jobs.size());
        assertNotNull(jobs.get(0).execution(), "Execution should be merged from DB");
        assertEquals("complete", jobs.get(0).execution().lastRunStatus());
    }

    @Test
    void shouldReturnNullExecutionForNewJobs() {
        // Arrange
        var configs = Map.of(1, createConfig("new-job", "echo new"));
        repo.sync(configs);

        // Act
        List<JobData> jobs = repo.getAll();

        // Assert — new job with no execution history
        assertEquals(1, jobs.size());
        assertNull(jobs.get(0).execution(), "New job should have null execution");
    }

    @Test
    void shouldDeactivateJobsNotInNewConfig() {
        // Arrange — initial sync with 2 jobs
        var configWith2 = Map.of(
                1, createConfig("keep-job", "echo keep"),
                2, createConfig("remove-job", "echo remove")
        );
        repo.sync(configWith2);
        assertEquals(2, repo.getAll().size());

        // Act — sync with only 1 job
        var configWith1 = Map.of(
                1, createConfig("keep-job", "echo keep")
        );
        repo.sync(configWith1);

        // Assert — only the kept job should remain
        List<JobData> jobs = repo.getAll();
        assertEquals(1, jobs.size());
        assertEquals("keep-job", jobs.get(0).meta().id());
    }

    private JobConfig createConfig(String id, String command) {
        var meta = new JobMeta(id, "Test " + id, "description", "medium", List.of());
        var cmd = new JobCommandData(ExecutionType.CMD, command, Interpreter.BASH, List.of());
        var schedule = new SimpleSchedule("1h", null, null);
        return new JobConfig(meta, cmd, schedule);
    }
}
