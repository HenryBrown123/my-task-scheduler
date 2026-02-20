package com.github.henrybrown123.integration;

import com.github.henrybrown123.configuration.AppConfig;
import com.github.henrybrown123.database.Database;
import com.github.henrybrown123.scheduling.execution.JobExecutor;
import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.model.job.Interpreter;
import com.github.henrybrown123.model.job.JobMeta;
import com.github.henrybrown123.model.job.command.JobCommandData;
import com.github.henrybrown123.model.job.command.JobCredential;
import com.github.henrybrown123.model.job.execution.ExecutionType;
import com.github.henrybrown123.model.job.schedule.SimpleSchedule;
import com.github.henrybrown123.repository.sql.ExecutionDao;
import com.github.henrybrown123.security.CredentialService;
import com.github.henrybrown123.security.ESecretType;
import com.github.henrybrown123.security.VaultLifecycle;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test for the full credential pipeline:
 * Vault → CredentialService → JobExecutor → stdin → script
 *
 * <p>Connects to the dev Vault instance, isolated by secret path
 * ({@code secret/my-scheduler/test}). Tests are skipped if Vault
 * is not running. Credentials stored during tests are cleaned up
 * between runs.
 */
class SecurityIntegrationTest {

    private static VaultLifecycle vault;
    private static CredentialService credentialService;
    private static Database database;
    private static ExecutionDao executionDao;
    private static JobExecutor executor;

    @BeforeAll
    static void setUp() throws Exception {
        System.out.println("Vault available: " + vaultReachable());
        System.out.println("Vault address: " + AppConfig.vault().address());
        assumeTrue(vaultReachable(), "Vault not running — skipping security integration tests");

        vault = new VaultLifecycle();
        vault.ensureReady();

        credentialService = new CredentialService(vault);

        database = new Database();
        Connection conn = database.getConnection();
        executionDao = new ExecutionDao(conn);
        executor = new JobExecutor(executionDao, credentialService);
    }

    @AfterAll
    static void tearDown() throws Exception {
        if (database != null) database.close();
    }

    @BeforeEach
    void cleanTestCredentials() {
        try { vault.delete("test-smtp"); } catch (Exception ignored) {}
    }

    @Test
    void shouldStoreAndRetrieveCredential() {
        credentialService.store("test-smtp", ESecretType.SMTP, smtpFields());

        var result = credentialService.get("test-smtp");

        assertTrue(result.isPresent());
        assertEquals("smtp.gmail.com", result.get().get("host"));
        assertEquals("587", result.get().get("port"));
        assertEquals("test@gmail.com", result.get().get("username"));
        assertEquals("test-secret", result.get().get("password"));
    }

    @Test
    void shouldDeleteCredential() {
        credentialService.store("test-smtp", ESecretType.SMTP, smtpFields());
        credentialService.delete("test-smtp");

        assertTrue(credentialService.get("test-smtp").isEmpty());
    }

    @Test
    void shouldReportJobReadyWhenCredentialPresent() {
        credentialService.store("test-smtp", ESecretType.SMTP, smtpFields());

        assertTrue(credentialService.jobIsReady(
                jobWith("ready-job", "echo ready", cred("test-smtp", ESecretType.SMTP))));
    }

    @Test
    void shouldReportJobNotReadyWhenCredentialMissing() {
        assertFalse(credentialService.jobIsReady(
                jobWith("missing-job", "echo missing", cred("test-smtp", ESecretType.SMTP))));
    }

    @Test
    void shouldScanAndReportMissingCredentials() {
        var jobs = List.of(
                jobWith("job-1", "echo test", cred("test-smtp", ESecretType.SMTP))
        );

        var missing = credentialService.scanMissing(jobs);

        assertEquals(1, missing.size());
        assertEquals("test-smtp", missing.get(0).name());
    }

    @Test
    void shouldSkipJobWhenCredentialsMissing() {
        var job = jobWith("skip-job", "echo should-not-run",
                cred("test-smtp", ESecretType.SMTP));

        executor.runJob(job);

        assertTrue(executionDao.getLastExecution("skip-job").isEmpty(),
                "Job should not have executed — credentials missing");
    }

    @Test
    void shouldPipeCredentialsToStdin() throws Exception {
        credentialService.store("test-smtp", ESecretType.SMTP, smtpFields());

        var job = jobWith("pipe-job", "cat",
                cred("test-smtp", ESecretType.SMTP));

        executor.runJob(job);

        Path stdout = findLogFile("pipe-job", "stdout");
        assertNotNull(stdout, "stdout log file should exist");

        String output = Files.readString(stdout);
        assertTrue(output.contains("\"credentials\""), "Should contain credentials wrapper");
        assertTrue(output.contains("smtp.gmail.com"), "Should contain host");
        assertTrue(output.contains("587"), "Should contain port");
        assertTrue(output.contains("test@gmail.com"), "Should contain username");
        assertTrue(output.contains("test-secret"), "Should contain password");
    }

    @Test
    void shouldCloseStdinWhenNoCredentials() throws Exception {
        var job = jobWith("no-creds-job", "cat");

        executor.runJob(job);

        var lastExec = executionDao.getLastExecution("no-creds-job");
        assertTrue(lastExec.isPresent(), "cat with no stdin should still complete");
        assertEquals("complete", lastExec.get().lastRunStatus());

        Path stdout = findLogFile("no-creds-job", "stdout");
        assertNotNull(stdout);
        assertEquals("", Files.readString(stdout).trim(), "Nothing piped — stdout should be empty");
    }

    @Test
    void shouldExecuteWithCredentialsAndCompleteSuccessfully() {
        credentialService.store("test-smtp", ESecretType.SMTP, smtpFields());

        var job = jobWith("success-job", "echo done",
                cred("test-smtp", ESecretType.SMTP));

        executor.runJob(job);

        var lastExec = executionDao.getLastExecution("success-job");
        assertTrue(lastExec.isPresent());
        assertEquals("complete", lastExec.get().lastRunStatus());
    }

    private static boolean vaultReachable() {
        try {
            Process p = new ProcessBuilder("vault", "status",
                    "-address=" + AppConfig.vault().address(),
                    "-format=json")
                    .redirectErrorStream(true)
                    .start();
            p.waitFor();
            return p.exitValue() == 0 || p.exitValue() == 2;
        } catch (Exception e) {
            return false;
        }
    }

    private Path findLogFile(String jobId, String stream) throws Exception {
        Path logsDir = Path.of(AppConfig.scheduling().logsDir());
        return Files.list(logsDir)
                .filter(p -> p.getFileName().toString().contains(jobId))
                .filter(p -> p.getFileName().toString().contains(stream))
                .findFirst()
                .orElse(null);
    }

    private Map<String, String> smtpFields() {
        return Map.of(
                "host", "smtp.gmail.com",
                "port", "587",
                "username", "test@gmail.com",
                "password", "test-secret"
        );
    }

    private JobCredential cred(String name, ESecretType type) {
        return new JobCredential(name, type);
    }

    private JobData jobWith(String id, String command, JobCredential... creds) {
        JobMeta meta = new JobMeta(id, "Test Job", "description", "medium", List.of());
        JobCommandData cmd = new JobCommandData(
                ExecutionType.CMD, command, Interpreter.BASH, List.of(creds));
        SimpleSchedule schedule = new SimpleSchedule("1h", null, null);
        return new JobData(meta, cmd, schedule, null);
    }
}