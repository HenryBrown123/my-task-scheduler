package com.github.henrybrown123.scheduling.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.henrybrown123.configuration.AppConfig;
import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.repository.sql.ExecutionDao;
import com.github.henrybrown123.security.AppCredential;
import com.github.henrybrown123.security.CredentialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Executes jobs as OS processes. Resolves credentials from Vault
 * and pipes them to the script via stdin as JSON.
 */
public class JobExecutor {
    private static final Logger log = LoggerFactory.getLogger(JobExecutor.class);
    private static final long DEFAULT_TIMEOUT_SECONDS = 300;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExecutionDao executionDao;
    private final CredentialService credentialService;
    private final String logsDir;

    public JobExecutor(ExecutionDao executionDao, CredentialService credentialService) {
        this.executionDao = executionDao;
        this.credentialService = credentialService;
        this.logsDir = AppConfig.scheduling().logsDir();
    }

    private sealed interface JobRunResult {
        record Success(long executionId) implements JobRunResult {}
        record Failure(String message, long executionId) implements JobRunResult {}
        record Timeout(long executionId) implements JobRunResult {}
    }

    private record LogFiles(Path stdout, Path stderr) {}

    public void runJob(JobData job, long execId) {
        if (requiresCredentials(job)) {
            if (!credentialService.isVaultAvailable()) {
                log.warn("Skipped — Vault not available");
                executionDao.setExecutionAsCompleted(execId, "cancelled", -1);
                return;
            }
            if (!credentialService.jobIsReady(job)) {
                log.warn("Skipped — missing credentials");
                executionDao.setExecutionAsCompleted(execId, "cancelled", -1);
                return;
            }
        }

        log.info("Running");
        JobRunResult result = executeJob(job, execId);

        switch (result) {
            case JobRunResult.Success(long id) ->
                    log.info("Success (execution: {})", id);
            case JobRunResult.Timeout(long id) ->
                    log.error("Timeout (execution: {})", id);
            case JobRunResult.Failure(String message, long id) ->
                    log.error("Failed: {} (execution: {})", message, id);
        }
    }

    private boolean requiresCredentials(JobData job) {
        return !job.command().credentials().isEmpty();
    }

    private JobRunResult executeJob(JobData job, long execId) {
        LogFiles logFiles;
        try {
            logFiles = createLogFiles(job.meta().id());
        } catch (IOException e) {
            executionDao.setExecutionAsCompleted(execId, "failed", -1);
            return new JobRunResult.Failure("Failed to create log files: " + e.getMessage(), execId);
        }

        executionDao.attachLogFiles(execId, logFiles.stdout().toString(), logFiles.stderr().toString());

        try {
            Process process = startProcess(job, logFiles);
            pipeCredentials(process, job);
            return waitAndHandleResult(process, execId);
        } catch (IOException e) {
            executionDao.setExecutionAsCompleted(execId, "failed", -1);
            return new JobRunResult.Failure("IO Error: " + e.getMessage(), execId);
        } catch (InterruptedException e) {
            executionDao.setExecutionAsCompleted(execId, "cancelled", -1);
            Thread.currentThread().interrupt();
            return new JobRunResult.Failure("Interrupted: " + e.getMessage(), execId);
        }
    }

    private LogFiles createLogFiles(String jobId) throws IOException {
        var logPrefix = LocalDateTime.now() + "-" + jobId + "_";
        var logsDirectoryPath = Paths.get(logsDir);
        Files.createDirectories(logsDirectoryPath);

        Path stdout = Files.createTempFile(logsDirectoryPath, logPrefix, "_stdout.log");
        Path stderr = Files.createTempFile(logsDirectoryPath, logPrefix, "_stderr.log");
        return new LogFiles(stdout, stderr);
    }

    private Process startProcess(JobData job, LogFiles logFiles) throws IOException {
        String[] command = CommandBuilder.buildCommand(
                job.command().interpreter(),
                job.command().command(),
                job.command().type()
        );

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectOutput(logFiles.stdout().toFile());
        pb.redirectError(logFiles.stderr().toFile());

        return pb.start();
    }

    /**
     * Pipes credentials to the process via stdin as JSON.
     * Matches the stdin contract:
     * <pre>
     * {
     *   "credentials": {
     *     "smtp": { "host": "...", "port": "...", ... }
     *   }
     * }
     * </pre>
     */
    private void pipeCredentials(Process process, JobData job) throws IOException {
        if (!requiresCredentials(job)) {
            process.getOutputStream().close();
            return;
        }

        Map<String, AppCredential> resolved = credentialService.resolveForJob(job.command().credentials());

        Map<String, Object> payload = new LinkedHashMap<>();
        Map<String, Object> credsMap = new LinkedHashMap<>();

        for (var entry : resolved.entrySet()) {
            Map<String, Object> fields = new LinkedHashMap<>(entry.getValue().exposeFields());
            fields.put("type", entry.getValue().type().name().toLowerCase());
            credsMap.put(entry.getKey(), fields);
        }

        payload.put("credentials", credsMap);

        try (OutputStream os = process.getOutputStream()) {
            MAPPER.writeValue(os, payload);
        }
    }

    private JobRunResult waitAndHandleResult(Process process, long execId) throws InterruptedException {
        boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            process.waitFor();
            executionDao.setExecutionAsCompleted(execId, "timeout", -1);
            return new JobRunResult.Timeout(execId);
        }

        int exitCode = process.exitValue();
        String status = exitCode == 0 ? "complete" : "failed";
        executionDao.setExecutionAsCompleted(execId, status, exitCode);

        return exitCode == 0
                ? new JobRunResult.Success(execId)
                : new JobRunResult.Failure("Exit code " + exitCode, execId);
    }
}
