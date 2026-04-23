---
title: Transactions
nav_order: 9
description: "Atomic multi-step database operations with TransactionalJdbcStore"
---

# Transactions
{: .no_toc }

## Table of contents
{: .no_toc .text-delta }

1. TOC
{:toc}

---

## Overview

Jaloquent supports explicit database transactions through the
`TransactionalJdbcStore` interface.  When the store opts into this interface,
any [`ModelRepository`](repositories) backed by that store can group multiple operations into a
single atomic unit — all changes commit together or all are rolled back.

Because transaction state is owned by the **store** implementation (not by the
repository), every existing `save()`, `find()`, `delete()`, and `query()` call
automatically participates in any open transaction without any code changes to
models or repositories.

---

## Prerequisites

Your store must implement `TransactionalJdbcStore` (which extends `JdbcStore`):

```java
public class MyJdbcStore implements DataStore, TransactionalJdbcStore {

    private Connection connection;

    @Override
    public void beginTransaction() throws StorageException {
        try {
            connection.setAutoCommit(false);
        }
        catch (SQLException e) {
            throw new TransactionException("Failed to begin transaction", e);
        }
    }

    @Override
    public void commitTransaction() throws StorageException {
        try {
            connection.commit();
            connection.setAutoCommit(true);
        }
        catch (SQLException e) {
            throw new TransactionException("Failed to commit transaction", e);
        }
    }

    @Override
    public void rollbackTransaction() throws StorageException {
        try {
            connection.rollback();
            connection.setAutoCommit(true);
        }
        catch (SQLException e) {
            throw new TransactionException("Failed to rollback transaction", e);
        }
    }

    // ... query() and executeUpdate() implementations ...
}
```

---

## Try-with-resources API

`ModelRepository#transaction()` returns a `Transaction` handle that implements
`AutoCloseable`.  If the try-with-resources block exits without an explicit
`commit()`, the transaction is **automatically rolled back**:

```java
try (Transaction tx = repo.transaction()) {
    repo.save(orderModel);
    repo.save(inventoryModel);
    tx.commit();                  // all changes committed atomically
}
// if commit() was never reached, rollback is called automatically
```

---

## Lambda callback API

`ModelRepository#transaction(TransactionCallback)` wraps the boilerplate: it
opens a transaction, calls your lambda, commits on success, and rolls back on
any exception:

```java
repo.transaction(() -> {
    repo.save(orderModel);
    repo.save(inventoryModel);
});
// committed if no exception was thrown, rolled back otherwise
```

If the lambda throws a non-`StorageException`, it is wrapped in a
`StorageException` before being re-thrown.

---

## Explicit rollback

Use `tx.rollback()` inside a try-with-resources block when you need to abort
based on business logic rather than an exception:

```java
try (Transaction tx = repo.transaction()) {
    repo.save(reservationModel);

    if (!inventoryService.reserve(itemId)) {
        tx.rollback();            // explicit rollback — close() is then a no-op
        return;
    }
    tx.commit();
}
```

---

## Sharing a transaction across repositories

Because transaction state lives in the **store**, two repositories that share
the same `TransactionalJdbcStore` instance automatically participate in the
same transaction:

```java
ModelRepository<Order>    orderRepo     = new ModelRepository<>(sharedStore, ...);
ModelRepository<Inventory> inventoryRepo = new ModelRepository<>(sharedStore, ...);

try (Transaction tx = orderRepo.transaction()) {
    orderRepo.save(order);
    inventoryRepo.save(inventory);  // same connection, same transaction
    tx.commit();
}
```

---

## Exception handling

| Exception | When thrown |
|-----------|-------------|
| [`StorageException`](exceptions#storageexception) | Store does not implement `TransactionalJdbcStore`; commit/rollback fails; callback throws a non-storage exception |
| [`TransactionException`](exceptions#transactionexception) | Subclass of `StorageException` raised by `TransactionalJdbcStore` implementations for begin/commit/rollback failures |

```java
try (Transaction tx = repo.transaction()) {
    repo.save(model);
    tx.commit();
}
catch (TransactionException e) {
    // specific transaction lifecycle failure
}
catch (StorageException e) {
    // any other storage failure, including non-transactional store
}
```

---

## Flat-map stores

Flat-map (`DataStore`-only) stores do not support transactions.  Calling
`repo.transaction()` when the store does not implement `TransactionalJdbcStore`
throws `StorageException("Store does not support transactions")`.

---

## API summary

| Method | Description |
|--------|-------------|
| `repo.transaction()` | Open a transaction; returns `Transaction` (try-with-resources) |
| `repo.transaction(callback)` | Execute lambda inside a transaction; auto-commit on success, auto-rollback on error |
| `tx.commit()` | Commit all changes since `beginTransaction()` |
| `tx.rollback()` | Discard all changes since `beginTransaction()` |
| `tx.close()` | Auto-rollback if neither `commit()` nor `rollback()` was called; no-op otherwise |
