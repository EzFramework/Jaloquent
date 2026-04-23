package com.github.ezframework.jaloquent.relation;

import com.github.ezframework.jaloquent.exception.StorageException;
import com.github.ezframework.jaloquent.model.BaseModel;
import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder;
import java.util.List;
import java.util.Optional;

/**
 * Represents a one-to-one relationship where the foreign key lives on the related model.
 *
 * <p>Example — a {@code User} has one {@code Phone}:
 * <pre>{@code
 * public HasOne<Phone> phone() {
 *     return hasOne(phoneRepo, "user_id");
 * }
 * }</pre>
 *
 * @param <T> the related model type
 */
public final class HasOne<T extends BaseModel> extends Relation<T> {

    /**
     * Constructor. Use
     * {@link com.github.ezframework.jaloquent.model.Model#hasOne(ModelRepository, String)} to create.
     *
     * @param related      the related model repository
     * @param queryBuilder the query builder with the base constraint applied
     */
    public HasOne(ModelRepository<T> related, QueryBuilder queryBuilder) {
        super(related, queryBuilder);
    }

    /**
     * Add an additional WHERE equals constraint to the relationship query.
     *
     * @param column column name
     * @param value  value to match
     * @return this for chaining
     */
    public HasOne<T> where(String column, Object value) {
        queryBuilder.whereEquals(column, value);
        return this;
    }

    /**
     * Order results for deterministic row selection.
     *
     * @param column column to sort on
     * @param asc    {@code true} for ascending order
     * @return this for chaining
     */
    public HasOne<T> orderBy(String column, boolean asc) {
        queryBuilder.orderBy(column, asc);
        return this;
    }

    /**
     * Execute the relationship and return the first matching related model.
     *
     * @return the related model, or an empty Optional when no match exists
     * @throws StorageException on storage failure
     */
    public Optional<T> get() throws StorageException {
        queryBuilder.limit(1);
        final List<T> results = related.query(queryBuilder.build());
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    /**
     * Return {@code true} when at least one related record exists.
     *
     * @return {@code true} when the relationship is populated
     * @throws StorageException on storage failure
     */
    public boolean exists() throws StorageException {
        return get().isPresent();
    }

}
