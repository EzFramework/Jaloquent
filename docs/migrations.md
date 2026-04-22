---
title: Migrations
nav_order: 10
description: "Schema versioning with Migration, Schema, and MigrationRunner"
---

# Migrations
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Overview

Jaloquent ships a Laravel-inspired migration system that lets you define your
database schema in version-controlled Java classes and apply or revert changes
with a single method call.

```text
Migration          ← interface — you implement getId(), up(Schema), down(Schema)
  └── CreateUsersTable
        ↓
MigrationRunner.run()       → applies all pending migrations
MigrationRunner.rollback()  → reverts the last batch
```

Migrations are tracked in an auto-created `jaloquent_migrations` table so the
runner can skip migrations that have already been applied.

---

## Creating a migration

Implement `Migration`. The ID must be unique across all migrations;
use a timestamp prefix so the list stays ordered lexicographically.

```java
import com.github.ezframework.jaloquent.migration.Migration;
import com.github.ezframework.jaloquent.migration.Schema;
import com.github.ezframework.jaloquent.exception.MigrationException;

public class CreateUsersTable implements Migration {

    @Override
    public String getId() {
        return "2026_04_23_001_create_users_table";
    }

    @Override
    public void up(Schema schema) throws MigrationException {
        schema.create("users", t -> t
            .id()
            .string("email", 255)
            .bool("active")
            .timestamps()
        );
    }

    @Override
    public void down(Schema schema) throws MigrationException {
        schema.dropIfExists("users");
    }
}
```

---

## Running migrations

Construct a `MigrationRunner` with a `JdbcStore`, a `SqlDialect`, and the
complete ordered list of all known migrations.

```java
import com.github.ezframework.jaloquent.migration.MigrationRunner;
import com.github.ezframework.javaquerybuilder.query.sql.SqlDialect;

List<Migration> migrations = List.of(
    new CreateUsersTable(),
    new CreateOrdersTable()
);

MigrationRunner runner = new MigrationRunner(store, SqlDialect.MYSQL, migrations);
runner.run();      // applies every migration that has not been applied yet
runner.rollback(); // reverts all migrations from the most recent batch
```

`run()` is **idempotent** — calling it a second time after all migrations have
already been applied is a no-op.

---

## Batch semantics

Every call to `run()` groups all migrations it applies into a single *batch*.
`rollback()` always reverts the entire most-recent batch, in reverse list order.

```text
run()           → applies m1, m2, m3 → batch 1
run()           → no-op (all already applied)

runner2.run()   → applies m4, m5     → batch 2

rollback()      → reverts m5, m4     → batch 2 removed
rollback()      → reverts m3, m2, m1 → batch 1 removed
```

---

## The `Schema` executor

`Schema` is passed to the `up()` and `down()` callbacks and provides three DDL
operations.

### `create(table, blueprint)`

Creates a table. The second argument is a lambda that receives a
`MigrationBlueprint` and chains column definitions:

```java
schema.create("orders", t -> t
    .id()
    .string("reference", 64)
    .integer("quantity")
    .decimal("total", 10, 2)
    .bool("shipped")
    .timestamps()
);
```

### `drop(table)`

Drops a table unconditionally. Throws `MigrationException` if the table does
not exist.

```java
schema.drop("orders");
```

### `dropIfExists(table)`

Drops a table if it exists; silently succeeds when it is absent.

```java
schema.dropIfExists("orders");
```

---

## Column types

