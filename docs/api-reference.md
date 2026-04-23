---
title: API Reference
nav_order: 10
description: "Complete public method tables for every class and interface in Jaloquent"
---

# API Reference
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## config

See [Configuration](configuration) for complete examples for each class.

### `JaloquentConfig`

Static configuration — all members are `static`. See [Configuration](configuration) for full documentation.

| Method | Type | Description |
|--------|------|-------------|
| `setDatabaseSettings(DatabaseSettings)` | `void` | Store the settings used by `buildStore()` |
| `getDatabaseSettings()` | `DatabaseSettings` | Return current settings, or `null` if not set |
| `buildStore()` | `DataSourceJdbcStore` | Build a `DataSourceJdbcStore` from stored settings |
| `enableLogging(boolean)` | `void` | Toggle SLF4J logging on / off globally |
| `isLoggingEnabled()` | `boolean` | Query the current logging state |
| `isSLF4JPresent()` | `boolean` | Whether `org.slf4j.Logger` was found at JVM startup |
| `setLogger(Object)` | `void` | Inject a custom SLF4J `Logger` (accepts `Object` to avoid hard dependency) |
| `enableMetrics(boolean)` | `void` | Toggle Micrometer metrics on / off globally |
| `isMetricsEnabled()` | `boolean` | Query the current metrics state |
| `isMicrometerPresent()` | `boolean` | Whether Micrometer was found at JVM startup |
| `setMeterRegistry(Object)` | `void` | Inject a custom `MeterRegistry` (accepts `Object` to avoid hard dependency) |

---

### `DatabaseSettings`

