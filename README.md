# Local Cloud Commerce Platform

Local Cloud Commerce Platform is a cloud infrastructure learning and portfolio project.
It starts from a local macOS development environment and gradually expands into Docker,
Kafka, Kubernetes, CI/CD, monitoring, Terraform, and AWS.

The goal of this project is not to build a large commerce application. The application
code stays intentionally small so the main focus can stay on packaging, deployment,
observability, operations, and cloud migration.

## Project Goal

Build a small Spring Boot order service and evolve it through the following stages:

- Run a Spring Boot service locally
- Connect the service to PostgreSQL and Redis
- Package the service as a Docker image
- Run the local stack with Docker Compose and NGINX
- Add Kafka-based event processing between services
- Deploy the services to a local Kubernetes cluster
- Manage Kubernetes resources with Helm
- Automate build and deployment with Jenkins
- Monitor the platform with Prometheus and Grafana
- Document troubleshooting scenarios
- Move selected infrastructure to AWS with Terraform

## Current Status

- Git repository initialized
- Base project directory structure created
- `order-service` Spring Boot project created
- Java 17 configured
- Spring Boot 4.1.0 configured
- Gradle Wrapper configured
- `GET /health-check` endpoint created
- `POST /orders` and `GET /orders/{id}` APIs created
- Dockerfile created
- Docker Compose stack created with NGINX, PostgreSQL, Redis, and order-service
- `order-service` connected to PostgreSQL
- `GET /orders/{id}` connected to Redis cache
- `POST /orders` publishes `order.created` events to Kafka
- `payment-service` consumes `order.created` events from Kafka
- `payment-service` publishes `payment.completed` events to Kafka
- `inventory-service` consumes `payment.completed` events from Kafka
- Kafka consumer retry and DLT handling added for failed messages

## Learning Hub

Start here when studying the project:

- [Learning Index](docs/00-learning-index.md)
- [Current Progress](docs/01-current-progress.md)
- [Runbook](docs/02-runbook.md)
- [order-service Code Guide](docs/03-order-service-code-guide.md)
- [Docker Compose Stack Guide](docs/04-docker-compose-stack.md)
- [Roadmap](docs/05-roadmap.md)
- [Kafka Guide](docs/06-kafka-guide.md)
- [payment-service Consumer Guide](docs/07-payment-service-consumer-guide.md)
- [inventory-service Consumer Guide](docs/08-inventory-service-consumer-guide.md)
- [Kafka Retry and DLT Guide](docs/09-kafka-retry-dlt-guide.md)

## First Milestone

The first milestone is to complete a minimal local application stack.

- Implement `order-service`
- Add order create and order lookup APIs
- Connect `order-service` to PostgreSQL
- Add Actuator health checks
- Create a Dockerfile for `order-service`
- Run `order-service`, PostgreSQL, Redis, and NGINX with Docker Compose

## Target Architecture

```text
Client
  |
  v
NGINX
  |
  v
order-service
  |
  +--> PostgreSQL
  |
  +--> Redis
  |
  +--> Kafka
          |
          +--> payment-service
          +--> inventory-service
          +--> *.DLT failed-message topics
          +--> notification-service
```

## Repository Structure

```text
.
├── apps
│   ├── order-service
│   ├── payment-service
│   ├── inventory-service
│   └── notification-service
├── compose
├── docker
├── nginx
├── k8s
├── helm
├── cicd
├── monitoring
├── terraform
└── docs
```

## Tech Stack

### Application

- Java 17
- Spring Boot 4.1.0
- Gradle
- Spring Web MVC
- Spring Data JPA
- Spring Boot Actuator
- Validation
- Lombok

### Data

- PostgreSQL
- Redis

### Messaging

- Apache Kafka
- Kafka Topic
- Consumer Group
- Retry Topic
- Dead Letter Queue

### Container and Orchestration

- Docker
- Docker Compose
- NGINX
- Kubernetes
- kind
- Helm

### CI/CD

- Jenkins
- Jenkinsfile
- GitHub
- Docker Registry
- Helm Deploy

### Monitoring and Operations

- Prometheus
- Grafana
- Alertmanager
- Spring Boot Actuator
- Micrometer
- k6
- kubectl
- docker logs
- journalctl

### Infrastructure as Code and Cloud

- Terraform
- AWS VPC
- AWS EC2
- AWS RDS
- AWS ECR
- AWS EKS
- AWS ALB
- AWS IAM

## Local Development

Run the current `order-service` tests:

```bash
cd apps/order-service
./gradlew test
```

Run a compile check:

```bash
cd apps/order-service
./gradlew compileJava
```

Run the full local Docker Compose stack:

```bash
docker compose -f compose/docker-compose.app.yml up --build -d
```

Check the API through NGINX:

```bash
curl http://localhost:8082/health-check
```

Stop the local stack:

```bash
docker compose -f compose/docker-compose.app.yml down
```

Read Kafka messages:

```bash
docker exec commerce-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic order.created \
  --from-beginning \
  --max-messages 1 \
  --timeout-ms 10000
```

Read payment completed events:

```bash
docker exec commerce-kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 \
  --topic payment.completed \
  --from-beginning \
  --max-messages 1 \
  --timeout-ms 10000
```

Check payment-service:

```bash
curl http://localhost:8083/actuator/health
docker logs --tail 100 payment-service | grep 'Payment completed'
```

Check inventory-service:

```bash
curl http://localhost:8084/actuator/health
docker logs --tail 100 inventory-service | grep 'Inventory decreased'
```

## Learning Log

Each major stage should leave a short document under `docs/`.

- [Learning Index](docs/00-learning-index.md)
- [Current Progress](docs/01-current-progress.md)
- [Runbook](docs/02-runbook.md)
- [order-service Code Guide](docs/03-order-service-code-guide.md)
- [Docker Compose Stack Guide](docs/04-docker-compose-stack.md)
- [Roadmap](docs/05-roadmap.md)
- [Kafka Guide](docs/06-kafka-guide.md)
- [payment-service Consumer Guide](docs/07-payment-service-consumer-guide.md)
- [inventory-service Consumer Guide](docs/08-inventory-service-consumer-guide.md)
