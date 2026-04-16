package com.github.ezframework.jaloquent.store;

import java.util.Map;
import java.util.Optional;

/**
 * Minimal persistence abstraction used by Jaloquent repositories.
 *
 * <p>Intentionally omits lifecycle methods (init/close) so that Jaloquent
 * remains decoupled from any platform-specific plugin bootstrapping.
 * Adapters wrapping platform providers (e.g. EzFramework's StorageProvider)
 * should implement this interface.
 */
public interface DataStore {

    /**
     * Save a flat map of values at the given path, overwriting any existing entry.
     *
     * @param path storage path
     * @param data values to persist
     * @throws Exception on failure
     */
    void save(String path, Map<String, Object> data) throws Exception;

    /**
     * Load the map stored at the given path.
     *
     * @param path storage path
     * @return optional map, empty when no entry exists at {@code path}
     * @throws Exception on failure
     */
    Optional<Map<String, Object>> load(String path) throws Exception;

    /**
     * Remove the entry at the given path. No-op when the path does not exist.
     *
     * @param path storage path
     * @throws Exception on failure
     */
    void delete(String path) throws Exception;

    /**
     * Return whether an entry exists at the given path.
     *
     * @param path storage path
     * @return {@code true} if an entry exists
     * @throws Exception on failure
     */
    boolean exists(String path) throws Exception;
}
