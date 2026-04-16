package com.github.ezframework.jaloquent.feature;

import com.github.ezframework.jaloquent.model.BaseModel;
import com.github.ezframework.jaloquent.model.Model;
import com.github.ezframework.jaloquent.model.ModelFactory;
import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.jaloquent.model.PivotModel;
import com.github.ezframework.jaloquent.model.TableRegistry;
import com.github.ezframework.jaloquent.relation.BelongsTo;
import com.github.ezframework.jaloquent.relation.BelongsToMany;
import com.github.ezframework.jaloquent.relation.HasMany;
import com.github.ezframework.jaloquent.relation.HasOne;
import com.github.ezframework.jaloquent.store.DataStore;
import com.github.ezframework.jaloquent.store.sql.JdbcStore;
import com.github.ezframework.javaquerybuilder.query.Query;
import com.github.ezframework.javaquerybuilder.query.QueryableStorage;
import com.github.ezframework.javaquerybuilder.query.condition.ConditionEntry;
import com.github.ezframework.javaquerybuilder.query.condition.Operator;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Feature tests for Eloquent-style model relationships:
 * {@link HasOne}, {@link HasMany}, {@link BelongsTo}, and {@link BelongsToMany}.
 */
public class RelationshipFeatureTest {

    // =========================================================================
    // Model Fixtures
    // =========================================================================

    static class UserModel extends Model {

        UserModel(String id) {
            super(id);
        }

        public HasOne<PhoneModel> phone(ModelRepository<PhoneModel> repo) {
            return hasOne(repo, "user_id");
        }

        public HasOne<PhoneModel> phoneByEmail(ModelRepository<PhoneModel> repo) {
            return hasOne(repo, "owner_email", "email");
        }

        public HasMany<PostModel> posts(ModelRepository<PostModel> repo) {
            return hasMany(repo, "user_id");
        }

        public BelongsToMany<RoleModel, PivotModel> roles(
                ModelRepository<RoleModel> roleRepo,
                ModelRepository<PivotModel> pivotRepo) {
            return belongsToMany(roleRepo, pivotRepo, PivotModel.FACTORY, "user_id", "role_id");
        }

    }

    static class PhoneModel extends Model {

        PhoneModel(String id) {
            super(id);
        }

        public BelongsTo<UserModel> owner(ModelRepository<UserModel> repo) {
            return belongsTo(repo, "user_id");
        }

    }

    static class PostModel extends Model {

        PostModel(String id) {
            super(id);
        }

    }

    static class RoleModel extends Model {

        RoleModel(String id) {
            super(id);
        }

    }

    // =========================================================================
    // Store Fixtures
    // =========================================================================

    /**
     * In-memory flat-map store that also implements {@link QueryableStorage} with
     * proper filtering for EQ and IN conditions. Injects an {@code "id"} field
     * (last path segment) into every stored map so that id-based queries work.
     */
    static class FilterableStore implements DataStore, QueryableStorage {

        private final Map<String, Map<String, Object>> store = new HashMap<>();

        @Override
        public void save(String path, Map<String, Object> data) {
            final Map<String, Object> copy = new HashMap<>(data);
            copy.put("id", pathId(path));
            store.put(path, copy);
        }

        @Override
        public Optional<Map<String, Object>> load(String path) {
            return Optional.ofNullable(store.get(path));
        }

        @Override
        public void delete(String path) {
            store.remove(path);
        }

        @Override
        public boolean exists(String path) {
            return store.containsKey(path);
        }

        @Override
        public List<String> query(Query q) {
            java.util.stream.Stream<String> stream = store.entrySet().stream()
                    .filter(e -> matchesQuery(e.getValue(), q))
                    .map(e -> pathId(e.getKey()));
            if (q.getLimit() > 0) {
                stream = stream.limit(q.getLimit());
            }
            return stream.collect(Collectors.toList());
        }

