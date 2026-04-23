---
title: Repositories
nav_order: 4
description: "Setting up ModelRepository, registering SQL tables, and performing CRUD operations"
---

# Repositories
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Overview

`ModelRepository<T>` is the single entry point for all persistence operations.
It inspects the store it receives at construction time and routes every call to
either the **SQL path** (JDBC, via `JdbcStore`) or the **flat-map path** (`DataStore`).

```text
ModelRepository<T>
  ├── JdbcStore path  → used when store implements JdbcStore AND a TableRegistry entry exists
  └── DataStore path  → fallback for any store implementing only DataStore
```

---

## Store interfaces

### `DataStore`

The minimal persistence contract — key/value pairs keyed by a string path:

```java
public interface DataStore {
    void                       save(String path, Map<String, Object> data);
    Optional<Map<String, Object>> load(String path);
    void                       delete(String path);
    boolean                    exists(String path);
}
```

### `JdbcStore`

Extend `DataStore` with parameterized SQL execution:

```java
public interface JdbcStore extends DataStore {
    List<Map<String, Object>> query(String sql, List<Object> params);
    int executeUpdate(String sql, List<Object> params);
}
```

{: .warning }
> Always pass user-supplied values through `List<Object> params` (the `?` bind parameters).
> The SQL path never interpolates model attribute values directly into query strings,
> making SQL injection structurally impossible.

To activate the SQL path, implement **both** `DataStore` and `JdbcStore` on your store class:

```java
public class MyDataSource implements DataStore, JdbcStore {

    @Override
    public List<Map<String, Object>> query(String sql, List<Object> params) {
        // execute with your JDBC connection pool
    }

    @Override
    public int executeUpdate(String sql, List<Object> params) {
        // execute INSERT / UPDATE / DELETE
    }

    // ... DataStore methods ...
}
```

---

## TableRegistry

`TableRegistry` maps a repository prefix to a SQL table name and its column
definitions. **A `JdbcStore` entry alone is not enough** — a matching registry
entry must also exist for the SQL path to activate.

```java
TableRegistry.register(
    "players",          // prefix — matches the repo constructor argument
    "player_data",      // SQL table name
    Map.of(
        "id",      "VARCHAR(36) PRIMARY KEY",
        "name",    "VARCHAR(64)",
        "coins",   "INT",
        "level",   "INT"
    )
);
```

| Method | Description |
|--------|-------------|
| `TableRegistry.register(prefix, tableName, columns)` | Register or overwrite a table entry |
| `TableRegistry.get(prefix)` | Returns `TableMeta` or `null` |
| `TableRegistry.all()` | Unmodifiable view of all entries |

