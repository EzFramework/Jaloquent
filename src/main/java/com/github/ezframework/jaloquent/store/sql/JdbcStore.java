package com.github.ezframework.jaloquent.store.sql;

import java.util.List;
import java.util.Map;

/**
 * JDBC-level query abstraction used by
 * {@link com.github.ezframework.jaloquent.model.ModelRepository} to execute
 * SQL against a relational database.
 *
 * <p>Implementations are responsible for mapping ResultSet rows to lists of
 * column-name-to-value maps and for binding parameters to PreparedStatements.
 */
public interface JdbcStore {

    /**
     * Execute a parameterized SELECT and return each row as a column-to-value map.
     *
     * @param sql    parameterized SQL string (use {@code ?} placeholders)
     * @param params positional parameter values (may be null or empty)
     * @return list of rows; empty list when no rows match
     * @throws Exception on SQL or mapping errors
     */
    List<Map<String, Object>> query(String sql, List<Object> params) throws Exception;

    /**
     * Execute a parameterized INSERT, UPDATE, or DELETE.
     *
     * @param sql    parameterized SQL string (use {@code ?} placeholders)
     * @param params positional parameter values (may be null or empty)
     * @return number of affected rows
     * @throws Exception on SQL errors
     */
    int executeUpdate(String sql, List<Object> params) throws Exception;
}
