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
    // Place a new order
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

            // .get() waits for result
            // in production — we'd return immediately and use websocket
            String response = result.get();
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            return ResponseEntity
                    .internalServerError()
                    .body("Error: " + e.getMessage());
        }
    }

    // GET /api/orders/{id}
    // Get order by ID
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrder(@PathVariable Long id) {
        return orderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // GET /api/orders/user/{userId}
    // Get all orders for a user
    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Order>> getUserOrders(
            @PathVariable String userId) {
        List<Order> orders = orderRepository.findByUserId(userId);
        return ResponseEntity.ok(orders);
    }

    // GET /api/orders/status
    // Check current inventory
    @GetMapping("/status")
    public ResponseEntity<String> getStatus() {
        long totalOrders = orderRepository.count();
        return ResponseEntity.ok(
                "SwiggyX running. Total orders: " + totalOrders);
    }
}