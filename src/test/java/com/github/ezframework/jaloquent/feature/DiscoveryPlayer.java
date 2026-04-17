package com.github.ezframework.jaloquent.feature;

import com.github.ezframework.jaloquent.model.Factory;
import com.github.ezframework.jaloquent.model.HasFactory;
import com.github.ezframework.jaloquent.model.Model;

/**
 * Test model used to verify naming-convention factory discovery.
 * Paired with {@link DiscoveryPlayerFactory}.
 */
public class DiscoveryPlayer extends Model implements HasFactory {

    /**
     * Construct a player with the given id.
     *
     * @param id model identifier
     */
    public DiscoveryPlayer(String id) {
        super(id);
    }

    /**
     * Return a factory for this model, discovered by naming convention.
     *
     * @return a new {@link DiscoveryPlayerFactory} instance
     */
    public static DiscoveryPlayerFactory factory() {
        return Factory.discover(DiscoveryPlayer.class);
    }

    /**
     * Serialize this model's attributes to a flat map.
     *
     * @return mutable copy of the attribute map
     */
    @Override
    public java.util.Map<String, Object> toMap() {
        return new java.util.HashMap<>(attributes());
    }

}
