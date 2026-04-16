package com.github.ezframework.jaloquent.model;

import com.github.ezframework.jaloquent.config.JaloquentConfig;
import com.github.ezframework.jaloquent.exception.StorageException;
import com.github.ezframework.jaloquent.model.TableRegistry.TableMeta;
import com.github.ezframework.jaloquent.store.DataStore;
import com.github.ezframework.jaloquent.store.sql.JdbcStore;
import com.github.ezframework.javaquerybuilder.query.Query;
import com.github.ezframework.javaquerybuilder.query.QueryableStorage;
import com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder;
import com.github.ezframework.javaquerybuilder.query.builder.SelectBuilder;
import com.github.ezframework.javaquerybuilder.query.sql.SqlDialect;
import com.github.ezframework.javaquerybuilder.query.sql.SqlResult;
import io.micrometer.core.instrument.Counter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;

/**
 * Generic repository for {@link BaseModel} instances backed by a {@link DataStore}.
 *
 * <p>When the prefix has a {@link TableRegistry} entry <em>and</em> the store
 * also implements {@link JdbcStore}, all operations use parameterized SQL.
 * The SQL dialect defaults to {@link SqlDialect#STANDARD}; pass a different
 * dialect at construction time if needed (e.g. {@code SqlDialect.MYSQL}).
 *
 * <p>For the {@link #query(Query)} method the supplied {@link Query} is
 * rendered to a parameterized SQL string via the configured dialect, then
 * executed directly — no manual WHERE-clause construction required.
 *
 * <p>When no SQL table is registered but the store implements
 * {@link QueryableStorage}, {@link #query(Query)} falls back to the
 * store's in-memory filter and loads results individually. When neither
 * condition holds, an empty list is returned.
 *
 * @param <T> model type
 */
public class ModelRepository<T extends BaseModel> {

    /**
     * Get the logger for this class.
     * @return the logger or null
     */
    private static Logger logger() {
        return JaloquentConfig.getLogger(ModelRepository.class);
    }

    /**
     * Get the save counter for metrics.
     * @return the counter or null
     */
    private static Counter saveCounter() {
        return JaloquentConfig.isMetricsEnabled() && JaloquentConfig.getMeterRegistry() != null
            ? Counter.builder("jaloquent.repository.save").register(JaloquentConfig.getMeterRegistry())
            : null;
    }

    /**
     * Get the delete counter for metrics.
     * @return the counter or null
     */
    private static Counter deleteCounter() {
        return JaloquentConfig.isMetricsEnabled() && JaloquentConfig.getMeterRegistry() != null
            ? Counter.builder("jaloquent.repository.delete").register(JaloquentConfig.getMeterRegistry())
            : null;
    }

    /**
     * Get the query counter for metrics.
     * @return the counter or null
     */
    private static Counter queryCounter() {
        return JaloquentConfig.isMetricsEnabled() && JaloquentConfig.getMeterRegistry() != null
            ? Counter.builder("jaloquent.repository.query").register(JaloquentConfig.getMeterRegistry())
            : null;
    }

    /** The backing data store. */
    /** The backing data store. */
    private final DataStore store;

    /** The path prefix for storage. */
    private final String prefix;

    /** The model factory. */
    private final ModelFactory<T> factory;

    /** The SQL dialect for query rendering. */
    private final SqlDialect dialect;

    /**
     * Create a repository using {@link SqlDialect#STANDARD} for SQL generation.
     *
     * @param store   backing store
     * @param prefix  repository prefix (may be null)
     * @param factory model factory
     */
    public ModelRepository(DataStore store, String prefix, ModelFactory<T> factory) {
        this(store, prefix, factory, SqlDialect.STANDARD);
    }

    /**
     * Create a repository with an explicit SQL dialect.
     *
     * @param store   backing store
     * @param prefix  repository prefix (may be null)
     * @param factory model factory
     * @param dialect SQL dialect for query rendering
     */
    public ModelRepository(DataStore store, String prefix, ModelFactory<T> factory, SqlDialect dialect) {
        this.store   = store;
        this.prefix  = (prefix == null) ? "" : prefix;
        this.factory = factory;
        this.dialect = dialect;
    }

