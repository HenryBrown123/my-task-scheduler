package com.github.henrybrown123.model.job.schedule;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record CronSchedule(
        String expression,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate
) implements IJobScheduleData {

    @Override
    @JsonProperty("type")  // Include in JSON output
    public String type() {
        return "cron";
    }

    @Override
    public LocalDateTime getNextExecutionDate(LocalDateTime lastExecution) {
        return LocalDateTime.now();
    };
}
