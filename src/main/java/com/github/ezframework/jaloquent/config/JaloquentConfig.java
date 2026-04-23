package com.github.ezframework.jaloquent.config;

import com.github.ezframework.jaloquent.store.sql.DataSourceJdbcStore;
import com.github.ezframework.jaloquent.store.sql.DriverManagerDataSource;

/**
 * Central configuration for Jaloquent.
 *
 * <p>Controls three cross-cutting concerns:
 * <ul>
 *   <li><b>Database settings</b> — provide JDBC connection details so Jaloquent
 *       can build a ready-to-use {@link DataSourceJdbcStore} via
 *       {@link #buildStore()}.</li>
 *   <li><b>Logging</b> — opt-in SLF4J support; auto-enabled when the SLF4J API
 *       is detected on the classpath. Override with {@link #enableLogging(boolean)}.
 *       SLF4J and Logback are declared as {@code optional} Maven dependencies —
 *       add them explicitly if you need log output.</li>
 *   <li><b>Metrics</b> — opt-in Micrometer support; auto-enabled when Micrometer
 *       is detected on the classpath. Override with {@link #enableMetrics(boolean)}.
 *       Micrometer is declared as an {@code optional} Maven dependency — add it
 *       explicitly if you need metrics.</li>
 * </ul>
 *
 * <p>Because SLF4J and Micrometer are optional, this class itself contains
 * <em>no</em> direct imports for those libraries. All SLF4J code is isolated in
 * {@link LoggingSupport} and all Micrometer code in {@link MetricsSupport}; both
 * classes are loaded lazily only when the feature is active.
 */
public final class JaloquentConfig {

    /** {@code true} when {@code org.slf4j.Logger} is present on the classpath. */
    private static final boolean SLF4J_PRESENT;

    /** {@code true} when {@code io.micrometer.core.instrument.Counter} is present on the classpath. */
    private static final boolean MICROMETER_PRESENT;

    /** Whether SLF4J logging is enabled. Initialised to {@link #SLF4J_PRESENT}. */
    private static volatile boolean loggingEnabled;

    /** Whether Micrometer metrics are enabled. Initialised to {@link #MICROMETER_PRESENT}. */
    private static volatile boolean metricsEnabled;

    /**
     * Optional custom SLF4J {@code Logger} instance, stored as {@code Object} so
     * that this class has no compile-time SLF4J dependency. Pass an
     * {@code org.slf4j.Logger} via {@link #setLogger(Object)}.
     */
    private static Object customLogger = null;

    /**
     * Optional custom Micrometer {@code MeterRegistry}, stored as {@code Object}
     * so that this class has no compile-time Micrometer dependency. Pass an
     * {@code io.micrometer.core.instrument.MeterRegistry} via
     * {@link #setMeterRegistry(Object)}.
     */
    private static Object customRegistry = null;

    /** Database connection settings; {@code null} until set by the caller. */
    private static DatabaseSettings databaseSettings = null;

    static {
        SLF4J_PRESENT = isClassPresent("org.slf4j.Logger");
        MICROMETER_PRESENT = isClassPresent("io.micrometer.core.instrument.Counter");
        loggingEnabled = SLF4J_PRESENT;
        metricsEnabled = MICROMETER_PRESENT;
    }

    private JaloquentConfig() { }

    // =========================================================================
    // Logging
    // =========================================================================

    /**
     * Enable or disable SLF4J logging globally.
     *
     * <p>Has no practical effect when SLF4J is absent from the classpath;
     * check {@link #isSLF4JPresent()} first if needed.
     *
     * @param enabled {@code true} to enable, {@code false} to suppress all output
     */
    public static void enableLogging(boolean enabled) {
        loggingEnabled = enabled;
    }

    /**
     * Return whether SLF4J logging is currently enabled.
     *
     * @return {@code true} if logging is active
     */
    public static boolean isLoggingEnabled() {
        return loggingEnabled;
    }

    /**
     * Return whether the SLF4J API was detected on the runtime classpath.
     *
     * <p>When this returns {@code false}, logging is disabled by default and
     * adding {@code org.slf4j:slf4j-api} to your project's dependencies will
     * activate it automatically.
     *
     * @return {@code true} if SLF4J is available
     */
    public static boolean isSLF4JPresent() {
        return SLF4J_PRESENT;
    }

    /**
     * Inject a custom SLF4J {@code Logger} instance.
     *
     * <p>Accepts {@code Object} to avoid a hard compile-time dependency on SLF4J.
     * Pass an {@code org.slf4j.Logger} at runtime; any other value is silently
     * ignored and the per-class default logger is used instead.
     *
     * @param logger an {@code org.slf4j.Logger}, or {@code null} to reset to the default
     */
    public static void setLogger(Object logger) {
        customLogger = logger;
    }

    // =========================================================================
    // Metrics
    // =========================================================================

    /**
     * Enable or disable Micrometer metrics globally.
     *
     * <p>Has no practical effect when Micrometer is absent from the classpath;
     * check {@link #isMicrometerPresent()} first if needed.
     *
     * @param enabled {@code true} to enable, {@code false} to suppress all counter updates
     */
    public static void enableMetrics(boolean enabled) {
        metricsEnabled = enabled;
    }

