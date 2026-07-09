package com.localcloud.inventoryservice.inventory.event;

// payment-service가 발행한 payment.completed 메시지를 inventory-service에서 읽기 위한 DTO입니다.
public record PaymentCompletedEvent(
        String paymentId,
        Long orderId,
        Long userId,
        Long productId,
        int quantity,
        String paymentStatus,
        String occurredAt
) {
}
