package com.github.ezframework.jaloquent.model;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry mapping repository prefixes to SQL table and column definitions.
 *
 * <p>Registrations are JVM-static.  To avoid cross-test contamination use a
 * unique prefix string per test; once registered an entry can be overwritten
 * by calling {@link #register} again with the same prefix.
 *
 * <p>Usage example:
 * <pre>{@code
 * Map<String, String> columns = Map.of(
 *     "id",    "VARCHAR(36)",
 *     "name",  "VARCHAR(255)",
 *     "coins", "INT"
 * );
 * TableRegistry.register("players", "ez_players", columns);
 * }</pre>
 */
public final class TableRegistry {

    /**
     * Registry map of prefix to table metadata.
     */
    private static final Map<String, TableMeta> MAP = new HashMap<>();

    private TableRegistry() { }

    /**
     * Register (or overwrite) table metadata for a repository prefix.
     *
     * @param prefix    repository prefix used as the registry key
     * @param tableName SQL table name
     * @param columns   map of column name to SQL type; a defensive copy is stored
     */
    public static void register(String prefix, String tableName, Map<String, String> columns) {
        MAP.put(prefix, new TableMeta(tableName, new HashMap<>(columns)));
    }

    /**
     * Look up table metadata for a prefix.
     *
     * @param prefix repository prefix
     * @return table metadata, or {@code null} when not registered
     */
    public static TableMeta get(String prefix) {
        return MAP.get(prefix);
    }

    /**
     * Return an unmodifiable view of all registered entries.
     *
     * @return prefix-to-metadata map
     */
    public static Map<String, TableMeta> all() {
        return Collections.unmodifiableMap(MAP);
    }

    /**
     * Metadata descriptor for a SQL table used by a repository.
     */
    public static final class TableMeta {
        /**
         * SQL table name.
         */
        private final String tableName;

        /**
         * Map of column names to SQL types.
         */
        private final Map<String, String> columns;

        /**
         * Construct a TableMeta instance.
         * @param tableName SQL table name
         * @param columns map of column names to SQL types
         */
        TableMeta(String tableName, Map<String, String> columns) {
            this.tableName = tableName;
            this.columns = columns;
        }
    

        /**
         * Return the SQL table name.
         *
         * @return table name
         */
        public String tableName() {
            return tableName;
        }

        /**
         * Return an unmodifiable view of the column map (column name → SQL type).
         *
         * @return column definitions
         */
        public Map<String, String> columns() {
            return Collections.unmodifiableMap(columns);
        }
    }
}
