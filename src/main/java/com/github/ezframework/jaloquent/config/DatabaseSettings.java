package com.github.ezframework.jaloquent.config;

/**
 * Immutable database connection settings for {@link JaloquentConfig#buildStore()}.
 *
 * <p>Use the fluent builder to construct an instance and then register it.
 * You can either supply a full JDBC URL:
 * <pre>{@code
 * JaloquentConfig.setDatabaseSettings(
 *     DatabaseSettings.builder()
 *         .url("jdbc:mysql://localhost:3306/mydb")
 *         .username("root")
 *         .password("secret")
 *         .build()
 * );
 * DataSourceJdbcStore store = JaloquentConfig.buildStore();
 * }</pre>
 *
 * <p>Or supply the individual connection parts and let Jaloquent compose the URL:
 * <pre>{@code
 * JaloquentConfig.setDatabaseSettings(
 *     DatabaseSettings.builder()
 *         .jdbcScheme(JdbcScheme.MYSQL)
 *         .host("localhost")
 *         .port(DatabaseSettings.DEFAULT_MYSQL_PORT)
 *         .databaseName("mydb")
 *         .username("root")
 *         .password("secret")
 *         .build()   // constructs jdbc:mysql://localhost:3306/mydb
 * );
 * DataSourceJdbcStore store = JaloquentConfig.buildStore();
 * }</pre>
 *
 * <p>All settings except {@code url} have sensible production-grade defaults.
 * Connection pooling is <em>not</em> provided by the built-in
 * {@code DriverManagerDataSource} — for production workloads supply your own
 * pooled {@code DataSource} (e.g. HikariCP, c3p0, Apache DBCP) directly to
 * {@link com.github.ezframework.jaloquent.store.sql.DataSourceJdbcStore}.
 *
 * <p>The pool-related constants ({@link #DEFAULT_MAX_POOL_SIZE},
 * {@link #DEFAULT_MIN_IDLE}, etc.) are exposed as hints so that callers can
 * pass them directly to their chosen connection-pool library.
 */
public final class DatabaseSettings {

    /** Default MySQL and MariaDB JDBC port. */
    public static final int DEFAULT_MYSQL_PORT = 3306;

    /** Default PostgreSQL JDBC port. */
    public static final int DEFAULT_POSTGRESQL_PORT = 5432;

    /** Default maximum number of pooled connections (informational hint for external pools). */
    public static final int DEFAULT_MAX_POOL_SIZE = 10;

    /** Default minimum number of idle connections (informational hint for external pools). */
    public static final int DEFAULT_MIN_IDLE = 2;

    /** Default connection-acquisition timeout in milliseconds (30 seconds). */
    public static final long DEFAULT_CONNECTION_TIMEOUT_MS = 30_000L;

    /** Default idle-connection eviction timeout in milliseconds (10 minutes). */
    public static final long DEFAULT_IDLE_TIMEOUT_MS = 600_000L;

    /** Default maximum lifetime of a pooled connection in milliseconds (30 minutes). */
    public static final long DEFAULT_MAX_LIFETIME_MS = 1_800_000L;

    /** JDBC connection URL. */
    private final String url;

    /** Database username; may be empty. */
    private final String username;

    /** Database password; may be empty. */
    private final String password;

    /** Optional JDBC driver class name; null means auto-detection. */
    private final String driverClassName;

    /** Maximum pool size hint for external connection pools. */
    private final int maximumPoolSize;

    /** Minimum idle size hint for external connection pools. */
    private final int minimumIdle;

    /** Connection-acquisition timeout hint in milliseconds. */
    private final long connectionTimeoutMs;

    /** Idle-connection eviction timeout hint in milliseconds. */
    private final long idleTimeoutMs;

    /** Maximum lifetime hint in milliseconds. */
    private final long maxLifetimeMs;

    /** JDBC scheme component, e.g. {@code mysql} or {@code postgresql}; used when composing the URL from parts. */
    private final String jdbcScheme;

    /** Database host name or IP address; used when composing the URL from parts. */
    private final String host;

    /** Database TCP port; 0 means «use the driver default / omit from URL». */
    private final int port;

    /** Database (schema) name; used when composing the URL from parts. */
    private final String databaseName;

