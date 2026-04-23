package com.github.ezframework.jaloquent.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;

/**
 * Internal Micrometer adapter.
 *
 * <p>This class holds all references to Micrometer types so that the main
 * {@link JaloquentConfig} class — and every caller — remains free of Micrometer
 * imports. The JVM loads this class lazily; if Micrometer is absent from the
 * runtime classpath, the {@link NoClassDefFoundError} is caught by the enclosing
 * guard in {@link JaloquentConfig} which then permanently disables metrics.
 */
final class MetricsSupport {

    private MetricsSupport() { }

    /**
     * Increment the named counter by one.
     *
     * <p>When {@code registry} is a {@link MeterRegistry} that registry is used;
     * otherwise Micrometer's global composite registry is the target.
     *
     * @param name     meter name (e.g. {@code "jaloquent.repository.save"})
     * @param registry optional custom {@link MeterRegistry}; may be {@code null}
     */
    static void increment(String name, Object registry) {
        final MeterRegistry reg = registry instanceof MeterRegistry
            ? (MeterRegistry) registry
            : Metrics.globalRegistry;
        Counter.builder(name).register(reg).increment();
    }
}
