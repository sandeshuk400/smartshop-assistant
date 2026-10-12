package com.smartshop.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

    // Used in Phase 3: "Where is my last order?" / "Cancel my last order"
    List<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId);
}