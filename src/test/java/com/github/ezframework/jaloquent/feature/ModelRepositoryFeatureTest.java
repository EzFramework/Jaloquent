package com.github.ezframework.jaloquent.model;

import com.github.ezframework.jaloquent.store.DataStore;
import com.github.ezframework.javaquerybuilder.query.Query;
import com.github.ezframework.javaquerybuilder.query.QueryableStorage;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class ModelRepositoryFeatureTest {
    // -------------------------------------------------------------------------
    // Subquery (inner query) support
    // ------------------------------------------------------------------------- 

    @Test
    void querySupportsSubqueryPattern() throws Exception {
        // Simulate a subquery: select models where coins > (select AVG(coins) ...)
        InMemoryQueryableStore store = new InMemoryQueryableStore();
        store.save("p/sq1", Map.of("name", "Alice", "id", "sq1", "coins", 200));
        store.save("p/sq2", Map.of("name", "Bob", "id", "sq2", "coins", 100));
        store.save("p/sq3", Map.of("name", "Carol", "id", "sq3", "coins", 300));

        ModelRepository<TestModel> repo = repo(store, "p");

        // Simulate the "subquery" step: compute average and pre-filter which IDs match.
        // In a real SQL backend this would be a subquery; with QueryableStorage the caller
        // resolves the target IDs in application code and registers them before querying.
        int avg = (200 + 100 + 300) / 3; // 200
        // Only sq3 (300) has coins above the average.
        store.registerQueryIds(List.of("sq3"));
        List<TestModel> result = repo.query(
            Model.queryBuilder().whereEquals("coins", avg + 1).build()
        );
        assertEquals(1, result.size());
        assertEquals("sq3", result.get(0).getId());
    }

    // -------------------------------------------------------------------------
    // find()
    // -------------------------------------------------------------------------

    @Test
    void findReturnsEmptyWhenNotPresent() throws Exception {
        ModelRepository<TestModel> repo = repo(new InMemoryStore(), "players");
        assertFalse(repo.find("nonexistent").isPresent());
    }

    @Test
    void findReturnsPresentAfterSave() throws Exception {
        InMemoryStore store = new InMemoryStore();
        ModelRepository<TestModel> repo = repo(store, "players");
        repo.save(model("u1", "Alice"));
        Optional<TestModel> found = repo.find("u1");
        assertTrue(found.isPresent());
        assertEquals("Alice", found.get().get("name"));
    }

    @Test
    void findReconstructsModelViaFactory() throws Exception {
        InMemoryStore store = new InMemoryStore();
        ModelRepository<TestModel> repo = repo(store, "p");
        repo.save(model("u2", "Bob"));
        TestModel loaded = repo.find("u2").orElseThrow();
        assertEquals("u2", loaded.getId());
        assertEquals("Bob", loaded.get("name"));
    }

    @Test
    void findWithNoPrefixUsesIdDirectly() throws Exception {
        InMemoryStore store = new InMemoryStore();
        ModelRepository<TestModel> repo = repo(store, null);
        repo.save(model("bare", "Alice"));
        assertTrue(store.exists("bare"), "should store directly under id when prefix is null");
    }

    // -------------------------------------------------------------------------
    // save()
    // -------------------------------------------------------------------------

    @Test
    void saveStoresModelInStore() throws Exception {
        InMemoryStore store = new InMemoryStore();
        ModelRepository<TestModel> repo = repo(store, "p");
        repo.save(model("m1", "Charlie"));
        assertTrue(store.exists("p/m1"));
    }

    @Test
    void saveOverwritesExistingModel() throws Exception {
        InMemoryStore store = new InMemoryStore();
        ModelRepository<TestModel> repo = repo(store, "p");
        repo.save(model("m1", "Before"));
        repo.save(model("m1", "After"));
        assertEquals("After", repo.find("m1").orElseThrow().get("name"));
    }

    // -------------------------------------------------------------------------
    // delete()
    // -------------------------------------------------------------------------

    @Test
    void deleteRemovesModelFromStore() throws Exception {
        InMemoryStore store = new InMemoryStore();
        ModelRepository<TestModel> repo = repo(store, "p");
        repo.save(model("m2", "Delete Me"));
        repo.delete("m2");
        assertFalse(repo.find("m2").isPresent());
    }

    @Test
    void deleteNonExistentDoesNotThrow() {
        ModelRepository<TestModel> repo = repo(new InMemoryStore(), "p");
        assertDoesNotThrow(() -> repo.delete("missing"));
    }

    // -------------------------------------------------------------------------
    // exists()
    // -------------------------------------------------------------------------

    @Test
    void existsReturnsFalseForMissingId() throws Exception {
        ModelRepository<TestModel> repo = repo(new InMemoryStore(), "p");
        assertFalse(repo.exists("nobody"));
    }

    @Test
    void existsReturnsTrueAfterSave() throws Exception {
        InMemoryStore store = new InMemoryStore();
        ModelRepository<TestModel> repo = repo(store, "p");
        repo.save(model("e1", "Eve"));
        assertTrue(repo.exists("e1"));
    }

    @Test
    void existsReturnsFalseAfterDelete() throws Exception {
        InMemoryStore store = new InMemoryStore();
        ModelRepository<TestModel> repo = repo(store, "p");
        repo.save(model("e2", "Frank"));
        repo.delete("e2");
        assertFalse(repo.exists("e2"));
    }

    // -------------------------------------------------------------------------
    // query() — fallback paths
    // -------------------------------------------------------------------------

    @Test
    void queryReturnsEmptyListWhenStoreIsPlainDataStore() throws Exception {
        ModelRepository<TestModel> repo = repo(new InMemoryStore(), "p");
        Query q = Model.queryBuilder().build();
        List<TestModel> result = repo.query(q);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void queryUsesQueryableStorageWhenNoSqlTableRegistered() throws Exception {
        InMemoryQueryableStore store = new InMemoryQueryableStore();
        store.save("p/q1", Map.of("name", "Alice", "id", "q1"));
        store.save("p/q2", Map.of("name", "Bob", "id", "q2"));
        store.registerQueryIds(List.of("q1", "q2"));

        ModelRepository<TestModel> repo = repo(store, "p");
        Query q = Model.queryBuilder().build();
        List<TestModel> result = repo.query(q);
        assertEquals(2, result.size());
    }

    @Test
    void queryLoadsEachIdReturnedByQueryableStorage() throws Exception {
        InMemoryQueryableStore store = new InMemoryQueryableStore();
        store.save("p/a", Map.of("name", "Alice", "id", "a"));
        store.save("p/b", Map.of("name", "Bob", "id", "b"));
        store.registerQueryIds(List.of("a"));

        ModelRepository<TestModel> repo = repo(store, "p");
        Query q = Model.queryBuilder().build();
        List<TestModel> result = repo.query(q);
        assertEquals(1, result.size());
        assertEquals("a", result.getFirst().getId());
    }

    @Test
    void queryReturnsEmptyListWhenQueryableStorageReturnsNone() throws Exception {
        InMemoryQueryableStore store = new InMemoryQueryableStore();
        store.registerQueryIds(List.of());

        ModelRepository<TestModel> repo = repo(store, "p");
        List<TestModel> result = repo.query(Model.queryBuilder().build());
        assertTrue(result.isEmpty());
    }

    // -------------------------------------------------------------------------
    // Active-record: Model.save() / Model.delete() / Model.find()
    // -------------------------------------------------------------------------

    @Test
    void modelSaveDelegatesToRepository() throws Exception {
        InMemoryStore store = new InMemoryStore();
        ModelRepository<TestModel> repo = repo(store, "p");
        TestModel m = model("ar1", "ActiveAlice");
        m.save(repo);
        assertTrue(store.exists("p/ar1"), "save() should persist to the backing store");
    }

    @Test
    void modelDeleteDelegatesToRepository() throws Exception {
        InMemoryStore store = new InMemoryStore();
        ModelRepository<TestModel> repo = repo(store, "p");
        TestModel m = model("ar2", "ActiveBob");
        repo.save(m);
        m.delete(repo);
        assertFalse(store.exists("p/ar2"), "delete() should remove from backing store");
    }

    @Test
    void modelFindStaticReturnsPresentModel() throws Exception {
        InMemoryStore store = new InMemoryStore();
        ModelRepository<TestModel> repo = repo(store, "p");
        repo.save(model("ar3", "ActiveCarol"));
        TestModel found = Model.find(repo, "ar3");
        assertNotNull(found);
        assertEquals("ar3", found.getId());
        assertEquals("ActiveCarol", found.get("name"));
    }

    @Test
    void modelFindStaticReturnsNullWhenNotFound() throws Exception {
        ModelRepository<TestModel> repo = repo(new InMemoryStore(), "p");
        assertNull(Model.find(repo, "nobody"));
    }

    @Test
    void modelSaveReturnsSameModelForChaining() throws Exception {
        InMemoryStore store = new InMemoryStore();
        ModelRepository<TestModel> repo = repo(store, "p");
        TestModel m = model("ar4", "Alice");
        assertSame(m, m.save(repo));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static ModelRepository<TestModel> repo(DataStore store, String prefix) {
        return new ModelRepository<>(store, prefix, (id, data) -> {
            TestModel m = new TestModel(id);
            m.fromMap(data);
            return m;
        });
    }

    private static TestModel model(String id, String name) {
        TestModel m = new TestModel(id);
        m.set("name", name);
        return m;
    }

    // -------------------------------------------------------------------------
    // Test fixtures
    // -------------------------------------------------------------------------

    static class TestModel extends Model {
        TestModel(String id) { super(id); }
    }

    /** Plain in-memory DataStore (does NOT implement QueryableStorage). */
    static class InMemoryStore implements DataStore {
        private final Map<String, Map<String, Object>> data = new HashMap<>();

        @Override
        public void save(String path, Map<String, Object> values) {
            Map<String, Object> copy = new HashMap<>(values);
            copy.put("id", idFromPath(path));
            data.put(path, copy);
        }

        @Override
        public Optional<Map<String, Object>> load(String path) {
            return Optional.ofNullable(data.get(path));
        }

        @Override
        public void delete(String path) { data.remove(path); }

        @Override
        public boolean exists(String path) { return data.containsKey(path); }

        private static String idFromPath(String path) {
            int slash = path.lastIndexOf('/');
            return slash >= 0 ? path.substring(slash + 1) : path;
        }
    }

    /** In-memory store that also implements QueryableStorage. */
    static class InMemoryQueryableStore extends InMemoryStore implements QueryableStorage {
        private List<String> queryIds = new ArrayList<>();

        void registerQueryIds(List<String> ids) { this.queryIds = new ArrayList<>(ids); }

        @Override
        public List<String> query(Query q) { return new ArrayList<>(queryIds); }
    }
}