    private DatabaseSettings(Builder b) {
        this.url = b.url;
        this.username = b.username;
        this.password = b.password;
        this.driverClassName = b.driverClassName;
        this.maximumPoolSize = b.maximumPoolSize;
        this.minimumIdle = b.minimumIdle;
        this.connectionTimeoutMs = b.connectionTimeoutMs;
        this.idleTimeoutMs = b.idleTimeoutMs;
        this.maxLifetimeMs = b.maxLifetimeMs;
        this.jdbcScheme = b.jdbcScheme;
        this.host = b.host;
        this.port = b.port;
        this.databaseName = b.databaseName;
    }

    /**
     * Create a new {@link Builder} for constructing {@link DatabaseSettings}.
     *
     * @return new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Return the JDBC connection URL.
     *
     * @return JDBC URL (never null or empty)
     */
    public String getUrl() {
        return url;
    }

    /**
     * Return the database username.
     *
     * @return username (may be empty, never null)
     */
    public String getUsername() {
        return username;
    }

    /**
     * Return the database password.
     *
     * @return password (may be empty, never null)
     */
    public String getPassword() {
        return password;
    }

    /**
     * Return the explicit JDBC driver class name, or {@code null} for auto-detection.
     *
     * @return driver class name or null
     */
    public String getDriverClassName() {
        return driverClassName;
    }

    /**
     * Return the maximum connection pool size hint.
     *
     * @return max pool size (default {@value #DEFAULT_MAX_POOL_SIZE})
     */
    public int getMaximumPoolSize() {
        return maximumPoolSize;
    }

    /**
     * Return the minimum idle connections hint.
     *
     * @return min idle (default {@value #DEFAULT_MIN_IDLE})
     */
    public int getMinimumIdle() {
        return minimumIdle;
    }

    /**
     * Return the connection-acquisition timeout hint in milliseconds.
     *
     * @return timeout in ms (default {@value #DEFAULT_CONNECTION_TIMEOUT_MS})
     */
    public long getConnectionTimeoutMs() {
        return connectionTimeoutMs;
    }

    /**
     * Return the idle-connection eviction timeout hint in milliseconds.
     *
     * @return idle timeout in ms (default {@value #DEFAULT_IDLE_TIMEOUT_MS})
     */
    public long getIdleTimeoutMs() {
        return idleTimeoutMs;
    }

    /**
     * Return the maximum connection lifetime hint in milliseconds.
     *
     * @return max lifetime in ms (default {@value #DEFAULT_MAX_LIFETIME_MS})
     */
    public long getMaxLifetimeMs() {
        return maxLifetimeMs;
    }

    /**
     * Return the JDBC URL scheme component used when building the URL from parts.
     *
     * <p>Examples: {@code mysql}, {@code postgresql}, {@code mariadb}, {@code h2}.
     * Returns {@code null} when the URL was supplied in full via {@link Builder#url}.
     *
     * @return JDBC scheme or null
     */
    public String getJdbcScheme() {
        return jdbcScheme;
    }

    /**
     * Return the database host name or IP address used when building the URL from parts.
     *
     * <p>Returns {@code null} when the URL was supplied in full via {@link Builder#url}.
     *
     * @return host or null
     */
    public String getHost() {
        return host;
    }

    /**
     * Return the database TCP port used when building the URL from parts.
     *
     * <p>A value of {@code 0} means the port was not specified and is omitted
     * from the composed URL (the driver uses its own default).
     * Returns {@code 0} when the URL was supplied in full via {@link Builder#url}.
     *
     * @return port number, or 0 when not specified
     */
    public int getPort() {
        return port;
    }

    /**
     * Return the database (schema) name used when building the URL from parts.
     *
     * <p>Returns {@code null} when the URL was supplied in full via {@link Builder#url}.
     *
     * @return database name or null
     */
    public String getDatabaseName() {
        return databaseName;
    }

    /**
     * Fluent builder for {@link DatabaseSettings}.
     */
    public static final class Builder {

        /** JDBC connection URL (required). */
        private String url;

        /** Database username. */
        private String username = "";

        /** Database password. */
        private String password = "";

        /** Optional JDBC driver class name; null means auto-detect. */
        private String driverClassName = null;

        /** Maximum pool size hint. */
        private int maximumPoolSize = DEFAULT_MAX_POOL_SIZE;

