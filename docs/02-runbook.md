# Runbook

이 문서는 프로젝트를 실행하고 확인하는 방법을 정리한 운영 메모입니다.

## 1. Spring Boot 테스트

```bash
cd /Users/mok/Desktop/MP/apps/order-service
./gradlew test
```

의미:

```text
Java 코드 컴파일
Spring ApplicationContext 로딩
테스트 환경에서 H2 DB 사용
```

## 2. JAR 빌드

```bash
cd /Users/mok/Desktop/MP/apps/order-service
./gradlew clean bootJar
```

생성 위치:

```text
apps/order-service/build/libs/order-service-0.0.1-SNAPSHOT.jar
```

왜 먼저 JAR를 만들까?

```text
Dockerfile이 build/libs/*.jar 파일을 app.jar로 복사하기 때문입니다.
즉, Docker 이미지 빌드 전에 실행 가능한 JAR가 있어야 합니다.
```

## 3. Docker Compose 실행

```bash
cd /Users/mok/Desktop/MP
docker compose -f compose/docker-compose.app.yml up --build -d
```

옵션 의미:

| 옵션 | 의미 |
| --- | --- |
| `-f compose/docker-compose.app.yml` | 사용할 Compose 파일 지정 |
| `up` | 서비스 생성/시작 |
| `--build` | 이미지가 필요하면 다시 빌드 |
| `-d` | 백그라운드 실행 |

## 4. 컨테이너 상태 확인

```bash
docker compose -f compose/docker-compose.app.yml ps
```

정상 예시:

```text
commerce-kafka   Up (healthy)
commerce-nginx   Up
commerce-redis   Up (healthy)
order-db         Up (healthy)
order-service    Up
payment-service  Up
```

## 5. API 확인

NGINX를 통해 호출:

```bash
curl http://localhost:8082/health-check
```

기대 응답:

```text
order-service is running
```

주문 생성:

```bash
curl -X POST http://localhost:8082/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":10,"productId":300,"quantity":4}'
```

주문 조회:

```bash
curl http://localhost:8082/orders/1
```

## 6. PostgreSQL 확인

컨테이너 안에서 `psql` 실행:

```bash
docker exec order-db psql -U orderuser -d orderdb \
  -c 'select id, user_id, product_id, quantity, status from orders order by id;'
```

왜 `docker exec`를 쓸까?

```text
psql 클라이언트가 내 Mac에 없어도,
PostgreSQL 컨테이너 안에는 psql이 들어있기 때문입니다.
```

## 7. Redis 확인

```bash
docker exec commerce-redis redis-cli ping
```

기대 응답:

```text
PONG
```

주문 캐시 key 확인:

```bash
docker exec commerce-redis redis-cli keys 'order:*'
```

특정 주문 캐시 확인:

```bash
docker exec commerce-redis redis-cli get order:1
```

TTL 확인:

```bash
docker exec commerce-redis redis-cli ttl order:1
```

캐시 미스 테스트:

```bash
docker exec commerce-redis redis-cli del order:1
curl http://localhost:8082/orders/1
docker exec commerce-redis redis-cli get order:1
```

의미:

```text
1. Redis key를 삭제한다.
2. API로 주문을 조회한다.
3. order-service가 PostgreSQL에서 조회한 뒤 Redis에 다시 저장한다.
```

## 8. Kafka 확인

topic 확인:

```bash
docker exec commerce-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --list
```

주문 생성 이벤트 읽기:

```bash
docker exec commerce-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic order.created \
  --from-beginning \
  --max-messages 1 \
  --timeout-ms 10000
```

결제 완료 이벤트 읽기:

```bash
docker exec commerce-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic payment.completed \
  --from-beginning \
  --max-messages 1 \
  --timeout-ms 10000
```

order-service 발행 로그 확인:

```bash
docker logs --tail 100 order-service | grep 'Published order.created'
```

payment-service 소비 로그 확인:

```bash
docker logs --tail 100 payment-service | grep 'Payment completed'
```

payment.completed 발행 로그 확인:

```bash
docker logs --tail 100 payment-service | grep 'Published payment.completed'
```

payment-service 헬스 체크:

```bash
curl http://localhost:8083/actuator/health
```

## 9. 로그 확인

전체 로그:

```bash
docker compose -f compose/docker-compose.app.yml logs
```

order-service 로그만:

```bash
docker compose -f compose/docker-compose.app.yml logs order-service
```

최근 100줄:

```bash
docker logs --tail 100 order-service
```

## 10. 스택 중지

컨테이너 중지/삭제:

```bash
docker compose -f compose/docker-compose.app.yml down
```

데이터 볼륨까지 삭제:

```bash
docker compose -f compose/docker-compose.app.yml down -v
```

주의:

```text
down -v를 실행하면 PostgreSQL에 저장된 주문 데이터도 삭제됩니다.
학습 중 데이터가 필요하면 down만 사용하세요.
```

## 11. 자주 나는 문제

### 8080 포트 충돌

증상:

```text
Port 8080 was already in use
```

해결:

```bash
lsof -nP -iTCP:8080 -sTCP:LISTEN
```

이 프로젝트는 충돌을 피하려고 NGINX를 `8082`, order-service를 `8081`로 열었습니다.

### NGINX 502 Bad Gateway

원인:

```text
NGINX가 order-service로 연결하지 못할 때 발생합니다.
```

확인:

```bash
docker compose -f compose/docker-compose.app.yml ps
docker logs --tail 100 commerce-nginx
curl http://localhost:8081/health-check
```

현재 NGINX 설정은 Docker 내부 DNS를 사용해 컨테이너 IP 변경에 대응합니다.

### Kafka topic이 안 보임

원인:

```text
아직 주문 생성 요청이 없어서 order.created topic이 자동 생성되지 않았을 수 있습니다.
```

확인:

```bash
curl -X POST http://localhost:8082/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":1,"productId":100,"quantity":1}'

docker exec commerce-kafka /opt/kafka/bin/kafka-topics.sh \
  --bootstrap-server localhost:9092 \
  --list
```
