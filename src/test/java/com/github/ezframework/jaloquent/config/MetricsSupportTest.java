package com.github.ezframework.jaloquent.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

/**
 * Unit tests for the package-private {@link MetricsSupport} class.
 *
 * <p>This test class lives in the same package as {@link MetricsSupport} so it can
 * access the package-private type directly.
 */
public class MetricsSupportTest {

    @Test
    void incrementWithNullRegistryUsesGlobalRegistry() {
        assertDoesNotThrow(() ->
            MetricsSupport.increment("jaloquent.metrics.support.test.global", null));
    }

    @Test
    void incrementWithCustomMeterRegistryUsesIt() {
        final SimpleMeterRegistry registry = new SimpleMeterRegistry();
        assertDoesNotThrow(() ->
            MetricsSupport.increment("jaloquent.metrics.support.test.custom", registry));
    }

    @Test
    void incrementWithNonRegistryObjectUsesGlobalRegistry() {
        assertDoesNotThrow(() ->
            MetricsSupport.increment("jaloquent.metrics.support.test.fallback", "not a registry"));
    }
}
