# Kafka Retry and DLT Guide

이 문서는 Kafka consumer가 실패했을 때 메시지를 어떻게 다시 처리하고, 끝까지 실패한 메시지를 어디에 남기는지 설명합니다.

## 1. 왜 필요한가

Kafka consumer는 메시지를 하나씩 읽어서 처리합니다.

정상 메시지라면 아래처럼 끝납니다.

```text
topic 메시지 읽기
  |
  v
JSON 파싱
  |
  v
서비스 로직 실행
  |
  v
offset commit
```

문제는 JSON 형식이 깨졌거나 서비스 로직에서 예외가 나는 경우입니다.

예외를 그냥 `catch`로 잡고 끝내면 Kafka 입장에서는 처리가 끝난 것처럼 보일 수 있습니다. 그러면 실패 메시지는 사라진 것처럼 지나갑니다. 운영에서는 이런 메시지를 추적할 수 있어야 하므로 실패 메시지를 DLT에 남깁니다.

## 2. 용어

| 용어 | 뜻 |
| --- | --- |
| retry | 실패한 메시지를 다시 처리하는 것 |
| backoff | retry 사이에 기다리는 시간 |
| DLT | Dead Letter Topic. 끝까지 실패한 메시지를 저장하는 Kafka topic |
| DLQ | Dead Letter Queue. 실패 메시지 보관소라는 개념 |
| offset | consumer가 어디까지 읽었는지 나타내는 위치 |

Kafka에서는 보통 DLQ 개념을 topic으로 구현하므로 이 프로젝트에서는 `.DLT` topic을 사용합니다.

## 3. 현재 적용된 서비스

```text
payment-service
  |
  +-- order.created 소비 실패
  +-- order.created.DLT로 원본 메시지 발행

inventory-service
  |
  +-- payment.completed 소비 실패
  +-- payment.completed.DLT로 원본 메시지 발행
```

## 4. 설정값

각 서비스의 `application.properties`에 아래 설정이 들어갑니다.

```properties
app.kafka.retry.max-attempts=${KAFKA_RETRY_MAX_ATTEMPTS:3}
app.kafka.retry.backoff-ms=${KAFKA_RETRY_BACKOFF_MS:1000}
app.kafka.retry.dlt-suffix=${KAFKA_DLT_SUFFIX:.DLT}
```

문법:

```text
${환경변수:기본값}
```

예를 들어 `${KAFKA_RETRY_MAX_ATTEMPTS:3}`은 `KAFKA_RETRY_MAX_ATTEMPTS` 환경변수가 있으면 그 값을 사용하고, 없으면 `3`을 사용한다는 뜻입니다.

## 5. KafkaConsumerConfig

핵심 파일:

```text
apps/payment-service/src/main/java/com/localcloud/paymentservice/config/KafkaConsumerConfig.java
apps/inventory-service/src/main/java/com/localcloud/inventoryservice/config/KafkaConsumerConfig.java
```

핵심 코드:

```java
factory.setCommonErrorHandler(kafkaErrorHandler(kafkaTemplate, maxAttempts, backoffMs, dltSuffix));
```

뜻:

```text
Kafka listener에서 예외가 발생하면 직접 종료하지 말고,
우리가 만든 errorHandler에게 실패 처리를 맡깁니다.
```

DLT 목적지:

```java
new TopicPartition(record.topic() + dltSuffix, -1)
```

문법 해석:

```text
record.topic()
  현재 실패한 메시지의 원본 topic 이름

dltSuffix
  기본값 .DLT

-1
  DLT topic의 partition은 Kafka producer가 정하게 둠
```

그래서 `payment.completed`에서 실패한 메시지는 `payment.completed.DLT`로 갑니다.

여기서 `-1`을 쓰는 이유는 DLT topic이 자동 생성될 때 partition 개수를 미리 맞추지 않아도 되게 하기 위해서입니다.

## 6. 왜 예외를 다시 던지는가

consumer 코드에서 JSON 파싱 실패 시 아래처럼 다시 예외를 던집니다.

```java
throw new IllegalArgumentException("Invalid payment.completed event payload", exception);
```

이유:

```text
consumer 메서드가 예외를 던짐
  |
  v
Spring Kafka가 실패를 감지
  |
  v
DefaultErrorHandler 실행
  |
  v
retry 또는 DLT 처리
```

반대로 예외를 잡고 로그만 남기면 Spring Kafka는 실패를 알 수 없습니다.

## 7. 직접 실패 메시지 넣어보기

스택 실행:

```bash
cd /Users/mok/Desktop/MP
docker compose -f compose/docker-compose.app.yml up --build -d payment-service inventory-service
```

깨진 payment 메시지 넣기:

```bash
echo 'broken-payment-message' | docker exec -i commerce-kafka \
  /opt/kafka/bin/kafka-console-producer.sh \
  --bootstrap-server localhost:9092 \
  --topic payment.completed
```

inventory-service retry 로그 확인:

```bash
docker logs --tail 100 inventory-service | grep 'Kafka consume retry'
```

DLT topic 확인:

```bash
docker exec commerce-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --list | grep DLT
```

DLT 메시지 읽기:

```bash
docker exec commerce-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic payment.completed.DLT \
  --from-beginning \
  --max-messages 1 \
  --timeout-ms 10000
```

예상 결과:

```text
broken-payment-message
```

## 8. consumer lag 확인

```bash
docker exec commerce-kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 \
  --describe \
  --group inventory-service-group
```

정상적으로 DLT로 이동한 뒤에는 원본 topic의 lag가 `0`이어야 합니다.

의미:

```text
원본 메시지는 실패했지만,
실패 처리는 완료되어 DLT에 보관되었다.
```

## 9. 다음에 더 깊게 갈 수 있는 것

```text
retry topic을 따로 만들기
DLT 메시지를 다시 처리하는 admin API 만들기
실패 원인 header 확인하기
consumer concurrency 늘리기
idempotency 적용하기
```
