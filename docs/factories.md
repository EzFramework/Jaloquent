---
title: Factories
nav_order: 7
description: "Generating test fixtures with Factory<T>, HasFactory, and FactoryCount"
---

# Factories
{: .no_toc }

{: .note }
> Factories require the **Jaker** optional dependency
> (`com.github.EzFramework:jaker:1.0.0`). See [Installation](installation#optional--jaker-factory-fixtures).

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Overview

Jaloquent ships a Laravel-style factory system built on [Jaker](https://github.com/EzFramework/Jaker),
a Java fake-data library. Factories let you generate realistic model instances
for tests and seeders with minimal boilerplate.

```text
Factory<T>          ← abstract base — you implement definition()
  └── PlayerFactory ← your concrete factory
        ↓
  factory().make()              → transient Player
  factory().create(repo)        → persisted Player
  factory().count(5).create(repo) → List<Player> (persisted)
```

---

## Creating a factory

Extend `Factory<T>` and implement `definition(Faker)`:

```java
import com.github.ezframework.jaloquent.model.Factory;
import com.github.ezframework.jaker.Faker;

public class PlayerFactory extends Factory<Player> {

    @Override
    protected Map<String, Object> definition(Faker faker) {
        return Map.of(
            "name",   faker.name().firstName(),
            "coins",  faker.number().numberBetween(0, 10_000),
            "status", "active",
            "level",  1
        );
    }
}
```

The naming convention is `<ModelName>Factory` — this is required by `Factory.discover()`.

---

## Opting in — HasFactory

Add the `HasFactory` marker interface to your model and a static `factory()` method:

```java
import com.github.ezframework.jaloquent.model.HasFactory;
import com.github.ezframework.jaloquent.model.Factory;

public class Player extends Model implements HasFactory {

    public Player(String id) { super(id); }

    public static PlayerFactory factory() {
        return Factory.discover(Player.class);
    }

    // ... typed accessors ...
}
```

`Factory.discover(Player.class)` locates `PlayerFactory` by convention and returns
a new instance. It throws `IllegalStateException` if no matching factory class is found.

---

## Building instances

### `make()` — transient (not persisted)

```java
Player p = Player.factory().make();
// p.getId() is set to a random UUID; no repo call is made
```

### `make(n)` — multiple transient instances

```java
List<Player> players = Player.factory().make(5);
```

### `create(repo)` — persisted

```java
Player p = Player.factory().create(playerRepo);
// model is built, then repo.save() is called
```

### `create(n, repo)` — multiple persisted

```java
List<Player> players = Player.factory().create(10, playerRepo);
```

---

## Overriding attributes with `state`

`state(Map)` merges overrides on top of `definition()`. Later `state()` calls win.
Returns `this` for chaining.

```java
// Single override
Player admin = Player.factory()
    .state(Map.of("role", "admin", "level", 99))
    .make();

// Chained overrides
Player banned = Player.factory()
    .state(Map.of("status", "banned"))
    .state(Map.of("coins", 0))
    .make();
```

---

## Batch operations with `count`

`factory().count(n)` returns a `FactoryCount<T>` that always produces a `List<T>`:

```java
// Transient list
List<Player> players = Player.factory().count(5).make();

// Persisted list
List<Player> players = Player.factory().count(5).create(playerRepo);

// With state applied to all instances in the batch
List<Player> vips = Player.factory()
    .state(Map.of("status", "vip"))
    .count(3)
    .create(playerRepo);
```

---

## Custom Faker locale

By default, factories use `en-US` locale. Pass a custom `Faker` instance:

```java
import com.github.ezframework.jaker.Faker;

Faker deFaker = new Faker(Locale.GERMAN);
PlayerFactory factory = new PlayerFactory(deFaker);
Player player = factory.make();
```

Or specify the model class explicitly:

```java
PlayerFactory factory = new PlayerFactory(deFaker, Player.class);
```

---

## Factory method reference

| Method | Returns | Description |
|--------|---------|-------------|
| `definition(Faker)` | `Map<String, Object>` | **Abstract** — return fake attributes for one instance |
| `state(Map)` | `Factory<T>` | Merge attribute overrides; chainable |
| `count(int)` | `FactoryCount<T>` | Switch to batch mode |
| `make()` | `T` | Build one transient model |
| `make(int)` | `List<T>` | Build N transient models |
| `create(ModelRepository<T>)` | `T` | Build + persist one model |
| `create(int, ModelRepository<T>)` | `List<T>` | Build + persist N models |
| `Factory.discover(Class<M>)` | `Factory<M>` | Locate and instantiate `MFactory` by convention |
