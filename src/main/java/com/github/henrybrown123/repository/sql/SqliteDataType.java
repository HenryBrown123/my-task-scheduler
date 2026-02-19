package com.github.henrybrown123.repository.sql;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.sql.Timestamp;

/**
 * Null-safe type conversions between Java types and SQLite text columns.
 *
 * <p>SQLite has no real type system — DATE, TIMESTAMP, BOOLEAN are all
 * stored as TEXT. This class makes that conversion explicit and consistent
 * across all repositories.
 *
 */
public final class SqliteDataType {

    private SqliteDataType() {}

    /**
     * Converts a {@link LocalDate} to an ISO-8601 date string for storage.
     *
     * @param date the date, or null
     * @return ISO-8601 string (e.g. "2025-01-15"), or null
     */
    public static String text(LocalDate date) {
        return date != null ? date.toString() : null;
    }

    /**
     * Converts a {@link LocalDateTime} to an ISO-8601 datetime string for storage.
     *
     * @param dateTime the datetime, or null
     * @return ISO-8601 string, or null
     */
    public static String text(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.toString() : null;
    }

    /**
     * Converts a {@link Boolean} to a string for storage.
     *
     * @param bool the boolean, or null
     * @return "true" or "false", or null
     */
    public static String text(Boolean bool) {
        return bool != null ? bool.toString() : null;
    }

    /**
     * Converts an {@link Enum} to its name string for storage.
     *
     * @param value the enum value, or null
     * @return the enum name, or null
     */
    public static String text(Enum<?> value) {
        return value != null ? value.name() : null;
    }

    /**
     * Parses an ISO-8601 date string from SQLite into a {@link LocalDate}.
     *
     * @param text the stored string, or null
     * @return parsed date, or null
     * @throws TypeException if the string is not a valid date
     */
    public static LocalDate toLocalDate(String text) {
        if (text == null) return null;
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new TypeException("Invalid date: '" + text + "'", e);
        }
    }

    /**
     * Parses an SQL timestamp from jdbc driver into a {@link LocalDateTime}.
     *
     * @param ts the stored string, or null
     * @return parsed date, or null
     * @throws TypeException if the string is not a valid date
     */
    public static LocalDateTime toLocalDateTime(Timestamp ts) {
        return ts != null ? ts.toLocalDateTime() : null;
    }

    /**
     * Parses an ISO-8601 datetime string from SQLite into a {@link LocalDateTime}.
     *
     * @param text the stored string, or null
     * @return parsed datetime, or null
     * @throws TypeException if the string is not a valid datetime
     */
    public static LocalDateTime toLocalDateTime(String text) {
        if (text == null) return null;
        try {
            return LocalDateTime.parse(text);
        } catch (DateTimeParseException e) {
            throw new TypeException("Invalid datetime: '" + text + "'", e);
        }
    }

    /**
     * Parses a boolean string from SQLite into a {@link Boolean}.
     *
     * @param text the stored string, or null
     * @return parsed boolean, or null
     */
    public static Boolean toBoolean(String text) {
        return text != null ? Boolean.parseBoolean(text) : null;
    }

    /**
     * Thrown when a value stored in SQLite cannot be parsed into
     * the expected Java type.
     */
    public static final class TypeException extends RuntimeException {
        TypeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}