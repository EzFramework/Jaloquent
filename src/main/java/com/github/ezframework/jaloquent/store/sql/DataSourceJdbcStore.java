package com.github.ezframework.jaloquent.store.sql;

import com.github.ezframework.jaloquent.exception.StorageException;
import com.github.ezframework.jaloquent.store.DataStore;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Built-in SQL backend for Jaloquent repositories.
 *
 * <p>Wraps a {@link DataSource} and implements both {@link DataStore} and
 * {@link TransactionalJdbcStore} (which extends {@link JdbcStore}), so a single
 * instance satisfies every store requirement of {@link
 * com.github.ezframework.jaloquent.model.ModelRepository}.
 *
 * <h3>Quick start (no custom store code needed)</h3>
 * <pre>{@code
 * DataSource ds = ...; // e.g. HikariCP, H2, any JDBC DataSource
 * TableRegistry.register("players", "players", columns);
 * ModelRepository<Player> repo = new ModelRepository<>(
 *     new DataSourceJdbcStore(ds), "players", Player::new
 * );
 * }</pre>
 *
 * <h3>SQL path vs. flat-map path</h3>
 * <p>This implementation is designed exclusively for the SQL path. The
 * {@link DataStore} methods ({@link #save}, {@link #load}, {@link #delete},
 * {@link #exists}) are intentionally unsupported and throw
 * {@link StorageException} — they will never be called when a
 * {@link com.github.ezframework.jaloquent.model.TableRegistry} entry exists for
 * the repository prefix, which is the expected setup when using this class.
 *
 * <h3>Transaction support</h3>
 * <p>Implements {@link TransactionalJdbcStore} via a {@link ThreadLocal}
 * connection. Once {@link #beginTransaction()} is called, all subsequent
 * {@link #query} and {@link #executeUpdate} calls on the current thread share
 * the same open {@link Connection} until {@link #commitTransaction()} or
 * {@link #rollbackTransaction()} closes it.
 */
public class DataSourceJdbcStore implements DataStore, TransactionalJdbcStore {

    /**
     * The backing data source that supplies connections.
     */
    private final DataSource dataSource;

    /**
     * Thread-local connection held open across a transaction. Null when no
     * transaction is active on the current thread.
     */
    private final ThreadLocal<Connection> txConn = new ThreadLocal<>();

    /**
     * Create a store backed by the supplied data source.
     *
     * @param dataSource JDBC data source; must not be {@code null}
     */
    public DataSourceJdbcStore(DataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource must not be null");
        }
        this.dataSource = dataSource;
    }

    // =========================================================================
    // JdbcStore — query and update
    // =========================================================================

    /**
     * Execute a parameterized SELECT and return each row as a column-to-value map.
     *
     * <p>Column labels (as reported by {@link ResultSetMetaData#getColumnLabel(int)})
     * are used as map keys so that SQL aliases work correctly.
     *
     * @param sql    parameterized SQL with {@code ?} placeholders
     * @param params positional bind values; may be {@code null} or empty
     * @return list of rows; empty list when no rows match
     * @throws Exception if the query fails
     */
    @Override
    public List<Map<String, Object>> query(String sql, List<Object> params) throws Exception {
        final Connection conn = getActiveConnection();
        try {
            final PreparedStatement stmt = conn.prepareStatement(sql);
            try {
                bindParams(stmt, params);
                final ResultSet rs = stmt.executeQuery();
                try {
                    return mapResultSet(rs);
                }
                finally {
                    rs.close();
                }
            }
            finally {
                stmt.close();
            }
        }
        finally {
            closeIfNotInTransaction(conn);
        }
    }

    /**
     * Execute a parameterized INSERT, UPDATE, or DELETE.
     *
     * @param sql    parameterized SQL with {@code ?} placeholders
     * @param params positional bind values; may be {@code null} or empty
     * @return number of affected rows
     * @throws Exception if the update fails
     */
    @Override
    public int executeUpdate(String sql, List<Object> params) throws Exception {
        final Connection conn = getActiveConnection();
        try {
            final PreparedStatement stmt = conn.prepareStatement(sql);
            try {
                bindParams(stmt, params);
                return stmt.executeUpdate();
            }
            finally {
                stmt.close();
            }
        }
        finally {
            closeIfNotInTransaction(conn);
        }
    }

    // =========================================================================
    // TransactionalJdbcStore — transaction lifecycle
    // =========================================================================

    /**
     * Begin a new database transaction on the current thread.
     *
     * <p>Obtains a connection from the data source, disables auto-commit, and
     * stores it in a thread-local so that subsequent {@link #query} and
     * {@link #executeUpdate} calls participate in the same transaction.
     *
     * @throws StorageException if a transaction is already active or the connection cannot be opened
     */
    @Override
    public void beginTransaction() throws StorageException {
        if (txConn.get() != null) {
            throw new StorageException("A transaction is already active on this thread");
        }
        try {
            final Connection conn = dataSource.getConnection();
            conn.setAutoCommit(false);
            txConn.set(conn);
        }
        catch (SQLException e) {
            throw new StorageException("Failed to begin transaction", e);
        }
    }

    /**
     * Commit the current transaction and close the connection.
     *
     * @throws StorageException if no transaction is active or the commit fails
     */
    @Override
    public void commitTransaction() throws StorageException {
        final Connection conn = txConn.get();
        if (conn == null) {
            throw new StorageException("No active transaction to commit");
        }
        try {
            conn.commit();
            conn.close();
        }
        catch (SQLException e) {
            throw new StorageException("Failed to commit transaction", e);
        }
        finally {
            txConn.remove();
        }
    }

    /**
     * Roll back the current transaction and close the connection.
     *
     * @throws StorageException if no transaction is active or the rollback fails
     */
    @Override
    public void rollbackTransaction() throws StorageException {
        final Connection conn = txConn.get();
        if (conn == null) {
            throw new StorageException("No active transaction to roll back");
        }
        try {
            conn.rollback();
            conn.close();
        }
        catch (SQLException e) {
            throw new StorageException("Failed to roll back transaction", e);
        }
        finally {
            txConn.remove();
        }
    }

    // =========================================================================
    // DataStore — unsupported (SQL stores must use the JDBC path)
    // =========================================================================

    /**
     * Not supported by this store.
     *
     * <p>Use {@link com.github.ezframework.jaloquent.model.TableRegistry} to register
     * a table for the prefix so that
     * {@link com.github.ezframework.jaloquent.model.ModelRepository} routes all
     * operations through the SQL path.
     *
     * @param path unused
     * @param data unused
     * @throws StorageException always
     */
    @Override
    public void save(String path, Map<String, Object> data) throws StorageException {
        throw unsupportedFlatMap();
    }

    /**
     * Not supported by this store.
     *
     * @param path unused
     * @return never returns
     * @throws StorageException always
     */
    @Override
    public Optional<Map<String, Object>> load(String path) throws StorageException {
        throw unsupportedFlatMap();
    }

    /**
     * Not supported by this store.
     *
     * @param path unused
     * @throws StorageException always
     */
    @Override
    public void delete(String path) throws StorageException {
        throw unsupportedFlatMap();
    }

    /**
     * Not supported by this store.
     *
     * @param path unused
     * @return never returns
     * @throws StorageException always
     */
    @Override
    public boolean exists(String path) throws StorageException {
        throw unsupportedFlatMap();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Return the active transaction connection if one exists on this thread,
     * or acquire a fresh auto-close connection from the data source.
     *
     * @return an open JDBC connection
     * @throws StorageException if the data source cannot provide a connection
     */
    private Connection getActiveConnection() throws StorageException {
        final Connection active = txConn.get();
        if (active != null) {
            return active;
        }
        try {
            return dataSource.getConnection();
        }
        catch (SQLException e) {
            throw new StorageException("Failed to obtain a database connection", e);
        }
    }

    /**
     * Close the connection only when it is not the current transaction connection.
     *
     * @param conn connection to close if transient
     * @throws StorageException if an unexpected close error occurs
     */
    private void closeIfNotInTransaction(Connection conn) throws StorageException {
        if (conn != txConn.get()) {
            try {
                conn.close();
            }
            catch (SQLException e) {
                throw new StorageException("Failed to close JDBC connection", e);
            }
        }
    }

    /**
     * Bind positional parameters to a prepared statement.
     *
     * @param stmt   the prepared statement
     * @param params parameter values; may be {@code null} or empty
     * @throws SQLException if binding fails
     */
    private void bindParams(PreparedStatement stmt, List<Object> params) throws SQLException {
        if (params == null) {
            return;
        }
        for (int i = 0; i < params.size(); i++) {
            stmt.setObject(i + 1, params.get(i));
        }
    }

    /**
     * Map all rows in a {@link ResultSet} to a list of column-to-value maps.
     *
     * <p>Uses {@link ResultSetMetaData#getColumnLabel(int)} so SQL aliases are
     * preserved as map keys.
     *
     * @param rs an open result set positioned before the first row
     * @return list of rows; empty list when the result set is empty
     * @throws SQLException if reading the result set fails
     */
    private List<Map<String, Object>> mapResultSet(ResultSet rs) throws SQLException {
        final ResultSetMetaData meta = rs.getMetaData();
        final int colCount = meta.getColumnCount();
        final List<Map<String, Object>> rows = new ArrayList<>();
        while (rs.next()) {
            final Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= colCount; i++) {
                row.put(meta.getColumnLabel(i), rs.getObject(i));
            }
            rows.add(row);
        }
        return rows;
    }

    /**
     * Build the standard exception for flat-map operations on a SQL-only store.
     *
     * @return a {@link StorageException} explaining that table registration is required
     */
    private StorageException unsupportedFlatMap() {
        return new StorageException(
            "DataSourceJdbcStore does not support flat-map operations. "
            + "Register a table with TableRegistry.register(...) so that "
            + "ModelRepository routes all operations through the SQL path."
        );
    }
}
