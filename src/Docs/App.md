# SwiggyX — Capstone App

### Building everything we learned into a real working backend.

> Java 21 + Spring Boot + PostgreSQL
> Every concept from the learning journey — active, visible, running.

---

## What We Are Building

A Food Delivery Backend that actively uses:

| Feature                           | Concept Used                          |
|-----------------------------------|---------------------------------------|
| Handle 1000 orders simultaneously | Virtual Thread Pool                   |
| Two users ordering last item      | Race Condition + Mutex (synchronized) |
| DB connection limiting            | Semaphore                             |
| Payment + Inventory together      | Deadlock Prevention (Lock Ordering)   |
| Background notifications          | Message Queue simulation              |
| Fraud detection                   | Platform Thread Pool (CPU work)       |

---

## Tech Stack

```
Language:      Java 21
Framework:     Spring Boot 3.2.5
Database:      PostgreSQL 14
Build Tool:    Maven
IDE:           IntelliJ
OS:            Mac (Apple Silicon)
```

---

## Environment Setup

### PostgreSQL Setup

```
Database:  swiggyx
User:      swiggyx_user
Password:  swiggyx123
Port:      5432
```

Commands used to create:

```sql
CREATE DATABASE swiggyx;
CREATE USER swiggyx_user WITH PASSWORD 'swiggyx123';
GRANT ALL PRIVILEGES ON DATABASE swiggyx TO swiggyx_user;
```

Verified in PgAdmin:

```
Databases → swiggyx ✅
Login/Group Roles → swiggyx_user ✅
```

---

## Project Setup

### pom.xml — Explained

`pom.xml` is Maven's configuration file. Answers three questions:

```
1. What is this project?     → groupId, artifactId, version
2. What does it need?        → dependencies
3. How should it be built?   → build plugins
```

#### Project Identity

```xml

<groupId>com.swiggyx</groupId>      <!-- organisation name -->
<artifactId>swiggyx</artifactId>    <!-- project name -->
<version>1.0-SNAPSHOT</version>     <!-- first version, in development -->
```

#### Spring Boot Parent

```xml

<parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.2.5</version>
</parent>
```

Our project inherits from Spring Boot's parent.
Gives us:

```
→ All Spring Boot default configurations
→ Compatible versions of all libraries automatically
→ No need to specify versions manually
```

#### Java Version

```xml

<properties>
    <java.version>21</java.version>
</properties>
```

Java 21 — required for Virtual Threads.

#### Dependencies

**Spring Web** — receive HTTP requests

```xml

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>
```

Gives us:

```
→ HTTP server (Tomcat runs inside our app)
→ REST API support (@RestController, @GetMapping)
→ JSON conversion automatically
→ Without this — app can't receive HTTP requests
```

**Spring Data JPA** — talk to database

```xml

<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

JPA = Java Persistence API.
Gives us:

```
→ Talk to DB without writing SQL manually
→ Define Java class → JPA creates DB table automatically
→ Save, find, delete objects without SQL
→ Hibernate runs underneath

// Instead of SQL:
// SELECT * FROM orders WHERE id = 101
// You write:
orderRepository.findById(101); // JPA handles SQL
```

**PostgreSQL Driver** — USB cable between Java and PostgreSQL

```xml

<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

```
→ Actual connector between Java and PostgreSQL
→ scope=runtime: needed when running, not compiling
→ Without this — Java can't talk to PostgreSQL
```

**Lombok** — reduces boilerplate

```xml

<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>
```

```
Without Lombok:                    With Lombok:
public class Order {               @Data
    private String id;             public class Order {
    public String getId()...           private String id;
    public void setId()...             private String userId;
    public String toString()...    }
    // 50 lines of boilerplate
}
```

#### Build Plugin

```xml

<plugin>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-maven-plugin</artifactId>
</plugin>
```

```
→ mvn spring-boot:run → starts our server
→ Packages everything into one runnable JAR
→ Without this → can't run Spring Boot from Maven
```

#### Complete pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>com.swiggyx</groupId>
    <artifactId>swiggyx</artifactId>
    <version>1.0-SNAPSHOT</version>
    <packaging>jar</packaging>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.2.5</version>
        <relativePath/>
    </parent>

    <properties>
        <java.version>21</java.version>
        <maven.compiler.source>21</maven.compiler.source>
        <maven.compiler.target>21</maven.compiler.target>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <dependencies>

        <!-- Spring Web — REST APIs -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>

        <!-- Spring Data JPA — database operations -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>

        <!-- PostgreSQL Driver -->
        <dependency>
            <groupId>org.postgresql</groupId>
            <artifactId>postgresql</artifactId>
            <scope>runtime</scope>
        </dependency>

        <!-- Lombok — reduces boilerplate -->
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- Spring Boot Test -->
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>

    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>

</project>
```

---

*Document continues as we build...*

*Last updated: pom.xml setup*

---

## Java & Maven Setup

### Problem Faced

Maven was using Java 25 (Homebrew) instead of Java 17.
Fix: enable jenv maven plugin so jenv controls Maven's Java version.

```bash
jenv enable-plugin maven
source ~/.zshrc
```

### .zshrc — Permanent Fix

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-17.jdk/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
export PATH="$HOME/.jenv/bin:$PATH"
eval "$(jenv init -)"
```

### Verify Setup

```bash
java -version   → 17.0.7 ✅
mvn -version    → 17.0.7 ✅
```

---

## application.properties

Location: `src/main/resources/application.properties`

```properties
# Database connection
spring.datasource.url=jdbc:postgresql://localhost:5432/swiggyx
spring.datasource.username=swiggyx_user
spring.datasource.password=swiggyx123
spring.datasource.driver-class-name=org.postgresql.Driver
# JPA settings
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
# Server port
server.port=8080
```

What each line does:

```
datasource.url      → where PostgreSQL is running
                      localhost = our machine
                      5432 = PostgreSQL default port
                      swiggyx = our database

datasource.username → swiggyx_user (created earlier)
datasource.password → swiggyx123

ddl-auto=update     → JPA auto creates/updates DB tables
                      from our Java classes
                      no manual CREATE TABLE SQL needed

show-sql=true       → prints every SQL query in console
                      helps see what JPA is doing underneath

server.port=8080    → app runs on port 8080
```

---

## Running the App

```bash
cd ~/Desktop/HARDIK/JAVA-AGAIN-2025/Spring-Boot-2026/SwiggyX
mvn clean spring-boot:run
```

Success output:

```
Tomcat started on port 8080 ✅
Started SwiggyXApplication in 1.506 seconds ✅
```

Verify in browser: http://localhost:8080
"Whitelabel Error Page" = server running, no routes yet ✅

---

## Current Status

```
✅ PostgreSQL database created (swiggyx)
✅ User created (swiggyx_user)
✅ Spring Boot project setup
✅ Maven configured with Java 17 permanently
✅ Database connected via application.properties
✅ Server running on port 8080
```

---

## Project Structure (planned)

