# order-service Code Guide

이 문서는 `apps/order-service` 코드의 역할과 문법을 설명합니다.

## 1. 전체 흐름

```text
HTTP 요청
  |
  v
OrderController
  |
  v
OrderService
  |
  +-- OrderCacheService
  |     |
  |     v
  |   Redis
  |
  +-- OrderEventPublisher
  |     |
  |     v
  |   Kafka order.created
  |
  v
OrderRepository
  |
  v
PostgreSQL orders table
```

## 2. 파일 역할

| 파일 | 역할 |
| --- | --- |
| `OrderServiceApplication.java` | Spring Boot 시작점 |
| `HealthCheckController.java` | `/health-check` API |
| `CreateOrderRequest.java` | 주문 생성 요청 DTO |
| `Order.java` | JPA 엔티티, DB `orders` 테이블과 매핑 |
| `OrderRepository.java` | DB CRUD 담당 |
| `OrderCacheService.java` | Redis 캐시 읽기/쓰기 담당 |
| `OrderResponse.java` | 주문 API 응답 DTO |
| `OrderCreatedEvent.java` | Kafka로 보낼 주문 생성 이벤트 |
| `OrderEventPublisher.java` | Kafka 이벤트 발행 담당 |
| `OrderService.java` | 주문 생성/조회 비즈니스 로직 |
| `OrderController.java` | HTTP API 진입점 |

## 3. Controller 문법

```java
@RestController
@RequestMapping("/orders")
public class OrderController {
}
```

의미:

| 문법 | 의미 |
| --- | --- |
| `@RestController` | 이 클래스가 REST API 응답을 반환하는 컨트롤러임을 표시 |
| `@RequestMapping("/orders")` | 이 컨트롤러의 기본 URL을 `/orders`로 설정 |

예를 들어 클래스에 `/orders`, 메서드에 `@GetMapping("/{id}")`가 있으면 최종 주소는 `/orders/{id}`입니다.

```java
@PostMapping
public ResponseEntity<Order> createOrder(@Valid @RequestBody CreateOrderRequest request) {
    OrderResponse order = orderService.createOrder(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(order);
}
```

문법 설명:

| 문법 | 의미 |
| --- | --- |
| `@PostMapping` | HTTP POST 요청 처리 |
| `@RequestBody` | JSON 요청 본문을 Java 객체로 변환 |
| `@Valid` | DTO에 적은 검증 규칙 실행 |
| `ResponseEntity.status(HttpStatus.CREATED)` | HTTP 상태코드를 201 Created로 응답 |
| `.body(order)` | 응답 JSON으로 order 객체 반환 |

## 4. DTO 문법

```java
public record CreateOrderRequest(
        @NotNull Long userId,
        @NotNull Long productId,
        @Min(1) int quantity
) {
}
```

`record`는 단순 데이터 전달 객체를 만들 때 좋습니다.

자동으로 만들어지는 것:

```text
생성자
userId()
productId()
quantity()
equals()
hashCode()
toString()
```

검증 어노테이션:

| 어노테이션 | 의미 |
| --- | --- |
| `@NotNull` | null이면 요청 실패 |
| `@Min(1)` | 최소값 1 이상 |

## 5. Entity 문법

```java
@Entity
@Table(name = "orders")
public class Order {
}
```

의미:

| 문법 | 의미 |
| --- | --- |
| `@Entity` | 이 클래스가 DB 테이블과 연결되는 JPA 엔티티임을 표시 |
| `@Table(name = "orders")` | 테이블 이름을 `orders`로 지정 |

```java
@Id
@GeneratedValue(strategy = GenerationType.IDENTITY)
private Long id;
```

의미:

| 문법 | 의미 |
| --- | --- |
| `@Id` | 기본키 컬럼 |
| `@GeneratedValue` | id 자동 생성 |
| `GenerationType.IDENTITY` | DB의 자동 증가 기능 사용 |

왜 `protected Order()`가 필요할까?

```text
JPA가 DB에서 데이터를 읽어 엔티티 객체를 만들 때 기본 생성자를 사용합니다.
외부에서 아무렇게나 생성하지 못하게 protected로 둡니다.
```

## 6. Repository 문법

```java
public interface OrderRepository extends JpaRepository<Order, Long> {
}
```

의미:

```text
Order 엔티티를 Long 타입 id로 관리하는 Repository입니다.
```

`JpaRepository`가 자동으로 제공하는 메서드:

```text
save()
findById()
findAll()
deleteById()
existsById()
```

그래서 직접 SQL을 쓰지 않아도 기본 CRUD가 가능합니다.

