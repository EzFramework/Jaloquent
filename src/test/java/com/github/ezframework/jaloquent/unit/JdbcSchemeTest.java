package com.github.ezframework.jaloquent.unit;

import com.github.ezframework.jaloquent.config.JdbcScheme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Unit tests for {@link JdbcScheme}.
 */
public class JdbcSchemeTest {

    @Test
    void mysqlSchemeString() {
        assertEquals("mysql", JdbcScheme.MYSQL.getScheme());
    }

    @Test
    void postgresqlSchemeString() {
        assertEquals("postgresql", JdbcScheme.POSTGRESQL.getScheme());
    }

    @Test
    void mariadbSchemeString() {
        assertEquals("mariadb", JdbcScheme.MARIADB.getScheme());
    }

    @Test
    void mssqlSchemeString() {
        assertEquals("sqlserver", JdbcScheme.MSSQL.getScheme());
    }

    @Test
    void h2SchemeString() {
        assertEquals("h2", JdbcScheme.H2.getScheme());
    }

    @Test
    void sqliteSchemeString() {
        assertEquals("sqlite", JdbcScheme.SQLITE.getScheme());
    }

    @Test
    void valuesContainsSixConstants() {
        assertEquals(6, JdbcScheme.values().length);
    }

    @Test
    void valueOfMysql() {
        assertEquals(JdbcScheme.MYSQL, JdbcScheme.valueOf("MYSQL"));
    }

    @Test
    void valueOfPostgresql() {
        assertEquals(JdbcScheme.POSTGRESQL, JdbcScheme.valueOf("POSTGRESQL"));
    }

    @Test
    void toStringIsEnumName() {
        assertNotNull(JdbcScheme.H2.toString());
    }
}
