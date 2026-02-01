package com.github.henrybrown123.shared.model;

import com.github.henrybrown123.shared.model.job.JobCommandData;
import com.github.henrybrown123.shared.model.job.JobExecutionData;
import com.github.henrybrown123.shared.model.job.JobMeta;
import com.github.henrybrown123.shared.model.job.schedule.IJobScheduleData;

public record JobData(
        JobMeta meta,
        JobCommandData command,
        IJobScheduleData schedule,
        JobExecutionData execution
) {}
