# Changelog

All notable changes to Jaloquent are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
Jaloquent uses [Semantic Versioning](https://semver.org/).

---

## [1.2.0] - 2026-04-23

### Added

**Database migration framework**

- `Migration` interface with `getId()`, `up(Schema)`, and `down(Schema)` methods
- `MigrationBlueprint` fluent column-definition DSL with shorthands for all common column types: integer, decimal, string, binary, boolean, date/time, JSON, UUID, and blob
- `Schema` executor with `create()`, `drop()`, and `dropIfExists()` operations
- `MigrationRunner` with batch-based `run()` and `rollback()`, backed by an auto-managed `jaloquent_migrations` tracking table
- `MigrationException` wrapping all migration-layer errors

**Configuration and connection settings**

- `DatabaseSettings` — immutable builder POJO for all JDBC connection settings; supports supplying a full URL or composing one from `jdbcScheme + host + port + databaseName`
- `JdbcScheme` — enum of well-known JDBC scheme identifiers (MYSQL, POSTGRESQL, MARIADB, MSSQL, H2, SQLITE)
- `DriverManagerDataSource` — minimal `javax.sql.DataSource` backed by `DriverManager`, suitable for development and test use
- `JaloquentConfig.setDatabaseSettings(DatabaseSettings)` and `JaloquentConfig.buildStore()` — one-call store construction from settings
- `LoggingSupport` and `MetricsSupport` — package-private isolation layers so `JaloquentConfig` carries zero hard compile-time dependencies on SLF4J or Micrometer

**Opt-in SLF4J and Micrometer**

- SLF4J, Logback, and Micrometer are now declared `optional` in the POM; they are auto-detected at startup via `Class.forName` and silently disabled when absent
- `JaloquentConfig.enableLogging(boolean)` / `enableMetrics(boolean)` — explicit toggle for both features
- `JaloquentConfig.setLogger(Object)` — inject a custom `org.slf4j.Logger` without a hard compile-time SLF4J dependency
- `JaloquentConfig.setMeterRegistry(Object)` — inject a custom `io.micrometer.core.instrument.MeterRegistry`

**PostgreSQL support**

- Dialect matrix extended with PostgreSQL
- PostgreSQL integration tests added for `DataSourceJdbcStore`
- `MigrationRunnerSqlFeatureTest` parameterised over both MySQL and PostgreSQL dialects

### Changed

- All logging and metrics call-sites in `Model`, `ModelRepository`, `Transaction`, and `AbstractRepository` migrated to `JaloquentConfig.logInfo` / `logError` / `incrementCounter`
- `JaloquentConfig` rewritten to support the new opt-in detection model; previous hard imports of SLF4J and Micrometer types removed
- JavaQueryBuilder dependency bumped to 1.1.0

### Fixed

- Javadoc `<h3>` headings in `MigrationRunner`, `InMemoryDataStore`, and `DataSourceJdbcStore` class comments changed to `<h2>` to fix "heading used out of sequence" errors
- Broken `@link` reference in `TransactionalJdbcStore` corrected to point to the right `query(String, List)` signature
- `@throws Exception` replaced with `@throws StorageException` on all public methods of `HasOne`, `HasMany`, `BelongsTo`, and `BelongsToMany`
- Missing `@param` Javadoc added to constructors of `JaloquentException`, `StorageException`, `ValidationException`, and `ModelNotFoundException`
- `Fillable` given an explicit no-arg constructor with Javadoc to replace the implicit default
- `docs/configuration.md` blank line between consecutive block-quotes replaced with `>` continuation (fixes MD028 lint)

### Tests

- 491 tests total (up from 392 at 1.1.0), 0 failures
- `DatabaseSettingsTest` — builder getters, URL composition from parts, port-zero omission, `IllegalStateException` on missing parts
- `JdbcSchemeTest` — all six constants, `valueOf`, `values()` length
- `DriverManagerDataSourceTest` — null guards, driver loading, full `DataSource` contract
- `JaloquentConfigTest` — logging and metrics toggles, `setDatabaseSettings`, `buildStore`, all log and counter paths enabled and as no-ops
- `LoggingSupportTest` and `MetricsSupportTest` — all method overloads with null, custom, and fallback arguments
- `TransactionFeatureTest` — added `ThrowingRollbackStore` fixture and test asserting `Transaction.close()` swallows rollback exceptions

---

## [1.1.0] — see the [1.1.0 release notes](https://github.com/EzFramework/Jaloquent/releases/tag/1.1.0)
