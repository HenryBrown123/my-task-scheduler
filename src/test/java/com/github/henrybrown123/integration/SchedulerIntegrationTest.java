package com.github.henrybrown123.integration;

import com.github.henrybrown123.configuration.AppConfig;
import com.github.henrybrown123.configuration.JobConfigLoader;
import com.github.henrybrown123.database.Database;
import com.github.henrybrown123.execution.JobExecutor;
import com.github.henrybrown123.repository.sql.CredentialDao;
import com.github.henrybrown123.repository.sql.ExecutionDao;
import com.github.henrybrown123.repository.JobDataRepository;
import com.github.henrybrown123.repository.sql.JobDao;
import com.github.henrybrown123.repository.sql.ScheduleDao;
import com.github.henrybrown123.scheduling.JobScheduler;
import com.github.henrybrown123.scheduling.SchedulerService;
import com.github.henrybrown123.security.CredentialService;
import com.github.henrybrown123.security.VaultLifecycle;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for reading job configuration yaml and executing jobs on the scheduler thread.
 * In memory sqlite database used.
 */
class SchedulerIntegrationTest {

    @TempDir
    Path tempDir;

    private Database database;
    private ExecutionDao execRepo;
    private JobDataRepository jobDataRepo;
    private JobScheduler scheduler;
    private CredentialDao credentialDao;

    @BeforeEach
    void setUp() throws Exception {
        database = new Database();
        Connection conn = database.getConnection();

        var jobRepo = new JobDao(conn);
        var scheduleRepo = new ScheduleDao(conn);
        execRepo = new ExecutionDao(conn);
        credentialDao = new CredentialDao(conn);
        jobDataRepo = new JobDataRepository(jobRepo, scheduleRepo, execRepo,credentialDao);
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

        var executor = new JobExecutor(execRepo, null);
        var loader = new JobConfigLoader(configPath, jobDataRepo);
        return new SchedulerService(scheduler, jobDataRepo, executor, loader);
    }

    private SchedulerService createServiceWithCredentials(String yaml, CredentialService credService) throws Exception {
        Path configPath = Files.createTempFile(tempDir, "jobs-", ".yaml");
        Files.writeString(configPath, yaml);

        var executor = new JobExecutor(execRepo, credService);
        var loader = new JobConfigLoader(configPath, jobDataRepo);
        return new SchedulerService(scheduler, jobDataRepo, executor, loader);
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
        } catch (Exception e) {
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

        var jobs = jobDataRepo.getAll();
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

        tickAndWait(service);

        var firstExec = execRepo.getLastExecution("once-job");
        assertTrue(firstExec.isPresent());
        long firstExecId = firstExec.get().executionId();

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
    void shouldPipeCredentialsWhenJobExecutesOnTick() throws Exception {
        var fakeVault = new FakeVaultLifecycle();
        fakeVault.store("smtp", Map.of(
                "host", "smtp.gmail.com",
                "port", "587",
                "username", "test@gmail.com",
                "password", "secret"
        ));
        var credService = new CredentialService(fakeVault);

        var service = createServiceWithCredentials("""
            jobs:
              - meta:
                  id: creds-tick-job
                  name: "Creds Tick Test"
                  description: "Job with credentials on scheduler"
                  priority: medium
                  tags: []
                schedule:
                  type: simple
                  interval: 5m
                command:
                  type: cmd
                  command: "cat"
                  interpreter: bash
                  credentials:
                    - name: smtp
                      type: SMTP
            """, credService);

        tickAndWait(service);

        var lastExec = execRepo.getLastExecution("creds-tick-job");
        assertTrue(lastExec.isPresent(), "Job should have executed");
        assertEquals("complete", lastExec.get().lastRunStatus());

        Path logsDir = Path.of(AppConfig.scheduling().logsDir());
        Path stdout = Files.list(logsDir)
                .filter(p -> p.getFileName().toString().contains("creds-tick-job"))
                .filter(p -> p.getFileName().toString().contains("stdout"))
                .max(java.util.Comparator.comparingLong(p -> p.toFile().lastModified()))
                .orElse(null);

        assertNotNull(stdout, "stdout log file should exist");
        String output = Files.readString(stdout);
        assertTrue(output.contains("\"credentials\""), "Should contain credentials wrapper");
        assertTrue(output.contains("smtp.gmail.com"), "Should contain host");
        assertTrue(output.contains("test@gmail.com"), "Should contain username");
        assertTrue(output.contains("secret"), "Should contain password");
    }

    @Test
    void shouldSkipJobOnTickWhenCredentialsMissing() throws Exception {
        var fakeVault = new FakeVaultLifecycle();
        var credService = new CredentialService(fakeVault);

        var service = createServiceWithCredentials("""
            jobs:
              - meta:
                  id: missing-creds-job
                  name: "Missing Creds Test"
                  description: "Job with missing credentials"
                  priority: medium
                  tags: []
                schedule:
                  type: simple
                  interval: 5m
                command:
                  type: cmd
                  command: "echo should-not-run"
                  interpreter: bash
                  credentials:
                    - name: smtp
                      type: SMTP
            """, credService);

        tickAndWait(service);

        var jobs = jobDataRepo.getAll();
        assertEquals(1, jobs.size());

        var lastExec = execRepo.getLastExecution("missing-creds-job");
        assertTrue(lastExec.isEmpty(), "Job should not have executed — credentials missing");
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

    /**
     * In-memory fake Vault for integration tests that need credential
     * piping without a running Vault server.
     */
    static class FakeVaultLifecycle extends VaultLifecycle {
        private final Map<String, Map<String, String>> secrets = new HashMap<>();

        FakeVaultLifecycle() {
            super();
        }

        void store(String name, Map<String, String> fields) {
            secrets.put(name, new HashMap<>(fields));
        }

        @Override
        public void ensureReady() {}

        @Override
        public Optional<Map<String, String>> read(String name) {
            return Optional.ofNullable(secrets.get(name));
        }

        @Override
        public void write(String name, Map<String, Object> fields) {
            Map<String, String> stringFields = new HashMap<>();
            fields.forEach((k, v) -> stringFields.put(k, v.toString()));
            secrets.put(name, stringFields);
        }

        @Override
        public void delete(String name) {
            secrets.remove(name);
        }

        @Override
        public boolean isHealthy() {
            return true;
        }
    }
}