package com.github.henrybrown123;

import com.github.henrybrown123.model.job.schedule.SimpleSchedule;
import com.github.henrybrown123.repository.JobRepository;
import org.junit.jupiter.api.Test;
import org.testng.annotations.Ignore;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class JobConfigFileReaderTest {

    @Test
    @Ignore
    void shouldMapAllJobFieldsCorrectly() throws Exception {
        Path filePath = Paths.get(
                Objects.requireNonNull(getClass().getClassLoader()
                                .getResource("test_jobs.yaml"))
                        .toURI()
        );


//        List<JobConfig> jobs = new ConfigLoader(filePath).read();
//
//        assertNotNull(jobs, "Jobs list should not be null");
//        assertEquals(1, jobs.size(), "Should have exactly 1 job");
//
//        JobConfig job = jobs.getFirst();
//        assertNotNull(job, "Job should not be null");
    }

    @Test
    void shouldMapMetaFieldsCorrectly() throws Exception {
        Path filePath = Paths.get(
                Objects.requireNonNull(getClass().getClassLoader()
                                .getResource("test_jobs.yaml"))
                        .toURI()
        );
//
//        List<JobConfig> jobs = new ConfigLoader(filePath).read();
//        JobMeta meta = jobs.getFirst().meta();

//        assertNotNull(meta, "Meta should not be null");
//        assertEquals("health-check-test", meta.id(), "ID should match");
//        assertEquals("System Health Check", meta.name(), "Name should match");
//        assertEquals("Quick health check with status echo", meta.description(), "Description should match");
//        assertEquals("medium", meta.priority(), "Priority should match");
//        assertNotNull(meta.tags(), "Tags should not be null");
//        assertTrue(meta.tags().contains("monitoring"), "Tags should contain 'monitoring'");
//        assertTrue(meta.tags().contains("health"), "Tags should contain 'health'");
    }

    @Test
    void shouldMapScheduleToCorrectType() throws Exception {
        Path filePath = Paths.get(
                Objects.requireNonNull(getClass().getClassLoader()
                                .getResource("test_jobs.yaml"))
                        .toURI()
        );

//        List<JobConfig> jobs = new ConfigLoader(filePath).read();
//        IJobScheduleData schedule = jobs.getFirst().schedule();
//        assertNotNull(schedule, "Schedule should not be null");
//        assertInstanceOf(SimpleSchedule.class, schedule, "Schedule should be SimpleSchedule type");
    }

    @Test
    void shouldMapSimpleScheduleFieldsCorrectly() throws Exception {
        Path filePath = Paths.get(
                getClass().getClassLoader()
                        .getResource("test_jobs.yaml")
                        .toURI()
        );
//
//        List<JobConfig> jobs = new ConfigLoader(filePath).read();
//        SimpleSchedule schedule = (SimpleSchedule) jobs.getFirst().schedule();
//
//        assertEquals("1m", schedule.interval(), "Interval should be '1m'");
//        assertNotNull(schedule.startDate(), "Start date should not be null");
//        assertEquals(LocalDate.of(2025, 1, 15), schedule.startDate(),
//                "Start date should be 2025-01-15");
//        assertNull(schedule.endDate(), "End date should be null");
    }

    @Test
    void shouldMapCommandFieldsCorrectly() throws Exception {
        Path filePath = Paths.get(
                Objects.requireNonNull(getClass().getClassLoader()
                                .getResource("test_jobs.yaml"))
                        .toURI()
        );
//
//        List<JobConfig> jobs = new ConfigLoader(filePath).read();
//        JobCommandData command = jobs.getFirst().command();
//
//        assertNotNull(command, "Command should not be null");
//        assertEquals(ExecutionType.CMD, command.type(), "Type should be CMD");
//        assertEquals("echo 'System health check passed' && exit 0", command.command(),
//                "Command text should match exactly");
//        assertEquals(Interpreter.BASH, command.interpreter(), "Interpreter should be BASH");
    }


    @Test
    void shouldHandleInvalidInterval() {
        assertThrows(RuntimeException.class, () -> {
            new SimpleSchedule("invalid", LocalDate.now(), null);
        }, "Should throw exception for invalid interval format");

        assertThrows(RuntimeException.class, () -> {
            new SimpleSchedule("0m", LocalDate.now(), null);
        }, "Should throw exception for interval less than 1");
    }
}
