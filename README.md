# Jaloquent

[![](https://jitpack.io/v/EzFramework/Jaloquent.svg)](https://jitpack.io/#EzFramework/Jaloquent)
[![GitHub Packages](https://img.shields.io/badge/GitHub_Packages-1.2.1-blue?logo=github)](https://github.com/EzFramework/Jaloquent/packages)
![Codecov](https://img.shields.io/codecov/c/github/EzFramework/Jaker)


Eloquent-style active-record model and repository layer for Java, built on top of the [EzFramework Java Query Builder](https://github.com/EzFramework/JavaQueryBuilder).  
Supports both SQL (JDBC) and flat-map stores with a consistent API inspired by Laravel's Eloquent ORM.

---

## Requirements

- Java 25+
- Maven or Gradle

---

## Installation

Jaloquent can be easily installed through Jitpack:
https://jitpack.io/#EzFramework/jaloquent

### Maven

Add the JitPack repository and the dependency to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.EzFramework</groupId>
    <artifactId>jaloquent</artifactId>
    <version>1.2.1</version>
</dependency>
```

### Gradle

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.EzFramework:jaloquent:1.2.1'
}
```

---

## Quick Start

### 1. Define a model

```java
public class PlayerData extends Model {
    public PlayerData(String id) { super(id); }

    public int getCoins() { return getAs("coins", Integer.class, 0); }
    public void setCoins(int coins) { set("coins", coins); }

    public String getName() { return getAs("name", String.class); }
    public void setName(String name) { set("name", name); }
}
```

### 2. (Optional) Register a SQL table

```java
TableRegistry.register("players", "player_data", Map.of(
    "id",    "VARCHAR(36) PRIMARY KEY",
    "name",  "VARCHAR(64)",
    "coins", "INT"
));
```

### 3. Create a repository

```java
// Flat-map store (e.g. your own DataStore implementation)
ModelRepository<PlayerData> repo = new ModelRepository<>(
    myDataStore,
    "players",
    (id, data) -> {
        PlayerData p = new PlayerData(id);
        p.fromMap(data);
        return p;
    }
);
```

### 4. CRUD

```java
// Create / update
PlayerData player = new PlayerData("uuid-1234");
player.setName("Alice");
player.setCoins(500);
player.save(repo);

// Read
Optional<PlayerData> found = repo.find("uuid-1234");

// Delete
repo.delete("uuid-1234");
```

### 5. Query

```java
// Simple query
Query q = Model.queryBuilder()
    .whereGreaterThan("coins", 100)
    .orderBy("name", true)
    .build();

List<PlayerData> richPlayers = repo.query(q);
```

### 6. Mass-assignment protection

```java
PlayerData p = new PlayerData("1");
p.setFillable("name", "coins");   // only these keys are accepted by fill()
// OR
p.setGuarded("admin_flag");        // this key is rejected by fill()

p.fill(Map.of("name", "Bob", "admin_flag", true)); // admin_flag silently dropped
```

---

## Relationships

Jaloquent supports Eloquent-style model relationships. Define them as methods on your model class using the protected factory methods inherited from `Model`.

### HasOne

The related model holds a foreign key pointing to the parent.

```java
public class User extends Model {
    public User(String id) { super(id); }

    // A User has one Phone (phones.user_id = users.id)
    public HasOne<Phone> phone() {
        return hasOne(phoneRepo, "user_id");
    }
}

// Usage
Optional<Phone> phone = user.phone().get();

// With additional constraints
Optional<Phone> workPhone = user.phone()
    .where("type", "work")
    .get();

// Check existence
boolean hasPhone = user.phone().exists();
```

Use a custom local key when the anchor is not the model's primary key:

```java
public HasOne<Phone> phoneByEmail() {
    return hasOne(phoneRepo, "owner_email", "email"); // phones.owner_email = users.email
}
```

### HasMany

```java
public class User extends Model {
    public User(String id) { super(id); }

    // A User has many Posts (posts.user_id = users.id)
    public HasMany<Post> posts() {
        return hasMany(postRepo, "user_id");
    }
}

// Usage
List<Post> posts = user.posts().get();

// With constraints and limit
List<Post> recent = user.posts()
    .where("status", "published")
    .orderBy("created_at", false)
    .limit(10)
    .get();

long count = user.posts().count();
```

### BelongsTo

The inverse of `hasOne` / `hasMany`. The foreign key lives on the current model.

```java
public class Phone extends Model {
    public Phone(String id) { super(id); }

    // A Phone belongs to a User (phones.user_id → users.id)
    public BelongsTo<User> owner() {
        return belongsTo(userRepo, "user_id");
    }
}

// Usage
Optional<User> owner = phone.owner().get();
boolean hasOwner = phone.owner().exists();
```

### BelongsToMany

A many-to-many relationship mediated by a pivot model. Use `PivotModel` (or a custom pivot subclass) for the intermediate table.

```java
public class User extends Model {
    public User(String id) { super(id); }

    // A User belongs to many Roles via user_roles pivot
    public BelongsToMany<Role, PivotModel> roles() {
        return belongsToMany(roleRepo, pivotRepo, PivotModel.FACTORY, "user_id", "role_id");
    }
}

// Retrieve
List<Role> roles = user.roles().get();

// Attach / detach
user.roles().attach("role-1");
user.roles().detach("role-1");

// Attach with extra pivot attributes
user.roles().attach("role-1", Map.of("granted_by", "admin"));

// Detach all
user.roles().detachAll();

// Sync — attaches missing IDs, detaches IDs not in the list
user.roles().sync(List.of("role-1", "role-3"));

// Count
long roleCount = user.roles().count();
```

#### Extra pivot attributes

Store additional data on the pivot entry by passing a map to `attach`:

```java
user.roles().attach("role-editor", Map.of("granted_at", "2026-04-15", "granted_by", "admin-id"));
```

Retrieve the pivot entry directly through its repository if you need to read those extra attributes.

### Pivot ID convention

Pivot record IDs are generated as `{parentId}_{relatedId}` (e.g. `"u1_r2"`). This is the key used for `attach`, `detach`, and repository-level lookups.

---

## Transactions

Jaloquent supports atomic multi-step operations through `TransactionalJdbcStore`.
Implement the interface in your store, then use the try-with-resources handle or
the lambda callback:

```java
// Try-with-resources (auto-rollback on exception)
try (Transaction tx = repo.transaction()) {
    repo.save(orderModel);
    repo.save(inventoryModel);
    tx.commit();
}

// Lambda callback (auto-commit on success, auto-rollback on exception)
repo.transaction(() -> {
    repo.save(orderModel);
    repo.save(inventoryModel);
});
```

See the [Transactions documentation](https://ezframework.github.io/Jaloquent/transactions)
for the full guide.

---

## License

See [LICENSE](LICENSE).
