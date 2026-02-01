package com.github.henrybrown123;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.*;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = SimpleSchedule.class, name = "simple"),
        @JsonSubTypes.Type(value = CronSchedule.class, name = "cron"),
        @JsonSubTypes.Type(value = WeeklySchedule.class, name = "weekly"),
        @JsonSubTypes.Type(value = MonthlySchedule.class, name = "monthly"),
        @JsonSubTypes.Type(value = YearlySchedule.class, name = "yearly")
})
public sealed interface JobScheduleData permits
        SimpleSchedule,
        CronSchedule,
        WeeklySchedule,
        MonthlySchedule,
        YearlySchedule {

    boolean isDue(LocalDateTime lastExecution);

    // Shared helper methods
    default boolean isWithinDateRange(LocalDateTime now, LocalDateTime start, LocalDateTime end) {
        if (start != null && now.isBefore(start)) {
            return true;
        }
        return end != null && now.isAfter(end);
    }

    default boolean hasRunInPeriod(LocalDateTime lastExecution, LocalDateTime periodStart) {
        if (lastExecution == null) {
            return false;
        }
        return lastExecution.isBefore(periodStart);
    }
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

record SimpleSchedule(
        String interval,
        @JsonProperty("start_date") LocalDate startDate,
        @JsonProperty("end_date") LocalDate endDate
) implements JobScheduleData {

    public sealed interface ValidationResult {
        record Success() implements ValidationResult {
        }

        record Error(String message) implements ValidationResult {
        }
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
    public boolean isDue(LocalDateTime lastExecution) {
        LocalDateTime now = LocalDateTime.now();

        // Convert LocalDate to LocalDateTime for range checking
        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(23, 59, 59) : null;

        // Use shared date range check
        if (isWithinDateRange(now, start, end)) {
            return false;
        }

        // If never executed, it's due
        if (lastExecution == null) {
            return true;
        }

        // Parse interval and check if enough time has passed
        long intervalMillis = parseIntervalToMillis(interval);
        Duration timeSinceLastRun = Duration.between(lastExecution, now);

        return timeSinceLastRun.toMillis() >= intervalMillis;
    }

    private long parseIntervalToMillis(String interval) {
        Pattern pattern = Pattern.compile("(\\d+)([a-z])");
        Matcher matcher = pattern.matcher(interval);

        if (matcher.matches()) {
            int value = Integer.parseInt(matcher.group(1));
            String timeCode = matcher.group(2);

            return switch (timeCode) {
                case "m" -> value * 60 * 1000L;
                case "h" -> value * 60 * 60 * 1000L;
                case "d" -> value * 24 * 60 * 60 * 1000L;
                case "w" -> value * 7 * 24 * 60 * 60 * 1000L;
                case "M" -> value * 30L * 24 * 60 * 60 * 1000L;
                case "y" -> value * 365L * 24 * 60 * 60 * 1000L;
                default -> throw new IllegalArgumentException("Unknown time code: " + timeCode);
            };
        }
        throw new IllegalArgumentException("Invalid interval format: " + interval);
    }
}

record CronSchedule(
        String expression,
        @JsonProperty("start_date") LocalDateTime startDate,
        @JsonProperty("end_date") LocalDateTime endDate
) implements JobScheduleData {

    @Override
    public boolean isDue(LocalDateTime lastExecution) {
        LocalDateTime now = LocalDateTime.now();

        // Use shared date range check
        if (isWithinDateRange(now, startDate, endDate)) {
            return false;
        }

        // TODO: Use cron-utils library to check if current time matches expression
        if (lastExecution == null) {
            return true;
        }

        return Duration.between(lastExecution, now).toMinutes() >= 1;
    }
}

record WeeklySchedule(
        List<DayOfWeek> days,
        LocalTime time,
        @JsonProperty("start_date") LocalDateTime startDate,
        @JsonProperty("end_date") LocalDateTime endDate
) implements JobScheduleData {

    @Override
    public boolean isDue(LocalDateTime lastExecution) {
        LocalDateTime now = LocalDateTime.now();

        // Use shared date range check
        if (isWithinDateRange(now, startDate, endDate)) {
            return false;
        }

        // Check if today is one of the scheduled days
        if (!days.contains(now.getDayOfWeek())) {
            return false;
        }

        // Check if we're past the scheduled time today
        if (now.toLocalTime().isBefore(time)) {
            return false;
        }

        // Use shared "has run in period" check
        LocalDateTime todayAtScheduledTime = now.toLocalDate().atTime(time);
        return hasRunInPeriod(lastExecution, todayAtScheduledTime);
    }
}

record MonthlySchedule(
        Integer dayOfMonth,
        LocalTime time,
        @JsonProperty("start_date") LocalDateTime startDate,
        @JsonProperty("end_date") LocalDateTime endDate
) implements JobScheduleData {

    @Override
    public boolean isDue(LocalDateTime lastExecution) {
        LocalDateTime now = LocalDateTime.now();

        // Use shared date range check
        if (isWithinDateRange(now, startDate, endDate)) {
            return false;
        }

        // Check if today is the scheduled day
        if (now.getDayOfMonth() != dayOfMonth) {
            return false;
        }

        // Check if we're past the scheduled time
        if (now.toLocalTime().isBefore(time)) {
            return false;
        }

        // Use shared "has run in period" check
        LocalDateTime thisMonthAtScheduledTime =
                LocalDate.of(now.getYear(), now.getMonth(), dayOfMonth).atTime(time);
        return hasRunInPeriod(lastExecution, thisMonthAtScheduledTime);
    }
}

record YearlySchedule(
        Month month,
        Integer dayOfMonth,
        LocalTime time,
        @JsonProperty("start_date") LocalDateTime startDate,
        @JsonProperty("end_date") LocalDateTime endDate
) implements JobScheduleData {

    @Override
    public boolean isDue(LocalDateTime lastExecution) {
        LocalDateTime now = LocalDateTime.now();

        // Use shared date range check
        if (isWithinDateRange(now, lastExecution, endDate)) {
            return false;
        }

        // Check if today is the scheduled month and day
        if (now.getMonth() != month || now.getDayOfMonth() != dayOfMonth) {
            return false;
        }

        // Check if we're past the scheduled time
        if (now.toLocalTime().isBefore(time)) {
            return false;
        }

        // At this point, job is due unless its already run
        LocalDateTime thisYearAtScheduledTime =
                LocalDate.of(now.getYear(), month, dayOfMonth).atTime(time);
        return hasRunInPeriod(lastExecution, thisYearAtScheduledTime);
    }
}