    private String storagePath(String id) {
        if (prefix.isEmpty()) {
            return id;
        }
        return prefix + "/" + id;
    }

    /**
     * Persist a model.  When a SQL table is registered and the store supports
     * JDBC, an upsert (INSERT … ON DUPLICATE KEY UPDATE) is issued; otherwise
     * the flat-map store is used.
     *
     * @param model model to persist
    * @throws StorageException on storage failure
    */
    public void save(T model) throws StorageException {
        try {
            final TableMeta meta = TableRegistry.get(prefix);
            if (meta != null && store instanceof JdbcStore) {
                final JdbcStore jdbc = (JdbcStore) store;
                final Map<String, Object> values = model.toMap();

                final StringBuilder updates = new StringBuilder();
                final var ib = QueryBuilder.insertInto(meta.tableName()).value("id", model.getId());
                for (final Map.Entry<String, String> col : meta.columns().entrySet()) {
                    final String cname = col.getKey();
                    if ("id".equals(cname)) {
                        continue;
                    }
                    ib.value(cname, values.get(cname));
                    if (updates.length() > 0) {
                        updates.append(',');
                    }
                    updates.append(cname).append("=VALUES(").append(cname).append(')');
                }
                final String onDupe = updates.length() > 0 ? updates.toString() : "id=id";
                final SqlResult insertResult = ib.build(dialect);
                final String sql = insertResult.getSql() + " ON DUPLICATE KEY UPDATE " + onDupe;
                jdbc.executeUpdate(sql, insertResult.getParameters());
                final Counter c = saveCounter();
                if (c != null) {
                    c.increment();
                }
                final Logger log = logger();
                if (log != null) {
                    log.info("Saved model {} to SQL table {}", model.getId(), meta.tableName());
                }
                return;
            }
            store.save(model.getStoragePath(prefix), model.toMap());
            final Counter c = saveCounter();
            if (c != null) {
                c.increment();
            }
            final Logger log = logger();
            if (log != null) {
                log.info("Saved model {} to flat-map store", model.getId());
            }
        }
        catch (Exception e) {
            final Logger log = logger();
            if (log != null) {
                log.error("Failed to save model {}: {}", model.getId(), e.getMessage(), e);
            }
            throw new StorageException("Failed to save model: " + model.getId(), e);
        }
    }

    /**
     * Find a model by id.
     *
     * @param id model identifier
     * @return optional model, empty when not found
     * @throws StorageException on storage failure
     */
    public Optional<T> find(String id) throws StorageException {
        try {
            final TableMeta meta = TableRegistry.get(prefix);
            if (meta != null && store instanceof JdbcStore) {
                final JdbcStore jdbc = (JdbcStore) store;
                final SqlResult findResult = new SelectBuilder()
                    .from(meta.tableName())
                    .whereEquals("id", id)
                    .limit(1)
                    .build(dialect);
                final List<Map<String, Object>> rows = jdbc.query(findResult.getSql(), findResult.getParameters());
                if (rows.isEmpty()) {
                    return Optional.empty();
                }
                final Map<String, Object> row = rows.get(0);
                final T m = factory.create(id, row);
                m.fromMap(row);
                return Optional.of(m);
            }
            return store.load(storagePath(id)).map(data -> {
                final T m = factory.create(id, data);
                m.fromMap(data);
                return m;
            });
        }
        catch (Exception e) {
            final Logger log = logger();
            if (log != null) {
                log.error("Failed to find model {}: {}", id, e.getMessage(), e);
            }
            throw new StorageException("Failed to find model: " + id, e);
        }
    }

