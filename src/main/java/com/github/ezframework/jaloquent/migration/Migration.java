package com.github.ezframework.jaloquent.migration;

import com.github.ezframework.jaloquent.exception.MigrationException;

/**
 * Contract for a single database schema migration.
 *
 * <p>Each implementation represents one migration step identified by a unique
 * string ID (typically a timestamp prefix, e.g.
 * {@code "2026_04_23_001_create_users_table"}).
 *
 * <p>Implementations <strong>must</strong> provide both {@link #up(Schema)} and
 * {@link #down(Schema)} to keep rollback support unconditional.
 *
 * <pre>{@code
 * public class CreateUsersTable implements Migration {
 *
 *     @Override
 *     public String getId() { return "2026_04_23_001_create_users_table"; }
 *
 *     @Override
 *     public void up(Schema schema) throws MigrationException {
 *         schema.create("users", t -> t
 *             .id()
 *             .string("email", 255)
 *             .timestamps()
 *         );
 *     }
 *
 *     @Override
 *     public void down(Schema schema) throws MigrationException {
 *         schema.dropIfExists("users");
 *     }
 * }
 * }</pre>
 */
public interface Migration {

    /**
     * Returns the unique identifier for this migration.
     *
     * <p>Migration IDs are compared as plain strings; they should be ordered
     * lexicographically (e.g. {@code "2026_04_23_001_..."}) so that the
     * {@link MigrationRunner} list can be sorted if needed.
     *
     * @return a non-null, non-blank unique migration identifier
     */
    String getId();

    /**
     * Applies this migration (the "forward" direction).
     *
     * @param schema the schema executor used to issue DDL statements
     * @throws MigrationException if the migration cannot be applied
     */
    void up(Schema schema) throws MigrationException;

    /**
     * Reverts this migration (the "rollback" direction).
     *
     * @param schema the schema executor used to issue DDL statements
     * @throws MigrationException if the migration cannot be reverted
     */
    void down(Schema schema) throws MigrationException;
}
