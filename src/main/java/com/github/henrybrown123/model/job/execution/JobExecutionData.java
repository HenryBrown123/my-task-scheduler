package com.github.henrybrown123.model.job.execution;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.henrybrown123.model.job.Status;

import java.time.LocalDateTime;

public record JobExecutionData(
        Status status,
        LocalDateTime lastExecution,
        LocalDateTime endDate,
        String lastRunStatus,
        LocalDateTime overdue
) {
}