        private boolean matchesQuery(Map<String, Object> row, Query q) {
            for (final ConditionEntry entry : q.getConditions()) {
                if (!matchesCondition(row, entry)) {
                    return false;
                }
            }
            return true;
        }

        @SuppressWarnings("unchecked")
        private boolean matchesCondition(Map<String, Object> row, ConditionEntry entry) {
            final Object rowVal = row.get(entry.getColumn());
            final Operator op = entry.getCondition().getOperator();
            if (op == Operator.EQ) {
                return Objects.equals(rowVal, entry.getCondition().getValue());
            }
            if (op == Operator.IN) {
                final List<Object> inList = (List<Object>) entry.getCondition().getValue();
                return inList.contains(rowVal);
            }
            return entry.getCondition().matches(row, entry.getColumn());
        }

        private static String pathId(String path) {
            final int slash = path.lastIndexOf('/');
            return slash >= 0 ? path.substring(slash + 1) : path;
        }

    }

    /**
     * Recording JDBC store used to verify SQL generated on the SQL relationship path.
     */
    static class RecordingJdbcStore implements DataStore, JdbcStore {

        final List<String> querySqls = new ArrayList<>();
        final List<List<Object>> queryParams = new ArrayList<>();
        List<Map<String, Object>> nextQueryRows = new ArrayList<>();

        @Override
        public void save(String path, Map<String, Object> data) {
        }

        @Override
        public Optional<Map<String, Object>> load(String path) {
            return Optional.empty();
        }

        @Override
        public void delete(String path) {
        }

        @Override
        public boolean exists(String path) {
            return false;
        }

        @Override
        public List<Map<String, Object>> query(String sql, List<Object> params) {
            querySqls.add(sql);
            queryParams.add(new ArrayList<>(params));
            return new ArrayList<>(nextQueryRows);
        }

        @Override
        public int executeUpdate(String sql, List<Object> params) {
            return 1;
        }

    }

    // =========================================================================
    // Helper Factories
    // =========================================================================

    private static ModelRepository<UserModel> userRepo(DataStore store, String prefix) {
        return new ModelRepository<>(store, prefix, (id, data) -> new UserModel(id));
    }

    private static ModelRepository<PhoneModel> phoneRepo(DataStore store, String prefix) {
        return new ModelRepository<>(store, prefix, (id, data) -> new PhoneModel(id));
    }

    private static ModelRepository<PostModel> postRepo(DataStore store, String prefix) {
        return new ModelRepository<>(store, prefix, (id, data) -> new PostModel(id));
    }

    private static ModelRepository<RoleModel> roleRepo(DataStore store, String prefix) {
        return new ModelRepository<>(store, prefix, (id, data) -> new RoleModel(id));
    }

    private static ModelRepository<PivotModel> pivotRepo(DataStore store, String prefix) {
        return new ModelRepository<>(store, prefix, PivotModel.FACTORY);
    }

    // =========================================================================
    // HasOne — flat-map path
    // =========================================================================

    @Test
    void hasOneReturnsEmptyWhenNoMatchingModel() throws Exception {
        final FilterableStore store = new FilterableStore();
        final UserModel user = new UserModel("u1");

        assertTrue(user.phone(phoneRepo(store, "phones")).get().isEmpty());
    }

    @Test
    void hasOneReturnsRelatedModelByForeignKey() throws Exception {
        final FilterableStore store = new FilterableStore();
        final UserModel user = new UserModel("u1");

        final PhoneModel phone = new PhoneModel("ph1");
        phone.set("user_id", "u1");
        phone.set("number", "555-0100");
        phoneRepo(store, "phones").save(phone);

        final Optional<PhoneModel> found = user.phone(phoneRepo(store, "phones")).get();
        assertTrue(found.isPresent());
        assertEquals("ph1", found.get().getId());
        assertEquals("555-0100", found.get().get("number"));
    }