```
com.swiggyx
├── SwiggyXApplication.java     ← main entry point ✅
├── controller
│   └── OrderController.java    ← receives HTTP requests
├── service
│   └── OrderService.java       ← business logic, concurrency lives here
├── repository
│   └── OrderRepository.java    ← talks to database
├── model
│   └── Order.java              ← Order object, maps to DB table
└── config
    └── ThreadPoolConfig.java   ← thread pools (VT + PT)
```

---

*Document continues as we build...*
*Last updated: Server running successfully on port 8080*


---

## Step 1 — Order Model

### What is a Model?

A Java class that represents one row in a database table.
JPA reads the class and creates the table automatically — no SQL needed.

```
orders table in PostgreSQL:
┌────┬──────────┬───────────────┬────────┬─────────────────────┐
│ id │ user_id  │ restaurant_id │ status │ created_at          │
├────┼──────────┼───────────────┼────────┼─────────────────────┤
│  1 │ USER_101 │ REST_55       │ PLACED │ 2026-06-14 20:00:00 │
│  2 │ USER_202 │ REST_55       │ PLACED │ 2026-06-14 20:00:01 │
└────┴──────────┴───────────────┴────────┴─────────────────────┘

Each row = one Order object in Java
```

### What JPA Does

```
Without JPA — you write SQL:
CREATE TABLE orders (
    id SERIAL PRIMARY KEY,
    user_id VARCHAR(255),
    ...
);

With JPA — you write a Java class:
JPA reads it → creates the table automatically
No SQL needed
```

### Annotations Explained

```
@Entity              → "This class maps to a DB table"
@Table               → "The table name is orders"
@Id                  → "This field is the primary key"
@GeneratedValue      → "DB auto generates this value (1, 2, 3...)"
@Column              → "This field maps to a column in the table"
@Data                → Lombok: generates getters, setters, toString
@Builder             → Lombok: lets us build objects cleanly
@NoArgsConstructor   → Lombok: generates empty constructor
@AllArgsConstructor  → Lombok: generates constructor with all fields
```

### Order Fields

```
id            → unique identifier for each order
userId        → who placed the order
restaurantId  → which restaurant
itemName      → what they ordered
quantity      → how many
totalAmount   → how much it costs
status        → PLACED, CONFIRMED, DELIVERED, CANCELLED
createdAt     → when was it placed
```

### Note on Virtual Threads

```
Virtual Threads = Java 21 feature
We have Java 17 → cannot use Virtual Threads

Java 17 replacement:
IO work  → CompletableFuture + Fixed Thread Pool (cores × 2)
CPU work → Fixed Thread Pool (= num of cores)

All other concepts fully available:
✅ Race conditions  → synchronized
✅ Semaphore        → DB connection pool
✅ Deadlock         → lock ordering
✅ Starvation       → ReentrantLock(fair)
✅ Async flow       → CompletableFuture
✅ Thread pools     → ExecutorService

Upgrade to Java 21 later → change one line:
Executors.newFixedThreadPool(50)
→ Executors.newVirtualThreadPerTaskExecutor()
Everything else stays the same.
```

---

*Last updated: Order Model — concepts explained, ready to code*


---

## Order Model — Code

Location: `src/main/java/com/swiggyx/model/Order.java`

```java
package com.swiggyx.model;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "orders")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(name = "restaurant_id", nullable = false)
    private String restaurantId;

    @Column(name = "item_name", nullable = false)
    private String itemName;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "total_amount", nullable = false)
    private Double totalAmount;

    @Column(nullable = false)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
```

### What Each Part Does

**Class level annotations:**

```
@Entity              → tells JPA this class maps to a DB table
@Table(name="orders")→ table name is "orders"
                       (can't use "order" — reserved SQL word)
@Data                → Lombok generates getters, setters,
                       toString, equals, hashCode
@Builder             → lets us create objects cleanly:
                       Order.builder().userId("U_101").build()
@NoArgsConstructor   → empty constructor — JPA needs this
@AllArgsConstructor  → constructor with all fields — Builder needs this
```

**Fields:**

```
@Id                              → primary key
@GeneratedValue(IDENTITY)        → DB auto generates (1, 2, 3...)
id: Long                         → unique order identifier

@Column(name="user_id")
userId: String                   → who placed the order

@Column(name="restaurant_id")
restaurantId: String             → which restaurant

@Column(name="item_name")
itemName: String                 → what they ordered

quantity: Integer                → how many items
totalAmount: Double              → total cost
status: String                   → PLACED/CONFIRMED/DELIVERED/CANCELLED

@Column(name="created_at")
createdAt: LocalDateTime         → when order was placed
```

### JPA Generated SQL

```sql
create table orders (
    id bigserial not null,
    created_at timestamp(6),
    item_name varchar(255) not null,
    quantity integer not null,
    restaurant_id varchar(255) not null,
    status varchar(255) not null,
    total_amount float(53) not null,
    user_id varchar(255) not null,
    primary key (id)
)
```

Zero SQL written by us. JPA generated everything from the Java class. ✅

Verified in PgAdmin:

```
swiggyx database → Schemas → public → Tables → orders ✅
```

---

*Last updated: Order Model complete, table created in PostgreSQL*


---

## Step 2 — Order Repository

Location: `src/main/java/com/swiggyx/repository/OrderRepository.java`

### What is a Repository?

Instead of writing SQL manually:

```sql
INSERT INTO orders (...) VALUES (...)
SELECT * FROM orders WHERE id = 1
SELECT * FROM orders WHERE user_id = 'USER_101'
```

Spring Data JPA generates all SQL automatically.
You just create an interface extending JpaRepository.

### What JpaRepository gives for FREE

```
save(order)        → INSERT into orders table
findById(id)       → SELECT WHERE id = ?
findAll()          → SELECT all orders
delete(order)      → DELETE from orders
count()            → COUNT all orders
existsById(id)     → check if order exists

Zero SQL. Zero implementation. Spring generates everything.
```

### Code

```java
package com.swiggyx.repository;

import com.swiggyx.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // Spring generates: SELECT * FROM orders WHERE user_id = ?
    List<Order> findByUserId(String userId);

    // Spring generates: SELECT * FROM orders WHERE restaurant_id = ?
    List<Order> findByRestaurantId(String restaurantId);

    // Spring generates: SELECT * FROM orders WHERE status = ?
    List<Order> findByStatus(String status);
}
```

### Annotations Explained

```
@Repository
→ tells Spring: this is a data access component
→ Spring manages it, handles DB exceptions properly

extends JpaRepository<Order, Long>
→ Order = which entity we work with
→ Long  = type of primary key (our id field)
→ gives us all CRUD operations for free
```

### Magic of Method Naming

```
Spring reads method name → generates SQL automatically

findBy + FieldName    →  WHERE field_name = ?
findByUserId          →  WHERE user_id = ?
findByStatus          →  WHERE status = ?
findByRestaurantId    →  WHERE restaurant_id = ?

Combine fields:
findByUserIdAndStatus →  WHERE user_id = ? AND status = ?
```

### Why List and not ArrayList?

