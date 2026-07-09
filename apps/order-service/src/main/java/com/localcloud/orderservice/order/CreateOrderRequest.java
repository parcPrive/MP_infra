package com.localcloud.orderservice.order;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
        @NotNull
        Long userId,

        @NotNull
        Long productId,

        @Min(1)
        int quantity
) {
}
