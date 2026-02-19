package com.github.henrybrown123.model.job.execution;

import com.github.henrybrown123.model.job.Status;

import java.time.LocalDateTime;

public record JobExecutionData(
        long executionId,
        Status status,
        LocalDateTime lastExecution,
        LocalDateTime endDate,
        String lastRunStatus,
        String stdoutFile,
        String stderrFile,
        LocalDateTime overdue
) {}
