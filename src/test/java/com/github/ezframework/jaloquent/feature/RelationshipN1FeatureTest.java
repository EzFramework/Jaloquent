package com.github.ezframework.jaloquent.feature;

import com.github.ezframework.jaloquent.model.BaseModel;
import com.github.ezframework.jaloquent.model.Model;
import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.jaloquent.model.PivotModel;
import com.github.ezframework.jaloquent.model.TableRegistry;
import com.github.ezframework.jaloquent.relation.BelongsToMany;
import com.github.ezframework.jaloquent.store.DataStore;
import com.github.ezframework.jaloquent.store.sql.JdbcStore;
import com.github.ezframework.javaquerybuilder.query.Query;
import com.github.ezframework.javaquerybuilder.query.QueryableStorage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feature tests that detect and verify the resolution of N+1 query problems
 * in Jaloquent relationship implementations.
 *
 * <p>Each test asserts an exact SQL operation count. Tests that expose N+1 problems
 * would fail with the naive implementation; after the fix they pass with O(1) SQL ops.
 *
 * <p>The QueryableStorage N+1 pattern (one load per matching ID in
 * {@code ModelRepository.query()}) is documented here as a known architectural
 * limitation of the flat-map {@link DataStore} interface.
 */
public class RelationshipN1FeatureTest {

    // =========================================================================
    // Model Fixtures
    // =========================================================================

    /** Minimal user model used to expose relationship factory methods. */
    static class UserModel extends Model {

        UserModel(String id) {
            super(id);
        }

        /**
         * Declare a BelongsToMany relationship with configurable repositories.
         *
         * @param roleRepo  the role repository
         * @param pivotRepo the pivot repository
         * @return the relationship instance
         */
        public BelongsToMany<RoleModel, PivotModel> roles(
                ModelRepository<RoleModel> roleRepo,
                ModelRepository<PivotModel> pivotRepo) {
            return belongsToMany(roleRepo, pivotRepo, PivotModel.FACTORY, "user_id", "role_id");
        }

    }

    /** Minimal role model. */
    static class RoleModel extends Model {

        RoleModel(String id) {
            super(id);
        }

    }

    // =========================================================================
    // Store Fixtures
    // =========================================================================

    /**
     * JDBC store that records every SQL operation and its parameter list.
     * Implements both {@link DataStore} and {@link JdbcStore} so that
     * {@link ModelRepository} takes the SQL path.
     */
    static class CountingJdbcStore implements DataStore, JdbcStore {

        /** Number of SELECT statements issued. */
        int queryCount = 0;

        /** Number of INSERT / UPDATE / DELETE statements issued. */
        int updateCount = 0;

        /** SQL strings from every executeUpdate call, in order. */
        final List<String> updateSqls = new ArrayList<>();

        /** Parameter lists from every executeUpdate call, in order. */
        final List<List<Object>> updateParamsList = new ArrayList<>();

        /** Rows returned by the next query call. */
        List<Map<String, Object>> nextQueryRows = new ArrayList<>();

        @Override
        public int executeUpdate(String sql, List<Object> params) {
            updateCount++;
            updateSqls.add(sql);
            updateParamsList.add(new ArrayList<>(params));
            return 1;
        }

        @Override
        public List<Map<String, Object>> query(String sql, List<Object> params) {
            queryCount++;
            return new ArrayList<>(nextQueryRows);
        }

        @Override
        public void save(String path, Map<String, Object> data) {
        }

        @Override
        public Optional<Map<String, Object>> load(String path) {
            return Optional.empty();
        }

        @Override
        public void delete(String path) {
        }

        @Override
        public boolean exists(String path) {
            return false;
        }

    }

    /**
     * Flat-map store that counts every {@code load()} and {@code delete()} call.
     * Implements {@link QueryableStorage} so that the QueryableStorage path in
     * {@link ModelRepository#query(Query)} is exercised.
     */
    static class CountingFlatStore implements DataStore, QueryableStorage {

        /** Number of {@link #load(String)} calls. */
        int loadCount = 0;

        /** Number of {@link #delete(String)} calls. */
        int deleteCount = 0;

        /** IDs returned by {@link #query(Query)}. */
        List<String> nextQueryIds = new ArrayList<>();

        private final Map<String, Map<String, Object>> data = new HashMap<>();

        @Override
        public void save(String path, Map<String, Object> d) {
            final Map<String, Object> copy = new HashMap<>(d);
            final int slash = path.lastIndexOf('/');
            copy.put("id", slash >= 0 ? path.substring(slash + 1) : path);
            data.put(path, copy);
        }

