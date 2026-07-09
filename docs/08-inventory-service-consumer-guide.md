# inventory-service Consumer Guide

이 문서는 `inventory-service`가 `payment.completed` 이벤트를 어떻게 소비하는지 설명합니다.

## 1. inventory-service의 역할

`inventory-service`는 결제가 완료된 주문에 대해 재고 차감을 처리하는 서비스입니다.

현재는 실제 재고 DB를 만들지 않고, Kafka 이벤트 체인이 정상으로 이어지는지 로그로 검증합니다.

```text
payment-service
  |
  | Kafka publish
  v
payment.completed topic
  |
  | Kafka consume
  v
inventory-service
  |
  v
Inventory decreased 로그 출력
```

## 2. 현재 구현된 파일

| 파일 | 역할 |
| --- | --- |
| `InventoryServiceApplication.java` | inventory-service 시작점 |
| `PaymentCompletedEvent.java` | Kafka 메시지를 Java 객체로 표현 |
| `InventoryEventConsumer.java` | `payment.completed` topic 구독 |
| `InventoryService.java` | 재고 차감 처리 로직 |
| `Dockerfile` | inventory-service Docker 이미지 생성 |

## 3. Kafka Consumer 설정

설정 파일:

```text
apps/inventory-service/src/main/resources/application.properties
```

핵심 설정:

```properties
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:19092}
spring.kafka.consumer.group-id=${KAFKA_CONSUMER_GROUP:inventory-service-group}
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer
app.kafka.topic.payment-completed=${PAYMENT_COMPLETED_TOPIC:payment.completed}
```

## 4. Consumer Group

현재 group id:

```text
inventory-service-group
```

`payment-service-group`과 `inventory-service-group`은 서로 다른 consumer group입니다.

```text
order.created
  |
  v
payment-service-group

payment.completed
  |
  v
inventory-service-group
```

이렇게 분리하면 각 서비스가 자기 책임에 맞는 이벤트를 독립적으로 처리합니다.

## 5. @KafkaListener

```java
@KafkaListener(
        topics = "${app.kafka.topic.payment-completed:payment.completed}",
        groupId = "${spring.kafka.consumer.group-id:inventory-service-group}",
        autoStartup = "${app.kafka.listener.enabled:true}"
)
public void consumePaymentCompleted(String message) {
}
```

의미:

| 항목 | 의미 |
| --- | --- |
| `topics` | 소비할 topic |
| `groupId` | consumer group 이름 |
| `autoStartup` | listener 자동 시작 여부 |
| `String message` | Kafka message value |

## 6. 메시지 처리 흐름

```text
Kafka JSON 문자열
  |
  v
ObjectMapper
  |
  v
PaymentCompletedEvent
  |
  v
InventoryService.decreaseInventory()
```

현재 출력 로그:

```text
Inventory decreased. orderId=7, productId=1300, quantity=4, paymentId=PAY-7
```

## 7. 전체 이벤트 체인

현재 완성된 체인:

```text
POST /orders
  |
  v
order-service
  |
  +-- PostgreSQL 저장
  +-- Redis 캐시 저장
  +-- order.created 발행
          |
          v
      payment-service
          |
          +-- payment.completed 발행
                  |
                  v
              inventory-service
                  |
                  v
              재고 차감 로그
```

## 8. 실행 확인

스택 실행:

```bash
docker compose -f compose/docker-compose.app.yml up --build -d
```

inventory-service 헬스 체크:

```bash
curl http://localhost:8084/actuator/health
```

주문 생성:

```bash
curl -X POST http://localhost:8082/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":505,"productId":1300,"quantity":4}'
```

inventory-service 로그:

```bash
docker logs --tail 100 inventory-service | grep 'Inventory decreased'
```

consumer group lag 확인:

```bash
docker exec commerce-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group inventory-service-group
```

정상 기준:

```text
LAG = 0
```

## 9. 다음 확장

이후에는 다음 중 하나로 확장할 수 있습니다.

```text
inventory-service에 DB 연결
재고 부족 실패 이벤트 발행
retry topic / DLQ 구성
Kafka consumer lag 모니터링
```

