package com.github.henrybrown123.integration;

import com.github.henrybrown123.database.DatabaseProvider;
import com.github.henrybrown123.repository.sql.ExecutionDao;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link ExecutionDao#getActiveExecutionCounts()}.
 * In-memory SQLite database used.
 */
class ActiveExecutionCountsIntegrationTest {

    private DatabaseProvider db;
    private ExecutionDao executionDao;

    @BeforeEach
    void setUp() {
        db = new DatabaseProvider();
        executionDao = new ExecutionDao(db.getConnection());
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    @Test
    void shouldReturnZerosWhenNoExecutions() {
        var counts = executionDao.getActiveExecutionCounts();

        assertEquals(0, counts.queued());
        assertEquals(0, counts.running());
    }

    @Test
    void shouldCountQueuedExecutions() {
        createExecution("job-1", "queued");
        createExecution("job-2", "queued");
        createExecution("job-3", "queued");

        var counts = executionDao.getActiveExecutionCounts();

        assertEquals(3, counts.queued());
        assertEquals(0, counts.running());
    }

    @Test
    void shouldCountRunningExecutions() {
        long execId = createExecution("job-1", "queued");
        executionDao.updateExecutionStatus(execId, "running");

        var counts = executionDao.getActiveExecutionCounts();

        assertEquals(0, counts.queued());
        assertEquals(1, counts.running());
    }

    @Test
    void shouldCountBothQueuedAndRunning() {
        createExecution("job-1", "queued");
        createExecution("job-2", "queued");

        long exec3 = createExecution("job-3", "queued");
        executionDao.updateExecutionStatus(exec3, "running");

        long exec4 = createExecution("job-4", "queued");
        executionDao.updateExecutionStatus(exec4, "running");

        var counts = executionDao.getActiveExecutionCounts();

        assertEquals(2, counts.queued());
        assertEquals(2, counts.running());
    }

    @Test
    void shouldNotCountCompletedExecutions() {
        long execId = createExecution("job-1", "queued");
        executionDao.updateExecutionStatus(execId, "running");
        executionDao.setExecutionAsCompleted(execId, "complete", 0);

        var counts = executionDao.getActiveExecutionCounts();

        assertEquals(0, counts.queued());
        assertEquals(0, counts.running());
    }

    @Test
    void shouldNotCountFailedExecutions() {
        long execId = createExecution("job-1", "queued");
        executionDao.updateExecutionStatus(execId, "running");
        executionDao.setExecutionAsCompleted(execId, "failed", 1);

        var counts = executionDao.getActiveExecutionCounts();

        assertEquals(0, counts.queued());
        assertEquals(0, counts.running());
    }

    @Test
    void shouldNotCountCancelledExecutions() {
        long execId = createExecution("job-1", "queued");
        executionDao.setExecutionAsCompleted(execId, "cancelled", -1);

        var counts = executionDao.getActiveExecutionCounts();

        assertEquals(0, counts.queued());
        assertEquals(0, counts.running());
    }

    @Test
    void shouldHandleMixOfTerminalAndActiveExecutions() {
        // 2 completed
        long exec1 = createExecution("job-1", "queued");
        executionDao.setExecutionAsCompleted(exec1, "complete", 0);
        long exec2 = createExecution("job-2", "queued");
        executionDao.setExecutionAsCompleted(exec2, "failed", 1);

        // 1 queued
        createExecution("job-3", "queued");

        // 1 running
        long exec4 = createExecution("job-4", "queued");
        executionDao.updateExecutionStatus(exec4, "running");

        // 1 cancelled
        long exec5 = createExecution("job-5", "queued");
        executionDao.setExecutionAsCompleted(exec5, "cancelled", -1);

        var counts = executionDao.getActiveExecutionCounts();

        assertEquals(1, counts.queued());
        assertEquals(1, counts.running());
    }

    private long createExecution(String jobId, String triggeredBy) {
        return executionDao.createQueuedExecution(jobId, triggeredBy);
    }
}
