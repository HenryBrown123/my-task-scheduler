package com.github.henrybrown123;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class JobConfigFileReaderTest {

    @Test
    void shouldMapAllJobFieldsCorrectly() throws Exception {
        Path filePath = Paths.get(
                Objects.requireNonNull(getClass().getClassLoader()
                                .getResource("health_test.yaml"))
                        .toURI()
        );

        List<JobData> jobs = JobConfigFileReader.read(filePath);

        assertNotNull(jobs, "Jobs list should not be null");
        assertEquals(1, jobs.size(), "Should have exactly 1 job");

        JobData job = jobs.getFirst();
        assertNotNull(job, "Job should not be null");
    }

    @Test
    void shouldMapMetaFieldsCorrectly() throws Exception {
        // Arrange
        Path filePath = Paths.get(
                Objects.requireNonNull(getClass().getClassLoader()
                                .getResource("health_test.yaml"))
                        .toURI()
        );

        List<JobData> jobs = JobConfigFileReader.read(filePath);
        JobData.JobMeta meta = jobs.getFirst().meta();

        // Assert - Meta fields
        assertNotNull(meta, "Meta should not be null");
        assertEquals("health-check-test", meta.id(), "ID should match");
        assertEquals("System Health Check", meta.name(), "Name should match");
        assertEquals("Quick health check with status echo", meta.description(), "Description should match");
        assertEquals("medium", meta.priority(), "Priority should match");
        assertNotNull(meta.tags(), "Tags should not be null");
        assertTrue(meta.tags().contains("monitoring"), "Tags should contain 'monitoring'");
        assertTrue(meta.tags().contains("health"), "Tags should contain 'health'");
    }

    @Test
    void shouldMapScheduleToCorrectType() throws Exception {
        // Arrange
        Path filePath = Paths.get(
                Objects.requireNonNull(getClass().getClassLoader()
                                .getResource("health_test.yaml"))
                        .toURI()
        );

        List<JobData> jobs = JobConfigFileReader.read(filePath);
        JobScheduleData schedule = jobs.getFirst().schedule();
        // Assert - Schedule type
        assertNotNull(schedule, "Schedule should not be null");
        assertInstanceOf(SimpleSchedule.class, schedule, "Schedule should be SimpleSchedule type");
    }

    @Test
    void shouldMapSimpleScheduleFieldsCorrectly() throws Exception {
        // Arrange
        Path filePath = Paths.get(
                getClass().getClassLoader()
                        .getResource("health_test.yaml")
                        .toURI()
        );

        List<JobData> jobs = JobConfigFileReader.read(filePath);
        SimpleSchedule schedule = (SimpleSchedule) jobs.getFirst().schedule();

        // Assert - Schedule fields
        assertEquals("1m", schedule.interval(), "Interval should be '1m'");
        assertNotNull(schedule.startDate(), "Start date should not be null");
        assertEquals(LocalDate.of(2025, 1, 15), schedule.startDate(),
                "Start date should be 2025-01-15");
        assertNull(schedule.endDate(), "End date should be null");
    }

    @Test
    void shouldMapCommandFieldsCorrectly() throws Exception {
        // Arrange
        Path filePath = Paths.get(
                Objects.requireNonNull(getClass().getClassLoader()
                                .getResource("health_test.yaml"))
                        .toURI()
        );

        List<JobData> jobs = JobConfigFileReader.read(filePath);
        JobData.JobCommandData command = jobs.getFirst().command();

        // Assert - Command fields
        assertNotNull(command, "Command should not be null");
        assertEquals(JobData.ExecutionType.CMD, command.type(), "Type should be CMD");
        assertEquals("echo 'System health check passed' && exit 0", command.command(),
                "Command text should match exactly");
        assertEquals(JobData.Interpreter.BASH, command.interpreter(), "Interpreter should be BASH");
    }

    @Test
    void shouldMapExecutionFieldsCorrectly() throws Exception {
        // Arrange
        Path filePath = Paths.get(
                Objects.requireNonNull(getClass().getClassLoader()
                                .getResource("health_test.yaml"))
                        .toURI()
        );

        List<JobData> jobs = JobConfigFileReader.read(filePath);
        JobData.JobExecutionData execution = jobs.getFirst().execution();

        // Assert - Execution fields
        assertNotNull(execution, "Execution should not be null");
        // Add specific assertions based on how you map "active" status
        assertNotNull(execution.status(), "Status should not be null");
    }

    @Test
    void shouldHandleInvalidInterval() {
        // This tests your validation logic in SimpleSchedule constructor
        assertThrows(RuntimeException.class, () -> {
            new SimpleSchedule("invalid", LocalDate.now(), null);
        }, "Should throw exception for invalid interval format");

        assertThrows(RuntimeException.class, () -> {
            new SimpleSchedule("0m", LocalDate.now(), null);
        }, "Should throw exception for interval less than 1");
    }
}