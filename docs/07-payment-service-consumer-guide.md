# payment-service Consumer Guide

이 문서는 `payment-service`가 Kafka 메시지를 어떻게 소비하는지 설명합니다.

## 1. payment-service의 역할

`payment-service`는 `order-service`가 발행한 `order.created` 이벤트를 소비합니다.

현재는 실제 결제 API를 호출하지 않고, 다음 흐름을 검증합니다.

```text
order-service
  |
  | Kafka publish
  v
order.created topic
  |
  | Kafka consume
  v
payment-service
  |
  v
Payment completed 로그 출력
  |
  v
payment.completed 이벤트 발행
```

## 2. 현재 구현된 파일

| 파일 | 역할 |
| --- | --- |
| `PaymentServiceApplication.java` | payment-service 시작점 |
| `OrderCreatedEvent.java` | Kafka 메시지를 Java 객체로 표현 |
| `PaymentEventConsumer.java` | Kafka topic을 구독하고 메시지 수신 |
| `PaymentService.java` | 결제 처리 로직 담당 |
| `PaymentCompletedEvent.java` | 결제 완료 이벤트 DTO |
| `PaymentEventPublisher.java` | `payment.completed` 이벤트 발행 담당 |
| `Dockerfile` | payment-service Docker 이미지 생성 |

## 3. Kafka Consumer 설정

설정 파일:

```text
apps/payment-service/src/main/resources/application.properties
```

핵심 설정:

```properties
spring.kafka.bootstrap-servers=${KAFKA_BOOTSTRAP_SERVERS:localhost:19092}
spring.kafka.consumer.group-id=${KAFKA_CONSUMER_GROUP:payment-service-group}
spring.kafka.consumer.auto-offset-reset=earliest
spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.consumer.value-deserializer=org.apache.kafka.common.serialization.StringDeserializer
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer
```

의미:

| 설정 | 의미 |
| --- | --- |
| `bootstrap-servers` | Kafka broker 주소 |
| `group-id` | Consumer Group 이름 |
| `auto-offset-reset=earliest` | 저장된 offset이 없으면 처음 메시지부터 읽기 |
| `StringDeserializer` | Kafka 메시지 key/value를 문자열로 읽기 |
| `StringSerializer` | Kafka 메시지 key/value를 문자열로 쓰기 |

## 4. @KafkaListener 문법

```java
@KafkaListener(
        topics = "${app.kafka.topic.order-created:order.created}",
        groupId = "${spring.kafka.consumer.group-id:payment-service-group}",
        autoStartup = "${app.kafka.listener.enabled:true}"
)
public void consumeOrderCreated(String message) {
}
```

의미:

| 문법 | 의미 |
| --- | --- |
| `topics` | 구독할 Kafka topic |
| `groupId` | 이 consumer가 속할 consumer group |
| `autoStartup` | 애플리케이션 시작 시 listener 자동 시작 여부 |
| `String message` | Kafka message value |

테스트에서는 Kafka broker 없이도 Spring Context가 뜨게 하려고 `autoStartup=false`를 사용합니다.

```properties
app.kafka.listener.enabled=false
```

## 5. Consumer Group

현재 group id:

```text
payment-service-group
```

Consumer Group은 같은 목적의 consumer 묶음입니다.

예를 들어 `payment-service` 인스턴스가 2개 떠 있고 같은 group id를 쓰면, Kafka는 같은 메시지를 둘 중 하나에게만 나눠줍니다.

반대로 나중에 `inventory-service-group`을 따로 만들면 같은 `order.created` 메시지를 inventory-service도 받을 수 있습니다.

```text
order.created
  |
  +-- payment-service-group
  |
  +-- inventory-service-group
```

이것이 Kafka에서 여러 서비스가 같은 이벤트를 독립적으로 처리할 수 있는 핵심입니다.

## 6. Offset

Offset은 Kafka topic 안에서 메시지의 위치입니다.

`payment-service-group`은 어디까지 읽었는지 offset을 Kafka에 저장합니다.

```text
order.created partition 0
offset 0 -> orderId=4
offset 1 -> orderId=5
```

consumer가 재시작해도 이미 처리한 offset 이후부터 다시 읽을 수 있습니다.

## 7. 메시지 처리 흐름

```java
OrderCreatedEvent event = objectMapper.readValue(message, OrderCreatedEvent.class);
paymentService.processPayment(event);
```

의미:

```text
Kafka에서 받은 JSON 문자열
  |
  v
ObjectMapper로 OrderCreatedEvent 변환
  |
  v
PaymentService로 결제 처리 위임
```

현재 결제 처리는 로그만 남깁니다.

```text
Payment completed. orderId=5, userId=303, productId=1001, quantity=2
```

그리고 결제 완료 이벤트를 발행합니다.

```text
payment.completed
```

예시 메시지:

```json
{
  "paymentId": "PAY-6",
  "orderId": 6,
  "userId": 404,
  "productId": 1200,
  "quantity": 3,
  "paymentStatus": "COMPLETED",
  "occurredAt": "2026-07-09T04:57:07.013708430Z"
}
```

## 8. 실행 확인

스택 실행:

```bash
docker compose -f compose/docker-compose.app.yml up --build -d
```

payment-service 상태:

```bash
curl http://localhost:8083/actuator/health
```

주문 생성:

```bash
curl -X POST http://localhost:8082/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":303,"productId":1001,"quantity":2}'
```

payment-service 로그:

```bash
docker logs --tail 100 payment-service | grep 'Payment completed'
```

payment.completed 메시지:

```bash
docker exec commerce-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic payment.completed \
  --from-beginning \
  --max-messages 1 \
  --timeout-ms 10000
```

## 9. 현재 검증 결과

검증된 흐름:

```text
POST /orders
  |
  v
order-service가 order.created 발행
  |
  v
payment-service가 order.created 소비
  |
  v
Payment completed 로그 출력
  |
  v
payment.completed 이벤트 발행
```

확인된 로그:

```text
Consumed order.created event. orderId=5
Payment completed. orderId=5, userId=303, productId=1001, quantity=2
Published payment.completed event. orderId=6, topic=payment.completed, partition=0, offset=0
```

## 10. 다음 확장

현재는 inventory-service가 payment-service 결과를 소비합니다.

```text
inventory-service
  |
  v
payment.completed topic 소비
```

`inventory-service`가 결제 완료 이벤트를 받아 재고 차감을 처리하는 흐름으로 확장할 수 있습니다.