    @Test
    void hasOneExistsMatchesForeignKey() throws Exception {
        final FilterableStore store = new FilterableStore();
        final UserModel user = new UserModel("u1");

        assertFalse(user.phone(phoneRepo(store, "phones")).exists());

        final PhoneModel phone = new PhoneModel("ph1");
        phone.set("user_id", "u1");
        phoneRepo(store, "phones").save(phone);

        assertTrue(user.phone(phoneRepo(store, "phones")).exists());
    }

    @Test
    void hasOneReturnsFirstWhenMultipleForeignKeyMatches() throws Exception {
        final FilterableStore store = new FilterableStore();
        final UserModel user = new UserModel("u1");

        phoneRepo(store, "phones").save(phoneWith("ph1", "u1", "555-0001"));
        phoneRepo(store, "phones").save(phoneWith("ph2", "u1", "555-0002"));

        assertTrue(user.phone(phoneRepo(store, "phones")).get().isPresent());
    }

    @Test
    void hasOneWithExtraWhereConstraintFiltersResults() throws Exception {
        final FilterableStore store = new FilterableStore();
        final UserModel user = new UserModel("u1");

        phoneRepo(store, "phones").save(phoneWith("ph1", "u1", "555-0001"));
        final PhoneModel ph2 = new PhoneModel("ph2");
        ph2.set("user_id", "u1");
        ph2.set("number", "555-0002");
        ph2.set("type", "work");
        phoneRepo(store, "phones").save(ph2);

        final Optional<PhoneModel> result = user.phone(phoneRepo(store, "phones"))
                .where("type", "work")
                .get();
        assertTrue(result.isPresent());
        assertEquals("ph2", result.get().getId());
    }

    @Test
    void hasOneWithCustomLocalKey() throws Exception {
        final FilterableStore store = new FilterableStore();
        final UserModel user = new UserModel("u1");
        user.set("email", "alice@example.com");

        final PhoneModel phone = new PhoneModel("ph1");
        phone.set("owner_email", "alice@example.com");
        phoneRepo(store, "phones").save(phone);

        final Optional<PhoneModel> found = user.phoneByEmail(phoneRepo(store, "phones")).get();
        assertTrue(found.isPresent());
        assertEquals("ph1", found.get().getId());
    }

    // =========================================================================
    // HasOne — SQL path
    // =========================================================================

    @Test
    void hasOneGeneratesCorrectSqlWhereClause() throws Exception {
        final String prefix = UUID.randomUUID().toString();
        TableRegistry.register(prefix, "phones", Map.of("user_id", "varchar"));

        final RecordingJdbcStore jdbc = new RecordingJdbcStore();
        final UserModel user = new UserModel("u1");

        user.phone(phoneRepo(jdbc, prefix)).get();

        assertEquals(1, jdbc.querySqls.size());
        assertTrue(jdbc.querySqls.get(0).contains("WHERE"));
        assertTrue(jdbc.querySqls.get(0).contains("user_id"));
        assertEquals(List.of("u1"), jdbc.queryParams.get(0));
    }

    @Test
    void hasOnePopulatesModelFromSqlRow() throws Exception {
        final String prefix = UUID.randomUUID().toString();
        TableRegistry.register(prefix, "phones", Map.of("user_id", "varchar", "number", "varchar"));

        final RecordingJdbcStore jdbc = new RecordingJdbcStore();
        final Map<String, Object> row = new HashMap<>();
        row.put("id", "ph1");
        row.put("user_id", "u1");
        row.put("number", "555-9999");
        jdbc.nextQueryRows = List.of(row);

        final UserModel user = new UserModel("u1");
        final Optional<PhoneModel> found = user.phone(phoneRepo(jdbc, prefix)).get();

        assertTrue(found.isPresent());
        assertEquals("555-9999", found.get().get("number"));
    }

    // =========================================================================
    // HasMany — flat-map path
    // =========================================================================

