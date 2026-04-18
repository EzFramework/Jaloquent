package com.github.ezframework.jaloquent.feature;

import com.github.ezframework.jaloquent.exception.StorageException;
import com.github.ezframework.jaloquent.model.Model;
import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.jaloquent.model.TableRegistry;
import com.github.ezframework.jaloquent.model.Transaction;
import com.github.ezframework.jaloquent.store.sql.DataSourceJdbcStore;
import com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder;
import com.github.ezframework.javaquerybuilder.query.sql.SqlDialect;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for {@link DataSourceJdbcStore} backed by an H2 in-memory database.
 *
 * <p>Each test registers its own UUID-based table and prefix to avoid cross-test
 * contamination with the JVM-static {@link TableRegistry}.
 */
public class DataSourceJdbcStoreFeatureTest {

    // =========================================================================
    // Fixtures
    // =========================================================================

    static class TestModel extends Model {

        /**
         * @param id model identifier
         */
        TestModel(String id) {
            super(id);
        }

    }

    // =========================================================================
    // Per-test state
    // =========================================================================

    /** H2 in-memory data source shared across all test methods in this class. */
    private JdbcDataSource ds;

    /** Unique table prefix registered with {@link TableRegistry} per test. */
    private String prefix;

    /** Unique table name created in H2 per test. */
    private String tableName;

    /** Store under test. */
    private DataSourceJdbcStore store;

    /** Repository wired to {store}. */
    private ModelRepository<TestModel> repo;

    // =========================================================================
    // Setup / teardown
    // =========================================================================

    /**
     * Creates an H2 data source, a uniquely-named table, and a
     * {@link ModelRepository} backed by a fresh {@link DataSourceJdbcStore}.
     *
     * @throws Exception if DDL or TableRegistry setup fails
     */
    @BeforeEach
    void setUp() throws Exception {
        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:jaloquent_ds_test;DB_CLOSE_DELAY=-1;MODE=MySQL");
        ds.setUser("sa");
        ds.setPassword("");

        final String uid = UUID.randomUUID().toString().replace("-", "");
        prefix    = "ds_" + uid;
        tableName = "t_"  + uid;

        final Map<String, String> cols = new LinkedHashMap<>();
        cols.put("id",   "VARCHAR(36)");
        cols.put("name", "VARCHAR(255)");
        TableRegistry.register(prefix, tableName, cols);

        try (Connection conn = ds.getConnection();
             Statement  stmt = conn.createStatement()) {
            stmt.execute(
                "CREATE TABLE " + tableName
                + " (id VARCHAR(36) PRIMARY KEY, name VARCHAR(255))"
            );
        }

        store = new DataSourceJdbcStore(ds);
        repo  = new ModelRepository<>(store, prefix,
            (id, data) -> {
                TestModel m = new TestModel(id);
                m.fromMap(data);
                return m;
            },
            SqlDialect.MYSQL);
    }