        @Override
        public Optional<Map<String, Object>> load(String path) {
            loadCount++;
            return Optional.ofNullable(data.get(path));
        }

        @Override
        public void delete(String path) {
            deleteCount++;
            data.remove(path);
        }

        @Override
        public boolean exists(String path) {
            return data.containsKey(path);
        }

        @Override
        public List<String> query(Query q) {
            return new ArrayList<>(nextQueryIds);
        }

    }

    // =========================================================================
    // Helper Factories
    // =========================================================================

    private ModelRepository<PivotModel> pivotRepo(DataStore store, String prefix) {
        return new ModelRepository<>(store, prefix, PivotModel.FACTORY);
    }

    private ModelRepository<RoleModel> roleRepo(DataStore store, String prefix) {
        return new ModelRepository<>(store, prefix, (id, d) -> new RoleModel(id));
    }

    private ModelRepository<RoleModel> roleRepo(DataStore store) {
        return roleRepo(store, UUID.randomUUID().toString());
    }

    // =========================================================================
    // N+1 Fix: BelongsToMany.detachAll() — SQL path
    //
    // Old behaviour (N+1): SELECT all pivot rows, then DELETE each row individually.
    //   → 1 SELECT + N DELETEs
    // Fixed behaviour: DELETE WHERE foreign_key = ?
    //   → 1 DELETE, no SELECT at all
    // =========================================================================

    @Test
    void detachAllSqlIssuesSingleDeleteWithoutSelect() throws Exception {
        final String prefix = UUID.randomUUID().toString();
        TableRegistry.register(prefix, "user_roles",
                Map.of("user_id", "varchar", "role_id", "varchar"));

        final CountingJdbcStore pivotJdbc = new CountingJdbcStore();
        final UserModel user = new UserModel("u1");

        user.roles(roleRepo(new CountingJdbcStore()), pivotRepo(pivotJdbc, prefix)).detachAll();

        assertEquals(0, pivotJdbc.queryCount,
                "detachAll must not SELECT pivot rows on the SQL path");
        assertEquals(1, pivotJdbc.updateCount,
                "detachAll must issue exactly 1 DELETE statement");
        assertTrue(pivotJdbc.updateSqls.get(0).contains("DELETE FROM user_roles"),
                "DELETE must target the pivot table");
        assertTrue(pivotJdbc.updateSqls.get(0).contains("user_id"),
                "DELETE must filter on the foreign-key column");
        assertEquals(List.of("u1"), pivotJdbc.updateParamsList.get(0),
                "DELETE parameter must be the parent id");
    }

    @Test
    void detachAllSqlScalesTo100PivotEntriesWithOneSqlStatement() throws Exception {
        final String prefix = UUID.randomUUID().toString();
        TableRegistry.register(prefix, "user_roles",
                Map.of("user_id", "varchar", "role_id", "varchar"));

        // Simulate 100 existing pivot rows in the database
        final CountingJdbcStore pivotJdbc = new CountingJdbcStore();
        for (int i = 0; i < 100; i++) {
            final Map<String, Object> row = new HashMap<>();
            row.put("id", "u1_r" + i);
            row.put("user_id", "u1");
            row.put("role_id", "r" + i);
            pivotJdbc.nextQueryRows.add(row);
        }

        final UserModel user = new UserModel("u1");
        user.roles(roleRepo(new CountingJdbcStore()), pivotRepo(pivotJdbc, prefix)).detachAll();

        assertEquals(1, pivotJdbc.updateCount,
                "detachAll must always issue exactly 1 DELETE regardless of pivot count");
    }

    // =========================================================================
    // N+1 Fix: BelongsToMany.sync() — SQL path
    //
    // Old behaviour (N+1 detach side):
    //   1 SELECT + N individual DELETEs for removed entries
    // Fixed behaviour:
    //   1 SELECT + 1 bulk DELETE WHERE id IN (…) for removed entries
    // =========================================================================

