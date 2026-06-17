# SwiggyX — Testing Guide

### How to test every endpoint — curl and Postman.

> Server must be running first: `mvn clean spring-boot:run`
> Base URL: http://localhost:8080

---

## Endpoint 1 — Health Check

**Purpose:** Verify server is running and check total order count.

```
Method: GET
URL:    http://localhost:8080/api/orders/status
```

### curl

```bash
curl http://localhost:8080/api/orders/status
```

### Postman

```
1. Open Postman
2. New Request
3. Method: GET
4. URL: http://localhost:8080/api/orders/status
5. Click Send
```

### Expected Response

```
SwiggyX running. Total orders: 0
```

---

## Endpoint 2 — Place Order

**Purpose:** Place a new order. Triggers the full concurrency flow.

```
Method: POST
URL:    http://localhost:8080/api/orders/place
Params: userId, restaurantId, itemName, quantity, amount
```

### curl

```bash
curl -X POST "http://localhost:8080/api/orders/place?userId=USER_101&restaurantId=REST_55&itemName=Biryani&quantity=1&amount=450"
```

### Postman

```
1. New Request
2. Method: POST
3. URL: http://localhost:8080/api/orders/place
4. Go to "Params" tab (below URL bar)
5. Add key-value pairs:
   userId        = USER_101
   restaurantId  = REST_55
   itemName      = Biryani
   quantity      = 1
   amount        = 450
6. Click Send
```

Postman auto-builds the URL with query params — same as curl.

### Expected Response

```
SUCCESS: Order placed. ID: 1
```

or if inventory unavailable:

```
FAILED: Item not available
```

### What to Watch

```
Switch to your terminal running mvn spring-boot:run
Watch these logs appear:
→ Order started for: USER_101 | Thread: pool-2-thread-X
→ Fraud check started on thread: pool-3-thread-X
→ Inventory check and deduction
→ DB connection acquired/released
→ Payment processing
→ Notification sent
```

---

## Endpoint 3 — Get Order by ID

**Purpose:** Fetch a specific order's details.

```
Method: GET
URL:    http://localhost:8080/api/orders/{id}
```

### curl

```bash
curl http://localhost:8080/api/orders/1
```

### Postman

```
1. New Request
2. Method: GET
3. URL: http://localhost:8080/api/orders/1
   (1 is the order ID — change as needed)
4. Click Send
```

### Expected Response (order found)

```json
{
  "id": 1,
  "userId": "USER_101",
  "restaurantId": "REST_55",
  "itemName": "Biryani",
  "quantity": 1,
  "totalAmount": 450.0,
  "status": "PLACED",
  "createdAt": "2026-06-15T09:53:59.451"
}
```

### Expected Response (order not found)

```
HTTP 404 Not Found
(empty body)
```

---

## Endpoint 4 — Get All Orders for a User

**Purpose:** Fetch order history for a specific user.

```
Method: GET
URL:    http://localhost:8080/api/orders/user/{userId}
```

### curl

```bash
curl http://localhost:8080/api/orders/user/USER_101
```

### Postman

```
1. New Request
2. Method: GET
3. URL: http://localhost:8080/api/orders/user/USER_101
4. Click Send
```

### Expected Response

```json
[
  {
    "id": 1,
    "userId": "USER_101",
    "itemName": "Biryani",
    "status": "PLACED",
    ...
  }
]
```

If user has no orders — returns empty array `[]` (not 404).

---

## Testing Concurrency — The Important Tests

---

### Test A — Two Simultaneous Orders (Race Condition Test)

**Purpose:** Verify inventory lock prevents race conditions.

#### curl

```bash
curl -X POST "http://localhost:8080/api/orders/place?userId=USER_102&restaurantId=REST_55&itemName=Biryani&quantity=1&amount=450" & curl -X POST "http://localhost:8080/api/orders/place?userId=USER_103&restaurantId=REST_55&itemName=Biryani&quantity=1&amount=450" &
```

The `&` at the end of each command fires them simultaneously, not one after another.

#### Postman — Simulating Simultaneous Requests

