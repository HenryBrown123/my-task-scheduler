package com.github.henrybrown123.configuration;

import com.github.henrybrown123.model.job.Interpreter;
import com.github.henrybrown123.model.job.execution.ExecutionType;
import com.github.henrybrown123.model.job.schedule.SimpleSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class ConfigLoaderTest {

    @TempDir
    Path tempDir;

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

        JobConfigLoader loader = new JobConfigLoader(configPath);
        Map<Integer, JobConfig> configs = loader.read();

        assertEquals(1, configs.size());
        JobConfig config = configs.get(1); // 1-indexed
        assertEquals("test-job", config.meta().id());
        assertEquals("Test Job", config.meta().name());
        assertEquals("high", config.meta().priority());
        assertEquals(List.of("test"), config.meta().tags());
        assertEquals(ExecutionType.CMD, config.command().type());
        assertEquals("echo hello", config.command().command());
        assertEquals(Interpreter.BASH, config.command().interpreter());
        assertInstanceOf(SimpleSchedule.class, config.schedule());
        assertEquals("5m", ((SimpleSchedule) config.schedule()).interval());
        assertEquals(LocalDate.of(2025, 1, 1), ((SimpleSchedule) config.schedule()).startDate());
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

        JobConfigLoader loader = new JobConfigLoader(configPath);
        Map<Integer, JobConfig> configs = loader.read();

        assertEquals(2, configs.size());
        assertEquals("job-1", configs.get(1).meta().id()); // 1-indexed
        assertEquals("job-2", configs.get(2).meta().id());
    }

    @Test
    void shouldThrowInvalidConfigExceptionForMissingFile() {
        Path nonExistent = tempDir.resolve("nonexistent.yaml");
        JobConfigLoader loader = new JobConfigLoader(nonExistent);

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

        JobConfigLoader loader = new JobConfigLoader(configPath);
        assertThrows(InvalidJobConfigException.class, loader::read);
    }

    @Test
    void shouldDeserializeTestJobsYamlResource() throws Exception {
        Path filePath = Paths.get(
                Objects.requireNonNull(getClass().getClassLoader()
                                .getResource("test_jobs.yaml"))
                        .toURI()
        );

        var loader = new JobConfigLoader(filePath);
        var job = loader.read().get(1); // 1-indexed

        assertEquals("health-check-test", job.meta().id());
        assertInstanceOf(SimpleSchedule.class, job.schedule());
        assertEquals(ExecutionType.CMD, job.command().type());
    }
}