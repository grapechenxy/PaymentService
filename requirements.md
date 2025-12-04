# Project: PaymentService

## 1. Goal
This is a backend service, it gets messages from a target queue in RabbitMQ, and call an external api to sent the payment info further to an external system


## 2. Core Features (MVP)
List as user stories:

- PaymentService has a rest client, to call an external rest API
- As soon as there is a new message in the target queue, the PaymentService should pick up the message and call the external API
- You can name the target queue in RabbitMQ, 
- The application need to be able to retry the failed rest call(non 4xx-errors), in the next 3 day, 1 time per day at time 00:00. If it fails at the end, save the data and the response message(failed reason) to MongoDB database. the 4xx should be directly put to database and no retry.


## 3. Non-Functional Requirements
- Observability: logging each message the application consumes from MQ, and log each call to the api and the response, using the morden way of logging for java.

- log fields: 
for incoming messages from MQ, log every field from message
for rest call: log userId, timestamp, paymentId, restURL
for rest call response: log userId, timestamp, paymentId and error message from response


## 4. Tech / Constraints
- Java 25, Springboot 4.0.0, RabbitMQ 4.2.1, MongoDB 8.2
- The application need a docker-compose file, to run a RabbitMQ AND MongoDB locally 

## 5. API contract:
- url: localhost:8090/{userId}/payment/{paymentId}, 
- request body, all fields are required: 
{
    "paymentType": "bankaccount",
    "paymentInfo": "123456",
    "amount": "1000.00",
    ç
}
(paymentType in enum of [swish, bankaccount, pg, bg])

- Example responses (JSON):

  - Success (200/201):

  {
    "status": "accepted",
    "paymentId": "123",
    "traceId": "abc-123"
  }

  - Fatal failure (4xx, e.g., 400):

  {
    "status": "error",
    "code": "INVALID_PAYMENT_INFO",
    "message": "Payment info format is invalid",
    "paymentId": "123",
    "traceId": "abc-123"
  }

  - Retriable failure (5xx, e.g., 503):

  {
    "status": "error",
    "code": "SERVICE_UNAVAILABLE",
    "message": "Upstream unavailable, try again later",
    "paymentId": "123",
    "traceId": "abc-123"
  }

## 6. MongoDB persistence model
-  persistence model for failures: {
    "userId": "999"
    "paymentId": "123",
    "paymentType": "bankaccount",
    "paymentInfo": "123456",
    "amount": "1000.00",
    "currency": "sek",
    "timestamp": "2025-10-10 10:10:10",
    "error": "404 not found"
}

## 7. RabbitMQ: 
- Message schema (MQ payload)
      - id: UUID string (message id / idempotency key)
      - userId: non-empty string
      - paymentId: non-empty string
      - paymentType: enum swish | bankaccount | pg | bg
      - paymentInfo: string shaped per type (Swish E.164 phone; bankaccount 10–34 alphanumerics; pg/bg Swedish patterns)
      - amount: decimal string (e.g., "1000.00")
      - "currency": "sek", enum sek | usd | euro 
      - createdAt: ISO-8601 timestamp
      - Optional: correlationId
      - Reject/route to DLQ on missing/invalid fields or invalid patterns.
- Mapping to external API call
    - URL: http://localhost:8090/{userId}/payment/{paymentId} (path param from message userId)
    - Request body (all required):
        - paymentType ← message paymentType
        - paymentInfo ← message paymentInfo
        - amount ← message amount
    - Correlate logs/metrics using id (message id) and correlationId if present.
    - Success determined by HTTP 2xx; HTTP 4xx is fatal (persist, no retry); non-4xx retried daily up to 3 attempts, then persisted with error.
- MQ bindings (minimal local setup)
      - Exchange: payments.exchange (topic/direct).
      - Queue: payments.queue bound with routing key payments.create.
      - DLQ: payments.dlq bound with routing key payments.create.dead; set main queue DLX to payments.exchange with DLK payments.create.dead; set max redeliveries (e.g., 3) before DLQ.
      - Listener uses manual acks; ack on success, reject/requeue on transient errors, reject/nack to DLQ on malformed/unrecoverable messages.
- Message contract examples
    - Routing key: payments.create.
    - Payload fields: id (UUID string), userId (non-empty string), paymentId (non-empty string), paymentType (swish | bankaccount | pg | bg), paymentInfo (string shaped per type), createdAt (ISO-8601), optional correlationId.
    - Example JSON: {"id":"...","userId":"999","paymentId":"123456", "paymentType":"swish","paymentInfo":"+46701234567","createdAt":"2024-05-01T10:00:00Z"}.