    @Test
    void syncSqlBulkDeletesRemovedPivotEntriesWithOneStatement() throws Exception {
        final String prefix = UUID.randomUUID().toString();
        TableRegistry.register(prefix, "user_roles",
                Map.of("user_id", "varchar", "role_id", "varchar"));

        final CountingJdbcStore pivotJdbc = new CountingJdbcStore();

        // Simulate 5 existing pivot entries
        for (int i = 1; i <= 5; i++) {
            final Map<String, Object> row = new HashMap<>();
            row.put("id", "u1_r" + i);
            row.put("user_id", "u1");
            row.put("role_id", "r" + i);
            pivotJdbc.nextQueryRows.add(row);
        }

        final UserModel user = new UserModel("u1");
        // Keep r1 and r2 — remove r3, r4, r5 (all three must be deleted in 1 SQL statement)
        user.roles(roleRepo(new CountingJdbcStore()), pivotRepo(pivotJdbc, prefix))
                .sync(List.of("r1", "r2"));

        assertEquals(1, pivotJdbc.queryCount,
                "sync must issue exactly 1 SELECT to read current pivot state");
        assertEquals(1, pivotJdbc.updateCount,
                "sync must issue exactly 1 bulk DELETE for all removed entries");
        assertTrue(pivotJdbc.updateSqls.get(0).contains("IN"),
                "bulk DELETE must use an IN clause");
    }

    @Test
    void syncSqlWithNoRemovals_issuesNoDeleteStatement() throws Exception {
        final String prefix = UUID.randomUUID().toString();
        TableRegistry.register(prefix, "user_roles",
                Map.of("user_id", "varchar", "role_id", "varchar"));

        final CountingJdbcStore pivotJdbc = new CountingJdbcStore();

        // No existing pivot entries
        final UserModel user = new UserModel("u1");
        // All desired IDs are new — 0 to remove, 3 to add
        user.roles(roleRepo(new CountingJdbcStore()), pivotRepo(pivotJdbc, prefix))
                .sync(List.of("r1", "r2", "r3"));

        // 1 SELECT + 0 deletes + 3 inserts = 4 total, of which updateCount = 3
        assertEquals(1, pivotJdbc.queryCount, "sync must issue 1 SELECT to load current state");
        assertEquals(3, pivotJdbc.updateCount,
                "sync must insert each new entry individually (no removals → no DELETE)");
    }

    @Test
    void syncSqlWithMixedChanges_issuesOneSelectOneBulkDeleteThenInserts() throws Exception {
        final String prefix = UUID.randomUUID().toString();
        TableRegistry.register(prefix, "user_roles",
                Map.of("user_id", "varchar", "role_id", "varchar"));

        final CountingJdbcStore pivotJdbc = new CountingJdbcStore();

        // 4 existing: r1, r2, r3, r4 — sync to r2, r3, r5, r6
        // Remove: r1, r4 (2). Add: r5, r6 (2). Keep: r2, r3 (2).
        for (int i = 1; i <= 4; i++) {
            final Map<String, Object> row = new HashMap<>();
            row.put("id", "u1_r" + i);
            row.put("user_id", "u1");
            row.put("role_id", "r" + i);
            pivotJdbc.nextQueryRows.add(row);
        }

        final UserModel user = new UserModel("u1");
        user.roles(roleRepo(new CountingJdbcStore()), pivotRepo(pivotJdbc, prefix))
                .sync(List.of("r2", "r3", "r5", "r6"));

        // 1 SELECT + 1 bulk DELETE (r1,r4) + 2 INSERTs (r5,r6) = 4 total SQL ops
        assertEquals(1, pivotJdbc.queryCount, "sync must issue 1 SELECT");
        assertEquals(3, pivotJdbc.updateCount,
                "sync must issue 1 bulk DELETE + 1 INSERT per new entry = 3 updates");
        assertTrue(pivotJdbc.updateSqls.get(0).contains("IN"),
                "the first update must be a bulk DELETE with IN clause");
        assertTrue(pivotJdbc.updateSqls.get(1).contains("INSERT"),
                "subsequent updates must be INSERTs for new entries");
    }

    // =========================================================================
    // Flat-map path: detachAll() behaviour preserved
    //
    // On a plain DataStore the flat-map path has no choice but to load and
    // delete individually. The test confirms correctness and documents count.
    // =========================================================================

    @Test
    void detachAllFlatMapDeletesOnlyTheParentsOwnPivotEntries() throws Exception {
        final CountingFlatStore pivotStore = new CountingFlatStore();
        final ModelRepository<PivotModel> pivot = pivotRepo(pivotStore, "ur");

        // Save 4 pivot entries for user u1
        for (int i = 1; i <= 4; i++) {
            final PivotModel p = new PivotModel("u1_r" + i);
            p.set("user_id", "u1");
            p.set("role_id", "r" + i);
            pivot.save(p);
        }

        // Save 1 pivot entry for a different user
        final PivotModel other = new PivotModel("u2_r1");
        other.set("user_id", "u2");
        other.set("role_id", "r1");
        pivot.save(other);

        // Return only u1's IDs from the queryable store
        pivotStore.nextQueryIds = List.of("u1_r1", "u1_r2", "u1_r3", "u1_r4");
        pivotStore.deleteCount = 0;

        new UserModel("u1").roles(roleRepo(new CountingFlatStore()), pivot).detachAll();

        assertEquals(4, pivotStore.deleteCount,
                "detachAll must delete exactly the 4 pivot entries belonging to u1");
        assertTrue(pivotStore.exists("ur/u2_r1"),
                "detachAll must not touch other users' pivot entries");
        assertFalse(pivotStore.exists("ur/u1_r1"),
                "user u1's pivot entry must be deleted");
    }

