package com.localcloud.paymentservice.payment.event;

import java.time.Instant;

// payment-service가 결제 처리를 끝낸 뒤 Kafka로 발행할 이벤트입니다.
// 나중에 inventory-service가 이 이벤트를 소비해 재고 차감을 처리할 수 있습니다.
public record PaymentCompletedEvent(
        String paymentId,
        Long orderId,
        Long userId,
        Long productId,
        int quantity,
        String paymentStatus,
        String occurredAt
) {

    public static PaymentCompletedEvent from(OrderCreatedEvent orderCreatedEvent) {
        return new PaymentCompletedEvent(
                "PAY-" + orderCreatedEvent.orderId(),
                orderCreatedEvent.orderId(),
                orderCreatedEvent.userId(),
                orderCreatedEvent.productId(),
                orderCreatedEvent.quantity(),
                "COMPLETED",
                Instant.now().toString()
        );
    }
}
