package com.github.henrybrown123.shared.model.job;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum ExecutionType {
    CMD("cmd"), SCRIPT("script");

    @JsonValue
    public final String commandType;

    ExecutionType(String commandType) {
        this.commandType = commandType.toUpperCase();
    }

    @JsonCreator
    public static ExecutionType fromString(String commandType) {
        return commandType == null
                ? null
                : ExecutionType.valueOf(commandType.toUpperCase());
    }
}
