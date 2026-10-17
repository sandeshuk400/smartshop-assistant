package com.smartshop.order;

import java.math.BigDecimal;
import java.time.Instant;

public record CreateOrderResponse(
        Long id,
        Long customerId,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        String billCode
) {
    static CreateOrderResponse from(Order o) {
        return new CreateOrderResponse(o.getId(), o.getCustomerId(), o.getStatus(),
                o.getTotalAmount(), o.getCreatedAt(), o.getBillCode());
    }
}