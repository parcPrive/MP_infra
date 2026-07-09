# Roadmap

앞으로의 작업을 단계별로 정리합니다.

## 현재 완료

```text
1. 프로젝트 구조 생성
2. Git/GitHub 연결
3. order-service 생성
4. health-check API
5. 주문 생성/조회 API
6. Dockerfile
7. Docker 이미지 빌드
8. Docker Compose
9. NGINX reverse proxy
10. PostgreSQL 컨테이너
11. Redis 컨테이너
12. order-service -> PostgreSQL 저장
13. order-service -> Redis 조회 캐시
14. order-service -> Kafka order.created 이벤트 발행
15. payment-service -> Kafka order.created 이벤트 소비
16. payment-service -> Kafka payment.completed 이벤트 발행
```

## 완료: Redis 캐시

목표:

```text
GET /orders/{id} 조회 시 Redis 캐시를 먼저 확인하고,
캐시에 없으면 PostgreSQL에서 조회한 뒤 Redis에 저장합니다.
```

완료한 것:

```text
Redis key/value 구조
cache hit / cache miss
TTL
Spring Data Redis
Docker Compose 환경변수
```

예상 흐름:

```text
GET /orders/1
  |
  v
Redis에 order:1 있음?
  |
  +-- yes -> Redis 값 반환
  |
  +-- no -> PostgreSQL 조회 -> Redis 저장 -> 응답
```

## 완료: Kafka Producer

목표:

```text
주문 생성 시 order.created 이벤트를 Kafka로 발행합니다.
```

처음에는 `payment-service` 하나만 소비자로 붙입니다.

현재 완료한 범위:

```text
Kafka broker 컨테이너 실행
order.created topic 자동 생성
order-service Kafka producer 구현
주문 생성 시 order.created JSON 이벤트 발행
Kafka CLI consumer로 메시지 확인
```

예상 흐름:

```text
POST /orders
  |
  v
order-service
  |
  +-- PostgreSQL 저장
  |
  +-- Kafka order.created 발행
          |
          v
      payment-service 소비
```

배울 것:

```text
Topic
Producer
Consumer
Consumer Group
message key/value
event-driven architecture
```

## 완료: payment-service Kafka Consumer

목표:

```text
payment-service가 order.created topic을 소비합니다.
```

배울 것:

```text
@KafkaListener
Consumer Group
Offset
Consumer Lag
역직렬화
```

검증 완료:

```text
payment-service 컨테이너 실행
payment-service-group 생성
order.created 메시지 소비
Payment completed 로그 확인
```

## 완료: payment.completed 이벤트 발행

목표:

```text
payment-service가 결제 처리 후 payment.completed 이벤트를 Kafka에 발행합니다.
```

예상 흐름:

```text
order.created
  |
  v
payment-service
  |
  v
payment.completed
```

검증 완료:

```text
payment.completed topic 생성
payment.completed 메시지 발행
Kafka CLI consumer로 메시지 확인
```

## 다음 1단계: inventory-service Kafka Consumer

목표:

```text
inventory-service가 payment.completed 이벤트를 소비해 재고 차감을 흉내냅니다.
```

예상 흐름:

```text
payment.completed
  |
  v
inventory-service
  |
  v
Inventory decreased 로그 출력
```

## 다음 2단계: Kubernetes

목표:

```text
Docker Compose로 실행하던 서비스를 kind Kubernetes로 옮깁니다.
```

배울 것:

```text
Namespace
Deployment
Service
Ingress
ConfigMap
Secret
Probe
HPA
Rolling Update
Rollback
```

## 다음 4단계: Helm

목표:

```text
반복되는 Kubernetes YAML을 Helm Chart로 템플릿화합니다.
```

배울 것:

```text
Chart.yaml
values.yaml
templates/
helm install
helm upgrade
helm rollback
```

## 다음 5단계: CI/CD

목표:

```text
GitHub push 이후 Jenkins가 테스트, 빌드, Docker 이미지 생성, 배포를 수행합니다.
```

배울 것:

```text
Jenkinsfile
Pipeline stage
Gradle test/build
Docker build
Helm deploy
```

## 다음 6단계: Monitoring

목표:

```text
Prometheus가 order-service와 Kubernetes 메트릭을 수집하고,
Grafana에서 대시보드로 확인합니다.
```

배울 것:

```text
Spring Boot Actuator
Micrometer
Prometheus scrape
Grafana dashboard
Alert rule
```

## 다음 7단계: Terraform/AWS

목표:

```text
로컬에서 만든 구성을 AWS로 이전할 수 있게 인프라를 코드화합니다.
```

초기 순서:

```text
1. VPC
2. Subnet
3. Security Group
4. EC2
5. RDS
6. ECR
7. EKS
```

## 학습 원칙

이 프로젝트에서는 기능을 많이 만드는 것보다 다음을 우선합니다.

```text
왜 이 기술을 쓰는지 설명할 수 있는가
어떤 명령어로 상태를 확인하는가
장애가 났을 때 어디를 볼 것인가
로컬에서 클라우드로 어떻게 옮길 것인가
```