    /**
     * Delete the model with the given id.
     *
     * @param id model identifier
    * @throws StorageException on storage failure
    */
    public void delete(String id) throws StorageException {
        try {
            final TableMeta meta = TableRegistry.get(prefix);
            if (meta != null && store instanceof JdbcStore) {
                final JdbcStore jdbc = (JdbcStore) store;
                final SqlResult deleteResult = QueryBuilder.deleteFrom(meta.tableName())
                    .whereEquals("id", id)
                    .build(dialect);
                jdbc.executeUpdate(deleteResult.getSql(), deleteResult.getParameters());
                final Counter c = deleteCounter();
                if (c != null) {
                    c.increment();
                }
                final Logger log = logger();
                if (log != null) {
                    log.info("Deleted model {} from SQL table {}", id, meta.tableName());
                }
                return;
            }
            store.delete(storagePath(id));
            final Counter c = deleteCounter();
            if (c != null) {
                c.increment();
            }
            final Logger log = logger();
            if (log != null) {
                log.info("Deleted model {} from flat-map store", id);
            }
        }
        catch (Exception e) {
            final Logger log = logger();
            if (log != null) {
                log.error("Failed to delete model {}: {}", id, e.getMessage(), e);
            }
            throw new StorageException("Failed to delete model: " + id, e);
        }
    }

    /**
     * Check whether a model with the given id exists.
     *
     * @param id model identifier
     * @return true when the model exists
     * @throws StorageException on storage failure
     */
    public boolean exists(String id) throws StorageException {
        try {
            final TableMeta meta = TableRegistry.get(prefix);
            if (meta != null && store instanceof JdbcStore) {
                final JdbcStore jdbc = (JdbcStore) store;
                final SqlResult existsResult = new SelectBuilder()
                    .from(meta.tableName())
                    .select("1")
                    .whereEquals("id", id)
                    .limit(1)
                    .build(dialect);
                final List<Map<String, Object>> rows = jdbc.query(existsResult.getSql(), existsResult.getParameters());
                return !rows.isEmpty();
            }
            return store.exists(storagePath(id));
        }
        catch (Exception e) {
            final Logger log = logger();
            if (log != null) {
                log.error("Failed to check existence of model {}: {}", id, e.getMessage(), e);
            }
            throw new StorageException("Failed to check existence of model: " + id, e);
        }
    }

    /**
     * Query for models matching the given {@link Query}.
     *
     * <p>SQL path: the query is rendered to a parameterized SQL string via the
     * configured {@link SqlDialect} and executed against the JDBC store.
     *
     * <p>In-memory path: when no SQL table is registered but the store
     * implements {@link QueryableStorage}, the store's in-memory filter returns
     * matching ids which are then loaded individually.
     *
     * @param q query built with
     *          {@link com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder}
     * @return matching models (may be empty); never null
    * @throws StorageException on storage or query errors
    */
    public List<T> query(Query q) throws StorageException {
        final List<T> out = new ArrayList<>();
        try {
            final TableMeta meta = TableRegistry.get(prefix);
            if (meta != null && store instanceof JdbcStore) {
                final JdbcStore jdbc = (JdbcStore) store;
                q.setTable(meta.tableName());
                final SqlResult result = dialect.render(q);
                final List<Map<String, Object>> rows = jdbc.query(result.getSql(), result.getParameters());
                for (final Map<String, Object> row : rows) {
                    final Object idVal = row.get("id");
                    final String idStr = idVal == null ? null : idVal.toString();
                    final T m = factory.create(idStr, row);
                    m.fromMap(row);
                    out.add(m);
                }
                final Counter c = queryCounter();
                if (c != null) {
                    c.increment();
                }
                final Logger log = logger();
                if (log != null) {
                    log.info("Queried {} rows from SQL table {}", out.size(), meta.tableName());
                }
                return out;
            }

            if (store instanceof QueryableStorage) {
                final List<String> ids = ((QueryableStorage) store).query(q);
                for (final String id : ids) {
                    find(id).ifPresent(out::add);
                }
                final Counter c = queryCounter();
                if (c != null) {
                    c.increment();
                }
                final Logger log = logger();
                if (log != null) {
                    log.info("Queried {} rows from flat-map store", out.size());
                }
                return out;
            }

            return out;
        }
        catch (final Exception e) {
            final Logger log = logger();
            if (log != null) {
                log.error("Failed to query repository: {}", e.getMessage(), e);
            }
            throw new StorageException("Failed to query repository", e);
        }
    }

