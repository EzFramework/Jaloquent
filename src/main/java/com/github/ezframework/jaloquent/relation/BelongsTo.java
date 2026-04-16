package com.github.ezframework.jaloquent.relation;

import com.github.ezframework.jaloquent.exception.StorageException;
import com.github.ezframework.jaloquent.model.BaseModel;
import com.github.ezframework.jaloquent.model.ModelRepository;
import java.util.Optional;

/**
 * Represents the inverse side of a one-to-one or one-to-many relationship.
 *
 * <p>The foreign key lives on the child model. The relationship is resolved by
 * looking up the stored FK value in the related model's repository.
 *
 * <p>Example — a {@code Post} belongs to a {@code User}:
 * <pre>{@code
 * public BelongsTo<User> author() {
 *     return belongsTo(userRepo, "user_id");
 * }
 * }</pre>
 *
 * @param <T> the related (parent/owner) model type
 */
public final class BelongsTo<T extends BaseModel> {

    /** Repository for the related (parent/owner) model. */
    private final ModelRepository<T> related;

    /** The FK value stored on this model (the owner's ID to look up). */
    private final String ownerKeyValue;

    /**
     * Constructor. Use
     * {@link com.github.ezframework.jaloquent.model.Model#belongsTo(ModelRepository, String)} to create.
     *
     * @param related       the related model repository
     * @param ownerKeyValue the FK value stored on this model (may be {@code null})
     */
    public BelongsTo(ModelRepository<T> related, String ownerKeyValue) {
        this.related = related;
        this.ownerKeyValue = ownerKeyValue;
    }

    /**
     * Load the related model by its primary key.
     *
     * @return the related model, or an empty Optional when the FK is {@code null} or has no match
     * @throws Exception on storage failure
     */
    public Optional<T> get() throws StorageException {
        if (ownerKeyValue == null) {
            return Optional.empty();
        }
        return related.find(ownerKeyValue);
    }

    /**
     * Return {@code true} when the FK is set and the related record exists.
     *
     * @return {@code true} when the relationship is populated
     * @throws Exception on storage failure
     */
    public boolean exists() throws StorageException {
        return get().isPresent();
    }

}
