---
title: HasOne
parent: Relations
nav_order: 1
description: "One-to-one relationship where the foreign key lives on the related model"
---

# HasOne
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Overview

{: .note }
> Part of the [Relations](../relations) family: `HasOne`, [`HasMany`](has-many), [`BelongsTo`](belongs-to), [`BelongsToMany`](belongs-to-many).

`HasOne<T>` represents a one-to-one relationship where the foreign key lives on
the **related** model. It is the *owning* side: this model's primary key is stored
as a column on the related table.

```text
users            phones
──────────────   ───────────────────
id  (PK)    ←──  user_id  (FK)
name             number
```

---

## Defining

Override a method in your model and call the protected `hasOne()` helper:

```java
public class User extends Model {

    private final ModelRepository<Phone> phoneRepo;

    public User(String id) { super(id); }

    /** One-to-one: "user_id" column lives on the Phone table. */
    public HasOne<Phone> phone() {
        return hasOne(phoneRepo, "user_id");
    }
}
```

### Custom local key

By default `hasOne` uses this model's `id` as the anchor value. Pass a third
argument to use a different attribute:

```java
public HasOne<Phone> phone() {
    return hasOne(phoneRepo, "user_id", "uuid"); // resolves phone WHERE user_id = this.uuid
}
```

---

## Using

```java
// Load the related phone
Optional<Phone> phone = user.phone().get();

// Check existence without loading the full model
boolean hasPhone = user.phone().exists();
```

### Constraints

Additional `WHERE` and `ORDER BY` clauses can be chained before calling `.get()`:

```java
Optional<Phone> verified = user.phone()
    .where("verified", true)
    .orderBy("created_at", false)
    .get();
```

{: .note }
> `.get()` internally applies `LIMIT 1`. If you add `orderBy`, the limit is
> applied to the sorted result set, giving deterministic row selection.

---

## Method reference

| Method | Return type | Description |
|--------|-------------|-------------|
| `where(column, value)` | `HasOne<T>` | Add `WHERE column = value` constraint |
| `orderBy(column, asc)` | `HasOne<T>` | Sort results before applying the limit |
| `get()` | `Optional<T>` | Return the first matching related record |
| `exists()` | `boolean` | `true` if at least one related record exists |
