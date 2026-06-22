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

---

## Understanding Composite Primary Keys

### What a Composite Key Means

```
A composite primary key = MULTIPLE columns TOGETHER act as
the unique identifier for a row.

Neither column ALONE guarantees uniqueness.
Only the COMBINATION of both columns guarantees uniqueness.
```

### Concrete Example

```
inventory table:

restaurant_id | item_name  | count
──────────────┼────────────┼───────
REST_55       | Biryani    | 10
REST_55       | Pizza      | 5      ← same restaurant_id, different item_name
REST_72       | Biryani    | 8      ← same item_name, different restaurant_id

Is restaurant_id alone unique? NO — "REST_55" appears TWICE
Is item_name alone unique?     NO — "Biryani" appears TWICE

Is (restaurant_id + item_name) together unique? YES
(REST_55, Biryani) → exactly ONCE
(REST_55, Pizza)   → exactly ONCE
(REST_72, Biryani) → exactly ONCE

No two rows can EVER have the SAME combination of both values.
```

### Why You Need Both Values to Query

```
To find "how much Biryani does REST_55 have?"
→ need BOTH values: WHERE restaurant_id = 'REST_55' AND item_name = 'Biryani'

Just WHERE restaurant_id = 'REST_55' alone → returns MULTIPLE rows
(Biryani AND Pizza) — ambiguous
```

### Connection to Single-Column Keys

```
Order.id (single column key):
id=1 → uniquely identifies exactly one order, by itself

InventoryId (composite key — restaurant_id + item_name):
Neither piece alone is enough — need BOTH together

Same CONCEPT (uniquely identify a row) — just achieved using
TWO columns working together instead of one.
```

---

## Is a Primary Key Strictly Necessary?

```
Technically, in raw SQL: NO — a table CAN exist without one.
In practice: YES, almost ALWAYS want one.
```

### What Breaks WITHOUT a Primary Key

```sql
-- table with NO primary key
CREATE TABLE inventory (
    restaurant_id VARCHAR(255),
    item_name VARCHAR(255),
    count INT
);

-- insert TWICE by accident
INSERT INTO inventory VALUES ('REST_55', 'Biryani', 10);
INSERT INTO inventory VALUES ('REST_55', 'Biryani', 10);
```

```
Without a primary key — database has NO WAY to stop this.
Two IDENTICAL rows now exist.

Trying to UPDATE "REST_55's Biryani count" —
WHICH row updates? Database doesn't know which one you mean.
Might update only ONE of the duplicates, leaving the
OTHER stale and wrong FOREVER.
```

### What a Primary Key Actually Guarantees

```
1. UNIQUENESS — database REJECTS any insert that would create
   a duplicate primary key (throws ERROR on attempt)

2. FAST LOOKUPS — database automatically creates an INDEX on
   the primary key — "find this exact row" is extremely fast
   (without one, finding a row means scanning the whole table)

3. STABLE REFERENCE point — foreign keys point TO a primary key,
   they need something stable and unique to point at
```

### Why We Specifically Need It Here

```
If TWO threads try to INSERT a new inventory row for the SAME
restaurant+item at the EXACT same moment — without a primary key,
BOTH inserts would succeed, creating duplicate rows.

WITH a primary key — the SECOND insert FAILS immediately,
guaranteed by the database itself.

This is concurrency protection enforced at the DATABASE level —
not requiring our Java locks at all. A different layer of
defense than what we built in Phase 1.
```

### One Line Summary

> **A primary key isn't syntactically required by SQL, but it's the database's built-in mechanism for guaranteeing "this
combination of values can only exist ONCE" — without it, you lose data integrity, fast lookups, and the database's own
automatic protection against duplicate/race-condition inserts.**


---

## Why @Data is CRITICAL (not just convenience) for InventoryId

### The Problem — Default Object Comparison

```
By default in Java, comparing two objects with == only checks
if they're the EXACT SAME object in memory — NOT if their
VALUES are equal.

new InventoryId("REST_55", "Biryani") == new InventoryId("REST_55", "Biryani")
→ FALSE by default (different objects in memory, even though
  the VALUES inside are identical)
```

### Why This Breaks JPA's Composite Key Lookups

```
JPA needs to answer: "Is THIS InventoryId(REST_55, Biryani) the
SAME as THAT InventoryId(REST_55, Biryani)?"

JPA internally uses equals() and hashCode() to compare keys
and find matching rows. Without proper implementations, JPA
might NEVER find the matching row, even though it exists in
the database with those exact values.
```

### @Data Solves This

