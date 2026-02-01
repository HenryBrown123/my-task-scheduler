package com.github.henrybrown123;

import com.github.henrybrown123.shared.model.JobData;
import com.github.henrybrown123.shared.model.job.ExecutionType;
import com.github.henrybrown123.shared.model.job.Interpreter;

import java.io.IOException;
import java.util.Map;

public class CommandExecutor {

    // Map of interpreter-specific inline flags
    private static final Map<Interpreter, String> INLINE_FLAGS = Map.of(
            Interpreter.BASH, "-c",
            Interpreter.PYTHON, "-c",
            Interpreter.GROOVY, "-e"
    );

    public static String[] buildCommand(Interpreter interpreter, String command, ExecutionType type) {
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
