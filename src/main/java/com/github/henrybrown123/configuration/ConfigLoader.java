package com.github.henrybrown123.configuration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.github.henrybrown123.shared.model.JobData;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class ConfigLoader {
    record JobConfigFile(List<JobData> jobs) {}

    public static List<JobData> read(Path file) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        mapper.registerModule(new JavaTimeModule());
        JobConfigFile jobsFile = mapper.readValue(file.toFile(), JobConfigFile.class);
        return jobsFile.jobs();
    }
}