```
@Data generates equals() and hashCode() that compare based on
ACTUAL VALUES of restaurantId and itemName — not memory location.

new InventoryId("REST_55", "Biryani").equals(
    new InventoryId("REST_55", "Biryani"))
→ TRUE (because @Data's generated equals() compares VALUES)
```

### Order entity vs InventoryId — Different Importance Level

```
For Order entity → @Data was mostly convenience
                    (getters/setters/toString — nice to have)

For InventoryId  → @Data's equals()/hashCode() is ESSENTIAL
                    This is a documented JPA REQUIREMENT —
                    composite key classes MUST properly
                    implement equals() and hashCode()
```

### One Line Summary

> **@Data's auto-generated equals() and hashCode() (based on field VALUES, not memory location) are a strict JPA
requirement for composite key classes — without them, JPA cannot reliably match a key object to its corresponding
database row.**


---

## How JPA Knows InventoryId Is the Primary Key — @EmbeddedId Explained

### The Annotation That Answers This

```java
@EmbeddedId
private InventoryId id;
```

```
@Id         → "this single field is THE primary key"
              (used for Order.id — a SIMPLE key)

@EmbeddedId → "this field is ALSO the primary key, but it's
              actually a WHOLE OBJECT containing multiple
              columns, not just one simple value"
```

```
JPA sees @EmbeddedId and looks INSIDE the InventoryId class
(because it's marked @Embeddable) to find ALL columns that
together make up the key: restaurantId, itemName

JPA creates BOTH as actual columns in the inventory table
and treats BOTH together as the primary key.
```

### Why Not Just Use Two Separate @Id Fields?

```java
// This does NOT work cleanly in JPA:
@Entity
public class Inventory {
    @Id
    private String restaurantId;

    @Id  // ❌ can't have two @Id annotations like this
    private String itemName;
}
```

```
JPA needs ONE single object that REPRESENTS the entire key —
so it can:
→ pass it around as ONE parameter (e.g., findById(InventoryId))
→ compare two keys using ONE equals() call
→ serialize/cache it as ONE unit
```

### The Real Reason — How We Actually Use It in Code

```java
// WITHOUT a wrapper class — doesn't work:
findById(restaurantId, itemName)
// JPA's findById() doesn't support multiple parameters like this

// WITH InventoryId wrapper class — works perfectly:
InventoryId key = new InventoryId("REST_55", "Biryani");
inventoryRepository.findById(key);
// ONE object represents the WHOLE key
```

### The Pattern, Generalized

```
Single-column key  → just use @Id on that one field directly
                      (what we did for Order.id)

Composite key       → CANNOT use multiple @Id annotations
(multiple columns)    MUST create a separate @Embeddable class
                      that BUNDLES those columns together
                      MUST use @EmbeddedId on a field of that
                      type in the main entity

This is a STANDARD, REQUIRED JPA pattern — every developer using
JPA with composite keys follows this exact pattern (an alternative
called @IdClass achieves the same concept with slightly different syntax).
```

### One Line Summary

> **@EmbeddedId tells JPA "look inside THIS object to find the actual primary key columns." A separate InventoryId class
is required because JPA needs ONE object to represent a composite key as a single unit — for method parameters, equals()
comparison, and internal caching.**


---

## The Load-Modify-Save Pattern — Why JPA Works This Way

### What's Actually Happening

```java
Inventory inv = inventoryRepository.findById(invId)
        .orElseThrow(...);
// 'inv' is a Java OBJECT loaded from the database
// represents the CURRENT state of that row, AT THIS MOMENT

inv.setCount(inv.getCount() - quantity);
// ONLY changes the Java object IN MEMORY
// the actual DATABASE ROW is UNCHANGED at this point!

inventoryRepository.save(inv);
// THIS is the line that ACTUALLY writes to the database
// generates: UPDATE inventory SET count = ?
//            WHERE restaurant_id = ? AND item_name = ?
```

### Why This Pattern Instead of Direct SQL UPDATE?

```
Without JPA (raw SQL) — one direct statement:
"UPDATE inventory SET count = count - 1
 WHERE restaurant_id = 'REST_55' AND item_name = 'Biryani'"

With JPA — three-step pattern:
1. LOAD the row into a Java OBJECT
2. MODIFY the object's field in Java memory
3. SAVE the object back (JPA generates the UPDATE SQL)
```

### Benefits of This Pattern

```
1. Work with Java objects, not raw SQL strings
   (type-safe, autocomplete, no manual string-building)

2. JPA tracks WHICH fields changed, generates efficient updates

3. REQUIRED for @Version optimistic locking (next step!)
   @Version needs to compare "version I loaded" vs
   "version currently in DB" — only works if you LOAD first,
   then SAVE — not with a direct blind UPDATE
```

