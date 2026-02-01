package com.github.henrybrown123.shared.model.job.schedule;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Month;

public record YearlySchedule(
        Month month,
        Integer dayOfMonth,
        LocalTime time,
        @JsonProperty("start_date") LocalDateTime startDate,
        @JsonProperty("end_date") LocalDateTime endDate
) implements IJobScheduleData {

    @Override
    public boolean isDue(LocalDateTime lastExecution) {
        LocalDateTime now = LocalDateTime.now();

        if (isWithinDateRange(now, lastExecution, endDate)) {
            return false;
        }

        if (now.getMonth() != month || now.getDayOfMonth() != dayOfMonth) {
            return false;
        }

        if (now.toLocalTime().isBefore(time)) {
            return false;
        }

        LocalDateTime thisYearAtScheduledTime =
                LocalDate.of(now.getYear(), month, dayOfMonth).atTime(time);
        return hasRunInPeriod(lastExecution, thisYearAtScheduledTime);
    }
}
