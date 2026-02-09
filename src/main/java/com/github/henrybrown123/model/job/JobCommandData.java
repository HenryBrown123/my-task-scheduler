package com.github.henrybrown123.model.job;

import com.github.henrybrown123.model.job.execution.ExecutionType;

public record JobCommandData(
        String execute,
        ExecutionType type,
        String command,
        Interpreter interpreter
) {}
