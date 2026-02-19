package com.github.henrybrown123.configuration;

import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.henrybrown123.model.JobData;
import com.github.henrybrown123.repository.JobAggregateProvider;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public class ConfigLoader {
    record JobConfigFile(List<JobConfig> jobs) {}

    private final Path jobConfigPath;
    private final JobAggregateProvider jobAggregateProvider;

    public ConfigLoader(Path jobConfigPath, JobAggregateProvider jobAggregateProvider) {
        this.jobConfigPath = jobConfigPath;
        this.jobAggregateProvider = jobAggregateProvider;
    }

    public void loadAndSync() throws InvalidConfigException {
        List<JobConfig> configs = read();
        syncWithDatabase(configs);
    }

    public List<JobConfig> read() throws InvalidConfigException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());

        try {
            JobConfigFile jobsFile = mapper.readValue(jobConfigPath.toFile(), JobConfigFile.class);

            if (jobsFile == null || jobsFile.jobs() == null || jobsFile.jobs().isEmpty()) {
                return Collections.emptyList();
            }

            return jobsFile.jobs();
        } catch (DatabindException e) {
            throw new InvalidConfigException(jobConfigPath, e);
        } catch (IOException e) {
            throw new InvalidConfigException("Failed to read config file: " + jobConfigPath, e);
        }
    }

    private void syncWithDatabase(List<JobConfig> configs) {
        List<JobData> existingJobs = jobAggregateProvider.getAllJobs();

        Map<String, JobConfig> existingJobsMap = existingJobs.stream()
                .collect(Collectors.toMap(job -> job.meta().id(), JobConfig::fromJobData));

        // todo: implement bulk save functionality ... ie.. filter by hasChanged and bulk save
        for (JobConfig config : configs) {
            JobConfig existingJob = existingJobsMap.get(config.meta().id());
            // only save jobs that have changed...
            if (existingJob == null || hasChanged(config, existingJob)) {
                jobAggregateProvider.saveJob(config.toJobData());
            }
        }
    }

    private boolean hasChanged(JobConfig newConfig, JobConfig oldConfig) {
        return !Objects.equals(newConfig.meta(), oldConfig.meta()) ||
                !Objects.equals(newConfig.command(), oldConfig.command()) ||
                !Objects.equals(newConfig.schedule(), oldConfig.schedule());
    }
}