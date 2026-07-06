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
- Basic Spring context test passing

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

## Learning Log

Each major stage should leave a short document under `docs/`.

- `docs/00-architecture.md`
- `docs/01-linux.md`
- `docs/02-docker.md`
- `docs/03-docker-compose.md`
- `docs/04-kafka.md`
- `docs/05-kubernetes.md`
- `docs/06-helm.md`
- `docs/07-cicd.md`
- `docs/08-monitoring.md`
- `docs/09-troubleshooting.md`
- `docs/10-terraform-aws.md`
