package com.github.henrybrown123.execution;

import com.github.henrybrown123.shared.model.JobData;
import com.github.henrybrown123.shared.model.job.ExecutionType;
import com.github.henrybrown123.shared.model.job.Interpreter;

import java.io.IOException;
import java.util.Map;

public class CommandExecutor {

    // this is just a nice way of writing an immutable new HashMap<> .... (Map.of)
    private static final Map<Interpreter, String> INLINE_FLAGS = Map.of(
            Interpreter.BASH, "-c",
            Interpreter.PYTHON, "-c",
            Interpreter.GROOVY, "-e"
    );

    /**
     * Returns a String array containing the full command line required depending on the execution
     * type and interpreter being used. E.g. bash cmd, python file. Each item in the array forms
     * part of the command line including any flags or parameters needed.
     *
     * @param interpreter
     * @param command
     * @param type
     * @return
     */
    public static String[] buildCommand(Interpreter interpreter, String command, ExecutionType type) {
        String executable = interpreter.executable;

        return switch (type) {
            case CMD -> new String[]{executable, INLINE_FLAGS.get(interpreter), command};
            case SCRIPT -> new String[]{executable, command};
        };
    }

    /**
     * Executes the relevant command using Java ProcessBulider.
     * todo: allow the target server to be specified?
     * @param job
     * @return
     * @throws IOException
     */
    public static Process execute(JobData job) throws IOException {
        String[] command = buildCommand(
                job.command().interpreter(),
                job.command().command(),
                job.command().type()
        );

        return new ProcessBuilder(command).start();
    }
}
