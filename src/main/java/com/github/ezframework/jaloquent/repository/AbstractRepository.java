package com.github.ezframework.jaloquent.repository;

import com.github.ezframework.jaloquent.config.JaloquentConfig;
import com.github.ezframework.jaloquent.store.DataStore;
import io.micrometer.core.instrument.Counter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;

/**
 * Convenience base implementation of {@link Repository} backed by a
 * {@link DataStore}.
 *
 * <p>Subclasses must supply:
 * <ul>
 *   <li>{@link #toMap(Object)} — serialize an entity to a flat map
 *   <li>{@link #fromMap(Map)} — reconstruct an entity from a flat map
 *   <li>{@link #extractId(Object)} — extract the entity's identifier
 * </ul>
 *
 * <p>{@link #findAll()} returns an empty list by default; override it when the
 * underlying store supports key enumeration.
 *
 * @param <T>  the entity type
 * @param <ID> the identifier type
 */
public abstract class AbstractRepository<T, ID> implements Repository<T, ID> {

    /**
     * Get the logger for this class.
     * @return the logger or null
     */
    private static Logger logger() {
        return JaloquentConfig.getLogger(AbstractRepository.class);
    }

    /**
     * Get the save counter for metrics.
     * @return the counter or null
     */
    private static Counter saveCounter() {
        return JaloquentConfig.isMetricsEnabled() && JaloquentConfig.getMeterRegistry() != null
            ? Counter.builder("jaloquent.abstractrepository.save").register(JaloquentConfig.getMeterRegistry())
            : null;
    }

    /**
     * Get the delete counter for metrics.
     * @return the counter or null
     */
    private static Counter deleteCounter() {
        return JaloquentConfig.isMetricsEnabled() && JaloquentConfig.getMeterRegistry() != null
            ? Counter.builder("jaloquent.abstractrepository.delete").register(JaloquentConfig.getMeterRegistry())
            : null;
    }

    /** The backing data store. */
    private final DataStore store;

    /** The path prefix for storage. */
    private final String prefix;

    /**
     * Create a new repository.
     *
     * @param store  the backing store (must not be null)
     * @param prefix optional path prefix; {@code null} is treated as empty
     */
    protected AbstractRepository(DataStore store, String prefix) {
        this.store  = Objects.requireNonNull(store, "store");
        this.prefix = (prefix == null) ? "" : prefix;
    }

    /**
     * Access the configured {@link DataStore}.
     *
     * @return the store
     */
    protected DataStore store() {
        return store;
    }

    /**
     * Build a storage path for the given id using the repository prefix.
     *
     * @param id the identifier
     * @return storage path ({@code prefix + id})
     */
    protected String pathFor(ID id) {
        return prefix + id.toString();
    }

    /**
     * Serialize an entity to a flat map for storage.
     *
     * @param entity the entity to serialize
     * @return map suitable for {@link DataStore#save}
     */
    protected abstract Map<String, Object> toMap(T entity);

    /**
     * Reconstruct an entity from a flat map loaded from storage.
     *
     * @param map stored values
     * @return a reconstructed entity instance
     */
    protected abstract T fromMap(Map<String, Object> map);

    /**
     * Extract the identifier from an entity instance.
     *
     * @param entity the entity to inspect
     * @return the entity's identifier
     */
    protected abstract ID extractId(T entity);

    @Override
    public Optional<T> find(ID id) throws Exception {
        try {
            final Optional<T> result = store.load(pathFor(id)).map(this::fromMap);
            final Logger log = logger();
            if (log != null) {
                log.info("Find entity {}: {}", id, result.isPresent() ? "found" : "not found");
            }
            return result;
        }
        catch (Exception e) {
            final Logger log = logger();
            if (log != null) {
                log.error("Failed to find entity {}: {}", id, e.getMessage(), e);
            }
            throw e;
        }
    }

    @Override
    public List<T> findAll() throws Exception {
        return new ArrayList<>();
    }

    @Override
    public void save(T entity) throws Exception {
        try {
            store.save(pathFor(extractId(entity)), toMap(entity));
            final Counter c = saveCounter();
            if (c != null) {
                c.increment();
            }
            final Logger log = logger();
            if (log != null) {
                log.info("Saved entity {}", extractId(entity));
            }
        }
        catch (Exception e) {
            final Logger log = logger();
            if (log != null) {
                log.error("Failed to save entity {}: {}", extractId(entity), e.getMessage(), e);
            }
            throw e;
        }
    }

    @Override
    public void delete(ID id) throws Exception {
        try {
            store.delete(pathFor(id));
            final Counter c = deleteCounter();
            if (c != null) {
                c.increment();
            }
            final Logger log = logger();
            if (log != null) {
                log.info("Deleted entity {}", id);
            }
        }
        catch (Exception e) {
            final Logger log = logger();
            if (log != null) {
                log.error("Failed to delete entity {}: {}", id, e.getMessage(), e);
            }
            throw e;
        }
    }
}