    @Test
    void hasManyReturnsEmptyListWhenNoPosts() throws Exception {
        final FilterableStore store = new FilterableStore();
        final UserModel user = new UserModel("u1");

        assertTrue(user.posts(postRepo(store, "posts")).get().isEmpty());
    }

    @Test
    void hasManyReturnsAllMatchingModels() throws Exception {
        final FilterableStore store = new FilterableStore();
        final UserModel user = new UserModel("u1");

        postRepo(store, "posts").save(postWith("p1", "u1", "First"));
        postRepo(store, "posts").save(postWith("p2", "u1", "Second"));
        postRepo(store, "posts").save(postWith("p3", "u2", "OtherUser"));

        final List<PostModel> posts = user.posts(postRepo(store, "posts")).get();
        assertEquals(2, posts.size());
        assertTrue(posts.stream().anyMatch(p -> "p1".equals(p.getId())));
        assertTrue(posts.stream().anyMatch(p -> "p2".equals(p.getId())));
    }

    @Test
    void hasManyCountReturnsCorrectNumber() throws Exception {
        final FilterableStore store = new FilterableStore();
        final UserModel user = new UserModel("u1");

        postRepo(store, "posts").save(postWith("p1", "u1", "First"));
        postRepo(store, "posts").save(postWith("p2", "u1", "Second"));

        assertEquals(2L, user.posts(postRepo(store, "posts")).count());
    }

    @Test
    void hasManyWithExtraWhereConstraint() throws Exception {
        final FilterableStore store = new FilterableStore();
        final UserModel user = new UserModel("u1");

        final PostModel published = new PostModel("p1");
        published.set("user_id", "u1");
        published.set("status", "published");
        postRepo(store, "posts").save(published);

        final PostModel draft = new PostModel("p2");
        draft.set("user_id", "u1");
        draft.set("status", "draft");
        postRepo(store, "posts").save(draft);

        final List<PostModel> results = user.posts(postRepo(store, "posts"))
                .where("status", "published")
                .get();
        assertEquals(1, results.size());
        assertEquals("p1", results.get(0).getId());
    }

    @Test
    void hasManyLimitReducesResults() throws Exception {
        final FilterableStore store = new FilterableStore();
        final UserModel user = new UserModel("u1");

        for (int i = 1; i <= 5; i++) {
            postRepo(store, "posts").save(postWith("p" + i, "u1", "Post " + i));
        }

        final List<PostModel> limited = user.posts(postRepo(store, "posts")).limit(2).get();
        assertEquals(2, limited.size());
    }

    // =========================================================================
    // BelongsTo
    // =========================================================================

    @Test
    void belongsToReturnsEmptyWhenForeignKeyIsNull() throws Exception {
        final FilterableStore store = new FilterableStore();
        final PhoneModel phone = new PhoneModel("ph1");

        assertFalse(phone.owner(userRepo(store, "users")).exists());
    }

    @Test
    void belongsToLoadsRelatedModelByForeignKey() throws Exception {
        final FilterableStore store = new FilterableStore();

        final UserModel user = new UserModel("u1");
        user.set("name", "Alice");
        userRepo(store, "users").save(user);

        final PhoneModel phone = new PhoneModel("ph1");
        phone.set("user_id", "u1");

        final Optional<UserModel> owner = phone.owner(userRepo(store, "users")).get();
        assertTrue(owner.isPresent());
        assertEquals("u1", owner.get().getId());
        assertEquals("Alice", owner.get().get("name"));
    }

    @Test
    void belongsToExistsReturnsFalseForMissingRelated() throws Exception {
        final FilterableStore store = new FilterableStore();
        final PhoneModel phone = new PhoneModel("ph1");
        phone.set("user_id", "nonexistent");

        assertFalse(phone.owner(userRepo(store, "users")).exists());
    }

    @Test
    void belongsToExistsReturnsTrueWhenOwnerExists() throws Exception {
        final FilterableStore store = new FilterableStore();

        userRepo(store, "users").save(new UserModel("u1"));

        final PhoneModel phone = new PhoneModel("ph1");
        phone.set("user_id", "u1");

        assertTrue(phone.owner(userRepo(store, "users")).exists());
    }