```
List      → interface (defines what operations are possible)
ArrayList → class (one implementation of List)

Returning List:
→ "I give you a collection of orders"
→ Spring chooses best implementation internally
→ flexible, not locked in

Returning ArrayList:
→ "I give you specifically an ArrayList"
→ if Spring returns LinkedList internally → error
→ rigid, locked in

Rule: Always program to the interface, not the implementation.
Use List not ArrayList.
Use Map not HashMap.
Use Set not HashSet.
```

---

*Last updated: Order Repository complete*


---

## Step 3 — Thread Pool Config

Location: `src/main/java/com/swiggyx/config/ThreadPoolConfig.java`

### What is a @Configuration class?

Creates shared objects once at startup.
Makes them available everywhere in the application.
Spring calls all @Bean methods at startup — once.

### Code

```java
package com.swiggyx.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

@Configuration
public class ThreadPoolConfig {

    // Number of CPU cores on this machine
    private static final int CPU_CORES =
        Runtime.getRuntime().availableProcessors();

    // IO Thread Pool — cores × 2
    // threads mostly waiting for IO, can have more than cores
    @Bean(name = "ioThreadPool")
    public ExecutorService ioThreadPool() {
        System.out.println("Creating IO Thread Pool with "
            + (CPU_CORES * 2) + " threads");
        return Executors.newFixedThreadPool(CPU_CORES * 2);
    }

    // CPU Thread Pool — exactly num of cores
    // heavy calculation, cache stays hot, no over-switching
    @Bean(name = "cpuThreadPool")
    public ExecutorService cpuThreadPool() {
        System.out.println("Creating CPU Thread Pool with "
            + CPU_CORES + " threads");
        return Executors.newFixedThreadPool(CPU_CORES);
    }

    // DB Connection Semaphore — max 20 simultaneous DB connections
    @Bean(name = "dbSemaphore")
    public Semaphore dbSemaphore() {
        System.out.println("Creating DB Semaphore with 20 permits");
        return new Semaphore(20);
    }
}
```

### Annotations Explained

```
@Configuration
→ tells Spring: this class creates shared objects
→ Spring runs this at startup
→ all @Bean methods called once

@Bean(name = "ioThreadPool")
→ creates ONE ExecutorService object
→ stored with name "ioThreadPool"
→ any class can ask Spring for it
→ Spring gives SAME object every time (singleton)

Runtime.getRuntime().availableProcessors()
→ asks OS: how many CPU cores?
→ pool sizes calculated dynamically
→ works on any machine automatically
```

### Why @Bean and not just new?

```
Without Spring:
OrderService:   ExecutorService pool = Executors.newFixedThreadPool(8);
PaymentService: ExecutorService pool = Executors.newFixedThreadPool(8);
FraudService:   ExecutorService pool = Executors.newFixedThreadPool(8);
Result: 3 pools, 24 threads, wasted resources

With Spring @Bean:
ONE pool created once, shared everywhere
OrderService, PaymentService, FraudService → same object
Result: 1 pool, 8 threads, efficient
```

### Output on Startup (8-core Mac)

```
Creating IO Thread Pool with 16 threads   (8 cores × 2) ✅
Creating CPU Thread Pool with 8 threads   (= cores) ✅
Creating DB Semaphore with 20 permits     ✅
```

### Concepts Active Here

```
IO Pool (16 threads)  → Concept 4 (Thread Pool)
                         Concept 18 (IO → more threads fine)
CPU Pool (8 threads)  → Concept 18 (CPU → threads = cores)
                         Concept 3 (cache stays hot, no over-switching)
Semaphore (20)        → Concept 8 (DB connection limiting)
```

---

*Last updated: Thread Pool Config complete, all pools running*


---

## Step 4 — Order Service

Location: `src/main/java/com/swiggyx/service/OrderService.java`

### What is a Service?

```
Controller  → receives HTTP request → calls Service
Service     → ALL business logic lives here ← this is it
Repository  → talks to DB
```

### What OrderService does — Full Flow

```
Step 1 — Validate order        → CPU work, instant, no locks
Step 2 — Fraud detection       → CPU intensive, runs on CPU pool in background
Step 3 — Check inventory       → synchronized with ReentrantLock(fair)
Step 4 — Wait for fraud result → fraudFuture.get()
Step 5 — Save to DB            → Semaphore controls max 20 connections
Step 6 — Charge payment        → ReentrantLock(fair), lock ordering
Step 7 — Send notification     → fire and forget, background
```

### Why ReentrantLock instead of synchronized?

```
synchronized:
→ simpler to write
→ no fairness guarantee
→ threads compete randomly → starvation possible

ReentrantLock(true):
→ fair = true → strict FIFO ordering
→ threads served in order they arrived
→ no starvation (Concept 10)
→ requires manual lock/unlock in try/finally
```

### Lock Ordering — Deadlock Prevention

```
Always acquire in this order:
1. inventoryLock FIRST
2. paymentLock SECOND

Never reversed. Deadlock impossible.
(Concept 9 — circular wait prevented)
```

### Lombok Fix

```
IntelliJ Community Edition doesn't show Lombok generated methods.
Fix: Install Lombok plugin
Preferences → Plugins → Marketplace → search "Lombok" → Install → Restart
```

### Complete Code