        /** Minimum idle connections hint. */
        private int minimumIdle = DEFAULT_MIN_IDLE;

        /** Connection timeout hint in ms. */
        private long connectionTimeoutMs = DEFAULT_CONNECTION_TIMEOUT_MS;

        /** Idle timeout hint in ms. */
        private long idleTimeoutMs = DEFAULT_IDLE_TIMEOUT_MS;

        /** Max lifetime hint in ms. */
        private long maxLifetimeMs = DEFAULT_MAX_LIFETIME_MS;

        /** JDBC scheme used when composing the URL from parts (e.g. {@code mysql}). */
        private String jdbcScheme = null;

        /** Host name or IP used when composing the URL from parts. */
        private String host = null;

        /** Port number; 0 = omit from composed URL. */
        private int port = 0;

        /** Database (schema) name used when composing the URL from parts. */
        private String databaseName = null;

        private Builder() { }

        /**
         * Set the JDBC connection URL (required).
         *
         * <p>Examples:
         * <ul>
         *   <li>{@code jdbc:mysql://localhost:3306/mydb}</li>
         *   <li>{@code jdbc:postgresql://localhost:5432/mydb}</li>
         *   <li>{@code jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1}</li>
         * </ul>
         *
         * @param url JDBC URL; must not be null or empty
         * @return this builder for chaining
         */
        public Builder url(String url) {
            this.url = url;
            return this;
        }

        /**
         * Set the database username.
         *
         * @param username login user; empty string for no authentication
         * @return this builder for chaining
         */
        public Builder username(String username) {
            this.username = username != null ? username : "";
            return this;
        }

        /**
         * Set the database password.
         *
         * @param password login password; empty string for no authentication
         * @return this builder for chaining
         */
        public Builder password(String password) {
            this.password = password != null ? password : "";
            return this;
        }

        /**
         * Set an explicit JDBC driver class name.
         *
         * <p>When omitted, the JDBC driver is assumed to be registered via the
         * {@code ServiceLoader} mechanism (the standard for JDBC 4.0+ drivers).
         * Set this only when automatic discovery is unavailable.
         *
         * @param driverClassName fully-qualified driver class (e.g.
         *                        {@code com.mysql.cj.jdbc.Driver})
         * @return this builder for chaining
         */
        public Builder driverClassName(String driverClassName) {
            this.driverClassName = driverClassName;
            return this;
        }

        /**
         * Set the maximum pool size hint (default {@value DatabaseSettings#DEFAULT_MAX_POOL_SIZE}).
         *
         * <p>This value is informational when using the built-in
         * {@code DriverManagerDataSource} (which does not pool). Pass it directly
         * to your connection-pool library when building a pooled {@code DataSource}.
         *
         * @param maximumPoolSize maximum connections; must be positive
         * @return this builder for chaining
         */
        public Builder maximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
            return this;
        }