### The Race Condition Concern — Still Protected by ReentrantLock

```
What if ANOTHER thread changes the DB row BETWEEN
findById() and save()?

Thread 1: inventoryLock.lock()
          → findById() → loads count=10
          → setCount(9)
          → save() → writes count=9 to DB
          → inventoryLock.unlock()

Thread 2: (was WAITING for the lock this whole time)
          → inventoryLock.lock() (NOW acquires it)
          → findById() → loads count=9 (correctly sees update!)
          → setCount(8)
          → save() → writes count=8 to DB
          → inventoryLock.unlock()

Our ReentrantLock STILL protects this read-modify-write
sequence, exactly like it protected the in-memory variable.
We moved WHERE the data lives (DB instead of RAM) —
the LOCKING logic protecting it hasn't changed YET.
```

### What Changes in the Next Step (@Version)

```
@Version will eventually REPLACE this manual ReentrantLock
for inventory — letting the DATABASE detect if another thread
modified the row between load and save, instead of manually
locking in Java.

For NOW — our lock does exactly the job it did before,
just protecting a database read-modify-write instead of
a Java variable read-modify-write.
```

### One Line Summary

> **setCount() only changes the in-memory Java object; save() is the actual database write. This load-modify-save
pattern is required for JPA's object-oriented approach and essential for @Version optimistic locking to work later. The
ReentrantLock still protects this sequence exactly as before — only WHERE the data lives has changed (DB instead of
RAM), not HOW it's protected (yet).**


---

## Phase 2, Step 3 — Complete: Inventory Successfully Moved to Database

### Final Verification Test

```bash
for i in {1..5}; do curl -X POST "http://localhost:8080/api/orders/place?userId=USER_CONCURRENT_$i&restaurantId=REST_55&itemName=Biryani&quantity=1&amount=450" & done
```

### Hibernate-Generated SQL — Composite Key in Action

```sql
-- SELECT — confirms composite key lookup
select i1_0.item_name,i1_0.restaurant_id,i1_0.count from inventory i1_0
where (i1_0.item_name,i1_0.restaurant_id) in ((?,?))

-- UPDATE — confirms composite key WHERE clause
update inventory set count=? where item_name=? and restaurant_id=?
```

### Race Condition Protection Confirmed — Sequential, Correct Values

```
Inventory check for: USER_CONCURRENT_5 | Current: 9 → Remaining: 8
Inventory check for: USER_CONCURRENT_4 | Current: 8 → Remaining: 7
Inventory check for: USER_CONCURRENT_2 | Current: 7 → Remaining: 6
Inventory check for: USER_CONCURRENT_3 | Current: 6 → Remaining: 5
Inventory check for: USER_CONCURRENT_1 | Current: 5 → Remaining: 4
```

```
9 → 8 → 7 → 6 → 5 → 4

Each thread correctly saw the PREVIOUS thread's update.
No race condition. ReentrantLock still protects this sequence
correctly — even with data now living in PostgreSQL instead of
in-memory Java variable.
```

### Persistence Across Restart — Confirmed

```
Before fix: server restart → inventory resets to hardcoded value
After fix:  server restart → inventory retains its DB value
            (verified: count stayed at 9, did not reset to 10)
```

### What's Now Complete in Step 3

```
✅ Inventory entity with composite primary key (InventoryId)
✅ InventoryId class with @Embeddable, @Data (equals/hashCode critical)
✅ InventoryRepository extends JpaRepository<Inventory, InventoryId>
✅ checkAndDeductInventory() reads/writes from DB
✅ Fraud-restoration logic updated to restore inventory in DB
✅ ReentrantLock still protects the DB read-modify-write sequence
✅ Verified: race condition protection works correctly
✅ Verified: persists across server restarts
```

### What Remains

```
→ @Version optimistic locking — will eventually replace the
  manual ReentrantLock for inventory, letting the database
  itself detect concurrent modification conflicts

→ This becomes essential once we have MULTIPLE SERVERS
  (horizontal scaling) — ReentrantLock only works within
  ONE JVM, but @Version works at the database level,
  correctly catching conflicts even across different servers
```

---

## @Version Optimistic Locking — The Concept

### The Problem We're Solving

```
ReentrantLock only works within ONE server/JVM.

Server 1: inventoryLock (Server 1's lock) → inventory = 9
Server 2: inventoryLock (Server 2's lock) → inventory = 9

Both succeed independently — but only 1 item was actually left!
Server 1's lock has ZERO knowledge of Server 2's lock.
```

