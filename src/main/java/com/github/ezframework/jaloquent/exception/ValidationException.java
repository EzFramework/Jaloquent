package com.github.ezframework.jaloquent.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thrown when a model or input fails validation.
 */
public class ValidationException extends JaloquentException {
    /** Logger for this exception class. */
    private static final Logger log = LoggerFactory.getLogger(ValidationException.class);

    /**
     * Creates a {@code ValidationException} with the given message.
     *
     * @param message the detail message
     */
    public ValidationException(String message) {
        super(message);
        logError(message, null);
    }

    /**
     * Creates a {@code ValidationException} with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
        logError(message, cause);
    }

    private void logError(String message, Throwable cause) {
        if (log != null) {
            if (cause != null && message != null) {
                log.error(message, cause);
            }
            else if (cause != null) {
                log.error("ValidationException", cause);
            }
            else if (message != null) {
                log.error(message);
            }
        }
    }
}
