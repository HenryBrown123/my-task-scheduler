package com.github.henrybrown123.execution;

import com.github.henrybrown123.model.job.Interpreter;
import com.github.henrybrown123.model.job.execution.ExecutionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommandBuilderTest {

    @Test
    void shouldBuildBashCmdCommand() {
        String[] command = CommandBuilder.buildCommand(Interpreter.BASH, "echo hello", ExecutionType.CMD);

        assertEquals(3, command.length);
        assertEquals("bash", command[0]);
        assertEquals("-c", command[1]);
        assertEquals("echo hello", command[2]);
    }

    @Test
    void shouldBuildBashScriptCommand() {
        String[] command = CommandBuilder.buildCommand(Interpreter.BASH, "/path/to/script.sh", ExecutionType.SCRIPT);

        assertEquals(2, command.length);
        assertEquals("bash", command[0]);
        assertEquals("/path/to/script.sh", command[1]);
    }

    @Test
    void shouldBuildPythonCmdCommand() {
        String[] command = CommandBuilder.buildCommand(Interpreter.PYTHON, "print('hello')", ExecutionType.CMD);

        assertEquals(3, command.length);
        assertEquals("python3", command[0]);
        assertEquals("-c", command[1]);
        assertEquals("print('hello')", command[2]);
    }

    @Test
    void shouldBuildPythonScriptCommand() {
        String[] command = CommandBuilder.buildCommand(Interpreter.PYTHON, "/path/to/script.py", ExecutionType.SCRIPT);

        assertEquals(2, command.length);
        assertEquals("python3", command[0]);
        assertEquals("/path/to/script.py", command[1]);
    }

    @Test
    void shouldBuildGroovyCmdCommand() {
        String[] command = CommandBuilder.buildCommand(Interpreter.GROOVY, "println 'hello'", ExecutionType.CMD);

        assertEquals(3, command.length);
        assertEquals("groovy", command[0]);
        assertEquals("-e", command[1]);
        assertEquals("println 'hello'", command[2]);
    }

    @Test
    void shouldBuildGroovyScriptCommand() {
        String[] command = CommandBuilder.buildCommand(Interpreter.GROOVY, "/path/to/script.groovy", ExecutionType.SCRIPT);

        assertEquals(2, command.length);
        assertEquals("groovy", command[0]);
        assertEquals("/path/to/script.groovy", command[1]);
    }

    @Test
    void shouldHandleComplexBashCommand() {
        String complexCmd = "ls -la | grep test && echo done";
        String[] command = CommandBuilder.buildCommand(Interpreter.BASH, complexCmd, ExecutionType.CMD);

        assertEquals(3, command.length);
        assertEquals(complexCmd, command[2]);
    }
}
