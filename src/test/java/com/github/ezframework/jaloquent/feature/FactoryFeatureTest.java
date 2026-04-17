package com.github.ezframework.jaloquent.feature;

import com.github.ezframework.jaker.Faker;
import com.github.ezframework.jaloquent.exception.StorageException;
import com.github.ezframework.jaloquent.model.Factory;
import com.github.ezframework.jaloquent.model.FactoryCount;
import com.github.ezframework.jaloquent.model.HasFactory;
import com.github.ezframework.jaloquent.model.Model;
import com.github.ezframework.jaloquent.model.ModelRepository;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feature tests for the {@link Factory} pattern, {@link FactoryCount}, and
 * {@link HasFactory} discovery.
 */
public class FactoryFeatureTest {

    // =========================================================================
    // Fixtures
    // =========================================================================

    static class TestModel extends Model implements HasFactory {
        TestModel(String id) { super(id); }
        @Override public Map<String, Object> toMap() { return new HashMap<>(attributes()); }
    }

    /** Uses explicit model class because TestModel is an inner class. */
    static class TestModelFactory extends Factory<TestModel> {

        TestModelFactory() {
            super(TestModel.class);
        }

        TestModelFactory(Faker faker) {
            super(faker, TestModel.class);
        }

        @Override
        protected Map<String, Object> definition(Faker faker) {
            final Map<String, Object> attrs = new HashMap<>();
            attrs.put("name", faker.name().firstName());
            attrs.put("email", faker.internet().email("user"));
            return attrs;
        }
    }

    private static ModelRepository<TestModel> inMemoryRepo() {
        return new ModelRepository<>(
            new ModelRepositoryJdbcFeatureTest.InMemoryStore(),
            "factory-" + UUID.randomUUID(),
            (id, data) -> {
                final TestModel m = new TestModel(id);
                m.fromMap(data);
                return m;
            }
        );
    }

    // =========================================================================
    // make / make(count)
    // =========================================================================

    @Test
    void make_returnsFakeModelNotPersisted() {
        final TestModelFactory factory = new TestModelFactory();
        final TestModel model = factory.make();

        assertNotNull(model.getId());
        assertFalse(model.getId().isBlank());
        assertNotNull(model.get("name"));
        assertNotNull(model.get("email"));
    }

    @Test
    void makeCount_returnsDistinctModels() {
        final TestModelFactory factory = new TestModelFactory();
        final List<TestModel> models = factory.make(5);

        assertEquals(5, models.size());
        final long distinctIds = models.stream().map(TestModel::getId).distinct().count();
        assertEquals(5, distinctIds);
    }

    // =========================================================================
    // state()
    // =========================================================================

    @Test
    void state_mergesOverridesIntoDefinition() {
        final TestModelFactory factory = new TestModelFactory();
        final TestModel model = factory.state(Map.of("role", "admin", "name", "Override")).make();

        assertEquals("Override", model.get("name"));
        assertEquals("admin", model.get("role"));
        assertNotNull(model.get("email"));
    }

    @Test
    void state_isChainable_laterWins() {
        final TestModelFactory factory = new TestModelFactory();
        final TestModel model = factory
            .state(Map.of("role", "admin"))
            .state(Map.of("role", "moderator"))
            .make();

        assertEquals("moderator", model.get("role"));
    }

    @Test
    void state_doesNotAffectOtherFactoryInstances() {
        final TestModel withRole = new TestModelFactory().state(Map.of("role", "admin")).make();
        final TestModel plain    = new TestModelFactory().make();

        assertEquals("admin", withRole.get("role"));
        assertNull(plain.get("role"));
    }

    // =========================================================================
    // count()
    // =========================================================================

    @Test
    void count_make_returnsList() {
        final FactoryCount<TestModel> counter = new TestModelFactory().count(3);
        final List<TestModel> models = counter.make();

        assertEquals(3, models.size());
        final long distinctIds = models.stream().map(TestModel::getId).distinct().count();
        assertEquals(3, distinctIds);
    }

    @Test
    void count_create_persistsAll() throws StorageException {
        final ModelRepository<TestModel> repo = inMemoryRepo();
        final List<TestModel> models = new TestModelFactory().count(3).create(repo);

        assertEquals(3, models.size());
        for (final TestModel m : models) {
            assertTrue(repo.find(m.getId()).isPresent());
        }
    }

    // =========================================================================
    // create()
    // =========================================================================

    @Test
    void create_persistsToRepository() throws StorageException {
        final ModelRepository<TestModel> repo = inMemoryRepo();
        final TestModel persisted = new TestModelFactory().create(repo);

        assertNotNull(persisted.getId());
        final Optional<TestModel> found = repo.find(persisted.getId());
        assertTrue(found.isPresent());
        assertEquals(persisted.getId(), found.get().getId());
    }

    // =========================================================================
    // Seeded Faker
    // =========================================================================

    @Test
    void seededFaker_isDeterministic() {
        final Faker faker1 = Faker.builder().locale("en-US").seed(42).build().faker();
        final Faker faker2 = Faker.builder().locale("en-US").seed(42).build().faker();

        final TestModel m1 = new TestModelFactory(faker1).make();
        final TestModel m2 = new TestModelFactory(faker2).make();

        assertEquals(m1.get("name"), m2.get("name"));
        assertEquals(m1.get("email"), m2.get("email"));
    }

    // =========================================================================
    // HasFactory convention discovery — uses top-level DiscoveryPlayer classes
    // =========================================================================

    @Test
    void discover_findsFactoryByNamingConvention() {
        final DiscoveryPlayerFactory factory = Factory.discover(DiscoveryPlayer.class);

        assertInstanceOf(DiscoveryPlayerFactory.class, factory);
    }

    @Test
    void modelDotFactory_returnsDiscoveredFactory() {
        final DiscoveryPlayerFactory factory = DiscoveryPlayer.factory();
        final DiscoveryPlayer player = factory.make();

        assertNotNull(player.getId());
        assertNotNull(player.get("name"));
        assertNotNull(player.get("email"));
    }

    @Test
    void playerDotFactory_fluentChain() throws StorageException {
        final ModelRepository<DiscoveryPlayer> repo = new ModelRepository<>(
            new ModelRepositoryJdbcFeatureTest.InMemoryStore(),
            "dp-" + UUID.randomUUID(),
            (id, data) -> {
                final DiscoveryPlayer p = new DiscoveryPlayer(id);
                p.fromMap(data);
                return p;
            }
        );

        final List<DiscoveryPlayer> players = DiscoveryPlayer.factory().count(3).create(repo);

        assertEquals(3, players.size());
        for (final DiscoveryPlayer p : players) {
            assertTrue(repo.find(p.getId()).isPresent());
        }
    }

    @Test
    void seededDiscoveryPlayer_isDeterministic() {
        final Faker f1 = Faker.builder().locale("en-US").seed(99).build().faker();
        final Faker f2 = Faker.builder().locale("en-US").seed(99).build().faker();
        final DiscoveryPlayer p1 = new DiscoveryPlayerFactory(f1).make();
        final DiscoveryPlayer p2 = new DiscoveryPlayerFactory(f2).make();

        assertEquals(p1.get("name"), p2.get("name"));
        assertNotEquals(p1.getId(), p2.getId());
    }

}
