---
title: Queries
nav_order: 5
description: "Filtering, ordering, and limiting results with the EzFramework query builder"
---

# Queries
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Overview

Jaloquent uses the [EzFramework Java Query Builder](https://github.com/EzFramework/JavaQueryBuilder)
for all SQL generation. You never write raw SQL strings — every query goes through a
builder that renders parameterized SQL with `?` placeholders and a separate parameter list.

Access the builder via the static helper:

```java
import com.github.ezframework.jaloquent.model.Model;

Model.queryBuilder() // returns a fresh QueryBuilder instance
```

---

## Building a query

### Filtering with `where`

```java
// WHERE coins > 1000
Model.queryBuilder().where("coins", ">", 1000)

// WHERE status = 'active'
Model.queryBuilder().where("status", "=", "active")

// WHERE name LIKE 'Ali%'
Model.queryBuilder().where("name", "LIKE", "Ali%")
```

### Multiple conditions

Chained `where` calls are combined with `AND`:

```java
Model.queryBuilder()
    .where("status", "=", "active")
    .where("coins", ">", 500)
    .where("level", ">=", 10)
```

### Ordering with `orderBy`

```java
// ascending
Model.queryBuilder().orderBy("name", true)

// descending
Model.queryBuilder().orderBy("coins", false)

// multiple sorts
Model.queryBuilder()
    .orderBy("level", false)   // level DESC
    .orderBy("name", true)     // name ASC
```

### Limiting results

```java
Model.queryBuilder().limit(20)
```

### Combining clauses

```java
List<Player> topPlayers = repo.query(
    Model.queryBuilder()
        .where("status", "=", "active")
        .where("coins", ">", 1000)
        .orderBy("coins", false)
        .limit(10)
        .build()
);
```

---

## Passing a query to the repository

Build the query and pass it to `repo.query(Query)`:

```java
import com.github.ezframework.javaquerybuilder.query.Query;

Query q = Model.queryBuilder()
    .where("name", "=", "Alice")
    .build();

List<Player> results = repo.query(q);
```

---

## Security: values are always bind parameters

User-supplied values passed to `where()` **always** appear in the bind parameter list
of the rendered `SqlResult`, never interpolated into the SQL string. This is
structurally enforced by the query builder — SQL injection is not possible through
this API.

```java
// Safe — even if userInput contains SQL metacharacters
String userInput = "'; DROP TABLE players; --";

Query q = Model.queryBuilder()
    .where("name", "=", userInput)
    .build();

// SqlResult.getSql()        → "SELECT ... WHERE name = ?"
// SqlResult.getParameters() → ["'; DROP TABLE players; --"]
```

{: .warning }
> Never deserialize raw SQL fragments from external input and pass them as column names
> or operators to `where()`. Column names and operators are not parameterized.
> Always use static, known-safe strings for those arguments.

---

## Deleting with a query

`deleteWhere(Query)` reuses the same builder to generate a `DELETE` statement:

```java
repo.deleteWhere(
    Model.queryBuilder()
        .where("status", "=", "banned")
        .where("coins", "<", 0)
        .build()
);
```

---

## Using SelectBuilder directly

For more complex queries (joins, subqueries), use `SelectBuilder` from the
Java Query Builder directly:

```java
import com.github.ezframework.javaquerybuilder.query.builder.SelectBuilder;
import com.github.ezframework.javaquerybuilder.query.result.SqlResult;
import com.github.ezframework.javaquerybuilder.query.dialect.SqlDialect;

SqlResult result = new SelectBuilder()
    .from("player_data")
    .select("id", "name", "coins")
    .where("coins", ">", 500)
    .orderBy("coins", false)
    .build(SqlDialect.MYSQL);

String sql    = result.getSql();
List<?> params = result.getParameters();
```

Then execute via your `JdbcStore`:

```java
List<Map<String, Object>> rows = jdbcStore.query(sql, params);
```

---

## Dialect matrix

The same query builder produces correct SQL across all supported dialects:

| Dialect | `SqlDialect` constant | Notes |
|---------|-----------------------|-------|
| Standard SQL | `STANDARD` | Default; used when no dialect is specified |
| MySQL / MariaDB | `MYSQL` | Uses `ON DUPLICATE KEY UPDATE` for upserts |
| PostgreSQL | `POSTGRESQL` | Uses `ON CONFLICT DO UPDATE` for upserts |
| H2 | `H2` | Used in tests with an in-memory database |

Pass the dialect to the `ModelRepository` constructor; query builder calls inside
`repo.query()` and `repo.deleteWhere()` automatically use the configured dialect.
