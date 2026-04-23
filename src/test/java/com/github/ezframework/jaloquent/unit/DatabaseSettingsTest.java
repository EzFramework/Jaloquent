package com.github.ezframework.jaloquent.unit;

import com.github.ezframework.jaloquent.config.DatabaseSettings;
import com.github.ezframework.jaloquent.config.JdbcScheme;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DatabaseSettings} and its builder.
 */
public class DatabaseSettingsTest {

    // =========================================================================
    // Constants
    // =========================================================================

    @Test
    void defaultMysqlPort() {
        assertEquals(3306, DatabaseSettings.DEFAULT_MYSQL_PORT);
    }

    @Test
    void defaultPostgresqlPort() {
        assertEquals(5432, DatabaseSettings.DEFAULT_POSTGRESQL_PORT);
    }

    @Test
    void defaultMaxPoolSize() {
        assertEquals(10, DatabaseSettings.DEFAULT_MAX_POOL_SIZE);
    }

    @Test
    void defaultMinIdle() {
        assertEquals(2, DatabaseSettings.DEFAULT_MIN_IDLE);
    }

    @Test
    void defaultConnectionTimeoutMs() {
        assertEquals(30_000L, DatabaseSettings.DEFAULT_CONNECTION_TIMEOUT_MS);
    }

    @Test
    void defaultIdleTimeoutMs() {
        assertEquals(600_000L, DatabaseSettings.DEFAULT_IDLE_TIMEOUT_MS);
    }

    @Test
    void defaultMaxLifetimeMs() {
        assertEquals(1_800_000L, DatabaseSettings.DEFAULT_MAX_LIFETIME_MS);
    }

    // =========================================================================
    // Builder — URL path
    // =========================================================================

