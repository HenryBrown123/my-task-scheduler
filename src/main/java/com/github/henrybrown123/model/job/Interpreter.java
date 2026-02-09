package com.github.henrybrown123.model.job;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Interpreter {
    BASH("bash", "-c"),
    PYTHON("python3", "-c"),
    GROOVY("groovy", "-e");

    @JsonValue
    public final String type;
    public final String executable;
    private final String flag;

    Interpreter(String executable, String flag) {
        this.type = name().toLowerCase();
        this.executable = executable;
        this.flag = flag;
    }

    public String[] getCommand() {
        return new String[]{executable, flag};
    }

    @JsonCreator
    public static Interpreter fromString(String type) {
        return type == null ? null : Interpreter.valueOf(type.toUpperCase());
    }
}
