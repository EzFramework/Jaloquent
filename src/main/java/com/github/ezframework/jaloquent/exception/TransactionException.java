package com.github.ezframework.jaloquent.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thrown when a database transaction operation fails (begin, commit, or rollback).
 *
 * <p>This exception is a specialisation of {@link StorageException} and is raised
 * by {@link com.github.ezframework.jaloquent.store.sql.TransactionalJdbcStore}
 * implementations and by
 * {@link com.github.ezframework.jaloquent.model.Transaction}.
 */
public class TransactionException extends StorageException {

    /** Logger for recording transaction errors. */
    private static final Logger LOG = LoggerFactory.getLogger(TransactionException.class);

    /**
     * Construct a transaction exception with a descriptive message.
     *
     * @param message description of the transaction failure
     */
    public TransactionException(String message) {
        super(message);
        logError(message, null);
    }

    /**
     * Construct a transaction exception with a message and the underlying cause.
     *
     * @param message description of the transaction failure
     * @param cause   root cause
     */
    public TransactionException(String message, Throwable cause) {
        super(message, cause);
        logError(message, cause);
    }

    private void logError(String message, Throwable cause) {
        if (cause != null && message != null) {
            LOG.error(message, cause);
        }
        else if (cause != null) {
            LOG.error("TransactionException", cause);
        }
        else if (message != null) {
            LOG.error(message);
        }
    }

}
