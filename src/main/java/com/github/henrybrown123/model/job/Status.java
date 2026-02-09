package com.github.henrybrown123.model.job;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Status {
    IDLE("idle"), RUNNING("running"), ERROR("error"), ACTIVE("active");

    @JsonValue
    public final String type;

    Status(String type) {
        this.type = type.toUpperCase();
    }

    @JsonCreator
    public static Status fromString(String type) {
        return type == null
                ? null
                : Status.valueOf(type.toUpperCase());
    }
}
