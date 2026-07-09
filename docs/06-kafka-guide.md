# Kafka Guide

이 문서는 현재 프로젝트에서 Kafka를 왜 붙였고, 어떻게 동작하는지 정리합니다.

## 1. Kafka를 왜 사용하는가

지금까지 주문 생성은 다음 흐름이었습니다.

```text
POST /orders
  |
  v
order-service
  |
  +-- PostgreSQL 저장
  |
  +-- Redis 캐시 저장
```

여기에 Kafka를 붙이면 주문 생성 사실을 다른 서비스에 비동기로 알릴 수 있습니다.

```text
POST /orders
  |
  v
order-service
  |
  +-- PostgreSQL 저장
  |
  +-- Redis 캐시 저장
  |
  +-- Kafka order.created 이벤트 발행
          |
          v
      payment-service가 소비
```

장점:

```text
order-service가 payment-service를 직접 호출하지 않아도 됨
서비스 간 결합도가 낮아짐
주문 생성 이후 여러 후속 작업을 병렬로 처리하기 좋음
```

## 2. 현재 Kafka 구성

Compose 서비스:

```text
commerce-kafka
```

이미지:

```text
apache/kafka:4.0.0
```

포트:

| 접근 위치 | 주소 |
| --- | --- |
| Compose 내부 | `kafka:9092` |
| Mac 로컬 | `localhost:19092` |

order-service는 Compose 내부에서 실행되므로 `kafka:9092`로 접속합니다.

## 3. Topic

현재 사용하는 topic:

```text
order.created
```

역할:

```text
주문이 생성됐다는 이벤트를 저장하는 Kafka topic
```

topic 확인:

```bash
docker exec commerce-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --list
```

## 4. Producer

producer는 메시지를 Kafka topic에 보내는 쪽입니다.

현재 producer:

```text
order-service
```

코드 위치:

```text
apps/order-service/src/main/java/com/localcloud/orderservice/order/event/OrderEventPublisher.java
```

핵심 흐름:

```java
kafkaTemplate.send(orderCreatedTopic, event.orderId().toString(), eventJson);
```

문법 의미:

| 값 | 의미 |
| --- | --- |
| `orderCreatedTopic` | 메시지를 보낼 topic 이름 |
| `event.orderId().toString()` | Kafka message key |
| `eventJson` | Kafka message value |

왜 key를 `orderId`로 줄까?

```text
Kafka는 key를 기준으로 같은 key의 메시지를 같은 partition에 보내는 특성이 있습니다.
나중에 partition을 늘렸을 때 같은 주문의 이벤트 순서를 유지하는 데 도움이 됩니다.
```

## 5. Event

이벤트 객체:

```text
OrderCreatedEvent
```

필드:

```text
orderId
userId
productId
quantity
status
occurredAt
```

예시 메시지:

```json
{
  "orderId": 4,
  "userId": 202,
  "productId": 901,
  "quantity": 2,
  "status": "CREATED",
  "occurredAt": "2026-07-09T01:45:29.782474258Z"
}
```

## 6. 왜 JSON 문자열로 보내는가

처음에는 Spring Kafka의 `JsonSerializer`를 사용하려고 했습니다.

하지만 현재 조합은 다음과 같습니다.

```text
Spring Boot 4.1.0
Spring Kafka 4.1.0
Jackson 3
```

이 과정에서 기본 `JsonSerializer`가 구 Jackson 패키지인 `com.fasterxml.jackson.databind` 클래스를 찾으면서 충돌이 났습니다.

그래서 현재는 애플리케이션에서 직접 JSON 문자열로 변환하고, Kafka에는 String value로 전송합니다.

```text
OrderCreatedEvent 객체
  |
  v
ObjectMapper로 JSON 문자열 변환
  |
  v
KafkaTemplate<String, String>로 발행
```

설정:

```properties
spring.kafka.producer.key-serializer=org.apache.kafka.common.serialization.StringSerializer
spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer
```

이 방식은 학습 단계에서 명확하고 디버깅이 쉽습니다.

## 7. Consumer로 메시지 확인

현재는 아직 별도 consumer service를 만들지 않았습니다.
대신 Kafka CLI consumer로 메시지를 확인합니다.

```bash
docker exec commerce-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic order.created \
  --from-beginning \
  --max-messages 1 \
  --timeout-ms 10000
```

의미:

| 옵션 | 의미 |
| --- | --- |
| `--bootstrap-server localhost:9092` | Kafka broker 주소 |
| `--topic order.created` | 읽을 topic |
| `--from-beginning` | 처음 메시지부터 읽기 |
| `--max-messages 1` | 메시지 1개만 읽고 종료 |
| `--timeout-ms 10000` | 10초 동안 메시지가 없으면 종료 |

## 8. 검증 순서

스택 실행:

```bash
docker compose -f compose/docker-compose.app.yml up --build -d
```

주문 생성:

```bash
curl -X POST http://localhost:8082/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":202,"productId":901,"quantity":2}'
```

topic 확인:

```bash
docker exec commerce-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --list
```

메시지 확인:

```bash
docker exec commerce-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic order.created \
  --from-beginning \
  --max-messages 1 \
  --timeout-ms 10000
```

order-service 발행 로그 확인:

```bash
docker logs --tail 100 order-service | grep 'Published order.created'
```

## 9. payment-service Consumer

현재는 실제 Spring Boot 서비스인 `payment-service`가 `order.created` topic을 소비합니다.

```text
order.created
  |
  v
payment-service-group
  |
  v
PaymentEventConsumer
```

Consumer Group 확인 포인트:

```text
payment-service-group은 payment-service가 어디까지 메시지를 읽었는지 offset을 저장합니다.
```

로그 확인:

```bash
docker logs --tail 100 payment-service | grep 'Payment completed'
```

## 10. 다음 단계

현재는 payment-service가 처리 결과를 다시 Kafka로 발행합니다.

```text
payment-service
  |
  +-- payment.completed topic 발행
```

payment.completed 메시지 확인:

```bash
docker exec commerce-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic payment.completed \
  --from-beginning \
  --max-messages 1 \
  --timeout-ms 10000
```

그리고 inventory-service가 `payment.completed`를 소비합니다.

```text
payment.completed
  |
  v
inventory-service-group
  |
  v
Inventory decreased 로그
```

이때 배울 것:

```text
Producer와 Consumer를 한 서비스에서 함께 사용하기
이벤트 체인
후속 서비스 연동
```

## Retry와 DLT

consumer가 메시지를 처리하다가 예외를 던지면 Spring Kafka의 `DefaultErrorHandler`가 동작합니다.

현재 설정:

```text
max-attempts = 3
backoff-ms = 1000
dlt-suffix = .DLT
```

흐름:

```text
consumer 처리 실패
  |
  v
1초 대기 후 retry
  |
  v
계속 실패
  |
  v
DeadLetterPublishingRecoverer
  |
  v
{원본 topic}.DLT로 메시지 발행
```

예시:

```text
order.created -> order.created.DLT
payment.completed -> payment.completed.DLT
```

자세한 실습은 [Kafka Retry and DLT Guide](./09-kafka-retry-dlt-guide.md)를 봅니다.
