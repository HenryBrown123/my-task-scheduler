package com.github.henrybrown123.shared.model.job.schedule;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.LocalDateTime;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SimpleSchedule.class, name = "simple"),
        @JsonSubTypes.Type(value = CronSchedule.class, name = "cron"),
        @JsonSubTypes.Type(value = WeeklySchedule.class, name = "weekly"),
        @JsonSubTypes.Type(value = MonthlySchedule.class, name = "monthly"),
        @JsonSubTypes.Type(value = YearlySchedule.class, name = "yearly")
})
public sealed interface IJobScheduleData permits
        SimpleSchedule,
        CronSchedule,
        WeeklySchedule,
        MonthlySchedule,
        YearlySchedule {

    boolean isDue(LocalDateTime lastExecution);

    default boolean isWithinDateRange(LocalDateTime now, LocalDateTime start, LocalDateTime end) {
        if (start != null && now.isBefore(start)) {
            return true;
        }
        return end != null && now.isAfter(end);
    }

    default boolean hasRunInPeriod(LocalDateTime lastExecution, LocalDateTime periodStart) {
        if (lastExecution == null) {
            return false;
        }
        return lastExecution.isBefore(periodStart);
    }
}
