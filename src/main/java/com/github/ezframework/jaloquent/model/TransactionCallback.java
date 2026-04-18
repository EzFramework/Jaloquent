package com.github.ezframework.jaloquent.model;

/**
 * Functional interface for a block of work that is executed inside a database transaction.
 *
 * <p>Pass a lambda (or method reference) to
 * {@link ModelRepository#transaction(TransactionCallback)} to run multiple
 * repository operations atomically:
 *
 * <pre>{@code
 * repo.transaction(() -> {
 *     repo.save(orderModel);
 *     repo.save(inventoryModel);
 * });
 * }</pre>
 *
 * <p>If {@code execute()} throws any exception the outer method automatically
 * rolls back the transaction before propagating the error.
 */
@FunctionalInterface
public interface TransactionCallback {

    /**
     * Execute the transactional work.
     *
     * @throws Exception if any step inside the transaction fails
     */
    void execute() throws Exception;

}
