package com.github.henrybrown123.shared.model.job;

public record JobCommandData(
        String execute,
        ExecutionType type,
        String command,
        Interpreter interpreter
) {}
