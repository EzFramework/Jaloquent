package com.github.ezframework.jaloquent.store.sql;

import com.github.ezframework.jaloquent.exception.StorageException;

/**
 * Extension of {@link JdbcStore} for stores that support explicit database transactions.
 *
 * <p>Implementations manage the connection-level transaction state. Once
 * {@link #beginTransaction()} is called, subsequent {@link #query(java.util.List, java.util.List)}
 * and {@link #executeUpdate(String, java.util.List)} calls on the same store instance
 * participate in the open transaction until {@link #commitTransaction()} or
 * {@link #rollbackTransaction()} is invoked.
 *
 * <p>The typical usage pattern via {@link com.github.ezframework.jaloquent.model.Transaction}
 * is try-with-resources, which auto-rolls-back if the block exits without a commit:
 *
 * <pre>{@code
 * try (Transaction tx = repo.transaction()) {
 *     repo.save(modelA);
 *     repo.save(modelB);
 *     tx.commit();
 * }
 * }</pre>
 */
public interface TransactionalJdbcStore extends JdbcStore {

    /**
     * Begin a new database transaction.
     *
     * <p>Typically disables auto-commit on the underlying connection and
     * marks the store as being in an active transaction.
     *
     * @throws StorageException if the transaction cannot be started
     */
    void beginTransaction() throws StorageException;

    /**
     * Commit the current transaction, making all changes permanent.
     *
     * @throws StorageException if the commit fails or no transaction is active
     */
    void commitTransaction() throws StorageException;

    /**
     * Roll back the current transaction, discarding all changes since
     * {@link #beginTransaction()} was called.
     *
     * @throws StorageException if the rollback fails or no transaction is active
     */
    void rollbackTransaction() throws StorageException;

}
