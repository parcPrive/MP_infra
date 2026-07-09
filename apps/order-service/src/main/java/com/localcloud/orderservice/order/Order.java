package com.localcloud.orderservice.order;

import lombok.Getter;

@Getter
public class Order {

    private final Long id;
    private final Long userId;
    private final Long productId;
    private final int quantity;
    private final String status;

    public Order(
            Long id,
            Long userId,
            Long productId,
            int quantity,
            String status
    ) {
        this.id = id;
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
    }
}
