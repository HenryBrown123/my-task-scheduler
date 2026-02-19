package com.github.henrybrown123.configuration;

import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

import java.nio.file.Path;

/**
 * This is a bespoke Exception that wraps some common Jackson de-serialization exceptions to form more user-friendly
 * error messages. Like when a mandatory property is missing or the wrong data type is used... (pretty common)
 *
 */
public class InvalidJobConfigException extends Exception {

    public InvalidJobConfigException(String message, Throwable throwable) {
        super(message, throwable);
    }

    /**
     * Specific constructor for jackson databind exceptions to allow human-readable error messages to be output.
     * @param configPath
     * @param e
     */
    public InvalidJobConfigException(Path configPath, DatabindException e) {
        super(buildMessage(configPath, e), e);
    }

    private static String buildMessage(Path configPath, DatabindException e) {
        String detail = switch (e) {
            case UnrecognizedPropertyException u ->
                    "Unknown property '" + u.getPropertyName() + "' at " + u.getLocation();
            case InvalidFormatException f ->
                    "Invalid value '" + f.getValue() + "' at " + f.getLocation();
            case MismatchedInputException m ->
                    "Wrong type for field at " + m.getLocation() + ": " + m.getOriginalMessage();
            default ->
                    e.getOriginalMessage();
        };

        return "Invalid config in " + configPath + ": " + detail;
    }
}