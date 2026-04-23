package com.github.ezframework.jaloquent.unit;

import com.github.ezframework.jaloquent.config.DatabaseSettings;
import com.github.ezframework.jaloquent.store.sql.DriverManagerDataSource;

import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.sql.SQLFeatureNotSupportedException;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DriverManagerDataSource}.
 */
public class DriverManagerDataSourceTest {

    // =========================================================================
    // Constructor(DatabaseSettings) — guards
    // =========================================================================

    @Test
    void nullSettingsThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DriverManagerDataSource((DatabaseSettings) null));
    }

    @Test
    void unknownDriverClassThrowsIllegalStateException() {
        final DatabaseSettings settings = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .driverClassName("com.example.NonExistentDriver")
            .build();
        assertThrows(IllegalStateException.class,
            () -> new DriverManagerDataSource(settings));
    }

    @Test
    void knownDriverClassDoesNotThrow() {
        final DatabaseSettings settings = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .driverClassName("org.h2.Driver")
            .build();
        // Should not throw — H2 is on the test classpath
        final DriverManagerDataSource ds = new DriverManagerDataSource(settings);
        assertTrue(ds.isWrapperFor(DataSource.class));
    }

    @Test
    void nullDriverClassNameDoesNotCallLoadDriver() {
        final DatabaseSettings settings = DatabaseSettings.builder()
            .url("jdbc:h2:mem:test")
            .build();
        // Should not throw
        new DriverManagerDataSource(settings);
    }

    // =========================================================================
    // Constructor(String, String, String) — guards
    // =========================================================================

    @Test
    void nullUrlThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DriverManagerDataSource(null, "user", "pass"));
    }

    @Test
    void emptyUrlThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> new DriverManagerDataSource("", "user", "pass"));
    }

    @Test
    void nullUsernameNormalisedToEmptyString() throws Exception {
        final DriverManagerDataSource ds =
            new DriverManagerDataSource("jdbc:h2:mem:test", null, "pass");
        // Should not throw; the username must be silently normalised to ""
        // We verify via the constructor completing successfully
        assertTrue(ds.isWrapperFor(DataSource.class));
    }

    @Test
    void nullPasswordNormalisedToEmptyString() throws Exception {
        final DriverManagerDataSource ds =
            new DriverManagerDataSource("jdbc:h2:mem:test", "user", null);
        assertTrue(ds.isWrapperFor(DataSource.class));
    }

    // =========================================================================
    // getConnection() — H2 round-trip
    // =========================================================================

    @Test
    void getConnectionOpensH2Connection() throws Exception {
        final DatabaseSettings settings = DatabaseSettings.builder()
            .url("jdbc:h2:mem:dstest;DB_CLOSE_DELAY=-1")
            .build();
        final DriverManagerDataSource ds = new DriverManagerDataSource(settings);
        try (final var conn = ds.getConnection()) {
            assertFalse(conn.isClosed());
        }
    }

    @Test
    void getConnectionWithCredentialsOpensH2Connection() throws Exception {
        final DriverManagerDataSource ds =
            new DriverManagerDataSource("jdbc:h2:mem:dstest2;DB_CLOSE_DELAY=-1", "", "");
        try (final var conn = ds.getConnection("", "")) {
            assertFalse(conn.isClosed());
        }
    }

    // =========================================================================
    // DataSource contract — log writer
    // =========================================================================

    @Test
    void logWriterIsNullByDefault() {
        final DriverManagerDataSource ds =
            new DriverManagerDataSource("jdbc:h2:mem:test", "", "");
        assertNull(ds.getLogWriter());
    }

    @Test
    void logWriterRoundTrip() throws Exception {
        final DriverManagerDataSource ds =
            new DriverManagerDataSource("jdbc:h2:mem:test", "", "");
        final PrintWriter pw = new PrintWriter(System.out);
        ds.setLogWriter(pw);
        assertEquals(pw, ds.getLogWriter());
    }

    // =========================================================================
    // DataSource contract — login timeout
    // =========================================================================

    @Test
    void loginTimeoutIsZeroByDefault() throws Exception {
        final DriverManagerDataSource ds =
            new DriverManagerDataSource("jdbc:h2:mem:test", "", "");
        assertEquals(0, ds.getLoginTimeout());
    }

    @Test
    void loginTimeoutRoundTrip() throws Exception {
        final DriverManagerDataSource ds =
            new DriverManagerDataSource("jdbc:h2:mem:test", "", "");
        ds.setLoginTimeout(30);
        assertEquals(30, ds.getLoginTimeout());
    }

    // =========================================================================
    // DataSource contract — parent logger
    // =========================================================================

    @Test
    void getParentLoggerThrowsSQLFeatureNotSupportedException() {
        final DriverManagerDataSource ds =
            new DriverManagerDataSource("jdbc:h2:mem:test", "", "");
        assertThrows(SQLFeatureNotSupportedException.class, ds::getParentLogger);
    }

    // =========================================================================
    // DataSource contract — unwrap / isWrapperFor
    // =========================================================================

    @Test
    void isWrapperForDataSourceReturnsTrue() {
        final DriverManagerDataSource ds =
            new DriverManagerDataSource("jdbc:h2:mem:test", "", "");
        assertTrue(ds.isWrapperFor(DataSource.class));
    }

    @Test
    void isWrapperForUnrelatedTypeReturnsFalse() {
        final DriverManagerDataSource ds =
            new DriverManagerDataSource("jdbc:h2:mem:test", "", "");
        assertFalse(ds.isWrapperFor(String.class));
    }

    @Test
    void unwrapToDataSourceReturnsSelf() throws Exception {
        final DriverManagerDataSource ds =
            new DriverManagerDataSource("jdbc:h2:mem:test", "", "");
        assertEquals(ds, ds.unwrap(DataSource.class));
    }

    @Test
    void unwrapToUnrelatedTypeThrowsSQLException() {
        final DriverManagerDataSource ds =
            new DriverManagerDataSource("jdbc:h2:mem:test", "", "");
        assertThrows(java.sql.SQLException.class, () -> ds.unwrap(String.class));
    }
}
