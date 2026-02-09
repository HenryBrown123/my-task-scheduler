package com.github.henrybrown123.model.job.execution;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.henrybrown123.model.job.Status;

import java.time.LocalDateTime;

public record JobExecutionData(
        Status status,
        @JsonProperty("last_execution") LocalDateTime lastExecution,
        @JsonProperty("end_date") LocalDateTime endDate,
        @JsonProperty("last_run_status") String lastRunStatus,
        LocalDateTime overdue
) {
}
