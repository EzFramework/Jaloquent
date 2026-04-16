# JavaQueryBuilder 1.0.3 — Migration & Outstanding Work

## Dependency coordinate change

`java-query-builder` (1.0.2) has been superseded by the renamed artifact `JavaQueryBuilder` (1.0.3).

```xml
<!-- pom.xml — already updated -->
<dependency>
    <groupId>com.github.EzFramework</groupId>
    <artifactId>JavaQueryBuilder</artifactId>
    <version>1.0.3</version>
</dependency>
```

---

## New API surface in 1.0.3

### `DeleteBuilder` — resolved limitation

`whereIn` (and several other conditions) are now available:

| New method | Signature |
|---|---|
| `whereIn` | `whereIn(String column, List<?> values)` |
| `whereNotIn` | `whereNotIn(String column, List<?> values)` |
| `whereBetween` | `whereBetween(String column, Object a, Object b)` |
| `whereGreaterThan` | `whereGreaterThan(String column, Object value)` |
| `whereGreaterThanOrEquals` | `whereGreaterThanOrEquals(String column, Object value)` |
| `whereLessThanOrEquals` | `whereLessThanOrEquals(String column, Object value)` |
| `whereNotEquals` | `whereNotEquals(String column, Object value)` |

### `SelectBuilder` — new dedicated SELECT builder

Replaces the `QueryBuilder → buildSql(table, dialect)` pattern for plain SELECT queries.

```java
new SelectBuilder()
    .from("users")
    .select("id", "name")
    .whereEquals("active", true)
    .orderBy("name", true)
    .limit(10)
    .build(dialect)      // returns SqlResult
```

### `CreateBuilder` — new DDL builder

```java
new CreateBuilder()
    .table("users")
    .column("id", "VARCHAR(36)")
    .column("name", "VARCHAR(255)")
    .primaryKey("id")
    .ifNotExists()
    .build(dialect)
```

### `QueryBuilder` — static factory methods

| Method | Returns |
|---|---|
| `QueryBuilder.insert()` / `QueryBuilder.insertInto(table)` | `InsertBuilder` |
| `QueryBuilder.update()` / `QueryBuilder.update(table)` | `UpdateBuilder` |
| `QueryBuilder.delete()` / `QueryBuilder.deleteFrom(table)` | `DeleteBuilder` |
| `QueryBuilder.createTable()` / `QueryBuilder.createTable(table)` | `CreateBuilder` |

---

## Required code changes in `ModelRepository`

### 1. `deleteAll()` — replace per-id loop with `whereIn`

**Current** (workaround for missing `whereIn`):
```java
for (final String id : ids) {
    final SqlResult r = new DeleteBuilder().from(table).whereEquals("id", id).build(dialect);
    jdbc.executeUpdate(r.getSql(), r.getParameters());
}
```

**Target** (single statement):
```java
final SqlResult r = QueryBuilder.deleteFrom(meta.tableName())
    .whereIn("id", new ArrayList<>(ids))
    .build(dialect);
jdbc.executeUpdate(r.getSql(), r.getParameters());
```

**File:** `src/main/java/.../model/ModelRepository.java`, method `deleteAll()`  
**Effort:** Small — replace loop body and update Javadoc.

---

### 2. `find()` / `exists()` — switch to `SelectBuilder`

**Current:**
```java
new QueryBuilder().whereEquals("id", id).limit(1).buildSql(meta.tableName(), dialect)
```

**Target:**
```java
QueryBuilder.select().from(meta.tableName()).whereEquals("id", id).limit(1).build(dialect)
```

Uses the new explicit `SelectBuilder` rather than the legacy `QueryBuilder → buildSql` path, which makes the intent clearer and is consistent with the rest of the DML using static factories.

**File:** `src/main/java/.../model/ModelRepository.java`, methods `find()` and `exists()`  
**Effort:** Small — method chain refactor only.

---

### 3. `save()` — switch to `QueryBuilder.insertInto()` static factory

Replace `new InsertBuilder().into(table)` with `QueryBuilder.insertInto(table)` to be consistent with the unified static factory pattern.

**File:** `src/main/java/.../model/ModelRepository.java`, method `save()`  
**Effort:** Trivial — constructor replacement only, logic unchanged.

---

### 4. `deleteWhere()` — switch to `QueryBuilder.deleteFrom()` static factory

Replace `new DeleteBuilder().from(table)` with `QueryBuilder.deleteFrom(table)`.

**File:** `src/main/java/.../model/ModelRepository.java`, method `deleteWhere()`  
**Effort:** Trivial.

