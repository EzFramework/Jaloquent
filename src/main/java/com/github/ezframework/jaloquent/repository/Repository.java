package com.github.ezframework.jaloquent.repository;

import java.util.List;
import java.util.Optional;

/**
 * Generic CRUD contract for domain repositories backed by a
 * {@link com.github.ezframework.jaloquent.store.DataStore}.
 *
 * @param <T>  the entity type
 * @param <ID> the identifier type
 */
public interface Repository<T, ID> {

    /**
     * Find an entity by id.
     *
     * @param id the id to look up
     * @return an optional containing the entity if found
     * @throws Exception on storage errors
     */
    Optional<T> find(ID id) throws Exception;

    /**
     * Retrieve all entities managed by this repository.
     *
     * @return list of all entities (may be empty)
     * @throws Exception on storage errors
     */
    List<T> findAll() throws Exception;

    /**
     * Persist or update an entity.
     *
     * @param entity the entity to save
     * @throws Exception on storage errors
     */
    void save(T entity) throws Exception;

    /**
     * Delete the entity with the given id.
     *
     * @param id the id of the entity to remove
     * @throws Exception on storage errors
     */
    void delete(ID id) throws Exception;
}