### The Core Idea — Optimistic vs Pessimistic Locking

```
Pessimistic (ReentrantLock, synchronized):
"Stop everyone else BEFORE I start working" — lock first, then act.

Optimistic (@Version):
"I'll assume no conflict will happen — but I'll VERIFY before
committing my change, just in case." — act first, check at save time.
```

### How @Version Works, Step by Step

```
1. Thread reads a row → sees: count=10, version=5

2. Thread does its work (calculates new count = 9)

3. Thread tries to SAVE:
   "UPDATE inventory SET count=9, version=6
    WHERE restaurant_id='REST_55' AND item_name='Biryani'
    AND version=5"
                                    ↑
                    Only update if version is STILL 5
                    (meaning nobody else changed it since read)

4a. Version still 5 in DB → UPDATE succeeds → version becomes 6
4b. Version now 6 (someone else updated first) →
    UPDATE matches ZERO rows → JPA throws OptimisticLockException
    → code must CATCH this — retry, or fail the order
```

### Why This Works Across Multiple Servers

```
The version number lives IN THE DATABASE — not in any
single server's memory.

Server 1 reads version=5, Server 2 ALSO reads version=5
(both see the SAME database state)

Server 1 updates FIRST → version becomes 6 in DB
Server 2 tries: "WHERE version=5" → DB has version=6 now
→ ZERO rows match → Server 2's update FAILS

The DATABASE is the single source of truth — not any
individual server's in-memory lock.
```

### Need a Real Database Column

```
version column must EXIST as a real column in the table.
JPA/Hibernate manages its VALUE automatically — never set
or increment it manually — but the COLUMN itself is required.

inventory table:
restaurant_id | item_name | count | version
REST_55       | Biryani   | 10    | 0        ← starts at 0
REST_55       | Biryani   | 9     | 1        ← after one successful update
```

### This Is a Universal, Language-Agnostic Pattern

```
The CONCEPT (optimistic locking via version/timestamp column)
exists across every major tech stack — not a Java-specific trick:

Java/Spring:      @Version annotation
C#/.NET:           [ConcurrencyCheck] / RowVersion column
Python/Django:     manual implementation or django-concurrency
Ruby on Rails:     built-in 'lock_version' column — same concept
Node.js/Mongoose:  __v field (versionKey) — MongoDB's built-in
                   optimistic concurrency control

Each framework just provides convenient syntax wrapping the
SAME underlying SQL pattern:

UPDATE inventory SET count=9, version=6
WHERE restaurant_id='REST_55' AND item_name='Biryani' AND version=5;
-- check: did this affect 1 row, or 0 rows?
-- 0 rows affected = someone else updated it first = CONFLICT
```

---

## @Transactional vs @Version — They Solve DIFFERENT Problems

### The Scenario Where @Transactional ALONE Fails (The "Lost Update" Problem)

```
Time 0ms: Thread 1 (Transaction A) reads inventory: count=10
Time 1ms: Thread 2 (Transaction B) ALSO reads inventory: count=10
          (Transaction B hasn't committed yet — depending on DB
          isolation level, Thread 2 can still read this same row)

Time 2ms: Thread 1 calculates 10-1=9 → SAVES → commits → DB: count=9
Time 3ms: Thread 2 calculates 10-1=9  ← WRONG! Should be 8!
          (Thread 2 never SAW Thread 1's update — read stale value
          BEFORE Thread 1 committed)
          Thread 2 SAVES → commits → DB: count=9

FINAL RESULT: count=9, but we deducted inventory TWICE!
Real answer should have been 8. We LOST a deduction.
```

### Why @Transactional Alone Doesn't Prevent This

```
@Transactional makes EACH transaction atomic and consistent
WITHIN ITSELF — but does NOT automatically prevent TWO SEPARATE
transactions from reading the SAME stale data and both acting on it.

This depends on TRANSACTION ISOLATION LEVEL (separate deep topic) —
by DEFAULT, most databases allow this "lost update" scenario.
```

### What @Version Catches That @Transactional Alone Does Not

```
Replaying the SAME scenario, WITH @Version:

Thread 1 reads: count=10, version=5
Thread 2 reads: count=10, version=5 (same as before)

Thread 1 saves: UPDATE ... WHERE version=5 → SUCCEEDS
                → DB now: count=9, version=6

Thread 2 saves: UPDATE ... WHERE version=5
                → DB has version=6 now, NOT 5
                → ZERO rows match → THROWS EXCEPTION
                → Thread 2's transaction ROLLS BACK
                → Thread 2 must RETRY (reads fresh: count=9, version=6)
                → THEN correctly deducts: count=8, version=7
```

