package com.github.ezframework.jaloquent.migration;

import com.github.ezframework.javaquerybuilder.query.builder.ColumnType;
import com.github.ezframework.javaquerybuilder.query.builder.CreateBuilder;
import com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder;
import com.github.ezframework.javaquerybuilder.query.sql.SqlDialect;
import com.github.ezframework.javaquerybuilder.query.sql.SqlResult;

/**
 * Fluent DSL for defining a table schema inside a {@link Migration#up(Schema)} callback.
 *
 * <p>Wraps {@link CreateBuilder} and exposes convenience methods that use
 * {@link ColumnType} type-safe constants so callers do not have to write
 * raw SQL strings for common column types:
 *
 * <pre>{@code
 * schema.create("orders", t -> t
 *     .id()
 *     .string("reference", 64)
 *     .integer("quantity")
 *     .bool("shipped")
 *     .timestamps()
 * );
 * }</pre>
 *
 * <p>For types not covered by the convenience methods, use
 * {@link #column(String, ColumnType)} or {@link #column(String, String)} directly:
 *
 * <pre>{@code
 * t.column("price", ColumnType.decimal(10, 2).notNull())
 *  .column("meta",  ColumnType.JSON)
 * }</pre>
 */
public class MigrationBlueprint {

    /** The underlying CreateBuilder that accumulates column definitions. */
    private final CreateBuilder builder;

    /**
     * Constructs a blueprint for the specified table name.
     *
     * @param table the table name passed to {@link QueryBuilder#createTable(String)}
     */
    public MigrationBlueprint(final String table) {
        this.builder = QueryBuilder.createTable(table);
    }

    // ── Raw column overloads ────────────────────────────────────────────────

