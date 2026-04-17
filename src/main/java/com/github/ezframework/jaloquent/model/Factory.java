package com.github.ezframework.jaloquent.model;

import com.github.ezframework.jaker.Faker;
import com.github.ezframework.jaloquent.exception.StorageException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Abstract base for Jaker-backed model factories, inspired by the Laravel Factory pattern.
 *
 * <p>Extend this class for each model type and implement {@link #definition(Faker)} to
 * return fake attribute values. The factory auto-discovers its model class from the
 * factory class name: {@code PlayerFactory} resolves to {@code Player} in the same
 * package or in a {@code factories} sub-package.
 *
 * <p>Example:
 * <pre>{@code
 * public class PlayerFactory extends Factory<Player> {
 *
 *     @Override
 *     protected Map<String, Object> definition(Faker faker) {
 *         return Map.of(
 *             "name",  faker.name().firstName(),
 *             "email", faker.internet().email("player")
 *         );
 *     }
 * }
 *
 * // Enable Player.factory() by implementing HasFactory:
 * public class Player extends Model implements HasFactory {
 *
 *     public static PlayerFactory factory() {
 *         return Factory.discover(Player.class);
 *     }
 * }
 *
 * // Usage:
 * Player p              = Player.factory().make();
 * List<Player> ten      = Player.factory().count(10).make();
 * Player admin          = Player.factory().state(Map.of("role", "admin")).make();
 * Player saved          = Player.factory().create(repo);
 * List<Player> batch    = Player.factory().count(5).create(repo);
 * }</pre>
 *
 * <p>Jaker must be present on the classpath. It is declared as an {@code optional}
 * dependency in Jaloquent, so add it explicitly to your own build:
 * <pre>{@code
 * <dependency>
 *   <groupId>com.github.EzFramework</groupId>
 *   <artifactId>jaker</artifactId>
 *   <version>1.0.0</version>
 * </dependency>
 * }</pre>
 *
 * @param <T> the model type produced by this factory
 */
public abstract class Factory<T extends Model> {

    /** Jaker instance used to generate fake attribute values. */
    private final Faker faker;

    /** Model class this factory produces. */
    private final Class<T> modelClass;

    /** Attribute overrides merged into {@link #definition(Faker)} output by {@link #state(Map)}. */
    private Map<String, Object> stateOverrides;

    /**
     * Create a factory backed by a non-deterministic {@code en-US} Jaker instance.
     * The model class is auto-discovered from this factory's class name by convention.
     */
    protected Factory() {
        this(Faker.builder().locale("en-US").build().faker());
    }

    /**
     * Create a factory with a custom Jaker instance.
     * The model class is auto-discovered from this factory's class name by convention.
     *
     * @param faker Jaker instance to use for generating fake values
     */
    protected Factory(Faker faker) {
        this.faker = faker;
        this.modelClass = resolveModelClass();
        this.stateOverrides = Collections.emptyMap();
    }

    /**
     * Create a factory with an explicit model class backed by a non-deterministic
     * {@code en-US} Jaker instance.
     * Use this constructor when the factory class name does not follow the naming convention.
     *
     * @param modelClass the model class this factory produces
     */
    protected Factory(Class<T> modelClass) {
        this(Faker.builder().locale("en-US").build().faker(), modelClass);
    }

    /**
     * Create a factory with an explicit model class and a custom Jaker instance.
     * Use this constructor when the factory class name does not follow the naming convention.
     *
     * @param faker      Jaker instance to use for generating fake values
     * @param modelClass the model class this factory produces
     */
    protected Factory(Faker faker, Class<T> modelClass) {
        this.faker = faker;
        this.modelClass = modelClass;
        this.stateOverrides = Collections.emptyMap();
    }

    /**
     * Return the fake attribute map for one model instance.
     *
     * @param faker Jaker instance for generating values
     * @return map of attribute key-value pairs
     */
    protected abstract Map<String, Object> definition(Faker faker);

    /**
     * Merge attribute overrides on top of the next {@link #definition(Faker)} call.
     * Multiple calls accumulate: later overrides win over earlier ones.
     * Returns {@code this} for chaining.
     *
     * @param overrides attributes to merge into the definition output
     * @return this factory for chaining
     */
    public Factory<T> state(Map<String, Object> overrides) {
        final Map<String, Object> merged = new HashMap<>(stateOverrides);
        merged.putAll(overrides);
        this.stateOverrides = merged;
        return this;
    }

    /**
     * Return a {@link FactoryCount} that produces exactly {@code count} models.
     * The returned object's {@code make()} and {@code create()} always return a {@link List}.
     *
     * @param count number of models to produce
     * @return a count-configured factory wrapper
     */
    public FactoryCount<T> count(int count) {
        return new FactoryCount<>(this, count);
    }

    /**
     * Build one transient model populated with fake data.
     * The model is not persisted to any store.
     *
     * @return new model instance with fake attributes and a random UUID id
     */
    public T make() {
        final String id = UUID.randomUUID().toString();
        final Map<String, Object> attrs = stateOverrides.isEmpty()
            ? definition(faker)
            : mergeWithState(definition(faker));
        return newModel(id, attrs);
    }

    /**
     * Build {@code count} transient models populated with fake data.
     * None of the models are persisted.
     *
     * @param count number of models to build
     * @return list of new model instances with distinct UUIDs
     */
    public List<T> make(int count) {
        final List<T> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(make());
        }
        return result;
    }

    /**
     * Build and persist one model to the given repository.
     *
     * @param repo repository to persist the model in
     * @return the persisted model
     * @throws StorageException when persistence fails
     */
    public T create(ModelRepository<T> repo) throws StorageException {
        return make().save(repo);
    }

    /**
     * Build and persist {@code count} models to the given repository.
     *
     * @param count number of models to create and persist
     * @param repo  repository to persist the models in
     * @return list of persisted models
     * @throws StorageException when persistence of any model fails
     */
    public List<T> create(int count, ModelRepository<T> repo) throws StorageException {
        final List<T> result = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            result.add(create(repo));
        }
        return result;
    }

    /**
     * Discover and return a factory for the given model class using naming conventions.
     * The model class must implement {@link HasFactory}.
     *
     * <p>Convention: given {@code Player}, searches for {@code PlayerFactory} first in
     * the same package as {@code Player}, then in a {@code factories} sub-package.
     *
     * @param <T>        model type
     * @param <F>        factory type
     * @param modelClass the model class to discover a factory for
     * @return a new factory instance
     * @throws IllegalStateException when no factory can be found or instantiated
     */
    @SuppressWarnings("unchecked")
    public static <T extends Model, F extends Factory<T>> F discover(
            Class<? extends HasFactory> modelClass) {
        final String modelName = modelClass.getSimpleName();
        final String modelPackage = modelClass.getPackageName();
        final String factoryName = modelName + "Factory";
        final List<String> candidates = new ArrayList<>();
        candidates.add(modelPackage + "." + factoryName);
        candidates.add(modelPackage + ".factories." + factoryName);
        for (final String candidate : candidates) {
            try {
                final Class<?> factoryClass = Class.forName(candidate);
                final Constructor<?> ctor = factoryClass.getDeclaredConstructor();
                ctor.setAccessible(true);
                return (F) ctor.newInstance();
            }
            catch (ClassNotFoundException ignored) {
                // try next candidate
            }
            catch (Exception e) {
                throw new IllegalStateException(
                    "Found factory class " + candidate + " but could not instantiate it", e);
            }
        }
        throw new IllegalStateException(
            "No factory found for model " + modelClass.getName()
            + ". Expected one of: " + String.join(", ", candidates));
    }

    /**
     * Return the Jaker instance used by this factory.
     *
     * @return the Jaker faker instance
     */
    protected Faker faker() {
        return faker;
    }

    /**
     * Return the model class this factory produces.
     *
     * @return model class
     */
    protected Class<T> modelClass() {
        return modelClass;
    }

    /**
     * Merge state overrides on top of the given base attribute map.
     *
     * @param base base attribute map from {@link #definition(Faker)}
     * @return merged map with state overrides applied
     */
    private Map<String, Object> mergeWithState(Map<String, Object> base) {
        final Map<String, Object> merged = new HashMap<>(base);
        merged.putAll(stateOverrides);
        return merged;
    }

    /**
     * Instantiate a model with the given id and attributes using reflection.
     * First tries the {@code (String, Map)} constructor; falls back to the
     * {@code (String)} constructor followed by {@link Model#fromMap(Map)}.
     *
     * @param id    model identifier
     * @param attrs attribute map
     * @return new model instance
     * @throws IllegalStateException when the model cannot be instantiated
     */
    @SuppressWarnings("unchecked")
    private T newModel(String id, Map<String, Object> attrs) {
        try {
            final Constructor<?> ctor = modelClass.getDeclaredConstructor(String.class, Map.class);
            ctor.setAccessible(true);
            return (T) ctor.newInstance(id, attrs);
        }
        catch (NoSuchMethodException ignored) {
            // fall back to (String) constructor + fromMap
        }
        catch (Exception e) {
            throw new IllegalStateException(
                "Failed to instantiate " + modelClass.getName()
                    + " via (String, Map) constructor", e);
        }
        try {
            final Constructor<?> ctor = modelClass.getDeclaredConstructor(String.class);
            ctor.setAccessible(true);
            final T model = (T) ctor.newInstance(id);
            model.fromMap(attrs);
            return model;
        }
        catch (Exception e) {
            throw new IllegalStateException(
                "Failed to instantiate " + modelClass.getName()
                    + " via (String) constructor", e);
        }
    }

    /**
     * Discover the model class for this factory using naming conventions.
     * Strips the {@code "Factory"} suffix from this factory's simple name and
     * searches in the factory's own package, then in the parent package.
     *
     * @return the model class
     * @throws IllegalStateException when convention discovery fails
     */
    @SuppressWarnings("unchecked")
    private Class<T> resolveModelClass() {
        final String factorySimpleName = this.getClass().getSimpleName();
        if (!factorySimpleName.endsWith("Factory")) {
            throw new IllegalStateException(
                "Cannot auto-discover model: '" + factorySimpleName
                    + "' does not end with 'Factory'. "
                    + "Use Factory(Class<T>) to specify the model class explicitly.");
        }
        final String modelName = factorySimpleName.substring(0, factorySimpleName.length() - 7);
        final String factoryPackage = this.getClass().getPackageName();
        final int lastDot = factoryPackage.lastIndexOf('.');
        final List<String> candidates = new ArrayList<>();
        candidates.add(factoryPackage + "." + modelName);
        if (lastDot > 0) {
            candidates.add(factoryPackage.substring(0, lastDot) + "." + modelName);
        }
        for (final String candidate : candidates) {
            try {
                return (Class<T>) Class.forName(candidate);
            }
            catch (ClassNotFoundException ignored) {
                // try next
            }
        }
        throw new IllegalStateException(
            "Cannot auto-discover model class for '" + factorySimpleName
                + "'. Expected one of: " + String.join(", ", candidates)
                + ". Use Factory(Class<T>) to specify the model class explicitly.");
    }

}
