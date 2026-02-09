package com.github.henrybrown123.model.job.schedule;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.LocalDate;
import java.time.LocalDateTime;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SimpleSchedule.class, name = "simple"),
        @JsonSubTypes.Type(value = CronSchedule.class, name = "cron"),
        @JsonSubTypes.Type(value = MonthlySchedule.class, name = "monthly"),
})
public sealed interface IJobScheduleData permits
        SimpleSchedule,
        CronSchedule,
        MonthlySchedule {

    String type();
    LocalDate startDate();
    LocalDate endDate();

    /**
     * Returns the time when next due based on the current execution time.
     *
     * @param lastExecution
     * @return
     */
    LocalDateTime getNextExecutionDate(LocalDateTime lastExecution);

    default boolean isScheduleActive(LocalDateTime now, LocalDateTime start, LocalDateTime end) {
        // Not started yet
        if (start != null && now.isBefore(start)) {
            return false;
        }
        // Passed the end date
        if (end != null && now.isAfter(end)) {
            return false;
        }

        return true;
    }
}
