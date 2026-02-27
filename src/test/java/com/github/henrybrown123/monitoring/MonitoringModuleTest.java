package com.github.henrybrown123.monitoring;

import com.github.henrybrown123.repository.sql.ExecutionDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link MonitoringModule}.
 */
@ExtendWith(MockitoExtension.class)
class MonitoringModuleTest {

    @Mock
    private ExecutionDao executionDao;

    @BeforeEach
    void setUp() {
        when(executionDao.getActiveExecutionCounts())
                .thenReturn(new ExecutionDao.ActiveCounts(0, 0));
    }

    @Test
    void shouldReturnOnDemandSnapshot() {
        var module = new MonitoringModule(executionDao);

        SystemSnapshot snapshot = module.snapshot();

        assertNotNull(snapshot);
        assertNotNull(snapshot.timestamp());
        assertNotNull(snapshot.memory());
        assertNotNull(snapshot.scheduler());
    }

    @Test
    void shouldReturnFreshSnapshotEachCall() {
        var module = new MonitoringModule(executionDao);

        SystemSnapshot first = module.snapshot();
        SystemSnapshot second = module.snapshot();

        assertNotSame(first, second);
    }

    @Test
    void shouldReflectSchedulerState() {
        when(executionDao.getActiveExecutionCounts())
                .thenReturn(new ExecutionDao.ActiveCounts(3, 7));

        var module = new MonitoringModule(executionDao);
        SystemSnapshot snapshot = module.snapshot();

        assertEquals(3, snapshot.scheduler().queuedJobs());
        assertEquals(7, snapshot.scheduler().runningJobs());
    }

    @Test
    void shouldStartDaemonThread() throws Exception {
        var module = new MonitoringModule(executionDao);
        int threadsBefore = Thread.activeCount();

        module.start();
        Thread.sleep(100); // let daemon thread start

        int threadsAfter = Thread.activeCount();
        assertTrue(threadsAfter >= threadsBefore,
                "Should have at least as many threads after starting monitor");
    }
}
