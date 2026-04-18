package com.github.ezframework.jaloquent.model;

import com.github.ezframework.jaloquent.config.JaloquentConfig;
import com.github.ezframework.jaloquent.exception.StorageException;
import com.github.ezframework.jaloquent.store.sql.TransactionalJdbcStore;
import org.slf4j.Logger;

/**
 * A database transaction handle returned by {@link ModelRepository#transaction()}.
 *
 * <p>Implements {@link AutoCloseable} so it can be used in a try-with-resources
 * block.  If the block exits without an explicit call to {@link #commit()}, the
 * transaction is automatically rolled back in {@link #close()} to prevent
 * uncommitted changes from leaking.
 *
 * <pre>{@code
 * try (Transaction tx = repo.transaction()) {
 *     repo.save(modelA);
 *     repo.save(modelB);
 *     tx.commit();
 * }
 * }</pre>
 *
 * <p>To roll back explicitly before the end of the block call {@link #rollback()}.
 * Once {@link #commit()} or {@link #rollback()} has been called, the subsequent
 * {@link #close()} is a no-op.
 */
public class Transaction implements AutoCloseable {

    /** The store whose transaction lifecycle this handle wraps. */
    private final TransactionalJdbcStore store;

    /** Tracks whether commit or rollback has already been called. */
    private boolean completed = false;

    /**
     * Open a transaction on the given store.
     *
     * <p>This constructor is package-private; callers should use
     * {@link ModelRepository#transaction()} instead.
     *
     * @param store the transactional store to begin a transaction on
     * @throws StorageException if the transaction cannot be started
     */
    Transaction(TransactionalJdbcStore store) throws StorageException {
        this.store = store;
        store.beginTransaction();
    }

    /**
     * Commit the transaction, making all changes permanent.
     *
     * @throws StorageException if the commit fails
     */
    public void commit() throws StorageException {
        store.commitTransaction();
        completed = true;
    }

    /**
     * Roll back the transaction explicitly, discarding all changes.
     *
     * @throws StorageException if the rollback fails
     */
    public void rollback() throws StorageException {
        store.rollbackTransaction();
        completed = true;
    }

    /**
     * Auto-rollback if neither {@link #commit()} nor {@link #rollback()} has been called.
     *
     * <p>Rollback failures during {@code close()} are logged and swallowed
     * rather than thrown, to avoid masking any primary exception propagating
     * out of the try-with-resources block.
     */
    @Override
    public void close() {
        if (completed) {
            return;
        }
        try {
            store.rollbackTransaction();
        }
        catch (Exception e) {
            final Logger log = JaloquentConfig.getLogger(Transaction.class);
            if (log != null) {
                log.error("Failed to auto-rollback transaction on close", e);
            }
        }
    }

}
