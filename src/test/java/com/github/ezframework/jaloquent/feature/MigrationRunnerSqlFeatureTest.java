package com.github.ezframework.jaloquent.feature;

import com.github.ezframework.jaloquent.migration.Migration;
import com.github.ezframework.jaloquent.migration.MigrationRunner;
import com.github.ezframework.jaloquent.migration.Schema;
import com.github.ezframework.jaloquent.store.sql.JdbcStore;
import com.github.ezframework.javaquerybuilder.query.sql.SqlDialect;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SQL-level assertions for {@link MigrationRunner} tracking operations.
 *
 * <p>Uses a {@link RecordingJdbcStore} to capture the exact SQL strings and
 * bind-parameter lists emitted by {@link MigrationRunner} without a live
 * database, following the same pattern used in
 * {@link ModelRepositoryJdbcFeatureTest}.
 *
 * <h3>Call sequence verified</h3>
 * <ol>
 *   <li>{@code run()} / {@code rollback()} → {@code CREATE TABLE IF NOT EXISTS
 *       jaloquent_migrations …} (no bind params)</li>
 *   <li>{@code run()} / {@code rollback()} → {@code SELECT id, batch FROM
 *       jaloquent_migrations} (no bind params)</li>
 *   <li>{@code run()} → {@code INSERT INTO jaloquent_migrations …} with
 *       migration ID and batch number as bind parameters</li>
 *   <li>{@code rollback()} → {@code DELETE FROM jaloquent_migrations WHERE
 *       id = ?} with migration ID as bind parameter</li>
 * </ol>
 */
public class MigrationRunnerSqlFeatureTest {

    // =========================================================================
    // Fixtures
    // =========================================================================

    /**
     * Records every SQL string and parameter list passed to
     * {@link JdbcStore#executeUpdate} and {@link JdbcStore#query}.
     */
    static class RecordingJdbcStore implements JdbcStore {

        /** SQL strings passed to {@link #executeUpdate}. */
        final List<String> updateSqls = new ArrayList<>();

        /** Parameter lists passed to {@link #executeUpdate}. */
        final List<List<Object>> updateParams = new ArrayList<>();

        /** SQL strings passed to {@link #query}. */
        final List<String> querySqls = new ArrayList<>();

        /** Parameter lists passed to {@link #query}. */
        final List<List<Object>> queryParams = new ArrayList<>();

        /** Rows returned by the next {@link #query} call; must be set before calling. */
        List<Map<String, Object>> nextQueryRows = new ArrayList<>();

        @Override
        public int executeUpdate(final String sql, final List<Object> params) throws Exception {
            updateSqls.add(sql);
            updateParams.add(new ArrayList<>(params));
            return 1;
        }

        @Override
        public List<Map<String, Object>> query(final String sql,
                final List<Object> params) throws Exception {
            querySqls.add(sql);
            queryParams.add(new ArrayList<>(params));
            return new ArrayList<>(nextQueryRows);
        }
    }

    /**
     * A migration that performs no DDL in {@code up()} or {@code down()}, so
     * that only the MigrationRunner tracking SQL appears in the recording store.
     */
    static class NoOpMigration implements Migration {

        /** The migration identifier returned by {@link #getId()}. */
        private final String id;

        /**
         * @param id the migration identifier
         */
        NoOpMigration(final String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

        @Override
        public void up(final Schema schema) { }

        @Override
        public void down(final Schema schema) { }
    }

    /**
     * Dialects exercised by every MigrationRunner SQL-level test.
     *
     * @return argument stream containing MYSQL and POSTGRESQL dialects
     */
    static Stream<Arguments> dialects() {
        return Stream.of(
            Arguments.of(SqlDialect.MYSQL),
            Arguments.of(SqlDialect.POSTGRESQL)
        );
    }

    // =========================================================================
    // run() — ensureMigrationsTable CREATE TABLE DDL
    // =========================================================================

    @ParameterizedTest
    @MethodSource("dialects")
    void run_firstUpdateIsMigrationsTableCreateDdl(SqlDialect dialect) throws Exception {
        final RecordingJdbcStore store = new RecordingJdbcStore();
        new MigrationRunner(store, dialect,
                List.of(new NoOpMigration("m1"))).run();

        assertTrue(store.updateSqls.get(0).toUpperCase().contains("CREATE TABLE"),
                "First executeUpdate should be CREATE TABLE DDL");
        assertTrue(store.updateSqls.get(0).contains("jaloquent_migrations"),
                "CREATE TABLE DDL must reference jaloquent_migrations");
        assertTrue(store.updateParams.get(0).isEmpty(),
                "CREATE TABLE DDL must carry no bind parameters");
    }

    // =========================================================================
    // run() — getRanMigrations SELECT
    // =========================================================================

