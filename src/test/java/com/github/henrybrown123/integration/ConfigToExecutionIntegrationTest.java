package com.github.henrybrown123.integration;

import com.github.henrybrown123.configuration.JobConfigLoader;
import com.github.henrybrown123.database.DatabaseProvider;
import com.github.henrybrown123.scheduling.execution.JobExecutor;
import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.repository.sql.CredentialDao;
import com.github.henrybrown123.repository.sql.ExecutionDao;
import com.github.henrybrown123.repository.sql.JobDao;
import com.github.henrybrown123.repository.sql.ScheduleDao;
import com.github.henrybrown123.repository.JobDataRepository;
import com.github.henrybrown123.security.CredentialService;
import com.github.henrybrown123.testutil.FakeSecretProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for reading configuration file from configured source and deserializing into
 * the JobData and syncing to the database. In memory sqlite database used.
 */
class ConfigToExecutionIntegrationTest {

    @TempDir
    Path tempDir;

    private DatabaseProvider db;
    private ExecutionDao execRepo;
    private JobDataRepository jobDataRepo;
    private Path configPath;

    @BeforeEach
    void setUp() throws Exception {
        db = new DatabaseProvider();
        Connection conn = db.getConnection();

        JobDao jobDao = new JobDao(conn);
        ScheduleDao scheduleDao = new ScheduleDao(conn);
        CredentialDao credentialDao = new CredentialDao(conn);
        execRepo = new ExecutionDao(conn);
        jobDataRepo = new JobDataRepository(jobDao, scheduleDao, execRepo, credentialDao);

        configPath = tempDir.resolve("jobs.yaml");
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    @Test
    void shouldLoadConfigAndSyncToDatabase() throws Exception {
        Files.writeString(configPath, """
            jobs:
              - meta:
                  id: integration-job
                  name: "Integration Test Job"
                  description: "Test job for integration"
                  priority: high
                  tags:
                    - test
                    - integration
                schedule:
                  type: simple
                  interval: 5m
                command:
                  type: cmd
                  command: "echo hello"
                  interpreter: bash
            """);

        var loader = new JobConfigLoader(configPath);
        var configs = loader.read();
        jobDataRepo.sync(configs);

        List<JobData> jobs = jobDataRepo.getAll();
        assertEquals(1, jobs.size());
        assertEquals("integration-job", jobs.get(0).meta().id());
        assertEquals("simple", jobs.get(0).schedule().type());
    }

    @Test
    void shouldExecuteJobFromConfig() throws Exception {
        Files.writeString(configPath, """
            jobs:
              - meta:
                  id: exec-test
                  name: "Execution Test"
                  description: "Tests full execution"
                  priority: medium
                  tags: []
                schedule:
                  type: simple
                  interval: 1h
                command:
                  type: cmd
                  command: "echo 'executed successfully'"
                  interpreter: bash
            """);

        var loader = new JobConfigLoader(configPath);
        var configs = loader.read();
        jobDataRepo.sync(configs);

        JobData job = jobDataRepo.get("exec-test").orElseThrow();
        var fakeVault = new FakeSecretProvider();
        var credService = new CredentialService(fakeVault);
        JobExecutor executor = new JobExecutor(execRepo, credService);

        long execId = execRepo.createQueuedExecution("exec-test", "test");
        executor.runJob(job, execId).get(5, TimeUnit.SECONDS);

        var lastExec = execRepo.getLastExecution("exec-test");
        assertTrue(lastExec.isPresent());
        assertEquals("complete", lastExec.get().lastRunStatus());
        assertTrue(lastExec.get().executionId() > 0);
    }

    @Test
    void shouldHandleMultipleJobs() throws Exception {
        Files.writeString(configPath, """
            jobs:
              - meta:
                  id: job-1
                  name: "First Job"
                  description: "First test job"
                  priority: high
                  tags: []
                schedule:
                  type: simple
                  interval: 5m
                command:
                  type: cmd
                  command: "echo first"
                  interpreter: bash
              - meta:
                  id: job-2
                  name: "Second Job"
                  description: "Second test job"
                  priority: low
                  tags: []
                schedule:
                  type: simple
                  interval: 10m
                command:
                  type: cmd
                  command: "echo second"
                  interpreter: bash
            """);

        var loader = new JobConfigLoader(configPath);
        var configs = loader.read();
        jobDataRepo.sync(configs);

        List<JobData> jobs = jobDataRepo.getAll();
        assertEquals(2, jobs.size());
    }

    @Test
    void shouldUpdateExistingJobOnConfigChange() throws Exception {
        var loader = new JobConfigLoader(configPath);

        Files.writeString(configPath, """
            jobs:
              - meta:
                  id: update-job
                  name: "Original Name"
                  description: "Original"
                  priority: low
                  tags: []
                schedule:
                  type: simple
                  interval: 5m
                command:
                  type: cmd
                  command: "echo original"
                  interpreter: bash
            """);
        jobDataRepo.sync(loader.read());

        Files.writeString(configPath, """
            jobs:
              - meta:
                  id: update-job
                  name: "Updated Name"
                  description: "Updated"
                  priority: high
                  tags: []
                schedule:
                  type: simple
                  interval: 10m
                command:
                  type: cmd
                  command: "echo updated"
                  interpreter: bash
            """);
        jobDataRepo.sync(loader.read());

        List<JobData> jobs = jobDataRepo.getAll();
        assertEquals(1, jobs.size());
        assertEquals("Updated Name", jobs.get(0).meta().name());
    }
}
