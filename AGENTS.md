# Jaloquent – Agent Guidelines

Eloquent-style active-record ORM for Java, built on top of
`com.github.EzFramework/java-query-builder`. Distributed via JitPack.

---

## Architecture

```
src/main/java/.../jaloquent/
  config/       JaloquentConfig – logger & Micrometer registry wiring
  exception/    JaloquentException hierarchy (Storage, Validation, ModelNotFound)
  model/        BaseModel, Model, Fillable, ModelRepository, TableRegistry, PivotModel, ModelFactory
  relation/     HasOne, HasMany, BelongsTo, BelongsToMany, Relation
  repository/   Repository (interface), AbstractRepository
  store/        DataStore (interface), store/sql/JdbcStore (interface)
```

`Model` extends `BaseModel`, holds a flat `Map<String,Object> attributes`, and
delegates mass-assignment protection to `Fillable`.  
`ModelRepository<T>` routes every persistence call to either the SQL path
(`JdbcStore`) or the flat-map `DataStore` path, depending on what the registered
store implements.

---

## Build & Test Commands

```bash
# Compile + run all tests
mvn test

# Check code style (must pass before commit)
mvn checkstyle:check -f checkstyle-pom.xml

# Full build with style check + tests
mvn verify -f checkstyle-pom.xml

# Coverage report (written to target/site/jacoco/)
mvn test jacoco:report
```

All three must pass (zero violations, zero test failures) before any change is complete.

---

## Code Style (Checkstyle 9.3)

The canonical rules are in `checkstyle.xml`. Violations block the build.
The most error-prone rules are listed below with corrected examples.

### Imports

- No star imports (`AvoidStarImport`).
- No unused imports (`UnusedImports`).
- Groups must be **alphabetically ordered** within each group, with a **blank line between groups** (`ImportOrder`).  
  Typical order for this project: `com.github.ezframework.*`, then `com.github.*`, then `io.*`, then `java.*`, then `org.*`.

```java
// CORRECT
import com.github.ezframework.jaloquent.exception.StorageException;
import com.github.ezframework.javaquerybuilder.query.builder.QueryBuilder;

import io.micrometer.core.instrument.Counter;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
```

### Braces & Curly Rules

- `NeedBraces` – every `if`, `else`, `for`, `while`, `do` body requires `{ }`.
- `LeftCurly(eol)` – opening brace on the same line as the statement.
- `RightCurly(alone)` – closing brace must be **on its own line**; never inline with `else`, `catch`, or `finally`.

```java
// WRONG
} catch (Exception e) {
} else {

// CORRECT
}
catch (Exception e) {
}
else {
```

### Spacing & Formatting

- `EmptyLineSeparator` – require a blank line between every method, constructor, and field declaration (including consecutive constructors).
- `FinalLocalVariable` – any local variable that is never reassigned **must** be declared `final`.
- `WhitespaceAround` / `WhitespaceAfter` – spaces required around operators and after keywords.
- `NoLineWrap` – do not let `import` or `package` statements wrap.
- Maximum line length: **120 characters**.
- Indentation: **4 spaces**, no tabs (`FileTabCharacter`).
- Empty single-line method bodies use a space: `private Foo() { }`.

### Javadoc

- Every `public` class and method requires a Javadoc comment (`JavadocType`, `JavadocMethod`).
- All `@param` tags must be present (no `allowMissingParamTags`).
- Fields need a `/** … */` comment (violation is a warning, not an error, but add them anyway).

---

## SQL & Query Builder

**Never** construct SQL strings by concatenation or `String.format`.  
All queries must go through the `java-query-builder` builders:

| Operation | Builder |
|-----------|----------|
| SELECT    | `new SelectBuilder().from(table)...build(dialect)` returns `SqlResult` |
| INSERT / UPSERT | `QueryBuilder.insertInto(table)...build(dialect)` returns `SqlResult` |
| UPDATE    | `QueryBuilder.update(table)...build(dialect)` returns `SqlResult` |
| DELETE    | `QueryBuilder.deleteFrom(table)...build(dialect)` returns `SqlResult` |
| BULK DELETE | `QueryBuilder.deleteFrom(table).whereIn("id", ids).build(dialect)` returns `SqlResult` |

`SqlResult` carries `.getSql()` (with `?` placeholders) and `.getParameters()`.
Always pass params through `JdbcStore#executeUpdate(sql, params)` or `JdbcStore#query(sql, params)` — never interpolate them into the SQL string.

---

## Mass-Assignment & Fillable

- `Fillable` is the single source of truth for mass-assignment rules.
- `isFillable(key)` – permissive mode: allows any key that is not guarded (used by `fill()`).
- `isExplicitlyFillable(key)` – strict mode: only keys explicitly declared via `setFillable(...)` (used by `update()`).
- `id` is block-listed in both modes regardless of declared rules.
- When no fillable set is declared, `update()` is a safe no-op (does not apply any keys).

---

## Testing Conventions

```
src/test/java/.../jaloquent/
  unit/     Pure unit tests – no I/O, no TableRegistry side-effects
  feature/  Integration-style tests – may use RecordingJdbcStore or in-memory DataStore
```

- Each feature test registers its own `UUID.randomUUID().toString()` prefix to avoid
  polluting the JVM-static `TableRegistry` across tests.
- Use `RecordingJdbcStore` (defined in `ModelRepositoryJdbcFeatureTest`) when you need to
  inspect the exact SQL and parameter list that `ModelRepository` sends to the store.
- Verify both the rendered SQL **and** the params list in SQL-path tests; never assert
  on string-concatenated SQL — assert that user-supplied values appear in `params`, not in `sql`.
- Test class naming: `*FeatureTest` for feature tests, `*Test` for unit tests.

---

## Security

- All user-supplied values must travel as `?` bind parameters — verified by checking
  `RecordingJdbcStore#queryParams` / `updateParams` in tests.
- Never accept raw SQL fragments from model attributes or external input.
- `Fillable` guards must be enforced before any write in mass-assignment paths.
- Log exceptions with SLF4J; never expose raw stack traces or SQL strings to callers.

---

## Common Pitfalls

| Mistake | Fix |
|---------|-----|
| `} catch (…)` on same line as `}` | Split: `}\ncatch (…) {` |
| `} else {` on same line as `}` | Split: `}\nelse {` |
| Missing blank line between constructors | Add blank line (`EmptyLineSeparator`) |
| Non-final local variable | Add `final` if never reassigned |
| Import groups not separated by blank line | Add blank line between each group |
| `if (x) doSomething();` without braces | Wrap body in `{ }` |
| Javadoc missing `@param` for a parameter | Add the missing tag |
| SQL built with `String.format` | Replace with appropriate builder |
| `fill()` vs `update()` confusion | `fill()` is permissive; `update()` is strict (requires declared fillable) |
