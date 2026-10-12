package com.smartshop.order;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateOrderRequest(
        @NotNull Long customerId,
        @NotNull @DecimalMin("0.01") BigDecimal totalAmount
) {
}