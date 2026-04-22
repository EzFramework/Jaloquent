package com.github.ezframework.jaloquent.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thrown when a database migration fails.
 */
public class MigrationException extends JaloquentException {

    /** Logger for recording migration errors. */
    private static final Logger log = LoggerFactory.getLogger(MigrationException.class);

    /**
     * Constructs a {@code MigrationException} with the given message.
     *
     * @param message human-readable description of the failure
     */
    public MigrationException(final String message) {
        super(message);
        logError(message, null);
    }

    /**
     * Constructs a {@code MigrationException} with a message and a root cause.
     *
     * @param message human-readable description of the failure
     * @param cause   the underlying exception
     */
    public MigrationException(final String message, final Throwable cause) {
        super(message, cause);
        logError(message, cause);
    }

    /**
     * Constructs a {@code MigrationException} wrapping a root cause.
     *
     * @param cause the underlying exception
     */
    public MigrationException(final Throwable cause) {
        super(cause);
        logError(null, cause);
    }

    /**
     * Logs the error using SLF4J.
     *
     * @param message the error message, or {@code null}
     * @param cause   the root cause, or {@code null}
     */
    private void logError(final String message, final Throwable cause) {
        if (log != null) {
            if (cause != null && message != null) {
                log.error(message, cause);
            }
            else if (cause != null) {
                log.error("MigrationException", cause);
            }
            else if (message != null) {
                log.error(message);
            }
        }
    }
}
