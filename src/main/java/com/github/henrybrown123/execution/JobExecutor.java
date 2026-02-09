package com.github.henrybrown123.execution;

import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.repository.ExecutionRepository;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

public class JobExecutor {
    private static final long DEFAULT_TIMEOUT_SECONDS = 300;
    private final ExecutionRepository executionRepository;

    public JobExecutor(ExecutionRepository executionRepository) {
        this.executionRepository = executionRepository;
    }

    private sealed interface JobRunResult {
        record Success(long executionId) implements JobRunResult {}
        record Failure(String message, long executionId) implements JobRunResult {}
        record Timeout(long executionId) implements JobRunResult {}
    }

    public void runJob(JobData job) {
        System.out.println("[" + job.meta().name() + "] Running");
        JobRunResult result = executeJob(job);

        switch (result) {
            case JobRunResult.Success(long execId) ->
                    System.out.println("[" + job.meta().name() + "] Success (execution: " + execId + ")");
            case JobRunResult.Timeout(long execId) ->
                    System.err.println("[" + job.meta().name() + "] Timeout (execution: " + execId + ")");
            case JobRunResult.Failure(String message, long execId) ->
                    System.err.println("[" + job.meta().name() + "] Failed: " + message + " (execution: " + execId + ")");
        }
    }

    private JobRunResult executeJob(JobData job) {
        ExecutionRepository.ExecutionContext ctx = null;

        try {
            ctx = executionRepository.startExecution(job.meta().id(), "job-executor");

            String[] command = CommandBuilder.buildCommand(
                    job.command().interpreter(),
                    job.command().command(),
                    job.command().type()
            );

            ProcessBuilder pb = new ProcessBuilder(command);

            File stdOut = new File(ctx.stdoutFile());
            File stdErr = new File(ctx.stderrFile());

            pb.redirectOutput(stdOut);
            pb.redirectError(stdErr);

            System.out.println("[DEBUG] Starting process...");
            Process process = pb.start();

            System.out.println("[DEBUG] Waiting for process...");
            boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            System.out.println("[DEBUG] Process finished: " + finished);

            if (!finished) {
                process.destroyForcibly();
                process.waitFor();
                executionRepository.completeExecution(ctx.executionId(), "timeout", -1);
                return new JobRunResult.Timeout(ctx.executionId());
            }

            int exitCode = process.exitValue();
            System.out.println("[DEBUG] Exit code: " + exitCode);

            // Check file immediately after process exits
            File f = new File(ctx.stdoutFile());
            System.out.println("[DEBUG] File exists: " + f.exists());
            System.out.println("[DEBUG] File size: " + f.length());

            String status = exitCode == 0 ? "complete" : "failed";


            executionRepository.completeExecution(ctx.executionId(), status, exitCode);

            return exitCode == 0
                    ? new JobRunResult.Success(ctx.executionId())
                    : new JobRunResult.Failure("Exit code " + exitCode, ctx.executionId());

        } catch (IOException e) {
            if (ctx != null) {
                try {
                    executionRepository.completeExecution(ctx.executionId(), "failed", -1);
                } catch (SQLException ignored) {}
            }
            return new JobRunResult.Failure("IO Error: " + e.getMessage(),
                    ctx != null ? ctx.executionId() : -1);

        } catch (InterruptedException e) {
            if (ctx != null) {
                try {
                    executionRepository.completeExecution(ctx.executionId(), "cancelled", -1);
                } catch (SQLException ignored) {}
            }
            Thread.currentThread().interrupt();
            return new JobRunResult.Failure("Interrupted: " + e.getMessage(),
                    ctx != null ? ctx.executionId() : -1);

        } catch (SQLException e) {
            throw new RuntimeException("Database error during job execution", e);
        }
    }
}