```java
package com.swiggyx.service;

import com.swiggyx.model.Order;
import com.swiggyx.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class OrderService {

    // Lock for inventory — always acquire FIRST (lock ordering)
    private final ReentrantLock inventoryLock = new ReentrantLock(true);

    // Lock for payment — always acquire SECOND (lock ordering)
    private final ReentrantLock paymentLock = new ReentrantLock(true);

    // Spring injects automatically
    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    @Qualifier("ioThreadPool")
    private ExecutorService ioThreadPool;

    @Autowired
    @Qualifier("cpuThreadPool")
    private ExecutorService cpuThreadPool;

    @Autowired
    @Qualifier("dbSemaphore")
    private Semaphore dbSemaphore;

    // Shared inventory — race condition risk
    // ALL threads can read/write this → needs protection
    private int inventory = 10;

    // Step 1 — Validate order
    // Parameters live on STACK → thread private → no lock needed
    private boolean validateOrder(String userId,
                                  String itemName,
                                  int quantity,
                                  double amount) {
        if (userId == null || userId.isEmpty()) {
            System.out.println("Invalid userId");
            return false;
        }
        if (itemName == null || itemName.isEmpty()) {
            System.out.println("Invalid itemName");
            return false;
        }
        if (quantity <= 0) {
            System.out.println("Invalid quantity");
            return false;
        }
        if (amount <= 0) {
            System.out.println("Invalid amount");
            return false;
        }
        return true;
    }

    // Step 2 — Fraud Detection
    // CPU intensive — runs on CPU thread pool
    // Returns CompletableFuture immediately — runs in background
    private CompletableFuture<Boolean> checkFraud(String userId,
                                                   double amount) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("Fraud check started for: " + userId
                + " on thread: " + Thread.currentThread().getName());

            try {
                Thread.sleep(200); // simulate ML model — 200ms CPU work
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            boolean isFraud = amount > 10000; // simple rule: >10000 = fraud

            System.out.println("Fraud check done for: " + userId
                + " isFraud: " + isFraud);

            return isFraud;

        }, cpuThreadPool); // runs on CPU pool — NOT IO pool
    }

    // Step 3 — Check and Deduct Inventory
    // CRITICAL SECTION — race condition protection
    // ReentrantLock(fair) — no starvation
    // inventoryLock acquired FIRST — lock ordering
    private boolean checkAndDeductInventory(String userId, int quantity) {
        inventoryLock.lock();
        try {
            System.out.println("Inventory check for: " + userId
                + " | Current: " + inventory
                + " | Thread: " + Thread.currentThread().getName());

            if (inventory < quantity) {
                System.out.println("Insufficient inventory for: " + userId);
                return false;
            }

            inventory = inventory - quantity; // protected LOAD-MODIFY-STORE
            System.out.println("Inventory deducted for: " + userId
                + " | Remaining: " + inventory);
            return true;

        } finally {
            inventoryLock.unlock(); // ALWAYS unlock in finally
        }
    }

    // Step 4 — Save Order to DB
    // Semaphore controls max 20 simultaneous DB connections
    private Order saveOrderToDB(String userId,
                                String restaurantId,
                                String itemName,
                                int quantity,
                                double amount) {
        try {
            dbSemaphore.acquire(); // blocks if 20 threads already in DB
            System.out.println("DB connection acquired for: " + userId
                + " | Thread: " + Thread.currentThread().getName());

            Order order = Order.builder()
                    .userId(userId)
                    .restaurantId(restaurantId)
                    .itemName(itemName)
                    .quantity(quantity)
                    .totalAmount(amount)
                    .status("PLACED")
                    .createdAt(LocalDateTime.now())
                    .build();

            Order savedOrder = orderRepository.save(order);
            System.out.println("Order saved: " + savedOrder.getId()
                + " for: " + userId);

            return savedOrder;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted waiting for DB connection");
        } finally {
            dbSemaphore.release(); // ALWAYS release in finally
            System.out.println("DB connection released for: " + userId);
        }
    }

    // Main method — connects all steps
    // Called by Controller
    public CompletableFuture<String> processOrder(String userId,
                                                   String restaurantId,
                                                   String itemName,
                                                   int quantity,
                                                   double amount) {

        return CompletableFuture.supplyAsync(() -> {

            System.out.println("Order started for: " + userId
                + " | Thread: " + Thread.currentThread().getName());

            // Step 1 — Validate
            if (!validateOrder(userId, itemName, quantity, amount)) {
                return "FAILED: Invalid order details";
            }

            // Step 2 — Start fraud check in background (CPU pool)
            CompletableFuture<Boolean> fraudFuture =
                checkFraud(userId, amount);

            // Step 3 — Check inventory (inventoryLock — FIRST lock)
            boolean inventoryAvailable =
                checkAndDeductInventory(userId, quantity);

            if (!inventoryAvailable) {
                return "FAILED: Item not available";
            }

            // Step 4 — Get fraud result (fraud ran in parallel with inventory check)
            try {
                boolean isFraud = fraudFuture.get();
                if (isFraud) {
                    // Restore inventory — order blocked
                    inventoryLock.lock();
                    try {
                        inventory = inventory + quantity;
                        System.out.println("Inventory restored — fraud: " + userId);
                    } finally {
                        inventoryLock.unlock();
                    }
                    return "FAILED: Fraud detected";
                }
            } catch (Exception e) {
                return "FAILED: Fraud check error";
            }

            // Step 5 — Save to DB (Semaphore limits to 20 connections)
            Order savedOrder = saveOrderToDB(userId, restaurantId,
                itemName, quantity, amount);

            // Step 6 — Payment (paymentLock — SECOND lock, always after inventoryLock)
            paymentLock.lock();
            try {
                System.out.println("Payment processing for: " + userId
                    + " | Amount: " + amount);
                Thread.sleep(30); // simulate payment gateway
                System.out.println("Payment done for: " + userId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                paymentLock.unlock();
            }

            // Step 7 — Notify (fire and forget — user doesn't wait)
            CompletableFuture.runAsync(() -> {
                System.out.println("Notification sent for order: "
                    + savedOrder.getId());
            }, ioThreadPool);

            return "SUCCESS: Order placed. ID: " + savedOrder.getId();

        }, ioThreadPool); // entire flow runs on IO thread pool
    }
}
```

### Concepts Active in OrderService

```
Concept 5  → Race condition: inventory protected by ReentrantLock
Concept 7  → Mutex: ReentrantLock(fair) on inventory and payment
Concept 8  → Semaphore: dbSemaphore(20) limits DB connections
Concept 9  → Deadlock: inventoryLock always before paymentLock
Concept 10 → Starvation: ReentrantLock(true) = fair = FIFO ordering
Concept 12 → Futures: CompletableFuture for fraud check
Concept 13 → Async: fraud check runs in background while inventory checked
Concept 18 → Threads vs Async: IO pool for order flow, CPU pool for fraud
```

### Parallel Execution Timeline

```
Time 0ms:   processOrder() starts on IO thread pool
Time 1ms:   validateOrder() — instant
Time 2ms:   checkFraud() STARTS on CPU pool (background)
Time 3ms:   checkAndDeductInventory() — instant (with lock)
Time 203ms: fraudFuture.get() — fraud check done by now
Time 223ms: saveOrderToDB() — DB call with semaphore
Time 253ms: paymentLock acquired — payment processed
Time 253ms: notification fired (background, fire and forget)
Time 253ms: return SUCCESS
```

### App Startup Output

```
Creating IO Thread Pool with 16 threads   (8 cores × 2) ✅
Creating CPU Thread Pool with 8 threads   (= cores) ✅
Creating DB Semaphore with 20 permits     ✅
Started SwiggyXApplication in 1.953s     ✅
HikariPool connected to PostgreSQL        ✅
```

---

## Real World Production Patterns

> See swiggyx_concurrency.md for full details.
> This section summarizes what we will implement after the learning version.

### What We Built (Learning) vs What Production Uses

```
Problem              Our Code                Production
───────────────────  ──────────────────────  ──────────────────────────
Race condition       ReentrantLock           @Version optimistic locking
Atomicity            Manual lock ordering    @Transactional
Scale                Direct processing       Kafka message queue
Distributed lock     ReentrantLock (broken!) Redis distributed lock
DB connections       Semaphore(20)           HikariCP (already in app!)
CPU work             In-process thread pool  Separate Python microservice
```

### Production Implementation Plan

```
Step 1 → Add @Version to Inventory entity (optimistic locking)
Step 2 → Add @Transactional to OrderService methods
Step 3 → Add Redis for distributed locking
Step 4 → Add Kafka for order processing queue
Step 5 → Configure HikariCP properly in application.properties
Step 6 → Extract fraud detection to separate service
```

---

## Step 5 — Order Controller

Location: `src/main/java/com/swiggyx/controller/OrderController.java`

### What is a Controller?