Immutable JDBC connection settings. See [Configuration → Database settings](configuration#database-settings) for full documentation.

**Builder methods** (`DatabaseSettings.builder().…build()`):

| Method | Description |
|--------|-------------|
| `url(String)` | Full JDBC URL (optional if `jdbcScheme` + `host` + `databaseName` are supplied) |
| `jdbcScheme(JdbcScheme)` | Scheme via enum — preferred for common databases |
| `jdbcScheme(String)` | Scheme via raw string (escape hatch for non-standard drivers) |
| `host(String)` | Database server host or IP address |
| `port(int)` | TCP port; `0` omits from composed URL |
| `databaseName(String)` | Database / schema name |
| `username(String)` | Login user |
| `password(String)` | Login password |
| `driverClassName(String)` | Explicit driver class; `null` — auto-detect via SPI |
| `maximumPoolSize(int)` | Pool size hint (default: `10`) |
| `minimumIdle(int)` | Min idle connections hint (default: `2`) |
| `connectionTimeoutMs(long)` | Connection timeout hint in ms (default: `30 000`) |
| `idleTimeoutMs(long)` | Idle timeout hint in ms (default: `600 000`) |
| `maxLifetimeMs(long)` | Max lifetime hint in ms (default: `1 800 000`) |

**Getters:** `getUrl()`, `getJdbcScheme()`, `getHost()`, `getPort()`, `getDatabaseName()`, `getUsername()`, `getPassword()`, `getDriverClassName()`, `getMaximumPoolSize()`, `getMinimumIdle()`, `getConnectionTimeoutMs()`, `getIdleTimeoutMs()`, `getMaxLifetimeMs()`.

**Constants:** `DEFAULT_MYSQL_PORT` (`3306`), `DEFAULT_POSTGRESQL_PORT` (`5432`), `DEFAULT_MAX_POOL_SIZE` (`10`), `DEFAULT_MIN_IDLE` (`2`), `DEFAULT_CONNECTION_TIMEOUT_MS` (`30 000`), `DEFAULT_IDLE_TIMEOUT_MS` (`600 000`), `DEFAULT_MAX_LIFETIME_MS` (`1 800 000`).

---

### `JdbcScheme` (enum)

Type-safe JDBC scheme selector for [`DatabaseSettings.Builder#jdbcScheme(JdbcScheme)`](#databasesettings). See [Configuration → Available JdbcScheme constants](configuration#available-jdbcscheme-constants).

| Constant | Scheme string | Default port |
|----------|---------------|--------------|
| `MYSQL` | `mysql` | 3306 |
| `POSTGRESQL` | `postgresql` | 5432 |
| `MARIADB` | `mariadb` | 3306 |
| `MSSQL` | `sqlserver` | 1433 |
| `H2` | `h2` | — |
| `SQLITE` | `sqlite` | — |

`getScheme()` returns the string segment used after `jdbc:` in the URL.

---

## exception

See [Exceptions](exceptions) for usage patterns and catch hierarchy recommendations.

### `JaloquentException` (base)

| Constructor | Description |
|-------------|-------------|
| `JaloquentException(String message)` | Simple message |
| `JaloquentException(String message, Throwable cause)` | Wraps another exception |
| `JaloquentException(Throwable cause)` | Re-throws |

### `StorageException`

Extends `JaloquentException`. Same three constructors. Thrown on persistence / I/O failures.

### `ValidationException`

Extends `JaloquentException`. Same three constructors. Thrown on semantic validation failures.

### `ModelNotFoundException`

Extends `JaloquentException`. Same three constructors. Thrown when a required lookup returns no result.

---

## model

See [Models](models) for attribute access, mass-assignment, and persistence shortcuts.

### `BaseModel` (abstract)

| Member | Description |
|--------|-------------|
| `BaseModel(String id)` | Protected constructor |
| `getId()` | Return the primary key |
| `setId(String id)` | Set the primary key |
| `getStoragePath(String prefix)` | Returns `prefix/id`; bare `id` when prefix is blank |
| `abstract toMap()` | Serialize state to `Map<String, Object>` |
| `abstract fromMap(Map<String,Object>)` | Populate state from flat map |

---

### `Model` (abstract, extends `BaseModel`)

#### Attribute access

| Method | Returns | Description |
|--------|---------|-------------|
| `set(String key, Object value)` | `Model` | Set one attribute; `"id"` key routes to `setId` |
| `get(String key)` | `Object` | Get raw attribute value; `"id"` routes to `getId` |
| `getAs(String key, Class<T>)` | `T` | Type-coercing get; `null` when absent or unconvertible |
| `getAs(String key, Class<T>, T def)` | `T` | Same; returns `def` instead of `null` |
| `attributes()` | `Map<String, Object>` | Unmodifiable snapshot of the attribute map |
| `toMap()` | `Map<String, Object>` | Mutable copy of all attributes |
| `fromMap(Map<String,Object>)` | `void` | Populate attributes; `"id"` key updates `id` field |

#### Mass-assignment

| Method | Returns | Description |
|--------|---------|-------------|
| `setFillable(String... keys)` | `void` | Declare mass-assignable keys |
| `setGuarded(String... keys)` | `void` | Declare blocked keys |
| `getFillable()` | `Set<String>` | Unmodifiable fillable set |
| `getGuarded()` | `Set<String>` | Unmodifiable guarded set |
| `fill(Map<String,Object>)` | `void` | Permissive mass-assign; respects guarded; `id` always blocked |
| `update(Map<String,Object>)` | `void` | Strict mass-assign; only explicitly-declared fillable keys |

#### Persistence shortcuts

| Method | Returns | Description |
|--------|---------|-------------|
| `save(ModelRepository<T>)` | `Model` | Upsert via repo; returns `this` for chaining |
| `delete(ModelRepository<T>)` | `void` | Delete by current id via repo |
| `static find(ModelRepository<T>, String id)` | `T` | Look up by id; `null` if not found |
| `static queryBuilder()` | `QueryBuilder` | New `QueryBuilder` instance |

**Relation factories** (protected, call from subclass methods) — see [Relations](relations) for full documentation

| Method | Returns | Description |
|--------|---------|-------------|
| `hasOne(repo, foreignKey)` | `HasOne<T>` | FK on related model; local key defaults to `"id"` |
| `hasOne(repo, foreignKey, localKey)` | `HasOne<T>` | FK on related model; explicit local key |
| `hasMany(repo, foreignKey)` | `HasMany<T>` | FK on related model; local key defaults to `"id"` |
| `hasMany(repo, foreignKey, localKey)` | `HasMany<T>` | FK on related model; explicit local key |
| `belongsTo(repo, foreignKey)` | `BelongsTo<T>` | FK on this model pointing to related model's PK |
| `belongsToMany(relatedRepo, pivotRepo, pivotFactory, foreignKey, relatedKey)` | `BelongsToMany<T,P>` | Many-to-many via pivot |

---

### `Fillable`

| Method | Returns | Description |
|--------|---------|-------------|
| `setFillable(String... keys)` | `void` | Replace fillable set |
| `setGuarded(String... keys)` | `void` | Replace guarded set |
| `getFillable()` | `Set<String>` | Unmodifiable fillable set |
| `getGuarded()` | `Set<String>` | Unmodifiable guarded set |
| `isFillable(String key)` | `boolean` | Permissive check (`id` always `false`) |
| `isExplicitlyFillable(String key)` | `boolean` | Strict check (`id` always `false`) |

---

### `ModelRepository<T extends BaseModel>`

See [Repositories](repositories) for setup, routing behaviour, and test patterns. See [Transactions](transactions) for transaction support.

#### Constructors

| Constructor | Description |
|-------------|-------------|
| `ModelRepository(store, prefix, factory)` | Uses `SqlDialect.STANDARD` |
| `ModelRepository(store, prefix, factory, dialect)` | Explicit SQL dialect |

#### Operations

| Method | Returns | Description |
|--------|---------|-------------|
| `save(T model)` | `void` | Upsert |
| `find(String id)` | `Optional<T>` | Look up by primary key |
| `exists(String id)` | `boolean` | Check existence |
| `delete(String id)` | `void` | Delete by primary key |
| `query(Query q)` | `List<T>` | Parameterized SELECT |
| `deleteWhere(String column, Object value)` | `void` | DELETE WHERE column = ? |
| `deleteWhere(Query q)` | `void` | DELETE matching query conditions |
| `deleteAll(List<String> ids)` | `void` | Bulk DELETE WHERE id IN (…) |
| `deleteWhereInSubquery(String column, Query sub)` | `void` | DELETE WHERE column IN (subquery) — SQL only |
| `deleteWhereExists(Query sub)` | `void` | DELETE WHERE EXISTS (subquery) — SQL only |

---

### `TableRegistry`

| Method | Returns | Description |
|--------|---------|-------------|
| `static register(String prefix, String tableName, Map<String,String> columns)` | `void` | Register or overwrite table entry |
| `static get(String prefix)` | `TableMeta` | Returns metadata; `null` if absent |
| `static all()` | `Map<String, TableMeta>` | Unmodifiable view of all entries |

**`TableRegistry.TableMeta`**

| Method | Returns | Description |
|--------|---------|-------------|
| `tableName()` | `String` | SQL table name |
| `columns()` | `Map<String, String>` | Unmodifiable column name → SQL type map |

---

### `ModelFactory<T>` (functional interface)

```java
T create(String id, Map<String, Object> data);
```

Lambda factory used in `ModelRepository` constructor to reconstruct model instances
from persisted data.

---

### `PivotModel` (final, extends `Model`)

| Member | Description |
|--------|-------------|
| `static final ModelFactory<PivotModel> FACTORY` | Pre-built factory constant |
| `PivotModel(String id)` | Construct by id |
| `toMap()` | Mutable copy of all attributes |
| `fromMap(Map<String,Object>)` | Applies all entries via `set()` |

---

### `Factory<T extends Model>` (abstract)

See [Factories](factories) for full usage documentation.

| Method | Returns | Description |
|--------|---------|-------------|
| `Factory()` | — | Auto-discovers model class; `en-US` Jaker |
| `Factory(Faker)` | — | Custom Jaker; auto-discovers model class |
| `Factory(Class<T>)` | — | Explicit model class; default Jaker |
| `Factory(Faker, Class<T>)` | — | Explicit both |
| `abstract definition(Faker)` | `Map<String, Object>` | Return fake attributes for one instance |
| `state(Map<String,Object>)` | `Factory<T>` | Merge attribute overrides; chainable |
| `count(int)` | `FactoryCount<T>` | Switch to batch mode |
| `make()` | `T` | Build one transient model |
| `make(int)` | `List<T>` | Build N transient models |
| `create(ModelRepository<T>)` | `T` | Build + persist one model |
| `create(int, ModelRepository<T>)` | `List<T>` | Build + persist N models |
| `static discover(Class<M>)` | `Factory<M>` | Locate and instantiate `MFactory` by naming convention |

---

### `FactoryCount<T>` (final)

| Method | Returns | Description |
|--------|---------|-------------|
| `make()` | `List<T>` | Build N transient models |
| `create(ModelRepository<T>)` | `List<T>` | Build + persist N models |

---

### `HasFactory` (marker interface)

Implement on a model to signal that a corresponding `Factory` class exists.
No methods — purely a marker.

---

## relation

See [Relations](relations) for the full overview including N+1 avoidance patterns.

### `HasOne<T>` (final)

| Method | Returns | Description |
|--------|---------|-------------|
| `where(String column, Object value)` | `HasOne<T>` | Add WHERE equals constraint |
| `orderBy(String column, boolean asc)` | `HasOne<T>` | Sort for deterministic row selection |
| `get()` | `Optional<T>` | Execute; returns first match |
| `exists()` | `boolean` | True if at least one related record exists |

---

### `HasMany<T>` (final)

| Method | Returns | Description |
|--------|---------|-------------|
| `where(String column, Object value)` | `HasMany<T>` | Add WHERE equals constraint |
| `orderBy(String column, boolean asc)` | `HasMany<T>` | Sort results |
| `limit(int n)` | `HasMany<T>` | Cap result count |
| `get()` | `List<T>` | Execute; returns all matching records |
| `count()` | `long` | Number of matching related records |

---

### `BelongsTo<T>` (final)

| Method | Returns | Description |
|--------|---------|-------------|
| `get()` | `Optional<T>` | Look up related model using FK value |
| `exists()` | `boolean` | True if FK is set and related record exists |

---

### `BelongsToMany<T, P>` (final)

| Method | Returns | Description |
|--------|---------|-------------|
| `where(String column, Object value)` | `BelongsToMany<T,P>` | Add constraint on related model query |
| `orderBy(String column, boolean asc)` | `BelongsToMany<T,P>` | Sort related models |
| `get()` | `List<T>` | Resolve pivot rows, then load related models |
| `attach(String relatedId)` | `void` | Create pivot entry |
| `attach(String relatedId, Map<String,Object> extra)` | `void` | Create pivot entry with extra columns |
| `detach(String relatedId)` | `void` | Delete pivot entry |
| `detachAll()` | `void` | Bulk delete all pivot rows for this parent |
| `sync(List<String> desiredIds)` | `void` | Attach missing, detach removed; single bulk DELETE |
| `count()` | `long` | Number of related records through pivot |

---

## repository

See [Repositories](repositories) for setup and usage documentation.

### `Repository<T, ID>` (interface)

| Method | Returns | Description |
|--------|---------|-------------|
| `find(ID id)` | `Optional<T>` | Look up by primary key |
| `findAll()` | `List<T>` | Return all persisted entities |
| `save(T entity)` | `void` | Persist or update |
| `delete(ID id)` | `void` | Remove by primary key |

---

### `AbstractRepository<T, ID>` (abstract, implements `Repository`)

Convenience base backed by `DataStore` with SLF4J + Micrometer instrumentation — see [Configuration](configuration) for opt-in details.

**Implement in subclass:**

| Method | Returns | Description |
|--------|---------|-------------|
| `toMap(T entity)` | `Map<String, Object>` | Serialize |
| `fromMap(Map<String,Object>)` | `T` | Deserialize |
| `extractId(T entity)` | `ID` | Extract the primary key |

**Protected accessor:**

| Method | Returns | Description |
|--------|---------|-------------|
| `store()` | `DataStore` | The underlying data store |

---

## store

See [Repositories → Store interfaces](repositories#store-interfaces) for implementation guidance.

### `DataStore` (interface)

| Method | Returns | Description |
|--------|---------|-------------|
| `save(String path, Map<String,Object> data)` | `void` | Write / overwrite entry at path |
| `load(String path)` | `Optional<Map<String,Object>>` | Read entry |
| `delete(String path)` | `void` | Remove entry (no-op if absent) |
| `exists(String path)` | `boolean` | Existence check |

---

### `JdbcStore` (interface, extends `DataStore`)

| Method | Returns | Description |
|--------|---------|-------------|
| `query(String sql, List<Object> params)` | `List<Map<String,Object>>` | Parameterized SELECT |
| `executeUpdate(String sql, List<Object> params)` | `int` | Parameterized INSERT / UPDATE / DELETE; returns affected row count |

---

### `TransactionalJdbcStore` (interface, extends `JdbcStore`)

Opt-in interface that enables transaction support in [`ModelRepository`](#modelrepositoryt-extends-basemodel). See [Transactions](transactions) for full documentation.

| Method | Returns | Description |
|--------|---------|-------------|
| `beginTransaction()` | `void` | Start a database transaction |
| `commitTransaction()` | `void` | Commit the current transaction |
| `rollbackTransaction()` | `void` | Discard all changes since `beginTransaction()` |

---

### `DriverManagerDataSource` (implements `javax.sql.DataSource`)

Simple `DataSource` backed by `DriverManager.getConnection()`. No connection pool — one physical connection per call. Produced by [`JaloquentConfig.buildStore()`](#jaloquentconfig). See [Configuration → Database settings](configuration#database-settings) for when to use this vs. a pooled `DataSource`.

| Constructor | Description |
|-------------|-------------|
| `DriverManagerDataSource(DatabaseSettings)` | Create from stored settings |
| `DriverManagerDataSource(String url, String username, String password)` | Create directly |

---

### `DataSourceJdbcStore`

Production-ready `JdbcStore` backed by any `javax.sql.DataSource`. Construct directly with your own pooled `DataSource` (HikariCP, c3p0, …) for production, or obtain a `DriverManagerDataSource`-backed instance via [`JaloquentConfig.buildStore()`](#jaloquentconfig).

| Constructor | Description |
|-------------|-------------|
| `DataSourceJdbcStore(DataSource)` | Create from any `DataSource` |
