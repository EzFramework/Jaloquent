package com.github.ezframework.jaloquent.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thrown when a model is not found in the repository.
 */
public class ModelNotFoundException extends JaloquentException {

    /** Logger for recording model-not-found errors. */
    private static final Logger log = LoggerFactory.getLogger(ModelNotFoundException.class);

    /**
     * Creates a {@code ModelNotFoundException} with the given message.
     *
     * @param message the detail message
     */
    public ModelNotFoundException(String message) {
        super(message);
        logError(message, null);
    }

    /**
     * Creates a {@code ModelNotFoundException} with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public ModelNotFoundException(String message, Throwable cause) {
        super(message, cause);
        logError(message, cause);
    }

    private void logError(String message, Throwable cause) {
        if (log != null) {
            if (cause != null && message != null) {
                log.error(message, cause);
            }
            else if (cause != null) {
                log.error("ModelNotFoundException", cause);
            }
            else if (message != null) {
                log.error(message);
            }
        }
    }
}
