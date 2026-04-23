package com.github.ezframework.jaloquent.model;

import com.github.ezframework.jaloquent.config.JaloquentConfig;
import com.github.ezframework.jaloquent.exception.StorageException;
import com.github.ezframework.jaloquent.relation.BelongsTo;
import com.github.ezframework.jaloquent.relation.BelongsToMany;
import com.github.ezframework.jaloquent.relation.HasMany;
import com.github.ezframework.jaloquent.relation.HasOne;
import com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Eloquent-style base model.
 *
 * <p>Attributes are stored in a flat map.  Fillable and guarded rules control
 * which keys can be mass-assigned via {@link #fill(Map)}.  The {@code id} key
 * is always excluded from mass-assignment regardless of those rules.
 *
 * <p>Example:
 * <pre>{@code
 * public class PlayerData extends Model {
 *     public PlayerData(String id) { super(id); }
 *     public int getCoins() { return getAs("coins", Integer.class, 0); }
 *     public void setCoins(int c) { set("coins", c); }
 * }
 * }</pre>
 */
public abstract class Model extends BaseModel {

    /**
     * Attribute storage map.
     */
    private final Map<String, Object> attributes = new HashMap<>();

    /**
     * Fillable/guarded rule manager.
     */
    private final Fillable fillableGuard = new Fillable();

    /**
     * Construct a model with the given id.
     *
     * @param id model identifier
     */
    protected Model(String id) {
        super(id);
    }

    /**
     * Construct a model with the given id and initial attributes.
     * The attributes are applied via {@link #fill(Map)}, respecting
     * fillable/guarded rules.
     *
     * @param id    model identifier
     * @param attrs initial attributes (may be null)
     */
    protected Model(String id, Map<String, Object> attrs) {
        super(id);
        if (attrs != null) {
            fill(attrs);
        }
    }

    /**
     * Set the list of mass-assignable attribute keys.
     * When non-empty only these keys are accepted by {@link #fill(Map)}.
     *
     * @param keys attribute names to mark as fillable
     * @return this model for chaining
     */
    public Model setFillable(String... keys) {
        fillableGuard.setFillable(keys);
        return this;
    }

    /**
     * Set the list of guarded (non-assignable) attribute keys.
     * Guarded keys are skipped by {@link #fill(Map)}.
     *
     * @param keys attribute names to mark as guarded
     * @return this model for chaining
     */
    public Model setGuarded(String... keys) {
        fillableGuard.setGuarded(keys);
        return this;
    }

    /**
     * Return an unmodifiable view of the fillable key set.
     *
     * @return fillable keys
     */
    public Set<String> getFillable() {
        return fillableGuard.getFillable();
    }

    /**
     * Return an unmodifiable view of the guarded key set.
     *
     * @return guarded keys
     */
    public Set<String> getGuarded() {
        return fillableGuard.getGuarded();
    }

    /**
     * Determine whether a key may be mass-assigned via {@link #fill(Map)}.
     *
     * @param key attribute name
     * @return true if the key is fillable
     */
    protected boolean isFillable(String key) {
        return fillableGuard.isFillable(key);
    }

    /**
     * Set a single attribute.  When {@code key} is {@code "id"} the model id
     * is updated instead.
     *
     * @param key   attribute name
     * @param value attribute value
     * @return this model for chaining
     */
    public Model set(String key, Object value) {
        if ("id".equals(key)) {
            setId(value == null ? null : value.toString());
        }
        else {
            attributes.put(key, value);
        }
        return this;
    }

    /**
     * Get the raw value of an attribute.
     *
     * @param key attribute name
     * @return attribute value, or {@code null} when not present
     */
    public Object get(String key) {
        if ("id".equals(key)) {
            return getId();
        }
        return attributes.get(key);
    }

    /**
     * Get an attribute coerced to the requested type.
     *
     * <p>Simple numeric conversions between {@code Integer}, {@code Long}, and
     * {@code String} are applied automatically.
     *
     * @param key attribute name
     * @param cls target type
     * @param <T> target type parameter
     * @return coerced value, or {@code null} when not present or not convertible
     */
    @SuppressWarnings("unchecked")
    public <T> T getAs(String key, Class<T> cls) {
        final Object v = get(key);
        if (v == null) {
            return null;
        }
        if (cls.isInstance(v)) {
            return (T) v;
        }
        if (cls == Integer.class && v instanceof Number) {
            return (T) Integer.valueOf(((Number) v).intValue());
        }
        if (cls == Long.class && v instanceof Number) {
            return (T) Long.valueOf(((Number) v).longValue());
        }
        if (cls == String.class) {
            return (T) v.toString();
        }
        return null;
    }

    /**
     * Get an attribute coerced to the requested type, returning a default
     * when not present or not convertible.
     *
     * @param key attribute name
     * @param cls target type
     * @param def fallback value
     * @param <T> target type parameter
     * @return coerced value or {@code def}
     */
    public <T> T getAs(String key, Class<T> cls, T def) {
        final T v = getAs(key, cls);
        return v == null ? def : v;
    }

    /**
     * Mass-assign attributes.  Only keys permitted by the fillable/guarded
     * rules are applied; all others are silently skipped.
     *
     * @param attrs attributes to apply (may be null)
     * @return this model for chaining
     */
    public Model fill(Map<String, Object> attrs) {
        if (attrs == null) {
            return this;
        }
        for (final Map.Entry<String, Object> e : attrs.entrySet()) {
            final String k = e.getKey();
            if (k == null) {
                continue;
            }
            if (!isFillable(k)) {
                continue;
            }
            set(k, e.getValue());
        }
        return this;
    }

    /**
     * Update this model's attributes using only explicitly fillable columns.
     *
     * <p>Unlike {@link #fill(Map)}, which accepts all non-guarded keys when
     * no fillable set is declared, {@code update} applies strict fillable
     * rules: only keys that have been explicitly declared via
     * {@link #setFillable(String...)} are written to the model.  Keys that
     * are not in the fillable set are silently ignored.
     *
     * @param data attributes to apply (may be null)
     * @return this model for chaining
     */
    public Model update(Map<String, Object> data) {
        if (data == null) {
            return this;
        }
        for (final Map.Entry<String, Object> e : data.entrySet()) {
            final String k = e.getKey();
            if (k == null) {
                continue;
            }
            if (!fillableGuard.isExplicitlyFillable(k)) {
                continue;
            }
            set(k, e.getValue());
        }
        return this;
    }

    /**
     * Return an unmodifiable view of the current attributes map.
     *
     * @return attribute snapshot
     */
    public Map<String, Object> attributes() {
        return Collections.unmodifiableMap(attributes);
    }

    @Override
    public Map<String, Object> toMap() {
        return new HashMap<>(attributes);
    }

    @Override
    public void fromMap(Map<String, Object> map) {
        attributes.clear();
        if (map == null) {
            return;
        }
        for (final Map.Entry<String, Object> e : map.entrySet()) {
            if ("id".equals(e.getKey())) {
                if (e.getValue() != null) {
                    setId(e.getValue().toString());
                }
            }
            else {
                attributes.put(e.getKey(), e.getValue());
            }
        }
    }

    /**
     * Return a new {@link QueryBuilder} for building queries against a
     * {@link ModelRepository}.
     *
     * @return new QueryBuilder instance
     */
    public static QueryBuilder queryBuilder() {
        return new QueryBuilder();
    }

    /**
     * Persist this model using the given repository.
     *
     * @param repo target repository
     * @param <T>  model type
     * @return this model for chaining
    * @throws StorageException when save fails
     */
    @SuppressWarnings("unchecked")
    public <T extends Model> T save(ModelRepository<T> repo) throws StorageException {
        try {
            repo.save((T) this);
            JaloquentConfig.incrementCounter("jaloquent.model.save");
            JaloquentConfig.logInfo(Model.class, "Model saved: {}", getId());
            return (T) this;
        }
        catch (Exception e) {
            throw new StorageException("Failed to save model: " + getId(), e);
        }
    }

    /**
     * Delete this model from the given repository.
     *
     * @param repo target repository
     * @param <T>  model type
    * @throws StorageException when delete fails
     */
    public <T extends Model> void delete(ModelRepository<T> repo) throws StorageException {
        try {
            repo.delete(getId());
            JaloquentConfig.incrementCounter("jaloquent.model.delete");
            JaloquentConfig.logInfo(Model.class, "Model deleted: {}", getId());
        }
        catch (Exception e) {
            throw new StorageException("Failed to delete model: " + getId(), e);
        }
    }

    /**
     * Find a model by id.
     *
     * @param repo target repository
     * @param id   model id to look up
     * @param <T>  model type
     * @return model instance or {@code null} when not found
    * @throws StorageException when find fails
     */
    public static <T extends Model> T find(ModelRepository<T> repo, String id) throws StorageException {
        try {
            return repo.find(id).orElse(null);
        }
        catch (Exception e) {
            throw new StorageException("Failed to find model: " + id, e);
        }
    }

    /**
     * Define a one-to-one relationship where the foreign key lives on the related model.
     *
     * @param related    the related model repository
     * @param foreignKey column on the related model that stores this model's ID
     * @param <R>        the related model type
     * @return a HasOne relationship instance
     */
    protected <R extends BaseModel> HasOne<R> hasOne(ModelRepository<R> related, String foreignKey) {
        return new HasOne<>(related, Model.queryBuilder().whereEquals(foreignKey, getId()));
    }

    /**
     * Define a one-to-one relationship with a custom local key.
     *
     * @param related    the related model repository
     * @param foreignKey column on the related model that stores the local key value
     * @param localKey   the attribute on this model used as the anchor value
     * @param <R>        the related model type
     * @return a HasOne relationship instance
     */
    protected <R extends BaseModel> HasOne<R> hasOne(
            ModelRepository<R> related, String foreignKey, String localKey) {
        final Object localValue = get(localKey);
        return new HasOne<>(related, Model.queryBuilder().whereEquals(foreignKey, localValue));
    }

    /**
     * Define a one-to-many relationship where the foreign key lives on the related model.
     *
     * @param related    the related model repository
     * @param foreignKey column on the related model that stores this model's ID
     * @param <R>        the related model type
     * @return a HasMany relationship instance
     */
    protected <R extends BaseModel> HasMany<R> hasMany(ModelRepository<R> related, String foreignKey) {
        return new HasMany<>(related, Model.queryBuilder().whereEquals(foreignKey, getId()));
    }

    /**
     * Define a one-to-many relationship with a custom local key.
     *
     * @param related    the related model repository
     * @param foreignKey column on the related model that stores the local key value
     * @param localKey   the attribute on this model used as the anchor value
     * @param <R>        the related model type
     * @return a HasMany relationship instance
     */
    protected <R extends BaseModel> HasMany<R> hasMany(
            ModelRepository<R> related, String foreignKey, String localKey) {
        final Object localValue = get(localKey);
        return new HasMany<>(related, Model.queryBuilder().whereEquals(foreignKey, localValue));
    }

    /**
     * Define the inverse of a hasOne or hasMany relationship.
     * The foreign key lives on this model and references the related model's primary key.
     *
     * @param related    the related model repository
     * @param foreignKey attribute on this model that stores the related model's ID
     * @param <R>        the related model type
     * @return a BelongsTo relationship instance
     */
    protected <R extends BaseModel> BelongsTo<R> belongsTo(
            ModelRepository<R> related, String foreignKey) {
        final Object fkValue = get(foreignKey);
        return new BelongsTo<>(related, fkValue == null ? null : fkValue.toString());
    }

    /**
     * Define a many-to-many relationship mediated by a pivot model.
     *
     * @param related      the related model repository
     * @param pivotRepo    the pivot model repository
     * @param pivotFactory factory for creating new pivot model instances (used by attach)
     * @param foreignKey   column in the pivot table storing this model's ID
     * @param relatedKey   column in the pivot table storing the related model's ID
     * @param <R>          the related model type
     * @param <P>          the pivot model type
     * @return a BelongsToMany relationship instance
     */
    protected <R extends BaseModel, P extends BaseModel> BelongsToMany<R, P> belongsToMany(
            ModelRepository<R> related,
            ModelRepository<P> pivotRepo,
            ModelFactory<P> pivotFactory,
            String foreignKey,
            String relatedKey) {
        return new BelongsToMany<>(related, pivotRepo, pivotFactory, getId(), foreignKey, relatedKey);
    }
}