    @Test
    void getUrlReturnsSuppliedUrl() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .build();
        assertEquals("jdbc:h2:mem:test", s.getUrl());
    }

    @Test
    void getUsernameReturnsSuppliedValue() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .username("admin")
            .build();
        assertEquals("admin", s.getUsername());
    }

    @Test
    void getPasswordReturnsSuppliedValue() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .password("secret")
            .build();
        assertEquals("secret", s.getPassword());
    }

    @Test
    void nullUsernameBecomesEmptyString() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .username(null)
            .build();
        assertEquals("", s.getUsername());
    }

    @Test
    void nullPasswordBecomesEmptyString() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .password(null)
            .build();
        assertEquals("", s.getPassword());
    }

    @Test
    void driverClassNameReturnsSuppliedValue() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .driverClassName("org.h2.Driver")
            .build();
        assertEquals("org.h2.Driver", s.getDriverClassName());
    }

    @Test
    void driverClassNameIsNullWhenNotSet() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .build();
        assertNull(s.getDriverClassName());
    }

    @Test
    void maximumPoolSizeDefaultValue() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .build();
        assertEquals(DatabaseSettings.DEFAULT_MAX_POOL_SIZE, s.getMaximumPoolSize());
    }

    @Test
    void maximumPoolSizeCustomValue() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .maximumPoolSize(20)
            .build();
        assertEquals(20, s.getMaximumPoolSize());
    }

    @Test
    void minimumIdleDefaultValue() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .build();
        assertEquals(DatabaseSettings.DEFAULT_MIN_IDLE, s.getMinimumIdle());
    }

    @Test
    void minimumIdleCustomValue() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .minimumIdle(5)
            .build();
        assertEquals(5, s.getMinimumIdle());
    }

    @Test
    void connectionTimeoutMsDefaultValue() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .build();
        assertEquals(DatabaseSettings.DEFAULT_CONNECTION_TIMEOUT_MS, s.getConnectionTimeoutMs());
    }

    @Test
    void connectionTimeoutMsCustomValue() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .connectionTimeoutMs(5000L)
            .build();
        assertEquals(5000L, s.getConnectionTimeoutMs());
    }

    @Test
    void idleTimeoutMsDefaultValue() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .build();
        assertEquals(DatabaseSettings.DEFAULT_IDLE_TIMEOUT_MS, s.getIdleTimeoutMs());
    }

    @Test
    void idleTimeoutMsCustomValue() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .idleTimeoutMs(120_000L)
            .build();
        assertEquals(120_000L, s.getIdleTimeoutMs());
    }

    @Test
    void maxLifetimeMsDefaultValue() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .build();
        assertEquals(DatabaseSettings.DEFAULT_MAX_LIFETIME_MS, s.getMaxLifetimeMs());
    }

    @Test
    void maxLifetimeMsCustomValue() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .maxLifetimeMs(3_600_000L)
            .build();
        assertEquals(3_600_000L, s.getMaxLifetimeMs());
    }

    // =========================================================================
    // Builder — URL from parts
    // =========================================================================

    @Test
    void urlComposedFromPartsWithPort() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .jdbcScheme(JdbcScheme.MYSQL)
            .host("localhost")
            .port(3306)
            .databaseName("mydb")
            .build();
        assertEquals("jdbc:mysql://localhost:3306/mydb", s.getUrl());
    }

    @Test
    void urlComposedFromPartsWithoutPort() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .jdbcScheme(JdbcScheme.H2)
            .host("localhost")
            .databaseName("testdb")
            .build();
        assertEquals("jdbc:h2://localhost/testdb", s.getUrl());
    }

    @Test
    void urlComposedFromStringScheme() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .jdbcScheme("postgresql")
            .host("db.example.com")
            .port(5432)
            .databaseName("orders")
            .build();
        assertEquals("jdbc:postgresql://db.example.com:5432/orders", s.getUrl());
    }

    @Test
    void getJdbcSchemeReturnsSchemeStringWhenBuiltFromParts() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .jdbcScheme(JdbcScheme.POSTGRESQL)
            .host("localhost")
            .port(5432)
            .databaseName("testdb")
            .build();
        assertEquals("postgresql", s.getJdbcScheme());
    }

    @Test
    void getJdbcSchemeIsNullWhenUrlGiven() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .build();
        assertNull(s.getJdbcScheme());
    }

    @Test
    void getHostReturnsHostWhenBuiltFromParts() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .jdbcScheme(JdbcScheme.MYSQL)
            .host("db-host")
            .databaseName("mydb")
            .build();
        assertEquals("db-host", s.getHost());
    }

    @Test
    void getPortReturnsPortWhenBuiltFromParts() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .jdbcScheme(JdbcScheme.MYSQL)
            .host("localhost")
            .port(3306)
            .databaseName("mydb")
            .build();
        assertEquals(3306, s.getPort());
    }

    @Test
    void getPortIsZeroWhenNotExplicitlySet() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .jdbcScheme(JdbcScheme.H2)
            .host("localhost")
            .databaseName("testdb")
            .build();
        assertEquals(0, s.getPort());
    }

    @Test
    void getDatabaseNameReturnsNameWhenBuiltFromParts() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .jdbcScheme(JdbcScheme.MYSQL)
            .host("localhost")
            .databaseName("production")
            .build();
        assertEquals("production", s.getDatabaseName());
    }

    // =========================================================================
    // Builder — validation failures
    // =========================================================================

    @Test
    void buildThrowsWhenNoUrlAndNoScheme() {
        assertThrows(IllegalStateException.class, () ->
            DatabaseSettings.builder()
                .host("localhost")
                .databaseName("mydb")
                .build()
        );
    }

    @Test
    void buildThrowsWhenNoUrlAndNoHost() {
        assertThrows(IllegalStateException.class, () ->
            DatabaseSettings.builder()
                .jdbcScheme(JdbcScheme.MYSQL)
                .databaseName("mydb")
                .build()
        );
    }

    @Test
    void buildThrowsWhenNoUrlAndNoDatabaseName() {
        assertThrows(IllegalStateException.class, () ->
            DatabaseSettings.builder()
                .jdbcScheme(JdbcScheme.MYSQL)
                .host("localhost")
                .build()
        );
    }

    @Test
    void portZeroOmittedFromComposedUrl() {
        final DatabaseSettings s = DatabaseSettings.builder()
            .jdbcScheme(JdbcScheme.MYSQL)
            .host("localhost")
            .port(0)
            .databaseName("mydb")
            .build();
        assertTrue(s.getUrl().contains("localhost/mydb"));
    }
}