---

### 5. `delete()` — switch to `QueryBuilder.deleteFrom()` static factory

Same as above for the single-id delete.

**File:** `src/main/java/.../model/ModelRepository.java`, method `delete()`  
**Effort:** Trivial.

---

## Required test additions

### 6. Dialect matrix tests — MySQL and SQLite SQL rendering

For each query operation in `ModelRepository`, verify that the rendered SQL is correct for both `SqlDialect.MYSQL` (back-tick identifier quoting) and `SqlDialect.SQLITE` (double-quote identifier quoting).

Operations to cover (minimum one test per dialect × operation cell):

| Operation | SQL generated | MySQL quoting | SQLite quoting |
|---|---|---|---|
| `save()` | `INSERT INTO t (id, …) VALUES (?, …) ON DUPLICATE KEY UPDATE …` | `` `t` `` | — (not valid SQLite; document) |
| `find()` | `SELECT * FROM t WHERE id = ? LIMIT 1` | `` `t`, `id` `` | `"t"`, `"id"` |
| `delete()` | `DELETE FROM t WHERE id = ?` | unquoted (DeleteBuilder) | unquoted (DeleteBuilder) |
| `deleteAll()` | `DELETE FROM t WHERE id IN (?, ?, …)` | unquoted | unquoted |
| `deleteWhere()` | `DELETE FROM t WHERE col = ?` | unquoted | unquoted |
| `exists()` | `SELECT 1 FROM t WHERE id = ? LIMIT 1` | `` `t`, `id` `` | `"t"`, `"id"` |
| `query()` with `whereEquals` | `SELECT * FROM t WHERE col = ?` | `` `t`, `col` `` | `"t"`, `"col"` |

**Note:** `InsertBuilder` and `DeleteBuilder` do not use `quoteIdentifier` (they build raw SQL strings directly). Only `SelectBuilder` / `QueryBuilder` honour the dialect quoting. Tests should assert the exact quoting characters.

**File:** new `src/test/java/.../feature/DialectMatrixFeatureTest.java`  
**Effort:** Medium — parameterized `@MethodSource` or `@EnumSource` over `SqlDialect.MYSQL` / `SqlDialect.SQLITE`.

---

### 7. `deleteAll()` with `whereIn` — verify single-statement bulk delete

Once change #1 is applied, add a test asserting:
- Exactly **one** `executeUpdate` call is issued regardless of list size.
- The SQL contains `IN (?, ?, …)` with the correct number of placeholders.
- All ids appear in the params list, not in the SQL string.

**File:** `src/test/java/.../feature/ModelRepositoryJdbcFeatureTest.java`  
**Effort:** Small — two or three new test methods.

---

### 8. `CreateBuilder` schema round-trip test

Verify that `TableRegistry` metadata can be used to generate a correctly-formed `CREATE TABLE` statement via `CreateBuilder` for both MySQL and SQLite dialects.

**File:** new test method in existing `TableRegistryFeatureTest.java` or a dedicated `CreateBuilderFeatureTest.java`  
**Effort:** Small.

---

## AGENTS.md updates required

- Remove: _"Known limitation: `DeleteBuilder` has no `whereIn`"_ note.
- Update the SQL & Query Builder table to include `SELECT → SelectBuilder`, `CREATE TABLE → CreateBuilder`.
- Update static factory factory pattern note.
- Remove the `deleteAll` workaround from the Common Pitfalls section.

**File:** `AGENTS.md`

---

## Summary checklist

- [ ] `pom.xml` — artifactId updated to `JavaQueryBuilder` (done)
- [ ] `ModelRepository#deleteAll` — replace loop with `whereIn` (change #1)
- [ ] `ModelRepository#find` — switch to `SelectBuilder` (change #2)
- [ ] `ModelRepository#exists` — switch to `SelectBuilder` (change #2)
- [ ] `ModelRepository#save` — `QueryBuilder.insertInto()` factory (change #3)
- [ ] `ModelRepository#deleteWhere` — `QueryBuilder.deleteFrom()` factory (change #4)
- [ ] `ModelRepository#delete` — `QueryBuilder.deleteFrom()` factory (change #5)
- [ ] `DialectMatrixFeatureTest` — new file (test #6)
- [ ] `ModelRepositoryJdbcFeatureTest` — `deleteAll` whereIn assertions (test #7)
- [ ] `CreateBuilder` schema test (test #8)
- [ ] `AGENTS.md` — remove limitation note and update tables