```
HTTP Request arrives
        ↓
Controller receives it
        ↓
Controller calls Service
        ↓
Service does all the work
        ↓
Controller returns response

Controller has ONE job:
Receive → extract data → call service → return response
No business logic here. Ever. Business logic lives in Service.
```

### Annotations Explained

```
@RestController
→ tells Spring: this class handles HTTP requests
→ every method returns data directly as JSON
→ no HTML templates

@RequestMapping("/api/orders")
→ all endpoints start with /api/orders

@PostMapping("/place")
→ handles POST requests to /api/orders/place
→ POST = creating something new

@GetMapping("/{id}")
→ handles GET requests to /api/orders/1

@RequestParam
→ reads from URL query parameters
→ /api/orders/place?userId=U1&amount=450

@PathVariable
→ reads from URL path
→ /api/orders/101 → id = 101

ResponseEntity
→ wraps response with HTTP status code
→ 200 OK, 404 Not Found, 500 Error
```

### Method name vs URL

```
Method name → for humans reading the code (semantic only)
URL path    → defined by annotations, used by Spring and callers

@PostMapping("/place")
public ResponseEntity<String> placeOrder(...)
                              ↑
                              this name means nothing to Spring
                              /place is what matters
```

### Understanding Key Code Patterns

**findById with Optional:**

```java
orderRepository.findById(id)
    .

map(ResponseEntity::ok)           // if found → 200 with order
    .

orElse(ResponseEntity.notFound()  // if not found → 404
        .

build());

Optional =
safe way
to handle "might not exist"
        =
box that
either has
Order inside
or is empty
        .

map()   =
transform value if present
        .

orElse()=fallback if empty
```

**ResponseEntity status codes:**

```java
ResponseEntity.ok(response)              // HTTP 200 — success
ResponseEntity.notFound().build()        // HTTP 404 — not found
ResponseEntity.internalServerError()     // HTTP 500 — server error
    .body("Error: " + e.getMessage())
```

**@RequestParam vs @PathVariable:**

```
@PathVariable  → identifying specific resource
               /orders/101 (which order?)
               /users/USER_101 (which user?)

@RequestParam  → providing data
               /orders/place?userId=U1&amount=450
```

### Complete Code

```java
package com.swiggyx.controller;

import com.swiggyx.model.Order;
import com.swiggyx.repository.OrderRepository;
import com.swiggyx.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private OrderRepository orderRepository;

    // POST /api/orders/place
    @PostMapping("/place")
    public ResponseEntity<String> placeOrder(
            @RequestParam String userId,
            @RequestParam String restaurantId,
            @RequestParam String itemName,
            @RequestParam int quantity,
            @RequestParam double amount) {

        try {
            CompletableFuture<String> result =
                orderService.processOrder(
                    userId, restaurantId, itemName, quantity, amount);

            String response = result.get(); // wait for result
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity
                .internalServerError()
                .body("Error: " + e.getMessage());
        }
    }

    // GET /api/orders/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable Long id) {
        return orderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // GET /api/orders/user/{userId}
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Order>> getUserOrders(
            @PathVariable String userId) {
        List<Order> orders = orderRepository.findByUserId(userId);
        return ResponseEntity.ok(orders);
    }

    // GET /api/orders/status
    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        long totalOrders = orderRepository.count();
        return ResponseEntity.ok(
            "SwiggyX running. Total orders: " + totalOrders);
    }
}
```

### All Endpoints

```
POST /api/orders/place?userId=X&restaurantId=Y&itemName=Z&quantity=1&amount=450
→ places new order
→ returns: "SUCCESS: Order placed. ID: 1" or "FAILED: ..."

GET /api/orders/1
→ gets order with ID 1
→ returns: order JSON or 404

GET /api/orders/user/USER_101
→ gets all orders for USER_101
→ returns: list of orders as JSON

GET /api/orders/status
→ health check
→ returns: "SwiggyX running. Total orders: X"
```

---

## Testing — All Results with Logs

### Test 1 — Health Check

```bash
curl http://localhost:8080/api/orders/status
```

Response:

```
SwiggyX running. Total orders: 0
```

### Test 2 — Single Order

```bash
curl -X POST "http://localhost:8080/api/orders/place?userId=USER_101&restaurantId=REST_55&itemName=Biryani&quantity=1&amount=450"
```

Response:

```
SUCCESS: Order placed. ID: 1
```

Server logs:

```
Hibernate: select count(*) from orders o1_0
Order started for: USER_101 | Thread: pool-2-thread-1
Fraud check started for: USER_101 on thread: pool-3-thread-1
Inventory check for: USER_101 | Current inventory: 10 | Thread: pool-2-thread-1
Inventory deducted for: USER_101 | Remaining: 9
Fraud check done for: USER_101 isFraud: false
DB connection acquired for: USER_101 | Thread: pool-2-thread-1
Hibernate: insert into orders (created_at,item_name,quantity,restaurant_id,status,total_amount,user_id) values (?,?,?,?,?,?,?)
Order saved to DB: 1 for: USER_101
DB connection released for: USER_101
Payment processing for: USER_101 | Amount: 450.0
Payment done for: USER_101
Notification sent for order: 1
```

What each log line proves:

```
pool-2-thread-1      → Concept 4: IO thread pool handling request
pool-3-thread-1      → Concept 18: CPU thread pool for fraud (separate)
Fraud + Inventory    → Concept 13: running in parallel simultaneously
Inventory deducted   → Concept 7: mutex protected, no race condition
DB acquired/released → Concept 8: Semaphore controlling DB access
Payment after DB     → Concept 9: lock ordering (inventory → payment)
Notification sent    → Concept 13: fire and forget, background
```

### Test 3 — Two Simultaneous Orders (Race Condition Test)

```bash
curl -X POST "http://localhost:8080/api/orders/place?userId=USER_102&restaurantId=REST_55&itemName=Biryani&quantity=1&amount=450" & curl -X POST "http://localhost:8080/api/orders/place?userId=USER_103&restaurantId=REST_55&itemName=Biryani&quantity=1&amount=450" &
```

Server logs:

```
Order started for: USER_103 | Thread: pool-2-thread-4
Order started for: USER_102 | Thread: pool-2-thread-3
Fraud check started for: USER_103 on thread: pool-3-thread-2
Fraud check started for: USER_102 on thread: pool-3-thread-3
Inventory check for: USER_103 | Current inventory: 9 | Thread: pool-2-thread-4
Inventory deducted for: USER_103 | Remaining: 8
Inventory check for: USER_102 | Current inventory: 8 | Thread: pool-2-thread-3
Inventory deducted for: USER_102 | Remaining: 7
```

What this proves:

```
Two threads started simultaneously ✅
Two fraud checks on CPU pool simultaneously ✅
Inventory never accessed by two threads at same time ✅
USER_103 checked first → saw 9 → deducted → became 8
USER_102 checked next  → saw 8 → deducted → became 7
Never both saw 9. Never went to -1. ReentrantLock worked. ✅
```

### Test 4 — 10 Simultaneous Orders (Scale Test)

