package com.github.ezframework.jaloquent.feature;

import com.github.ezframework.jaloquent.exception.StorageException;
import com.github.ezframework.jaloquent.model.Model;
import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.jaloquent.model.TableRegistry;
import com.github.ezframework.jaloquent.store.DataStore;
import com.github.ezframework.jaloquent.store.sql.JdbcStore;
import com.github.ezframework.javaquerybuilder.query.Query;
import com.github.ezframework.javaquerybuilder.query.QueryableStorage;
import com.github.ezframework.javaquerybuilder.query.sql.SqlDialect;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Feature tests for the SQL/JDBC path in {@link ModelRepository}.
 *
 * <p>Each test registers its own UUID-based prefix to prevent cross-test
 * contamination with the JVM-static {@link TableRegistry}.
 */
public class ModelRepositoryJdbcFeatureTest {

    // =========================================================================
    // Fixtures
    // =========================================================================

    static class TestModel extends Model {
        TestModel(String id) { super(id); }
    }

    /**
     * Implements both DataStore and JdbcStore so that ModelRepository
     * chooses the SQL path whenever a TableRegistry entry exists.
     */
    static class RecordingJdbcStore implements DataStore, JdbcStore {

        final List<String>         updateSqls   = new ArrayList<>();
        final List<List<Object>>   updateParams = new ArrayList<>();
        final List<String>         querySqls    = new ArrayList<>();
        final List<List<Object>>   queryParams  = new ArrayList<>();
        List<Map<String, Object>>  nextQueryRows = new ArrayList<>();

        boolean dataStoreSaveCalled   = false;
        boolean dataStoreLoadCalled   = false;
        boolean dataStoreDeleteCalled = false;
        boolean dataStoreExistsCalled = false;

        // DataStore — must not be invoked on the SQL path
        @Override public void save(String path, Map<String, Object> data)   { dataStoreSaveCalled   = true; }
        @Override public Optional<Map<String, Object>> load(String path)    { dataStoreLoadCalled   = true; return Optional.empty(); }
        @Override public void delete(String path)                            { dataStoreDeleteCalled = true; }
        @Override public boolean exists(String path)                         { dataStoreExistsCalled = true; return false; }

        // JdbcStore
        @Override
        public List<Map<String, Object>> query(String sql, List<Object> params) throws Exception {
            querySqls.add(sql);
            queryParams.add(new ArrayList<>(params));
            return new ArrayList<>(nextQueryRows);
        }

        @Override
        public int executeUpdate(String sql, List<Object> params) throws Exception {
            updateSqls.add(sql);
            updateParams.add(new ArrayList<>(params));
            return 1;
        }
    }

    /** Plain DataStore with no JdbcStore support — used for fallback tests. */
    static class InMemoryStore implements DataStore {
        private final Map<String, Map<String, Object>> map = new HashMap<>();

        @Override public void save(String path, Map<String, Object> data) { map.put(path, new HashMap<>(data)); }
        @Override public Optional<Map<String, Object>> load(String path)  { return Optional.ofNullable(map.get(path)); }
        @Override public void delete(String path)                          { map.remove(path); }
        @Override public boolean exists(String path)                       { return map.containsKey(path); }
    }

    /**
     * {@link RecordingJdbcStore} variant whose {@link #executeUpdate} always throws
     * — used to drive the exception-handling paths in repository methods.
     */
    static class ThrowingJdbcStore extends RecordingJdbcStore {

        /** The exception thrown from every {@link #executeUpdate} call. */
        private final RuntimeException toThrow;

        /**
         * @param ex exception to throw from every {@link #executeUpdate} call
         */
        ThrowingJdbcStore(RuntimeException ex) {
            this.toThrow = ex;
        }

        @Override
        public int executeUpdate(String sql, List<Object> params) throws Exception {
            throw toThrow;
        }
    }

    /**
     * {@link DataStore} that also implements {@link QueryableStorage} — used to exercise
     * the flat-map path in {@code deleteWhere(Query)}.
     *
     * <p>Returns a pre-configured list of IDs from {@link #query(Query)}.
     */
    static class QueryableFlatStore implements DataStore, QueryableStorage {

