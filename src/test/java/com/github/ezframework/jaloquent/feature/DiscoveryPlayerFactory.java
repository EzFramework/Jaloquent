package com.github.ezframework.jaloquent.feature;

import com.github.ezframework.jaker.Faker;
import com.github.ezframework.jaloquent.model.Factory;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for {@link DiscoveryPlayer}, used to verify naming-convention discovery.
 * The no-arg constructor triggers {@link Factory}'s auto-discovery of the model class.
 */
public class DiscoveryPlayerFactory extends Factory<DiscoveryPlayer> {

    /**
     * Create a factory — model class is auto-discovered from this class name by convention.
     */
    public DiscoveryPlayerFactory() {
        super();
    }

    /**
     * Create a factory with a custom or seeded Jaker instance.
     *
     * @param faker Jaker instance to use
     */
    public DiscoveryPlayerFactory(Faker faker) {
        super(faker);
    }

    /**
     * Return fake attributes for a {@link DiscoveryPlayer}.
     *
     * @param faker Jaker instance for generating values
     * @return attribute map with {@code name} and {@code email}
     */
    @Override
    protected Map<String, Object> definition(Faker faker) {
        final Map<String, Object> attrs = new HashMap<>();
        attrs.put("name", faker.name().firstName());
        attrs.put("email", faker.internet().email("player"));
        return attrs;
    }

}
