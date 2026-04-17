package com.github.ezframework.jaloquent.model;

import com.github.ezframework.jaloquent.exception.StorageException;
import java.util.List;

/**
 * Fluent count wrapper returned by {@link Factory#count(int)}.
 *
 * <p>Holds a configured {@link Factory} and a model count so that
 * {@code make()} and {@code create()} always return a {@link List}:
 * <pre>{@code
 * List<Player> players = Player.factory().count(5).make();
 * List<Player> saved   = Player.factory().state(Map.of("role", "admin")).count(3).create(repo);
 * }</pre>
 *
 * <p>Instances are created via {@link Factory#count(int)} and should not be
 * constructed directly.
 *
 * @param <T> the model type produced by the factory
 */
public final class FactoryCount<T extends Model> {

    /** The underlying factory used to build each model. */
    private final Factory<T> factory;

    /** Number of models to produce. */
    private final int count;

    /**
     * Construct a count-configured factory wrapper.
     *
     * @param factory underlying factory
     * @param count   number of models to produce
     */
    FactoryCount(Factory<T> factory, int count) {
        this.factory = factory;
        this.count = count;
    }

    /**
     * Build {@code count} transient models populated with fake data.
     * None of the models are persisted.
     *
     * @return list of new model instances with distinct UUIDs
     */
    public List<T> make() {
        return factory.make(count);
    }

    /**
     * Build and persist {@code count} models to the given repository.
     *
     * @param repo repository to persist the models in
     * @return list of persisted models
     * @throws StorageException when persistence of any model fails
     */
    public List<T> create(ModelRepository<T> repo) throws StorageException {
        return factory.create(count, repo);
    }

}