    /**
     * Adds a column using a type-safe {@link ColumnType}.
     *
     * @param name       column name
     * @param columnType the type descriptor (may have modifiers chained)
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint column(final String name, final ColumnType columnType) {
        builder.column(name, columnType);
        return this;
    }

    /**
     * Adds a column using a raw SQL type string.
     *
     * @param name    column name
     * @param sqlType raw SQL type string (e.g. {@code "GEOMETRY"})
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint column(final String name, final String sqlType) {
        builder.column(name, sqlType);
        return this;
    }

    /**
     * Marks a column (already added via {@link #column}) as the primary key.
     *
     * @param name the primary-key column name
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint primaryKey(final String name) {
        builder.primaryKey(name);
        return this;
    }

    /**
     * Adds {@code IF NOT EXISTS} to the generated {@code CREATE TABLE} statement.
     *
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint ifNotExists() {
        builder.ifNotExists();
        return this;
    }

    // ── Convenience shorthands ──────────────────────────────────────────────

    /**
     * Adds a {@code VARCHAR(36) NOT NULL} primary-key column named {@code id}.
     *
     * <p>Equivalent to:
     * <pre>{@code .column("id", ColumnType.varChar(36).notNull()).primaryKey("id")}</pre>
     *
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint id() {
        builder.column("id", ColumnType.varChar(36).notNull());
        builder.primaryKey("id");
        return this;
    }

    /**
     * Adds a {@code VARCHAR(length) NOT NULL} column.
     *
     * @param name   column name
     * @param length maximum character length
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint string(final String name, final int length) {
        builder.column(name, ColumnType.varChar(length).notNull());
        return this;
    }

    /**
     * Adds an {@code INT NOT NULL} column.
     *
     * @param name column name
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint integer(final String name) {
        builder.column(name, ColumnType.INT.notNull());
        return this;
    }

    /**
     * Adds a {@code BIGINT NOT NULL} column.
     *
     * @param name column name
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint bigInteger(final String name) {
        builder.column(name, ColumnType.BIGINT.notNull());
        return this;
    }

    /**
     * Adds a {@code BOOLEAN NOT NULL} column.
     *
     * @param name column name
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint bool(final String name) {
        builder.column(name, ColumnType.BOOLEAN.notNull());
        return this;
    }

    /**
     * Adds a {@code TEXT} column (nullable).
     *
     * @param name column name
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint text(final String name) {
        builder.column(name, ColumnType.TEXT);
        return this;
    }

    /**
     * Adds a {@code TIMESTAMP} column (nullable).
     *
     * @param name column name
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint timestamp(final String name) {
        builder.column(name, ColumnType.TIMESTAMP);
        return this;
    }

    /**
     * Adds {@code created_at} and {@code updated_at} {@code TIMESTAMP} columns
     * (both nullable).
     *
     * <p>Equivalent to calling {@link #timestamp(String)} twice:
     * <pre>{@code .timestamp("created_at").timestamp("updated_at")}</pre>
     *
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint timestamps() {
        builder.column("created_at", ColumnType.TIMESTAMP);
        builder.column("updated_at", ColumnType.TIMESTAMP);
        return this;
    }

    /**
     * Adds a {@code TINYINT NOT NULL} column.
     *
     * @param name column name
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint tinyInteger(final String name) {
        builder.column(name, ColumnType.TINYINT.notNull());
        return this;
    }

    /**
     * Adds a {@code SMALLINT NOT NULL} column.
     *
     * @param name column name
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint smallInteger(final String name) {
        builder.column(name, ColumnType.SMALLINT.notNull());
        return this;
    }

    /**
     * Adds a {@code DECIMAL(precision, scale) NOT NULL} column.
     *
     * @param name      column name
     * @param precision total number of digits
     * @param scale     digits after the decimal point
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint decimal(final String name, final int precision, final int scale) {
        builder.column(name, ColumnType.decimal(precision, scale).notNull());
        return this;
    }

    /**
     * Adds a {@code DATE} column (nullable).
     *
     * @param name column name
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint date(final String name) {
        builder.column(name, ColumnType.DATE);
        return this;
    }

    /**
     * Adds a {@code TIME} column (nullable).
     *
     * @param name column name
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint time(final String name) {
        builder.column(name, ColumnType.TIME);
        return this;
    }

    /**
     * Adds a {@code DATETIME} column (nullable).
     *
     * @param name column name
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint dateTime(final String name) {
        builder.column(name, ColumnType.DATETIME);
        return this;
    }

    /**
     * Adds a {@code JSON} column (nullable).
     *
     * @param name column name
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint json(final String name) {
        builder.column(name, ColumnType.JSON);
        return this;
    }

    /**
     * Adds a {@code UUID NOT NULL} column.
     *
     * @param name column name
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint uuid(final String name) {
        builder.column(name, ColumnType.UUID.notNull());
        return this;
    }

    /**
     * Adds a {@code TINYTEXT} column (nullable).
     *
     * @param name column name
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint tinyText(final String name) {
        builder.column(name, ColumnType.TINYTEXT);
        return this;
    }

    /**
     * Adds a {@code MEDIUMTEXT} column (nullable).
     *
     * @param name column name
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint mediumText(final String name) {
        builder.column(name, ColumnType.MEDIUMTEXT);
        return this;
    }

    /**
     * Adds a {@code LONGTEXT} column (nullable).
     *
     * @param name column name
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint longText(final String name) {
        builder.column(name, ColumnType.LONGTEXT);
        return this;
    }

    /**
     * Adds a {@code BLOB} column (nullable).
     *
     * @param name column name
     * @return this blueprint for method chaining
     */
    public MigrationBlueprint blob(final String name) {
        builder.column(name, ColumnType.BLOB);
        return this;
    }

    // ── Build ───────────────────────────────────────────────────────────────

    /**
     * Builds and returns the {@link SqlResult} for the {@code CREATE TABLE} statement.
     *
     * @param dialect the SQL dialect to use for identifier quoting; may be {@code null}
     * @return the SQL result carrying the DDL string and empty parameter list
     */
    public SqlResult buildSql(final SqlDialect dialect) {
        return builder.build(dialect);
    }
}