```bash
for i in {1..10}; do curl -X POST "http://localhost:8080/api/orders/place?userId=USER_$i&restaurantId=REST_55&itemName=Biryani&quantity=1&amount=450" & done
```

Server logs:

```
Order started for: USER_2  | Thread: pool-2-thread-5
Order started for: USER_9  | Thread: pool-2-thread-4
Order started for: USER_10 | Thread: pool-2-thread-10
Order started for: USER_3  | Thread: pool-2-thread-7
Order started for: USER_4  | Thread: pool-2-thread-2
Order started for: USER_7  | Thread: pool-2-thread-8
Order started for: USER_6  | Thread: pool-2-thread-1
Order started for: USER_5  | Thread: pool-2-thread-9
Order started for: USER_8  | Thread: pool-2-thread-3
Order started for: USER_1  | Thread: pool-2-thread-6

Fraud check started for: USER_5  on thread: pool-3-thread-2
Fraud check started for: USER_7  on thread: pool-3-thread-1
Fraud check started for: USER_10 on thread: pool-3-thread-3
Fraud check started for: USER_6  on thread: pool-3-thread-4
Fraud check started for: USER_4  on thread: pool-3-thread-5
Fraud check started for: USER_9  on thread: pool-3-thread-6
Fraud check started for: USER_2  on thread: pool-3-thread-7
Fraud check started for: USER_1  on thread: pool-3-thread-8
← USER_3 and USER_8 waited — CPU pool full (8 = num of cores)

Inventory check for: USER_5  | Current: 10 → Remaining: 9
Inventory check for: USER_3  | Current: 9  → Remaining: 8
Inventory check for: USER_8  | Current: 8  → Remaining: 7
Inventory check for: USER_7  | Current: 7  → Remaining: 6
Inventory check for: USER_10 | Current: 6  → Remaining: 5
Inventory check for: USER_6  | Current: 5  → Remaining: 4
Inventory check for: USER_4  | Current: 4  → Remaining: 3
Inventory check for: USER_9  | Current: 3  → Remaining: 2
Inventory check for: USER_2  | Current: 2  → Remaining: 1
Inventory check for: USER_1  | Current: 1  → Remaining: 0

Order saved to DB: 4  for: USER_2
Order saved to DB: 5  for: USER_4
Order saved to DB: 6  for: USER_7
Order saved to DB: 7  for: USER_1
Order saved to DB: 8  for: USER_10
Order saved to DB: 9  for: USER_5
Order saved to DB: 10 for: USER_9
Order saved to DB: 11 for: USER_6
Order saved to DB: 12 for: USER_8
Order saved to DB: 13 for: USER_3
```

What this proves:

```
10 threads simultaneously              → Concept 4: Thread Pool ✅
8 fraud checks simultaneously          → Concept 18: CPU pool = cores ✅
USER_3, USER_8 waited for CPU pool     → Concept 4: pool limiting ✅
Inventory: 10→9→8→7→6→5→4→3→2→1→0    → Concept 7: mutex perfect ✅
Never negative. Never two threads same time → Concept 5: no race condition ✅
All 10 orders saved to DB              → Concept 8: Semaphore worked ✅
All notifications background           → Concept 13: fire and forget ✅
```

### Test 5 — Sold Out Test

```bash
curl -X POST "http://localhost:8080/api/orders/place?userId=USER_999&restaurantId=REST_55&itemName=Biryani&quantity=1&amount=450"
```

Response:

```
FAILED: Item not available
```

Server logs:

```
Order started for: USER_999 | Thread: pool-2-thread-5
Fraud check started for: USER_999 on thread: pool-3-thread-5
Inventory check for: USER_999 | Current inventory: 0 | Thread: pool-2-thread-5
Insufficient inventory for: USER_999
Fraud check done for: USER_999 isFraud: false
```

What this proves:

```
inventory = 0 correctly detected ✅
ReentrantLock prevented any negative inventory ✅
Fraud check ran in background (doesn't matter — already failed) ✅
User got clear "Item not available" message ✅
```

### Complete Test Summary

```
Test 1 — Health check:        SwiggyX running ✅
Test 2 — Single order:        SUCCESS, saved to DB ✅
Test 3 — Two simultaneous:    Both succeeded, inventory correct ✅
Test 4 — 10 simultaneous:     All succeeded, inventory 10→0 ✅
Test 5 — Sold out:            FAILED: Item not available ✅

Every concept from 21 lessons — working in real running code.
```

### Verify in PostgreSQL

```sql
SELECT * FROM orders;
-- Shows all 13 orders saved correctly
```

### Note — Inventory Resets on Restart

```
inventory lives in memory: private int inventory = 10;
Server restart → resets to 10
Production fix → move inventory to DB table (Phase 2, Step 3)
```

---

## Code Deep Dive — Questions & Answers

> Every question asked while reading the code. Documented for future reference.

---

### Q1 — Why start fraud check before inventory check?

```java
// Step 2 — Fraud detection starts in background
CompletableFuture<Boolean> fraudFuture = checkFraud(userId, amount);

// Step 3 — Inventory check happens simultaneously
boolean inventoryAvailable = checkAndDeductInventory(userId, quantity);
```

**Answer — Parallel execution saves time:**

```
Without parallel:
0ms   → inventory check → 1ms
1ms   → fraud check     → 200ms
Total: 201ms

With parallel:
0ms   → fraud check STARTS (background, 200ms)
0ms   → inventory check    (1ms, done instantly)
200ms → fraudFuture.get()  (fraud done by now)
Total: 200ms

Saved: ~1ms per order
At 10,000 orders simultaneously: 10 seconds saved
```

**But what if fraud IS detected after inventory deducted?**

```java
// We RESTORE inventory if fraud detected
if(isFraud){
        inventoryLock.

lock();
    try{
inventory =inventory +quantity;  // give it back
    }finally{
            inventoryLock.

unlock();
    }
            return"FAILED: Fraud detected";
            }
```

---

### Q2 — Every inventory operation must acquire inventoryLock

```
Rule: Any code that reads OR writes inventory
      MUST acquire inventoryLock first. No exceptions.

Touch 1 → checkAndDeductInventory() → inventoryLock.lock()
Touch 2 → restore on fraud          → inventoryLock.lock()

One bypass = protection broken.
Even a READ without lock is dangerous:
→ might read half-updated value
→ logs show wrong inventory
→ support team confused
```

---

### Q3 — Inventory lives on HEAP, not DATA segment

```java
@Service
public class OrderService {
    private int inventory = 10;  // where does this live?
}
```

```
DATA segment → static variables only
               static int inventory = 10  ← would be here

Stack        → local variables inside methods
               void method() { int x = 10; }  ← would be here

HEAP         → instance variables of objects ← OUR CASE
               Spring creates new OrderService() on heap
               inventory lives inside that object on heap

HEAP
┌─────────────────────────────────┐
│  OrderService object            │
│  ┌───────────────────────────┐  │
│  │ inventory = 10            │  │  ← here
│  │ inventoryLock = ...       │  │
│  │ paymentLock = ...         │  │
│  └───────────────────────────┘  │
└─────────────────────────────────┘

All threads share same OrderService object on heap
All threads can read/write inventory simultaneously
That's exactly why we need the lock
```

