package com.github.henrybrown123.configuration;

import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Reads and parses YAML job configuration files.
 * Pure parsing — no database interaction.
 */
public class JobConfigLoader {
    record JobConfigFile(List<JobConfig> jobs) {}

    private final Path jobConfigPath;

    public JobConfigLoader(Path jobConfigPath) {
        this.jobConfigPath = jobConfigPath;
    }

    public List<JobConfig> read() throws InvalidJobConfigException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());

        try {
            JobConfigFile jobsFile = mapper.readValue(jobConfigPath.toFile(), JobConfigFile.class);

            if (jobsFile == null || jobsFile.jobs() == null || jobsFile.jobs().isEmpty()) {
                return Collections.emptyList();
            }

            return jobsFile.jobs();
        } catch (DatabindException e) {
            throw new InvalidJobConfigException(jobConfigPath, e);
        } catch (IOException e) {
            throw new InvalidJobConfigException("Failed to read config file: " + jobConfigPath, e);
        }
    }
}