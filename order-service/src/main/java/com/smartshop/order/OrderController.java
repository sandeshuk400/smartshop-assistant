package com.smartshop.order;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderRepository orderRepository;

    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @PostMapping
    public ResponseEntity<CreateOrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
        Order order = new Order();
        order.setCustomerId(request.customerId());
        order.setTotalAmount(request.totalAmount());
        order.setStatus(OrderStatus.PLACED);

        Order saved = orderRepository.save(order);
        // Read it again so we get the bill code the database made.
        Order stored = orderRepository.findById(saved.getId()).orElseThrow();
        return ResponseEntity
                .created(URI.create("/orders/" + stored.getId()))
                .body(CreateOrderResponse.from(stored));
    }

    @GetMapping("/{id}")
    public Order get(@PathVariable Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Order " + id + " not found"));
    }
}