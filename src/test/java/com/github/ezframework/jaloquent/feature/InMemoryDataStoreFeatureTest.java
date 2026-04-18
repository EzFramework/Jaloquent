package com.github.ezframework.jaloquent.feature;

import com.github.ezframework.jaloquent.model.Model;
import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.jaloquent.store.InMemoryDataStore;
import com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Feature tests for {@link InMemoryDataStore} used both directly and via
 * {@link ModelRepository}.
 *
 * <p>No {@link com.github.ezframework.jaloquent.model.TableRegistry} entry is
 * registered, so {@link ModelRepository} routes all operations through the
 * flat-map path, delegating to {@link InMemoryDataStore} for every operation.
 */
public class InMemoryDataStoreFeatureTest {

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

    /** Store under test. */
    private InMemoryDataStore store;

    /** Repository backed by {@link #store}; prefix "p" with no registered SQL table. */
    private ModelRepository<TestModel> repo;

    // =========================================================================
    // Setup
    // =========================================================================

    /**
     * Creates a fresh store and repository before each test.
     */
    @BeforeEach
    void setUp() {
        store = new InMemoryDataStore();
        repo  = new ModelRepository<>(store, "p",
            (id, data) -> {
                TestModel m = new TestModel(id);
                m.fromMap(data);
                return m;
            });
    }

    // =========================================================================
    // Helper
    // =========================================================================

    private static TestModel model(String id, String name) {
        final TestModel m = new TestModel(id);
        m.set("name", name);
        return m;
    }

    // =========================================================================
    // DataStore direct — save / load / delete / exists
    // =========================================================================

    @Test
    void saveAndLoadDirectly() throws Exception {
        store.save("players/p1", new HashMap<>(Map.of("id", "p1", "name", "Alice")));
        final Optional<Map<String, Object>> loaded = store.load("players/p1");
        assertTrue(loaded.isPresent());
        assertEquals("Alice", loaded.get().get("name"));
    }

    @Test
    void loadReturnsEmptyWhenPathAbsent() throws Exception {
        assertFalse(store.load("missing/path").isPresent());
    }

    @Test
    void deleteRemovesEntryDirectly() throws Exception {
        store.save("players/p2", Map.of("id", "p2"));
        store.delete("players/p2");
        assertFalse(store.load("players/p2").isPresent());
    }

    @Test
    void existsReturnsTrueAfterSaveDirectly() throws Exception {
        store.save("players/p3", Map.of("id", "p3"));
        assertTrue(store.exists("players/p3"));
    }

    @Test
    void existsReturnsFalseForAbsentPath() throws Exception {
        assertFalse(store.exists("players/nosuchpath"));
    }

    @Test
    void saveOverwritesExistingEntry() throws Exception {
        store.save("players/p4", Map.of("name", "Before"));
        store.save("players/p4", Map.of("name", "After"));
        assertEquals("After", store.load("players/p4").orElseThrow().get("name"));
    }

    // =========================================================================
    // Via ModelRepository — flat-map path (no TableRegistry entry)
    // =========================================================================

    @Test
    void repoSaveAndFindRoundTrip() throws Exception {
        repo.save(model("r1", "Alice"));
        final Optional<TestModel> found = repo.find("r1");
        assertTrue(found.isPresent());
        assertEquals("Alice", found.get().get("name"));
    }

    @Test
    void repoFindReturnsEmptyWhenNotSaved() throws Exception {
        assertFalse(repo.find("nobody").isPresent());
    }

    @Test
    void repoDeleteRemovesModel() throws Exception {
        repo.save(model("r2", "Bob"));
        repo.delete("r2");
        assertFalse(repo.find("r2").isPresent());
    }

    @Test
    void repoExistsReturnsTrueAfterSave() throws Exception {
        repo.save(model("r3", "Carol"));
        assertTrue(repo.exists("r3"));
    }

    @Test
    void repoExistsReturnsFalseForUnknownId() throws Exception {
        assertFalse(repo.exists("nobody"));
    }

    @Test
    void repoExistsReturnsFalseAfterDelete() throws Exception {
        repo.save(model("r4", "Dan"));
        repo.delete("r4");
        assertFalse(repo.exists("r4"));
    }

    @Test
    void repoSaveOverwritesExistingModel() throws Exception {
        repo.save(model("r5", "Before"));
        repo.save(model("r5", "After"));
        assertEquals("After", repo.find("r5").orElseThrow().get("name"));
    }

    // =========================================================================
    // query() via ModelRepository (QueryableStorage path)
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
    void queryReturnsEmptyWhenNoModelsMatch() throws Exception {
        repo.save(model("qe1", "Existing"));
        final List<TestModel> result = repo.query(
            new QueryBuilder().whereEquals("name", "NoSuchName").build()
        );
        assertTrue(result.isEmpty());
    }

    @Test
    void queryReturnsEmptyListFromEmptyStore() throws Exception {
        final List<TestModel> result = repo.query(Model.queryBuilder().build());
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    // =========================================================================
    // clear()
    // =========================================================================

    @Test
    void clearEmptiesAllEntries() throws Exception {
        repo.save(model("c1", "Alice"));
        repo.save(model("c2", "Bob"));
        store.clear();
        assertFalse(repo.exists("c1"));
        assertFalse(repo.exists("c2"));
    }

    @Test
    void clearOnEmptyStoreIsNoOp() {
        assertDoesNotThrow(() -> store.clear());
    }
}
