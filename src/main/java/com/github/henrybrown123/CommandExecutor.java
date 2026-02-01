package com.github.henrybrown123;

import java.io.IOException;
import java.util.Map;

public class CommandExecutor {

    // Map of interpreter-specific inline flags
    private static final Map<JobData.Interpreter, String> INLINE_FLAGS = Map.of(
            JobData.Interpreter.BASH, "-c",
            JobData.Interpreter.PYTHON, "-c",
            JobData.Interpreter.GROOVY, "-e"
    );

    public static String[] buildCommand(JobData.Interpreter interpreter, String command, JobData.ExecutionType type) {
        String executable = interpreter.executable;

        return switch (type) {
            case CMD -> {
                // Inline command needs a flag
                String flag = INLINE_FLAGS.get(interpreter);
                yield new String[]{executable, flag, command};
            }
            case SCRIPT -> {
                // Script file doesn't need a flag
                yield new String[]{executable, command};
            }
        };
    }

    public static Process execute(JobData job) throws IOException {
        String[] command = buildCommand(
                job.command().interpreter(),
                job.command().command(),
                job.command().type()
        );

        return new ProcessBuilder(command).start();
    }
}