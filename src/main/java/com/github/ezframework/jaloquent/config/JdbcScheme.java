package com.github.ezframework.jaloquent.config;

/**
 * Common JDBC URL scheme identifiers for use with
 * {@link DatabaseSettings.Builder#jdbcScheme(JdbcScheme)}.
 *
 * <p>Pass one of these constants instead of a raw string to get type-safe,
 * IDE-discoverable scheme selection:
 * <pre>{@code
 * DatabaseSettings.builder()
 *     .jdbcScheme(JdbcScheme.MYSQL)
 *     .host("localhost")
 *     .port(3306)
 *     .databaseName("mydb")
 *     .build();
 * }</pre>
 *
 * <p>For drivers not listed here (e.g. {@code oracle:thin}, custom JDBC wrappers)
 * use {@link DatabaseSettings.Builder#jdbcScheme(String)} with a raw string.
 */
public enum JdbcScheme {

    /** MySQL and MySQL-compatible databases. Scheme: {@code mysql}. Default port: 3306. */
    MYSQL("mysql"),

    /** PostgreSQL. Scheme: {@code postgresql}. Default port: 5432. */
    POSTGRESQL("postgresql"),

    /** MariaDB. Scheme: {@code mariadb}. Default port: 3306. */
    MARIADB("mariadb"),

    /** Microsoft SQL Server. Scheme: {@code sqlserver}. Default port: 1433. */
    MSSQL("sqlserver"),

    /** H2 in-process or server database. Scheme: {@code h2}. */
    H2("h2"),

    /** SQLite file or in-memory database. Scheme: {@code sqlite}. */
    SQLITE("sqlite");

    /** The JDBC URL segment that follows {@code jdbc:} in the connection URL. */
    private final String scheme;

    JdbcScheme(String scheme) {
        this.scheme = scheme;
    }

    /**
     * Return the JDBC URL segment used after {@code jdbc:} in the connection URL.
     *
     * @return scheme string, e.g. {@code "mysql"} or {@code "postgresql"}
     */
    public String getScheme() {
        return scheme;
    }
}