### The Precise Distinction

```
@Transactional → "make MY OWN sequence of operations atomic"
                 (all-or-nothing WITHIN my own work)

@Version       → "detect if SOMEONE ELSE'S concurrent work
                 conflicts with mine, reject my save if so"
                 (protection AGAINST other transactions)

They solve DIFFERENT problems. Production systems typically
use BOTH together.
```

---

## What "All-or-Nothing" Means — Concretely, Using Our Own Bug

### The Exact Problem We Identified Earlier

```java
boolean inventoryAvailable = checkAndDeductInventory(...);
// inventory: 10 → 9 (DEDUCTED, committed to DB)

Order savedOrder = saveOrderToDB(...);
// ❌ what if THIS throws an exception?
```

```
WITHOUT @Transactional:
Inventory deduction ALREADY COMMITTED.
If order save fails — inventory deduction CANNOT be undone.

Result: inventory shows 9, but NO order exists, payment never
charged. We've PERMANENTLY lost one unit of inventory for nothing.
```

### What @Transactional Actually Guarantees

```java

@Transactional
public String processOrderAtomically(...) {
    deductInventory(...);   // Step 1
    saveOrderToDB(...);      // Step 2
    // if EITHER throws an exception ANYWHERE in this method...
}
```

```
WITH @Transactional:
Both steps bundled into ONE database transaction.
If Step 2 fails, Spring AUTOMATICALLY tells the database:
"ROLL BACK everything in this transaction"
→ Step 1's deduction gets UNDONE too, automatically
→ Database returns to EXACTLY the state before we started
→ inventory goes back to 10, no orphaned/broken state
```

### Only Two Possible Outcomes, Permanently

```
Outcome A — ALL succeed:
inventory deducted AND order saved AND payment charged
→ everything committed together

Outcome B — NOTHING succeeds:
inventory NOT deducted AND no order exists AND no payment
→ everything rolled back together, as if never attempted

IMPOSSIBLE with @Transactional:
inventory deducted BUT no order saved
(the broken state we identified manually — which would
otherwise require extensive custom catch-and-restore code)
```

### One Line Summary

> **"All-or-nothing" means @Transactional guarantees only two possible outcomes ever exist in the database: either every
operation succeeded together, or none of them did (full automatic rollback on any failure) — eliminating the broken "
partially completed" states we identified manually (inventory deducted but order never saved).**


---

## @Version Implementation — Test Results and a Critical Discovery

### What We Implemented

```java
private boolean checkAndDeductInventory(String userId,
                                        String restaurantId,
                                        String itemName,
                                        int quantity) {

    int maxRetries = 3;

    for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            InventoryId invId = new InventoryId(restaurantId, itemName);

            Inventory inv = inventoryRepository.findById(invId)
                    .orElseThrow(() -> new RuntimeException(
                        "Inventory not found for: " + restaurantId + ", " + itemName));

            System.out.println("Inventory check for: " + userId
                + " | Current: " + inv.getCount()
                + " | Version: " + inv.getVersion()
                + " | Attempt: " + attempt
                + " | Thread: " + Thread.currentThread().getName());

            if (inv.getCount() < quantity) {
                System.out.println("Insufficient inventory for: " + userId);
                return false;
            }

            inv.setCount(inv.getCount() - quantity);
            inventoryRepository.save(inv);

            System.out.println("Inventory deducted for: " + userId
                + " | Remaining: " + inv.getCount());
            return true;

        } catch (OptimisticLockingFailureException e) {
            System.out.println("Conflict detected for: " + userId
                + " | Attempt: " + attempt + " | Retrying...");
        }
    }

    System.out.println("Failed after " + maxRetries + " attempts for: " + userId);
    return false;
}
```

**Deliberately removed `inventoryLock.lock()`/`unlock()` entirely**
— this version relies PURELY on `@Version` + retry, with NO
pessimistic locking at all, specifically to test the hypothesis
that @Version alone could correctly prevent the race condition.

### Pre-Version Field Migration Issue (Resolved)

```
When @Version was first added to the EXISTING inventory table,
Hibernate generated:
  alter table if exists inventory add column version bigint

PostgreSQL filled the EXISTING row's new column with NULL
(no DEFAULT value specified). NULL breaks @Version's comparison
logic (NULL ≠ 0 in SQL), so the very first update would have
incorrectly appeared as a conflict.

Fix applied manually:
  UPDATE inventory SET version = 0 WHERE version IS NULL;

Note: this manual fix was ONLY needed for the RETROFITTED
existing row. Any NEW row created going forward automatically
gets version=0 from JPA on insert — no manual intervention needed.
```