```
Postman doesn't fire requests simultaneously by default in the free UI.
Options:
1. Use Postman's "Runner" feature:
   → Collections → ... menu → Run collection
   → Set iterations and 0ms delay
   → Closest to simultaneous in Postman

2. Use curl for true simultaneous testing (recommended)
   → curl with & is the most reliable way to test race conditions

3. Use Postman's pre-request script with async (advanced)
   → not necessary for our purposes — curl is simpler
```

**For race condition testing — always prefer curl with `&`.**

#### What to Watch in Logs

```
Both "Order started" lines appear close together
Both "Fraud check started" on different CPU pool threads
Inventory check happens ONE AT A TIME (never simultaneous)
Inventory value decreases correctly: 10 → 9 → 8
Never goes negative
```

---

### Test B — Scale Test (10 Simultaneous Orders)

**Purpose:** Verify thread pools and locks hold up under load.

#### curl

```bash
for i in {1..10}; do curl -X POST "http://localhost:8080/api/orders/place?userId=USER_$i&restaurantId=REST_55&itemName=Biryani&quantity=1&amount=450" & done
```

This loop fires 10 requests simultaneously using background processes (`&`).

#### Postman

```
Use Collection Runner:
1. Create a collection with the "Place Order" request
2. Use a CSV/JSON data file with 10 different userId values
3. Collections → Run → select data file → Run
4. Set delay to 0ms for near-simultaneous firing

Note: True simultaneous firing is harder in Postman UI.
For load testing — tools like k6 or JMeter are better (Phase 3).
For learning — curl loop is simplest and most reliable.
```

#### What to Watch in Logs

```
10 threads from IO pool (pool-2-thread-1 through pool-2-thread-10)
Only 8 threads from CPU pool active at once (pool-3, = num cores)
Some fraud checks wait for a free CPU thread
Inventory decreases one at a time: 10→9→8→7→6→5→4→3→2→1→0
All 10 orders saved to DB with unique IDs
```

---

### Test C — Sold Out Test

**Purpose:** Verify system correctly rejects orders when inventory is 0.

#### curl

```bash
curl -X POST "http://localhost:8080/api/orders/place?userId=USER_999&restaurantId=REST_55&itemName=Biryani&quantity=1&amount=450"
```

#### Postman

```
Same as Endpoint 2 setup, just run after inventory is depleted
(after running Test B which drains inventory to 0)
```

#### Expected Response

```
FAILED: Item not available
```

#### What to Watch in Logs

```
Inventory check for: USER_999 | Current inventory: 0
Insufficient inventory for: USER_999
Fraud check still runs in background (wasted work, but harmless)
```

---

## Postman Collection Setup (Recommended)

To make repeated testing easier, save these as a Postman Collection:

```
1. Open Postman
2. Click "New" → "Collection"
3. Name it: SwiggyX API

4. Add requests to the collection:
   - Health Check       (GET /api/orders/status)
   - Place Order         (POST /api/orders/place)
   - Get Order by ID      (GET /api/orders/{id})
   - Get User Orders      (GET /api/orders/user/{userId})

5. For "Place Order" — use Postman Variables for reusability:
   URL: {{base_url}}/api/orders/place
   Params: userId={{userId}}, etc.

6. Create a Postman Environment:
   - base_url = http://localhost:8080
   - userId    = USER_101
   (makes switching environments easy later — local vs production)
```

---

## Quick Reference — All Endpoints

```
GET  /api/orders/status              → health check
POST /api/orders/place               → place new order (needs params)
GET  /api/orders/{id}                → get order by ID
GET  /api/orders/user/{userId}       → get all orders for user
```

---

## Checking PostgreSQL Directly (PgAdmin)

After placing orders via API — verify in database:

```sql
-- See all orders
SELECT * FROM orders;

-- See orders for specific user
SELECT * FROM orders WHERE user_id = 'USER_101';

-- Count total orders
SELECT COUNT(*) FROM orders;

-- See most recent orders first
SELECT * FROM orders ORDER BY created_at DESC;
```

---

## Restarting the Server (Resets In-Memory Inventory)

```bash
# Stop server: Ctrl+C in terminal running mvn spring-boot:run

# Start again
mvn clean spring-boot:run
```

**Important:** `inventory` resets to 10 on every restart because it's stored in Java memory (RAM), not in the database.
This is a known limitation fixed in Phase 2 (moving inventory to DB with @Version).

---

*Last updated: All endpoints and concurrency tests documented*