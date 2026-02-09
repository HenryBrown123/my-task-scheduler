package com.github.henrybrown123.model.job.schedule;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public record SimpleSchedule(
        String interval,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate
) implements IJobScheduleData {


    @Override
    @JsonProperty("type")
    public String type() {
        return "simple";
    }

    public sealed interface ValidationResult {
        record Success() implements ValidationResult {}
        record Error(String message) implements ValidationResult {}
    }

    private ValidationResult validateInterval(String interval) {
        Pattern intervalPattern = Pattern.compile("(\\d+)([a-z])");
        Matcher matcher = intervalPattern.matcher(interval);

        if (!matcher.matches()) {
            return new ValidationResult.Error("Interval not valid, must be <digit><timecode> e.g. 5m, 1h, 2h, 1M, 1y");
        }

        int every = Integer.parseInt(matcher.group(1));

        if (every < 1) {
            return new ValidationResult.Error("Interval must be at least 1, not " + every);
        }

        String timeCode = matcher.group(2);
        boolean isValidTimeUnit = Arrays.stream(TimeUnitShort.values())
                .anyMatch(unit -> unit.timeCode().equals(timeCode));

        if (!isValidTimeUnit) {
            String validUnits = Arrays.stream(TimeUnitShort.values())
                    .map(TimeUnitShort::description)
                    .collect(Collectors.joining(", "));
            return new ValidationResult.Error("Invalid interval, must be one of: " + validUnits);
        }

        return new ValidationResult.Success();
    }

    public SimpleSchedule {
        if (validateInterval(interval) instanceof ValidationResult.Error(String message)) {
            throw new RuntimeException(message);
        }
    }

    @Override
    public LocalDateTime getNextExecutionDate(LocalDateTime lastExecutionStarted) {
        LocalDateTime now = LocalDateTime.now();

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(23, 59, 59) : null;

        if (!isScheduleActive(now, start, end)) {
            return null;
        }

        if (lastExecutionStarted == null) {
            return now;
        }

        Duration intervalSeconds = parseIntervalToSeconds(interval);

        return lastExecutionStarted.plus(intervalSeconds);
    }

    private Duration parseIntervalToSeconds(String interval) {
        Pattern pattern = Pattern.compile("(\\d+)([a-z])");
        Matcher matcher = pattern.matcher(interval);

        if (matcher.matches()) {
            int value = Integer.parseInt(matcher.group(1));
            var timeCode = matcher.group(2);

            var seconds =  switch (timeCode) {
                case "m" -> (long) value * 60;
                case "h" -> value * 60 * 60 * 1000L;
                case "d" -> value * 24 * 60 * 60 * 1000L;
                case "w" -> value * 7 * 24 * 60 * 60 * 1000L;
                case "M" -> value * 30L * 24 * 60 * 60 * 1000L;
                case "y" -> value * 365L * 24 * 60 * 60 * 1000L;
                default -> throw new IllegalArgumentException("Unknown time code: " + timeCode);
            };

            return Duration.ofSeconds(seconds);
        }
        throw new IllegalArgumentException("Invalid interval format: " + interval);
    }

    enum TimeUnitShort {
        MINUTE("m"), HOUR("h"), DAY("d"),
        WEEK("w"), MONTH("M"), YEAR("y");

        private final String timeCode;

        TimeUnitShort(String timeCode) {
            this.timeCode = timeCode;
        }

        public String timeCode() {
            return timeCode;
        }

        public String description() {
            return timeCode + " - " + this.name();
        }
    }
}