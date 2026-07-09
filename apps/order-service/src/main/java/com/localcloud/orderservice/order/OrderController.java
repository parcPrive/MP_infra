package com.localcloud.orderservice.order;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        Order order = orderService.createOrder(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    @GetMapping("/{id}")
    public Order getOrder(@PathVariable Long id) {
        try {
            return orderService.getOrder(id);
        } catch (NoSuchElementException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }
}
