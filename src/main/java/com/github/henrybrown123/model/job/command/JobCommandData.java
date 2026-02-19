package com.github.henrybrown123.model.job.command;

import com.github.henrybrown123.model.job.Interpreter;
import com.github.henrybrown123.model.job.execution.ExecutionType;

import java.util.List;

public record JobCommandData(
     //   String execute,
        ExecutionType type,
        String command,
        Interpreter interpreter,
        List<JobCredential> credentials
) {
        public JobCommandData {
                if (credentials == null) credentials = List.of();
        }
}
