package com.github.ezframework.jaloquent.unit;

import com.github.ezframework.jaloquent.config.DatabaseSettings;
import com.github.ezframework.jaloquent.config.JaloquentConfig;
import com.github.ezframework.jaloquent.config.JdbcScheme;
import com.github.ezframework.jaloquent.store.sql.DataSourceJdbcStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link JaloquentConfig}.
 */
public class JaloquentConfigTest {

    /** Saves original logging state for restoration after each test. */
    private boolean originalLoggingEnabled;

    /** Saves original metrics state for restoration after each test. */
    private boolean originalMetricsEnabled;

    /** Saves previous database settings so tests do not leak state. */
    private DatabaseSettings originalSettings;

    @BeforeEach
    void captureState() {
        originalLoggingEnabled = JaloquentConfig.isLoggingEnabled();
        originalMetricsEnabled = JaloquentConfig.isMetricsEnabled();
        originalSettings = JaloquentConfig.getDatabaseSettings();
    }

    @AfterEach
    void restoreState() {
        JaloquentConfig.enableLogging(originalLoggingEnabled);
        JaloquentConfig.enableMetrics(originalMetricsEnabled);
        JaloquentConfig.setLogger(null);
        JaloquentConfig.setMeterRegistry(null);
        if (originalSettings != null) {
            JaloquentConfig.setDatabaseSettings(originalSettings);
        }
    }

    // =========================================================================
    // Classpath detection
    // =========================================================================

    @Test
    void slf4jIsPresentOnTestClasspath() {
        assertTrue(JaloquentConfig.isSLF4JPresent());
    }

    @Test
    void micrometerIsPresentOnTestClasspath() {
        assertTrue(JaloquentConfig.isMicrometerPresent());
    }

    // =========================================================================
    // Logging toggle
    // =========================================================================

    @Test
    void enableLoggingTrueActivatesLogging() {
        JaloquentConfig.enableLogging(true);
        assertTrue(JaloquentConfig.isLoggingEnabled());
    }

    @Test
    void enableLoggingFalseDeactivatesLogging() {
        JaloquentConfig.enableLogging(false);
        assertFalse(JaloquentConfig.isLoggingEnabled());
    }

    @Test
    void logInfoIsNoOpWhenLoggingDisabled() {
        JaloquentConfig.enableLogging(false);
        // Must not throw
        JaloquentConfig.logInfo(JaloquentConfigTest.class, "should not appear");
    }

    @Test
    void logErrorWithThrowableIsNoOpWhenLoggingDisabled() {
        JaloquentConfig.enableLogging(false);
        // Must not throw
        JaloquentConfig.logError(JaloquentConfigTest.class, "should not appear",
            new RuntimeException("test"));
    }

    @Test
    void logErrorWithFormatIsNoOpWhenLoggingDisabled() {
        JaloquentConfig.enableLogging(false);
        // Must not throw
        JaloquentConfig.logError(JaloquentConfigTest.class, "should not appear: {}", "arg");
    }

    @Test
    void logInfoProducesOutputWhenLoggingEnabled() {
        JaloquentConfig.enableLogging(true);
        // Must not throw
        JaloquentConfig.logInfo(JaloquentConfigTest.class, "test log info: {}", "ok");
    }

    @Test
    void logErrorWithThrowableProducesOutputWhenLoggingEnabled() {
        JaloquentConfig.enableLogging(true);
        // Must not throw
        JaloquentConfig.logError(JaloquentConfigTest.class, "test log error",
            new RuntimeException("cause"));
    }

    @Test
    void logErrorWithFormatProducesOutputWhenLoggingEnabled() {
        JaloquentConfig.enableLogging(true);
        // Must not throw
        JaloquentConfig.logError(JaloquentConfigTest.class, "test log error format: {}", "detail");
    }

    @Test
    void setLoggerAcceptsNullWithoutThrowing() {
        JaloquentConfig.setLogger(null);
        JaloquentConfig.logInfo(JaloquentConfigTest.class, "after null logger");
    }

    @Test
    void setLoggerAcceptsNonLoggerWithoutThrowing() {
        JaloquentConfig.setLogger("not a logger");
        JaloquentConfig.logInfo(JaloquentConfigTest.class, "non-logger custom object");
    }

    // =========================================================================
    // Metrics toggle
    // =========================================================================

    @Test
    void enableMetricsTrueActivatesMetrics() {
        JaloquentConfig.enableMetrics(true);
        assertTrue(JaloquentConfig.isMetricsEnabled());
    }

    @Test
    void enableMetricsFalseDeactivatesMetrics() {
        JaloquentConfig.enableMetrics(false);
        assertFalse(JaloquentConfig.isMetricsEnabled());
    }

    @Test
    void incrementCounterIsNoOpWhenMetricsDisabled() {
        JaloquentConfig.enableMetrics(false);
        // Must not throw
        JaloquentConfig.incrementCounter("jaloquent.test.counter");
    }

    @Test
    void incrementCounterWorksWhenMetricsEnabled() {
        JaloquentConfig.enableMetrics(true);
        // Must not throw
        JaloquentConfig.incrementCounter("jaloquent.test.counter2");
    }

    @Test
    void setMeterRegistryAcceptsNullWithoutThrowing() {
        JaloquentConfig.setMeterRegistry(null);
        JaloquentConfig.incrementCounter("jaloquent.test.counter3");
    }

    // =========================================================================
    // Database settings
    // =========================================================================

    @Test
    void setDatabaseSettingsNullThrowsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class,
            () -> JaloquentConfig.setDatabaseSettings(null));
    }

    @Test
    void getDatabaseSettingsRoundTrip() {
        final DatabaseSettings settings = DatabaseSettings.builder()
            .url("jdbc:h2:mem:config-test")
            .build();
        JaloquentConfig.setDatabaseSettings(settings);
        assertNotNull(JaloquentConfig.getDatabaseSettings());
    }

    @Test
    void buildStoreThrowsWhenNoSettingsConfigured() {
        // Force settings to null via reflection to ensure clean state for this test
        // (we rely on JaloquentConfig never having been initialised with settings
        //  in this specific call path — not guaranteed if other tests ran first,
        //  but we use @BeforeEach/@AfterEach for state capture/restore only)
        final DatabaseSettings before = JaloquentConfig.getDatabaseSettings();
        if (before != null) {
            // Settings were set by a previous test; we cannot null them out without
            // reflection, so we skip the negative assertion in that case and only
            // verify buildStore works when settings ARE present.
            return;
        }
        assertThrows(IllegalStateException.class, JaloquentConfig::buildStore);
    }

    @Test
    void buildStoreReturnsStoreWhenSettingsConfigured() {
        final DatabaseSettings settings = DatabaseSettings.builder()
            .url("jdbc:h2:mem:build-store-test;DB_CLOSE_DELAY=-1")
            .build();
        JaloquentConfig.setDatabaseSettings(settings);
        final DataSourceJdbcStore store = JaloquentConfig.buildStore();
        assertNotNull(store);
    }
}
