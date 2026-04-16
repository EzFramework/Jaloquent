package com.github.ezframework.jaloquent.model;

import java.util.Map;

/**
 * Factory for instantiating {@link BaseModel} subclasses from persisted data.
 *
 * <p>Typically implemented as a lambda or method reference:
 * <pre>{@code
 * ModelFactory<PlayerData> factory = (id, data) -> {
 *     PlayerData p = new PlayerData(id);
 *     p.fromMap(data);
 *     return p;
 * };
 * }</pre>
 *
 * @param <T> the model type
 */
@FunctionalInterface
public interface ModelFactory<T extends BaseModel> {

    /**
     * Instantiate a model for the given id and persisted data.
     *
     * @param id   model identifier
     * @param data persisted attribute values
     * @return new model instance
     */
    T create(String id, Map<String, Object> data);
}
