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
    // Lock for inventory — always acquire FIRST
    private final ReentrantLock inventoryLock = new ReentrantLock(true);
    // Lock for payment — always acquire SECOND
    private final ReentrantLock paymentLock = new ReentrantLock(true);
    // Spring injects the OrderRepository automatically
    @Autowired
    private OrderRepository orderRepository;
    // Spring injects ioThreadPool from ThreadPoolConfig
    @Autowired
    @Qualifier("ioThreadPool")
    private ExecutorService ioThreadPool;
    // Spring injects cpuThreadPool from ThreadPoolConfig
    @Autowired
    @Qualifier("cpuThreadPool")
    private ExecutorService cpuThreadPool;
    // Spring injects dbSemaphore from ThreadPoolConfig
    @Autowired
    @Qualifier("dbSemaphore")
    private Semaphore dbSemaphore;
    // Shared inventory — race condition risk
    // multiple threads can read/write simultaneously
    private int inventory = 10;

    // Step 1 — Validate order
    // Fast CPU work — no IO, no locks needed
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
    // simulates ML model calculation
    private CompletableFuture<Boolean> checkFraud(String userId,
                                                  double amount) {
        return CompletableFuture.supplyAsync(() -> {
            System.out.println("Fraud check started for: " + userId
                    + " on thread: " + Thread.currentThread().getName());

            // Simulate heavy ML model calculation
            // In real world: call Python ML service
            try {
                Thread.sleep(200); // 200ms CPU work simulation
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Simple fraud rule: amount > 10000 is suspicious
            boolean isFraud = amount > 10000;

            System.out.println("Fraud check done for: " + userId
                    + " isFraud: " + isFraud);

            return isFraud;

        }, cpuThreadPool); // runs on CPU thread pool — not IO pool
    }

    // Step 3 — Check and Deduct Inventory
    // CRITICAL SECTION — race condition protection
    // Two users cannot order last item simultaneously
    private boolean checkAndDeductInventory(String userId, int quantity) {
        inventoryLock.lock();
        try {
            System.out.println("Inventory check for: " + userId
                    + " | Current inventory: " + inventory
                    + " | Thread: " + Thread.currentThread().getName());

            if (inventory < quantity) {
                System.out.println("Insufficient inventory for: " + userId);
                return false;
            }

            // Deduct inventory
            inventory = inventory - quantity;
            System.out.println("Inventory deducted for: " + userId
                    + " | Remaining: " + inventory);
            return true;

        } finally {
            inventoryLock.unlock();
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
            // Acquire semaphore — blocks if 20 threads already in DB
            dbSemaphore.acquire();
            System.out.println("DB connection acquired for: " + userId
                    + " | Thread: " + Thread.currentThread().getName());

            // Build order object
            Order order = Order.builder()
                    .userId(userId)
                    .restaurantId(restaurantId)
                    .itemName(itemName)
                    .quantity(quantity)
                    .totalAmount(amount)
                    .status("PLACED")
                    .createdAt(LocalDateTime.now())
                    .build();

            // Save to DB — JPA handles SQL
            Order savedOrder = orderRepository.save(order);
            System.out.println("Order saved to DB: " + savedOrder.getId()
                    + " for: " + userId);

            return savedOrder;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while waiting for DB connection");
        } finally {
            // ALWAYS release semaphore — even if exception thrown
            dbSemaphore.release();
            System.out.println("DB connection released for: " + userId);
        }
    }

    // Main method — connects all steps together
    // This is what Controller will call
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

            // Step 2 — Fraud detection (runs on CPU pool in background)
            CompletableFuture<Boolean> fraudFuture =
                    checkFraud(userId, amount);

            // Step 3 — Check and deduct inventory
            // Lock ordering: inventoryLock acquired here (FIRST)
            boolean inventoryAvailable =
                    checkAndDeductInventory(userId, quantity);

            if (!inventoryAvailable) {
                return "FAILED: Item not available";
            }

            // Step 4 — Wait for fraud result
            // By now fraud check has been running in background
            // hopefully already done or almost done
            try {
                boolean isFraud = fraudFuture.get();
                if (isFraud) {
                    // Restore inventory — order blocked
                    inventoryLock.lock();
                    try {
                        inventory = inventory + quantity;
                        System.out.println("Inventory restored for: "
                                + userId + " fraud detected");
                    } finally {
                        inventoryLock.unlock();
                    }
                    return "FAILED: Fraud detected";
                }
            } catch (Exception e) {
                return "FAILED: Fraud check error";
            }

            // Step 5 — Save to DB (Semaphore controls access)
            Order savedOrder = saveOrderToDB(userId, restaurantId,
                    itemName, quantity, amount);

            // Step 6 — Charge payment
            // Lock ordering: paymentLock acquired here (SECOND)
            paymentLock.lock();
            try {
                System.out.println("Payment processing for: " + userId
                        + " | Amount: " + amount);
                // Simulate payment gateway call
                Thread.sleep(30);
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