    // =========================================================================
    // BelongsToMany — attach / detach / get / sync
    // =========================================================================

    @Test
    void belongsToManyReturnsEmptyWhenNoPivotEntries() throws Exception {
        final FilterableStore roleStore = new FilterableStore();
        final FilterableStore pivotStore = new FilterableStore();
        final UserModel user = new UserModel("u1");

        assertTrue(user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).get().isEmpty());
    }

    @Test
    void belongsToManyAttachCreatesRelationship() throws Exception {
        final FilterableStore roleStore = new FilterableStore();
        final FilterableStore pivotStore = new FilterableStore();
        final UserModel user = new UserModel("u1");

        roleRepo(roleStore, "roles").save(roleWith("r1", "admin"));
        roleRepo(roleStore, "roles").save(roleWith("r2", "editor"));

        user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).attach("r1");
        user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).attach("r2");

        final List<RoleModel> roles = user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).get();
        assertEquals(2, roles.size());
        assertTrue(roles.stream().anyMatch(r -> "r1".equals(r.getId())));
        assertTrue(roles.stream().anyMatch(r -> "r2".equals(r.getId())));
    }

    @Test
    void belongsToManyAttachWithExtraAttrsStoredOnPivot() throws Exception {
        final FilterableStore pivotStore = new FilterableStore();
        final UserModel user = new UserModel("u1");
        final ModelRepository<PivotModel> pivot = pivotRepo(pivotStore, "user_roles");

        user.roles(roleRepo(new FilterableStore(), "roles"), pivot)
                .attach("r1", Map.of("granted_by", "admin"));

        final Optional<PivotModel> entry = pivot.find("u1_r1");
        assertTrue(entry.isPresent());
        assertEquals("admin", entry.get().get("granted_by"));
    }

    @Test
    void belongsToManyDetachRemovesSpecificRelationship() throws Exception {
        final FilterableStore roleStore = new FilterableStore();
        final FilterableStore pivotStore = new FilterableStore();
        final UserModel user = new UserModel("u1");

        roleRepo(roleStore, "roles").save(roleWith("r1", "admin"));
        roleRepo(roleStore, "roles").save(roleWith("r2", "editor"));

        user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).attach("r1");
        user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).attach("r2");
        user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).detach("r1");

        final List<RoleModel> roles = user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).get();
        assertEquals(1, roles.size());
        assertEquals("r2", roles.get(0).getId());
    }

    @Test
    void belongsToManyDetachAllClearsAllRelationships() throws Exception {
        final FilterableStore roleStore = new FilterableStore();
        final FilterableStore pivotStore = new FilterableStore();
        final UserModel user = new UserModel("u1");

        roleRepo(roleStore, "roles").save(roleWith("r1", "admin"));
        roleRepo(roleStore, "roles").save(roleWith("r2", "editor"));

        user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).attach("r1");
        user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).attach("r2");
        user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).detachAll();

        assertTrue(user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).get().isEmpty());
    }

    @Test
    void belongsToManySyncAddsAndRemovesEntries() throws Exception {
        final FilterableStore roleStore = new FilterableStore();
        final FilterableStore pivotStore = new FilterableStore();
        final UserModel user = new UserModel("u1");

        roleRepo(roleStore, "roles").save(roleWith("r1", "admin"));
        roleRepo(roleStore, "roles").save(roleWith("r2", "editor"));
        roleRepo(roleStore, "roles").save(roleWith("r3", "viewer"));

        user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).attach("r1");
        user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).attach("r2");

        // sync to [r2, r3]: removes r1, keeps r2, adds r3
        user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).sync(List.of("r2", "r3"));

        final List<RoleModel> roles = user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).get();
        assertEquals(2, roles.size());
        assertFalse(roles.stream().anyMatch(r -> "r1".equals(r.getId())));
        assertTrue(roles.stream().anyMatch(r -> "r2".equals(r.getId())));
        assertTrue(roles.stream().anyMatch(r -> "r3".equals(r.getId())));
    }

    @Test
    void belongsToManySyncToEmptyListDetachesAll() throws Exception {
        final FilterableStore roleStore = new FilterableStore();
        final FilterableStore pivotStore = new FilterableStore();
        final UserModel user = new UserModel("u1");

        roleRepo(roleStore, "roles").save(roleWith("r1", "admin"));
        user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).attach("r1");

        user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).sync(List.of());

        assertTrue(user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).get().isEmpty());
    }

    @Test
    void belongsToManyCountMatchesPivotEntries() throws Exception {
        final FilterableStore roleStore = new FilterableStore();
        final FilterableStore pivotStore = new FilterableStore();
        final UserModel user = new UserModel("u1");

        roleRepo(roleStore, "roles").save(roleWith("r1", "admin"));
        roleRepo(roleStore, "roles").save(roleWith("r2", "editor"));

        user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).attach("r1");
        user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).attach("r2");

        assertEquals(2L, user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).count());
    }

    @Test
    void belongsToManyWhereConstraintFiltersRelated() throws Exception {
        final FilterableStore roleStore = new FilterableStore();
        final FilterableStore pivotStore = new FilterableStore();
        final UserModel user = new UserModel("u1");

        final RoleModel admin = new RoleModel("r1");
        admin.set("name", "admin");
        admin.set("active", "true");
        roleRepo(roleStore, "roles").save(admin);

        final RoleModel inactive = new RoleModel("r2");
        inactive.set("name", "editor");
        inactive.set("active", "false");
        roleRepo(roleStore, "roles").save(inactive);

        user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).attach("r1");
        user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).attach("r2");

        final List<RoleModel> active = user.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles"))
                .where("active", "true")
                .get();
        assertEquals(1, active.size());
        assertEquals("r1", active.get(0).getId());
    }

    @Test
    void belongsToManyIsolatedAcrossParents() throws Exception {
        final FilterableStore roleStore = new FilterableStore();
        final FilterableStore pivotStore = new FilterableStore();
        final UserModel alice = new UserModel("u1");
        final UserModel bob = new UserModel("u2");

        roleRepo(roleStore, "roles").save(roleWith("r1", "admin"));
        roleRepo(roleStore, "roles").save(roleWith("r2", "editor"));

        alice.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).attach("r1");
        bob.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).attach("r2");

        final List<RoleModel> aliceRoles = alice.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).get();
        final List<RoleModel> bobRoles = bob.roles(roleRepo(roleStore, "roles"), pivotRepo(pivotStore, "user_roles")).get();

        assertEquals(1, aliceRoles.size());
        assertEquals("r1", aliceRoles.get(0).getId());
        assertEquals(1, bobRoles.size());
        assertEquals("r2", bobRoles.get(0).getId());
    }

    // =========================================================================
    // Model Factories not using FilterableStore (for withLocalKey/custom tests)
    // =========================================================================

    private static <T extends BaseModel> ModelRepository<T> repo(
            DataStore store, String prefix, ModelFactory<T> factory) {
        return new ModelRepository<>(store, prefix, factory);
    }

    // =========================================================================
    // Private builder helpers
    // =========================================================================

    private static PhoneModel phoneWith(String id, String userId, String number) {
        final PhoneModel p = new PhoneModel(id);
        p.set("user_id", userId);
        p.set("number", number);
        return p;
    }

    private static PostModel postWith(String id, String userId, String title) {
        final PostModel p = new PostModel(id);
        p.set("user_id", userId);
        p.set("title", title);
        return p;
    }

    private static RoleModel roleWith(String id, String name) {
        final RoleModel r = new RoleModel(id);
        r.set("name", name);
        return r;
    }

}
