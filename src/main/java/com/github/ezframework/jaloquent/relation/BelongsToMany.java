package com.github.ezframework.jaloquent.relation;

import com.github.ezframework.jaloquent.exception.StorageException;
import com.github.ezframework.jaloquent.model.BaseModel;
import com.github.ezframework.jaloquent.model.Model;
import com.github.ezframework.jaloquent.model.ModelFactory;
import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * Represents a many-to-many relationship mediated by a pivot model.
 *
 * <p>Example — a {@code User} belongs to many {@code Role}s via a {@code user_roles} pivot table:
 * <pre>{@code
 * public BelongsToMany<Role, PivotModel> roles() {
 *     return belongsToMany(roleRepo, pivotRepo, PivotModel.FACTORY, "user_id", "role_id");
 * }
 * }</pre>
 *
 * <p>The pivot model must expose the two FK columns via {@link BaseModel#toMap()} and accept
 * them via {@link BaseModel#fromMap(Map)}.
 *
 * @param <T> the related model type
 * @param <P> the pivot model type
 */
public final class BelongsToMany<T extends BaseModel, P extends BaseModel> {

    /** Repository for the related model. */
    private final ModelRepository<T> related;

    /** Repository for the pivot model. */
    private final ModelRepository<P> pivotRepo;

    /** Factory used to create new pivot model instances for attach operations. */
    private final ModelFactory<P> pivotFactory;

    /** The primary key value of the parent model. */
    private final String parentId;

    /** Column name in the pivot table that stores the parent model's ID. */
    private final String foreignKey;

    /** Column name in the pivot table that stores the related model's ID. */
    private final String relatedKey;

    /** Additional constraints applied to the related model query in {@link #get()}. */
    private final List<Consumer<QueryBuilder>> relatedConstraints;

    /**
     * Constructor. Use
     * {@link com.github.ezframework.jaloquent.model.Model#belongsToMany} to create.
     *
     * @param related       the related model repository
     * @param pivotRepo     the pivot model repository
     * @param pivotFactory  factory for creating new pivot model instances
     * @param parentId      the ID of the parent model
     * @param foreignKey    pivot column pointing to the parent model's ID
     * @param relatedKey    pivot column pointing to the related model's ID
     */
    public BelongsToMany(
            ModelRepository<T> related,
            ModelRepository<P> pivotRepo,
            ModelFactory<P> pivotFactory,
            String parentId,
            String foreignKey,
            String relatedKey) {
        this.related = related;
        this.pivotRepo = pivotRepo;
        this.pivotFactory = pivotFactory;
        this.parentId = parentId;
        this.foreignKey = foreignKey;
        this.relatedKey = relatedKey;
        this.relatedConstraints = new ArrayList<>();
    }

    /**
     * Add an additional WHERE equals constraint applied to the related model query after pivot resolution.
     *
     * @param column column name on the related model table
     * @param value  value to match
     * @return this for chaining
     */
    public BelongsToMany<T, P> where(String column, Object value) {
        relatedConstraints.add(qb -> qb.whereEquals(column, value));
        return this;
    }

    /**
     * Order the related results.
     *
     * @param column column to sort on
     * @param asc    {@code true} for ascending order
     * @return this for chaining
     */
    public BelongsToMany<T, P> orderBy(String column, boolean asc) {
        relatedConstraints.add(qb -> qb.orderBy(column, asc));
        return this;
    }

    /**
     * Execute the relationship: resolve pivot rows then load all related models.
     *
     * @return list of related models; never {@code null}
     * @throws StorageException on storage failure
     */
    public List<T> get() throws StorageException {
        final List<P> pivotEntries = pivotRepo.query(
                new QueryBuilder().whereEquals(foreignKey, parentId).build());
        if (pivotEntries.isEmpty()) {
            return new ArrayList<>();
        }
        final List<String> relatedIds = collectRelatedIds(pivotEntries);
        if (relatedIds.isEmpty()) {
            return new ArrayList<>();
        }
        final QueryBuilder qb = new QueryBuilder().whereIn("id", relatedIds);
        relatedConstraints.forEach(c -> c.accept(qb));
        return related.query(qb.build());
    }

    /**
     * Attach a related model to this relationship by creating a pivot entry.
     *
     * @param relatedId the ID of the related model to attach
     * @throws StorageException on storage failure
     */
    public void attach(String relatedId) throws StorageException {
        attach(relatedId, null);
    }

    /**
     * Attach a related model with optional extra attributes stored on the pivot entry.
     *
     * @param relatedId  the ID of the related model to attach
     * @param extraAttrs additional key-value pairs to store on the pivot entry (may be {@code null})
     * @throws StorageException on storage failure
     */
    public void attach(String relatedId, Map<String, Object> extraAttrs) throws StorageException {
        final String pivotId = parentId + "_" + relatedId;
        final Map<String, Object> data = new HashMap<>();
        data.put(foreignKey, parentId);
        data.put(relatedKey, relatedId);
        if (extraAttrs != null) {
            data.putAll(extraAttrs);
        }
        final P pivot = pivotFactory.create(pivotId, data);
        pivot.fromMap(data);
        pivotRepo.save(pivot);
    }

    /**
     * Detach a specific related model from this relationship by removing the pivot entry.
     *
     * @param relatedId the ID of the related model to detach
     * @throws StorageException on storage failure
     */
    public void detach(String relatedId) throws StorageException {
        pivotRepo.delete(parentId + "_" + relatedId);
    }

    /**
     * Detach all related models from this relationship.
     *
     * <p>Issues a single bulk DELETE (SQL path) or iterates once over QueryableStorage results
     * (flat-map path). No separate SELECT is issued on the SQL path.
     *
     * @throws StorageException on storage failure
     */
    public void detachAll() throws StorageException {
        pivotRepo.deleteWhere(foreignKey, parentId);
    }

    /**
     * Sync the relationship to exactly the given set of related IDs.
     * Existing IDs not in the list are detached; new IDs not yet present are attached.
     *
     * <p>Removals are executed as a single bulk DELETE rather than one DELETE per removed entry,
     * avoiding the N+1 problem when many pivot records must be removed.
     *
     * @param desiredIds the desired set of related model IDs
     * @throws StorageException on storage failure
     */
    public void sync(List<String> desiredIds) throws StorageException {
        final List<P> existing = pivotRepo.query(
                new QueryBuilder().whereEquals(foreignKey, parentId).build());
        final List<String> currentIds = collectRelatedIds(existing);
        final List<String> pivotIdsToDelete = currentIds.stream()
                .filter(id -> !desiredIds.contains(id))
                .map(id -> parentId + "_" + id)
                .collect(Collectors.toList());
        pivotRepo.deleteAll(pivotIdsToDelete);
        for (final String desiredId : desiredIds) {
            if (!currentIds.contains(desiredId)) {
                attach(desiredId);
            }
        }
    }

    /**
     * Count the number of related records through the pivot table.
     *
     * @return number of related models
     * @throws StorageException on storage failure
     */
    public long count() throws StorageException {
        return get().size();
    }

    /**
     * Extract the related model IDs from a list of pivot model instances.
     *
     * @param pivotEntries the pivot model instances to inspect
     * @return list of related model ID strings, with {@code null} values filtered out
     */
    private List<String> collectRelatedIds(List<P> pivotEntries) {
        return pivotEntries.stream()
                .map(p -> {
                    final Object v = (p instanceof Model)
                            ? ((Model) p).get(relatedKey)
                            : p.toMap().get(relatedKey);
                    return v == null ? null : v.toString();
                })
                .filter(id -> id != null)
                .collect(Collectors.toList());
    }

}