{: .note }
> `TableRegistry` is JVM-static. In tests, use a unique `UUID.randomUUID().toString()` prefix
> per test class to prevent cross-test pollution. See the [testing guide](#test-isolation).

---

## Creating a ModelRepository

### Flat-map store (no SQL)

```java
ModelRepository<Player> repo = new ModelRepository<>(
    myDataStore,    // implements DataStore
    "players",      // prefix used for path construction and TableRegistry lookup
    (id, data) -> {
        Player p = new Player(id);
        p.fromMap(data);
        return p;
    }
);
```

### SQL store

```java
// 1. Register the table
TableRegistry.register("players", "player_data", columnMap);

// 2. Provide a store that implements both DataStore and JdbcStore
ModelRepository<Player> repo = new ModelRepository<>(
    myJdbcDataStore,
    "players",
    (id, data) -> { Player p = new Player(id); p.fromMap(data); return p; }
);
// → all repo calls now use SQL automatically
```

### Custom SQL dialect

Pass an explicit `SqlDialect` as the fourth argument:

```java
import com.github.ezframework.javaquerybuilder.query.dialect.SqlDialect;

ModelRepository<Player> repo = new ModelRepository<>(
    store, "players", factory, SqlDialect.MYSQL
);
```

Supported dialects: `STANDARD`, `MYSQL`, `POSTGRESQL`, `H2`.

---

## CRUD operations

### `save(model)` — upsert

On the SQL path this executes an `INSERT … ON DUPLICATE KEY UPDATE` equivalent
(dialect-aware). On the flat-map path it calls `DataStore.save`.

```java
repo.save(player); // returns void
player.save(repo); // convenience shortcut on Model — returns this
```

### `find(id)` — read by primary key

```java
Optional<Player> result = repo.find("some-uuid");
result.ifPresent(p -> System.out.println(p.getName()));
```

### `exists(id)`

```java
boolean active = repo.exists("some-uuid");
```

### `delete(id)`

```java
repo.delete("some-uuid");
player.delete(repo); // convenience shortcut on Model
```

---

## Querying

Pass a `Query` object built by `Model.queryBuilder()` to `repo.query()`:

```java
List<Player> rich = repo.query(
    Model.queryBuilder()
        .where("coins", ">", 1000)
        .orderBy("name", true)
        .limit(20)
        .build()
);
```

See the [Queries](queries) page for the full query builder reference.

{: .note }
> For atomic multi-step operations across multiple `save()` and `delete()` calls,
> see [Transactions](transactions).

---

## Bulk and conditional delete

### `deleteWhere(column, value)`

```java
repo.deleteWhere("status", "banned");
```

### `deleteWhere(Query)`

```java
repo.deleteWhere(
    Model.queryBuilder().where("coins", "<", 0).build()
);
```

### `deleteAll(List<String> ids)` — bulk delete by primary key

On the SQL path this generates a single `DELETE … WHERE id IN (?, ?, …)`.

```java
repo.deleteAll(List.of("uuid-1", "uuid-2", "uuid-3"));
```

### `deleteWhereInSubquery(column, subquery)` — SQL only

```java
Query activePlayers = Model.queryBuilder()
    .from("player_data")
    .where("status", "=", "active")
    .build();

repo.deleteWhereInSubquery("player_id", activePlayers);
// → DELETE FROM ... WHERE player_id IN (SELECT ...)
```

### `deleteWhereExists(subquery)` — SQL only

```java
repo.deleteWhereExists(subquery);
// → DELETE FROM ... WHERE EXISTS (SELECT ...)
```

---

## Test isolation

When writing integration tests against a JVM-static `TableRegistry`, isolate each
test class with a unique prefix so entries never collide:

```java
private final String prefix = "test-" + UUID.randomUUID();

@BeforeEach
void setUp() {
    TableRegistry.register(prefix, "player_data", Map.of(
        "id",     "VARCHAR(36) PRIMARY KEY",
        "name",   "VARCHAR(64)"
    ));
    repo = new ModelRepository<>(store, prefix, factory);
}
```

---

## AbstractRepository

For non-`Model` domain objects, extend `AbstractRepository<T, ID>` which provides
`find`, `findAll`, `save`, and `delete` backed by `DataStore` with built-in
SLF4J logging and Micrometer counter instrumentation — see [Configuration](configuration) for opt-in details.

```java
public class PlayerRepository extends AbstractRepository<Player, String> {

    public PlayerRepository(DataStore store) {
        super(store, "players");
    }

    @Override
    protected Map<String, Object> toMap(Player p) { return p.toMap(); }

    @Override
    protected Player fromMap(Map<String, Object> data) {
        Player p = new Player((String) data.get("id"));
        p.fromMap(data);
        return p;
    }

    @Override
    protected String extractId(Player p) { return p.getId(); }
}
```

---

## See also

- [Queries](queries) — building `Query` objects to pass to `repo.query()` and `repo.deleteWhere()`
- [Transactions](transactions) — grouping repository operations into atomic units
- [Configuration](configuration) — wiring a production-ready `DataSourceJdbcStore` via `DatabaseSettings`