---

### Q4 — Locks across files/packages is a real risk

**The concern:**

```
Today: inventory + payment locks both in OrderService.java
→ easy to see both, easy to enforce ordering

Tomorrow: split into separate files:
InventoryService.java → has inventoryLock
PaymentService.java   → has paymentLock

Developer A modifies InventoryService — doesn't see paymentLock
Developer B modifies PaymentService  — doesn't see inventoryLock
Lock ordering rule only in documentation
Easy to forget → DEADLOCK in production
```

**Why @Transactional solves this:**

```java
// InventoryService.java
@Transactional
public void deductInventory(...) {
    // DB row-level lock — DB decides ordering internally
}

// PaymentService.java
@Transactional
public void chargePayment(...) {
    // DB row-level lock — DB decides ordering internally
}

// OrderService.java
@Transactional
public void processOrder(...) {
    inventoryService.deductInventory(...);
    paymentService.chargePayment(...);
    // DB ensures consistency across both
    // DB detects deadlock and retries automatically
    // No manual lock ordering needed
    // Works regardless of how many files are involved
}
```

> **This is the #1 reason @Transactional exists — DB enforces ordering regardless of how many files/services touch the
data.**

---

### Q5 — Is payment service a DB level thing?

**Answer — Payment has TWO parts:**

```
Part 1 → External API call (Razorpay/Stripe)
         actual money movement
         NOT a DB operation
         @Transactional has NO effect here
         needs: retries, timeouts, circuit breaker

Part 2 → Recording payment result in OUR DB
         INSERT INTO payments (...)
         UPDATE orders SET status = 'PAID'
         THIS is a DB operation
         @Transactional protects this
```

**Critical rule — never hold DB transaction during external API call:**

```java
// WRONG — holds DB connection for 2 seconds
@Transactional
public void badExample() {
    razorpayClient.charge(amount);  // 2 seconds — DB connection held!
    orderRepository.save(order);
}

// RIGHT — external call outside @Transactional
public void processPayment() {
    // Step 1: external call (outside @Transactional)
    PaymentResult result = razorpayClient.charge(amount);

    // Step 2: save result in DB (inside @Transactional)
    savePaymentRecord(result);  // fast, DB connection held briefly
}

@Transactional
public void savePaymentRecord(PaymentResult result) {
    paymentRepository.save(result);
    order.setStatus("PAID");
    orderRepository.save(order);
}
```

---

### Q6 — Is razorpayClient.charge() blocking?

```java
PaymentResult result = razorpayClient.charge(order.getAmount());
```

**Answer — Yes, blocking by default:**

```
This is a synchronous HTTP call.
Thread waits 1-3 seconds for Razorpay to respond.
Exactly like a DB call — IO problem (Concept 11).
Thread sitting idle, wasting 1MB stack.

Fix — wrap in CompletableFuture:
CompletableFuture<PaymentResult> resultFuture =
    CompletableFuture.supplyAsync(() ->
        razorpayClient.charge(amount), ioThreadPool);
// thread freed during 1-3 second wait
// resumes when Razorpay responds
```

---

### Q7 — What does }, ioThreadPool) mean at end of CompletableFuture?

```java
CompletableFuture.supplyAsync(() -> {
    // anonymous function body
    return someValue;
}, ioThreadPool);
//  ↑
//  second argument = WHERE to run this lambda
```

```
supplyAsync(Supplier, Executor)
            ↑          ↑
            WHAT       WHERE

Without second argument:
→ runs on Java's default ForkJoinPool.commonPool()
→ NOT our custom pool
→ wrong thread count, competes with JVM internals

With ioThreadPool specified:
→ runs on our 16-thread IO pool

With cpuThreadPool specified:
→ runs on our 8-thread CPU pool

Proof from our logs:
Order started    → pool-2-thread-1  (pool-2 = ioThreadPool)
Fraud check      → pool-3-thread-1  (pool-3 = cpuThreadPool)
Thread names prove work ran on correct pool ✅
```

---

### Q8 — findById not in OrderRepository, where does it come from?

```java
public interface OrderRepository extends JpaRepository<Order, Long> {
    List<Order> findByUserId(String userId);      // we wrote this
    List<Order> findByRestaurantId(String id);    // we wrote this
    List<Order> findByStatus(String status);      // we wrote this
}
```

**Answer — inherited from JpaRepository:**

```
JpaRepository gives these for FREE:
→ findById(id)      ← this one, from JpaRepository
→ save(entity)
→ findAll()
→ delete(entity)
→ count()
→ existsById(id)

We only write CUSTOM methods not already in JpaRepository.
Everything else inherited automatically.

Like Java class inheritance:
JpaRepository has findById() already written by Spring
Our OrderRepository extends it → gets all methods for free
We only add our specific custom queries on top
```

---

### Q9 — Inventory in RAM is a real production problem

```
Problem 1 — Restart resets inventory:
Server restarts → inventory = 10 again
Even if 9 orders placed → looks like 10 available

Problem 2 — Multiple servers break everything:
Server 1: inventory = 7 (in its RAM)
Server 2: inventory = 9 (in its RAM — separate!)
ReentrantLock only works within ONE server
Race condition across servers — lock useless

Problem 3 — No history or audit trail

Fix in Phase 2 Step 3:
→ Move inventory to DB table
→ All servers read from same DB (single source of truth)
→ @Version for optimistic locking
→ Survives restarts, works across servers
```

---

### Q10 — What happens when more requests arrive than CPU pool threads?

**The scenario:**

```
8 CPU threads in cpuThreadPool
10 simultaneous orders → 10 calls to checkFraud()
```

**Answer — Fixed thread pool has an internal queue:**

```java
Executors.newFixedThreadPool(CPU_CORES); // 8 threads
```

```
Request 1-8  → immediately get a thread → start running
Request 9    → no thread free → goes into INTERNAL QUEUE → waits
Request 10   → no thread free → goes into INTERNAL QUEUE → waits

When Thread 1 finishes (e.g. USER_3's fraud check) →
→ becomes free → picks up Request 9 from queue → starts running
```

This is exactly Concept 4 — Thread Pool — in action:

```
Thread Pool
┌─────────────────────────────────┐
│  Thread 1 ── RUNNING            │
│  ...                            │
│  Thread 8 ── RUNNING            │
│  Task Queue: [Req9, Req10]      │  ← waiting here
└─────────────────────────────────┘
```

**What the calling code experiences:**

```java
CompletableFuture<Boolean> fraudFuture = checkFraud(userId, amount);
// returns IMMEDIATELY — even if fraud check hasn't STARTED yet
// (it might just be sitting in cpuThreadPool's internal queue)

boolean isFraud = fraudFuture.get();
// THIS is where actual waiting happens, only if result isn't ready yet
```

**Proof from our own 10-order test logs:**