    /**
     * Return whether Micrometer metrics are currently enabled.
     *
     * @return {@code true} if metrics are active
     */
    public static boolean isMetricsEnabled() {
        return metricsEnabled;
    }

    /**
     * Return whether Micrometer was detected on the runtime classpath.
     *
     * <p>When this returns {@code false}, metrics are disabled by default and
     * adding {@code io.micrometer:micrometer-core} to your project will activate
     * them automatically.
     *
     * @return {@code true} if Micrometer is available
     */
    public static boolean isMicrometerPresent() {
        return MICROMETER_PRESENT;
    }

    /**
     * Inject a custom Micrometer {@code MeterRegistry}.
     *
     * <p>Accepts {@code Object} to avoid a hard compile-time dependency on Micrometer.
     * Pass an {@code io.micrometer.core.instrument.MeterRegistry} at runtime; any
     * other value is silently ignored and Micrometer's global registry is used instead.
     *
     * @param registry an {@code io.micrometer.core.instrument.MeterRegistry},
     *                 or {@code null} to reset to the global registry
     */
    public static void setMeterRegistry(Object registry) {
        customRegistry = registry;
    }

    // =========================================================================
    // Database settings
    // =========================================================================

    /**
     * Set database connection settings used by {@link #buildStore()}.
     *
     * <p>Example:
     * <pre>{@code
     * JaloquentConfig.setDatabaseSettings(
     *     DatabaseSettings.builder()
     *         .url("jdbc:mysql://localhost:3306/mydb")
     *         .username("root")
     *         .password("secret")
     *         .build()
     * );
     * DataSourceJdbcStore store = JaloquentConfig.buildStore();
     * }</pre>
     *
     * @param settings JDBC connection settings; must not be {@code null}
     * @throws IllegalArgumentException when {@code settings} is {@code null}
     */
    public static void setDatabaseSettings(DatabaseSettings settings) {
        if (settings == null) {
            throw new IllegalArgumentException("settings must not be null");
        }
        databaseSettings = settings;
    }

    /**
     * Return the currently configured database settings, or {@code null} when none
     * have been set.
     *
     * @return current {@link DatabaseSettings} or {@code null}
     */
    public static DatabaseSettings getDatabaseSettings() {
        return databaseSettings;
    }

    /**
     * Build and return a new {@link DataSourceJdbcStore} from the configured
     * {@link DatabaseSettings}.
     *
     * <p>The store is backed by a {@link DriverManagerDataSource}, which opens a
     * new physical connection per operation (no pooling). For production workloads
     * supply your own pooled {@code DataSource} (e.g. HikariCP) to
     * {@link DataSourceJdbcStore} directly.
     *
     * @return a ready-to-use {@link DataSourceJdbcStore}
     * @throws IllegalStateException when no database settings have been configured
     */
    public static DataSourceJdbcStore buildStore() {
        if (databaseSettings == null) {
            throw new IllegalStateException(
                "No database settings configured. "
                    + "Call JaloquentConfig.setDatabaseSettings(...) first.");
        }
        return new DataSourceJdbcStore(new DriverManagerDataSource(databaseSettings));
    }

    // =========================================================================
    // Public helpers — used by Model, ModelRepository, AbstractRepository, Transaction
    // =========================================================================

    /**
     * Log at INFO level on behalf of {@code source}.
     *
     * @param source the originating class
     * @param format SLF4J parameterized message format
     * @param args   format arguments
     */
    public static void logInfo(Class<?> source, String format, Object... args) {
        if (!loggingEnabled) {
            return;
        }
        try {
            LoggingSupport.info(source, customLogger, format, args);
        }
        catch (NoClassDefFoundError e) {
            loggingEnabled = false;
        }
    }

    /**
     * Log at ERROR level with a causal exception on behalf of {@code source}.
     *
     * @param source  the originating class
     * @param message error description
     * @param cause   causal exception
     */
    public static void logError(Class<?> source, String message, Throwable cause) {
        if (!loggingEnabled) {
            return;
        }
        try {
            LoggingSupport.error(source, customLogger, message, cause);
        }
        catch (NoClassDefFoundError e) {
            loggingEnabled = false;
        }
    }

    /**
     * Log at ERROR level with a parameterized format string on behalf of {@code source}.
     *
     * <p>Per SLF4J convention, if the last element of {@code args} is a
     * {@link Throwable} its stack trace is included in the output automatically.
     *
     * @param source the originating class
     * @param format SLF4J parameterized message format
     * @param args   format arguments; the last may be a {@link Throwable}
     */
    public static void logError(Class<?> source, String format, Object... args) {
        if (!loggingEnabled) {
            return;
        }
        try {
            LoggingSupport.error(source, customLogger, format, args);
        }
        catch (NoClassDefFoundError e) {
            loggingEnabled = false;
        }
    }

    /**
     * Increment the named Micrometer counter by one.
     *
     * @param name meter name (e.g. {@code "jaloquent.repository.save"})
     */
    public static void incrementCounter(String name) {
        if (!metricsEnabled) {
            return;
        }
        try {
            MetricsSupport.increment(name, customRegistry);
        }
        catch (NoClassDefFoundError e) {
            metricsEnabled = false;
        }
    }

    private static boolean isClassPresent(String className) {
        try {
            Class.forName(className);
            return true;
        }
        catch (ClassNotFoundException e) {
            return false;
        }
    }
}
