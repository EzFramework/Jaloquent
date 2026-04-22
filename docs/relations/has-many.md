---
title: HasMany
parent: Relations
nav_order: 2
description: "One-to-many relationship where the foreign key lives on the related model"
---

# HasMany
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Overview

`HasMany<T>` represents a one-to-many relationship where the foreign key lives on
the **related** model. This model is the *parent*; the related model stores a
reference back to it.

```text
users            posts
──────────────   ───────────────────
id  (PK)    ←──  user_id  (FK)
name             title
                 published
```

---

## Defining

Override a method in your model and call the protected `hasMany()` helper:

```java
public class User extends Model {

    private final ModelRepository<Post> postRepo;

    public User(String id) { super(id); }

    /** One-to-many: "user_id" column lives on the Post table. */
    public HasMany<Post> posts() {
        return hasMany(postRepo, "user_id");
    }
}
```

### Custom local key

By default `hasMany` uses this model's `id` as the anchor value. Pass a third
argument to use a different attribute:

```java
public HasMany<Post> posts() {
    return hasMany(postRepo, "author_ref", "uuid"); // resolves posts WHERE author_ref = this.uuid
}
```

---

## Using

```java
// Load all related posts
List<Post> allPosts = user.posts().get();

// Count without loading models
long count = user.posts().count();
```

### Constraints

`WHERE`, `ORDER BY`, and `LIMIT` can be chained before calling `.get()` or
`.count()`:

```java
List<Post> recent = user.posts()
    .where("published", true)
    .orderBy("created_at", false)
    .limit(5)
    .get();
```

---

## Method reference

| Method | Return type | Description |
|--------|-------------|-------------|
| `where(column, value)` | `HasMany<T>` | Add `WHERE column = value` constraint |
| `orderBy(column, asc)` | `HasMany<T>` | Sort results |
| `limit(n)` | `HasMany<T>` | Cap the maximum number of results |
| `get()` | `List<T>` | Return all matching related records |
| `count()` | `long` | Number of matching related records |
