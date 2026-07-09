package com.localcloud.orderservice.order.event;

import com.localcloud.orderservice.order.OrderResponse;

import java.time.Instant;

// Kafka로 발행할 주문 생성 이벤트입니다.
// API 응답 DTO와 별도로 두는 이유는, 이벤트는 다른 서비스와 약속하는 메시지 계약이기 때문입니다.
public record OrderCreatedEvent(
        Long orderId,
        Long userId,
        Long productId,
        int quantity,
        String status,
        String occurredAt
) {

    public static OrderCreatedEvent from(OrderResponse order) {
        return new OrderCreatedEvent(
                order.id(),
                order.userId(),
                order.productId(),
                order.quantity(),
                order.status(),
                Instant.now().toString()
        );
    }
}
