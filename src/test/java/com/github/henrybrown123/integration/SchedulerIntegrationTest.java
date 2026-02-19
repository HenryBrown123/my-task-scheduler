package com.github.henrybrown123.integration;

import com.github.henrybrown123.configuration.JobConfigLoader;
import com.github.henrybrown123.database.Database;
import com.github.henrybrown123.execution.JobExecutor;
import com.github.henrybrown123.repository.ExecutionRepository;
import com.github.henrybrown123.repository.JobAggregateProvider;
import com.github.henrybrown123.repository.JobRepository;
import com.github.henrybrown123.repository.ScheduleRepository;
import com.github.henrybrown123.scheduling.JobScheduler;
import com.github.henrybrown123.scheduling.SchedulerService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for reading job configuration yaml and executing jobs on the scheduler thread.
 * In memory sqlite database used.
 *
 */
class SchedulerIntegrationTest {

    @TempDir
    Path tempDir;

    private Database database;
    private ExecutionRepository execRepo;
    private JobAggregateProvider provider;
    private JobScheduler scheduler;

    @BeforeEach
    void setUp() throws Exception {
        database = new Database();
        Connection conn = database.getConnection();

        var jobRepo = new JobRepository(conn);
        var scheduleRepo = new ScheduleRepository(conn);
        execRepo = new ExecutionRepository(conn);
        provider = new JobAggregateProvider(jobRepo, scheduleRepo, execRepo);
        scheduler = new JobScheduler();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (scheduler != null) {
            scheduler.shutdown();
        }
        if (database != null) {
            database.close();
        }
    }

    private SchedulerService createService(String yaml) throws Exception {
        Path configPath = Files.createTempFile(tempDir, "jobs-", ".yaml");
        Files.writeString(configPath, yaml);

        var executor = new JobExecutor(execRepo);
        var loader = new JobConfigLoader(configPath, provider);
        return new SchedulerService(scheduler, provider, executor, loader);
    }

    private void tickAndWait(SchedulerService service) {
        try {
            service.tick();
            scheduler.shutdown();
            boolean terminated = scheduler.awaitTermination(5, TimeUnit.SECONDS);
            if (!terminated) {
                fail("Scheduler did not terminate within 5 seconds — jobs may still be running");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail("Scheduler interrupted while waiting for termination", e);
        }
        catch (Exception e){
            throw new RuntimeException("Failed to complete tickAndWait", e);
        }
    }

    @Test
    void shouldLoadJobsAndExecuteOnTick() throws Exception {
        var service = createService("""
            jobs:
              - meta:
                  id: tick-job
                  name: "Tick Test"
                  description: "Runs on tick"
                  priority: medium
                  tags: []
                schedule:
                  type: simple
                  interval: 5m
                command:
                  type: cmd
                  command: "echo ticked"
                  interpreter: bash
            """);

        tickAndWait(service);

        var jobs = provider.getAllJobs();
        assertEquals(1, jobs.size());
        assertEquals("tick-job", jobs.get(0).meta().id());

        var lastExec = execRepo.getLastExecution("tick-job");
        assertTrue(lastExec.isPresent());
        assertEquals("complete", lastExec.get().lastRunStatus());
    }

    @Test
    void shouldExecuteMultipleDueJobs() throws Exception {
        var service = createService("""
            jobs:
              - meta:
                  id: job-a
                  name: "Job A"
                  description: "First job"
                  priority: high
                  tags: []
                schedule:
                  type: simple
                  interval: 5m
                command:
                  type: cmd
                  command: "echo a"
                  interpreter: bash
              - meta:
                  id: job-b
                  name: "Job B"
                  description: "Second job"
                  priority: low
                  tags: []
                schedule:
                  type: simple
                  interval: 5m
                command:
                  type: cmd
                  command: "echo b"
                  interpreter: bash
            """);

        tickAndWait(service);

        var execA = execRepo.getLastExecution("job-a");
        var execB = execRepo.getLastExecution("job-b");
        assertTrue(execA.isPresent());
        assertTrue(execB.isPresent());
        assertEquals("complete", execA.get().lastRunStatus());
        assertEquals("complete", execB.get().lastRunStatus());
    }

    @Test
    void shouldNotReExecuteJobThatIsNotDue() throws Exception {
        var service = createService("""
            jobs:
              - meta:
                  id: once-job
                  name: "Once Job"
                  description: "Should only run once"
                  priority: medium
                  tags: []
                schedule:
                  type: simple
                  interval: 1h
                command:
                  type: cmd
                  command: "echo once"
                  interpreter: bash
            """);

        // first tick — job is due, should execute
        tickAndWait(service);

        var firstExec = execRepo.getLastExecution("once-job");
        assertTrue(firstExec.isPresent());
        long firstExecId = firstExec.get().executionId();

        // second tick — job ran recently, 1h interval, should not execute again
        scheduler = new JobScheduler();
        var service2 = createService("""
            jobs:
              - meta:
                  id: once-job
                  name: "Once Job"
                  description: "Should only run once"
                  priority: medium
                  tags: []
                schedule:
                  type: simple
                  interval: 1h
                command:
                  type: cmd
                  command: "echo once"
                  interpreter: bash
            """);

        tickAndWait(service2);

        var secondExec = execRepo.getLastExecution("once-job");
        assertTrue(secondExec.isPresent());
        assertEquals(firstExecId, secondExec.get().executionId(), "Should not have created a new execution");
    }

    @Test
    void shouldHandleFailingJob() throws Exception {
        var service = createService("""
            jobs:
              - meta:
                  id: fail-job
                  name: "Failing Job"
                  description: "This job fails"
                  priority: medium
                  tags: []
                schedule:
                  type: simple
                  interval: 5m
                command:
                  type: cmd
                  command: "exit 1"
                  interpreter: bash
            """);

        tickAndWait(service);

        var lastExec = execRepo.getLastExecution("fail-job");
        assertTrue(lastExec.isPresent());
        assertEquals("failed", lastExec.get().lastRunStatus());
    }
}