package com.github.henrybrown123.repository;

import com.github.henrybrown123.configuration.JobConfig;
import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.model.job.Interpreter;
import com.github.henrybrown123.model.job.JobMeta;
import com.github.henrybrown123.model.job.command.JobCommandData;
import com.github.henrybrown123.model.job.command.JobCredential;
import com.github.henrybrown123.model.job.execution.ExecutionType;
import com.github.henrybrown123.repository.sql.CredentialDao;
import com.github.henrybrown123.repository.sql.ExecutionDao;
import com.github.henrybrown123.repository.sql.JobDao;
import com.github.henrybrown123.repository.sql.ScheduleDao;

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
public class JobDataRepository {
    private final JobDao jobRepo;
    private final ScheduleDao scheduleRepo;
    private final ExecutionDao execRepo;
    private final CredentialDao credentialDao;

    public JobDataRepository(JobDao jobRepo, ScheduleDao scheduleRepo,
                             ExecutionDao execRepo, CredentialDao credentialDao) {
        this.jobRepo = jobRepo;
        this.scheduleRepo = scheduleRepo;
        this.execRepo = execRepo;
        this.credentialDao = credentialDao;
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
        jobRepo.save(toJobRecord(config));
        scheduleRepo.save(config.meta().id(), config.schedule());
        config.command().credentials().forEach(cred ->
                credentialDao.save(config.meta().id(), cred));
    }

    /**
     * Retrieves a single job by ID, enriched with schedule and execution data.
     *
     * @param jobId the job identifier
     * @return the enriched job data, or empty if not found
     */
    public Optional<JobData> get(String jobId) {
        return jobRepo.findById(jobId)
                .map(this::toJobData);
    }

    /**
     * Retrieves all jobs, enriched with schedule and execution data.
     *
     * @return all jobs
     */
    public List<JobData> getAll() {
        return jobRepo.findAllActiveJobs().stream()
                .map(this::toJobData)
                .toList();
    }

    /**
     * Maps a persistence record to a domain object, enriching
     * with schedule details and last execution data.
     */
    private JobData toJobData(JobDao.JobRecord record) {
        JobMeta meta = new JobMeta(
                record.id(),
                record.name(),
                record.description(),
                record.priority(),
                record.tags()
        );

        var credentials = credentialDao.findByJobId(record.id());

        JobCommandData command = new JobCommandData(
                ExecutionType.valueOf(record.commandType().toUpperCase()),
                record.command(),
                Interpreter.valueOf(record.interpreter().toUpperCase()),
                credentials
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
    private JobDao.JobRecord toJobRecord(JobConfig config) {
        return new JobDao.JobRecord(
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