All column definitions go through `MigrationBlueprint`, which wraps the
[`ColumnType`](https://github.com/EzFramework/JavaQueryBuilder) class from
JavaQueryBuilder 1.1.0. Two ways to add a column:

1. **Shorthand method** — covers the most common types (see table below).
2. **Raw `ColumnType`** — for any type not listed, use  
   `t.column("name", ColumnType.FLOAT)` or  
   `t.column("name", ColumnType.decimal(8, 4).notNull())`.

### Integer types

| Method | SQL type | `NOT NULL` |
|--------|----------|-----------|
| `tinyInteger(name)` | `TINYINT` | ✓ |
| `smallInteger(name)` | `SMALLINT` | ✓ |
| `integer(name)` | `INT` | ✓ |
| `bigInteger(name)` | `BIGINT` | ✓ |

### Decimal / floating-point types

| Method | SQL type | `NOT NULL` |
|--------|----------|-----------|
| `decimal(name, precision, scale)` | `DECIMAL(p, s)` | ✓ |

For `FLOAT`, `DOUBLE`, and `REAL` use the raw form:

```java
t.column("score",  ColumnType.FLOAT)
 .column("ratio",  ColumnType.DOUBLE)
 .column("weight", ColumnType.REAL)
```

### String / character types

| Method | SQL type | `NOT NULL` |
|--------|----------|-----------|
| `id()` | `VARCHAR(36)` + `PRIMARY KEY` | ✓ |
| `string(name, length)` | `VARCHAR(length)` | ✓ |
| `uuid(name)` | `UUID` | ✓ |
| `text(name)` | `TEXT` | — |
| `tinyText(name)` | `TINYTEXT` | — |
| `mediumText(name)` | `MEDIUMTEXT` | — |
| `longText(name)` | `LONGTEXT` | — |

For `CHAR(n)` use the raw form:

```java
t.column("code", ColumnType.charType(3))
```

### Binary types

| Method | SQL type | `NOT NULL` |
|--------|----------|-----------|
| `blob(name)` | `BLOB` | — |

For `TINYBLOB`, `MEDIUMBLOB`, `LONGBLOB`, `CLOB`, `BINARY(n)`, and
`VARBINARY(n)` use the raw form:

```java
t.column("thumbnail", ColumnType.TINYBLOB)
 .column("payload",   ColumnType.varBinary(512))
```

### Boolean

| Method | SQL type | `NOT NULL` |
|--------|----------|-----------|
| `bool(name)` | `BOOLEAN` | ✓ |

### Date and time types

| Method | SQL type | `NOT NULL` |
|--------|----------|-----------|
| `date(name)` | `DATE` | — |
| `time(name)` | `TIME` | — |
| `dateTime(name)` | `DATETIME` | — |
| `timestamp(name)` | `TIMESTAMP` | — |
| `timestamps()` | `created_at TIMESTAMP`, `updated_at TIMESTAMP` | — |

For `TIMESTAMP(precision)` use the raw form:

```java
t.column("recorded_at", ColumnType.timestamp(6))
```

### Other types

| Method | SQL type | `NOT NULL` |
|--------|----------|-----------|
| `json(name)` | `JSON` | — |

For `SERIAL`, `BIGSERIAL`, and `NUMERIC(p, s)` use the raw form:

```java
t.column("seq",   ColumnType.SERIAL)
 .column("score", ColumnType.numeric(5, 2).notNull())
```

---

## Column modifiers

When calling `column(name, ColumnType)` directly you can chain modifiers on
the `ColumnType` before passing it:

| Modifier | Effect |
|----------|--------|
| `.notNull()` | Appends `NOT NULL` |
| `.unique()` | Appends `UNIQUE` |
| `.autoIncrement()` | Appends `AUTO_INCREMENT` |
| `.defaultValue(value)` | Appends `DEFAULT value` |

Modifiers may be chained:

```java
t.column("status", ColumnType.varChar(16).notNull().defaultValue("'pending'"))
 .column("seq",    ColumnType.INT.notNull().autoIncrement().unique())
```

---

## Primary keys

`id()` automatically marks the `id` column as the primary key. For any other
column call `primaryKey(name)` after adding the column:

```java
schema.create("sessions", t -> t
    .column("token", ColumnType.varChar(64).notNull())
    .primaryKey("token")
    .string("user_id", 36)
    .dateTime("expires_at")
);
```

---

## Idempotent table creation

Call `ifNotExists()` on the blueprint to emit `CREATE TABLE IF NOT EXISTS`:

```java
schema.create("cache", t -> t
    .ifNotExists()
    .string("key", 255)
    .text("value")
    .dateTime("expires_at")
    .primaryKey("key")
);
```

---

## Custom column types

Pass any `ColumnType` constant or factory result directly when the shorthand
methods do not cover your use case:

```java
schema.create("products", t -> t
    .id()
    .string("sku", 64)
    .column("price",    ColumnType.decimal(10, 2).notNull())
    .column("weight",   ColumnType.FLOAT)
    .column("meta",     ColumnType.JSON)
    .column("geometry", "GEOMETRY")   // raw string for DB-specific types
    .timestamps()
);
```

---

## The `jaloquent_migrations` tracking table

`MigrationRunner` creates this table automatically on the first `run()` or
`rollback()` call:

```sql
CREATE TABLE IF NOT EXISTS jaloquent_migrations (
    id    VARCHAR(255) NOT NULL,
    batch INT NOT NULL,
    PRIMARY KEY (id)
)
```

You should **not** manage this table manually. The runner always uses
parameterised queries when reading and writing it, so migration IDs are never
interpolated into SQL.

---

## Error handling

All migration errors are wrapped in `MigrationException`, which extends
`JaloquentException`. Catch it to implement custom retry or alerting logic:

```java
try {
    runner.run();
}
catch (MigrationException e) {
    log.error("Migration failed", e);
    // decide whether to halt or continue
}
```
