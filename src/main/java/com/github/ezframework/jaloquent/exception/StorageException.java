package com.github.ezframework.jaloquent.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thrown when a storage or persistence error occurs.
 */
public class StorageException extends JaloquentException {

    /** Logger for recording storage errors. */
    private static final Logger log = LoggerFactory.getLogger(StorageException.class);

    /**
     * Creates a {@code StorageException} with the given message.
     *
     * @param message the detail message
     */
    public StorageException(String message) {
        super(message);
        logError(message, null);
    }

    /**
     * Creates a {@code StorageException} with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public StorageException(String message, Throwable cause) {
        super(message, cause);
        logError(message, cause);
    }

    /**
     * Creates a {@code StorageException} wrapping the given cause.
     *
     * @param cause the underlying cause
     */
    public StorageException(Throwable cause) {
        super(cause);
        logError(null, cause);
    }

    private void logError(String message, Throwable cause) {
        if (log != null) {
            if (cause != null && message != null) {
                log.error(message, cause);
            }
            else if (cause != null) {
                log.error("StorageException", cause);
            }
            else if (message != null) {
                log.error(message);
            }
        }
    }
}
