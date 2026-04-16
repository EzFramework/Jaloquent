package com.github.ezframework.jaloquent.relation;

import com.github.ezframework.jaloquent.model.BaseModel;
import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder;

/**
 * Abstract base for HasOne and HasMany relationship types.
 *
 * <p>Holds a reference to the related model repository and a {@link QueryBuilder}
 * pre-configured with the base WHERE constraint for the relationship. Subclasses add
 * their own typed {@code where}, {@code limit}, {@code orderBy}, and {@code get} methods.
 *
 * @param <T> the related model type
 */
public abstract class Relation<T extends BaseModel> {

    /** Repository for the related model. */
    protected final ModelRepository<T> related;

    /** Builder used to accumulate query constraints for this relationship. */
    protected final QueryBuilder queryBuilder;

    /**
     * Construct a relation with the given repository and pre-configured query builder.
     *
     * @param related      the related model repository
     * @param queryBuilder the query builder with the base relationship constraint already applied
     */
    protected Relation(ModelRepository<T> related, QueryBuilder queryBuilder) {
        this.related = related;
        this.queryBuilder = queryBuilder;
    }

}
