# SwiggyX — Phase 2: Production Upgrade

### Step-by-step upgrade from learning patterns to real production patterns.

> Phase 1 code preserved in swiggyx_app.md and the `learning-version` git branch.
> This document tracks every Phase 2 change, why it was made, and what we learned.

---

## Phase 2 Overview

```
Step 1 — Replace Semaphore with HikariCP config         ✅ Complete
Step 2 — Add @Transactional                              ← IN PROGRESS
Step 3 — Move inventory to DB + @Version optimistic lock
Step 4 — Add Redis distributed lock
Step 5 — Add Kafka message queue
Step 6 — Extract fraud detection to separate service
```

---

## Step 1 — Replace Semaphore with HikariCP ✅

### The Problem

```
We had TWO separate limiting mechanisms for DB access:
1. Our manual Semaphore(20)
2. Spring Boot's auto-configured HikariCP (default pool size 10)

These could disagree — Semaphore says "20 threads can proceed"
but HikariCP only has 10 real connections, causing confusion
about which layer actually governs DB access.
```

### What HikariCP Actually Is

```
HikariCP = independent Java connection pool library
NOT a Spring Boot feature itself — it's pulled in TRANSITIVELY
through spring-boot-starter-data-jpa's dependency chain.

Spring Boot's auto-configuration detects it's available and
sets it up automatically as the default DataSource.
```

### The Fix

```properties
# application.properties — added explicit configuration
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=30000
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.max-lifetime=1800000
```

```java
// ThreadPoolConfig.java — REMOVED
@Bean(name = "dbSemaphore")
public Semaphore dbSemaphore() {
    return new Semaphore(20);
}

// OrderService.java — REMOVED
@Autowired
@Qualifier("dbSemaphore")
private Semaphore dbSemaphore;
```

```java
// OrderService.java — saveOrderToDB() simplified
private Order saveOrderToDB(String userId, String restaurantId,
                            String itemName, int quantity, double amount) {
    System.out.println("Saving order to DB for: " + userId
            + " | Thread: " + Thread.currentThread().getName());

    Order order = Order.builder()
            .userId(userId).restaurantId(restaurantId)
            .itemName(itemName).quantity(quantity)
            .totalAmount(amount).status("PLACED")
            .createdAt(LocalDateTime.now()).build();

    Order savedOrder = orderRepository.save(order);
    System.out.println("Order saved: " + savedOrder.getId() + " for: " + userId);
    return savedOrder;
}
```

### Major Discovery During Testing — The Real Bottleneck

While stress-testing to see if Semaphore(20) or HikariCP(20) was the
actual limiter, we discovered something more important:

```
Test: 25 simultaneous orders fired
Expected: 20 "DB connection acquired" together, 5 waiting
Actual: Only 8 appeared together before any release
```

**Root cause traced through the code:**

```java
public CompletableFuture<String> processOrder(...) {
    return CompletableFuture.supplyAsync(() -> {
        CompletableFuture<Boolean> fraudFuture = checkFraud(userId, amount);
        boolean inventoryAvailable = checkAndDeductInventory(userId, quantity);
        boolean isFraud = fraudFuture.get();  // ← BLOCKS here
        Order savedOrder = saveOrderToDB(...); // ← only reached AFTER above
    }, ioThreadPool);
}
```

```
checkFraud() runs on cpuThreadPool (only 8 threads)
saveOrderToDB() can ONLY run after fraudFuture.get() unblocks
→ only 8 orders can pass through fraud check simultaneously
→ therefore only 8 orders reach the DB step simultaneously
→ Semaphore(20) and HikariCP(20) were NEVER actually stressed
```

**The Bottleneck Principle:**
> A pipeline's overall throughput is limited by its narrowest stage —
> not by the stage you assume is the problem.

```
Stage 1: ioThreadPool   → 16 threads (wide)
Stage 2: cpuThreadPool  →  8 threads (NARROWEST — actual bottleneck)
Stage 3: Semaphore      → 20 permits (never stressed)
Stage 4: HikariCP       → 20 conns   (never stressed)
```

**Bonus discovery — Thread.sleep() isn't real CPU work:**

```
Our checkFraud() uses Thread.sleep(200) to simulate ML model work.
This puts the thread in WAITING state (Concept 3) — it does NOT
compete for CPU cores at all. So increasing cpuThreadPool beyond
8 threads would NOT cause cache eviction (Concept 18 doesn't apply
here) because there's no genuine computation happening.

In production, with an ACTUAL ML model doing real inference,
the 8-thread limit (= cores) would matter exactly as Concept 18
describes — this was a quirk of our simulation, not a contradiction
of the concept.
```

### Verification

```bash
for i in {1..10}; do curl -X POST "http://localhost:8080/api/orders/place?userId=USER_DB_$i&restaurantId=REST_55&itemName=Biryani&quantity=1&amount=450" & done
```

Result: All 10 orders saved (IDs 72-81), inventory correctly decremented
50→40, no errors. "Saving order to DB" log confirms Semaphore removal
works correctly with HikariCP managing connections internally.

