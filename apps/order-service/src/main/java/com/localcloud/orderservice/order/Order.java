package com.localcloud.orderservice.order;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

@Entity
@Table(name = "orders")
@Getter
public class Order {

    // IDENTITY는 PostgreSQL의 자동 증가 컬럼을 사용해 DB가 id를 생성하게 합니다.
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long userId;
    private Long productId;
    private int quantity;
    private String status;

    // JPA는 엔티티를 만들 때 기본 생성자가 필요합니다.
    protected Order() {
    }

    public Order(
            Long userId,
            Long productId,
            int quantity,
            String status
    ) {
        this.userId = userId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
    }
}
