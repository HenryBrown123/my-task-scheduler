package com.github.henrybrown123.model;

import com.github.henrybrown123.model.job.JobCommandData;
import com.github.henrybrown123.model.job.execution.JobExecutionData;
import com.github.henrybrown123.model.job.JobMeta;
import com.github.henrybrown123.model.job.schedule.IJobScheduleData;

import java.time.LocalDateTime;

public record JobData(
        JobMeta meta,
        JobCommandData command,
        IJobScheduleData schedule,
        JobExecutionData execution
) {


    public LocalDateTime getNextExecutionTime() {
        return schedule.getNextExecutionDate(execution.lastExecution());
    }

    public boolean isDue() {
        return getNextExecutionTime().isBefore(LocalDateTime.now());
    }
}
