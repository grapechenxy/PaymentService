# PaymentService

Java 25 / Spring Boot 4 service that consumes payment messages from RabbitMQ, calls an external REST API, retries non-4xx failures daily, and stores terminal failures in MongoDB. Local docker-compose brings up RabbitMQ (with UI), MongoDB, a configurable mock API, and seeds 5 test messages.

## Prerequisites
- JDK 25
- Maven 3.9+
- Docker + Docker Compose

## Running locally
1) Start infrastructure (RabbitMQ + UI at http://localhost:15672, MongoDB, mock API at http://localhost:8090, seeded messages):
```
docker-compose up -d rabbitmq mongodb mock-api rabbitmq-seeder
```

2) Run the service:
```
mvn spring-boot:run
```

## Key configuration
- Messaging: exchange `payments.exchange`, queue `payments.queue`, DLQ `payments.dlq`, routing key `payments.create`, DL routing key `payments.create.dead`.
- External API: POST `/{userId}/payment` with `paymentId`, `paymentType`, `paymentInfo`, `amount`.
- Retry: non-4xx responses/timeouts retried daily (default max total attempts = 4, including initial). 4xx are persisted immediately.
- MongoDB failures: stored in `payment_attempts` collection with payload, timestamps, status, and error message.
- Logging: structured JSON via Logback/logstash encoder, including correlation and message IDs.

## Mock API controls
- Env `MOCK_MODE` options: `success`, `fail4xx`, `fail5xx`, `alternate` (default), `random`, `timeout`.
- Env `MOCK_RANDOM_SUCCESS_PCT` for `random` mode (default 50).
- Per-call override: `?forceStatus=200|400|422|500|503|timeout`.

## Seeding
- `rabbitmq-seeder` publishes 5 sample messages to `payments.exchange` with routing key `payments.create` at startup. Payloads live in `ops/rabbitmq/payloads`.
