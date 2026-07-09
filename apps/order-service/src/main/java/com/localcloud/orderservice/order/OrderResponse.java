package com.localcloud.orderservice.order;

// API 응답 전용 DTO입니다.
// Entity를 그대로 외부에 노출하지 않고, 클라이언트가 필요한 필드만 응답합니다.
public record OrderResponse(
        Long id,
        Long userId,
        Long productId,
        int quantity,
        String status
) {

    public static OrderResponse from(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getUserId(),
                order.getProductId(),
                order.getQuantity(),
                order.getStatus()
        );
    }
}