        /** Backing storage map keyed by path. */
        private final Map<String, Map<String, Object>> map = new HashMap<>();

        /** IDs returned by {@link #query(Query)}, configured per test. */
        private List<String> resultIds = new ArrayList<>();

        /**
         * Pre-populate a record at {@code path} with {@code data}.
         *
         * @param path storage path
         * @param data record attributes
         */
        void putRecord(String path, Map<String, Object> data) {
            map.put(path, new HashMap<>(data));
        }

        /**
         * Set the IDs that {@link #query(Query)} will return.
         *
         * @param ids model IDs to return
         */
        void setQueryResultIds(List<String> ids) {
            resultIds = new ArrayList<>(ids);
        }

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

        @Override
        public List<String> query(Query q) {
            return new ArrayList<>(resultIds);
        }
    }

    // =========================================================================
    // Shared helpers
    // =========================================================================

    /** Each call returns a unique prefix, keeping tests isolated from the JVM-static TableRegistry. */
    private static String uniquePrefix() {
        return "jdbc-" + UUID.randomUUID();
    }

    private static ModelRepository<TestModel> sqlRepo(RecordingJdbcStore store, String prefix) {
        return new ModelRepository<>(store, prefix, (id, data) -> new TestModel(id));
    }

    private static TestModel model(String id, String name) {
        TestModel m = new TestModel(id);
        m.set("name", name);
        return m;
    }

    // =========================================================================
    // save() — SQL path
    // =========================================================================

    @Test
    void saveSqlPathExecutesUpdateNotDataStore() throws Exception {
        String prefix = uniquePrefix();
        Map<String, String> cols = new LinkedHashMap<>();
        cols.put("id",   "VARCHAR(36)");
        cols.put("name", "VARCHAR(255)");
        TableRegistry.register(prefix, "users", cols);

        RecordingJdbcStore store = new RecordingJdbcStore();
        sqlRepo(store, prefix).save(model("u1", "Alice"));

        assertEquals(1, store.updateSqls.size());
        assertFalse(store.dataStoreSaveCalled);
    }

    @Test
    void saveSqlPathContainsRegisteredTableName() throws Exception {
        String prefix = uniquePrefix();
        Map<String, String> cols = new LinkedHashMap<>();
        cols.put("id",   "VARCHAR(36)");
        cols.put("name", "VARCHAR(255)");
        TableRegistry.register(prefix, "ez_users", cols);

        RecordingJdbcStore store = new RecordingJdbcStore();
        sqlRepo(store, prefix).save(model("u2", "Bob"));

        assertTrue(store.updateSqls.get(0).contains("ez_users"));
    }

    @Test
    void saveSqlPathFirstParamIsModelId() throws Exception {
        String prefix = uniquePrefix();
        Map<String, String> cols = new LinkedHashMap<>();
        cols.put("id",   "VARCHAR(36)");
        cols.put("name", "VARCHAR(255)");
        TableRegistry.register(prefix, "users", cols);

        RecordingJdbcStore store = new RecordingJdbcStore();
        sqlRepo(store, prefix).save(model("id-42", "Charlie"));

        assertEquals("id-42", store.updateParams.get(0).get(0));
    }

    @Test
    void saveSqlPathIncludesColumnValueInParams() throws Exception {
        String prefix = uniquePrefix();
        Map<String, String> cols = new LinkedHashMap<>();
        cols.put("id",   "VARCHAR(36)");
        cols.put("name", "VARCHAR(255)");
        TableRegistry.register(prefix, "users", cols);

        RecordingJdbcStore store = new RecordingJdbcStore();
        sqlRepo(store, prefix).save(model("u3", "Diana"));

        assertTrue(store.updateParams.get(0).contains("Diana"));
    }

