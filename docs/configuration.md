---
title: Configuration
nav_order: 8
description: "Toggling SLF4J logging and Micrometer metrics via JaloquentConfig"
---

# Configuration
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Overview

`JaloquentConfig` is a static configuration class that controls two cross-cutting
concerns across all Jaloquent components: **SLF4J logging** and **Micrometer metrics**.
Both are enabled by default; each can be toggled independently or replaced with a
custom implementation.

---

## Logging

Jaloquent uses [SLF4J](https://www.slf4j.org/) and ships with Logback as the default
implementation. Log output is at `DEBUG` level for normal operations and `ERROR`
level for exceptions.

### Enable / disable

```java
import com.github.ezframework.jaloquent.config.JaloquentConfig;

// Disable all Jaloquent log output (e.g., in production where you only want metrics)
JaloquentConfig.enableLogging(false);

// Re-enable (default)
JaloquentConfig.enableLogging(true);

// Check current state
boolean logging = JaloquentConfig.isLoggingEnabled();
```

### Custom logger

Inject a pre-configured SLF4J `Logger` from your own logging setup:

```java
import org.slf4j.LoggerFactory;

JaloquentConfig.setLogger(LoggerFactory.getLogger("my.app.jaloquent"));
```

Internally, Jaloquent calls `JaloquentConfig.getLogger(MyClass.class)` which
returns `null` when logging is disabled — ensuring zero log overhead when off.

---

## Metrics

Jaloquent instruments persistence operations with [Micrometer](https://micrometer.io/)
counters. By default it writes to `Metrics.globalRegistry`.

### Enable / disable

```java
// Disable metrics (e.g., for unit tests)
JaloquentConfig.enableMetrics(false);

// Re-enable (default)
JaloquentConfig.enableMetrics(true);

boolean metrics = JaloquentConfig.isMetricsEnabled();
```

### Custom registry

Inject your own `MeterRegistry` — for example, a Prometheus registry bound to
your HTTP server:

```java
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import io.micrometer.prometheusmetrics.PrometheusConfig;

PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
JaloquentConfig.setMeterRegistry(registry);
```

When metrics are disabled, `JaloquentConfig.getMeterRegistry()` returns `null`
and no meters are created.

---

## Recommended setup

### Production

In production, use your application's existing logging and metrics infrastructure:

```java
// Application startup
JaloquentConfig.setLogger(LoggerFactory.getLogger("com.myapp.persistence"));
JaloquentConfig.setMeterRegistry(myPromRegisty);
```

### Tests

Silence Jaloquent output in unit tests:

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
| `enableLogging(boolean)` | Toggle SLF4J logging on / off globally |
| `isLoggingEnabled()` | Query the current logging state |
| `setLogger(Logger)` | Inject a custom SLF4J logger |
| `getLogger(Class<?>)` | Returns the active logger, or `null` if logging is disabled |
| `enableMetrics(boolean)` | Toggle Micrometer metrics on / off globally |
| `isMetricsEnabled()` | Query the current metrics state |
| `setMeterRegistry(MeterRegistry)` | Inject a custom `MeterRegistry` |
| `getMeterRegistry()` | Returns the active registry, or `null` if metrics are disabled |
