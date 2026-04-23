package com.github.ezframework.jaloquent.relation;

import com.github.ezframework.jaloquent.exception.StorageException;
import com.github.ezframework.jaloquent.model.BaseModel;
import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder;
import java.util.List;

/**
 * Represents a one-to-many relationship where the foreign key lives on the related model.
 *
 * <p>Example — a {@code User} has many {@code Post}s:
 * <pre>{@code
 * public HasMany<Post> posts() {
 *     return hasMany(postRepo, "user_id");
 * }
 * }</pre>
 *
 * @param <T> the related model type
 */
public final class HasMany<T extends BaseModel> extends Relation<T> {

    /**
     * Constructor. Use
     * {@link com.github.ezframework.jaloquent.model.Model#hasMany(ModelRepository, String)} to create.
     *
     * @param related      the related model repository
     * @param queryBuilder the query builder with the base constraint applied
     */
    public HasMany(ModelRepository<T> related, QueryBuilder queryBuilder) {
        super(related, queryBuilder);
    }

    /**
     * Add an additional WHERE equals constraint to the relationship query.
     *
     * @param column column name
     * @param value  value to match
     * @return this for chaining
     */
    public HasMany<T> where(String column, Object value) {
        queryBuilder.whereEquals(column, value);
        return this;
    }

    /**
     * Limit the number of results returned.
     *
     * @param n maximum row count
     * @return this for chaining
     */
    public HasMany<T> limit(int n) {
        queryBuilder.limit(n);
        return this;
    }

    /**
     * Order the results.
     *
     * @param column column to sort on
     * @param asc    {@code true} for ascending order
     * @return this for chaining
     */
    public HasMany<T> orderBy(String column, boolean asc) {
        queryBuilder.orderBy(column, asc);
        return this;
    }

    /**
     * Execute the relationship and return all matching related models.
     *
     * @return list of related models; never {@code null}
     * @throws StorageException on storage failure
     */
    public List<T> get() throws StorageException {
        return related.query(queryBuilder.build());
    }

    /**
     * Count the related records.
     *
     * @return the number of related models
     * @throws StorageException on storage failure
     */
    public long count() throws StorageException {
        return get().size();
    }

}
