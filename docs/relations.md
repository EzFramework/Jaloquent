---
title: Relations
nav_order: 6
has_children: true
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
| One-to-one (owning side) | [`HasOne<T>`](relations/has-one) | FK on the **related** model |
| One-to-many | [`HasMany<T>`](relations/has-many) | FK on the **related** model |
| Belongs-to (inverse) | [`BelongsTo<T>`](relations/belongs-to) | FK on **this** model |
| Many-to-many | [`BelongsToMany<T, P>`](relations/belongs-to-many) | FK pair in a **pivot** table |

All relations are **lazy** — they execute a query only when `.get()`, `.count()`,
`.exists()`, etc. are called.

Each relation type is documented on its own page (see the submenu on the left).

---

## Defining relations

Override a method in your model class and return the relation object built by
one of the protected helpers inherited from `Model`:

```java
public class User extends Model {

    public User(String id) { super(id); }

    // One-to-one: FK lives on Phone
    public HasOne<Phone> phone() {
        return hasOne(phoneRepo, "user_id");
    }

    // One-to-many: FK lives on Post
    public HasMany<Post> posts() {
        return hasMany(postRepo, "user_id");
    }

    // Many-to-many through a pivot table
    public BelongsToMany<Role, PivotModel> roles() {
        return belongsToMany(roleRepo, pivotRepo, PivotModel.FACTORY, "user_id", "role_id");
    }
}

public class Phone extends Model {

    public Phone(String id) { super(id); }

    // Inverse: FK lives on Phone
    public BelongsTo<User> owner() {
        return belongsTo(userRepo, "user_id");
    }
}
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
