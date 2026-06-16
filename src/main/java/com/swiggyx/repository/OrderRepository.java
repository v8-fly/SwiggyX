package com.swiggyx.repository;

import com.swiggyx.model.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    // Find all orders for a specific user
    List<Order> findByUserId(String userId);

    // Find all orders for a specific restaurant
    List<Order> findByRestaurantId(String restaurantId);

    // Find all orders with a specific status
    List<Order> findByStatus(String status);

}