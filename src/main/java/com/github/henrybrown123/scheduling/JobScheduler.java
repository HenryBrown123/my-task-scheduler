package com.github.henrybrown123.scheduling;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

/**
 * Bespoke implementation of a scheduler, wrapping ScheduledExecutorService
 **/

public class JobScheduler {
    private final ScheduledExecutorService executor;
    // notes: thread safe collections for tacking jobs at runtime
    private final Map<String, ScheduledFuture<?>> jobs = new ConcurrentHashMap<>();
    private final Set<String> runningJobs = ConcurrentHashMap.newKeySet();

    /**
     * Constructor to create a scheduler instance of a specified pool size
     */
    public JobScheduler(int poolSize) {
        this.executor = Executors.newScheduledThreadPool(poolSize);
    }

    /**
     * Wrapper to provide tracking running jobs, logging etc... could be split
     * into its own class if extra functionality gets too much: ScheduledJob
     * @param jobId
     * @param job
     * @return
     */
    private Runnable wrappedScheduledJob(String jobId, Runnable job) {
        return () -> {
            runningJobs.add(jobId);
            try {
                job.run();
            } finally {
                runningJobs.remove(jobId);
            }
        };
    }

    public void scheduleJob(String jobId, Runnable job, LocalDateTime when) {
        long delayInSeconds = Duration.between(LocalDateTime.now(), when).getSeconds();

        if(runningJobs.contains(jobId)){
            System.out.println("[" + jobId + "] Job already running... unable to schedule");
            return;
        }

        ScheduledFuture<?> future = executor.schedule(
                wrappedScheduledJob(jobId, job),
                delayInSeconds,
                TimeUnit.SECONDS
        );

        jobs.put(jobId, future);
    }

    public void shutdown() {
        executor.shutdown();
    }

}