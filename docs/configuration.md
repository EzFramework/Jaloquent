---
title: Configuration
nav_order: 8
description: "Database settings, opt-in SLF4J logging, and Micrometer metrics via JaloquentConfig"
---

# Configuration
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Overview

`JaloquentConfig` is a static configuration class that controls three cross-cutting
concerns across all Jaloquent components:

1. **Database connection** — supply JDBC credentials once; Jaloquent builds the store for you.
2. **SLF4J logging** — opt-in; auto-detected at startup, zero overhead when absent.
3. **Micrometer metrics** — opt-in; auto-detected at startup, zero overhead when absent.

Both SLF4J and Micrometer are **optional** dependencies — Jaloquent works with neither,
either, or both on the class-path.

---

## Database settings

`DatabaseSettings` is an immutable value object that holds your JDBC connection details.
Pass it to `JaloquentConfig`, then call `buildStore()` to get a ready-to-use
`DataSourceJdbcStore` backed by a simple `DriverManager` connection (no pool —
suitable for development and low-concurrency workloads). Pass the store to a
[`ModelRepository`](repositories) or [`AbstractRepository`](repositories#abstractrepository).

### Quick start — full URL

```java
import com.github.ezframework.jaloquent.config.DatabaseSettings;
import com.github.ezframework.jaloquent.config.JaloquentConfig;
import com.github.ezframework.jaloquent.store.sql.JdbcStore;

DatabaseSettings settings = DatabaseSettings.builder()
    .url("jdbc:mysql://localhost:3306/mydb")
    .username("app")
    .password("secret")
    .build();

JaloquentConfig.setDatabaseSettings(settings);
JdbcStore store = JaloquentConfig.buildStore();

// Use `store` when constructing ModelRepository or AbstractRepository
```

### Quick start — individual parts

Instead of writing the JDBC URL by hand, supply the connection parts and let
Jaloquent compose the URL for you:

```java
import com.github.ezframework.jaloquent.config.DatabaseSettings;
import com.github.ezframework.jaloquent.config.JaloquentConfig;
import com.github.ezframework.jaloquent.store.sql.JdbcStore;

DatabaseSettings settings = DatabaseSettings.builder()
    .jdbcScheme(JdbcScheme.MYSQL)                    // type-safe, IDE-discoverable
    .host("localhost")
    .port(DatabaseSettings.DEFAULT_MYSQL_PORT)       // 3306
    .databaseName("mydb")
    .username("app")
    .password("secret")
    .build();  // composes jdbc:mysql://localhost:3306/mydb

JaloquentConfig.setDatabaseSettings(settings);
JdbcStore store = JaloquentConfig.buildStore();
```

The two approaches are equivalent — `build()` stores the resulting URL in both
cases. Use whichever fits your configuration style; a `.properties` file or
environment variables map naturally to individual fields.

#### Available `JdbcScheme` constants

| Constant | Scheme string | Default port |
|----------|---------------|--------------|
| `JdbcScheme.MYSQL` | `mysql` | 3306 |
| `JdbcScheme.POSTGRESQL` | `postgresql` | 5432 |
| `JdbcScheme.MARIADB` | `mariadb` | 3306 |
| `JdbcScheme.MSSQL` | `sqlserver` | 1433 |
| `JdbcScheme.H2` | `h2` | — |
| `JdbcScheme.SQLITE` | `sqlite` | — |

For drivers not listed (e.g. `oracle:thin`), use the raw-string overload:

```java
.jdbcScheme("oracle:thin")   // escape hatch — accepts any string
```

### All available settings

| Setting | Default | Description |
|---------|---------|-------------|
| `url` | *(optional)* | Full JDBC connection URL. When omitted, `jdbcScheme` + `host` + `databaseName` (and optionally `port`) are used to compose it. |
| `jdbcScheme` | *(optional)* | JDBC scheme — use a [`JdbcScheme`](#available-jdbcscheme-constants) constant (e.g. `JdbcScheme.MYSQL`) or a raw string for non-standard drivers. |
| `host` | *(optional)* | Database server host name or IP address (e.g. `localhost`, `db.example.com`). |
| `port` | `0` (omit) | Database server TCP port. `0` omits the port from the composed URL so the driver uses its built-in default. Use `DEFAULT_MYSQL_PORT` (3306) or `DEFAULT_POSTGRESQL_PORT` (5432). |
| `databaseName` | *(optional)* | The schema / catalog name that appears at the end of the URL path. |
| `username` | `""` | Database username |
| `password` | `""` | Database password |
| `driverClassName` | `null` (auto) | Fully-qualified JDBC driver class. When `null`, the driver must be discoverable via the `DriverManager` SPI (DriverManager auto-loads drivers on the class-path in Java 6+). |
| `maximumPoolSize` | `10` | Hint: maximum number of connections in your pool |
| `minimumIdle` | `2` | Hint: minimum idle connections to keep open |
| `connectionTimeoutMs` | `30 000` | Hint: max wait for a connection from the pool (ms) |
| `idleTimeoutMs` | `600 000` | Hint: max time a connection may sit idle in the pool (ms) |
| `maxLifetimeMs` | `1 800 000` | Hint: max lifetime of a connection before it is retired (ms) |

> **Either `url` or (`jdbcScheme` + `host` + `databaseName`) must be supplied.** `build()` throws
> `IllegalStateException` when neither is present.
>
> **Pool settings are hints.** `JaloquentConfig.buildStore()` creates a
> `DriverManagerDataSource` which opens one physical connection per call — it has
> no built-in pool. The pool fields are provided so you can read them back and wire
> them into your own pool (HikariCP, c3p0, …) without storing the values in two places.

### Using a pooled DataSource in production

For production use, bring your own connection pool and pass it directly to
`DataSourceJdbcStore`:

```java
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import com.github.ezframework.jaloquent.config.DatabaseSettings;
import com.github.ezframework.jaloquent.store.sql.DataSourceJdbcStore;

DatabaseSettings s = DatabaseSettings.builder()
    .url("jdbc:postgresql://db.prod/myapp")
    .username("app")
    .password(System.getenv("DB_PASS"))
    .maximumPoolSize(20)
    .minimumIdle(5)
    .connectionTimeoutMs(5_000)
    .build();

HikariConfig hk = new HikariConfig();
hk.setJdbcUrl(s.getUrl());
hk.setUsername(s.getUsername());
hk.setPassword(s.getPassword());
hk.setMaximumPoolSize(s.getMaximumPoolSize());
hk.setMinimumIdle(s.getMinimumIdle());
hk.setConnectionTimeout(s.getConnectionTimeoutMs());
hk.setIdleTimeout(s.getIdleTimeoutMs());
hk.setMaxLifetime(s.getMaxLifetimeMs());

DataSourceJdbcStore store = new DataSourceJdbcStore(new HikariDataSource(hk));
```

---

## Logging (opt-in)

Jaloquent supports [SLF4J](https://www.slf4j.org/) for structured logging but does
**not** require it. At JVM startup, Jaloquent checks whether `org.slf4j.Logger` is
present on the class-path:

- **SLF4J found** → logging enabled by default; Jaloquent logs at `INFO` level for
  normal operations and `ERROR` for exceptions.
- **SLF4J absent** → logging is silently off; no `NoClassDefFoundError` is thrown.

### Adding SLF4J / Logback

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.13</version>
</dependency>
<dependency>
    <groupId>ch.qos.logback</groupId>
    <artifactId>logback-classic</artifactId>
    <version>1.5.6</version>
</dependency>
```

### Enable / disable

```java
import com.github.ezframework.jaloquent.config.JaloquentConfig;

// Suppress all Jaloquent log output even when SLF4J is on the class-path
JaloquentConfig.enableLogging(false);

// Re-enable (default when SLF4J is present)
JaloquentConfig.enableLogging(true);

// Runtime presence / state checks
boolean slf4jAvailable = JaloquentConfig.isSLF4JPresent();
boolean loggingActive  = JaloquentConfig.isLoggingEnabled();
```

### Custom logger

Inject a pre-configured SLF4J `Logger` from your own setup:

```java
import org.slf4j.LoggerFactory;

// Accept Object so the call compiles even when SLF4J is not directly imported
JaloquentConfig.setLogger(LoggerFactory.getLogger("my.app.jaloquent"));
```

---

## Metrics (opt-in)

Jaloquent instruments persistence operations with [Micrometer](https://micrometer.io/)
counters. At JVM startup it checks whether `io.micrometer.core.instrument.Counter`
is present:

- **Micrometer found** → metrics enabled by default; counters are written to
  `Metrics.globalRegistry`.
- **Micrometer absent** → metrics are silently off; no exception is thrown.

### Adding Micrometer

```xml
<!-- pom.xml — add the backend registry you use, e.g. Prometheus -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-core</artifactId>
    <version>1.12.5</version>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
    <version>1.12.5</version>
</dependency>
```

### Enable / disable

```java
// Disable metrics, e.g. in unit tests
JaloquentConfig.enableMetrics(false);

// Re-enable (default when Micrometer is present)
JaloquentConfig.enableMetrics(true);

boolean micrometerAvailable = JaloquentConfig.isMicrometerPresent();
boolean metricsActive       = JaloquentConfig.isMetricsEnabled();
```

### Custom registry

```java
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;

PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
// Accept Object so the call compiles without a Micrometer import at the call site
JaloquentConfig.setMeterRegistry(registry);
```

---

## Recommended setup

### Development (no pooling)

```java
// Application startup — one call wires database, logging, and metrics
DatabaseSettings settings = DatabaseSettings.builder()
    .url("jdbc:h2:mem:dev;DB_CLOSE_DELAY=-1")
    .build();
JaloquentConfig.setDatabaseSettings(settings);
JdbcStore store = JaloquentConfig.buildStore();
```

### Production (pooled DataSource, custom observability)

```java
// Wire HikariCP (see "Using a pooled DataSource" above), then configure observability:
JaloquentConfig.setLogger(LoggerFactory.getLogger("com.myapp.persistence"));
JaloquentConfig.setMeterRegistry(myPrometheusRegistry);
```

### Tests (silence all output)

```java
@BeforeAll
static void configure() {
    JaloquentConfig.enableLogging(false);
    JaloquentConfig.enableMetrics(false);
}
```

---

## Full method reference

| Method | Description |
|--------|-------------|
| `setDatabaseSettings(DatabaseSettings)` | Store the settings used by `buildStore()` |
| `getDatabaseSettings()` | Return the current `DatabaseSettings`, or `null` if not set |
| `buildStore()` | Build a `DataSourceJdbcStore` from the stored settings |
| `enableLogging(boolean)` | Toggle SLF4J logging on / off globally |
| `isLoggingEnabled()` | Query the current logging state |
| `isSLF4JPresent()` | Whether `org.slf4j.Logger` was found on the class-path at startup |
| `setLogger(Object)` | Inject a custom SLF4J `Logger` (accepts `Object` to avoid a hard compile-time dependency) |
| `enableMetrics(boolean)` | Toggle Micrometer metrics on / off globally |
| `isMetricsEnabled()` | Query the current metrics state |
| `isMicrometerPresent()` | Whether Micrometer was found on the class-path at startup |
| `setMeterRegistry(Object)` | Inject a custom `MeterRegistry` (accepts `Object` to avoid a hard compile-time dependency) |
