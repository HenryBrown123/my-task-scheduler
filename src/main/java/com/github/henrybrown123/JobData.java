package com.github.henrybrown123;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;
import org.jetbrains.annotations.Nullable;

import java.time.LocalDateTime;
import java.util.List;


public record JobData(
        JobMeta meta,
        JobCommandData command,
        JobScheduleData schedule,
        JobExecutionData execution
) {
    public enum ExecutionType {
        CMD("cmd"), SCRIPT("script");
        @JsonValue
        public final String commandType;

        ExecutionType(String commandType) {
            this.commandType = commandType.toUpperCase();
        }

        /**
         * Some slightly fiddly logic to allow jackson to deserialize yaml values in either lowercase or uppercase
         * @param commandType
         * @return
         */
        @JsonCreator
        public static ExecutionType fromString(String commandType) {
            return commandType == null
                    ? null
                    : ExecutionType.valueOf(commandType.toUpperCase());
        }

    }

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


    public enum Status {
        IDLE("idle"), RUNNING("running"), ERROR("error"), ACTIVE("active");
        @JsonValue
        public final String type;
        Status(String type){
            this.type = type.toUpperCase();
        }

        @JsonCreator
        public static Status fromString(String type) {
            return type == null
                    ? null
                    : Status.valueOf(type.toUpperCase());

        }
    }

    public record JobMeta(
            String id,
            String name,
            String description,
            String priority,
            List<String> tags
    ) {}

    public record JobCommandData(
            String execute,
            ExecutionType type,
            String command,
            Interpreter interpreter
    ) {}

    public record JobExecutionData(
            Status status,
            @JsonProperty("last_execution") LocalDateTime lastExecution,
            @JsonProperty("end_date") LocalDateTime endDate,
            @JsonProperty("last_run_status") String lastRunStatus,
            LocalDateTime overdue
    ) {}
}