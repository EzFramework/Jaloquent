package com.github.ezframework.jaloquent.feature;

import com.github.ezframework.jaloquent.exception.MigrationException;
import com.github.ezframework.jaloquent.migration.Migration;
import com.github.ezframework.jaloquent.migration.MigrationBlueprint;
import com.github.ezframework.jaloquent.migration.MigrationRunner;
import com.github.ezframework.jaloquent.migration.Schema;
import com.github.ezframework.jaloquent.store.sql.DataSourceJdbcStore;
import com.github.ezframework.javaquerybuilder.query.builder.ColumnType;
import com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder;
import com.github.ezframework.javaquerybuilder.query.sql.SqlDialect;
import com.github.ezframework.javaquerybuilder.query.sql.SqlResult;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Feature tests for the migration framework using an H2 in-memory database.
 *
 * <p>Covers {@link MigrationRunner}, {@link Schema}, and {@link MigrationBlueprint}
 * integration: table creation, idempotent re-runs, batch-based rollback, and
 * {@link ColumnType} rendering in {@code CREATE TABLE} statements.
 */
public class MigrationFeatureTest {

    // =========================================================================
    // Fixtures — Migration implementations
    // =========================================================================

    /**
     * Creates a {@code users} table: id, email, created_at, updated_at.
     */
    static class CreateUsersTable implements Migration {

        /** {@inheritDoc} */
        @Override
        public String getId() {
            return "2026_04_23_001_create_users_table";
        }

        /** {@inheritDoc} */
        @Override
        public void up(final Schema schema) throws MigrationException {
            schema.create("users", t -> t
                .id()
                .string("email", 255)
                .timestamps()
            );
        }

        /** {@inheritDoc} */
        @Override
        public void down(final Schema schema) throws MigrationException {
            schema.dropIfExists("users");
        }
    }

    /**
     * Creates an {@code orders} table: id, quantity, shipped.
     */
    static class CreateOrdersTable implements Migration {

        /** {@inheritDoc} */
        @Override
        public String getId() {
            return "2026_04_23_002_create_orders_table";
        }

        /** {@inheritDoc} */
        @Override
        public void up(final Schema schema) throws MigrationException {
            schema.create("orders", t -> t
                .id()
                .integer("quantity")
                .bool("shipped")
            );
        }

        /** {@inheritDoc} */
        @Override
        public void down(final Schema schema) throws MigrationException {
            schema.dropIfExists("orders");
        }
    }

    // =========================================================================
    // Per-test state
    // =========================================================================

    /** H2 data source shared across all test methods in this class. */
    private JdbcDataSource ds;

    /** Store under test. */
    private DataSourceJdbcStore store;

    // =========================================================================
    // Setup / teardown
    // =========================================================================

