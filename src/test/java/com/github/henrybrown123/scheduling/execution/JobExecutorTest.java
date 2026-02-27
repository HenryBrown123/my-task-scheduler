package com.github.henrybrown123.scheduling.execution;

import com.github.henrybrown123.configuration.AppConfig;
import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.model.job.Interpreter;
import com.github.henrybrown123.model.job.command.JobCommandData;
import com.github.henrybrown123.model.job.JobMeta;
import com.github.henrybrown123.model.job.command.JobCredential;
import com.github.henrybrown123.model.job.execution.ExecutionType;
import com.github.henrybrown123.model.job.schedule.SimpleSchedule;
import com.github.henrybrown123.repository.sql.ExecutionDao;
import com.github.henrybrown123.security.AppCredential;
import com.github.henrybrown123.security.CredentialService;
import com.github.henrybrown123.security.ESecretType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobExecutorTest {

    private static final long EXEC_ID = 1L;

    @Mock
    private ExecutionDao execRepo;

    @Mock
    private CredentialService credentialService;

    private JobExecutor executor;
    private Path logsDir;

    @BeforeEach
    void setUp() {
        executor = new JobExecutor(execRepo, credentialService);
        logsDir = Path.of(AppConfig.scheduling().logsDir());
    }

    @Test
    void shouldCompleteExecution() throws Exception {
        JobData job = createJob("test-job", "echo hello");

        executor.runJob(job, EXEC_ID).get(5, TimeUnit.SECONDS);

        verify(execRepo).attachLogFiles(eq(EXEC_ID), anyString(), anyString());
        verify(execRepo).setExecutionAsCompleted(EXEC_ID, "complete", 0);
    }

    @Test
    void shouldRecordFailedExecution() throws Exception {
        JobData job = createJob("fail-job", "exit 1");

        executor.runJob(job, EXEC_ID).get(5, TimeUnit.SECONDS);

        verify(execRepo).setExecutionAsCompleted(EXEC_ID, "failed", 1);
    }

    @Test
    void shouldPipeCredentialsToProcess() throws Exception {
        JobMeta meta = new JobMeta("creds-job", "Test Job", "description", "medium", List.of());
        var credReq = new JobCredential("smtp", ESecretType.SMTP);
        JobCommandData cmd = new JobCommandData(ExecutionType.CMD, "cat", Interpreter.BASH, List.of(credReq));
        SimpleSchedule schedule = new SimpleSchedule("1h", null, null);
        JobData job = new JobData(meta, cmd, schedule, null);

        when(credentialService.isVaultAvailable()).thenReturn(true);
        when(credentialService.jobIsReady(any())).thenReturn(true);
        when(credentialService.resolveForJob(any())).thenReturn(Map.of(
                "smtp", AppCredential.fromRawFields("smtp", ESecretType.SMTP, Map.of(
                        "host", "smtp.gmail.com",
                        "port", "587",
                        "username", "test@gmail.com",
                        "password", "secret"
                ))
        ));

        executor.runJob(job, EXEC_ID).get(5, TimeUnit.SECONDS);

        Path stdout = findLogFile("creds-job", "stdout");
        assertNotNull(stdout);
        String output = Files.readString(stdout);
        assertTrue(output.contains("smtp.gmail.com"));
        assertTrue(output.contains("test@gmail.com"));
        assertTrue(output.contains("\"credentials\""));
    }

    @Test
    void shouldCaptureStdout() throws Exception {
        JobData job = createJob("output-job", "echo 'test output'");

        executor.runJob(job, EXEC_ID).get(5, TimeUnit.SECONDS);

        Path stdout = findLogFile("output-job", "stdout");
        assertNotNull(stdout, "stdout log file should exist");
        assertTrue(Files.readString(stdout).contains("test output"));
    }

    @Test
    void shouldCaptureStderr() throws Exception {
        JobData job = createJob("error-job", "echo 'error message' >&2");

        executor.runJob(job, EXEC_ID).get(5, TimeUnit.SECONDS);

        Path stderr = findLogFile("error-job", "stderr");
        assertNotNull(stderr, "stderr log file should exist");
        assertTrue(Files.readString(stderr).contains("error message"));
    }

    @Test
    void shouldCompleteWithFailedOnIOError() throws Exception {
        JobData job = createJob("bad-cmd", "/nonexistent/file");

        executor.runJob(job, EXEC_ID).get(5, TimeUnit.SECONDS);

        verify(execRepo).setExecutionAsCompleted(EXEC_ID, "failed", 127);
    }

    private Path findLogFile(String jobId, String stream) throws Exception {
        return Files.list(logsDir)
                .filter(p -> p.getFileName().toString().contains(jobId))
                .filter(p -> p.getFileName().toString().contains(stream))
                .max(java.util.Comparator.comparingLong(p -> p.toFile().lastModified()))
                .orElse(null);
    }

    private JobData createJob(String id, String command) {
        JobMeta meta = new JobMeta(id, "Test Job", "description", "medium", List.of());
        JobCommandData cmd = new JobCommandData(ExecutionType.CMD, command, Interpreter.BASH, List.of());
        SimpleSchedule schedule = new SimpleSchedule("1h", null, null);
        return new JobData(meta, cmd, schedule, null);
    }
}
