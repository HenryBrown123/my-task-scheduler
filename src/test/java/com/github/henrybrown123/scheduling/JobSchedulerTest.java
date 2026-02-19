package com.github.henrybrown123.scheduling;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class JobSchedulerTest {

    private JobScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new JobScheduler(2);
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    /**
     * Verifies that JobScheduler rejects a duplicate schedule request when a job
     * with the same ID is already running. Other logic tested within the integration tests.
     * <p>
     * The test uses two "latches" to control timing across threads:
     *   - startLatch: test thread waits here until the first job has started running
     *   - endLatch: test thread waits here until the first job finishes
     * <p>
     * Timeline:
     *   1. Schedule "long-job" — runs on pool thread, increments counter to 1
     *   2. Pool thread counts down startLatch — test thread unblocks
     *   3. Test thread schedules "long-job" again — should be rejected (job still running)
     *   4. Pool thread sleeps 2s then counts down endLatch — test thread unblocks
     *   5. Assert counter is still 1 — proving the second schedule was rejected
     */
    @Test
    void shouldNotScheduleDuplicateRunningJob() throws Exception {
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(1);
        AtomicInteger executionCount = new AtomicInteger(0);

        // first schedule — this job will run for 2 seconds to simulate a long-running job
        scheduler.scheduleJob("long-job", () -> {
            executionCount.incrementAndGet();
            startLatch.countDown(); // signal: job has started
            try {
                Thread.sleep(2000); // hold the job open so it's still "running"
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            endLatch.countDown(); // signal: job is done
        }, LocalDateTime.now());

        // wait until the first job is running before attempting the duplicate
        assertTrue(startLatch.await(5, TimeUnit.SECONDS));

        // second schedule with same ID — should be rejected because "long-job" is still running
        scheduler.scheduleJob("long-job", executionCount::incrementAndGet, LocalDateTime.now());

        /* wait for the first job to finish */
        endLatch.await(5, TimeUnit.SECONDS);

        // counter should be 1, not 2 — proving the duplicate was rejected
        assertEquals(1, executionCount.get());
    }
}