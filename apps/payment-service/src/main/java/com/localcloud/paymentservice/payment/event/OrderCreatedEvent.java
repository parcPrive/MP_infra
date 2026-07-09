package com.localcloud.paymentservice.payment.event;

// order-service가 Kafka에 발행한 order.created 메시지를 payment-service에서 읽기 위한 DTO입니다.
public record OrderCreatedEvent(
        Long orderId,
        Long userId,
        Long productId,
        int quantity,
        String status,
        String occurredAt
) {
}
