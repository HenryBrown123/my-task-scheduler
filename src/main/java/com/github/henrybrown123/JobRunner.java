package com.github.henrybrown123;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class JobRunner {
    private final List<JobData> jobsDue;
    private static final long DEFAULT_TIMEOUT_SECONDS = 300; // 5 minutes

    private sealed interface JobRunResult {
        record Success(String output) implements JobRunResult {}
        record Failure(String output) implements JobRunResult {}
    }

    public JobRunner(List<JobData> jobs) {
        this.jobsDue = jobs.stream()
                .filter((job) -> job.schedule().isDue(job.execution().lastExecution()))
                .toList();
    }

    public void runAllJobs() {
        jobsDue.forEach(this::runJob);
    }

    private void runJob(JobData job) {
        System.out.println("[" + job.meta().name() +"] Running");
        System.out.println("===================================");
        JobRunResult result = executeJob(job);
        System.out.println("===================================");
        switch (result) {
            case JobRunResult.Success(String output) -> {
                System.out.println("[" + job.meta().name() + "] Success");
            }
            case JobRunResult.Failure(String message) ->
                    System.err.println("[" + job.meta().name() + "] Failed: " + message);
        }
    }

    private JobRunResult executeJob(JobData job) {
        try {
            String[] command = CommandExecutor.buildCommand(
                    job.command().interpreter(),
                    job.command().command(),
                    job.command().type()
            );

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true); // Merge stderr into stdout (e.g. 2>&1)

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());

            boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new JobRunResult.Failure(
                        "Process timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds\n" + output
                );
            }

            int exitCode = process.exitValue();

            System.out.println(output);
            return exitCode == 0
                    ? new JobRunResult.Success(output)
                    : new JobRunResult.Failure("Failed with exit code " + exitCode);

        } catch (IOException e) {
            return new JobRunResult.Failure("IO Error: " + e.getMessage());
        } catch (InterruptedException e) {
            return new JobRunResult.Failure("Interrupted: " + e.getMessage());
        }
    }
}