package com.github.ezframework.jaloquent.model;

import com.github.ezframework.jaloquent.store.DataStore;
import com.github.ezframework.jaloquent.store.sql.JdbcStore;
import com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder;
import com.github.ezframework.javaquerybuilder.query.sql.SqlDialect;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Parameterized matrix tests that exercise every {@link ModelRepository} SQL operation
 * against both {@link SqlDialect#MYSQL} and {@link SqlDialect#SQLITE} dialects.
 *
 * <p>Each test verifies two invariants:
 * <ol>
 *   <li>The rendered SQL contains dialect-specific identifier quoting where applicable.</li>
 *   <li>All user-supplied values travel as {@code ?} bind parameters, never embedded in SQL.</li>
 * </ol>
 *
 * <p>Each test registers its own UUID-prefixed table to avoid contaminating the
 * JVM-static {@link TableRegistry}.
 */
public class DialectMatrixFeatureTest {

    // =========================================================================
    // Fixtures
    // =========================================================================

    static class TestModel extends Model {

        /** Construct a TestModel with the given id. @param id model identifier */
        TestModel(String id) {
            super(id);
        }

    }

    /**
     * Minimal JDBC store that records every SQL statement and its parameters.
     * Implements both {@link DataStore} and {@link JdbcStore} so that
     * {@link ModelRepository} always takes the SQL path when a table is registered.
     */
    static class RecordingJdbcStore implements DataStore, JdbcStore {

        /** Accumulated UPDATE/DELETE/INSERT SQL strings. */
        final List<String> updateSqls = new ArrayList<>();

        /** Accumulated UPDATE/DELETE/INSERT parameter lists. */
        final List<List<Object>> updateParams = new ArrayList<>();

        /** Accumulated SELECT SQL strings. */
        final List<String> querySqls = new ArrayList<>();

        /** Accumulated SELECT parameter lists. */
        final List<List<Object>> queryParams = new ArrayList<>();

        /** Rows returned by the next {@link #query} call. */
        List<Map<String, Object>> nextQueryRows = new ArrayList<>();

        @Override
        public void save(String path, Map<String, Object> data) { }

        @Override
        public Optional<Map<String, Object>> load(String path) {
            return Optional.empty();
        }

        @Override
        public void delete(String path) { }

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
            updateSqls.add(sql);
            updateParams.add(new ArrayList<>(params));
            return 1;
        }

    }

    // =========================================================================
    // Dialect × quote-character matrix
    // =========================================================================

    /**
     * Each row: dialect, opening quote char, closing quote char.
     *
     * <p>STANDARD produces unquoted identifiers; MYSQL uses back-ticks;
     * SQLITE uses double-quotes.
     *
     * @return argument stream for parameterized tests
     */
    static Stream<Arguments> selectDialects() {
        return Stream.of(
            Arguments.of(SqlDialect.STANDARD, "",  ""),
            Arguments.of(SqlDialect.MYSQL,    "`", "`"),
            Arguments.of(SqlDialect.SQLITE,   "\"", "\"")
        );
    }

    /** Dialect arguments without quoting expectations – used for DML tests. */
    static Stream<Arguments> allDialects() {
        return Stream.of(
            Arguments.of(SqlDialect.STANDARD),
            Arguments.of(SqlDialect.MYSQL),
            Arguments.of(SqlDialect.SQLITE)
        );
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    private static String uniquePrefix(SqlDialect dialect) {
        return "dialect-" + dialect.getClass().getSimpleName() + "-" + UUID.randomUUID();
    }

    private static ModelRepository<TestModel> repo(
            RecordingJdbcStore store, String prefix, SqlDialect dialect) {
        return new ModelRepository<>(store, prefix, (id, data) -> new TestModel(id), dialect);
    }

    private static Map<String, String> cols(String... pairs) {
        final Map<String, String> m = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length - 1; i += 2) {
            m.put(pairs[i], pairs[i + 1]);
        }
        return m;
    }

    // =========================================================================
    // find() — SELECT quoting matrix
    // =========================================================================

    /**
     * find() must produce a SELECT … FROM players … WHERE id = ? LIMIT 1 SQL for all dialects.
     *
     * @param dialect  the SQL dialect under test
     * @param open     expected opening quote character (unused — SelectBuilder does not quote)
     * @param close    expected closing quote character (unused — SelectBuilder does not quote)
     * @throws Exception propagated from JDBC store
     */
    @ParameterizedTest
    @MethodSource("selectDialects")
    void findProducesSelectStructureForAllDialects(SqlDialect dialect, String open, String close)
            throws Exception {
        final String prefix = uniquePrefix(dialect);
        TableRegistry.register(prefix, "players", cols("id", "VARCHAR(36)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        repo(store, prefix, dialect).find("p1");

        final String sql = store.querySqls.get(0).toUpperCase();
        assertTrue(sql.startsWith("SELECT"), "Expected SELECT but got: " + sql);
        assertTrue(sql.contains("FROM"),     "Expected FROM in: " + sql);
        assertTrue(sql.contains("WHERE"),    "Expected WHERE in: " + sql);
    }

    /**
     * find() must always pass the id value as a bind parameter, never in the SQL string.
     *
     * @param dialect  the SQL dialect under test
     * @param open     expected opening quote character (unused)
     * @param close    expected closing quote character (unused)
     * @throws Exception propagated from JDBC store
     */
    @ParameterizedTest
    @MethodSource("selectDialects")
    void findPassesIdAsBindParam(SqlDialect dialect, String open, String close)
            throws Exception {
        final String prefix = uniquePrefix(dialect);
        TableRegistry.register(prefix, "players", cols("id", "VARCHAR(36)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        repo(store, prefix, dialect).find("target-id-42");

        final String sql = store.querySqls.get(0);
        assertFalse(sql.contains("target-id-42"),
            "User-supplied id must not appear in SQL but got: " + sql);
        assertTrue(store.queryParams.get(0).contains("target-id-42"),
            "User-supplied id must be a bind parameter");
    }

    /**
     * find() must include LIMIT 1 in the SQL to avoid full-table scans.
     *
     * @param dialect  the SQL dialect under test
     * @param open     expected opening quote character (unused)
     * @param close    expected closing quote character (unused)
     * @throws Exception propagated from JDBC store
     */
    @ParameterizedTest
    @MethodSource("selectDialects")
    void findIncludesLimit1(SqlDialect dialect, String open, String close) throws Exception {
        final String prefix = uniquePrefix(dialect);
        TableRegistry.register(prefix, "players", cols("id", "VARCHAR(36)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        repo(store, prefix, dialect).find("any");

        assertTrue(store.querySqls.get(0).contains("LIMIT 1"),
            "find() must include LIMIT 1");
    }

    // =========================================================================
    // exists() — SELECT quoting matrix
    // =========================================================================

    /**
     * exists() must produce a SELECT 1 FROM … WHERE id = ? LIMIT 1 statement for all dialects.
     *
     * @param dialect  the SQL dialect under test
     * @param open     expected opening quote character (unused — SelectBuilder does not quote)
     * @param close    expected closing quote character (unused — SelectBuilder does not quote)
     * @throws Exception propagated from JDBC store
     */
    @ParameterizedTest
    @MethodSource("selectDialects")
    void existsProducesSelectOneStructureForAllDialects(SqlDialect dialect, String open, String close)
            throws Exception {
        final String prefix = uniquePrefix(dialect);
        TableRegistry.register(prefix, "items", cols("id", "VARCHAR(36)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        repo(store, prefix, dialect).exists("i1");

        final String sql = store.querySqls.get(0);
        assertTrue(sql.contains("1"),     "Expected SELECT 1 column in: " + sql);
        assertTrue(sql.toUpperCase().contains("LIMIT 1"), "Expected LIMIT 1 in: " + sql);
    }

    /**
     * exists() must pass the id as a bind parameter for all dialects.
     *
     * @param dialect  the SQL dialect under test
     * @param open     expected opening quote character (unused)
     * @param close    expected closing quote character (unused)
     * @throws Exception propagated from JDBC store
     */
    @ParameterizedTest
    @MethodSource("selectDialects")
    void existsPassesIdAsBindParam(SqlDialect dialect, String open, String close)
            throws Exception {
        final String prefix = uniquePrefix(dialect);
        TableRegistry.register(prefix, "items", cols("id", "VARCHAR(36)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        repo(store, prefix, dialect).exists("secret-id");

        assertFalse(store.querySqls.get(0).contains("secret-id"),
            "User-supplied id must not appear in SQL");
        assertTrue(store.queryParams.get(0).contains("secret-id"),
            "User-supplied id must be a bind parameter");
    }

    // =========================================================================
    // query(Query) — SELECT quoting matrix
    // =========================================================================

    /**
     * query() must apply identifier quoting for the registered table per dialect.
     *
     * @param dialect  the SQL dialect under test
     * @param open     expected opening quote character
     * @param close    expected closing quote character
     * @throws Exception propagated from JDBC store
     */
    @ParameterizedTest
    @MethodSource("selectDialects")
    void queryQuotesTablePerDialect(SqlDialect dialect, String open, String close)
            throws Exception {
        final String prefix = uniquePrefix(dialect);
        TableRegistry.register(prefix, "scores", cols("id", "VARCHAR(36)", "points", "INT"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        repo(store, prefix, dialect).query(
            new QueryBuilder().whereEquals("points", 100).build()
        );

        final String sql       = store.querySqls.get(0);
        final String quotedTbl = open + "scores" + close;
        assertTrue(sql.contains(quotedTbl),
            "Expected " + quotedTbl + " in SQL: " + sql);
    }

    /**
     * query(Query) must pass WHERE values as bind parameters, not in the SQL string.
     *
     * @param dialect  the SQL dialect under test
     * @param open     expected opening quote character (unused)
     * @param close    expected closing quote character (unused)
     * @throws Exception propagated from JDBC store
     */
    @ParameterizedTest
    @MethodSource("selectDialects")
    void queryPassesWhereValueAsBindParam(SqlDialect dialect, String open, String close)
            throws Exception {
        final String prefix = uniquePrefix(dialect);
        TableRegistry.register(prefix, "scores", cols("id", "VARCHAR(36)", "name", "VARCHAR(255)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        repo(store, prefix, dialect).query(
            new QueryBuilder().whereEquals("name", "injection-payload").build()
        );

        assertFalse(store.querySqls.get(0).contains("injection-payload"),
            "WHERE value must not appear in SQL");
        assertTrue(store.queryParams.get(0).contains("injection-payload"),
            "WHERE value must be a bind parameter");
    }

    // =========================================================================
    // delete() — DML params matrix
    // =========================================================================

    /**
     * delete() must generate a single DELETE statement and pass the id as a bind param
     * regardless of dialect.
     *
     * @param dialect the SQL dialect under test
     * @throws Exception propagated from JDBC store
     */
    @ParameterizedTest
    @MethodSource("allDialects")
    void deletePassesIdAsBindParam(SqlDialect dialect) throws Exception {
        final String prefix = uniquePrefix(dialect);
        TableRegistry.register(prefix, "orders", cols("id", "VARCHAR(36)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        repo(store, prefix, dialect).delete("order-99");

        assertEquals(1, store.updateSqls.size(),
            "delete() must issue exactly one statement");
        final String sql = store.updateSqls.get(0).toUpperCase();
        assertTrue(sql.startsWith("DELETE FROM"),
            "Expected DELETE FROM but got: " + sql);
        assertFalse(store.updateSqls.get(0).contains("order-99"),
            "User-supplied id must not appear in SQL");
        assertTrue(store.updateParams.get(0).contains("order-99"),
            "User-supplied id must be a bind parameter");
    }

    // =========================================================================
    // deleteAll() — whereIn bulk delete matrix
    // =========================================================================

    /**
     * deleteAll() must issue exactly ONE statement using IN (?, …) for any list size,
     * regardless of dialect, and never embed ids in the SQL string.
     *
     * @param dialect the SQL dialect under test
     * @throws Exception propagated from JDBC store
     */
    @ParameterizedTest
    @MethodSource("allDialects")
    void deleteAllIssuesSingleWhereInStatement(SqlDialect dialect) throws Exception {
        final String prefix = uniquePrefix(dialect);
        TableRegistry.register(prefix, "sessions", cols("id", "VARCHAR(36)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        repo(store, prefix, dialect).deleteAll(List.of("s1", "s2", "s3"));

        assertEquals(1, store.updateSqls.size(),
            "deleteAll() must issue exactly one bulk-delete statement");
        final String sql = store.updateSqls.get(0).toUpperCase();
        assertTrue(sql.contains("IN"),
            "Expected IN clause but got: " + sql);
    }

    /**
     * deleteAll() must pass all ids as bind parameters, not embed them in the SQL string.
     *
     * @param dialect the SQL dialect under test
     * @throws Exception propagated from JDBC store
     */
    @ParameterizedTest
    @MethodSource("allDialects")
    void deleteAllPassesAllIdsAsBindParams(SqlDialect dialect) throws Exception {
        final String prefix = uniquePrefix(dialect);
        TableRegistry.register(prefix, "sessions", cols("id", "VARCHAR(36)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        final List<String> ids = List.of("id-alpha", "id-beta", "id-gamma");
        repo(store, prefix, dialect).deleteAll(ids);

        final String sql          = store.updateSqls.get(0);
        final List<Object> params = store.updateParams.get(0);
        for (final String id : ids) {
            assertFalse(sql.contains(id),
                "Id '" + id + "' must not appear in SQL string");
            assertTrue(params.contains(id),
                "Id '" + id + "' must be a bind parameter");
        }
        assertEquals(3, params.size(),
            "Expected exactly 3 bind parameters for 3 ids");
    }

    /**
     * deleteAll() must produce IN with exactly N placeholders for N ids.
     *
     * @param dialect the SQL dialect under test
     * @throws Exception propagated from JDBC store
     */
    @ParameterizedTest
    @MethodSource("allDialects")
    void deleteAllProducesCorrectPlaceholderCount(SqlDialect dialect) throws Exception {
        final String prefix = uniquePrefix(dialect);
        TableRegistry.register(prefix, "sessions", cols("id", "VARCHAR(36)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        repo(store, prefix, dialect).deleteAll(List.of("a", "b", "c", "d", "e"));

        assertEquals(5, store.updateParams.get(0).size(),
            "Expected 5 bind parameters for 5 ids");
    }

    // =========================================================================
    // deleteWhere() — column value as bind param matrix
    // =========================================================================

    /**
     * deleteWhere() must pass the filter value as a bind parameter for all dialects.
     *
     * @param dialect the SQL dialect under test
     * @throws Exception propagated from JDBC store
     */
    @ParameterizedTest
    @MethodSource("allDialects")
    void deleteWherePassesValueAsBindParam(SqlDialect dialect) throws Exception {
        final String prefix = uniquePrefix(dialect);
        TableRegistry.register(prefix, "logs",
            cols("id", "VARCHAR(36)", "level", "VARCHAR(10)"));

        final RecordingJdbcStore store = new RecordingJdbcStore();
        repo(store, prefix, dialect).deleteWhere("level", "DEBUG");

        final String sql = store.updateSqls.get(0);
        assertFalse(sql.contains("DEBUG"),
            "Filter value must not appear in SQL string");
        assertTrue(store.updateParams.get(0).contains("DEBUG"),
            "Filter value must be a bind parameter");
    }

    // =========================================================================
    // save() — INSERT params matrix
    // =========================================================================

    /**
     * save() must pass all column values as bind parameters and never embed them in SQL.
     *
     * @param dialect the SQL dialect under test
     * @throws Exception propagated from JDBC store
     */
    @ParameterizedTest
    @MethodSource("allDialects")
    void savePassesAllColumnValuesAsBindParams(SqlDialect dialect) throws Exception {
        final String prefix = uniquePrefix(dialect);
        TableRegistry.register(prefix, "users",
            cols("id", "VARCHAR(36)", "name", "VARCHAR(255)"));

        final TestModel m = new TestModel("user-1");
        m.set("name", "payload-value");

        final RecordingJdbcStore store = new RecordingJdbcStore();
        repo(store, prefix, dialect).save(m);

        final String sql = store.updateSqls.get(0);
        assertFalse(sql.contains("payload-value"),
            "Column value must not appear in SQL string");
        assertTrue(store.updateParams.get(0).contains("payload-value"),
            "Column value must be a bind parameter");
    }

    /**
     * save() must include the model id as the first bind parameter.
     *
     * @param dialect the SQL dialect under test
     * @throws Exception propagated from JDBC store
     */
    @ParameterizedTest
    @MethodSource("allDialects")
    void saveIncludesModelIdAsFirstParam(SqlDialect dialect) throws Exception {
        final String prefix = uniquePrefix(dialect);
        TableRegistry.register(prefix, "users",
            cols("id", "VARCHAR(36)", "name", "VARCHAR(255)"));

        final TestModel m = new TestModel("id-xyz");
        m.set("name", "Alice");

        final RecordingJdbcStore store = new RecordingJdbcStore();
        repo(store, prefix, dialect).save(m);

        assertEquals("id-xyz", store.updateParams.get(0).get(0),
            "Model id must be the first bind parameter");
    }

}