## 7. Service 문법

```java
@Service
public class OrderService {
}
```

`@Service`는 이 클래스가 비즈니스 로직을 담당한다는 표시입니다.
Spring이 이 객체를 직접 생성하고 관리합니다.

```java
public OrderService(OrderRepository orderRepository) {
    this.orderRepository = orderRepository;
}
```

이것은 생성자 주입입니다.

의미:

```text
OrderService가 필요로 하는 OrderRepository를 Spring이 생성자에 넣어줍니다.
```

왜 직접 `new OrderRepository()`를 하지 않을까?

```text
Repository는 Spring Data JPA가 런타임에 구현체를 만들어 관리합니다.
그래서 개발자는 인터페이스만 선언하고, 객체 생성은 Spring에게 맡깁니다.
```

## 8. 현재 코드의 중요한 특징

```text
이전 단계: Map<Long, Order>로 메모리에 저장
현재 단계: PostgreSQL orders 테이블에 저장하고 Redis에 조회 캐시 저장
```

차이:

| 방식 | 장점 | 단점 |
| --- | --- | --- |
| 메모리 Map | 빠르고 단순함 | 앱 재시작 시 데이터 삭제 |
| PostgreSQL | 데이터 유지, SQL 조회 가능 | DB 연결 설정 필요 |

## 9. Redis Cache Service 문법

```java
public Optional<OrderResponse> get(Long id) {
    String json = redisTemplate.opsForValue().get(cacheKey(id));

    if (json == null) {
        return Optional.empty();
    }

    return Optional.of(objectMapper.readValue(json, OrderResponse.class));
}
```

의미:

| 문법 | 의미 |
| --- | --- |
| `StringRedisTemplate` | Redis에 문자열 key/value를 읽고 쓰는 Spring 도구 |
| `opsForValue()` | Redis String 자료구조 사용 |
| `get(key)` | key에 저장된 문자열 값 조회 |
| `Optional.empty()` | 캐시에 값이 없음을 표현 |
| `ObjectMapper` | JSON 문자열과 Java 객체를 변환 |

Redis에는 Java 객체가 그대로 들어가는 것이 아니라 JSON 문자열로 저장됩니다.

예:

```json
{"id":2,"userId":77,"productId":880,"quantity":5,"status":"CREATED"}
```

저장 문법:

```java
redisTemplate.opsForValue().set(cacheKey(order.id()), json, ttl);
```

의미:

```text
order:2 라는 key에 JSON 문자열을 저장하고,
ttl 시간이 지나면 Redis가 자동 삭제하게 합니다.
```

## 10. cache hit / cache miss

`OrderService` 조회 흐름:

```java
public OrderResponse getOrder(Long id) {
    return orderCacheService.get(id)
            .orElseGet(() -> findOrderFromDatabaseAndCache(id));
}
```

문법 설명:

| 문법 | 의미 |
| --- | --- |
| `orderCacheService.get(id)` | Redis에서 먼저 조회 |
| `Optional<OrderResponse>` | 있을 수도 있고 없을 수도 있는 값 |
| `orElseGet(...)` | 값이 없을 때만 뒤의 로직 실행 |
| `findOrderFromDatabaseAndCache(id)` | DB에서 조회하고 Redis에 저장 |

즉:

```text
Redis에 있으면 바로 반환
Redis에 없으면 DB 조회
DB 조회 결과를 Redis에 저장
응답 반환
```

## 11. Kafka Event Publisher 문법

```java
kafkaTemplate.send(orderCreatedTopic, event.orderId().toString(), eventJson);
```

의미:

| 값 | 의미 |
| --- | --- |
| `orderCreatedTopic` | 메시지를 보낼 Kafka topic |
| `event.orderId().toString()` | Kafka message key |
| `eventJson` | Kafka message value |

현재 value는 JSON 문자열입니다.

왜 객체를 그대로 보내지 않을까?

```text
Spring Boot 4 + Jackson 3 환경에서 Spring Kafka JsonSerializer와 충돌이 있어,
학습 단계에서는 직접 JSON 문자열로 바꾼 뒤 StringSerializer로 보내는 방식이 더 명확합니다.
```

```java
kafkaTemplate.send(...)
        .whenComplete((result, exception) -> {
            // 성공/실패 로그 처리
        });
```

`send`는 비동기입니다.
즉, Kafka에 보내는 작업이 끝날 때까지 HTTP 요청 스레드가 계속 기다리는 구조가 아닙니다.

`whenComplete`는 발행 성공 또는 실패 이후 실행되는 콜백입니다.
