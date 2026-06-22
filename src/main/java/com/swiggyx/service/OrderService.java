package com.swiggyx.service;

import com.swiggyx.model.Inventory;
import com.swiggyx.model.InventoryId;
import com.swiggyx.model.Order;
import com.swiggyx.repository.InventoryRepository;
import com.swiggyx.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
    @Autowired
    private InventoryRepository inventoryRepository;
    // Spring injects ioThreadPool from ThreadPoolConfig
    @Autowired
    @Qualifier("ioThreadPool")
    private ExecutorService ioThreadPool;
    // Spring injects cpuThreadPool from ThreadPoolConfig
    @Autowired
    @Qualifier("cpuThreadPool")
    private ExecutorService cpuThreadPool;
    // Spring injects dbSemaphore from ThreadPoolConfig

    private Semaphore dbSemaphore;
    // Shared inventory — race condition risk
    // multiple threads can read/write simultaneously


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
                    return false;  // genuinely sold out
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
        throw new RuntimeException("HIGH_DEMAND");  // ← NEW: distinct signal
    }

    // Step 4 — Save Order to DB
    // Semaphore controls max 20 simultaneous DB connections
    private Order saveOrderToDB(String userId,
                                String restaurantId,
                                String itemName,
                                int quantity,
                                double amount) {


        System.out.println("Saving order to DB for: " + userId
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
        System.out.println("Order saved: " + savedOrder.getId() + " for: " + userId);
        return savedOrder;
    }

    private void restoreInventory(String restaurantId, String itemName, int quantity, String userId) {
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

    // UNCHANGED — this is our outer method, runs on ioThreadPool
    public CompletableFuture<String> processOrder(String userId,
                                                  String restaurantId,
                                                  String itemName,
                                                  int quantity,
                                                  double amount) {

        return CompletableFuture.supplyAsync(() -> {

            System.out.println("Order started for: " + userId
                    + " | Thread: " + Thread.currentThread().getName());

            if (!validateOrder(userId, itemName, quantity, amount)) {
                return "FAILED: Invalid order details";
            }

            CompletableFuture<Boolean> fraudFuture = checkFraud(userId, amount);

            // NEW — calls our @Transactional method, runs on THIS
            // SAME ioThreadPool thread (no further async hop happens)
            Order savedOrder;
            try {
                savedOrder = processOrderTransactionally(userId, restaurantId, itemName, quantity, amount);
            } catch (RuntimeException e) {
                if ("HIGH_DEMAND".equals(e.getMessage())) {
                    return "FAILED: High demand right now, please try again";
                }
                if ("INSUFFICIENT_INVENTORY".equals(e.getMessage())) {
                    return "FAILED: Item not available";
                }
                return "FAILED: Order processing error";
            }

            // Step 4 — Wait for fraud result
            try {
                boolean isFraud = fraudFuture.get();
                if (isFraud) {
                    restoreInventory(restaurantId, itemName, quantity, userId);
                    // also need to undo the order we just saved!
                    orderRepository.delete(savedOrder);
                    return "FAILED: Fraud detected";
                }
            } catch (Exception e) {
                restoreInventory(restaurantId, itemName, quantity, userId);
                orderRepository.delete(savedOrder);
                return "FAILED: Fraud check error";
            }

            // Step 6 — Charge payment
            paymentLock.lock();
            try {
                System.out.println("Payment processing for: " + userId + " | Amount: " + amount);
                Thread.sleep(30);
                System.out.println("Payment done for: " + userId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                paymentLock.unlock();
            }

            // Step 7 — Notify
            CompletableFuture.runAsync(() -> {
                System.out.println("Notification sent for order: " + savedOrder.getId());
            }, ioThreadPool);

            return "SUCCESS: Order placed. ID: " + savedOrder.getId();

        }, ioThreadPool);
    }

    // NEW — the actual @Transactional method
// Inventory deduction AND order save now happen ATOMICALLY
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

        if (true) {
            throw new RuntimeException("SIMULATED FAILURE AFTER INVENTORY DEDUCTED");
        }  // ← TEMPORARY

        return saveOrderToDB(userId, restaurantId, itemName, quantity, amount);
    }

}