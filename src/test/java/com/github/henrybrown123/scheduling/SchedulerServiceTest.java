package com.github.henrybrown123.scheduling;

import com.github.henrybrown123.database.DatabaseProvider;
import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.model.job.Interpreter;
import com.github.henrybrown123.model.job.JobMeta;
import com.github.henrybrown123.model.job.Status;
import com.github.henrybrown123.model.job.command.JobCommandData;
import com.github.henrybrown123.model.job.execution.ExecutionType;
import com.github.henrybrown123.model.job.execution.JobExecutionData;
import com.github.henrybrown123.model.job.schedule.SimpleSchedule;
import com.github.henrybrown123.repository.JobDataRepository;
import com.github.henrybrown123.repository.sql.ExecutionDao;
import com.github.henrybrown123.scheduling.execution.JobExecutor;
import com.github.henrybrown123.security.CredentialService;
import com.github.henrybrown123.testutil.FakeSecretProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SchedulerService} tick behavior.
 * Uses in-memory database to verify filtering and scheduling logic.
 */
class SchedulerServiceTest {

    private DatabaseProvider db;
    private ExecutionDao execDao;
    private SchedulerService scheduler;

    @BeforeEach
    void setUp() {
        db = new DatabaseProvider();
        Connection conn = db.getConnection();

        execDao = new ExecutionDao(conn);
    }

    @AfterEach
    void tearDown() {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (db != null) {
            db.close();
        }
    }

    @Test
    void shouldOnlyScheduleIdleJobs() throws Exception {
        // Arrange: Create a mock repository that returns jobs with different states
        var idleJob = createJob("idle-job", null);
        var queuedJob = createJob("queued-job", executionWith(Status.QUEUED));
        var runningJob = createJob("running-job", executionWith(Status.RUNNING));

        var mockRepo = new MockJobDataRepository(List.of(idleJob, queuedJob, runningJob));
        var credService = new CredentialService(new FakeSecretProvider());
        var executor = new JobExecutor(execDao, credService);
        scheduler = new SchedulerService(mockRepo, execDao, executor);

        // Act
        scheduler.tick();
        scheduler.shutdown();
        scheduler.awaitTermination(5, TimeUnit.SECONDS);

        // Assert: only the idle job should have a queued execution
        var idleExec = execDao.getLastExecution("idle-job");
        var queuedExec = execDao.getLastExecution("queued-job");
        var runningExec = execDao.getLastExecution("running-job");

        assertTrue(idleExec.isPresent(), "Idle job should have been scheduled");
        assertFalse(queuedExec.isPresent(), "Already queued job should NOT be scheduled again");
        assertFalse(runningExec.isPresent(), "Running job should NOT be scheduled again");
    }

    @Test
    void shouldNotScheduleJobThatIsNotDue() throws Exception {
        // Job with 1h interval, executed 5 minutes ago — not due
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        var notDueJob = createJobWithInterval("not-due-job", "1h",
                executionAt(fiveMinutesAgo, Status.COMPLETE));

        var mockRepo = new MockJobDataRepository(List.of(notDueJob));
        var credService = new CredentialService(new FakeSecretProvider());
        var executor = new JobExecutor(execDao, credService);
        scheduler = new SchedulerService(mockRepo, execDao, executor);

        // Act
        scheduler.tick();
        scheduler.shutdown();
        scheduler.awaitTermination(5, TimeUnit.SECONDS);

        // Assert: job should NOT be scheduled (execution count should be 0)
        var history = execDao.getHistory("not-due-job", 10);
        assertEquals(0, history.size(), "Job should not have been scheduled — not due yet");
    }

    @Test
    void shouldScheduleJobThatIsDue() throws Exception {
        // Job with 1h interval, executed 2 hours ago — due
        LocalDateTime twoHoursAgo = LocalDateTime.now().minusHours(2);
        var dueJob = createJobWithInterval("due-job", "1h",
                executionAt(twoHoursAgo, Status.COMPLETE));

        var mockRepo = new MockJobDataRepository(List.of(dueJob));
        var credService = new CredentialService(new FakeSecretProvider());
        var executor = new JobExecutor(execDao, credService);
        scheduler = new SchedulerService(mockRepo, execDao, executor);

        // Act
        scheduler.tick();
        scheduler.shutdown();
        scheduler.awaitTermination(5, TimeUnit.SECONDS);

        // Assert
        var exec = execDao.getLastExecution("due-job");
        assertTrue(exec.isPresent(), "Due job should have been scheduled");
    }

    @Test
    void shouldScheduleJobThatNeverRan() throws Exception {
        // Job with no execution history — due immediately
        var neverRanJob = createJob("never-ran-job", null);

        var mockRepo = new MockJobDataRepository(List.of(neverRanJob));
        var credService = new CredentialService(new FakeSecretProvider());
        var executor = new JobExecutor(execDao, credService);
        scheduler = new SchedulerService(mockRepo, execDao, executor);

        // Act
        scheduler.tick();
        scheduler.shutdown();
        scheduler.awaitTermination(5, TimeUnit.SECONDS);

        // Assert
        var exec = execDao.getLastExecution("never-ran-job");
        assertTrue(exec.isPresent(), "Never-ran job should be scheduled immediately");
    }

    // ==================== Helper classes and methods ====================

    /**
     * Mock repository that returns a fixed list of jobs.
     * Allows testing tick behavior without touching the real database for config.
     */
    static class MockJobDataRepository extends JobDataRepository {
        private final List<JobData> jobs;

        MockJobDataRepository(List<JobData> jobs) {
            super(null, null, null, null);
            this.jobs = jobs;
        }

        @Override
        public List<JobData> getAll() {
            return jobs;
        }
    }

    private JobData createJob(String id, JobExecutionData execution) {
        return createJobWithInterval(id, "5m", execution);
    }

    private JobData createJobWithInterval(String id, String interval, JobExecutionData execution) {
        JobMeta meta = new JobMeta(id, "Test " + id, "description", "medium", List.of());
        JobCommandData command = new JobCommandData(
                ExecutionType.CMD, "echo " + id, Interpreter.BASH, List.of());
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