    // =========================================================================
    // Documented limitation: ModelRepository.query() QueryableStorage N+1
    //
    // When a store implements QueryableStorage but not JdbcStore, query() calls
    // store.query() to obtain a list of IDs and then calls store.load() once
    // per ID. This is an inherent consequence of the flat DataStore interface,
    // which provides no batch-load or cursor API.
    //
    // Solution: register the table with TableRegistry and provide a JdbcStore
    // implementation — the SQL path issues a single SELECT for any result size.
    // =========================================================================

    @Test
    void queryableStoragePathRequiresOneLoadPerMatchingId() throws Exception {
        final CountingFlatStore store = new CountingFlatStore();

        // Store 5 models
        for (int i = 1; i <= 5; i++) {
            store.save("m/item" + i, Map.of("value", i));
        }

        // Configure the queryable store to signal all 5 IDs as matching
        store.nextQueryIds = List.of("item1", "item2", "item3", "item4", "item5");
        store.loadCount = 0;

        final ModelRepository<RoleModel> repo = roleRepo(store, "m");
        repo.query(Model.queryBuilder().build());

        // This is O(N) — one load() call per matching ID.
        // It is a known architectural limitation of the flat-map DataStore interface.
        // The SQL/JDBC path (TableRegistry + JdbcStore) issues a single SELECT
        // regardless of result set size and avoids this completely (see next test).
        assertEquals(5, store.loadCount,
                "QueryableStorage path issues one load() per matching ID (known limitation)");
    }

    @Test
    void sqlPathQueryIssuesSingleSelectForAnyResultSize() throws Exception {
        final String prefix = UUID.randomUUID().toString();
        TableRegistry.register(prefix, "roles", Map.of("name", "varchar"));

        final CountingJdbcStore jdbc = new CountingJdbcStore();
        for (int i = 1; i <= 50; i++) {
            final Map<String, Object> row = new HashMap<>();
            row.put("id", "r" + i);
            row.put("name", "role" + i);
            jdbc.nextQueryRows.add(row);
        }

        final ModelRepository<RoleModel> repo = roleRepo(jdbc, prefix);
        final List<RoleModel> results = repo.query(Model.queryBuilder().build());

        assertEquals(50, results.size(), "all 50 roles must be returned");
        assertEquals(1, jdbc.queryCount,
                "SQL path must issue exactly 1 SELECT regardless of result size");
    }

    // =========================================================================
    // Documented limitation: BelongsToMany.attach() is always O(1)
    // — no N+1 here, but we verify the count for completeness.
    // =========================================================================

    @Test
    void attachSqlIssuesSingleInsert() throws Exception {
        final String prefix = UUID.randomUUID().toString();
        TableRegistry.register(prefix, "user_roles",
                Map.of("user_id", "varchar", "role_id", "varchar"));

        final CountingJdbcStore pivotJdbc = new CountingJdbcStore();
        new UserModel("u1").roles(roleRepo(new CountingJdbcStore()), pivotRepo(pivotJdbc, prefix))
                .attach("r1");

        assertEquals(1, pivotJdbc.updateCount, "attach must issue exactly 1 INSERT");
        assertEquals(0, pivotJdbc.queryCount, "attach must not SELECT");
    }

    @Test
    void detachSqlIssuesSingleDelete() throws Exception {
        final String prefix = UUID.randomUUID().toString();
        TableRegistry.register(prefix, "user_roles",
                Map.of("user_id", "varchar", "role_id", "varchar"));

        final CountingJdbcStore pivotJdbc = new CountingJdbcStore();
        new UserModel("u1").roles(roleRepo(new CountingJdbcStore()), pivotRepo(pivotJdbc, prefix))
                .detach("r1");

        assertEquals(1, pivotJdbc.updateCount, "detach must issue exactly 1 DELETE");
        assertEquals(0, pivotJdbc.queryCount, "detach must not SELECT");
    }

}
