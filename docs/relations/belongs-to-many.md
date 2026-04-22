---
title: BelongsToMany
parent: Relations
nav_order: 4
description: "Many-to-many relationship via a pivot table — attach, detach, sync"
---

# BelongsToMany
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Overview

`BelongsToMany<T, P>` represents a many-to-many relationship mediated by a pivot
table. Both the parent model's ID and the related model's ID are stored as foreign
keys on the pivot row.

```text
users            user_roles (pivot)     roles
──────────────   ─────────────────────  ──────────────
id  (PK)    ←──  user_id  (FK)          id  (PK)
name             role_id  (FK)     ───► name
                 granted_by             type
```

Jaloquent provides `PivotModel` as a ready-made pivot class. Use it whenever
the pivot table acts as a pure join table. Supply your own `Model` subclass if
you need to read extra pivot columns after attaching.

---

## Defining

Override a method in your model and call the protected `belongsToMany()` helper:

```java
public class User extends Model {

    private final ModelRepository<Role>       roleRepo;
    private final ModelRepository<PivotModel> pivotRepo;

    public User(String id) { super(id); }

    public BelongsToMany<Role, PivotModel> roles() {
        return belongsToMany(
            roleRepo,           // repository for the related model
            pivotRepo,          // repository for the pivot table
            PivotModel.FACTORY, // factory for creating new pivot instances
            "user_id",          // pivot column storing this model's ID
            "role_id"           // pivot column storing the related model's ID
        );
    }
}
```

### Setting up the pivot repository

```java
ModelRepository<PivotModel> pivotRepo = new ModelRepository<>(
    myStore,
    "user_roles",       // pivot table name
    PivotModel.FACTORY  // pre-built factory constant — no boilerplate
);
```

---

## Reading

```java
// Load all related models through the pivot table
List<Role> roles = user.roles().get();

// Count related models
long count = user.roles().count();
```

### Constraints on the related model

`WHERE` and `ORDER BY` are applied to the **related** model query (after pivot
resolution), not to the pivot table itself:

```java
List<Role> adminRoles = user.roles()
    .where("type", "admin")
    .orderBy("name", true)
    .get();
```

---

## Attaching

Create a pivot entry linking this model to a related model:

```java
// Simple attach — creates a pivot row with user_id and role_id
user.roles().attach("role-admin");
```

### Attach with extra pivot columns

```java
user.roles().attach("role-moderator", Map.of(
    "granted_by", "super-user-id",
    "expires_at", "2026-12-31"
));
```

Extra attributes are stored on the pivot row alongside the two FK columns.

---

## Detaching

```java
// Remove a specific pivot entry
user.roles().detach("role-admin");

// Remove ALL pivot entries for this parent (bulk DELETE)
user.roles().detachAll();
```

`detachAll()` issues a **single `DELETE WHERE user_id = ?`** rather than
iterating per row, minimising round-trips.

---

## `sync` — declarative reconciliation

`sync` brings the pivot table to an exact desired state: it attaches IDs that
are missing and detaches IDs that are no longer wanted.

```java
// After this call, the user has exactly role-a and role-b.
// role-c, role-d, etc. are detached if they were previously attached.
user.roles().sync(List.of("role-a", "role-b"));
```

Removals are executed as a **single bulk `DELETE`** for all removed pivot rows,
then individual `attach` calls are made for new ones.

{: .note }
> Prefer `sync` over manual `detach`/`attach` loops when the desired set of
> related IDs is known up front — it minimises database round-trips.

---

## Method reference

| Method | Return type | Description |
|--------|-------------|-------------|
| `where(column, value)` | `BelongsToMany<T,P>` | Add constraint on the related model query |
| `orderBy(column, asc)` | `BelongsToMany<T,P>` | Sort related models |
| `get()` | `List<T>` | Resolve pivot rows, then load related models |
| `count()` | `long` | Number of related records through the pivot table |
| `attach(relatedId)` | `void` | Create a pivot entry |
| `attach(relatedId, extraAttrs)` | `void` | Create a pivot entry with extra columns |
| `detach(relatedId)` | `void` | Delete a specific pivot entry |
| `detachAll()` | `void` | Bulk-delete all pivot entries for this parent |
| `sync(List<String>)` | `void` | Reconcile: attach missing, detach removed |

---

## Pivot IDs

Jaloquent generates the pivot entry's own ID as `parentId + "_" + relatedId`
(e.g. `"user-1_role-admin"`). This means each `(parent, related)` pair is
unique by construction — attaching the same related ID twice produces a
duplicate-key error at the store level.

---

## Custom pivot model

Extend `Model` when you need to read extra columns from the pivot row:

```java
public class UserRole extends Model {

    public static final ModelFactory<UserRole> FACTORY =
        (id, data) -> { UserRole r = new UserRole(id); r.fromMap(data); return r; };

    public UserRole(String id) { super(id); }

    public String getGrantedBy() { return getAs("granted_by", String.class, null); }
}
```

Then use `UserRole` as the pivot type:

```java
public BelongsToMany<Role, UserRole> roles() {
    return belongsToMany(roleRepo, userRoleRepo, UserRole.FACTORY, "user_id", "role_id");
}
```