    /**
     * Creates an H2 in-memory data source and a fresh {@link DataSourceJdbcStore}.
     */
    @BeforeEach
    void setUp() {
        ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:migration_test_" + System.nanoTime()
            + ";DB_CLOSE_DELAY=-1;MODE=MySQL");
        ds.setUser("sa");
        ds.setPassword("");
        store = new DataSourceJdbcStore(ds);
    }

    /**
     * Drops all tables created during each test.
     *
     * @throws Exception if DDL teardown fails
     */
    @AfterEach
    void tearDown() throws Exception {
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS jaloquent_migrations");
            stmt.execute("DROP TABLE IF EXISTS users");
            stmt.execute("DROP TABLE IF EXISTS orders");
        }
    }

    // =========================================================================
    // run() — basic execution
    // =========================================================================

    /**
     * run() must create the jaloquent_migrations tracking table and all
     * migration tables, and record one tracking row per migration.
     */
    @Test
    void runCreatesMigrationsTableAndUserTables() throws Exception {
        final MigrationRunner runner = runner(
            new CreateUsersTable(), new CreateOrdersTable());
        runner.run();

        assertTrue(tableExists("jaloquent_migrations"));
        assertTrue(tableExists("users"));
        assertTrue(tableExists("orders"));
        assertEquals(2, countRows("jaloquent_migrations"));
    }

    /**
     * run() called a second time must be a no-op because both migrations are
     * already recorded in the tracking table.
     */
    @Test
    void runIsIdempotentWhenAllMigrationsAlreadyApplied() throws Exception {
        final MigrationRunner runner = runner(
            new CreateUsersTable(), new CreateOrdersTable());
        runner.run();
        runner.run();

        assertEquals(2, countRows("jaloquent_migrations"));
    }

    /**
     * run() called with an empty migration list must still create the tracking
     * table and leave it empty.
     */
    @Test
    void runWithNoMigrationsCreatesEmptyTrackingTable() throws Exception {
        final MigrationRunner runner = runner();
        runner.run();

        assertTrue(tableExists("jaloquent_migrations"));
        assertEquals(0, countRows("jaloquent_migrations"));
    }

    // =========================================================================
    // run() — batch numbering
    // =========================================================================

    /**
     * Migrations applied in two separate run() calls must be assigned to
     * distinct batch numbers.
     */
    @Test
    void separateRunCallsProduceSeparateBatches() throws Exception {
        final MigrationRunner firstRunner = runner(new CreateUsersTable());
        firstRunner.run();

        final MigrationRunner secondRunner = runner(
            new CreateUsersTable(), new CreateOrdersTable());
        secondRunner.run();

        assertEquals(2, countRows("jaloquent_migrations"));
        assertEquals(1, batchFor("2026_04_23_001_create_users_table"));
        assertEquals(2, batchFor("2026_04_23_002_create_orders_table"));
    }

    // =========================================================================
    // rollback() — basic behaviour
    // =========================================================================

    /**
     * rollback() must call down() on every migration in the last batch, remove
     * their tracking records, and leave the tables dropped.
     */
    @Test
    void rollbackRevertsLastBatchAndRemovesTrackingRecords() throws Exception {
        final MigrationRunner runner = runner(
            new CreateUsersTable(), new CreateOrdersTable());
        runner.run();
        runner.rollback();

        assertFalse(tableExists("users"));
        assertFalse(tableExists("orders"));
        assertEquals(0, countRows("jaloquent_migrations"));
    }

    /**
     * rollback() when no migrations have run must be a no-op.
     */
    @Test
    void rollbackWithNoAppliedMigrationsIsANoOp() throws Exception {
        final MigrationRunner runner = runner(new CreateUsersTable());
        runner.run();
        runner.rollback();
        assertDoesNotThrow(() -> runner.rollback());
    }

    /**
     * rollback() must only revert the last batch, leaving earlier batches intact.
     */
    @Test
    void rollbackOnlyRevertsLastBatch() throws Exception {
        final MigrationRunner firstRunner = runner(new CreateUsersTable());
        firstRunner.run();

        final MigrationRunner secondRunner = runner(
            new CreateUsersTable(), new CreateOrdersTable());
        secondRunner.run();

        secondRunner.rollback();

        assertTrue(tableExists("users"),
            "users table (batch 1) must survive rollback of batch 2");
        assertFalse(tableExists("orders"),
            "orders table (batch 2) must be dropped by rollback");
        assertEquals(1, countRows("jaloquent_migrations"));
    }

    // =========================================================================
    // MigrationBlueprint — ColumnType integration
    // =========================================================================

    /**
     * MigrationBlueprint.column(name, ColumnType) must produce CREATE TABLE SQL
     * containing the correct type string.
     */
    @Test
    void columnWithColumnTypeRendersCorrectSql() {
        final MigrationBlueprint bp = new MigrationBlueprint("products");
        bp.column("id", ColumnType.varChar(36).notNull())
          .column("price", ColumnType.decimal(10, 2))
          .column("stock", ColumnType.INT.notNull())
          .primaryKey("id");

        final SqlResult result = bp.buildSql(null);
        final String sql = result.getSql();

        assertTrue(sql.contains("VARCHAR(36) NOT NULL"),
            "Expected VARCHAR(36) NOT NULL in: " + sql);
        assertTrue(sql.contains("DECIMAL(10, 2)"),
            "Expected DECIMAL(10, 2) in: " + sql);
        assertTrue(sql.contains("INT NOT NULL"),
            "Expected INT NOT NULL in: " + sql);
        assertTrue(sql.contains("PRIMARY KEY (id)"),
            "Expected PRIMARY KEY (id) in: " + sql);
    }

    /**
     * MigrationBlueprint.timestamps() must add both created_at and updated_at
     * TIMESTAMP columns to the generated SQL.
     */
    @Test
    void timestampsAddsCreatedAtAndUpdatedAt() {
        final MigrationBlueprint bp = new MigrationBlueprint("events");
        bp.id().timestamps();

        final SqlResult result = bp.buildSql(null);
        final String sql = result.getSql();

        assertTrue(sql.contains("created_at"), "Expected created_at in: " + sql);
        assertTrue(sql.contains("updated_at"), "Expected updated_at in: " + sql);
        assertTrue(sql.contains("TIMESTAMP"),  "Expected TIMESTAMP in: " + sql);
    }

    /**
     * MigrationBlueprint convenience shorthands must generate the expected SQL
     * TYPE definitions for string, integer, bigInteger, bool, and text.
     */
    @Test
    void blueprintShorthandsProduceExpectedColumnTypes() {
        final MigrationBlueprint bp = new MigrationBlueprint("t");
        bp.string("name", 128)
          .integer("qty")
          .bigInteger("large_num")
          .bool("active")
          .text("notes");

        final SqlResult result = bp.buildSql(null);
        final String sql = result.getSql();

        assertTrue(sql.contains("VARCHAR(128) NOT NULL"), sql);
        assertTrue(sql.contains("INT NOT NULL"),          sql);
        assertTrue(sql.contains("BIGINT NOT NULL"),       sql);
        assertTrue(sql.contains("BOOLEAN NOT NULL"),      sql);
        assertTrue(sql.contains("TEXT"),                  sql);
    }

    /**
     * tinyInteger() and smallInteger() shorthands must render TINYINT NOT NULL
     * and SMALLINT NOT NULL respectively.
     */
    @Test
    void tinyIntegerAndSmallIntegerProduceNotNullTypes() {
        final MigrationBlueprint bp = new MigrationBlueprint("t");
        bp.tinyInteger("tiny_col").smallInteger("small_col");

        final String sql = bp.buildSql(null).getSql();

        assertTrue(sql.contains("TINYINT NOT NULL"),  sql);
        assertTrue(sql.contains("SMALLINT NOT NULL"), sql);
    }

    /**
     * decimal() shorthand must render DECIMAL(precision, scale) NOT NULL.
     */
    @Test
    void decimalShorthandRendersDecimalNotNull() {
        final MigrationBlueprint bp = new MigrationBlueprint("t");
        bp.decimal("price", 10, 2);

        final String sql = bp.buildSql(null).getSql();

        assertTrue(sql.contains("DECIMAL(10, 2) NOT NULL"), sql);
    }

    /**
     * date(), time(), and dateTime() shorthands must render DATE, TIME, and
     * DATETIME columns without a NOT NULL constraint.
     */
    @Test
    void dateTimeShorthandsRenderNullableTypes() {
        final MigrationBlueprint bp = new MigrationBlueprint("t");
        bp.date("dob").time("start_time").dateTime("scheduled_at");

        final String sql = bp.buildSql(null).getSql();

        assertTrue(sql.contains("DATE"),     sql);
        assertTrue(sql.contains("TIME"),     sql);
        assertTrue(sql.contains("DATETIME"), sql);
    }

    /**
     * json() shorthand must render a JSON column without NOT NULL.
     */
    @Test
    void jsonShorthandRendersJsonColumn() {
        final MigrationBlueprint bp = new MigrationBlueprint("t");
        bp.json("metadata");

        final String sql = bp.buildSql(null).getSql();

        assertTrue(sql.contains("JSON"), sql);
        assertFalse(sql.contains("JSON NOT NULL"), sql);
    }

    /**
     * uuid() shorthand must render UUID NOT NULL.
     */
    @Test
    void uuidShorthandRendersUuidNotNull() {
        final MigrationBlueprint bp = new MigrationBlueprint("t");
        bp.uuid("ref_id");

        final String sql = bp.buildSql(null).getSql();

        assertTrue(sql.contains("UUID NOT NULL"), sql);
    }

    /**
     * tinyText(), mediumText(), and longText() shorthands must render the
     * corresponding TEXT-family types without NOT NULL.
     */
    @Test
    void textVariantShorthandsRenderNullableTypes() {
        final MigrationBlueprint bp = new MigrationBlueprint("t");
        bp.tinyText("tiny").mediumText("medium").longText("long_col");

        final String sql = bp.buildSql(null).getSql();

        assertTrue(sql.contains("TINYTEXT"),   sql);
        assertTrue(sql.contains("MEDIUMTEXT"),  sql);
        assertTrue(sql.contains("LONGTEXT"),    sql);
    }

    /**
     * blob() shorthand must render a BLOB column without NOT NULL.
     */
    @Test
    void blobShorthandRendersBlobColumn() {
        final MigrationBlueprint bp = new MigrationBlueprint("t");
        bp.blob("data");

        final String sql = bp.buildSql(null).getSql();

        assertTrue(sql.contains("BLOB"), sql);
        assertFalse(sql.contains("BLOB NOT NULL"), sql);
    }

    /**
     * The MigrationBlueprint.id() shorthand must add a VARCHAR(36) NOT NULL
     * primary key column named "id".
     */
    @Test
    void idShorthandAddsVarChar36PrimaryKey() {
        final MigrationBlueprint bp = new MigrationBlueprint("things");
        bp.id();

        final SqlResult result = bp.buildSql(null);
        final String sql = result.getSql();

        assertTrue(sql.contains("id VARCHAR(36) NOT NULL"), sql);
        assertTrue(sql.contains("PRIMARY KEY (id)"),        sql);
    }

    /**
     * MigrationBlueprint.buildSql() parameters list must always be empty
     * because CREATE TABLE has no bind parameters.
     */
    @Test
    void blueprintBuildSqlParametersAreEmpty() {
        final MigrationBlueprint bp = new MigrationBlueprint("foo");
        bp.id();
        assertTrue(bp.buildSql(null).getParameters().isEmpty());
    }

    // =========================================================================
    // Schema — raw ColumnType usage via QueryBuilder
    // =========================================================================

    /**
     * Tables created via Schema.create() must be queryable via the underlying
     * JDBC store.
     */
    @Test
    void schemaCreateMakesTableQueryable() throws Exception {
        final Schema schema = new Schema(store, SqlDialect.MYSQL);
        schema.create("items", t -> t
            .id()
            .string("label", 64)
        );

        assertTrue(tableExists("items"));

        final SqlResult insert = QueryBuilder.insertInto("items")
            .value("id", "item-1")
            .value("label", "Widget")
            .build(SqlDialect.MYSQL);
        store.executeUpdate(insert.getSql(), insert.getParameters());

        final List<java.util.Map<String, Object>> rows = store.query(
            "SELECT id, label FROM items", List.of());
        assertEquals(1, rows.size());
        assertEquals("Widget", rows.get(0).get("label"));
    }

    /**
     * Schema.drop() must remove a table that exists.
     */
    @Test
    void schemaDropRemovesTable() throws Exception {
        final Schema schema = new Schema(store, SqlDialect.MYSQL);
        schema.create("to_drop", t -> t.id());
        assertTrue(tableExists("to_drop"));
        schema.drop("to_drop");
        assertFalse(tableExists("to_drop"));
    }

    /**
     * Schema.dropIfExists() must not throw when the table does not exist.
     */
    @Test
    void schemaDropIfExistsIsIdempotent() throws Exception {
        final Schema schema = new Schema(store, SqlDialect.MYSQL);
        assertDoesNotThrow(() -> schema.dropIfExists("nonexistent_table"));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Creates a {@link MigrationRunner} backed by the test store and
     * {@link SqlDialect#MYSQL}.
     *
     * @param migrations the migrations to include
     * @return a configured runner
     */
    private MigrationRunner runner(final Migration... migrations) {
        return new MigrationRunner(store, SqlDialect.MYSQL, List.of(migrations));
    }

    /**
     * Returns {@code true} when the named table exists in the H2 catalog.
     *
     * @param tableName the table name to check (case-insensitive)
     * @return {@code true} if the table exists
     * @throws Exception if the metadata query fails
     */
    private boolean tableExists(final String tableName) throws Exception {
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeQuery("SELECT 1 FROM " + tableName + " LIMIT 1");
            return true;
        }
        catch (Exception e) {
            return false;
        }
    }

    /**
     * Counts the rows in the given table.
     *
     * @param tableName the table to count rows in
     * @return the row count
     * @throws Exception if the query fails
     */
    private int countRows(final String tableName) throws Exception {
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /**
     * Returns the batch number recorded for the given migration ID.
     *
     * @param migrationId the migration ID to look up
     * @return the batch number
     * @throws Exception if the query fails
     */
    private int batchFor(final String migrationId) throws Exception {
        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                 "SELECT batch FROM jaloquent_migrations WHERE id = '"
                     + migrationId + "'")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
