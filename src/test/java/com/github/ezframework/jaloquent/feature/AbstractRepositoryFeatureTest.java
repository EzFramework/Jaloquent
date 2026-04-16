package com.github.ezframework.jaloquent.repository;

import com.github.ezframework.jaloquent.store.DataStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class AbstractRepositoryFeatureTest {

    // -------------------------------------------------------------------------
    // find()
    // -------------------------------------------------------------------------

    @Test
    void findReturnsEmptyWhenNotPresent() throws Exception {
        TestRepo repo = new TestRepo(new InMemoryStore());
        Optional<Item> result = repo.find("nonexistent");
        assertFalse(result.isPresent());
    }

    @Test
    void findReturnsPresentAfterSave() throws Exception {
        InMemoryStore store = new InMemoryStore();
        TestRepo repo = new TestRepo(store);
        Item item = new Item("user/42", "Alice");
        repo.save(item);
        Optional<Item> found = repo.find("user/42");
        assertTrue(found.isPresent());
        assertEquals("Alice", found.get().name());
    }

    // -------------------------------------------------------------------------
    // findAll()
    // -------------------------------------------------------------------------

    @Test
    void findAllDefaultReturnsEmptyList() throws Exception {
        TestRepo repo = new TestRepo(new InMemoryStore());
        List<Item> all = repo.findAll();
        assertNotNull(all);
        assertTrue(all.isEmpty());
    }

    // -------------------------------------------------------------------------
    // save()
    // -------------------------------------------------------------------------

    @Test
    void saveStoresEntityInStore() throws Exception {
        InMemoryStore store = new InMemoryStore();
        TestRepo repo = new TestRepo(store);
        Item item = new Item("user/1", "Bob");
        repo.save(item);
        assertTrue(store.exists("user/1"), "store should have stored the entity");
    }

    @Test
    void saveOverwritesExistingEntity() throws Exception {
        InMemoryStore store = new InMemoryStore();
        TestRepo repo = new TestRepo(store);
        repo.save(new Item("user/1", "Alice"));
        repo.save(new Item("user/1", "Alicia"));
        Optional<Item> found = repo.find("user/1");
        assertTrue(found.isPresent());
        assertEquals("Alicia", found.get().name());
    }

    // -------------------------------------------------------------------------
    // delete()
    // -------------------------------------------------------------------------

    @Test
    void deleteRemovesEntityFromStore() throws Exception {
        InMemoryStore store = new InMemoryStore();
        TestRepo repo = new TestRepo(store);
        repo.save(new Item("user/99", "Delete Me"));
        repo.delete("user/99");
        assertFalse(store.exists("user/99"), "store should not have the entity after delete");
    }

    @Test
    void deleteNonExistentDoesNotThrow() throws Exception {
        TestRepo repo = new TestRepo(new InMemoryStore());
        assertDoesNotThrow(() -> repo.delete("user/missing"));
    }

    // -------------------------------------------------------------------------
    // pathFor() (via prefix)
    // -------------------------------------------------------------------------

    @Test
    void prefixIsPrependedToId() throws Exception {
        InMemoryStore store = new InMemoryStore();
        TestRepo repo = new TestRepo(store, "items/");
        repo.save(new Item("42", "Prefixed"));
        assertTrue(store.exists("items/42"), "path should include prefix");
    }

    @Test
    void nullPrefixTreatedAsEmpty() throws Exception {
        InMemoryStore store = new InMemoryStore();
        TestRepo repo = new TestRepo(store, null);
        repo.save(new Item("abc", "NullPrefix"));
        assertTrue(store.exists("abc"));
    }

    // -------------------------------------------------------------------------
    // store()
    // -------------------------------------------------------------------------

    @Test
    void storeAccessorReturnsConstructorArgument() {
        InMemoryStore store = new InMemoryStore();
        TestRepo repo = new TestRepo(store);
        assertSame(store, repo.exposedStore());
    }

    // -------------------------------------------------------------------------
    // findAll() — overridden implementation
    // -------------------------------------------------------------------------

    @Test
    void findAllOverrideReturnsAllSavedEntities() throws Exception {
        InMemoryStore store = new InMemoryStore();
        EnumerableRepo repo = new EnumerableRepo(store, "items/");
        repo.save(new Item("items/1", "Alpha"));
        repo.save(new Item("items/2", "Beta"));
        List<Item> all = repo.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void findAllOverrideReturnsEmptyWhenNonePresent() throws Exception {
        InMemoryStore store = new InMemoryStore();
        EnumerableRepo repo = new EnumerableRepo(store, "items/");
        List<Item> all = repo.findAll();
        assertNotNull(all);
        assertTrue(all.isEmpty());
    }

    @Test
    void findAllOverrideReflectsDeletions() throws Exception {
        InMemoryStore store = new InMemoryStore();
        EnumerableRepo repo = new EnumerableRepo(store, "items/");
        repo.save(new Item("items/1", "Alpha"));
        repo.save(new Item("items/2", "Beta"));
        repo.delete("items/1");
        List<Item> all = repo.findAll();
        assertEquals(1, all.size());
        assertEquals("Beta", all.getFirst().name());
    }

    // -------------------------------------------------------------------------
    // Test fixtures
    // -------------------------------------------------------------------------

    /** Simple entity. */
    record Item(String id, String name) {}

    /** Concrete repo for Item. */
    static class TestRepo extends AbstractRepository<Item, String> {
        TestRepo(DataStore store) { super(store, ""); }
        TestRepo(DataStore store, String prefix) { super(store, prefix); }

        @Override
        protected Map<String, Object> toMap(Item item) {
            Map<String, Object> m = new HashMap<>();
            m.put("name", item.name());
            return m;
        }

        @Override
        protected Item fromMap(Map<String, Object> map) {
            return new Item((String) map.get("id"), (String) map.get("name"));
        }

        @Override
        protected String extractId(Item item) { return item.id(); }

        /** Expose protected store() for assertions. */
        DataStore exposedStore() { return store(); }
    }

    /**
     * Repo that overrides findAll() to enumerate all keys in the backing store.
     * Demonstrates and tests overriding the default empty-list behaviour.
     */
    static class EnumerableRepo extends AbstractRepository<Item, String> {
        private final InMemoryStore enumerableStore;

        EnumerableRepo(InMemoryStore store, String prefix) {
            super(store, prefix);
            this.enumerableStore = store;
        }

        @Override
        public List<Item> findAll() {
            List<Item> result = new ArrayList<>();
            for (Map.Entry<String, Map<String, Object>> entry : enumerableStore.data.entrySet()) {
                result.add(fromMap(entry.getValue()));
            }
            return result;
        }

        @Override
        protected Map<String, Object> toMap(Item item) {
            Map<String, Object> m = new HashMap<>();
            m.put("name", item.name());
            return m;
        }

        @Override
        protected Item fromMap(Map<String, Object> map) {
            return new Item((String) map.get("id"), (String) map.get("name"));
        }

        @Override
        protected String extractId(Item item) { return item.id(); }
    }

    /** Simple in-memory DataStore. */
    static class InMemoryStore implements DataStore {
        final Map<String, Map<String, Object>> data = new HashMap<>();

        @Override
        public void save(String path, Map<String, Object> values) {
            Map<String, Object> copy = new HashMap<>(values);
            copy.put("id", path); // store path as id for round-trip reconstruction
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
    }
}
