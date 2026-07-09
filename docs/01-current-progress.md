# Current Progress

지금까지 완료한 작업을 단계별로 정리합니다.

## 1. 프로젝트 기본 구조 생성

처음에 빈 폴더였던 `/Users/mok/Desktop/MP` 아래에 포트폴리오용 구조를 만들었습니다.

```text
apps/
compose/
docker/
nginx/
k8s/
helm/
cicd/
monitoring/
terraform/
docs/
```

역할은 다음과 같습니다.

| 폴더 | 역할 |
| --- | --- |
| `apps/` | Spring Boot 같은 애플리케이션 코드 |
| `compose/` | Docker Compose 실행 파일 |
| `nginx/` | NGINX reverse proxy 설정 |
| `k8s/` | Kubernetes YAML |
| `helm/` | Helm Chart |
| `cicd/` | Jenkins, GitHub Actions 설정 |
| `monitoring/` | Prometheus, Grafana 설정 |
| `terraform/` | AWS 인프라 코드 |
| `docs/` | 학습 기록과 운영 문서 |

## 2. Git/GitHub 연결

완료한 일:

```text
git init
GitHub origin 연결
첫 커밋
GitHub push
```

현재 저장소는 `main` 브랜치가 `origin/main`을 추적합니다.

확인 명령어:

```bash
git status --short --branch
git log --oneline --decorate -5
```

## 3. order-service 생성

`apps/order-service`에 Spring Boot 프로젝트를 만들었습니다.

현재 기준:

| 항목 | 값 |
| --- | --- |
| Java | 17 |
| Spring Boot | 4.1.0 |
| Build Tool | Gradle Wrapper |
| Web | Spring Web MVC |
| DB | Spring Data JPA + PostgreSQL |
| Health | Spring Boot Actuator |
| Test DB | H2 |

## 4. API 구현

현재 동작하는 API:

| Method | Path | 역할 |
| --- | --- | --- |
| `GET` | `/health-check` | 직접 만든 간단한 헬스 체크 |
| `GET` | `/actuator/health` | Spring Boot Actuator 헬스 체크 |
| `POST` | `/orders` | 주문 생성 |
| `GET` | `/orders/{id}` | 주문 단건 조회 |

주문 생성 예시:

```bash
curl -X POST http://localhost:8082/orders \
  -H "Content-Type: application/json" \
  -d '{"userId":10,"productId":300,"quantity":4}'
```

응답 예시:

```json
{
  "userId": 10,
  "productId": 300,
  "quantity": 4,
  "status": "CREATED",
  "id": 1
}
```

## 5. Docker 이미지 생성

Spring Boot JAR를 만들고, Dockerfile로 이미지화했습니다.

```bash
cd apps/order-service
./gradlew clean bootJar
docker build -t orderservice:0.1 .
```

이 단계의 의미:

```text
내 Mac에서 직접 실행하는 앱
-> JAR 파일로 패키징
-> Docker 이미지로 패키징
-> 컨테이너 안에서 독립 실행
```

## 6. Docker Compose 구성

현재 Compose 스택:

```text
order-service
order-db(PostgreSQL)
commerce-redis
commerce-nginx
```

검증된 것:

```text
PostgreSQL healthy
Redis healthy
NGINX -> order-service 프록시 정상
POST /orders 정상
GET /orders/1 정상
orders 테이블에 실제 데이터 저장 확인
Redis order:{id} 캐시 저장 확인
Redis cache miss 이후 DB 조회 및 cache fill 확인
Kafka order.created topic 생성 확인
Kafka order.created 이벤트 발행 확인
payment-service order.created 이벤트 소비 확인
payment.completed 이벤트 발행 확인
inventory-service payment.completed 이벤트 소비 확인
```

## 7. 현재 상태 한 줄 요약

```text
Spring Boot 주문 API가 Docker Compose 환경에서 NGINX를 통해 호출되고,
주문 데이터는 PostgreSQL 컨테이너에 저장된다.
주문 조회 결과는 Redis 컨테이너에 TTL 300초 캐시로 저장된다.
주문 생성 이벤트는 Kafka order.created topic에 JSON 메시지로 발행된다.
payment-service는 order.created 이벤트를 소비해 결제 처리 로그를 남긴다.
결제 처리 후 payment.completed 이벤트를 Kafka에 다시 발행한다.
inventory-service는 payment.completed 이벤트를 소비해 재고 차감 로그를 남긴다.
```

## 8. Redis 캐시 흐름

주문 생성:

```text
POST /orders
  |
  v
OrderController
  |
  v
OrderService
  |
  +-- PostgreSQL INSERT
  |
  +-- Redis SET order:{id} ... EX 300
```

주문 조회:

```text
GET /orders/{id}
  |
  v
Redis GET order:{id}
  |
  +-- cache hit  -> Redis JSON 응답
  |
  +-- cache miss -> PostgreSQL SELECT -> Redis SET -> 응답
```

## 9. Kafka 이벤트 흐름

```text
POST /orders
  |
  v
PostgreSQL INSERT
  |
  v
Redis SET order:{id}
  |
  v
Kafka SEND order.created
```

검증한 메시지 예시:

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

## 12. inventory-service 소비 흐름

```text
payment.completed topic
  |
  v
inventory-service-group
  |
  v
InventoryEventConsumer
  |
  v
InventoryService
  |
  v
Inventory decreased 로그
```

검증한 로그:

```text
Consumed payment.completed event. orderId=7
Inventory decreased. orderId=7, productId=1300, quantity=4, paymentId=PAY-7
```

## 10. payment-service 소비 흐름

```text
order.created topic
  |
  v
payment-service-group
  |
  v
PaymentEventConsumer
  |
  v
PaymentService
  |
  v
Payment completed 로그
```

검증한 로그:

```text
Consumed order.created event. orderId=5
Payment completed. orderId=5, userId=303, productId=1001, quantity=2
```

## 11. payment.completed 이벤트 흐름

```text
payment-service
  |
  v
payment.completed topic
```

검증한 메시지 예시:

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
