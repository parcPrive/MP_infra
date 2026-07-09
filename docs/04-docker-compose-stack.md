# Docker Compose Stack Guide

이 문서는 `compose/docker-compose.app.yml`과 `nginx/default.conf`를 이해하기 위한 문서입니다.

## 1. 왜 Docker Compose를 쓰는가

Docker 단독 실행은 컨테이너 하나씩 실행할 때는 괜찮습니다.

하지만 지금은 여러 컨테이너가 같이 필요합니다.

```text
order-service
payment-service
inventory-service
PostgreSQL
Redis
NGINX
Kafka
```

Docker Compose를 쓰면 이 컨테이너들을 하나의 YAML 파일로 정의하고 함께 실행할 수 있습니다.

```bash
docker compose -f compose/docker-compose.app.yml up -d
```

## 2. Compose 핵심 문법

```yaml
services:
  order-service:
    image: orderservice:0.1
```

의미:

| 문법 | 의미 |
| --- | --- |
| `services` | 실행할 컨테이너 목록 |
| `order-service` | Compose 내부 서비스 이름 |
| `image` | 사용할 Docker 이미지 |

서비스 이름은 중요합니다.
같은 Compose 네트워크 안에서는 서비스 이름이 DNS 이름처럼 동작합니다.

예:

```text
order-service 컨테이너에서 order-db:5432 로 접속 가능
nginx 컨테이너에서 order-service:8080 으로 접속 가능
```

## 3. build와 image

```yaml
build:
  context: ../apps/order-service
  dockerfile: Dockerfile
image: orderservice:0.1
```

의미:

| 문법 | 의미 |
| --- | --- |
| `context` | Docker build 명령을 실행할 기준 폴더 |
| `dockerfile` | 사용할 Dockerfile 이름 |
| `image` | 빌드 결과 이미지 이름 |

이 설정은 아래 명령과 비슷한 일을 합니다.

```bash
docker build -t orderservice:0.1 /Users/mok/Desktop/MP/apps/order-service
```

## 4. ports 문법

```yaml
ports:
  - "8081:8080"
```

의미:

```text
내 Mac의 8081 포트 -> 컨테이너 내부의 8080 포트
```

왼쪽은 호스트 포트, 오른쪽은 컨테이너 포트입니다.

현재 포트:

| 서비스 | 호스트 포트 | 컨테이너 포트 |
| --- | --- | --- |
| order-service | `8081` | `8080` |
| payment-service | `8083` | `8080` |
| inventory-service | `8084` | `8080` |
| nginx | `8082` | `80` |
| PostgreSQL | `15432` | `5432` |
| Redis | `16379` | `6379` |
| Kafka | `19092` | `19092` |

## 5. depends_on과 healthcheck

```yaml
depends_on:
  order-db:
    condition: service_healthy
```

의미:

```text
order-db 컨테이너가 healthy 상태가 된 뒤 order-service를 시작합니다.
```

DB는 컨테이너가 시작됐다고 바로 접속 가능한 상태가 아닐 수 있습니다.
그래서 healthcheck가 필요합니다.

```yaml
healthcheck:
  test: ["CMD-SHELL", "pg_isready -U orderuser -d orderdb"]
  interval: 10s
  timeout: 5s
  retries: 5
```

문법:

| 항목 | 의미 |
| --- | --- |
| `test` | 헬스체크 명령 |
| `interval` | 검사 주기 |
| `timeout` | 한 번 검사할 때 기다리는 시간 |
| `retries` | 실패를 몇 번까지 허용할지 |

## 6. environment 문법

```yaml
environment:
  DB_URL: jdbc:postgresql://order-db:5432/orderdb
  DB_USER: orderuser
  DB_PASSWORD: orderpass
```

컨테이너 안에 환경변수를 주입합니다.

Spring Boot에서는 다음 설정으로 읽습니다.

```properties
spring.datasource.url=${DB_URL:jdbc:postgresql://localhost:15432/orderdb}
```

의미:

```text
DB_URL 환경변수가 있으면 그 값을 사용
없으면 localhost:15432 기본값 사용
```

그래서 같은 애플리케이션이 두 환경에서 모두 동작합니다.

```text
로컬 직접 실행: localhost:15432
Compose 실행: order-db:5432
```

## 7. volumes 문법

```yaml
volumes:
  - order-db-data:/var/lib/postgresql/data
```

의미:

```text
PostgreSQL 데이터 저장 폴더를 Docker named volume에 연결합니다.
```

이렇게 하면 컨테이너를 지웠다 다시 만들어도 데이터가 유지됩니다.

데이터까지 지우려면:

```bash
docker compose -f compose/docker-compose.app.yml down -v
```

## 8. NGINX 역할

현재 NGINX는 reverse proxy입니다.

```text
Client
  |
  | localhost:8082
  v
NGINX
  |
  | order-service:8080
  v
Spring Boot
```

NGINX 설정:

```nginx
location / {
    proxy_pass $order_service;
}
```

의미:

```text
들어오는 모든 요청 경로를 order-service로 전달합니다.
```

예:

```text
localhost:8082/orders
-> order-service:8080/orders
```

## 9. Docker 내부 DNS

```nginx
resolver 127.0.0.11 valid=10s ipv6=off;
set $order_service http://order-service:8080;
```

왜 필요할까?

```text
컨테이너가 재생성되면 내부 IP가 바뀔 수 있습니다.
NGINX가 예전 IP를 계속 들고 있으면 502 Bad Gateway가 납니다.
```

`127.0.0.11`은 Docker가 제공하는 내부 DNS 서버입니다.
서비스 이름 `order-service`를 현재 컨테이너 IP로 다시 찾아줍니다.

## 10. 현재 Redis 상태

Redis 컨테이너는 `order-service`와 연결되어 주문 조회 캐시로 사용됩니다.

현재 완료:

```text
Redis 컨테이너 실행
포트 매핑
healthcheck
PONG 확인
order:{id} 캐시 key 저장 확인
cache miss 이후 cache fill 확인
```

Compose 환경변수:

```yaml
REDIS_HOST: redis
REDIS_PORT: 6379
ORDER_CACHE_TTL_SECONDS: 300
```

왜 `redis`를 host로 쓸까?

```text
Docker Compose 내부 네트워크에서는 서비스 이름이 DNS 이름처럼 동작합니다.
그래서 order-service 컨테이너는 redis:6379로 Redis 컨테이너에 접속합니다.
```

로컬 Mac에서 Redis를 확인할 때는 포트 매핑 때문에 `localhost:16379`를 사용합니다.

```bash
docker exec commerce-redis redis-cli keys 'order:*'
```

## 11. Kafka 역할

Kafka는 주문 생성 이벤트를 저장하는 message broker입니다.

현재 서비스:

```yaml
kafka:
  image: apache/kafka:4.0.0
```

핵심 설정:

| 설정 | 의미 |
| --- | --- |
| `KAFKA_PROCESS_ROLES` | 단일 컨테이너가 controller와 broker 역할을 함께 수행 |
| `KAFKA_LISTENERS` | Kafka가 실제로 바인딩할 listener |
| `KAFKA_ADVERTISED_LISTENERS` | 클라이언트에게 알려줄 접속 주소 |
| `KAFKA_AUTO_CREATE_TOPICS_ENABLE` | topic이 없을 때 자동 생성 허용 |

왜 listener가 두 개일까?

```text
Compose 내부 클라이언트(order-service)는 kafka:9092로 접속합니다.
Mac 로컬에서 직접 붙는 클라이언트는 localhost:19092로 접속합니다.
```

그래서 advertised listener를 둘 다 둡니다.