### The Test

```bash
# Reset clean state first
# SQL: UPDATE inventory SET count = 10, version = 0
#      WHERE restaurant_id = 'REST_55' AND item_name = 'Biryani';

for i in {1..5}; do curl -X POST "http://localhost:8080/api/orders/place?userId=USER_VERSION_$i&restaurantId=REST_55&itemName=Biryani&quantity=1&amount=450" & done
```

### Result — Proof @Version Correctly Detects Conflicts

```
All 5 threads read the SAME version=0 simultaneously (nothing
prevented concurrent reads — there's no lock anymore):

Inventory check for: USER_VERSION_4 | Current: 10 | Version: 0 | Attempt: 1
Inventory check for: USER_VERSION_5 | Current: 10 | Version: 0 | Attempt: 1
Inventory check for: USER_VERSION_3 | Current: 10 | Version: 0 | Attempt: 1
Inventory check for: USER_VERSION_1 | Current: 10 | Version: 0 | Attempt: 1
Inventory check for: USER_VERSION_2 | Current: 10 | Version: 0 | Attempt: 1

Only ONE succeeded on attempt 1:
Inventory deducted for: USER_VERSION_3 | Remaining: 9

The other FOUR correctly detected the conflict and retried:
Conflict detected for: USER_VERSION_2 | Attempt: 1 | Retrying...
Conflict detected for: USER_VERSION_4 | Attempt: 1 | Retrying...
Conflict detected for: USER_VERSION_1 | Attempt: 1 | Retrying...
Conflict detected for: USER_VERSION_5 | Attempt: 1 | Retrying...
```

```
This proves the core mechanism works exactly as designed:
no lock prevented the race condition from forming, but
@Version correctly DETECTED it after the fact (via the
generated SQL "WHERE ... AND version=?" matching zero rows),
and our retry loop CORRECTED it by re-reading fresh data.
```

### CRITICAL DISCOVERY — Legitimate Requests Can Fail Under High Contention

```
At the end of the test, despite count finishing at 7 (meaning
3 units were successfully deducted, with PLENTY of inventory
remaining for more orders) — TWO users still FAILED entirely:

Conflict detected for: USER_VERSION_2 | Attempt: 3 | Retrying...
Conflict detected for: USER_VERSION_4 | Attempt: 3 | Retrying...
Failed after 3 attempts for: USER_VERSION_4
Failed after 3 attempts for: USER_VERSION_2
```

```
USER_VERSION_2 and USER_VERSION_4 repeatedly lost the "race" to
update — attempt after attempt, getting beaten by OTHER threads
completing their update microseconds faster — and exhausted all
3 retry attempts before succeeding, even though inventory was
NEVER actually unavailable for them.
```

### Why This Happened — Connecting to Master Reference Pillar 19

```
This is a direct, empirical demonstration of the exact tradeoff
documented in Pillar 19 of our master reference:

"Optimistic concurrency control works BEST when conflicts are
EXPECTED TO BE RARE... When conflicts are EXPECTED to be
frequent, traditional (pessimistic) locking often performs
better, since constant retries under heavy contention become
wasteful themselves."

5 threads simultaneously targeting the EXACT SAME row is a
HIGH-CONTENTION scenario. We have now empirically PROVEN that
high contention can cause legitimate, valid requests to fail
under pure optimistic locking — even when the underlying
resource (inventory) was genuinely available the whole time.
```

### Important Distinction — This Is NOT Starvation

```
This LOOKS similar to starvation (Pillar 13) at first glance —
"some threads keep losing out" — but it is a DIFFERENT, more
specific phenomenon:

Starvation (Pillar 13): an unfair SCHEDULER repeatedly favors
                         certain threads when granting a LOCK
                         that ALREADY EXISTS — fixable by adding
                         fairness to that lock.

What we observed here:  there IS no lock at all. The "failure"
                         is a property of optimistic retry having
                         a FINITE retry count under HIGH
                         CONTENTION — not a scheduling fairness
                         issue. Increasing maxRetries would
                         reduce (not eliminate) this specific
                         failure mode; it's a different root
                         cause than starvation.
```

### Three Possible Paths Forward (Decision Pending)

