package com.github.henrybrown123.configuration;

import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.model.job.command.JobCommandData;
import com.github.henrybrown123.model.job.JobMeta;
import com.github.henrybrown123.model.job.schedule.IJobScheduleData;


/**
 * ConfigFile record representing data in the config file... follows the same
 * structure to allow Jackson to map it. Currently, model objects contain @json_property
 * annotations to allow Jackson to map to the right field in the config file where names differ.
 * <p>
 * If this becomes too hard to manage, then the same structure could be duplicated here with a
 * proper mapping interface instead of leaking Jackson config into the model layer.
 *
 * @param meta
 * @param command
 * @param schedule
 */
public record JobConfig(
        JobMeta meta,
        JobCommandData command,
        IJobScheduleData schedule
) {
    public static JobConfig fromJobData(JobData jobData){
        return new JobConfig(jobData.meta(), jobData.command(), jobData.schedule());
    }

    public JobData toJobData(){
        return new JobData(meta, command, schedule, null);
    }
}
