package com.github.henrybrown123.repository;

import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.model.job.*;
import com.github.henrybrown123.model.job.execution.ExecutionType;
import com.github.henrybrown123.model.job.execution.JobExecutionData;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JobAggregateProvider {
    private final JobRepository jobRepo;
    private final ScheduleRepository scheduleRepo;
    private final ExecutionRepository execRepo;

    public JobAggregateProvider(JobRepository jobRepo, ScheduleRepository scheduleRepo, ExecutionRepository execRepo) {
        this.jobRepo = jobRepo;
        this.scheduleRepo = scheduleRepo;
        this.execRepo = execRepo;
    }

    public Optional<JobData> getJob(String jobId) {
        return jobRepo.findById(jobId).map(this::buildJobData);
    }

    public List<JobData> getAllJobs() {
        return jobRepo.findAll().stream()
                .map(this::buildJobData)
                .toList();
    }

    public List<JobData> getActiveJobs() {
        return jobRepo.findByStatus("ACTIVE").stream()
                .map(this::buildJobData)
                .toList();
    }

    public void saveJob(JobData job) {
        var jobRecord = new JobRepository.JobRecord(
                job.meta().id(),
                job.meta().name(),
                job.meta().description(),
                job.meta().priority(),
                job.meta().tags(),
                job.schedule().type(),
                job.command().type().name(),
                job.command().command(),
                job.command().interpreter().name(),
                job.execution() != null ? job.execution().status().name() : "ACTIVE",
                job.schedule().startDate() != null ? job.schedule().startDate().toString() : null,
                job.schedule().endDate() != null ? job.schedule().endDate().toString() : null
        );
        jobRepo.save(jobRecord);
        scheduleRepo.save(job.meta().id(), job.schedule());
    }

    /**
     * Returns JobData from an input job record, looking up additional supplementary information
     * such as schedule details and execution history.
     * @param record
     * @return
     */
    private JobData buildJobData(JobRepository.JobRecord record) {
        JobMeta meta = new JobMeta(
                record.id(),
                record.name(),
                record.description(),
                record.priority(),
                record.tags()
        );

        JobCommandData command = new JobCommandData(
                record.command(),
                ExecutionType.valueOf(record.commandType()),
                record.command(),
                Interpreter.valueOf(record.interpreter())
        );

        var schedule = scheduleRepo.findByJobId(
                record.id(),
                record.scheduleType(),
                record.startDate(),
                record.endDate()
        ).orElse(null);

        var execution = execRepo.getLastExecution(record.id())
                .map(summary -> new JobExecutionData(
                        Status.valueOf(record.status()),
                        summary.lastExecution(),
                        summary.endTime(),
                        summary.status(),
                        null
                )).orElse(null);

        return new JobData(meta, command, schedule, execution);
    }
}