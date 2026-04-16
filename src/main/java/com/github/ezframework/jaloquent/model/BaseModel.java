package com.github.ezframework.jaloquent.model;

import java.util.Map;

/**
 * Base class for storage-backed domain objects.
 *
 * <p>The {@code id} is the primary key used by repositories to build storage
 * paths.  Subclasses must implement {@link #toMap()} and {@link #fromMap(Map)}
 * to describe how the model's state is serialized to and from a flat map.
 */
public abstract class BaseModel {

    /** Primary identifier for this model. */
    protected String id;

    /**
     * Construct a model with the given id.
     *
     * @param id model identifier
     */
    protected BaseModel(String id) {
        this.id = id;
    }

    /**
     * Return the model identifier.
     *
     * @return model id
     */
    public String getId() {
        return id;
    }

    /**
     * Set the model identifier.
     *
     * @param id model id
     */
    public void setId(String id) {
        this.id = id;
    }

    /**
     * Build the storage path for this model given a repository prefix.
     *
     * @param prefix repository prefix (may be null or empty)
     * @return {@code prefix/id} when prefix is non-empty, otherwise {@code id}
     */
    public String getStoragePath(String prefix) {
        if (prefix == null || prefix.isEmpty()) {
            return id;
        }
        return prefix + "/" + id;
    }

    /**
     * Serialize this model's state to a flat map suitable for
     * {@link com.github.ezframework.jaloquent.store.DataStore#save}.
     *
     * @return map of attribute names to values
     */
    public abstract Map<String, Object> toMap();

    /**
     * Populate this model's state from a flat map loaded from storage.
     *
     * @param map source attributes (may be null)
     */
    public abstract void fromMap(Map<String, Object> map);
}
