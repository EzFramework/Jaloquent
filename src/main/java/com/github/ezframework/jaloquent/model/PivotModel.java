package com.github.ezframework.jaloquent.model;

import java.util.HashMap;
import java.util.Map;

/**
 * A ready-to-use {@link Model} for pivot (join) tables used in many-to-many relationships.
 *
 * <p>Provides a pre-built {@link ModelFactory} constant so that callers do not need to write
 * a factory lambda when using
 * {@link Model#belongsToMany(com.github.ezframework.jaloquent.model.ModelRepository,
 * com.github.ezframework.jaloquent.model.ModelRepository, ModelFactory, String, String)}:
 * <pre>{@code
 * public BelongsToMany<Role, PivotModel> roles() {
 *     return belongsToMany(roleRepo, pivotRepo, PivotModel.FACTORY, "user_id", "role_id");
 * }
 * }</pre>
 */
public final class PivotModel extends Model {

    /**
     * Pre-built factory that creates and populates a {@link PivotModel} from its id and data map.
     */
    public static final ModelFactory<PivotModel> FACTORY = (id, data) -> {
        final PivotModel p = new PivotModel(id);
        if (data != null) {
            p.fromMap(data);
        }
        return p;
    };

    /**
     * Construct a pivot model with the given id.
     *
     * @param id the pivot record identifier
     */
    public PivotModel(String id) {
        super(id);
    }

    /**
     * Serialize all attributes to a flat map.
     *
     * @return a mutable copy of the attribute map
     */
    @Override
    public Map<String, Object> toMap() {
        return new HashMap<>(attributes());
    }

    /**
     * Populate this pivot model's attributes from a flat map.
     *
     * @param map the source map (may be {@code null})
     */
    @Override
    public void fromMap(Map<String, Object> map) {
        if (map == null) {
            return;
        }
        map.forEach(this::set);
    }

}
