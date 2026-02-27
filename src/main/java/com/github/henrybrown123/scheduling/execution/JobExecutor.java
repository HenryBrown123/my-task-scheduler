package com.github.henrybrown123.scheduling.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.henrybrown123.configuration.AppConfig;
import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.repository.sql.ExecutionDao;
import com.github.henrybrown123.security.AppCredential;
import com.github.henrybrown123.security.CredentialService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Executes jobs as OS processes. Resolves credentials from Vault
 * and pipes them to the script via stdin as JSON.
 *
 * <p>Process execution is non-blocking — the calling thread is released
 * as soon as the process is started. Completion is handled asynchronously
 * via {@link Process#onExit()}.
 */
public class JobExecutor {
    private static final Logger log = LoggerFactory.getLogger(JobExecutor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ExecutionDao executionDao;
    private final CredentialService credentialService;
    private final String logsDir;

    public JobExecutor(ExecutionDao executionDao, CredentialService credentialService) {
        this.executionDao = executionDao;
        this.credentialService = credentialService;
        this.logsDir = AppConfig.scheduling().logsDir();
    }

    private record LogFiles(Path stdout, Path stderr) {}

    /**
     * Launches a job as an OS process. Returns a future that completes
     * when the process exits. The future completes immediately if the
     * job is skipped (missing credentials, Vault unavailable, etc.).
     *
     * <p>In production, callers can fire-and-forget. In tests, callers
     * can {@code future.get(timeout, unit)} for deterministic completion.
     */
    public CompletableFuture<Void> runJob(JobData job, long execId) {
        if (requiresCredentials(job)) {
            if (!credentialService.isVaultAvailable()) {
                log.warn("Skipped — Vault not available");
                executionDao.setExecutionAsCompleted(execId, "cancelled", -1);
                return CompletableFuture.completedFuture(null);
            }
            if (!credentialService.jobIsReady(job)) {
                log.warn("Skipped — missing credentials");
                executionDao.setExecutionAsCompleted(execId, "cancelled", -1);
                return CompletableFuture.completedFuture(null);
            }
        }

        return launchProcess(job, execId);
    }

    private boolean requiresCredentials(JobData job) {
        return !job.command().credentials().isEmpty();
    }

    private CompletableFuture<Void> launchProcess(JobData job, long execId) {
        LogFiles logFiles;
        try {
            logFiles = createLogFiles(job.meta().id());
        } catch (IOException e) {
            log.error("Failed to create log files: {}", e.getMessage());
            executionDao.setExecutionAsCompleted(execId, "failed", -1);
            return CompletableFuture.completedFuture(null);
        }

        executionDao.attachLogFiles(execId, logFiles.stdout().toString(), logFiles.stderr().toString());

        try {
            Process process = startProcess(job, logFiles);
            pipeCredentials(process, job);

            executionDao.updateExecutionStatus(execId, "running");
            log.info("Running (pid: {})", process.pid());

            return handleProcessAsync(process, execId);
        } catch (IOException e) {
            log.error("Failed to start process: {}", e.getMessage());
            executionDao.setExecutionAsCompleted(execId, "failed", -1);
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Sets up async completion handling. The calling thread returns immediately.
     * MDC context is captured and restored in the callback so log formatting
     * is consistent with the setup logs.
     *
     * @return a future that completes when the process exits
     */
    private CompletableFuture<Void> handleProcessAsync(Process process, long execId) {
        Map<String, String> mdcContext = MDC.getCopyOfContextMap();

        return process.onExit().thenAccept(p -> {
            if (mdcContext != null) MDC.setContextMap(mdcContext);

            try {
                int exitCode = p.exitValue();
                String status = exitCode == 0 ? "complete" : "failed";
                executionDao.setExecutionAsCompleted(execId, status, exitCode);

                if (exitCode == 0) {
                    log.info("Success (execution: {})", execId);
                } else {
                    log.error("Failed: exit code {} (execution: {})", exitCode, execId);
                }
            } finally {
                MDC.clear();
            }
        }).exceptionally(throwable -> {
            if (mdcContext != null) MDC.setContextMap(mdcContext);
            try {
                process.destroyForcibly();
                executionDao.setExecutionAsCompleted(execId, "failed", -1);
                log.error("Process error (execution: {}): {}", execId, throwable.getMessage());
            } finally {
                MDC.clear();
            }
            return null;
        });
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

        Map<String, AppCredential> resolved = credentialService.resolveForJob(
                job.command().credentials());

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
}
