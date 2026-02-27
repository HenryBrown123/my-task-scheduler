package com.github.henrybrown123.model;

import com.github.henrybrown123.model.job.Interpreter;
import com.github.henrybrown123.model.job.JobMeta;
import com.github.henrybrown123.model.job.Status;
import com.github.henrybrown123.model.job.command.JobCommandData;
import com.github.henrybrown123.model.job.execution.ExecutionType;
import com.github.henrybrown123.model.job.execution.JobExecutionData;
import com.github.henrybrown123.model.job.schedule.SimpleSchedule;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link JobData} scheduling predicates.
 * These methods gate all scheduling decisions.
 */
class JobDataTest {

    // ==================== isIdle() tests ====================

    @Test
    void shouldBeIdleWhenNoExecution() {
        JobData job = createJob(null);

        assertTrue(job.isIdle());
    }

    @Test
    void shouldBeIdleWhenLastExecutionComplete() {
        JobData job = createJob(executionWith(Status.COMPLETE));

        assertTrue(job.isIdle());
    }

    @Test
    void shouldBeIdleWhenLastExecutionFailed() {
        JobData job = createJob(executionWith(Status.FAILED));

        assertTrue(job.isIdle());
    }

    @Test
    void shouldBeIdleWhenLastExecutionTimedOut() {
        JobData job = createJob(executionWith(Status.TIMEOUT));

        assertTrue(job.isIdle());
    }

    @Test
    void shouldBeIdleWhenLastExecutionCancelled() {
        JobData job = createJob(executionWith(Status.CANCELLED));

        assertTrue(job.isIdle());
    }

    @Test
    void shouldNotBeIdleWhenQueued() {
        JobData job = createJob(executionWith(Status.QUEUED));

        assertFalse(job.isIdle());
    }

    @Test
    void shouldNotBeIdleWhenRunning() {
        JobData job = createJob(executionWith(Status.RUNNING));

        assertFalse(job.isIdle());
    }

    // ==================== isDue() tests ====================

    @Test
    void shouldBeDueWhenNeverExecuted() {
        // Job with no execution history should be due immediately
        JobData job = createJob(null);

        assertTrue(job.isDue());
    }

    @Test
    void shouldBeDueWhenIntervalElapsed() {
        // Job executed 2 hours ago with 1h interval should be due
        LocalDateTime twoHoursAgo = LocalDateTime.now().minusHours(2);
        JobData job = createJobWithInterval("1h", executionAt(twoHoursAgo, Status.COMPLETE));

        assertTrue(job.isDue());
    }

    @Test
    void shouldNotBeDueWhenRecentlyExecuted() {
        // Job executed 5 minutes ago with 1h interval should NOT be due
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        JobData job = createJobWithInterval("1h", executionAt(fiveMinutesAgo, Status.COMPLETE));

        assertFalse(job.isDue());
    }

    @Test
    void shouldBeDueWhenExactlyAtInterval() {
        // Job executed exactly 1 hour ago with 1h interval should be due
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1).minusSeconds(1);
        JobData job = createJobWithInterval("1h", executionAt(oneHourAgo, Status.COMPLETE));

        assertTrue(job.isDue());
    }

    @Test
    void shouldBeDueWhenLastExecutionFailed() {
        // Failed execution 2 hours ago — job is still due based on interval
        LocalDateTime twoHoursAgo = LocalDateTime.now().minusHours(2);
        JobData job = createJobWithInterval("1h", executionAt(twoHoursAgo, Status.FAILED));

        assertTrue(job.isDue());
    }

    // ==================== Helper methods ====================

    private JobData createJob(JobExecutionData execution) {
        return createJobWithInterval("1h", execution);
    }

    private JobData createJobWithInterval(String interval, JobExecutionData execution) {
        JobMeta meta = new JobMeta("test-job", "Test Job", "description", "medium", List.of());
        JobCommandData command = new JobCommandData(
                ExecutionType.CMD, "echo test", Interpreter.BASH, List.of());
        SimpleSchedule schedule = new SimpleSchedule(interval, null, null);
        return new JobData(meta, command, schedule, execution);
    }

    private JobExecutionData executionWith(Status status) {
        return new JobExecutionData(
                1L,
                status,
                LocalDateTime.now().minusHours(1),
                status == Status.COMPLETE || status == Status.FAILED ? LocalDateTime.now() : null,
                status.type.toLowerCase(),
                null,
                null,
                null
        );
    }

    private JobExecutionData executionAt(LocalDateTime startTime, Status status) {
        return new JobExecutionData(
                1L,
                status,
                startTime,
                status == Status.COMPLETE || status == Status.FAILED ? startTime.plusMinutes(1) : null,
                status.type.toLowerCase(),
                null,
                null,
                null
        );
    }
}
