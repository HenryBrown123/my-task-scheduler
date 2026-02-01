package com.github.henrybrown123.shared.model.job.schedule;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.time.LocalDateTime;

public record CronSchedule(
        String expression,
        @JsonProperty("start_date") LocalDateTime startDate,
        @JsonProperty("end_date") LocalDateTime endDate
) implements IJobScheduleData {

    @Override
    public boolean isDue(LocalDateTime lastExecution) {
        LocalDateTime now = LocalDateTime.now();

        if (isWithinDateRange(now, startDate, endDate)) {
            return false;
        }

        // TODO: Use cron-utils library to check if current time matches expression
        if (lastExecution == null) {
            return true;
        }

        return Duration.between(lastExecution, now).toMinutes() >= 1;
    }
}
