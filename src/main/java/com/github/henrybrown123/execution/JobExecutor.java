package com.github.henrybrown123.execution;

import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.repository.ExecutionRepository;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class JobExecutor {
    private static final long DEFAULT_TIMEOUT_SECONDS = 300;
    private final ExecutionRepository executionRepository;

    public JobExecutor(ExecutionRepository executionRepository) {
        this.executionRepository = executionRepository;
    }

    public record RuntimeContext(
            long executionId,
            String stdoutFile,
            String stderrFile
    ) {}

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
        RuntimeContext ctx = startExecution(job);

        try {
            Process process = startProcess(job, ctx);
            return waitAndHandleResult(process, ctx);
        } catch (IOException e) {
            completeExecution(ctx, "failed", -1);
            return new JobRunResult.Failure("IO Error: " + e.getMessage(), ctx.executionId());
        } catch (InterruptedException e) {
            completeExecution(ctx, "cancelled", -1);
            Thread.currentThread().interrupt();
            return new JobRunResult.Failure("Interrupted: " + e.getMessage(), ctx.executionId());
        }
    }

    private RuntimeContext startExecution(JobData job) {
        var repoCtx = executionRepository.startExecution(job.meta().id(), "job-executor");
        return new RuntimeContext(
                repoCtx.executionId(),
                repoCtx.stdoutFile(),
                repoCtx.stderrFile()
        );
    }

    private Process startProcess(JobData job, RuntimeContext ctx) throws IOException {
        String[] command = CommandBuilder.buildCommand(
                job.command().interpreter(),
                job.command().command(),
                job.command().type()
        );

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectOutput(new File(ctx.stdoutFile()));
        pb.redirectError(new File(ctx.stderrFile()));

        return pb.start();
    }

    private JobRunResult waitAndHandleResult(Process process, RuntimeContext ctx) throws InterruptedException {
        boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

        if (!finished) {
            process.destroyForcibly();
            process.waitFor();
            completeExecution(ctx, "timeout", -1);
            return new JobRunResult.Timeout(ctx.executionId());
        }

        int exitCode = process.exitValue();
        String status = exitCode == 0 ? "complete" : "failed";
        completeExecution(ctx, status, exitCode);

        return exitCode == 0
                ? new JobRunResult.Success(ctx.executionId())
                : new JobRunResult.Failure("Exit code " + exitCode, ctx.executionId());
    }

    private void completeExecution(RuntimeContext ctx, String status, int exitCode) {
        executionRepository.completeExecution(ctx.executionId(), status, exitCode);
    }
}