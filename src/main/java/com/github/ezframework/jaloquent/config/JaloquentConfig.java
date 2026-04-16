package com.github.ezframework.jaloquent.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Configuration class for Jaloquent logging and metrics.
 */
public class JaloquentConfig {

    /** Whether logging is enabled. */
    private static boolean loggingEnabled = true;

    /** Whether metrics are enabled. */
    private static boolean metricsEnabled = true;

    /** Custom logger instance, if provided. */
    private static Logger customLogger = null;

    /** Custom MeterRegistry instance, if provided. */
    private static MeterRegistry customRegistry = null;

    /**
     * Enable or disable logging.
     * @param enable true to enable logging, false to disable
     */
    public static void enableLogging(boolean enable) {
        loggingEnabled = enable;
    }

    /**
     * Check if logging is enabled.
     * @return true if logging is enabled
     */
    public static boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    /**
     * Set a custom logger instance.
     * @param logger the logger to use
     */
    public static void setLogger(Logger logger) {
        customLogger = logger;
    }

    /**
     * Get the logger for a given class, or null if logging is disabled.
     * @param clazz the class
     * @return the logger or null
     */
    public static Logger getLogger(Class<?> clazz) {
        if (!loggingEnabled) {
            return null;
        }
        return customLogger != null ? customLogger : LoggerFactory.getLogger(clazz);
    }

    /**
     * Enable or disable metrics.
     * @param enable true to enable metrics, false to disable
     */
    public static void enableMetrics(boolean enable) {
        metricsEnabled = enable;
    }

    /**
     * Check if metrics are enabled.
     * @return true if metrics are enabled
     */
    public static boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    /**
     * Set a custom MeterRegistry instance.
     * @param registry the registry to use
     */
    public static void setMeterRegistry(MeterRegistry registry) {
        customRegistry = registry;
    }

    /**
     * Get the MeterRegistry, or null if metrics are disabled.
     * @return the registry or null
     */
    public static MeterRegistry getMeterRegistry() {
        if (!metricsEnabled) {
            return null;
        }
        return customRegistry != null ? customRegistry : Metrics.globalRegistry;
    }
}
