package com.localcloud.orderservice.order;

import org.springframework.data.jpa.repository.JpaRepository;

// JpaRepository<Order, Long>은 Order 엔티티의 CRUD 메서드를 자동으로 제공합니다.
// 예: save, findById, findAll, deleteById
public interface OrderRepository extends JpaRepository<Order, Long> {
}
