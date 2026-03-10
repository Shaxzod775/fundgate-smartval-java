# FundGate SmartVal

AI-powered startup evaluation and valuation platform built with Java 21 and Spring Boot 3.4.1.

## Tech Stack

- **Java 21** + **Spring Boot 3.4.1** + **Maven** multi-module
- **PostgreSQL 16** — primary database
- **Redis 7** — caching and rate limiting
- **RabbitMQ 3.13** — async messaging between services
- **Apache Kafka** — event streaming and audit logs
- **AWS Bedrock** (Claude) — AI analysis engine
- **Firebase** — authentication and cloud storage
- **Nginx** — reverse proxy with rate limiting
- **Docker** + **Kubernetes** — containerization and orchestration

## Modules

| Module | Port | Description |
|--------|------|-------------|
| `smartval-common` | — | Shared DTOs, security, config, events |
| `smartval-gateway` | 8080 | API Gateway with rate limiting |
| `smartval-fundgate` | 8081 | AI startup analysis (6 specialized agents) |
| `smartval-valuation` | 8082 | Berkus, Scorecard, Risk Factor valuation |
| `smartval-submission` | 8083 | Startup submission intake |
| `smartval-email` | 8084 | Email notifications (Thymeleaf templates) |
| `smartval-chat` | 8085 | AI chat + document generation |
| `smartval-crm` | 8086 | CRM AI assistant with tool use |
| `smartval-spam` | 8087 | AI spam detection |
| `smartval-scheduler` | 8088 | Scheduled reports + Telegram bot |

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.9+
- PostgreSQL 16+
- Redis 7+
- RabbitMQ 3.13+

### Build

```bash
mvn clean compile
```

### Run with Docker Compose

```bash
docker-compose up -d
```

This starts all services with PostgreSQL, Redis, RabbitMQ, Kafka, and Nginx.

### Run Individual Service

```bash
mvn spring-boot:run -pl smartval-gateway
```

## Kubernetes

```bash
kubectl apply -f k8s/namespace.yaml
kubectl apply -f k8s/
```

## API Documentation

Each service exposes Swagger UI at `http://localhost:<port>/swagger-ui.html`

## Environment Variables

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | localhost | PostgreSQL host |
| `DB_PORT` | 5432 | PostgreSQL port |
| `DB_NAME` | fundgate | Database name |
| `DB_USER` | fundgate | Database user |
| `DB_PASS` | fundgate_pass | Database password |
| `REDIS_HOST` | localhost | Redis host |
| `RABBITMQ_HOST` | localhost | RabbitMQ host |
| `AWS_REGION` | us-east-1 | AWS region for Bedrock |
| `INTERNAL_API_KEY` | — | Service-to-service auth key |
