package com.github.henrybrown123.shared.model.job.schedule;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record WeeklySchedule(
        List<DayOfWeek> days,
        LocalTime time,
        @JsonProperty("start_date") LocalDateTime startDate,
        @JsonProperty("end_date") LocalDateTime endDate
) implements IJobScheduleData {

    @Override
    public boolean isDue(LocalDateTime lastExecution) {
        LocalDateTime now = LocalDateTime.now();

        if (isWithinDateRange(now, startDate, endDate)) {
            return false;
        }

        if (!days.contains(now.getDayOfWeek())) {
            return false;
        }

        if (now.toLocalTime().isBefore(time)) {
            return false;
        }

        LocalDateTime todayAtScheduledTime = now.toLocalDate().atTime(time);
        return hasRunInPeriod(lastExecution, todayAtScheduledTime);
    }
}
