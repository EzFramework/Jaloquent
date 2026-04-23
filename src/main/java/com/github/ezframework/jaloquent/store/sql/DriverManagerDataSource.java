package com.github.ezframework.jaloquent.store.sql;

import com.github.ezframework.jaloquent.config.DatabaseSettings;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Minimal {@link DataSource} backed by {@link DriverManager}.
 *
 * <p>Each call to {@link #getConnection()} opens a new physical connection directly
 * via {@link DriverManager#getConnection(String, String, String)}. There is no
 * connection pooling. This implementation is suitable for development, migration
 * runners, unit tests, and single-threaded or low-concurrency applications.
 *
 * <p>For production workloads that require connection pooling (HikariCP, c3p0,
 * Apache DBCP, etc.) build a pooled {@code DataSource} independently and supply it
 * to {@link DataSourceJdbcStore} directly:
 * <pre>{@code
 * // Development / simple use — built from JaloquentConfig settings:
 * DataSourceJdbcStore store = JaloquentConfig.buildStore();
 *
 * // Production (HikariCP example):
 * HikariConfig hikari = new HikariConfig();
 * hikari.setJdbcUrl("jdbc:postgresql://localhost:5432/mydb");
 * hikari.setUsername("myuser");
 * hikari.setPassword("secret");
 * hikari.setMaximumPoolSize(DatabaseSettings.DEFAULT_MAX_POOL_SIZE);
 * DataSourceJdbcStore store = new DataSourceJdbcStore(new HikariDataSource(hikari));
 * }</pre>
 */
public class DriverManagerDataSource implements DataSource {

    /** JDBC URL used for every connection. */
    private final String url;

    /** Database username passed to {@link DriverManager}. */
    private final String username;

    /** Database password passed to {@link DriverManager}. */
    private final String password;

    /** Optional log writer for the {@link DataSource} contract; unused internally. */
    private PrintWriter logWriter = null;

    /** Login timeout in seconds; 0 means use the JDBC default. */
    private int loginTimeout = 0;

    /**
     * Construct a data source from {@link DatabaseSettings}.
     *
     * <p>When {@link DatabaseSettings#getDriverClassName()} is non-null the driver
     * class is loaded eagerly via {@link Class#forName(String)} so that any
     * {@code ClassNotFoundException} surfaces at construction time rather than
     * on the first query.
     *
     * @param settings connection settings; must not be {@code null}
     * @throws IllegalArgumentException when {@code settings} is null
     * @throws IllegalStateException    when the specified driver class is not found
     */
    public DriverManagerDataSource(DatabaseSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        this.url = settings.getUrl();
        this.username = settings.getUsername();
        this.password = settings.getPassword();
        if (settings.getDriverClassName() != null) {
            loadDriver(settings.getDriverClassName());
        }
    }

    /**
     * Construct a data source directly from URL, username, and password.
     *
     * @param url      JDBC connection URL; must not be null or empty
     * @param username database username; null is treated as empty string
     * @param password database password; null is treated as empty string
     * @throws IllegalArgumentException when {@code url} is null or empty
     */
    public DriverManagerDataSource(String url, String username, String password) {
        if (url == null || url.isEmpty()) {
            throw new IllegalArgumentException("url must not be null or empty");
        }
        this.url = url;
        this.username = username != null ? username : "";
        this.password = password != null ? password : "";
    }

    /**
     * Open a new physical connection to the database using the configured credentials.
     *
     * @return a new JDBC connection; the caller is responsible for closing it
     * @throws SQLException if the connection cannot be obtained
     */
    @Override
    public Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }

    /**
     * Open a new physical connection using the supplied credentials.
     *
     * @param user database username
     * @param pass database password
     * @return a new JDBC connection; the caller is responsible for closing it
     * @throws SQLException if the connection cannot be obtained
     */
    @Override
    public Connection getConnection(String user, String pass) throws SQLException {
        return DriverManager.getConnection(url, user, pass);
    }

    /**
     * Return the current log writer.
     *
     * @return log writer or {@code null}
     */
    @Override
    public PrintWriter getLogWriter() {
        return logWriter;
    }

    /**
     * Set the log writer.
     *
     * @param out new log writer
     */
    @Override
    public void setLogWriter(PrintWriter out) {
        this.logWriter = out;
    }

    /**
     * Set the login timeout in seconds.
     *
     * @param seconds timeout; 0 means use the JDBC driver default
     */
    @Override
    public void setLoginTimeout(int seconds) {
        this.loginTimeout = seconds;
    }

    /**
     * Return the current login timeout in seconds.
     *
     * @return timeout
     */
    @Override
    public int getLoginTimeout() {
        return loginTimeout;
    }

    /**
     * Not supported — this implementation uses the JDK logging bridge via {@link DriverManager}.
     *
     * @return never returns
     * @throws SQLFeatureNotSupportedException always
     */
    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("getParentLogger is not supported");
    }

    /**
     * Unwrap to the requested interface.
     *
     * @param iface target interface
     * @param <T>   target type
     * @return this instance cast to {@code iface}
     * @throws SQLException if this instance does not implement {@code iface}
     */
    @Override
    public <T> T unwrap(Class<T> iface) throws SQLException {
        if (iface.isInstance(this)) {
            return iface.cast(this);
        }
        throw new SQLException("Cannot unwrap to " + iface.getName());
    }

    /**
     * Check whether this instance implements the given interface.
     *
     * @param iface interface to test
     * @return {@code true} if this instance is assignable to {@code iface}
     */
    @Override
    public boolean isWrapperFor(Class<?> iface) {
        return iface.isInstance(this);
    }

    private void loadDriver(String className) {
        try {
            Class.forName(className);
        }
        catch (ClassNotFoundException e) {
            throw new IllegalStateException("JDBC driver class not found: " + className, e);
        }
    }
}
