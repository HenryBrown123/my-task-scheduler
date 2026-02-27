package com.github.henrybrown123.repository;

import com.github.henrybrown123.configuration.JobConfig;
import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.model.job.Interpreter;
import com.github.henrybrown123.model.job.JobMeta;
import com.github.henrybrown123.model.job.command.JobCommandData;
import com.github.henrybrown123.model.job.execution.ExecutionType;
import com.github.henrybrown123.model.job.execution.JobExecutionData;
import com.github.henrybrown123.repository.sql.CredentialDao;
import com.github.henrybrown123.repository.sql.ExecutionDao;
import com.github.henrybrown123.repository.sql.JobDao;
import com.github.henrybrown123.repository.sql.ScheduleDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Aggregates job data across repositories and maps between
 * persistence records and domain objects.
 *
 * <p>Job config (meta, command, schedule, credentials) is cached in memory
 * and only reloaded when config file changes trigger a sync.
 * Execution status is always fetched fresh from the database.
 */
public class JobDataRepository {
    private static final Logger log = LoggerFactory.getLogger(JobDataRepository.class);

    private final JobDao jobRepo;
    private final ScheduleDao scheduleRepo;
    private final ExecutionDao execRepo;
    private final CredentialDao credentialDao;

    /** Cached job config keyed by job ID. Invalidated on config sync. */
    private final Map<String, JobData> configCache = new ConcurrentHashMap<>();

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
     * Invalidates the config cache so the next getAll() reloads.
     */
    public void sync(Map<Integer, JobConfig> incoming) {
        Set<String> incomingIds = incoming.values().stream()
                .map(config -> config.meta().id())
                .collect(Collectors.toSet());

        jobRepo.findAllIds().stream()
                .filter(id -> !incomingIds.contains(id))
                .forEach(jobRepo::deactivate);

        incoming.forEach(this::save);
        reloadConfigCache();
    }

    private void save(Integer ordering, JobConfig config) {
        jobRepo.save(toJobRecord(config, ordering));
        scheduleRepo.save(config.meta().id(), config.schedule());
        config.command().credentials().forEach(cred ->
                credentialDao.save(config.meta().id(), cred));
    }

    public Optional<JobData> get(String jobId) {
        return jobRepo.findById(jobId)
                .map(this::toJobData);
    }

    /**
     * Returns all active jobs with fresh execution status.
     * Job config comes from cache; execution data from a single bulk query.
     */
    public List<JobData> getAll() {
        if (configCache.isEmpty()) {
            reloadConfigCache();
        }

        Map<String, JobExecutionData> executions = execRepo.getAllLatestExecutions();

        return configCache.values().stream()
                .map(cached -> new JobData(
                        cached.meta(),
                        cached.command(),
                        cached.schedule(),
                        executions.getOrDefault(cached.meta().id(), null)
                ))
                .toList();
    }

    /**
     * Reloads all job config from the database into the cache.
     * Called on startup and after config sync.
     */
    private void reloadConfigCache() {
        configCache.clear();
        jobRepo.findAllActiveJobs().stream()
                .map(this::toJobData)
                .forEach(job -> configCache.put(job.meta().id(), job));
        log.debug("Config cache loaded ({} jobs)", configCache.size());
    }

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

        // Execution is null in cache — always merged fresh in getAll()
        return new JobData(meta, command, schedule, null);
    }

    private JobDao.JobRecord toJobRecord(JobConfig config, Integer ordering) {
        return new JobDao.JobRecord(
                config.meta().id(),
                ordering,
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