    @Test
    void saveSqlPathIdOnlyTableUsesFallbackOnDuplicate() throws Exception {
        String prefix = uniquePrefix();
        TableRegistry.register(prefix, "tokens", Map.of("id", "VARCHAR(36)"));

        RecordingJdbcStore store = new RecordingJdbcStore();
        sqlRepo(store, prefix).save(model("tok1", "ignored"));

        String sql = store.updateSqls.get(0);
        assertTrue(sql.contains("id=id"), "Expected 'id=id' fallback but got: " + sql);
    }

    @Test
    void saveSqlPathSqlContainsInsertKeyword() throws Exception {
        String prefix = uniquePrefix();
        Map<String, String> cols = new LinkedHashMap<>();
        cols.put("id",   "VARCHAR(36)");
        cols.put("name", "VARCHAR(255)");
        TableRegistry.register(prefix, "users", cols);

        RecordingJdbcStore store = new RecordingJdbcStore();
        sqlRepo(store, prefix).save(model("u4", "Eve"));

        String sql = store.updateSqls.get(0).toUpperCase();
        assertTrue(sql.contains("INSERT"));
    }

    // =========================================================================
    // find() — SQL path
    // =========================================================================

    @Test
    void findSqlPathReturnsEmptyWhenNoRowsReturned() throws Exception {
        String prefix = uniquePrefix();
        TableRegistry.register(prefix, "users", Map.of("id", "VARCHAR(36)", "name", "VARCHAR(255)"));

        RecordingJdbcStore store = new RecordingJdbcStore(); // nextQueryRows empty by default
        Optional<TestModel> result = sqlRepo(store, prefix).find("missing-id");

        assertFalse(result.isPresent());
        assertFalse(store.dataStoreLoadCalled);
    }

    @Test
    void findSqlPathReturnsPresentWhenRowReturned() throws Exception {
        String prefix = uniquePrefix();
        TableRegistry.register(prefix, "users", Map.of("id", "VARCHAR(36)", "name", "VARCHAR(255)"));

        RecordingJdbcStore store = new RecordingJdbcStore();
        store.nextQueryRows.add(Map.of("id", "u10", "name", "Frank"));

        Optional<TestModel> result = sqlRepo(store, prefix).find("u10");

        assertTrue(result.isPresent());
        assertEquals("u10", result.get().getId());
    }

    @Test
    void findSqlPathPopulatesAttributesFromRow() throws Exception {
        String prefix = uniquePrefix();
        TableRegistry.register(prefix, "users", Map.of("id", "VARCHAR(36)", "name", "VARCHAR(255)"));

        RecordingJdbcStore store = new RecordingJdbcStore();
        store.nextQueryRows.add(Map.of("id", "u11", "name", "Greta"));

        TestModel m = sqlRepo(store, prefix).find("u11").orElseThrow();
        assertEquals("Greta", m.get("name"));
    }

    @Test
    void findSqlPathPassesIdAsQueryParam() throws Exception {
        String prefix = uniquePrefix();
        TableRegistry.register(prefix, "users", Map.of("id", "VARCHAR(36)"));

        RecordingJdbcStore store = new RecordingJdbcStore();
        sqlRepo(store, prefix).find("target-id");

        assertFalse(store.querySqls.isEmpty());
        assertEquals(List.of("target-id"), store.queryParams.get(0));
    }

    @Test
    void findSqlPathExecutesSelectQuery() throws Exception {
        String prefix = uniquePrefix();
        TableRegistry.register(prefix, "users", Map.of("id", "VARCHAR(36)"));

        RecordingJdbcStore store = new RecordingJdbcStore();
        sqlRepo(store, prefix).find("any-id");

        String sql = store.querySqls.get(0).toUpperCase();
        assertTrue(sql.contains("SELECT") && sql.contains("WHERE"));
    }

    // =========================================================================
    // delete() — SQL path
    // =========================================================================