---

## Step 2 — Add @Transactional (In Progress)

### The Problem We're Solving

```
Current code uses manual ReentrantLock with strict ordering:
inventoryLock acquired FIRST
paymentLock acquired SECOND

Risk identified earlier: if inventory and payment logic ever get
split into separate files/services (InventoryService.java,
PaymentService.java), the lock ordering rule lives only in
documentation/comments — easy for a new developer to violate,
causing deadlock in production.
```

### What @Transactional Does

```
Wraps a method's database operations in a single atomic transaction.
The DATABASE handles locking and ordering internally — regardless
of how many files, classes, or services are involved in the call chain.

If any step fails, the ENTIRE transaction rolls back automatically.
No manual lock management needed.
```

### Status: Implementation pending — to be added step by step.

---

*Last updated: Step 1 complete, Step 2 starting*

---

## Database Design Primer — Before Building Inventory Table

> Quick grounding in database design principles before we decide
> the Inventory table structure.

---

### The Core Question

> **"What are the 'things' in my system, and how do they relate to each other?"**

---

### Mental Model — Events vs State

```
EVENTS (things that happened, accumulate over time, append-only):
→ orders table    — keeps growing, every order ever placed stays
→ payments table  — every payment transaction ever made

STATE (current snapshot, gets updated in place, few rows):
→ inventory table — represents "right now", same row gets UPDATED
                     as count changes, doesn't grow per order
```

```
orders table (grows forever):           inventory table (few rows, values change):
id=1, status=PLACED  ← 8:00pm           restaurant_id=REST_55, count=10  ← 8:00pm
id=2, status=PLACED  ← 8:01pm           restaurant_id=REST_55, count=9   ← UPDATED 8:01pm
id=3, status=PLACED  ← 8:02pm           restaurant_id=REST_55, count=8   ← UPDATED 8:02pm
```

### Why Inventory Needs Its Own Table

```
Could we calculate inventory by COUNTING orders instead?
"SELECT COUNT(*) FROM orders WHERE restaurant_id = 'REST_55'"

Technically possible, but bad for our use case:
→ slow at scale (counting millions of rows on every check)
→ doesn't represent restocking cleanly
→ no natural place to apply @Version locking
  (would mean conceptually locking the entire orders history)

A dedicated inventory table:
→ ONE row per restaurant/item, instant lookup
→ restocking = simple UPDATE, adding to count
→ natural place for @Version — lock THIS specific row only
```

### The General Rule

> **Ask: "Is this a RECORD of something that happened, or a CURRENT
> VALUE that changes over time?"**
> RECORD → goes in an events table (append-only)
> CURRENT VALUE → goes in a state table (update-in-place)

---

### Database Design Fundamentals

**Step 1 — Identify Entities (nouns in the system)**

```
User, Restaurant, Order, Item, Inventory, Payment, Driver
Each typically becomes its own table.
```

**Step 2 — Identify Relationships**

```
One Restaurant HAS MANY Items           → one-to-many
One Restaurant HAS MANY Inventory rows  → one-to-many
One User PLACES MANY Orders             → one-to-many
One Order BELONGS TO one User           → many-to-one
One Order CONTAINS MANY Items           → many-to-many (needs join table)
```

**Step 3 — Normalization (avoid duplicate data)**

```
BAD (denormalized — data repeated everywhere):
orders table:
id | user_name | user_email   | item_name
1  | Hardik    | hardik@x.com | Chicken Biryani
2  | Hardik    | hardik@x.com | Paneer Tikka
→ Hardik's info repeated in every row
→ updating his email requires updating MANY rows

GOOD (normalized — data stored ONCE, referenced by ID):
users table:
id=1, name=Hardik, email=hardik@x.com

orders table:
id=1, user_id=1, item_name=Chicken Biryani
id=2, user_id=1, item_name=Paneer Tikka
→ Hardik's info exists in exactly ONE place
→ orders just REFERENCE him via user_id (foreign key)
```

**The Three Normal Forms (glimpse only)**

```
1NF — each column holds ONE atomic value (no comma-separated lists)
2NF — every non-key column depends on the WHOLE primary key
3NF — no column depends on another NON-KEY column

Most real systems target 3NF as baseline, then deliberately
denormalize specific parts later for read performance (advanced topic).
```

**Primary Key vs Foreign Key**

```
Primary Key (PK) → uniquely identifies a row WITHIN its own table
                    (orders.id, users.id)

Foreign Key (FK) → a column POINTING to a Primary Key in ANOTHER
                    table, creating the relationship
                    (orders.user_id → users.id)
```

---

### Our Exact Design Decision — Inventory Table Structure

```
This is a real database design decision about the primary key:

Option A — primary key = restaurant_id only
           (assumes ONE item per restaurant — simple but unrealistic)

Option B — primary key = restaurant_id + item_name (composite key)
           (matches a real menu with multiple items per restaurant)
```