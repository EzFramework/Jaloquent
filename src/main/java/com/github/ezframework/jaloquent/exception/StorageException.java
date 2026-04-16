package com.github.ezframework.jaloquent.exception;

/**
 * Thrown when a storage or persistence error occurs.
 */
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StorageException extends JaloquentException {

    /** Logger for recording storage errors. */
    private static final Logger log = LoggerFactory.getLogger(StorageException.class);

    public StorageException(String message) {
        super(message);
        logError(message, null);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
        logError(message, cause);
    }

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
