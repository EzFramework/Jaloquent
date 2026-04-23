---
title: Exceptions
nav_order: 9
description: "Exception hierarchy, typical catch patterns, and when each exception is thrown"
---

# Exceptions
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Hierarchy

All Jaloquent exceptions extend `JaloquentException`, which itself extends `Exception`.
Every subclass logs itself at `ERROR` level via SLF4J on construction.

```text
Exception
  └── JaloquentException
        ├── StorageException
        │     └── TransactionException
        ├── ValidationException
        └── ModelNotFoundException
```

---

## JaloquentException

The base for all library exceptions. Catch this type when you do not care about
the specific failure mode.

```java
try {
    repo.save(model);
}
catch (JaloquentException e) {
    // handles StorageException, ValidationException, ModelNotFoundException
    log.error("Persistence failed", e);
}
```

**Constructors:**

| Constructor | Use case |
|-------------|----------|
| `JaloquentException(String message)` | Simple message |
| `JaloquentException(String message, Throwable cause)` | Wraps another exception |
| `JaloquentException(Throwable cause)` | Re-throws without adding a message |

---

## StorageException

Thrown when a persistence or I/O operation fails — for example, when a JDBC
query throws a `SQLException`, when a `DataStore` implementation encounters
disk I/O problems, or when a bulk operation partially fails.

```java
try {
    repo.save(model);
    repo.deleteAll(ids);
}
catch (StorageException e) {
    // the underlying I/O failure is available via getCause()
    log.error("Storage operation failed: {}", e.getMessage(), e.getCause());
}
```

---

## ValidationException

Thrown when a model or input fails a validation check before a persistence
operation is attempted. Use this to signal semantic errors — for example,
a required field is blank, or a value is out of an acceptable range.

```java
try {
    model.validate(); // your custom validation method
    repo.save(model);
}
catch (ValidationException e) {
    // return HTTP 422 to the caller — the data is invalid
    response.sendError(422, e.getMessage());
}
```

---

## TransactionException

A subclass of [`StorageException`](#storageexception) raised when a transaction lifecycle operation
fails — `beginTransaction()`, `commitTransaction()`, or `rollbackTransaction()`.
See [Transactions](transactions) for full transaction usage and error handling patterns.

```java
try (Transaction tx = repo.transaction()) {
    repo.save(model);
    tx.commit();
}
catch (TransactionException e) {
    // transaction-specific failure — e.g. connection dropped mid-commit
    log.error("Transaction failed: {}", e.getMessage(), e.getCause());
}
catch (StorageException e) {
    // general storage failure, including store not supporting transactions
    log.error("Storage failed: {}", e.getMessage(), e.getCause());
}
```

---

## ModelNotFoundException

Thrown when a lookup operation **requires** a result but none is found — for
example, in `find(id)` variants that are expected to return a non-null model.

```java
try {
    Player player = repo.findOrFail("unknown-id"); // your helper wrapping repo.find()
}
catch (ModelNotFoundException e) {
    // return HTTP 404 to the caller
    response.sendError(404, e.getMessage());
}
```

{: .note }
> The standard `repo.find(id)` returns `Optional<T>` and never throws
> `ModelNotFoundException`. Throw it yourself when you need to signal that a
> required resource is absent.

---

## Best practices

### Do not expose raw exceptions to API callers

SLF4J logs the exception on construction. Catch `JaloquentException`, map it
to an appropriate response, and do **not** forward the raw exception or its
message to external clients.

```java
// CORRECT — map to a safe API response
catch (StorageException e) {
    return Response.serverError().entity("Storage error").build();
}

// WRONG — leaks internal details
catch (StorageException e) {
    return Response.serverError().entity(e.getMessage()).build();
}
```

### Layer your catches from specific to general

```java
try {
    repo.save(model);
}
catch (ValidationException e) {
    // 422 — invalid input
}
catch (ModelNotFoundException e) {
    // 404 — referenced entity missing
}
catch (StorageException e) {
    // 503 — storage is unavailable or failed
}
catch (JaloquentException e) {
    // 500 — unexpected library error
}
```

### Wrapping in unchecked exceptions

If you prefer unchecked exception handling, wrap at the service layer:

```java
public Player loadPlayer(String id) {
    try {
        return repo.find(id).orElseThrow(() ->
            new ModelNotFoundException("Player not found: " + id)
        );
    }
    catch (JaloquentException e) {
        throw new RuntimeException("Persistence failure", e);
    }
}
```
