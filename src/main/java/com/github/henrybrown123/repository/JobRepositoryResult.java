package com.github.henrybrown123.repository;
import com.github.henrybrown123.model.JobData;
import java.util.List;

public sealed interface JobRepositoryResult {
    // Query results
    record Found(JobData job) implements JobRepositoryResult {}
    record FoundMany(List<JobData> jobs) implements JobRepositoryResult {}
    record NotFound(String jobId) implements JobRepositoryResult {}

    // Mutation results
    record Saved(String jobId) implements JobRepositoryResult {}
    record Deleted(String jobId) implements JobRepositoryResult {}

    // Error result
    record Failed(String operation, String reason, Throwable cause) implements JobRepositoryResult {}
}