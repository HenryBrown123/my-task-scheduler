package com.github.henrybrown123.model.job;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.henrybrown123.model.job.execution.ExecutionType;
import com.github.henrybrown123.security.AppCredential;
import com.github.henrybrown123.security.SecretType;

import java.util.List;

public record JobCommandData(
        String execute,
        ExecutionType type,
        String command,
        Interpreter interpreter,
        @JsonInclude(JsonInclude.Include.NON_NULL)
        List<AppCredential> credentials
) {}
