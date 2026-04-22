---
layout: home
title: Jaloquent
nav_order: 1
description: "Eloquent-style active-record ORM for Java"
permalink: /
---

# Jaloquent

[![JitPack](https://jitpack.io/v/EzFramework/Jaloquent.svg)](https://jitpack.io/#EzFramework/Jaloquent)
[![GitHub Packages](https://img.shields.io/badge/GitHub_Packages-1.1.0-blue?logo=github)](https://github.com/EzFramework/Jaloquent/packages)
[![Coverage](https://img.shields.io/codecov/c/github/EzFramework/Jaker)](https://codecov.io/github/EzFramework/Jaker)

**Jaloquent** is an Eloquent-style active-record ORM layer for Java, built on the
[EzFramework Java Query Builder](https://github.com/EzFramework/JavaQueryBuilder).
It provides a consistent, fluent API for both SQL (JDBC) and flat-map stores,
inspired by Laravel's Eloquent ORM.

---

## Features

- **Dual-store routing** — the same model class works against an in-memory flat-map store *and*
  any JDBC data source; switching is transparent
- **Active-record helpers** — `model.save(repo)`, `Model.find(repo, id)`, `model.delete(repo)`
  with no boilerplate
- **Type-safe attribute access** — `getAs(key, Class<T>)` with built-in Integer ↔ Long ↔ String coercion
- **Mass-assignment protection** — `fill()` (permissive) and `update()` (strict) respect `setFillable`
  / `setGuarded` declarations; `id` is always block-listed
- **Query builder** — fluent `WHERE`, `ORDER BY`, `LIMIT` with `?` bind parameters — SQL injection is
  structurally impossible
- **Four relation types** — `HasOne`, `HasMany`, `BelongsTo`, `BelongsToMany` with lazy loading,
  `sync`, `attach`, `detach`, and `detachAll`
- **Laravel-style factories** — `Factory<T>` + `HasFactory` marker for fixture generation backed by Jaker
- **Database transactions** — atomic multi-step operations via `TransactionalJdbcStore`;
  try-with-resources handle or lambda callback with automatic commit/rollback
- **Schema migrations** — version-controlled DDL with `Migration`, `Schema`, and `MigrationRunner`;
  batch-based `run()` / `rollback()` with automatic tracking table
- **SLF4J + Micrometer** — opt-in observability with zero mandatory dependencies

---

## Quick start

**1. Add Jaloquent via JitPack:**

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>

<dependency>
  <groupId>com.github.EzFramework</groupId>
  <artifactId>jaloquent</artifactId>
  <version>1.1.0</version>
</dependency>
```

**2. Define a model:**

```java
public class Player extends Model {
    public Player(String id) { super(id); }

    public String getName()       { return getAs("name", String.class, ""); }
    public void   setName(String n) { set("name", n); }

    public int  getCoins()        { return getAs("coins", Integer.class, 0); }
    public void setCoins(int c)   { set("coins", c); }
}
```

**3. Register a SQL table (optional — skip for flat-map stores):**

```java
TableRegistry.register("players", "player_data", Map.of(
    "id",     "VARCHAR(36) PRIMARY KEY",
    "name",   "VARCHAR(64)",
    "coins",  "INT"
));
```

**4. Create a repository and persist:**

```java
ModelRepository<Player> repo = new ModelRepository<>(
    myStore,    // implements DataStore (+ JdbcStore for SQL)
    "players",
    (id, data) -> { Player p = new Player(id); p.fromMap(data); return p; }
);

Player p = new Player(UUID.randomUUID().toString());
p.setName("Alice");
p.setCoins(500);
p.save(repo);

Optional<Player> found = repo.find(p.getId());
```

---

## Documentation

| Page | What it covers |
|------|----------------|
| [Installation](installation) | Maven, Gradle, JitPack, requirements |
| [Models](models) | Defining models, attributes, mass-assignment |
| [Repositories](repositories) | Setup, TableRegistry, CRUD, bulk operations |
| [Queries](queries) | Query builder — filters, ordering, limits |
| [Relations](relations) | HasOne, HasMany, BelongsTo, BelongsToMany overview |
| &nbsp;&nbsp;[HasOne](relations/has-one) | One-to-one (FK on related model) |
| &nbsp;&nbsp;[HasMany](relations/has-many) | One-to-many (FK on related model) |
| &nbsp;&nbsp;[BelongsTo](relations/belongs-to) | Inverse — FK on this model |
| &nbsp;&nbsp;[BelongsToMany](relations/belongs-to-many) | Many-to-many via pivot table |
| [Factories](factories) | Generating test fixtures with `Factory<T>` |
| [Configuration](configuration) | Logging and metrics via `JaloquentConfig` |
| [Transactions](transactions) | Atomic multi-step operations, commit/rollback |
| [Migrations](migrations) | Schema versioning, column types, `run()` / `rollback()` |
| [Exceptions](exceptions) | Error hierarchy and handling patterns |
| [API Reference](api-reference) | Full public-method tables for every class |