    @Test
    void deleteSqlPathExecutesDeleteNotDataStore() throws Exception {
        String prefix = uniquePrefix();
        TableRegistry.register(prefix, "users", Map.of("id", "VARCHAR(36)"));

        RecordingJdbcStore store = new RecordingJdbcStore();
        sqlRepo(store, prefix).delete("del-1");

        assertEquals(1, store.updateSqls.size());
        assertTrue(store.updateSqls.get(0).toUpperCase().contains("DELETE"));
        assertFalse(store.dataStoreDeleteCalled);
    }

    @Test
    void deleteSqlPathPassesIdAsParam() throws Exception {
        String prefix = uniquePrefix();
        TableRegistry.register(prefix, "users", Map.of("id", "VARCHAR(36)"));

        RecordingJdbcStore store = new RecordingJdbcStore();
        sqlRepo(store, prefix).delete("del-42");

        assertEquals(List.of("del-42"), store.updateParams.get(0));
    }

    // =========================================================================
    // exists() — SQL path
    // =========================================================================

    @Test
    void existsSqlPathReturnsFalseWhenNoRows() throws Exception {
        String prefix = uniquePrefix();
        TableRegistry.register(prefix, "users", Map.of("id", "VARCHAR(36)"));

        RecordingJdbcStore store = new RecordingJdbcStore();
        assertFalse(sqlRepo(store, prefix).exists("ghost"));
        assertFalse(store.dataStoreExistsCalled);
    }

    @Test
    void existsSqlPathReturnsTrueWhenRowPresent() throws Exception {
        String prefix = uniquePrefix();
        TableRegistry.register(prefix, "users", Map.of("id", "VARCHAR(36)"));

        RecordingJdbcStore store = new RecordingJdbcStore();
        store.nextQueryRows.add(Map.of("1", 1));

        assertTrue(sqlRepo(store, prefix).exists("real-id"));
    }

    @Test
    void existsSqlPathExecutesSelectWhereQuery() throws Exception {
        String prefix = uniquePrefix();
        TableRegistry.register(prefix, "users", Map.of("id", "VARCHAR(36)"));

        RecordingJdbcStore store = new RecordingJdbcStore();
        sqlRepo(store, prefix).exists("check-id");

        assertFalse(store.querySqls.isEmpty());
        String sql = store.querySqls.get(0).toUpperCase();
        assertTrue(sql.contains("SELECT") && sql.contains("WHERE"));
    }

    // =========================================================================
    // query() — SQL path
    // =========================================================================

    @Test
    void querySqlPathExecutesJdbcQuery() throws Exception {
        String prefix = uniquePrefix();
        TableRegistry.register(prefix, "users", Map.of("id", "VARCHAR(36)"));

        RecordingJdbcStore store = new RecordingJdbcStore();
        Query q = Model.queryBuilder().build();
        sqlRepo(store, prefix).query(q);

        assertFalse(store.querySqls.isEmpty());
    }

