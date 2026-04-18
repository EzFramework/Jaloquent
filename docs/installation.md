---
title: Installation
nav_order: 2
description: "Add Jaloquent to your Java project via JitPack or GitHub Packages"
---

# Installation
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Requirements

| Requirement | Minimum version |
|-------------|----------------|
| Java | **25** |
| Build tool | Maven **3.8+** or Gradle **8+** |
| (Optional) Jaker | `1.0.0` — only required for the `Factory<T>` API |

---

## Maven

### 1. Add the JitPack repository

```xml
<repositories>
  <repository>
    <id>jitpack.io</id>
    <url>https://jitpack.io</url>
  </repository>
</repositories>
```

### 2. Add the dependency

```xml
<dependency>
  <groupId>com.github.EzFramework</groupId>
  <artifactId>jaloquent</artifactId>
  <version>1.0.5</version>
</dependency>
```

### Optional — Jaker (factory fixtures)

```xml
<dependency>
  <groupId>com.github.EzFramework</groupId>
  <artifactId>jaker</artifactId>
  <version>1.0.0</version>
</dependency>
```

---

## Gradle (Kotlin DSL)

### 1. Add the JitPack repository

```kotlin
repositories {
    maven("https://jitpack.io")
}
```

### 2. Add the dependency

```kotlin
dependencies {
    implementation("com.github.EzFramework:jaloquent:1.0.5")
}
```

---

## GitHub Packages

Jaloquent is also published to GitHub Packages. To consume it from there, authenticate
with a personal access token that has `read:packages` scope.

**`~/.m2/settings.xml`:**

```xml
<servers>
  <server>
    <id>github</id>
    <username>YOUR_GITHUB_USERNAME</username>
    <password>YOUR_GITHUB_PAT</password>
  </server>
</servers>
```

**`pom.xml`:**

```xml
<repositories>
  <repository>
    <id>github</id>
    <url>https://maven.pkg.github.com/EzFramework/Jaloquent</url>
  </repository>
</repositories>
```

---

## Transitive dependencies

| Artifact | Version | Purpose |
|----------|---------|---------|
| `EzFramework/JavaQueryBuilder` | `1.0.4` | SQL query builder — always required |
| `slf4j-api` | `2.0.13` | Logging facade — always required |
| `logback-classic` | `1.5.6` | Default logging implementation |
| `micrometer-core` | `1.12.5` | Metrics facade |
| `micrometer-registry-prometheus` | `1.12.5` | Prometheus metrics export |

{: .note }
> Micrometer and Logback are included for convenience but are opt-in at runtime.
> Set `JaloquentConfig.enableLogging(false)` and `JaloquentConfig.enableMetrics(false)`
> to disable their overhead if you supply your own implementations.

---

## Verifying the installation

Add this snippet to a test class after setup — it should compile and run without errors:

```java
import com.github.ezframework.jaloquent.model.Model;
import com.github.ezframework.jaloquent.model.ModelRepository;
import com.github.ezframework.jaloquent.store.DataStore;

// ... your store implementation ...

ModelRepository<MyModel> repo = new ModelRepository<>(
    myStore, "prefix", (id, data) -> new MyModel(id)
);
System.out.println("Jaloquent is wired correctly.");
```