    /**
     * Delete all records where the given column equals the given value.
     *
     * <p>On the SQL path (when a {@link TableRegistry} entry exists and the store
     * implements {@link JdbcStore}) a single parameterised
     * {@code DELETE FROM table WHERE column = ?} statement is issued with no prior SELECT.
     *
     * <p>On the flat-map path, records matching the condition are loaded via
     * {@link #query(Query)} (requires the store to implement
     * {@link QueryableStorage}) and then deleted individually.
     *
     * @param column the column name to filter on
     * @param value  the value the column must equal
    * @throws StorageException on storage failure
    */
    public void deleteWhere(String column, Object value) throws StorageException {
        try {
            final TableMeta meta = TableRegistry.get(prefix);
            if (meta != null && store instanceof JdbcStore) {
                final JdbcStore jdbc = (JdbcStore) store;
                final SqlResult deleteWhereResult = QueryBuilder.deleteFrom(meta.tableName())
                    .whereEquals(column, value)
                    .build(dialect);
                jdbc.executeUpdate(deleteWhereResult.getSql(), deleteWhereResult.getParameters());
                final Counter c = deleteCounter();
                if (c != null) {
                    c.increment();
                }
                final Logger log = logger();
                if (log != null) {
                    log.info("Bulk-deleted from SQL table {} where {}={}", meta.tableName(), column, value);
                }
                return;
            }
            final List<T> matching = query(new QueryBuilder().whereEquals(column, value).build());
            for (final T m : matching) {
                store.delete(storagePath(m.getId()));
            }
            final Counter c = deleteCounter();
            if (c != null) {
                c.increment();
            }
        }
        catch (Exception e) {
            final Logger log = logger();
            if (log != null) {
                log.error("Failed to deleteWhere {}={}: {}", column, value, e.getMessage(), e);
            }
            throw new StorageException("Failed to deleteWhere: " + column + "=" + value, e);
        }
    }

    /**
     * Delete all records with the given primary key ids in a single bulk operation.
     *
     * <p>On the SQL path a single parameterised
     * {@code DELETE FROM table WHERE id IN (?, …)} statement is issued.
     * On the flat-map path each id is deleted individually.
     * When {@code ids} is {@code null} or empty this method is a no-op.
     *
     * @param ids record identifiers to delete ({@code null} or empty is a no-op)
    * @throws StorageException on storage failure
    */
    public void deleteAll(List<String> ids) throws StorageException {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        try {
            final TableMeta meta = TableRegistry.get(prefix);
            if (meta != null && store instanceof JdbcStore) {
                final JdbcStore jdbc = (JdbcStore) store;
                final SqlResult deleteAllResult = QueryBuilder.deleteFrom(meta.tableName())
                    .whereIn("id", new ArrayList<>(ids))
                    .build(dialect);
                jdbc.executeUpdate(deleteAllResult.getSql(), deleteAllResult.getParameters());
                final Counter c = deleteCounter();
                if (c != null) {
                    c.increment();
                }
                final Logger log = logger();
                if (log != null) {
                    log.info("Bulk-deleted {} records from SQL table {}", ids.size(), meta.tableName());
                }
                return;
            }
            for (final String id : ids) {
                store.delete(storagePath(id));
            }
            final Counter c = deleteCounter();
            if (c != null) {
                c.increment();
            }
        }
        catch (Exception e) {
            final Logger log = logger();
            if (log != null) {
                log.error("Failed to deleteAll: {}", e.getMessage(), e);
            }
            throw new StorageException("Failed to deleteAll", e);
        }
    }

}