```
Option A — Pure @Version, increase maxRetries / add backoff
           Reduces (doesn't eliminate) the high-contention
           failure rate. Keeps the "no lock, works across
           multiple servers" benefit fully intact.

Option B — @Version + ReentrantLock together
           The lock prevents the high-contention scramble from
           happening AT ALL within one server (since only one
           thread can even ATTEMPT the read-modify-save sequence
           at a time) — @Version becomes "extra insurance"
           rather than the primary mechanism. Re-introduces the
           "only works within one server" limitation for the
           in-process portion, though @Version would still catch
           genuine cross-server conflicts.

Option C — Accept the current behavior as-is
           In our SwiggyX context, a failed order due to high
           contention could simply be treated as "please retry
           your order" at the APPLICATION/UI level (similar to
           how real systems sometimes show "high demand, please
           try again") — rather than solving it purely in the
           backend logic.
```

---

## @Version Implementation — Final Confirmation (Complete)

### The Improvement — Distinguishing Failure Reasons

```java
// checkAndDeductInventory() now throws on retry exhaustion
// instead of silently returning false:
throw new RuntimeException("HIGH_DEMAND");

// New helper — restoreInventory() uses @Version + retry,
// replacing the leftover inventoryLock-based restoration
private void restoreInventory(String restaurantId, String itemName,
                              int quantity, String userId) {
    int maxRetries = 3;
    for (int attempt = 1; attempt <= maxRetries; attempt++) {
        try {
            InventoryId invId = new InventoryId(restaurantId, itemName);
            Inventory inv = inventoryRepository.findById(invId)
                    .orElseThrow(() -> new RuntimeException("Inventory not found"));
            inv.setCount(inv.getCount() + quantity);
            inventoryRepository.save(inv);
            System.out.println("Inventory restored for: " + userId);
            return;
        } catch (OptimisticLockingFailureException e) {
            System.out.println("Conflict while restoring for: " + userId + " | Retrying...");
        }
    }
    System.out.println("WARNING: Failed to restore inventory for: " + userId);
}

// processOrder() Step 3 — catches HIGH_DEMAND distinctly
boolean inventoryAvailable;
try {
    inventoryAvailable = checkAndDeductInventory(userId, restaurantId, itemName, quantity);
} catch (RuntimeException e) {
    if ("HIGH_DEMAND".equals(e.getMessage())) {
        return "FAILED: High demand right now, please try again";
    }
    throw e;
}
if (!inventoryAvailable) {
    return "FAILED: Item not available";
}

// Step 4 — fraud-check failure path now also restores via the
// SAME @Version-based helper (previously a gap — was never
// restored at all in this branch)
} catch (Exception e) {
    restoreInventory(restaurantId, itemName, quantity, userId);
    return "FAILED: Fraud check error";
}
```

### Final Confirmation Test

```bash
# Reset: UPDATE inventory SET count = 3, version = 0
#        WHERE restaurant_id = 'REST_55' AND item_name = 'Biryani';

for i in {1..5}; do curl -X POST "http://localhost:8080/api/orders/place?userId=USER_FINAL_$i&restaurantId=REST_55&itemName=Biryani&quantity=1&amount=450" & done
```

### Results — Curl Responses

```
SUCCESS: Order placed. ID: 11
SUCCESS: Order placed. ID: 10
SUCCESS: Order placed. ID: 12
FAILED: High demand right now, please try again
FAILED: High demand right now, please try again
```

```
3 successful orders — exactly matching the 3 units of inventory
available. 2 correctly distinguished "high demand" failures —
NOT "Item not available," since inventory was genuinely available
the whole time; these 2 users simply lost the optimistic-locking
race repeatedly. Zero misleading messages shown.
```

### @Version Optimistic Locking — Status: COMPLETE

```
✅ @Version field added, retrofitted existing row (NULL → 0 fix)
✅ checkAndDeductInventory() uses pure @Version + retry, no lock
✅ Confirmed via real logs: conflict detection works correctly
✅ Confirmed via real logs: legitimate high-contention failures
   occur (the known probabilistic tradeoff of optimistic locking)
✅ Fixed: distinct error message for "high demand" vs "sold out"
✅ Fixed: restoreInventory() consistently uses @Version (removed
   leftover inconsistent inventoryLock usage)
✅ Fixed: fraud-check-error path now also restores inventory
   (previously a silent gap)
```

---

## Step 2 — @Transactional: A Real Bug Proven, and a Real Pitfall Discovered

### The Bug We Set Out to Fix

```
checkAndDeductInventory() commits its DB update immediately
(no transaction wrapping it). If saveOrderToDB() fails AFTER
inventory was already deducted, the system is left in a broken
state: inventory permanently reduced, but no order exists and
no payment was charged.
```

### Proof of the Bug — Forced Failure Test

```java
// Temporarily added inside saveOrderToDB(), before the actual save:
if(true){throw new RuntimeException("SIMULATED DB FAILURE FOR TESTING"); }
```