    @ParameterizedTest
    @MethodSource("dialects")
    void run_firstQuerySelectsFromMigrationsTable(SqlDialect dialect) throws Exception {
        final RecordingJdbcStore store = new RecordingJdbcStore();
        new MigrationRunner(store, dialect,
                List.of(new NoOpMigration("m1"))).run();

        assertTrue(store.querySqls.get(0).toUpperCase().contains("SELECT"),
                "First query should be a SELECT statement");
        assertTrue(store.querySqls.get(0).contains("jaloquent_migrations"),
                "SELECT must read from jaloquent_migrations");
        assertTrue(store.queryParams.get(0).isEmpty(),
                "SELECT must carry no bind parameters");
    }

    @ParameterizedTest
    @MethodSource("dialects")
    void run_idAndBatchColumnsSelectedFromMigrationsTable(SqlDialect dialect) throws Exception {
        final RecordingJdbcStore store = new RecordingJdbcStore();
        new MigrationRunner(store, dialect,
                List.of(new NoOpMigration("m1"))).run();

        final String selectSql = store.querySqls.get(0);
        assertTrue(selectSql.contains("id"), "SELECT must include the 'id' column");
        assertTrue(selectSql.contains("batch"), "SELECT must include the 'batch' column");
    }

    // =========================================================================
    // run() — recordMigration INSERT
    // =========================================================================

    @ParameterizedTest
    @MethodSource("dialects")
    void run_secondUpdateIsInsertTrackingRecord(SqlDialect dialect) throws Exception {
        final RecordingJdbcStore store = new RecordingJdbcStore();
        new MigrationRunner(store, dialect,
                List.of(new NoOpMigration("m1"))).run();

        // updateSqls[0] = CREATE TABLE jaloquent_migrations
        // updateSqls[1] = INSERT INTO jaloquent_migrations
        assertTrue(store.updateSqls.get(1).toUpperCase().contains("INSERT"),
                "Second executeUpdate should be INSERT tracking record");
        assertTrue(store.updateSqls.get(1).contains("jaloquent_migrations"),
                "INSERT must target jaloquent_migrations");
    }

    @ParameterizedTest
    @MethodSource("dialects")
    void run_migrationIdIsBindParamNotInterpolatedIntoInsertSql(SqlDialect dialect) throws Exception {
        final RecordingJdbcStore store = new RecordingJdbcStore();
        final String migId = "2026_05_01_marker_id_must_not_appear_in_sql";
        new MigrationRunner(store, dialect,
                List.of(new NoOpMigration(migId))).run();

        assertFalse(store.updateSqls.get(1).contains(migId),
                "Migration ID must not be interpolated into the INSERT SQL string");
        assertTrue(store.updateParams.get(1).contains(migId),
                "Migration ID must appear as a bind parameter");
    }

    @ParameterizedTest
    @MethodSource("dialects")
    void run_batchNumberOneForFirstRun(SqlDialect dialect) throws Exception {
        final RecordingJdbcStore store = new RecordingJdbcStore();
        new MigrationRunner(store, dialect,
                List.of(new NoOpMigration("m1"))).run();

        assertTrue(
                store.updateParams.get(1).stream()
                        .anyMatch(p -> p instanceof Number && ((Number) p).intValue() == 1),
                "Batch number 1 must appear as a bind parameter in the INSERT");
    }

    // =========================================================================
    // rollback() — removeMigrationRecord DELETE
    // =========================================================================

    @ParameterizedTest
    @MethodSource("dialects")
    void rollback_secondUpdateIsDeleteFromMigrationsTable(SqlDialect dialect) throws Exception {
        final RecordingJdbcStore store = new RecordingJdbcStore();
        final String migId = "2026_05_01_rollback_marker";
        final Map<String, Object> row = new HashMap<>();
        row.put("id", migId);
        row.put("batch", 1);
        store.nextQueryRows = List.of(row);

        new MigrationRunner(store, dialect,
                List.of(new NoOpMigration(migId))).rollback();

        // updateSqls[0] = CREATE TABLE jaloquent_migrations
        // updateSqls[1] = DELETE FROM jaloquent_migrations WHERE id = ?
        assertTrue(store.updateSqls.get(1).toUpperCase().startsWith("DELETE FROM"),
                "Rollback should issue a DELETE FROM statement");
        assertTrue(store.updateSqls.get(1).contains("jaloquent_migrations"),
                "DELETE must target jaloquent_migrations");
    }

    @ParameterizedTest
    @MethodSource("dialects")
    void rollback_migrationIdIsBindParamNotInterpolatedIntoDeleteSql(SqlDialect dialect) throws Exception {
        final RecordingJdbcStore store = new RecordingJdbcStore();
        final String migId = "2026_05_01_marker_must_not_appear_in_delete_sql";
        final Map<String, Object> row = new HashMap<>();
        row.put("id", migId);
        row.put("batch", 1);
        store.nextQueryRows = List.of(row);

        new MigrationRunner(store, dialect,
                List.of(new NoOpMigration(migId))).rollback();

        assertFalse(store.updateSqls.get(1).contains(migId),
                "Migration ID must not be interpolated into the DELETE SQL string");
        assertTrue(store.updateParams.get(1).contains(migId),
                "Migration ID must appear as a bind parameter");
    }
}
