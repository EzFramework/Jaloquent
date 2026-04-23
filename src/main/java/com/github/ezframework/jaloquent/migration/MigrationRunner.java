package com.github.ezframework.jaloquent.migration;

import com.github.ezframework.jaloquent.exception.MigrationException;
import com.github.ezframework.jaloquent.store.sql.JdbcStore;
import com.github.ezframework.javaquerybuilder.query.builder.ColumnType;
import com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder;
import com.github.ezframework.javaquerybuilder.query.builder.SelectBuilder;
import com.github.ezframework.javaquerybuilder.query.sql.SqlDialect;
import com.github.ezframework.javaquerybuilder.query.sql.SqlResult;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Executes and tracks database migrations for a Jaloquent-managed schema.
 *
 * <p>Migration state is persisted in a {@value #MIGRATIONS_TABLE} table that
 * is created automatically on the first call to {@link #run()} or
 * {@link #rollback()}. Each row stores the migration ID and the batch number it
 * was applied in.
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * List<Migration> migrations = List.of(
 *     new CreateUsersTable(),
 *     new CreateOrdersTable()
 * );
 * MigrationRunner runner = new MigrationRunner(store, SqlDialect.MYSQL, migrations);
 * runner.run();      // applies all pending migrations in list order
 * runner.rollback(); // reverts the last batch
 * }</pre>
 *
 * <h2>Batch semantics</h2>
 * <p>All migrations applied in a single {@link #run()} call share the same
 * batch number. {@link #rollback()} reverts every migration belonging to the
 * most recent batch, in reverse list order.
 */
public class MigrationRunner {

    /** Name of the table used to track which migrations have been applied. */
    static final String MIGRATIONS_TABLE = "jaloquent_migrations";

    /** Logger for migration lifecycle events. */
    private static final Logger log = LoggerFactory.getLogger(MigrationRunner.class);

    /** The JDBC store used to execute all DDL and tracking queries. */
    private final JdbcStore store;

    /** SQL dialect used when building DDL and DML statements. */
    private final SqlDialect dialect;

    /** The ordered list of all known migrations. */
    private final List<Migration> migrations;

    /** Schema executor passed to migration {@code up} and {@code down} callbacks. */
    private final Schema schema;

    /**
     * Constructs a runner for the given store, dialect, and ordered migration list.
     *
     * @param store      the JDBC store to execute DDL and tracking queries against;
     *                   must not be {@code null}
     * @param dialect    the SQL dialect for identifier quoting and DDL rendering;
     *                   may be {@code null} to use the standard dialect
     * @param migrations the complete, ordered list of all known migrations;
     *                   must not be {@code null}
     */
    public MigrationRunner(final JdbcStore store, final SqlDialect dialect,
            final List<Migration> migrations) {
        this.store = store;
        this.dialect = dialect;
        this.migrations = List.copyOf(migrations);
        this.schema = new Schema(store, dialect);
    }

    /**
     * Applies all pending migrations in list order.
     *
     * <p>Migrations whose {@link Migration#getId()} already appears in the
     * {@value #MIGRATIONS_TABLE} table are skipped. All newly-applied migrations
     * are grouped into the same batch number.
     *
     * @throws MigrationException if any migration fails or if the tracking table
     *                            cannot be queried or updated
     */
    public void run() throws MigrationException {
        ensureMigrationsTable();
        final Map<String, Integer> ran = getRanMigrations();
        final int nextBatch = nextBatch(ran);
        for (final Migration migration : migrations) {
            if (ran.containsKey(migration.getId())) {
                continue;
            }
            log.info("Running migration: {}", migration.getId());
            migration.up(schema);
            recordMigration(migration.getId(), nextBatch);
        }
    }

    /**
     * Reverts all migrations belonging to the most recent batch.
     *
     * <p>Migrations are rolled back in reverse list order. After each
     * {@link Migration#down(Schema)} call, the corresponding tracking record is
     * removed from {@value #MIGRATIONS_TABLE}. This method is a no-op when no
     * migrations have been applied.
     *
     * @throws MigrationException if any migration fails or if the tracking table
     *                            cannot be queried or updated
     */
    public void rollback() throws MigrationException {
        ensureMigrationsTable();
        final Map<String, Integer> ran = getRanMigrations();
        if (ran.isEmpty()) {
            return;
        }
        final int lastBatch = Collections.max(ran.values());
        final List<Migration> reversed = new ArrayList<>(migrations);
        Collections.reverse(reversed);
        for (final Migration migration : reversed) {
            if (!ran.containsKey(migration.getId())) {
                continue;
            }
            final int batchNum = ran.get(migration.getId());
            if (batchNum != lastBatch) {
                continue;
            }
            log.info("Rolling back migration: {}", migration.getId());
            migration.down(schema);
            removeMigrationRecord(migration.getId());
        }
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /**
     * Creates the {@value #MIGRATIONS_TABLE} tracking table if it does not
     * already exist.
     *
     * @throws MigrationException if the DDL execution fails
     */
    private void ensureMigrationsTable() throws MigrationException {
        final SqlResult result = QueryBuilder.createTable(MIGRATIONS_TABLE)
            .ifNotExists()
            .column("id", ColumnType.varChar(255).notNull())
            .column("batch", ColumnType.INT.notNull())
            .primaryKey("id")
            .build(dialect);
        try {
            store.executeUpdate(result.getSql(), result.getParameters());
        }
        catch (Exception e) {
            throw new MigrationException("Failed to ensure migrations table exists", e);
        }
    }

    /**
     * Queries the {@value #MIGRATIONS_TABLE} table and returns a map of
     * migration ID to batch number for every already-applied migration.
     *
     * @return a mutable map of migration ID → batch number; empty when no
     *         migrations have been applied
     * @throws MigrationException if the query fails
     */
    private Map<String, Integer> getRanMigrations() throws MigrationException {
        final SqlResult queryResult = new SelectBuilder()
            .from(MIGRATIONS_TABLE)
            .select("id", "batch")
            .build(dialect);
        try {
            final List<Map<String, Object>> rows = store.query(
                queryResult.getSql(), queryResult.getParameters());
            final Map<String, Integer> ran = new HashMap<>();
            for (final Map<String, Object> row : rows) {
                final String id = (String) row.get("id");
                final Object batchVal = row.get("batch");
                ran.put(id, ((Number) batchVal).intValue());
            }
            return ran;
        }
        catch (Exception e) {
            throw new MigrationException("Failed to query migrations table", e);
        }
    }

    /**
     * Returns the batch number to use for the next {@link #run()} call.
     *
     * @param ran the current set of applied migrations and their batch numbers
     * @return 1 when no migrations have been applied; otherwise the current
     *         maximum batch number incremented by one
     */
    private int nextBatch(final Map<String, Integer> ran) {
        if (ran.isEmpty()) {
            return 1;
        }
        return Collections.max(ran.values()) + 1;
    }

    /**
     * Inserts a tracking record for a successfully-applied migration.
     *
     * @param id    the migration identifier
     * @param batch the batch number assigned to this run
     * @throws MigrationException if the insert fails
     */
    private void recordMigration(final String id, final int batch) throws MigrationException {
        final SqlResult result = QueryBuilder.insertInto(MIGRATIONS_TABLE)
            .value("id", id)
            .value("batch", batch)
            .build(dialect);
        try {
            store.executeUpdate(result.getSql(), result.getParameters());
        }
        catch (Exception e) {
            throw new MigrationException("Failed to record migration '" + id + "'", e);
        }
    }

    /**
     * Removes the tracking record for a migration that has been rolled back.
     *
     * @param id the migration identifier to remove
     * @throws MigrationException if the delete fails
     */
    private void removeMigrationRecord(final String id) throws MigrationException {
        final SqlResult result = QueryBuilder.deleteFrom(MIGRATIONS_TABLE)
            .whereEquals("id", id)
            .build(dialect);
        try {
            store.executeUpdate(result.getSql(), result.getParameters());
        }
        catch (Exception e) {
            throw new MigrationException("Failed to remove migration record '" + id + "'", e);
        }
    }
}
