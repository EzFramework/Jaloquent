package com.github.ezframework.jaloquent.exception;

/**
 * Base exception for all Jaloquent errors.
 */
public class JaloquentException extends Exception {

    public JaloquentException(String message) {
        super(message);
    }

    public JaloquentException(String message, Throwable cause) {
        super(message, cause);
    }

    public JaloquentException(Throwable cause) {
        super(cause);
    }
}
