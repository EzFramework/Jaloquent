package com.github.ezframework.jaloquent.migration;

import com.github.ezframework.jaloquent.exception.MigrationException;
import com.github.ezframework.jaloquent.store.sql.JdbcStore;
import com.github.ezframework.javaquerybuilder.query.sql.SqlDialect;
import com.github.ezframework.javaquerybuilder.query.sql.SqlResult;
import java.util.List;
import java.util.function.Consumer;

/**
 * DDL executor used inside {@link Migration} callbacks.
 *
 * <p>{@code Schema} provides the bridge between the {@link MigrationBlueprint}
 * DSL and the underlying {@link JdbcStore}. It issues {@code CREATE TABLE} and
 * {@code DROP TABLE} statements via the store's {@code executeUpdate} method.
 *
 * <p>Instances are constructed by {@link MigrationRunner} and passed directly
 * to {@link Migration#up(Schema)} and {@link Migration#down(Schema)}:
 *
 * <pre>{@code
 * public void up(Schema schema) throws MigrationException {
 *     schema.create("users", t -> t
 *         .id()
 *         .string("email", 255)
 *         .timestamps()
 *     );
 * }
 *
 * public void down(Schema schema) throws MigrationException {
 *     schema.dropIfExists("users");
 * }
 * }</pre>
 */
public class Schema {

    /** The JDBC store used to execute DDL statements. */
    private final JdbcStore store;

    /** The SQL dialect used when building DDL statements. */
    private final SqlDialect dialect;

    /**
     * Constructs a {@code Schema} backed by the given store and dialect.
     *
     * @param store   the JDBC store to execute DDL against; must not be {@code null}
     * @param dialect the SQL dialect for identifier quoting; may be {@code null}
     *                to fall back to the standard dialect
     */
    public Schema(final JdbcStore store, final SqlDialect dialect) {
        this.store = store;
        this.dialect = dialect;
    }

    /**
     * Creates a table using the column definitions supplied by the blueprint callback.
     *
     * <pre>{@code
     * schema.create("orders", t -> t
     *     .id()
     *     .string("reference", 64)
     *     .integer("quantity")
     * );
     * }</pre>
     *
     * @param table     the name of the table to create
     * @param blueprint a callback that receives and configures a {@link MigrationBlueprint}
     * @throws MigrationException if the DDL execution fails
     */
    public void create(final String table, final Consumer<MigrationBlueprint> blueprint)
            throws MigrationException {
        final MigrationBlueprint bp = new MigrationBlueprint(table);
        blueprint.accept(bp);
        final SqlResult result = bp.buildSql(dialect);
        try {
            store.executeUpdate(result.getSql(), List.of());
        }
        catch (Exception e) {
            throw new MigrationException("Failed to create table '" + table + "'", e);
        }
    }

    /**
     * Drops a table unconditionally.
     *
     * @param table the name of the table to drop
     * @throws MigrationException if the DDL execution fails
     */
    public void drop(final String table) throws MigrationException {
        final String sql = "DROP TABLE " + table;
        try {
            store.executeUpdate(sql, List.of());
        }
        catch (Exception e) {
            throw new MigrationException("Failed to drop table '" + table + "'", e);
        }
    }

    /**
     * Drops a table if it exists; silently succeeds when the table is absent.
     *
     * @param table the name of the table to drop
     * @throws MigrationException if the DDL execution fails for a reason other than
     *                            the table not existing
     */
    public void dropIfExists(final String table) throws MigrationException {
        final String sql = "DROP TABLE IF EXISTS " + table;
        try {
            store.executeUpdate(sql, List.of());
        }
        catch (Exception e) {
            throw new MigrationException("Failed to drop table '" + table + "'", e);
        }
    }
}
