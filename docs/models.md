---
title: Models
nav_order: 3
description: "Defining model classes, reading and writing attributes, and mass-assignment protection"
---

# Models
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Overview

Every domain object in Jaloquent extends `Model`, which extends `BaseModel`.
A model holds its state in an internal `HashMap<String, Object>` called _attributes_
and delegates all persistence to a `ModelRepository`.

```
BaseModel  ←  id, getStoragePath(), toMap(), fromMap()
   └── Model  ←  attributes map, get/set, fill/update, relations, save/delete/find
         └── YourModel  ←  typed accessors, relation methods
```

---

## Defining a model

```java
import com.github.ezframework.jaloquent.model.Model;

public class Player extends Model {

    public Player(String id) {
        super(id);
    }

    // Typed accessor — getAs handles Integer ↔ Long ↔ String coercion
    public String getName() {
        return getAs("name", String.class, "");
    }

    public void setName(String name) {
        set("name", name);
    }

    public int getCoins() {
        return getAs("coins", Integer.class, 0);
    }

    public void setCoins(int coins) {
        set("coins", coins);
    }
}
```

{: .note }
> The `id` attribute is stored separately from the attribute map. Reading `get("id")`
> and writing `set("id", value)` both route through `getId()` / `setId()` automatically.

---

## Reading and writing attributes

### `set(key, value)` and `get(key)`

```java
player.set("name", "Alice");
Object raw = player.get("name"); // returns Object
```

### `getAs(key, Class<T>)` — type-coercing read

`getAs` performs automatic coercion between compatible types so models stay
robust against the type differences between JDBC drivers, JSON deserializers,
and in-memory stores.

```java
// Returns Integer even if the store returned a Long
int coins = player.getAs("coins", Integer.class, 0);

// Returns String even if the stored value is an Integer
String level = player.getAs("level", String.class, "1");
```

Supported coercions:

| Stored type | Requested type | Result |
|-------------|----------------|--------|
| `Long` | `Integer` | `longValue.intValue()` |
| `Integer` | `Long` | `intValue.longValue()` |
| `Integer` / `Long` | `String` | `String.valueOf(v)` |
| `String` | same | direct cast |
| any | same | direct cast |

### `attributes()`

Returns an **unmodifiable** snapshot of the attribute map:

```java
Map<String, Object> attrs = player.attributes();
```

### `toMap()` and `fromMap(Map)`

Used internally for persistence but also useful for serialization:

```java
// Serialize (e.g., to send over HTTP)
Map<String, Object> data = player.toMap();

// Deserialize (e.g., after loading from a store)
player.fromMap(data);
```

---

## Mass-assignment

Jaloquent provides two modes of mass-assignment to protect against
over-posting vulnerabilities.

### `fill(Map)` — permissive mode

Applies every key from the map **unless** the key is guarded. If no
`fillable` set is declared, all non-guarded keys are accepted.

```java
player.setGuarded("isAdmin"); // block this key

// "name" and "coins" are applied; "isAdmin" is silently dropped
player.fill(Map.of("name", "Bob", "coins", 100, "isAdmin", true));
```

### `update(Map)` — strict mode

Applies **only** keys that are explicitly declared via `setFillable`.
If no fillable set has been declared, `update()` is a safe no-op.

```java
player.setFillable("name", "coins"); // explicit allowlist

// Only "name" and "coins" are applied; everything else is dropped
player.update(Map.of("name", "Bob", "coins", 100, "role", "admin"));
```

### `id` is always block-listed

Both `fill()` and `update()` will never apply an `"id"` key, regardless
of fillable or guarded declarations.

### Declaring fillable and guarded keys

```java
// Only "name" and "coins" may be mass-assigned via update()
setFillable("name", "coins");

// Additional keys blocked from fill() even if fillable is empty
setGuarded("isAdmin", "role");
```

| Method | Returns |
|--------|---------|
| `getFillable()` | Unmodifiable `Set<String>` |
| `getGuarded()` | Unmodifiable `Set<String>` |

{: .important }
> Prefer `update()` in web or API handlers where you forward user-supplied maps
> directly to a model — it provides strict field-level control.

---

## Persistence shortcuts

These convenience methods delegate directly to the given repository.

### `save(repo)`

Upserts the model and returns `this` for chaining:

```java
player.setName("Alice").save(repo); // chained
// or
player.save(repo);
```

### `delete(repo)`

Deletes the model by its current `id`:

```java
player.delete(repo);
```

### `Model.find(repo, id)` — static helper

```java
Player player = Model.find(repo, "some-uuid"); // null if not found
```

---

## BaseModel contract

`Model` inherits from `BaseModel`, which defines:

| Method | Description |
|--------|-------------|
| `getId()` | Return the primary key string |
| `setId(String)` | Update the primary key |
| `getStoragePath(String prefix)` | Returns `prefix/id` (or bare `id` when prefix is blank) |
| `toMap()` | Abstract — serialize to flat map |
| `fromMap(Map)` | Abstract — populate from flat map |

The storage path is used by flat-map `DataStore` implementations to locate records.
When a `TableRegistry` entry exists for the prefix, the SQL path is used instead.
