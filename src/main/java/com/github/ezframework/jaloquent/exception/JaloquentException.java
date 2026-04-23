package com.github.ezframework.jaloquent.exception;

/**
 * Base exception for all Jaloquent errors.
 */
public class JaloquentException extends Exception {

    /**
     * Creates a {@code JaloquentException} with the given message.
     *
     * @param message the detail message
     */
    public JaloquentException(String message) {
        super(message);
    }

    /**
     * Creates a {@code JaloquentException} with the given message and cause.
     *
     * @param message the detail message
     * @param cause   the underlying cause
     */
    public JaloquentException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a {@code JaloquentException} wrapping the given cause.
     *
     * @param cause the underlying cause
     */
    public JaloquentException(Throwable cause) {
        super(cause);
    }
}
