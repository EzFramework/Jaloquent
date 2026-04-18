---
title: Relations
nav_order: 6
description: "HasOne, HasMany, BelongsTo, and BelongsToMany with attach, detach, and sync"
---

# Relations
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Overview

Jaloquent supports four relation types modelled on Laravel's Eloquent:

| Relation | Class | FK location |
|----------|-------|-------------|
| One-to-one (owning side) | `HasOne<T>` | FK on the **related** model |
| One-to-many | `HasMany<T>` | FK on the **related** model |
| Belongs-to (inverse) | `BelongsTo<T>` | FK on **this** model |
| Many-to-many | `BelongsToMany<T, P>` | FK pair in a **pivot** table |

All relations are **lazy** — they execute a query only when `.get()`, `.count()`,
`.exists()`, etc. are called.

---

## HasOne

Use `HasOne` when this model owns one related model via a foreign key
stored on the **related** side.

### Defining

```java
// In User model:
private final ModelRepository<Phone> phoneRepo;

public HasOne<Phone> phone() {
    return hasOne(phoneRepo, "user_id");       // FK "user_id" is on Phone
}

public HasOne<Phone> phone() {
    return hasOne(phoneRepo, "user_id", "id"); // explicit local key (default is "id")
}
```

### Using

```java
Optional<Phone> phone = user.phone().get();
boolean hasPhone = user.phone().exists();

// With additional constraints
Optional<Phone> verified = user.phone()
    .where("verified", true)
    .get();
```

### Method reference

| Method | Description |
|--------|-------------|
| `where(column, value)` | Add `WHERE column = value` |
| `orderBy(column, asc)` | Sort (useful for deterministic result ordering) |
| `get()` | `Optional<T>` — returns the first matching related record |
| `exists()` | `boolean` — true if at least one related record exists |

---

## HasMany

Use `HasMany` when this model owns many related models.

### Defining

```java
// In User model:
public HasMany<Post> posts() {
    return hasMany(postRepo, "user_id");
}
```

### Using

```java
List<Post> allPosts = user.posts().get();
long count = user.posts().count();

List<Post> recent = user.posts()
    .where("published", true)
    .orderBy("created_at", false)
    .limit(5)
    .get();
```

### Method reference

| Method | Description |
|--------|-------------|
| `where(column, value)` | Add `WHERE column = value` |
| `orderBy(column, asc)` | Sort results |
| `limit(n)` | Cap result count |
| `get()` | `List<T>` of all matching related records |
| `count()` | Number of matching related records |

---

## BelongsTo

`BelongsTo` is the **inverse** side. The foreign key lives on *this* model
and points to the primary key of the related model.

### Defining

```java
// In Phone model:
public BelongsTo<User> owner() {
    return belongsTo(userRepo, "user_id"); // "user_id" is an attribute on Phone
}
```

### Using

```java
Optional<User> user = phone.owner().get();
boolean linked = phone.owner().exists();
```

### Method reference

| Method | Description |
|--------|-------------|
| `get()` | `Optional<T>` — loads the related model using the FK value |
| `exists()` | `boolean` — true if FK is set and the related record exists |

---

## BelongsToMany

Use `BelongsToMany` for many-to-many relationships via a pivot table.
Jaloquent provides `PivotModel` as a ready-made pivot class — or you can
supply your own `Model` subclass with extra pivot columns.

### Defining

```java
// In User model:
private final ModelRepository<Role> roleRepo;
private final ModelRepository<PivotModel> pivotRepo;

public BelongsToMany<Role, PivotModel> roles() {
    return belongsToMany(
        roleRepo,
        pivotRepo,
        PivotModel.FACTORY,  // factory constant on PivotModel
        "user_id",           // FK referencing this model in the pivot table
        "role_id"            // FK referencing the related model in the pivot table
    );
}
```

### Reading

```java
List<Role> roles = user.roles().get();
long count = user.roles().count();

List<Role> adminRoles = user.roles()
    .where("type", "admin")
    .orderBy("name", true)
    .get();
```

### Attaching and detaching

```java
// Create a pivot entry  user_id=X, role_id=Y
user.roles().attach("role-admin");

// Pivot entry with extra columns
user.roles().attach("role-moderator", Map.of(
    "granted_by", "super-user",
    "expires_at", "2026-12-31"
));

// Remove a single pivot entry
user.roles().detach("role-admin");

// Remove ALL pivot entries for this user (bulk DELETE)
user.roles().detachAll();
```

### `sync` — declarative reconciliation

`sync` ensures the pivot table matches an exact desired list:
it attaches missing IDs and detaches removed ones in the fewest possible operations.

```java
// After: user has exactly role-a and role-b; role-c is detached if present
user.roles().sync(List.of("role-a", "role-b"));
```

{: .note }
> `sync` performs a **single bulk `DELETE`** for all removed pivot rows, then
> individual `attach` calls for new ones. Use it instead of manual `detach`/`attach`
> loops to minimize round-trips.

### Method reference

| Method | Description |
|--------|-------------|
| `where(column, value)` | Add constraint on the related model query |
| `orderBy(column, asc)` | Sort related models |
| `get()` | `List<T>` — resolve pivot rows, then load related models |
| `attach(relatedId)` | Create pivot entry |
| `attach(relatedId, extraAttrs)` | Create pivot entry with extra columns |
| `detach(relatedId)` | Delete pivot entry |
| `detachAll()` | Bulk delete all pivot rows for this parent |
| `sync(List<String>)` | Reconcile: attach missing, detach removed |
| `count()` | Number of related records through pivot |

---

## PivotModel

`PivotModel` is a concrete, general-purpose pivot class. Use it unless
your pivot table has columns you need to read after attaching.

```java
// Pre-built factory constant — no boilerplate
ModelRepository<PivotModel> pivotRepo = new ModelRepository<>(
    myStore, "user_roles", PivotModel.FACTORY
);
```

---

## N+1 avoidance

Jaloquent does not automatically eager-load relations. For large collections,
avoid N+1 queries by batching outside the relation API:

```java
// N+1 — BAD: one query per user
List<User> users = userRepo.query(someQuery);
for (User u : users) {
    List<Role> roles = u.roles().get(); // extra query per user
}

// Better: load roles for all user IDs in one query
Set<String> userIds = users.stream().map(User::getId).collect(toSet());
List<PivotModel> pivots = pivotRepo.query(
    Model.queryBuilder().where("user_id", "IN", userIds).build()
);
```

{: .note }
> A first-class eager-loading API is planned for a future release.
> For now, prefer batched queries when processing collections.
