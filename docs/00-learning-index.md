# Learning Index

이 문서는 프로젝트를 학습할 때 가장 먼저 보는 목차입니다.

현재 프로젝트의 목표는 백엔드 기능을 많이 만드는 것이 아니라, 작은 Spring Boot 서비스를 기준으로 로컬 실행부터 Docker, Docker Compose, DB, Redis, NGINX, 이후 Kafka/Kubernetes/AWS까지 확장하는 과정을 이해하는 것입니다.

## 지금 읽을 순서

1. [현재 진행 상황](./01-current-progress.md)
2. [실행과 확인 방법](./02-runbook.md)
3. [order-service 코드 해설](./03-order-service-code-guide.md)
4. [Docker Compose 스택 해설](./04-docker-compose-stack.md)
5. [다음 작업 로드맵](./05-roadmap.md)
6. [Kafka 해설](./06-kafka-guide.md)
7. [payment-service Consumer 해설](./07-payment-service-consumer-guide.md)
8. [inventory-service Consumer 해설](./08-inventory-service-consumer-guide.md)

## 현재 완성된 흐름

```text
Client
  |
  | http://localhost:8082
  v
NGINX container
  |
  | http://order-service:8080
  v
order-service container
  |
  | jdbc:postgresql://order-db:5432/orderdb
  v
PostgreSQL container

Redis container
  |
  +-- order:{id} key로 주문 조회 결과 캐시
  +-- TTL 300초 후 자동 만료

Kafka container
  |
  +-- order.created topic에 주문 생성 이벤트 저장

payment-service container
  |
  +-- order.created topic 소비
  +-- 결제 처리 완료 로그 출력
  +-- payment.completed topic 발행

inventory-service container
  |
  +-- payment.completed topic 소비
  +-- 재고 차감 로그 출력
```

## 핵심 포트

| 목적 | 주소 |
| --- | --- |
| NGINX 경유 API | `http://localhost:8082` |
| order-service 직접 접근 | `http://localhost:8081` |
| PostgreSQL | `localhost:15432` |
| Redis | `localhost:16379` |

Mac에 이미 PostgreSQL `5432`, Redis `6379`, Java `8080`이 떠 있어서 충돌을 피하려고 위 포트를 사용합니다.

## 현재 핵심 학습 주제

```text
POST /orders
  -> PostgreSQL 저장
  -> Redis order:{id} 캐시 저장

GET /orders/{id}
  -> Redis 캐시 먼저 확인
  -> 없으면 PostgreSQL 조회
  -> 조회 결과를 Redis에 다시 저장

POST /orders
  -> Kafka order.created 이벤트 발행
  -> payment-service가 order.created 이벤트 소비
  -> payment-service가 payment.completed 이벤트 발행
  -> inventory-service가 payment.completed 이벤트 소비
```
