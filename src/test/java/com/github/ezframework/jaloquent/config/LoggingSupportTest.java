package com.github.ezframework.jaloquent.config;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for the package-private {@link LoggingSupport} class.
 *
 * <p>This test class lives in the same package as {@link LoggingSupport} so it can
 * access the package-private type directly.
 */
public class LoggingSupportTest {

    // =========================================================================
    // Default (per-class) logger path
    // =========================================================================

    @Test
    void infoWithNullCustomUsesDefaultLogger() {
        assertDoesNotThrow(() ->
            LoggingSupport.info(LoggingSupportTest.class, null, "default logger info: {}", "ok"));
    }

    @Test
    void errorWithThrowableAndNullCustomUsesDefaultLogger() {
        assertDoesNotThrow(() ->
            LoggingSupport.error(LoggingSupportTest.class, null,
                "default logger error", new RuntimeException("test")));
    }

    @Test
    void errorWithFormatAndNullCustomUsesDefaultLogger() {
        assertDoesNotThrow(() ->
            LoggingSupport.error(LoggingSupportTest.class, null,
                "default logger format error: {}", "detail"));
    }

    // =========================================================================
    // Custom SLF4J Logger path
    // =========================================================================

    @Test
    void infoWithCustomLoggerUsesCustomLogger() {
        final org.slf4j.Logger custom = LoggerFactory.getLogger("custom.logger");
        assertDoesNotThrow(() ->
            LoggingSupport.info(LoggingSupportTest.class, custom, "custom logger info: {}", "ok"));
    }

    @Test
    void errorWithThrowableAndCustomLoggerUsesCustomLogger() {
        final org.slf4j.Logger custom = LoggerFactory.getLogger("custom.logger");
        assertDoesNotThrow(() ->
            LoggingSupport.error(LoggingSupportTest.class, custom,
                "custom logger error", new RuntimeException("cause")));
    }

    @Test
    void errorWithFormatAndCustomLoggerUsesCustomLogger() {
        final org.slf4j.Logger custom = LoggerFactory.getLogger("custom.logger");
        assertDoesNotThrow(() ->
            LoggingSupport.error(LoggingSupportTest.class, custom,
                "custom logger format error: {}", "detail"));
    }

    // =========================================================================
    // Non-Logger custom object falls back to default logger
    // =========================================================================

    @Test
    void infoWithNonLoggerCustomFallsBackToDefaultLogger() {
        assertDoesNotThrow(() ->
            LoggingSupport.info(LoggingSupportTest.class, "not a logger", "fallback info"));
    }

    @Test
    void errorWithNonLoggerCustomFallsBackToDefaultLogger() {
        assertDoesNotThrow(() ->
            LoggingSupport.error(LoggingSupportTest.class, 42,
                "fallback error", new RuntimeException("cause")));
    }
}
