package com.localcloud.orderservice.order;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class OrderService {

    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(1);

    public Order createOrder(CreateOrderRequest request) {
        Long id = sequence.getAndIncrement();

        Order order = new Order(
                id,
                request.userId(),
                request.productId(),
                request.quantity(),
                "CREATED"
        );

        orders.put(id, order);

        return order;
    }

    public Order getOrder(Long id) {
        Order order = orders.get(id);

        if (order == null) {
            throw new NoSuchElementException("Order not found. id=" + id);
        }

        return order;
    }
}
