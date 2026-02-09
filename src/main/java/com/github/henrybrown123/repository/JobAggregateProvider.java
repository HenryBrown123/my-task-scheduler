package com.github.henrybrown123.repository;

import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.model.job.*;
import com.github.henrybrown123.model.job.execution.ExecutionType;
import com.github.henrybrown123.model.job.execution.JobExecutionData;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class JobAggregateProvider {
    private final JobRepository jobRepo;
    private final ScheduleRepository scheduleRepo;
    private final ExecutionRepository execRepo;

    public JobAggregateProvider(JobRepository jobRepo, ScheduleRepository scheduleRepo, ExecutionRepository execRepo) {
        this.jobRepo = jobRepo;
        this.scheduleRepo = scheduleRepo;
        this.execRepo = execRepo;
    }

    public JobData getJob(String jobId) throws SQLException {
        var jobRecord = jobRepo.findById(jobId);
        if (jobRecord == null) {
            return null;
        }

        return buildJobData(jobRecord);
    }

    public List<JobData> getAllJobs() throws SQLException {
        List<JobRepository.JobRecord> jobRecords = jobRepo.findAll();
        List<JobData> jobs = new ArrayList<>();

        for (var record : jobRecords) {
            jobs.add(buildJobData(record));
        }

        return jobs;
    }

    public List<JobData> getActiveJobs() throws SQLException {
        List<JobRepository.JobRecord> jobRecords = jobRepo.findByStatus("ACTIVE");
        List<JobData> jobs = new ArrayList<>();

        for (var record : jobRecords) {
            jobs.add(buildJobData(record));
        }

        return jobs;
    }

    public void saveJob(JobData job) throws SQLException {
        // Save to jobs table
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

        // Save schedule
        scheduleRepo.save(job.meta().id(), job.schedule());
    }

    private JobData buildJobData(JobRepository.JobRecord record) throws SQLException {
        // Build meta
        JobMeta meta = new JobMeta(
                record.id(),
                record.name(),
                record.description(),
                record.priority(),
                record.tags()
        );

        // Build command
        JobCommandData command = new JobCommandData(
                record.command(),
                ExecutionType.valueOf(record.commandType()),
                record.command(),
                Interpreter.valueOf(record.interpreter())
        );

        // Load schedule
        var schedule = scheduleRepo.findByJobId(
                record.id(),
                record.scheduleType(),
                record.startDate(),
                record.endDate()
        );

        // Load execution data
        var execSummary = execRepo.getLastExecution(record.id());
        var execution = new JobExecutionData(
                Status.valueOf(record.status()),
                execSummary != null ? execSummary.lastExecution() : null,
                execSummary != null ? execSummary.endTime() : null,
                execSummary != null ? execSummary.status() : null,
                null
        );

        return new JobData(meta, command, schedule, execution);
    }
}