```sql
-- Before test: inventory.count = 5
-- After test:
SELECT * FROM inventory;  → count = 4   (deducted, NOT rolled back)
SELECT * FROM orders WHERE user_id = 'USER_BUG_TEST';  → 0 rows
```

```
Confirmed: inventory permanently lost ONE unit for nothing —
no order, no payment, no biryani made. Bug proven with real data.
```

### First Attempted Fix — @Transactional on processOrder()

```
Initial instinct: just add @Transactional directly on
processOrder(). Investigated WHY this wouldn't work BEFORE
even writing the code, by tracing thread execution:

processOrder() executes mostly INSIDE
CompletableFuture.supplyAsync(..., ioThreadPool) — meaning the
ACTUAL database calls happen on a DIFFERENT thread (from
ioThreadPool) than the thread that entered processOrder() itself.

@Transactional ties its transaction to the CURRENT THREAD via
a ThreadLocal. A transaction started on the calling thread does
NOT automatically transfer to a different thread picked up
later by the executor. This would have silently failed to
protect the actual DB calls.
```

### The Fix — Extract Transactional Logic Into Its Own Method

```java
// Called FROM INSIDE the lambda — runs on the SAME ioThreadPool
// thread, no further thread-switching between annotation and DB calls
@Transactional
public Order processOrderTransactionally(String userId,
                                         String restaurantId,
                                         String itemName,
                                         int quantity,
                                         double amount) {
    boolean inventoryAvailable = checkAndDeductInventory(userId, restaurantId, itemName, quantity);
    if (!inventoryAvailable) {
        throw new RuntimeException("INSUFFICIENT_INVENTORY");
    }
    return saveOrderToDB(userId, restaurantId, itemName, quantity, amount);
}
```

Also added: deleting the saved order if fraud is detected AFTER
the order was already saved (a previously-missed gap — inventory
was being restored on fraud, but the saved order row was never
removed).

### SECOND Discovery — Self-Invocation Bypasses the Proxy

```
Re-ran the forced-failure test (this time inside
processOrderTransactionally(), after inventory deduction):

if (true) { throw new RuntimeException("SIMULATED FAILURE AFTER INVENTORY DEDUCTED"); }
```

```sql
-- Before test: inventory.count = 5
-- After test:
SELECT * FROM inventory;  → count = 4   (STILL deducted — NOT rolled back!)
```

```
@Transactional did NOT roll back the change, even though it
was now correctly placed on the SAME thread. Root cause:

processOrder() calls processOrderTransactionally() as a DIRECT,
SAME-CLASS method call ("this.processOrderTransactionally(...)").

Spring's @Transactional ONLY activates when a method is invoked
THROUGH Spring's PROXY object — which only happens when the call
comes from OUTSIDE the bean (e.g., a DIFFERENT Spring-managed
class calling it via its injected reference). A direct,
same-class call completely BYPASSES the proxy, and the
annotation is SILENTLY ignored — no error, no warning.

This is one of the most well-known, common @Transactional
pitfalls in Spring applications.
```

### Two Standard Fix Options (Documented for Future Implementation)

```java
// Option A — Self-injection
@Autowired
private OrderService self;  // Spring injects a PROXY reference to itself
// Then call: self.processOrderTransactionally(...)

// Option B — Extract into a SEPARATE Spring-managed class (more standard)
@Service
public class InventoryTransactionService {
    @Transactional
    public Order processOrderTransactionally(...) { ...}
}
// OrderService autowires and calls THIS separate bean instead
```

### Status — Flagged as Known Gap, Revisit Later

```
The bug, the diagnosis, and BOTH fix options are now fully
understood and documented. The temporary sabotage line has been
removed. The @Transactional annotation remains in place on
processOrderTransactionally() (technically inactive due to the
self-invocation issue) as a placeholder/reminder.

This is intentionally left as a DOCUMENTED, KNOWN gap rather
than fully implemented right now, to keep learning momentum
moving forward. To revisit: apply Option A or B above.
```

### Key Lesson — Two Distinct, Separate @Transactional Pitfalls Discovered

```
Pitfall 1: @Transactional + manual async (CompletableFuture)
           → transaction tied to ThreadLocal, doesn't survive
           a thread switch

Pitfall 2: @Transactional + self-invocation (same-class call)
           → bypasses Spring's proxy entirely, silently does nothing

BOTH pitfalls fail SILENTLY — no exception, no warning, no log
message indicating @Transactional didn't work. This makes them
genuinely dangerous in real production code if not specifically
tested for, exactly as we just did ourselves.
```