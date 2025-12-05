# Project: PaymentService

## 1. Goal
A Spring Boot backend that consumes payment messages from RabbitMQ, calls an external REST API to forward payments, retries transient failures, and persists terminal failures in MongoDB.

## 2. Core Features (MVP)
- Consume messages from RabbitMQ and call the external REST API for each message.
- Message schema:
  - `id` (UUID), `userId`, `paymentId`, `paymentType` (swish | bankaccount | pg | bg), `paymentInfo`, `amount` (decimal string), `createdAt` (ISO-8601), optional `correlationId`, `currency` (sek | usd | euro).
- Retry rules:
  - HTTP 4xx → no retry; persist failure immediately.
  - Non-4xx errors/timeouts → retry once per day for 3 days (max attempts = initial + 3) at 00:00; use jitter if desired.
  - After max attempts, persist with last error.

## 3. Non-Functional Requirements
- Logging (structured/modern for Java):
  - MQ consume: log all message fields.
  - REST call: log userId, timestamp, paymentId, restURL.
  - REST response: log userId, timestamp, paymentId, error message (if any).

## 4. Tech / Constraints
- Java 25, Spring Boot 4.0.0, RabbitMQ 4.2.1, MongoDB 8.2.
- Provide docker-compose to run RabbitMQ (with UI) and MongoDB locally.
- External API mocked locally; no auth.

## 5. API Contract (outbound call)
- URL: `http://localhost:8090/{userId}/payment/{paymentId}`
- Request body (all required):
  - `paymentType` (swish | bankaccount | pg | bg), `paymentInfo`, `amount`, `currency`.
- Example responses (JSON):
  - Success (200/201):
    ```
    {
      "status": "accepted",
      "paymentId": "123",
      "traceId": "abc-123"
    }
    ```
  - Fatal failure (4xx, e.g., 400):
    ```
    {
      "status": "error",
      "code": "INVALID_PAYMENT_INFO",
      "message": "Payment info format is invalid",
      "paymentId": "123",
      "traceId": "abc-123"
    }
    ```
  - Retriable failure (5xx, e.g., 503):
    ```
    {
      "status": "error",
      "code": "SERVICE_UNAVAILABLE",
      "message": "Upstream unavailable, try again later",
      "paymentId": "123",
      "traceId": "abc-123"
    }
    ```

## 6. MongoDB Persistence Model (failures)
```
{
  "userId": "999",
  "paymentId": "123",
  "paymentType": "bankaccount",
  "paymentInfo": "123456",
  "amount": "1000.00",
  "currency": "sek",
  "timestamp": "2025-10-10 10:10:10",
  "error": "404 not found"
}
```

## 7. RabbitMQ
- Message schema (MQ payload):
  - `id` (UUID), `userId`, `paymentId`, `paymentType` (swish | bankaccount | pg | bg), `paymentInfo`, `amount` (decimal string), `currency` (sek | usd | euro), `createdAt` (ISO-8601), optional `correlationId`.
  - Reject/route to DLQ on missing/invalid fields or invalid patterns.
- Mapping to external API call:
  - URL: `http://localhost:8090/{userId}/payment/{paymentId}`.
  - Body: `paymentType`, `paymentInfo`, `amount`, `currency`.
  - Success = HTTP 2xx; HTTP 4xx fatal (persist, no retry); non-4xx retried daily up to 3 attempts, then persisted with error.
- MQ bindings (minimal local setup):
  - Exchange: `payments.exchange` (topic/direct).
  - Queue: `payments.queue` bound with routing key `payments.create`.
  - DLQ: `payments.dlq` bound with routing key `payments.create.dead`; main queue uses DLX `payments.exchange` + DLK `payments.create.dead`; max redeliveries ~3 before DLQ.
  - Listener uses manual acks; ack on success, reject/requeue on transient errors, reject/nack to DLQ on malformed/unrecoverable.
- Validation rules:
  - Required fields present; `paymentType` in enum; `id` valid UUID; `createdAt` ISO-8601; payload ≤ 10 KB.
  - `paymentInfo` patterns:
    - swish: E.164 phone (`+4670...`)
    - bankaccount: 10–34 alphanumerics (IBAN-like)
    - pg: 2–8 digits with optional dash + 2–4 check digits
    - bg: 2–8 digits with optional dash + 1–4 check digits
  - Idempotency: treat `id` as unique; duplicates ignored/acked.
- Retry schedule:
  - Initial attempt on consume; retriable failures at 00:00 next day, then daily for 3 days total (initial + 3). Add optional jitter 0–15 minutes. Retry only non-4xx/timeouts; 4xx stored immediately.

## 8. Out of Scope / Local Notes
- Run locally via docker-compose (RabbitMQ + UI, MongoDB, mock API).
- Seed 5 test messages into RabbitMQ on startup (see payloads).
- RabbitMQ UI: http://localhost:15672 (guest/guest).
- Mock API: parameterized responses; toggle failure codes/patterns (env/query); default alternate success/fail.
