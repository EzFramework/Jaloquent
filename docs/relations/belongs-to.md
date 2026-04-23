---
title: BelongsTo
parent: Relations
nav_order: 3
description: "Inverse side of a one-to-one or one-to-many relationship — FK lives on this model"
---

# BelongsTo
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Overview

{: .note }
> Part of the [Relations](../relations) family: [`HasOne`](has-one), [`HasMany`](has-many), `BelongsTo`, [`BelongsToMany`](belongs-to-many).

`BelongsTo<T>` is the **inverse** side of a [`HasOne`](has-one) or [`HasMany`](has-many) relationship.
The foreign key lives on *this* model and references the primary key of the
related (parent) model.

```text
users            phones
──────────────   ───────────────────
id  (PK)    ←──  user_id  (FK)   ← FK lives here
name             number
```

`Phone` *belongs to* `User`: `Phone.user_id` stores the ID of the owning user.

---

## Defining

Override a method in your model and call the protected `belongsTo()` helper:

```java
public class Phone extends Model {

    private final ModelRepository<User> userRepo;

    public Phone(String id) { super(id); }

    /** Inverse of User.phone() — the FK "user_id" lives on this model. */
    public BelongsTo<User> owner() {
        return belongsTo(userRepo, "user_id");
    }
}
```

`belongsTo` reads the current value of `"user_id"` from this model's attributes
at call time and uses it for the lookup. If the attribute is `null` (not yet set),
`.get()` immediately returns `Optional.empty()` without issuing a query.

---

## Using

```java
// Load the owning user
Optional<User> user = phone.owner().get();

// Check existence without loading the full model
boolean linked = phone.owner().exists();
```

---

## Method reference

`BelongsTo` does **not** support the `where` / `orderBy` / `limit` chain —
the lookup is always a direct primary-key lookup via `repository.find(id)`.

| Method | Return type | Description |
|--------|-------------|-------------|
| `get()` | `Optional<T>` | Load the related model by the stored FK value |
| `exists()` | `boolean` | `true` if the FK is set and the related record exists |

---

## Null FK behaviour

If the FK attribute is `null` at the time `belongsTo()` is evaluated,
`get()` returns `Optional.empty()` and `exists()` returns `false` — no query
is issued.

```java
Phone unlinked = new Phone("p-1");
// user_id not set
assertFalse(unlinked.owner().exists()); // no DB round-trip
```
