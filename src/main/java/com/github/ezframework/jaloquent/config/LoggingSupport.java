package com.github.ezframework.jaloquent.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Internal SLF4J adapter.
 *
 * <p>This class holds all references to SLF4J types so that the main
 * {@link JaloquentConfig} class — and every caller — remains free of SLF4J imports.
 * The JVM loads this class lazily; if SLF4J is absent from the runtime classpath,
 * the {@link NoClassDefFoundError} is caught by the enclosing guard in
 * {@link JaloquentConfig} which then permanently disables logging.
 */
final class LoggingSupport {

    private LoggingSupport() { }

    /**
     * Log at INFO level.
     *
     * @param source the originating class
     * @param custom optional pre-configured logger; any other value is ignored
     * @param format SLF4J parameterized message format
     * @param args   format arguments
     */
    static void info(Class<?> source, Object custom, String format, Object... args) {
        logger(source, custom).info(format, args);
    }

    /**
     * Log at ERROR level with a causal exception.
     *
     * @param source  the originating class
     * @param custom  optional pre-configured logger; any other value is ignored
     * @param message error description
     * @param cause   causal exception (stack trace is included in output)
     */
    static void error(Class<?> source, Object custom, String message, Throwable cause) {
        logger(source, custom).error(message, cause);
    }

    /**
     * Log at ERROR level with a parameterized format string.
     *
     * <p>Per SLF4J convention, if the last element of {@code args} is a
     * {@link Throwable} its stack trace is printed automatically.
     *
     * @param source the originating class
     * @param custom optional pre-configured logger; any other value is ignored
     * @param format SLF4J parameterized message format
     * @param args   format arguments; the last may be a {@link Throwable}
     */
    static void error(Class<?> source, Object custom, String format, Object... args) {
        logger(source, custom).error(format, args);
    }

    private static Logger logger(Class<?> source, Object custom) {
        if (custom instanceof Logger) {
            return (Logger) custom;
        }
        return LoggerFactory.getLogger(source);
    }
}
