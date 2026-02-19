package com.github.henrybrown123.repository;

import com.github.henrybrown123.configuration.JobConfig;
import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.model.job.Interpreter;
import com.github.henrybrown123.model.job.JobMeta;
import com.github.henrybrown123.model.job.command.JobCommandData;
import com.github.henrybrown123.model.job.execution.ExecutionType;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Aggregates job data across repositories and maps between
 * persistence records and domain objects.
 *
 * <p>Repositories handle raw data access (SQL + records).
 * This class handles domain logic: mapping, enrichment, sync.
 */
public class JobAggregate {
    private final JobRepository jobRepo;
    private final ScheduleRepository scheduleRepo;
    private final ExecutionRepository execRepo;

    public JobAggregate(JobRepository jobRepo, ScheduleRepository scheduleRepo, ExecutionRepository execRepo) {
        this.jobRepo = jobRepo;
        this.scheduleRepo = scheduleRepo;
        this.execRepo = execRepo;
    }

    /**
     * Syncs the database to match the incoming config.
     * Deactivates jobs no longer in config, upserts the rest.
     *
     * @param incoming the complete list of job configs
     */
    public void sync(List<JobConfig> incoming) {
        Set<String> incomingIds = incoming.stream()
                .map(config -> config.meta().id())
                .collect(Collectors.toSet());

        jobRepo.findAllIds().stream()
                .filter(id -> !incomingIds.contains(id))
                .forEach(jobRepo::deactivate);

        incoming.forEach(this::save);
    }

    /**
     * Saves a single job config — persists both the job record
     * and its schedule details as a unit.
     *
     * @param config the job config to save
     */
    public void save(JobConfig config) {
        jobRepo.save(toRecord(config));
        scheduleRepo.save(config.meta().id(), config.schedule());
    }

    public Optional<JobData> get(String jobId) {
        return jobRepo.findById(jobId)
                .map(this::toJobData);
    }

    public List<JobData> getAll() {
        return jobRepo.findAll().stream()
                .map(this::toJobData)
                .toList();
    }

    public List<JobData> getActive() {
        return jobRepo.findByStatus("active").stream()
                .map(this::toJobData)
                .toList();
    }

    /**
     * Maps a persistence record to a domain object, enriching
     * with schedule details and last execution data.
     */
    private JobData toJobData(JobRepository.JobRecord record) {
        JobMeta meta = new JobMeta(
                record.id(),
                record.name(),
                record.description(),
                record.priority(),
                record.tags()
        );

        JobCommandData command = new JobCommandData(
                null,
                ExecutionType.valueOf(record.commandType().toUpperCase()),
                record.command(),
                Interpreter.valueOf(record.interpreter().toUpperCase()),
                List.of()
        );

        var schedule = scheduleRepo.findByJobId(
                record.id(),
                record.scheduleType(),
                record.startDate(),
                record.endDate()
        ).orElse(null);

        var execution = execRepo.getLastExecution(record.id())
                .orElse(null);

        return new JobData(meta, command, schedule, execution);
    }

    /**
     * Maps a job config to a persistence record for the jobs table.
     */
    private JobRepository.JobRecord toRecord(JobConfig config) {
        return new JobRepository.JobRecord(
                config.meta().id(),
                config.meta().name(),
                config.meta().description(),
                config.meta().priority(),
                config.meta().tags(),
                config.schedule().type(),
                config.command().type().name(),
                config.command().command(),
                config.command().interpreter().name(),
                "active",
                config.schedule().startDate(),
                config.schedule().endDate()
        );
    }
}