    /**
     * Drops the per-test table.
     *
     * @throws Exception if DDL fails
     */
    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = ds.getConnection();
             Statement  stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS " + tableName);
        }
    }

    // =========================================================================
    // Helper factories
    // =========================================================================

    private static TestModel model(String id, String name) {
        final TestModel m = new TestModel(id);
        m.set("name", name);
        return m;
    }

    // =========================================================================
    // save() + find() round-trip
    // =========================================================================

    @Test
    void saveAndFindRoundTrip() throws Exception {
        repo.save(model("u1", "Alice"));
        final Optional<TestModel> found = repo.find("u1");
        assertTrue(found.isPresent());
        assertEquals("Alice", found.get().get("name"));
    }

    @Test
    void findReturnsEmptyWhenNotPresent() throws Exception {
        assertFalse(repo.find("nobody").isPresent());
    }

    @Test
    void saveOverwritesExistingRecord() throws Exception {
        repo.save(model("u2", "Before"));
        repo.save(model("u2", "After"));
        assertEquals("After", repo.find("u2").orElseThrow().get("name"));
    }

    // =========================================================================
    // delete()
    // =========================================================================

    @Test
    void deleteRemovesRecord() throws Exception {
        repo.save(model("u3", "Charlie"));
        repo.delete("u3");
        assertFalse(repo.find("u3").isPresent());
    }

    @Test
    void deleteNonExistentRecordDoesNotThrow() {
        assertDoesNotThrow(() -> repo.delete("ghost"));
    }

    // =========================================================================
    // exists()
    // =========================================================================

    @Test
    void existsReturnsTrueAfterSave() throws Exception {
        repo.save(model("u4", "Diana"));
        assertTrue(repo.exists("u4"));
    }

    @Test
    void existsReturnsFalseForUnknownId() throws Exception {
        assertFalse(repo.exists("unknown"));
    }

    @Test
    void existsReturnsFalseAfterDelete() throws Exception {
        repo.save(model("u5", "Eve"));
        repo.delete("u5");
        assertFalse(repo.exists("u5"));
    }

    // =========================================================================
    // query()
    // =========================================================================

    @Test
    void queryWithEqConditionReturnsMatchingModels() throws Exception {
        repo.save(model("q1", "Alice"));
        repo.save(model("q2", "Bob"));
        repo.save(model("q3", "Alice"));

        final List<TestModel> result = repo.query(
            new QueryBuilder().whereEquals("name", "Alice").build()
        );

        assertEquals(2, result.size());
        assertTrue(result.stream().allMatch(m -> "Alice".equals(m.get("name"))));
    }

    @Test
    void queryWithNoConditionsReturnsAllModels() throws Exception {
        repo.save(model("qa1", "X"));
        repo.save(model("qa2", "Y"));

        final List<TestModel> all = repo.query(Model.queryBuilder().build());
        assertEquals(2, all.size());
    }

    @Test
    void queryReturnsEmptyWhenNoRowsMatch() throws Exception {
        repo.save(model("qe1", "Existing"));
        final List<TestModel> result = repo.query(
            new QueryBuilder().whereEquals("name", "NoSuchName").build()
        );
        assertTrue(result.isEmpty());
    }

    // =========================================================================
    // deleteAll()
    // =========================================================================

    @Test
    void deleteAllRemovesAllListedIds() throws Exception {
        repo.save(model("da1", "A"));
        repo.save(model("da2", "B"));
        repo.save(model("da3", "C"));

        repo.deleteAll(List.of("da1", "da2"));

        assertFalse(repo.exists("da1"));
        assertFalse(repo.exists("da2"));
        assertTrue(repo.exists("da3"));
    }

    @Test
    void deleteAllWithEmptyListIsNoOp() throws Exception {
        repo.save(model("da4", "D"));
        repo.deleteAll(List.of());
        assertTrue(repo.exists("da4"));
    }

    // =========================================================================
    // deleteWhere(Query)
    // =========================================================================

    @Test
    void deleteWhereQueryRemovesMatchingRecords() throws Exception {
        repo.save(model("dw1", "Remove"));
        repo.save(model("dw2", "Remove"));
        repo.save(model("dw3", "Keep"));

        repo.deleteWhere(new QueryBuilder().whereEquals("name", "Remove").build());

        assertFalse(repo.exists("dw1"));
        assertFalse(repo.exists("dw2"));
        assertTrue(repo.exists("dw3"));
    }

    // =========================================================================
    // transaction() — commit path
    // =========================================================================

    @Test
    void transactionCommitPersistsChanges() throws Exception {
        try (Transaction tx = repo.transaction()) {
            repo.save(model("tx1", "TxAlice"));
            tx.commit();
        }
        assertTrue(repo.exists("tx1"));
        assertEquals("TxAlice", repo.find("tx1").orElseThrow().get("name"));
    }

    // =========================================================================
    // transaction(callback) — auto-rollback path
    // =========================================================================

    @Test
    void transactionCallbackAutoRollbackOnException() throws Exception {
        assertThrows(StorageException.class, () ->
            repo.transaction(() -> {
                repo.save(model("rb1", "WillRollback"));
                throw new RuntimeException("simulated failure");
            })
        );
        assertFalse(repo.exists("rb1"));
    }

    @Test
    void transactionCallbackCommitsOnSuccess() throws Exception {
        repo.transaction(() -> repo.save(model("cb1", "Committed")));
        assertTrue(repo.exists("cb1"));
    }

    // =========================================================================
    // DataStore flat-map methods throw StorageException
    // =========================================================================

    @Test
    void dataStoreSaveThrowsStorageException() {
        assertThrows(StorageException.class,
            () -> store.save("any/path", Map.of("key", "val")));
    }

    @Test
    void dataStoreLoadThrowsStorageException() {
        assertThrows(StorageException.class, () -> store.load("any/path"));
    }

    @Test
    void dataStoreDeleteThrowsStorageException() {
        assertThrows(StorageException.class, () -> store.delete("any/path"));
    }

    @Test
    void dataStoreExistsThrowsStorageException() {
        assertThrows(StorageException.class, () -> store.exists("any/path"));
    }
}
