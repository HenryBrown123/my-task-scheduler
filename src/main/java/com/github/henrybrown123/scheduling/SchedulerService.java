package com.github.henrybrown123.scheduling;

import com.github.henrybrown123.configuration.AppConfig;
import com.github.henrybrown123.configuration.JobConfigSync;
import com.github.henrybrown123.scheduling.execution.JobExecutor;
import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.repository.JobDataRepository;
import com.github.henrybrown123.repository.sql.ExecutionDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

/**
 * Main scheduling service that polls for due jobs and executes them.
 */
public class SchedulerService {
    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final ScheduledExecutorService executor;
    private final JobDataRepository jobDataRepository;
    private final ExecutionDao executionDao;
    private final JobExecutor jobExecutor;
    private final JobConfigSync configSync;
    private final long pollIntervalMs;

    private volatile boolean running = false;
    private Thread schedulerThread;

    public SchedulerService(
            JobDataRepository jobDataRepository,
            ExecutionDao executionDao,
            JobExecutor jobExecutor,
            JobConfigSync configSync
    ) {
        this.executor = Executors.newScheduledThreadPool(AppConfig.scheduling().poolSize());
        this.jobDataRepository = jobDataRepository;
        this.executionDao = executionDao;
        this.jobExecutor = jobExecutor;
        this.configSync = configSync;
        this.pollIntervalMs = AppConfig.scheduling().pollIntervalMs();
    }

    public void start() {
        running = true;
        schedulerThread = new Thread(this::run, "SchedulerService-Thread");
        schedulerThread.start();
    }

    private void run() {
        while (running) {
            try {
                tick();
                Thread.sleep(pollIntervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    public void tick() {
        configSync.syncIfChanged();

        List<JobData> dueJobs = jobDataRepository.getAll().stream()
                .filter(JobData::isDue)
                .filter(JobData::isIdle)
                .toList();

        dueJobs.forEach(this::scheduleJob);

        if (!dueJobs.isEmpty()) {
            log.info("Scheduled {} due job(s)", dueJobs.size());
        }
    }

    private void scheduleJob(JobData job) {
        String jobId = job.meta().id();
        String jobName = job.meta().name();

        long execId = executionDao.createQueuedExecution(jobId, "scheduler");

        long delayInSeconds = Duration.between(LocalDateTime.now(),
                job.getNextExecutionTime()).getSeconds();

        executor.schedule(
                wrappedJob(jobId, jobName, execId, () -> jobExecutor.runJob(job, execId)),
                delayInSeconds,
                TimeUnit.SECONDS
        );
    }

    private Runnable wrappedJob(String jobId, String jobName, long execId, Runnable job) {
        return () -> {
            MDC.put("jobName", "[" + jobName + "]");
            MDC.put("jobId", jobId);
            executionDao.updateExecutionStatus(execId, "running");
            try {
                job.run();
            } finally {
                MDC.clear();
            }
        };
    }

    public void shutdown() {
        running = false;
        if (schedulerThread != null) {
            schedulerThread.interrupt();
        }
        executor.shutdown();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }
}
