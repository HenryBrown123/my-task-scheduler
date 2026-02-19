package com.github.henrybrown123.integration;

import com.github.henrybrown123.configuration.JobConfigLoader;
import com.github.henrybrown123.database.Database;
import com.github.henrybrown123.execution.JobExecutor;
import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.repository.ExecutionRepository;
import com.github.henrybrown123.repository.JobAggregateProvider;
import com.github.henrybrown123.repository.JobRepository;
import com.github.henrybrown123.repository.ScheduleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for reading configuratino file from configured source and deserializing into
 * the JobData and syncing to the database. In memory sqlite database used.
 *
 */
class ConfigToExecutionIntegrationTest {

    @TempDir
    Path tempDir;

    private Database database;
    private Connection conn;
    private JobRepository jobRepo;
    private ScheduleRepository scheduleRepo;
    private ExecutionRepository execRepo;
    private JobAggregateProvider provider;
    private Path configPath;

    @BeforeEach
    void setUp() throws Exception {
        database = new Database();
        conn = database.getConnection();

        jobRepo = new JobRepository(conn);
        scheduleRepo = new ScheduleRepository(conn);
        execRepo = new ExecutionRepository(conn);
        provider = new JobAggregateProvider(jobRepo, scheduleRepo, execRepo);
        configPath = tempDir.resolve("jobs.yaml");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (database != null) {
            database.close();
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

        JobConfigLoader loader = new JobConfigLoader(configPath, provider);
        loader.loadAndSync();

        List<JobData> jobs = provider.getAllJobs();
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

        JobConfigLoader loader = new JobConfigLoader(configPath, provider);
        loader.loadAndSync();

        JobData job = provider.getJob("exec-test").orElseThrow();
        JobExecutor executor = new JobExecutor(execRepo);
        executor.runJob(job);

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

        JobConfigLoader loader = new JobConfigLoader(configPath, provider);
        loader.loadAndSync();

        List<JobData> jobs = provider.getAllJobs();
        assertEquals(2, jobs.size());
    }

    @Test
    void shouldUpdateExistingJobOnConfigChange() throws Exception {
        JobConfigLoader loader = new JobConfigLoader(configPath, provider);

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
        loader.loadAndSync();

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
        loader.loadAndSync();

        List<JobData> jobs = provider.getAllJobs();
        assertEquals(1, jobs.size());
        assertEquals("Updated Name", jobs.get(0).meta().name());
    }
}