    @Test
    void querySqlPathReturnsEmptyListWhenNoRows() throws Exception {
        String prefix = uniquePrefix();
        TableRegistry.register(prefix, "users", Map.of("id", "VARCHAR(36)"));

        RecordingJdbcStore store = new RecordingJdbcStore();
        List<TestModel> result = sqlRepo(store, prefix).query(Model.queryBuilder().build());

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void querySqlPathReturnsMappedModels() throws Exception {
        String prefix = uniquePrefix();
        TableRegistry.register(prefix, "users", Map.of("id", "VARCHAR(36)", "name", "VARCHAR(255)"));

        RecordingJdbcStore store = new RecordingJdbcStore();
        store.nextQueryRows.add(new HashMap<>(Map.of("id", "r1", "name", "Hans")));
        store.nextQueryRows.add(new HashMap<>(Map.of("id", "r2", "name", "Ida")));

        List<TestModel> result = sqlRepo(store, prefix).query(Model.queryBuilder().build());

        assertEquals(2, result.size());
        assertTrue(result.stream().anyMatch(m -> "r1".equals(m.getId()) && "Hans".equals(m.get("name"))));
        assertTrue(result.stream().anyMatch(m -> "r2".equals(m.getId()) && "Ida".equals(m.get("name"))));
    }

    @Test
    void querySqlPathDoesNotUseDataStore() throws Exception {
        String prefix = uniquePrefix();
        TableRegistry.register(prefix, "users", Map.of("id", "VARCHAR(36)"));

        RecordingJdbcStore store = new RecordingJdbcStore();
        sqlRepo(store, prefix).query(Model.queryBuilder().build());

        assertFalse(store.dataStoreLoadCalled);
    }

    // =========================================================================
    // Explicit dialect constructor
    // =========================================================================

    @Test
    void explicitDialectConstructorSavesViaJdbc() throws Exception {
        String prefix = uniquePrefix();
        Map<String, String> cols = new LinkedHashMap<>();
        cols.put("id",   "VARCHAR(36)");
        cols.put("name", "VARCHAR(255)");
        TableRegistry.register(prefix, "users_d", cols);

        RecordingJdbcStore store = new RecordingJdbcStore();
        ModelRepository<TestModel> repo =
            new ModelRepository<>(store, prefix, (id, data) -> new TestModel(id), SqlDialect.STANDARD);
        repo.save(model("d1", "Jane"));

        assertEquals(1, store.updateSqls.size());
        assertTrue(store.updateSqls.get(0).contains("users_d"));
        assertFalse(store.dataStoreSaveCalled);
    }

    // =========================================================================
    // Flat-map fallback when store is not JdbcStore even if table is registered
    // =========================================================================

    @Test
    void flatMapPathUsedWhenStoreIsNotJdbcStore() throws Exception {
        String prefix = uniquePrefix();
        TableRegistry.register(prefix, "users_fm",
            Map.of("id", "VARCHAR(36)", "name", "VARCHAR(255)"));

        InMemoryStore store = new InMemoryStore(); // plain DataStore; not a JdbcStore
        ModelRepository<TestModel> repo = new ModelRepository<>(store, prefix, (id, data) -> new TestModel(id));
        repo.save(model("fm1", "Karl"));

        // Data went to the flat-map store, not JDBC
        assertTrue(store.exists(prefix + "/fm1"));
    }

    // =========================================================================
    // deleteWhere(Query) — SQL path
    // =========================================================================

    @Test
    void deleteWhereQuerySqlPathIssuesSingleDeleteStatement() throws Exception {
        final String prefix = uniquePrefix();
        TableRegistry.register(prefix, "events",
            Map.of("id", "VARCHAR(36)", "type", "VARCHAR(50)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        sqlRepo(store, prefix).deleteWhere(
            com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder.class
                .cast(new com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder()
                    .whereEquals("type", "CLICK"))
                .build()
        );

        assertEquals(1, store.updateSqls.size());
        assertFalse(store.dataStoreDeleteCalled);
    }

    @Test
    void deleteWhereQuerySqlPathSqlStartsWithDeleteFrom() throws Exception {
        final String prefix = uniquePrefix();
        TableRegistry.register(prefix, "events",
            Map.of("id", "VARCHAR(36)", "type", "VARCHAR(50)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        sqlRepo(store, prefix).deleteWhere(
            new com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder()
                .whereEquals("type", "VIEW")
                .build()
        );

        final String sql = store.updateSqls.get(0).toUpperCase();
        assertTrue(sql.startsWith("DELETE FROM"), "Expected DELETE FROM but got: " + sql);
        assertTrue(sql.contains("WHERE"),         "Expected WHERE in: " + sql);
    }

    @Test
    void deleteWhereQuerySqlPathPassesWhereEqValueAsParam() throws Exception {
        final String prefix = uniquePrefix();
        TableRegistry.register(prefix, "events",
            Map.of("id", "VARCHAR(36)", "type", "VARCHAR(50)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        sqlRepo(store, prefix).deleteWhere(
            new com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder()
                .whereEquals("type", "SCROLL")
                .build()
        );

        final String sql = store.updateSqls.get(0);
        assertFalse(sql.contains("SCROLL"), "Value must not appear in SQL string");
        assertTrue(store.updateParams.get(0).contains("SCROLL"), "Value must be a bind parameter");
    }

    @Test
    void deleteWhereQuerySqlPathPassesWhereInValuesAsParams() throws Exception {
        final String prefix = uniquePrefix();
        TableRegistry.register(prefix, "events",
            Map.of("id", "VARCHAR(36)", "type", "VARCHAR(50)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        sqlRepo(store, prefix).deleteWhere(
            new com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder()
                .whereIn("type", List.of("CLICK", "VIEW", "SCROLL"))
                .build()
        );

        final String sql          = store.updateSqls.get(0);
        final List<Object> params = store.updateParams.get(0);
        assertFalse(sql.contains("CLICK"),  "whereIn value must not appear in SQL");
        assertFalse(sql.contains("VIEW"),   "whereIn value must not appear in SQL");
        assertFalse(sql.contains("SCROLL"), "whereIn value must not appear in SQL");
        assertTrue(params.contains("CLICK"),  "CLICK must be a bind parameter");
        assertTrue(params.contains("VIEW"),   "VIEW must be a bind parameter");
        assertTrue(params.contains("SCROLL"), "SCROLL must be a bind parameter");
        assertEquals(3, params.size(), "Expected 3 bind parameters for 3 IN values");
    }

    @Test
    void deleteWhereQuerySqlPathMultiConditionPassesAllValuesAsParams() throws Exception {
        final String prefix = uniquePrefix();
        TableRegistry.register(prefix, "events",
            Map.of("id", "VARCHAR(36)", "type", "VARCHAR(50)", "status", "VARCHAR(20)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        sqlRepo(store, prefix).deleteWhere(
            new com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder()
                .whereEquals("type", "CLICK")
                .whereEquals("status", "PENDING")
                .build()
        );

        final List<Object> params = store.updateParams.get(0);
        assertTrue(params.contains("CLICK"),   "type value must be a bind parameter");
        assertTrue(params.contains("PENDING"), "status value must be a bind parameter");
        assertEquals(2, params.size(), "Expected exactly 2 bind parameters");
    }

    // =========================================================================
    // deleteWhereInSubquery() — SQL path
    // =========================================================================

    @Test
    void deleteWhereInSubquerySqlPathIssuesSingleDeleteStatement() throws Exception {
        final String prefix = uniquePrefix();
        TableRegistry.register(prefix, "users",
            Map.of("id", "VARCHAR(36)", "name", "VARCHAR(255)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        final Query subquery = new com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder()
            .from("banned_ids")
            .select("user_id")
            .whereEquals("reason", "spam")
            .build();
        sqlRepo(store, prefix).deleteWhereInSubquery("id", subquery);

        assertEquals(1, store.updateSqls.size(),
            "deleteWhereInSubquery must issue exactly one statement");
        assertFalse(store.dataStoreDeleteCalled,
            "flat-map delete must not be called on SQL path");
    }

    @Test
    void deleteWhereInSubquerySqlContainsDeleteFromAndWhereIn() throws Exception {
        final String prefix = uniquePrefix();
        TableRegistry.register(prefix, "users",
            Map.of("id", "VARCHAR(36)", "name", "VARCHAR(255)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        final Query subquery = new com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder()
            .from("banned_ids")
            .select("user_id")
            .build();
        sqlRepo(store, prefix).deleteWhereInSubquery("id", subquery);

        final String sql = store.updateSqls.get(0).toUpperCase();
        assertTrue(sql.startsWith("DELETE FROM"),
            "Expected DELETE FROM but got: " + sql);
        assertTrue(sql.contains("WHERE"),
            "Expected WHERE in: " + sql);
        assertTrue(sql.contains(" IN "),
            "Expected IN subquery but got: " + sql);
        assertTrue(sql.contains("SELECT"),
            "Expected SELECT in subquery but got: " + sql);
    }

    @Test
    void deleteWhereInSubqueryPassesSubqueryParamAsBindParam() throws Exception {
        final String prefix = uniquePrefix();
        TableRegistry.register(prefix, "users",
            Map.of("id", "VARCHAR(36)", "name", "VARCHAR(255)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        final Query subquery = new com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder()
            .from("reports")
            .select("offender_id")
            .whereEquals("severity", "HIGH")
            .build();
        sqlRepo(store, prefix).deleteWhereInSubquery("id", subquery);

        final String sql          = store.updateSqls.get(0);
        final List<Object> params = store.updateParams.get(0);
        assertFalse(sql.contains("HIGH"),
            "Subquery param must not appear in SQL string");
        assertTrue(params.contains("HIGH"),
            "Subquery param must be a bind parameter");
        assertEquals(1, params.size(),
            "Expected exactly 1 bind parameter");
    }

    @Test
    void deleteWhereInSubqueryThrowsOnNonSqlStore() {
        final String prefix = uniquePrefix();
        // No TableRegistry.register — no SQL table, plain DataStore store
        final ModelRepository<TestModel> repo = new ModelRepository<>(
            new InMemoryStore(), prefix, (id, data) -> new TestModel(id));
        final Query subquery = new com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder()
            .from("other")
            .select("id")
            .build();

        assertThrows(UnsupportedOperationException.class,
            () -> repo.deleteWhereInSubquery("id", subquery),
            "deleteWhereInSubquery must throw on a non-SQL store");
    }

    // =========================================================================
    // deleteWhereExists() — SQL path
    // =========================================================================

    @Test
    void deleteWhereExistsSqlPathIssuesSingleDeleteStatement() throws Exception {
        final String prefix = uniquePrefix();
        TableRegistry.register(prefix, "orders",
            Map.of("id", "VARCHAR(36)", "status", "VARCHAR(20)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        final Query subquery = new com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder()
            .from("cancellations")
            .select("order_id")
            .whereEquals("confirmed", true)
            .build();
        sqlRepo(store, prefix).deleteWhereExists(subquery);

        assertEquals(1, store.updateSqls.size(),
            "deleteWhereExists must issue exactly one statement");
        assertFalse(store.dataStoreDeleteCalled,
            "flat-map delete must not be called on SQL path");
    }

    @Test
    void deleteWhereExistsSqlContainsDeleteFromAndExists() throws Exception {
        final String prefix = uniquePrefix();
        TableRegistry.register(prefix, "orders",
            Map.of("id", "VARCHAR(36)", "status", "VARCHAR(20)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        final Query subquery = new com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder()
            .from("cancellations")
            .select("order_id")
            .build();
        sqlRepo(store, prefix).deleteWhereExists(subquery);

        final String sql = store.updateSqls.get(0).toUpperCase();
        assertTrue(sql.startsWith("DELETE FROM"),
            "Expected DELETE FROM but got: " + sql);
        assertTrue(sql.contains("EXISTS"),
            "Expected EXISTS keyword but got: " + sql);
        assertTrue(sql.contains("SELECT"),
            "Expected SELECT in subquery but got: " + sql);
    }

    @Test
    void deleteWhereExistsPassesSubqueryParamAsBindParam() throws Exception {
        final String prefix = uniquePrefix();
        TableRegistry.register(prefix, "orders",
            Map.of("id", "VARCHAR(36)", "status", "VARCHAR(20)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        final Query subquery = new com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder()
            .from("fraud_flags")
            .select("account_id")
            .whereEquals("flagged_by", "system")
            .build();
        sqlRepo(store, prefix).deleteWhereExists(subquery);

        final String sql          = store.updateSqls.get(0);
        final List<Object> params = store.updateParams.get(0);
        assertFalse(sql.contains("system"),
            "Subquery param must not appear in SQL string");
        assertTrue(params.contains("system"),
            "Subquery param must be a bind parameter");
        assertEquals(1, params.size(),
            "Expected exactly 1 bind parameter");
    }

    @Test
    void deleteWhereExistsThrowsOnNonSqlStore() {
        final String prefix = uniquePrefix();
        // No TableRegistry entry — no SQL table, plain DataStore store
        final ModelRepository<TestModel> repo = new ModelRepository<>(
            new InMemoryStore(), prefix, (id, data) -> new TestModel(id));
        final Query subquery = new com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder()
            .from("other")
            .select("id")
            .build();

        assertThrows(UnsupportedOperationException.class,
            () -> repo.deleteWhereExists(subquery),
            "deleteWhereExists must throw on a non-SQL store");
    }

    // =========================================================================
    // deleteWhere(Query) — flat-map path
    // =========================================================================

    @Test
    void deleteWhereQueryFlatMapPathDeletesMatchingRecords() throws Exception {
        final String prefix = uniquePrefix();
        // No table registered → flat-map path taken
        final QueryableFlatStore store = new QueryableFlatStore();
        final String path = prefix + "/r1";
        store.putRecord(path, Map.of("id", "r1", "type", "click"));
        store.setQueryResultIds(List.of("r1"));

        final ModelRepository<TestModel> repo =
            new ModelRepository<>(store, prefix, (id, data) -> new TestModel(id));
        repo.deleteWhere(
            new com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder()
                .whereEquals("type", "click")
                .build()
        );

        assertFalse(store.exists(path),
            "Flat-map path must delete records returned by QueryableStorage");
    }

    @Test
    void deleteWhereQueryFlatMapPathEmptyResultDoesNotDelete() throws Exception {
        final String prefix = uniquePrefix();
        final QueryableFlatStore store = new QueryableFlatStore();
        final String path = prefix + "/r2";
        store.putRecord(path, Map.of("id", "r2", "type", "view"));
        store.setQueryResultIds(List.of()); // empty — loop body never executes

        final ModelRepository<TestModel> repo =
            new ModelRepository<>(store, prefix, (id, data) -> new TestModel(id));
        repo.deleteWhere(
            new com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder()
                .whereEquals("type", "no-match")
                .build()
        );

        assertTrue(store.exists(path),
            "Flat-map path with no matches must not delete unrelated records");
    }

    // =========================================================================
    // deleteWhere(Query) / deleteWhereInSubquery / deleteWhereExists — exception paths
    // =========================================================================

    @Test
    void deleteWhereQuerySqlPathWrapsExceptionAsStorageException() {
        final String prefix = uniquePrefix();
        TableRegistry.register(prefix, "events",
            Map.of("id", "VARCHAR(36)", "type", "VARCHAR(50)"));

        final ThrowingJdbcStore store = new ThrowingJdbcStore(new RuntimeException("db error"));
        final Query q = new com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder()
            .whereEquals("type", "click")
            .build();

        assertThrows(StorageException.class,
            () -> sqlRepo(store, prefix).deleteWhere(q),
            "deleteWhere(Query) must wrap store exceptions as StorageException");
    }

    @Test
    void deleteWhereInSubquerySqlPathWrapsExceptionAsStorageException() {
        final String prefix = uniquePrefix();
        TableRegistry.register(prefix, "users",
            Map.of("id", "VARCHAR(36)", "name", "VARCHAR(255)"));

        final ThrowingJdbcStore store = new ThrowingJdbcStore(new RuntimeException("db error"));
        final Query subquery = new com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder()
            .from("banned")
            .select("id")
            .build();

        assertThrows(StorageException.class,
            () -> sqlRepo(store, prefix).deleteWhereInSubquery("id", subquery),
            "deleteWhereInSubquery must wrap store exceptions as StorageException");
    }

    @Test
    void deleteWhereExistsSqlPathWrapsExceptionAsStorageException() {
        final String prefix = uniquePrefix();
        TableRegistry.register(prefix, "orders",
            Map.of("id", "VARCHAR(36)", "status", "VARCHAR(20)"));

        final ThrowingJdbcStore store = new ThrowingJdbcStore(new RuntimeException("db error"));
        final Query subquery = new com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder()
            .from("cancellations")
            .select("order_id")
            .build();

        assertThrows(StorageException.class,
            () -> sqlRepo(store, prefix).deleteWhereExists(subquery),
            "deleteWhereExists must wrap store exceptions as StorageException");
    }
}
