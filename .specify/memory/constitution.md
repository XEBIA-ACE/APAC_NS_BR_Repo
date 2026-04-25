# Constitution — Notification Service (SRV-004)

> Living guardrails for the Notification Service within the Swiss Medication Management System.
> This document governs design, implementation, and operational decisions for SRV-004.

## Service Identity

| Attribute | Value |
|---|---|
| **Service ID** | SRV-004 |
| **Service Name** | Notification Service |
| **Service Type** | Infrastructure |
| **Runtime** | Node.js 18 · Express |
| **Primary Store** | MongoDB (DocumentDB in production) |
| **Cache / Scheduler** | Redis (ElastiCache) |
| **Event Bus** | Apache Kafka (MSK) |
| **Delivery Channels** | Firebase Cloud Messaging, Email (SES/SendGrid), SMS (Twilio/SNS) |
| **Deployment** | EKS · 4 replicas · 0.5 CPU, 1 GB RAM each |
| **Related Features** | F-04, F-06, F-07, F-09 |

## Quality Principles

1. **Delivery Reliability Over Speed** — A notification must never silently disappear. Every delivery attempt MUST be logged. Failed deliveries MUST be retried with exponential backoff and ultimately written to a dead-letter queue.
2. **User Preference Supremacy** — The user's channel selection, quiet hours, language, and opt-out preferences MUST override every system-level default. The only exception is `priority: urgent`, which MAY bypass quiet hours.
3. **Multi-Language First** — All user-facing notification content MUST be rendered via templates with locale resolution (en, de, fr, it). English is the fallback locale. Hardcoded strings are forbidden.
4. **Idempotency** — Kafka consumers and REST endpoints MUST be idempotent. Duplicate event delivery MUST NOT result in duplicate user notifications.
5. **Graceful Degradation** — If the Localization Service is unavailable, fall back to English. If the Medication Service is unavailable, use a generic template. If a delivery channel is down, attempt remaining channels and schedule a retry.

## Testing Standards

- Domain model unit tests: 100% coverage for state transitions (`markAsSent`, `markAsFailed`, `isActiveAt`).
- Business service tests: Mock all external dependencies; verify orchestration, bundling, and rate-limiting logic.
- Integration tests: All REST endpoints via supertest; Kafka consumer/producer with test containers.
- Contract tests: Pact or equivalent for UserServiceClient, MedicationServiceClient, LocalizationServiceClient.
- No production deployment without green CI on all tiers.

## Performance Guardrails

| Metric | Target |
|---|---|
| 95th-percentile notification delivery (event-to-channel) | ≤ 30 seconds |
| REST API p95 response time | ≤ 200 ms |
| Kafka consumer lag | ≤ 1 000 messages |
| WebSocket push latency | ≤ 500 ms |
| Notification history query (paginated) | ≤ 150 ms |

## Security Guardrails

- All API endpoints (except health probes) MUST require a valid JWT bearer token validated at the SecurityFilter layer.
- Scopes: `notification:read`, `notification:write`, `notification:admin`.
- User-scoped endpoints MUST enforce row-level access — users can only see their own data.
- Sensitive PII (phone numbers, email addresses) MUST NOT appear in application logs.
- Secrets (API keys, DB credentials) MUST be loaded from AWS Secrets Manager, NEVER from environment variables in plaintext.

## Data Guardrails

- Notification retention: 90 days. Data older than 90 days MUST be archived or purged automatically via a TTL index or scheduled job.
- Delivery logs: Retained for 180 days for audit and compliance.
- Redis cache TTLs: Preferences = 15 min, Templates = 1 hr, Translations = 1 hr, User contact info = 5 min.
- MongoDB indexes MUST be defined for: `userId + createdAt`, `status`, `scheduledAt`, `type`.

## Technology Constraints

- Runtime MUST be Node.js 18+ with TypeScript. No plain JavaScript.
- ORM/ODM: Mongoose for MongoDB. No raw driver calls outside the repository layer.
- HTTP clients: axios with circuit-breaker wrapper (opossum). 5 s timeout, 50% failure threshold, 30 s reset.
- Kafka client: kafkajs. Consumer group: `notification-service-group`. Dead-letter topic: `notification-events-dlq`.
- Schema validation: Zod for all request/response DTOs.
- No business logic in controllers. Controllers are thin; all logic lives in services.

---