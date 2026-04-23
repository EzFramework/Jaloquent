package com.github.ezframework.jaloquent.feature;

import com.github.ezframework.jaloquent.exception.StorageException;
import com.github.ezframework.jaloquent.model.Model;
import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.jaloquent.model.Transaction;
import com.github.ezframework.jaloquent.store.DataStore;
import com.github.ezframework.jaloquent.store.sql.JdbcStore;
import com.github.ezframework.jaloquent.store.sql.TransactionalJdbcStore;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feature tests for database transaction support in {@link ModelRepository}.
 *
 * <p>Uses a {@link RecordingTransactionalJdbcStore} fixture to verify that
 * {@link ModelRepository#transaction()} and
 * {@link ModelRepository#transaction(com.github.ezframework.jaloquent.model.TransactionCallback)}
 * delegate correctly to the store's lifecycle methods.
 */
public class TransactionFeatureTest {

    // =========================================================================
    // Fixture — simple model
    // =========================================================================

    static class SampleModel extends Model {

        /**
         * @param id model identifier
         */
        SampleModel(String id) {
            super(id);
        }

    }

    // =========================================================================
    // Fixture — recording store that supports transactions
    // =========================================================================

    /**
     * {@link JdbcStore} + {@link TransactionalJdbcStore} + {@link DataStore}
     * implementation that records every lifecycle call so tests can verify
     * the exact call sequence.
     */
    static class RecordingTransactionalJdbcStore implements DataStore, TransactionalJdbcStore {

        /** Number of times {@link #beginTransaction()} was called. */
        int beginCount = 0;

        /** Number of times {@link #commitTransaction()} was called. */
        int commitCount = 0;

        /** Number of times {@link #rollbackTransaction()} was called. */
        int rollbackCount = 0;

        /** Pre-staged rows returned by the next {@link #query} call. */
        List<Map<String, Object>> nextQueryRows = new ArrayList<>();

        // ----- TransactionalJdbcStore -----------------------------------------

        @Override
        public void beginTransaction() throws StorageException {
            beginCount++;
        }

        @Override
        public void commitTransaction() throws StorageException {
            commitCount++;
        }

        @Override
        public void rollbackTransaction() throws StorageException {
            rollbackCount++;
        }

        // ----- JdbcStore ------------------------------------------------------

        @Override
        public List<Map<String, Object>> query(String sql, List<Object> params) throws Exception {
            return new ArrayList<>(nextQueryRows);
        }

        @Override
        public int executeUpdate(String sql, List<Object> params) throws Exception {
            return 1;
        }

        // ----- DataStore (should not be called on SQL path) -------------------

        @Override
        public void save(String path, Map<String, Object> data) { }

        @Override
        public Optional<Map<String, Object>> load(String path) {
            return Optional.empty();
        }

        @Override
        public void delete(String path) { }

        @Override
        public boolean exists(String path) {
            return false;
        }

    }

    /**
     * Plain {@link DataStore} with no transaction support — used for
     * negative-path tests.
     */
    static class InMemoryStore implements DataStore {

        /** Backing map keyed by path. */
        private final Map<String, Map<String, Object>> map = new HashMap<>();

        @Override
        public void save(String path, Map<String, Object> data) {
            map.put(path, new HashMap<>(data));
        }

        @Override
        public Optional<Map<String, Object>> load(String path) {
            return Optional.ofNullable(map.get(path));
        }

        @Override
        public void delete(String path) {
            map.remove(path);
        }

        @Override
        public boolean exists(String path) {
            return map.containsKey(path);
        }

    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String uniquePrefix() {
        return "tx-" + UUID.randomUUID();
    }

    private static ModelRepository<SampleModel> repo(RecordingTransactionalJdbcStore store) {
        return new ModelRepository<>(store, uniquePrefix(), (id, data) -> new SampleModel(id));
    }

    private static ModelRepository<SampleModel> nonTxRepo() {
        return new ModelRepository<>(new InMemoryStore(), uniquePrefix(), (id, data) -> new SampleModel(id));
    }

    // =========================================================================
    // transaction() — handle API
    // =========================================================================

    @Test
    void transactionBeginIsCalledOnTransactionOpen() throws Exception {
        final RecordingTransactionalJdbcStore store = new RecordingTransactionalJdbcStore();
        final Transaction tx = repo(store).transaction();
        tx.close();

        assertEquals(1, store.beginCount, "beginTransaction must be called exactly once on open");
    }

    @Test
    void transactionCommitIsCalledOnCommit() throws Exception {
        final RecordingTransactionalJdbcStore store = new RecordingTransactionalJdbcStore();
        try (final Transaction tx = repo(store).transaction()) {
            tx.commit();
        }

        assertEquals(1, store.commitCount, "commitTransaction must be called exactly once");
        assertEquals(0, store.rollbackCount, "rollback must not be called after commit");
    }

    @Test
    void transactionRollbackIsCalledOnExplicitRollback() throws Exception {
        final RecordingTransactionalJdbcStore store = new RecordingTransactionalJdbcStore();
        try (final Transaction tx = repo(store).transaction()) {
            tx.rollback();
        }

        assertEquals(1, store.rollbackCount, "rollbackTransaction must be called exactly once");
        assertEquals(0, store.commitCount, "commit must not be called after explicit rollback");
    }

    @Test
    void transactionAutoRollsBackOnCloseWithoutCommit() throws Exception {
        final RecordingTransactionalJdbcStore store = new RecordingTransactionalJdbcStore();
        try (final Transaction tx = repo(store).transaction()) {
            // close without commit — auto-rollback expected
        }

        assertEquals(1, store.rollbackCount, "auto-rollback must be called when block exits without commit");
        assertEquals(0, store.commitCount, "commit must not be called");
    }

    @Test
    void committedTransactionDoesNotRollbackOnClose() throws Exception {
        final RecordingTransactionalJdbcStore store = new RecordingTransactionalJdbcStore();
        try (final Transaction tx = repo(store).transaction()) {
            tx.commit();
        }

        assertEquals(1, store.commitCount);
        assertEquals(0, store.rollbackCount, "close after commit must not call rollback");
    }

    // =========================================================================
    // transaction() — availability guard
    // =========================================================================

    @Test
    void transactionThrowsStorageExceptionOnNonTransactionalStore() {
        assertThrows(StorageException.class,
            () -> nonTxRepo().transaction(),
            "transaction() must throw StorageException when the store is not TransactionalJdbcStore");
    }

    // =========================================================================
    // transaction(TransactionCallback) — lambda API
    // =========================================================================

    @Test
    void transactionCallbackCommitsOnSuccess() throws Exception {
        final RecordingTransactionalJdbcStore store = new RecordingTransactionalJdbcStore();
        repo(store).transaction(() -> { });

        assertEquals(1, store.beginCount,    "beginTransaction must be called");
        assertEquals(1, store.commitCount,   "commitTransaction must be called on success");
        assertEquals(0, store.rollbackCount, "rollback must not be called on success");
    }

    @Test
    void transactionCallbackRollsBackOnException() {
        final RecordingTransactionalJdbcStore store = new RecordingTransactionalJdbcStore();

        assertThrows(StorageException.class,
            () -> repo(store).transaction(() -> {
                throw new RuntimeException("callback error");
            }),
            "transaction(callback) must propagate the error as StorageException");

        assertEquals(1, store.beginCount,    "beginTransaction must be called");
        assertEquals(0, store.commitCount,   "commit must not be called on error");
        assertTrue(store.rollbackCount >= 1, "rollback must be called on error");
    }

    @Test
    void transactionCallbackPropagatesStorageExceptionDirectly() {
        final RecordingTransactionalJdbcStore store = new RecordingTransactionalJdbcStore();
        final StorageException original = new StorageException("direct storage error");

        final StorageException thrown = assertThrows(StorageException.class,
            () -> repo(store).transaction(() -> {
                throw original;
            }));

        assertEquals(original, thrown, "StorageException thrown by callback must not be wrapped");
    }

    // =========================================================================
    // close() — rollback exception swallowing
    // =========================================================================

    @Test
    void closeSwallowsRollbackException() throws Exception {
        final ThrowingRollbackStore store = new ThrowingRollbackStore();
        final Transaction tx = repo(store).transaction();
        // close() must NOT propagate the StorageException thrown by rollbackTransaction
        tx.close();
    }

    // =========================================================================
    // Fixture — store whose rollbackTransaction always throws
    // =========================================================================

    /**
     * {@link TransactionalJdbcStore} + {@link DataStore} variant whose
     * {@link #rollbackTransaction()} always throws, used to exercise the
     * exception-swallowing path in {@link Transaction#close()}.
     */
    static class ThrowingRollbackStore extends RecordingTransactionalJdbcStore {

        @Override
        public void rollbackTransaction() throws StorageException {
            throw new StorageException("simulated rollback failure");
        }

    }

}
