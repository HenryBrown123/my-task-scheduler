package com.github.henrybrown123.model;

import com.github.henrybrown123.model.job.command.JobCommandData;
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

    /**
     * Record method using schedule and execution record methods and fields.
     * @return next execution date and time for job
     */
    public LocalDateTime getNextExecutionTime() {
        LocalDateTime lastExecution = null;
        if (execution != null){
            lastExecution = execution.lastExecution();
        }

        return schedule.getNextExecutionDate(lastExecution);
    }

    public boolean isDue() {
        return getNextExecutionTime().isBefore(LocalDateTime.now());
    }
}