        /**
         * Set the minimum idle connections hint (default {@value DatabaseSettings#DEFAULT_MIN_IDLE}).
         *
         * @param minimumIdle minimum idle connections; must be non-negative
         * @return this builder for chaining
         */
        public Builder minimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
            return this;
        }

        /**
         * Set the connection-acquisition timeout hint in milliseconds
         * (default {@value DatabaseSettings#DEFAULT_CONNECTION_TIMEOUT_MS} ms = 30 s).
         *
         * @param connectionTimeoutMs timeout in milliseconds; must be positive
         * @return this builder for chaining
         */
        public Builder connectionTimeoutMs(long connectionTimeoutMs) {
            this.connectionTimeoutMs = connectionTimeoutMs;
            return this;
        }

        /**
         * Set the idle-connection eviction timeout hint in milliseconds
         * (default {@value DatabaseSettings#DEFAULT_IDLE_TIMEOUT_MS} ms = 10 min).
         *
         * @param idleTimeoutMs timeout in milliseconds; must be positive
         * @return this builder for chaining
         */
        public Builder idleTimeoutMs(long idleTimeoutMs) {
            this.idleTimeoutMs = idleTimeoutMs;
            return this;
        }

        /**
         * Set the maximum connection lifetime hint in milliseconds
         * (default {@value DatabaseSettings#DEFAULT_MAX_LIFETIME_MS} ms = 30 min).
         *
         * @param maxLifetimeMs lifetime in milliseconds; must be positive
         * @return this builder for chaining
         */
        public Builder maxLifetimeMs(long maxLifetimeMs) {
            this.maxLifetimeMs = maxLifetimeMs;
            return this;
        }

        /**
         * Set the JDBC URL scheme using a well-known {@link JdbcScheme} constant.
         *
         * <p>This is the preferred overload for common databases. For drivers with
         * non-standard scheme strings (e.g. {@code oracle:thin}) use
         * {@link #jdbcScheme(String)} instead.
         * Required (together with {@link #host} and {@link #databaseName}) when
         * no full {@link #url} is provided.
         *
         * @param scheme well-known JDBC scheme constant; must not be null
         * @return this builder for chaining
         */
        public Builder jdbcScheme(JdbcScheme scheme) {
            this.jdbcScheme = scheme.getScheme();
            return this;
        }

        /**
         * Set the JDBC URL scheme using a raw string.
         *
         * <p>Use this escape hatch for drivers not covered by {@link JdbcScheme},
         * for example {@code oracle:thin}. For common databases prefer the
         * type-safe {@link #jdbcScheme(JdbcScheme)} overload.
         * Required (together with {@link #host} and {@link #databaseName}) when
         * no full {@link #url} is provided.
         *
         * @param jdbcScheme scheme segment that follows {@code jdbc:} in the URL
         * @return this builder for chaining
         */
        public Builder jdbcScheme(String jdbcScheme) {
            this.jdbcScheme = jdbcScheme;
            return this;
        }

        /**
         * Set the database server host name or IP address.
         *
         * <p>Used together with {@link #jdbcScheme}, {@link #port}, and
         * {@link #databaseName} to compose a full JDBC URL when no explicit
         * {@link #url} is given.
         *
         * @param host host name or IP address (e.g. {@code localhost})
         * @return this builder for chaining
         */
        public Builder host(String host) {
            this.host = host;
            return this;
        }

        /**
         * Set the database server TCP port.
         *
         * <p>Use {@link DatabaseSettings#DEFAULT_MYSQL_PORT} (3306) or
         * {@link DatabaseSettings#DEFAULT_POSTGRESQL_PORT} (5432) for the most
         * common databases. A value of {@code 0} (the default) omits the port
         * from the composed URL, causing the driver to apply its built-in default.
         *
         * @param port TCP port number, or 0 to omit
         * @return this builder for chaining
         */
        public Builder port(int port) {
            this.port = port;
            return this;
        }

        /**
         * Set the database (schema) name.
         *
         * <p>This is the name that appears at the end of the JDBC URL path,
         * e.g. {@code mydb} in {@code jdbc:mysql://localhost:3306/mydb}.
         * Required (together with {@link #jdbcScheme} and {@link #host}) when
         * no full {@link #url} is provided.
         *
         * @param databaseName schema / catalog name
         * @return this builder for chaining
         */
        public Builder databaseName(String databaseName) {
            this.databaseName = databaseName;
            return this;
        }

        /**
         * Build and return an immutable {@link DatabaseSettings} instance.
         *
         * @return new settings
         * @throws IllegalStateException when neither a full URL nor all required
         *                               parts ({@code jdbcScheme}, {@code host},
         *                               {@code databaseName}) are provided
         */
        public DatabaseSettings build() {
            if (url == null || url.isEmpty()) {
                url = buildUrlFromParts();
            }
            return new DatabaseSettings(this);
        }

        private String buildUrlFromParts() {
            if (jdbcScheme == null || jdbcScheme.isEmpty()) {
                throw new IllegalStateException(
                    "Either url or jdbcScheme + host + databaseName must be set");
            }
            if (host == null || host.isEmpty()) {
                throw new IllegalStateException(
                    "host must not be null or empty when url is not set");
            }
            if (databaseName == null || databaseName.isEmpty()) {
                throw new IllegalStateException(
                    "databaseName must not be null or empty when url is not set");
            }
            final StringBuilder sb = new StringBuilder("jdbc:")
                .append(jdbcScheme)
                .append("://")
                .append(host);
            if (port > 0) {
                sb.append(':').append(port);
            }
            sb.append('/').append(databaseName);
            return sb.toString();
        }
    }
}