- Validation rules
    - Common: all required fields present; reject null/empty; paymentType must be one of allowed enums (case-sensitive); createdAt parsable ISO-8601; id valid UUID.
    - name: length 1–100; trim spaces; no control characters.
    - paymentInfo by type (example patterns):
        - swish: E.164 phone (e.g., +4670...).
        - bankaccount: IBAN or country-specific account format (define one; for minimal local, require 10–34 alphanumerics, no spaces).
        - pg: 2–8 digits plus optional dash/2–4 check digits (Swedish plusgiro pattern).
        - bg: 2–8 digits plus optional dash/1–4 check digits (Swedish bankgiro pattern).
    - Size limits: payload ≤ 10 KB.
    - Malformed handling: log, do not retry API call, send to DLQ; include reason tag (missing field, invalid enum, invalid pattern).
    - Idempotency: treat id as unique; drop/ack duplicates or route to DLQ with duplicate reason.
- Retry schedule:
    - Attempts: initial call at consume time (attempt 0). If retriable failure: attempt 1 at next 02:00 UTC, attempt 2 at 02:00 UTC the following day, attempt 3 (final) at 02:00 UTC the day after. After attempt 3 fails, persist as terminal.
    - Jitter: add a random delay of 0–15 minutes to each scheduled retry to avoid thundering herd.
    - Backoff within a day: none (requirement is 1/day). If you want a safety net, allow one short safety retry after, say, 5 minutes only for connection timeouts, but still keep the main daily schedule.
    - Retry window anchoring: compute next-run as “next 02:00 UTC after failure” to survive restarts; store next-run timestamp with the record.
    - Error classification: only non-4xx and timeouts are retried; 4xx go straight to persistence with no retry.
  



## 5. Out of Scope
- In v1, I need to run the application locally, with all the dependencies from docker-compose and minimum setup, please skip authentication and so on;
- for RabbitMQ in docker-compose, i want to init 5 test messages; and i need a RabbitMQ ui where i can login via broswer and adminustrate the queue;

RabbitMQ ui
  - URL/port: http://localhost:15672
  - RabbitMQ management defaults (guest/guest on port 15672).
 
 Here are five ready-to-seed MQ messages (use routing key payments.create on payments.exchange; payload JSON per message):

  {
    "id": "11111111-1111-4111-8111-111111111111",
    "userId": "user-001",
    "paymentId": "pay-001",
    "paymentType": "swish",
    "paymentInfo": "+46701234567",
    "amount": "250.00",
    "currency": "sek",
    "createdAt": "2025-01-10T10:00:00Z",
    "correlationId": "corr-001"
  }


  {
    "id": "22222222-2222-4222-8222-222222222222",
    "userId": "user-002",
    "paymentId": "pay-002",
    "paymentType": "bankaccount",
    "paymentInfo": "SE3550000000054910000003",
    "amount": "1000.00",
    "currency": "sek",
    "createdAt": "2025-01-10T10:05:00Z",
    "correlationId": "corr-002"
  }


  {
    "id": "33333333-3333-4333-8333-333333333333",
    "userId": "user-003",
    "paymentId": "pay-003",
    "paymentType": "pg",
    "paymentInfo": "123456-7",
    "amount": "75.50",
    "currency": "sek",
    "createdAt": "2025-01-10T10:10:00Z",
    "correlationId": "corr-003"
  }


  {
    "id": "44444444-4444-4444-8444-444444444444",
    "userId": "user-004",
    "paymentId": "pay-004",
    "paymentType": "bg",
    "paymentInfo": "9876-5",
    "amount": "5000.00",
    "currency": "usd",
    "createdAt": "2025-01-10T10:15:00Z",
    "correlationId": "corr-004"
  }


  {
    "id": "55555555-5555-4555-8555-555555555555",
    "userId": "user-005",
    "paymentId": "pay-005",
    "paymentType": "swish",
    "paymentInfo": "+46709876543",
    "amount": "15.99",
    "currency": "euro",
    "createdAt": "2025-01-10T10:20:00Z",
    "correlationId": "corr-005"
  }  
- Exact toggle mechanism for mock failure codes: use env parameter in application.yml file.

 - Default behavior: return HTTP 200 with a small JSON body (e.g., status/traceId). Classify success on 2xx, fatal on 4xx, retriable on 5xx/timeouts.
  - Env flags for patterns (read once at startup):
      - MOCK_MODE=success → always 200.
      - MOCK_MODE=fail4xx → always chosen 4xx (e.g., 400/422).
      - MOCK_MODE=fail5xx → always chosen 5xx (e.g., 503).
      - MOCK_MODE=alternate → alternate 200/503 per call.
      - MOCK_MODE=random with MOCK_RANDOM_SUCCESS_PCT (e.g., 50) → probability-based mix of 200 vs 503.
  - Per-call overrides (for tests) via query/header:
      - ?forceStatus=200|400|422|500|503 or header X-Mock-Status: 503 to override the next response.
  - Response shapes (consistent JSON):
      - Success (200): { "status": "accepted", "paymentId": "...", "traceId": "..." }
      - Fatal (4xx): { "status": "error", "code": "INVALID_PAYMENT_INFO", "message": "...", "paymentId": "...", "traceId": "..." }
      - Retriable (5xx): { "status": "error", "code": "SERVICE_UNAVAILABLE", "message": "...", "paymentId": "...", "traceId": "..." }
  - Timeouts simulation (optional): MOCK_MODE=timeout or forceStatus=timeout to sleep past client timeout.


