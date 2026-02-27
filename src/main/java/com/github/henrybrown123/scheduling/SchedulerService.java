package com.github.henrybrown123.scheduling;

import com.github.henrybrown123.configuration.AppConfig;
import com.github.henrybrown123.model.job.Status;
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
 *
 * <p>Process concurrency is limited by counting active (queued + running)
 * executions from the database. The thread pool handles lightweight job
 * setup only — actual process execution is async via {@code Process.onExit()}.
 *
 * <p>Config syncing is handled separately by {@code JobConfigSync} via a
 * file watcher — this class only concerns itself with scheduling.
 */
public class SchedulerService {
    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    private final ScheduledExecutorService executor;
    private final JobDataRepository jobDataRepository;
    private final ExecutionDao executionDao;
    private final JobExecutor jobExecutor;
    private final long pollIntervalMs;
    private final long maxConcurrentProcesses;

    private volatile boolean running = false;
    private Thread schedulerThread;

    public SchedulerService(
            JobDataRepository jobDataRepository,
            ExecutionDao executionDao,
            JobExecutor jobExecutor
    ) {
        this.executor = Executors.newScheduledThreadPool(AppConfig.scheduling().poolSize());
        this.jobDataRepository = jobDataRepository;
        this.executionDao = executionDao;
        this.jobExecutor = jobExecutor;
        this.pollIntervalMs = AppConfig.scheduling().pollIntervalMs();
        this.maxConcurrentProcesses = AppConfig.scheduling().maxConcurrentProcesses();
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
        List<JobData> allJobs = jobDataRepository.getAll();

        long activeCount = allJobs.stream()
                .filter(job -> job.execution() != null)
                .filter(job -> job.execution().status() == Status.QUEUED
                            || job.execution().status() == Status.RUNNING)
                .count();

        long capacity = maxConcurrentProcesses - activeCount;

        if (capacity <= 0) {
            log.debug("At max capacity ({} active), skipping scheduling", activeCount);
            return;
        }

        List<JobData> dueJobs = allJobs.stream()
                .filter(JobData::isDue)
                .filter(JobData::isIdle)
                .limit(capacity)
                .toList();

        dueJobs.forEach(this::scheduleJob);

        if (!dueJobs.isEmpty()) {
            log.info("Scheduled {} due job(s) ({} active, {} capacity)",
                    dueJobs.size(), activeCount, capacity);
        }
    }

    private void scheduleJob(JobData job) {
        String jobId = job.meta().id();
        String jobName = job.meta().name();

        long execId = executionDao.createQueuedExecution(jobId, "scheduler");

        long delayInSeconds = Duration.between(LocalDateTime.now(),
                job.getNextExecutionTime()).getSeconds();

        executor.schedule(
                wrappedJob(jobId, jobName, () -> jobExecutor.runJob(job, execId)),
                Math.max(0, delayInSeconds),
                TimeUnit.SECONDS
        );
    }

    private Runnable wrappedJob(String jobId, String jobName, Runnable job) {
        return () -> {
            MDC.put("jobName", "[" + jobName + "]");
            MDC.put("jobId", jobId);
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