```
Initial batch — exactly 8 "Fraud check started" lines:
Fraud check started for: USER_5  on thread: pool-3-thread-2
Fraud check started for: USER_7  on thread: pool-3-thread-1
Fraud check started for: USER_10 on thread: pool-3-thread-3
Fraud check started for: USER_6  on thread: pool-3-thread-4
Fraud check started for: USER_4  on thread: pool-3-thread-5
Fraud check started for: USER_9  on thread: pool-3-thread-6
Fraud check started for: USER_2  on thread: pool-3-thread-7
Fraud check started for: USER_1  on thread: pool-3-thread-8

USER_3 and USER_8 MISSING from initial batch — queued!

Later in same logs:
Fraud check started for: USER_3 on thread: pool-3-thread-2  ← reused thread
Fraud check started for: USER_8 on thread: pool-3-thread-4  ← reused thread

These started ONLY after threads 2 and 4 finished their first job.
This is the internal queue — proven with our own test data.
```

**Production consideration — unbounded queue risk:**

```java
// Default newFixedThreadPool — UNBOUNDED queue
// can grow infinitely if requests arrive faster than processing
// risk: memory grows unbounded at extreme scale

// Production fix — bounded queue with rejection policy
new ThreadPoolExecutor(
    8, 8,                          // core and max threads
    0L, TimeUnit.MILLISECONDS,
    new LinkedBlockingQueue<>(100) // max 100 waiting tasks
);
// if queue full AND all threads busy → reject task (with a policy)
```

> **Key insight: A fixed thread pool always comes with an internal queue. Extra tasks beyond the thread count don't
fail — they wait in queue until a thread frees up. checkFraud() returning a CompletableFuture immediately does NOT mean
the work started immediately — it might just be queued.**


---

### Q11 — Where do we GENUINELY wait in our code? (.get() deep dive)

**The exact line in OrderService.java:**

```java
// Step 4 — Get fraud result (fraud ran in parallel with inventory check)
try {
    boolean isFraud = fraudFuture.get();  // ← GENUINE WAIT HAPPENS HERE
    if (isFraud) {
        // restore inventory, return FAILED
    }
} catch (Exception e) {
    return "FAILED: Fraud check error";
}
```

**This is the ONLY place in our entire processOrder() flow where the calling thread can genuinely block and wait.**

---

**Why is THIS line special — walking through every possibility:**

```
Possibility 1 — Fraud check already finished by the time we reach .get()
─────────────────────────────────────────────────────────────────────
Timeline:
0ms   → checkFraud() called → returns CompletableFuture immediately
0ms   → checkAndDeductInventory() runs → takes ~1ms
1ms   → (other minor work)
200ms → fraud check (running in background) FINISHES
201ms → we reach fraudFuture.get()
        → result is ALREADY there
        → .get() returns INSTANTLY, no waiting
        → thread continues immediately

Possibility 2 — Fraud check still running when we reach .get()
─────────────────────────────────────────────────────────────────────
Timeline:
0ms   → checkFraud() called → returns CompletableFuture immediately
0ms   → checkAndDeductInventory() runs → takes ~1ms
1ms   → we reach fraudFuture.get() ALREADY (inventory check was very fast)
        → fraud check is STILL RUNNING (needs 200ms total, only 1ms passed)
        → .get() BLOCKS this thread — genuinely waits
        → thread sits here doing nothing until fraud check finishes
        → at 200ms, fraud check completes → .get() unblocks → returns isFraud value

Possibility 3 — Fraud check still QUEUED (Q10 scenario — CPU pool full)
─────────────────────────────────────────────────────────────────────
Timeline:
0ms   → checkFraud() called → task goes into cpuThreadPool's QUEUE
        → (all 8 CPU threads already busy with other orders)
        → returns CompletableFuture immediately — but fraud check HASN'T STARTED
0ms   → checkAndDeductInventory() runs → takes ~1ms
1ms   → we reach fraudFuture.get()
        → fraud check hasn't even STARTED yet (still in queue)
        → .get() BLOCKS — waits for:
          1. a CPU thread to become free
          2. THEN the fraud logic to run (200ms)
        → could wait LONGER than 200ms if queue was backed up
```

---

**Which thread is actually blocked during this wait?**

```
Remember: processOrder() itself runs on ioThreadPool
(CompletableFuture.supplyAsync(..., ioThreadPool))

So the thread sitting at fraudFuture.get() is an IO POOL thread
— NOT the CPU pool thread doing the actual fraud calculation

This means:
→ One of our 16 ioThreadPool threads is now BLOCKED, waiting
→ That thread cannot pick up any OTHER order while waiting here
→ This is exactly the IO thread pool equivalent of the
  "thread waiting wastes resources" problem from Concept 11

We accepted this tradeoff because:
→ We NEED the fraud result before deciding to save the order
→ The order literally cannot proceed without knowing isFraud
→ This is unavoidable — SOME thread must wait for SOME amount of time
```

---

**Why we still call this "good" design despite the wait:**

```
Compare to NOT running fraud check in background at all:

BAD design (sequential, no parallelism):
0ms   → checkAndDeductInventory() → 1ms
1ms   → checkFraud() starts AND BLOCKS until done → 201ms
Total wait: 201ms

OUR design (fraud started early, runs in background):
0ms   → checkFraud() starts in background (doesn't block)
0ms   → checkAndDeductInventory() → 1ms (runs WHILE fraud check happens)
1ms   → fraudFuture.get() → waits for REMAINING 199ms (not full 200ms)
Total wait: 200ms

We saved 1ms by overlapping the two operations.
The GENUINE wait at .get() is unavoidable —
but we minimized HOW LONG we wait by starting fraud check earlier.
```

---

**The general rule about CompletableFuture.get():**

```
.get() is a BLOCKING call.
It ALWAYS waits until the CompletableFuture has a result —
whether that result:
→ is already available (returns instantly)
→ is still being computed (waits for computation to finish)
→ hasn't even started yet — still queued (waits for queue + computation)

The thread calling .get() is PAUSED for however long is needed.
This is the single point in async code where "async" temporarily
becomes "sync" again — you're explicitly asking to wait for the value.
```

---

**Why don't we avoid .get() entirely then?**

```
Because at SOME point, we genuinely need the answer to proceed.
"Is this fraud or not?" determines whether we save the order at all.

The alternative would be fully async chaining:
checkFraud(userId, amount)
    .thenCompose(isFraud -> {
        if (isFraud) return CompletableFuture.completedFuture("FAILED");
        return checkAndDeductInventory(...)
            .thenCompose(...)
    });

This avoids blocking entirely — but makes the code much harder
to read (the "callback hell" problem from Concept 12).
We chose .get() for clarity at the cost of one controlled blocking point.
```

---

> **Key insight: `fraudFuture.get()` is the ONE genuine blocking point in our entire order flow. Everything before it (
checkFraud starting, inventory check) is non-blocking and runs in parallel. At .get(), an IO pool thread pauses —
waiting anywhere from 0ms (if fraud already finished) to 200ms+ (if fraud check was queued behind other CPU work) —
until the fraud result is actually available.**