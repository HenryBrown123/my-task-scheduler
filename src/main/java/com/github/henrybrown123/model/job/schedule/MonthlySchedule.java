package com.github.henrybrown123.model.job.schedule;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public record MonthlySchedule(
        Integer dayOfMonth,
        LocalTime time,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate
) implements IJobScheduleData {


    @Override
    @JsonProperty("type")  // Include in JSON output
    public String type() {
        return "monthly";
    }



    @Override
    public LocalDateTime getNextExecutionDate(LocalDateTime lastExecution) {
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime thisMonthDue =  now.withDayOfMonth(dayOfMonth).with(time);
        if (now.isBefore(thisMonthDue)){
            return thisMonthDue;
        }

        return now.plusMonths(1).withDayOfMonth(dayOfMonth).with(time);

    }
}
