package com.localcloud.orderservice.order;

import com.localcloud.orderservice.order.event.OrderEventPublisher;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OrderCacheService orderCacheService;
    private final OrderEventPublisher orderEventPublisher;

    // 생성자가 하나면 Spring이 자동으로 OrderRepository를 주입합니다.
    public OrderService(
            OrderRepository orderRepository,
            OrderCacheService orderCacheService,
            OrderEventPublisher orderEventPublisher
    ) {
        this.orderRepository = orderRepository;
        this.orderCacheService = orderCacheService;
        this.orderEventPublisher = orderEventPublisher;
    }

    public OrderResponse createOrder(CreateOrderRequest request) {
        Order order = new Order(
                request.userId(),
                request.productId(),
                request.quantity(),
                "CREATED"
        );

        // save는 새 엔티티를 INSERT하고, 생성된 id가 채워진 Order를 반환합니다.
        Order savedOrder = orderRepository.save(order);
        OrderResponse response = OrderResponse.from(savedOrder);

        // 생성 직후 Redis에 저장해두면 첫 조회부터 cache hit가 가능합니다.
        orderCacheService.put(response);

        // 주문 생성 사실을 Kafka로 발행해 다른 서비스가 비동기로 처리할 수 있게 합니다.
        orderEventPublisher.publishOrderCreated(response);

        return response;
    }

    public OrderResponse getOrder(Long id) {
        return orderCacheService.get(id)
                .orElseGet(() -> findOrderFromDatabaseAndCache(id));
    }

    private OrderResponse findOrderFromDatabaseAndCache(Long id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Order not found. id=" + id));

        OrderResponse response = OrderResponse.from(order);
        orderCacheService.put(response);

        return response;
    }
}
