package com.github.ezframework.jaloquent.store;

import com.github.ezframework.javaquerybuilder.query.Query;
import com.github.ezframework.javaquerybuilder.query.QueryableStorage;
import com.github.ezframework.javaquerybuilder.query.condition.ConditionEntry;
import com.github.ezframework.javaquerybuilder.query.condition.Connector;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Built-in in-memory flat-map store for Jaloquent repositories.
 *
 * <p>Backed by a {@link ConcurrentHashMap} and implements both {@link DataStore}
 * and {@link QueryableStorage} so that
 * {@link com.github.ezframework.jaloquent.model.ModelRepository} can evaluate
 * {@link com.github.ezframework.javaquerybuilder.query.Query} objects
 * in-process without a database.
 *
 * <h2>Quick start (no custom store code needed)</h2>
 * <pre>{@code
 * InMemoryDataStore store = new InMemoryDataStore();
 * ModelRepository<Player> repo = new ModelRepository<>(store, "players", Player::new);
 * repo.save(player);
 * List<Player> found = repo.query(Model.queryBuilder().whereEquals("active", true).build());
 * }</pre>
 *
 * <h2>Query support</h2>
 * <p>Implements {@link QueryableStorage#query(Query)} using the condition
 * evaluation built into the {@code java-query-builder} library
 * ({@link com.github.ezframework.javaquerybuilder.query.condition.Condition#matches}).
 * Supports the same operators as the SQL path: {@code EQ}, {@code NEQ},
 * {@code GT}, {@code GTE}, {@code LT}, {@code LTE}, {@code LIKE},
 * {@code IS_NULL}, {@code IS_NOT_NULL}, {@code IN}, {@code NOT_IN}.
 * When a query has no conditions every stored entry is returned (up to the
 * configured limit, if any).
 *
 * <h2>Thread safety</h2>
 * <p>The backing {@link ConcurrentHashMap} provides safe concurrent access for
 * individual operations. Multi-step compound operations are not atomic.
 *
 * <h2>Persistence</h2>
 * <p>All data lives in the JVM heap and is discarded when the instance is
 * garbage-collected or {@link #clear()} is called. This store is intentionally
 * scoped to tests and development. For production, use
 * {@link com.github.ezframework.jaloquent.store.sql.DataSourceJdbcStore}.
 */
public class InMemoryDataStore implements DataStore, QueryableStorage {

    /**
     * Backing store keyed by path.
     */
    private final ConcurrentHashMap<String, Map<String, Object>> store = new ConcurrentHashMap<>();

    /**
     * Create an empty in-memory store.
     */
    public InMemoryDataStore() { }

    // =========================================================================
    // DataStore
    // =========================================================================

    /**
     * Save a defensive copy of {@code data} at {@code path}.
     *
     * @param path storage path
     * @param data values to persist
     */
    @Override
    public void save(String path, Map<String, Object> data) {
        store.put(path, new HashMap<>(data));
    }

    /**
     * Load the map stored at {@code path}.
     *
     * @param path storage path
     * @return optional map; empty when {@code path} has no entry
     */
    @Override
    public Optional<Map<String, Object>> load(String path) {
        return Optional.ofNullable(store.get(path));
    }

    /**
     * Remove the entry at {@code path}. No-op when the path does not exist.
     *
     * @param path storage path
     */
    @Override
    public void delete(String path) {
        store.remove(path);
    }

    /**
     * Return whether an entry exists at {@code path}.
     *
     * @param path storage path
     * @return {@code true} if an entry exists
     */
    @Override
    public boolean exists(String path) {
        return store.containsKey(path);
    }

    // =========================================================================
    // QueryableStorage
    // =========================================================================

    /**
     * Evaluate the query conditions against every stored entry and return the
     * matching model IDs.
     *
     * <p>The model ID is extracted from the stored path as the segment after
     * the last {@code /}, or the full path when no {@code /} is present.
     * Condition connectors ({@code AND}/{@code OR}) are evaluated left-to-right.
     * The query {@code limit} is honoured when set.
     *
     * @param query query containing conditions, optional limit
     * @return list of IDs of entries that satisfy all conditions
     * @throws Exception if condition evaluation fails unexpectedly
     */
    @Override
    public List<String> query(Query query) throws Exception {
        final List<ConditionEntry> conditions = query.getConditions();
        final Integer rawLimit = query.getLimit();
        // QueryBuilder uses -1 as the "no limit" sentinel; treat anything < 0 as unlimited
        final Integer limit = (rawLimit != null && rawLimit >= 0) ? rawLimit : null;
        final List<String> results = new ArrayList<>();
        for (final Map.Entry<String, Map<String, Object>> entry : store.entrySet()) {
            if (matchesConditions(entry.getValue(), conditions)) {
                final String path = entry.getKey();
                final int slash = path.lastIndexOf('/');
                final String id = slash >= 0 ? path.substring(slash + 1) : path;
                results.add(id);
                if (limit != null && results.size() >= limit) {
                    break;
                }
            }
        }
        return results;
    }

    // =========================================================================
    // Convenience
    // =========================================================================

    /**
     * Remove all entries from the store.
     *
     * <p>Intended for use in test teardown to prevent state leakage between tests.
     */
    public void clear() {
        store.clear();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Evaluate a list of conditions against a data row using left-to-right
     * AND/OR connector logic.
     *
     * @param row        the data row to test
     * @param conditions conditions to evaluate; empty or null means match-all
     * @return {@code true} when the row satisfies the combined conditions
     */
    private boolean matchesConditions(Map<String, Object> row, List<ConditionEntry> conditions) {
        if (conditions == null || conditions.isEmpty()) {
            return true;
        }
        boolean result = false;
        boolean first = true;
        for (final ConditionEntry entry : conditions) {
            final boolean matches = entry.getCondition().matches(row, entry.getColumn());
            if (first) {
                result = matches;
                first = false;
            }
            else if (entry.getConnector() == Connector.OR) {
                result = result || matches;
            }
            else {
                result = result && matches;
            }
        }
        return result;
    }
}
