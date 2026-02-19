package com.github.henrybrown123.configuration;

import com.github.henrybrown123.model.job.Interpreter;
import com.github.henrybrown123.model.job.execution.ExecutionType;
import com.github.henrybrown123.model.job.schedule.SimpleSchedule;
import com.github.henrybrown123.repository.JobDataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    @Mock
    private JobDataRepository jobDataRepo;

    private Path configPath;

    @BeforeEach
    void setUp() {
        configPath = tempDir.resolve("jobs.yaml");
    }

    @Test
    void shouldReadValidConfigFile() throws Exception {
        Files.writeString(configPath, """
            jobs:
              - meta:
                  id: test-job
                  name: "Test Job"
                  description: "A test job"
                  priority: high
                  tags:
                    - test
                schedule:
                  type: simple
                  interval: 5m
                  start_date: "2025-01-01"
                command:
                  type: cmd
                  command: "echo hello"
                  interpreter: bash
            """);

        JobConfigLoader loader = new JobConfigLoader(configPath, jobDataRepo);
        List<JobConfig> configs = loader.read();

        assertEquals(1, configs.size());
        assertEquals("test-job", configs.get(0).meta().id());
        assertEquals("Test Job", configs.get(0).meta().name());
        assertEquals("high", configs.get(0).meta().priority());
        assertEquals(List.of("test"), configs.get(0).meta().tags());
        assertEquals(ExecutionType.CMD, configs.get(0).command().type());
        assertEquals("echo hello", configs.get(0).command().command());
        assertEquals(Interpreter.BASH, configs.get(0).command().interpreter());
        assertInstanceOf(SimpleSchedule.class, configs.get(0).schedule());
        assertEquals("5m", ((SimpleSchedule) configs.get(0).schedule()).interval());
        assertEquals(LocalDate.of(2025, 1, 1), ((SimpleSchedule) configs.get(0).schedule()).startDate());
    }

    @Test
    void shouldReadMultipleJobs() throws Exception {
        Files.writeString(configPath, """
            jobs:
              - meta:
                  id: job-1
                  name: "First"
                  description: "First job"
                  priority: high
                  tags: []
                schedule:
                  type: simple
                  interval: 5m
                command:
                  type: cmd
                  command: "echo first"
                  interpreter: bash
              - meta:
                  id: job-2
                  name: "Second"
                  description: "Second job"
                  priority: low
                  tags: []
                schedule:
                  type: simple
                  interval: 10m
                command:
                  type: cmd
                  command: "echo second"
                  interpreter: bash
            """);

        JobConfigLoader loader = new JobConfigLoader(configPath, jobDataRepo);
        List<JobConfig> configs = loader.read();

        assertEquals(2, configs.size());
        assertEquals("job-1", configs.get(0).meta().id());
        assertEquals("job-2", configs.get(1).meta().id());
    }

    @Test
    void shouldThrowInvalidConfigExceptionForMissingFile() {
        Path nonExistent = tempDir.resolve("nonexistent.yaml");
        JobConfigLoader loader = new JobConfigLoader(nonExistent, jobDataRepo);

        assertThrows(InvalidJobConfigException.class, loader::read);
    }

    @Test
    void shouldThrowInvalidConfigExceptionForMalformedYaml() throws Exception {
        Files.writeString(configPath, """
            jobs:
              - meta:
                  id: test
                invalid indentation here
            """);

        JobConfigLoader loader = new JobConfigLoader(configPath, jobDataRepo);
        assertThrows(InvalidJobConfigException.class, loader::read);
    }

    @Test
    void shouldDeserializeTestJobsYamlResource() throws Exception {
        Path filePath = Paths.get(
                Objects.requireNonNull(getClass().getClassLoader()
                                .getResource("test_jobs.yaml"))
                        .toURI()
        );

        var loader = new JobConfigLoader(filePath, jobDataRepo);
        var job = loader.read().getFirst();

        assertEquals("health-check-test", job.meta().id());
        assertInstanceOf(SimpleSchedule.class, job.schedule());
        assertEquals(ExecutionType.CMD, job.command().type());
    }
}