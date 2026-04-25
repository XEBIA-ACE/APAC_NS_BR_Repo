# Tasks: Notification Service (SRV-004)

> Ordered, actionable task breakdown for an AI coding agent.

---

## Phase 1 — Project Scaffold & Domain Models

- [ ] **T-001** Initialize Node.js project with TypeScript 5, Express 4, ESLint (strict), Prettier, Jest + ts-jest, and `tsconfig.json` (strict mode).
- [ ] **T-002** Define enum types in `src/business/models/enums.ts`: `NotificationType` (MEDICATION_REMINDER, REFILL_REMINDER, HEALTH_EVENT, PRESCRIPTION_EXPIRY, DRUG_INTERACTION, INSURANCE_UPDATE), `Channel` (PUSH_NOTIFICATION, SMS, EMAIL, IN_APP), `Priority` (low, normal, high, urgent), `NotificationStatus` (unread, read), `DeliveryState` (pending, sent, delivered, failed, not_applicable).
- [ ] **T-003** Define `NotificationEntity` in `src/business/models/NotificationEntity.ts` with all fields from the plan. Implement `markAsSent()`, `markAsFailed(reason)`, `isScheduled()`, `canRetry()` methods with unit tests.
- [ ] **T-004** Define `NotificationPreference` in `src/business/models/NotificationPreference.ts` with `isActiveAt(time: Date, timezone: string): boolean` for quiet-hours checking and `supportsChannel(channel: Channel): boolean`. Unit tests for quiet-hour edge cases (overnight wrap, timezone offsets).
- [ ] **T-005** Define `NotificationTemplate`, `Device`, `DeliveryResult` (value object), and `NotificationContent` models.
- [ ] **T-006** Define request/response DTOs using Zod schemas: `CreateNotificationRequest`, `NotificationResponse`, `NotificationPreferencesUpdate`, `BatchNotification`, `TestNotification`, `NotificationStatusUpdate`, `DeviceRegistration`. Export both types and runtime validators.

## Phase 2 — Data Access Layer

- [ ] **T-007** Configure Mongoose connection in `src/config/database.config.ts` with connection pooling (min: 5, max: 20), retry on disconnect, and `readPreference: 'secondaryPreferred'` for reads.
- [ ] **T-008** Define Mongoose schemas in `src/data/schemas/` for all five collections (`notifications`, `notification_preferences`, `notification_templates`, `delivery_logs`, `devices`) including indexes and TTL indexes.
- [ ] **T-009** Implement `NotificationRepository` with: `save()`, `findById()`, `findByUserId(userId, pagination, filters)`, `findPendingNotifications(beforeTime)`, `updateStatus()`, `deleteById()`, `countUnreadByUserId()`.
- [ ] **T-010** Implement `TemplateRepository` with: `findByTypeAndLanguage()`, `findAll(filters)`, `save()`, `findById()`.
- [ ] **T-011** Implement `PreferenceRepository` with: `findByUserId()`, `upsert()`, `deleteByUserId()`.
- [ ] **T-012** Implement `DeliveryLogRepository` with: `appendLog()`, `findByNotificationId()`.
- [ ] **T-013** Implement device repository operations within `NotificationRepository` or as a separate `DeviceRepository`: `registerDevice()`, `findDevicesByUserId()`, `deleteDevice()`, `findByPushToken()`.

## Phase 3 — Infrastructure Layer

- [ ] **T-014** Configure Redis connection in `src/config/redis.config.ts` with ioredis, cluster-mode support, and connection retry.
- [ ] **T-015** Implement `CacheManager` in `src/infrastructure/cache/CacheManager.ts`: generic `get<T>()`, `set<T>(key, value, ttlSeconds)`, `invalidate(key)`, `invalidatePattern(glob)`.
- [ ] **T-016** Implement `RateLimiter` in `src/business/delivery/RateLimiter.ts` using Redis sliding-window algorithm. Key format: `ratelimit:{userId}:{channel}:{windowStart}`. Configurable limits per channel (default: 50/day).
- [ ] **T-017** Implement `SecurityFilter` middleware: extract JWT from `Authorization: Bearer <token>`, validate signature (RS256), extract userId and scopes, attach to `req.auth`. Return 401 if missing/invalid.
- [ ] **T-018** Implement `RequestValidator` middleware using Zod schemas. Return 400 with `ValidationError` response shape on failure.
- [ ] **T-019** Implement `ErrorHandler` middleware: catch all unhandled errors, map to standard `Error` / `ValidationError` JSON shape with `requestId`, `code`, `message`, `timestamp`. Never leak stack traces or PII.
- [ ] **T-020** Implement `MetricsCollector`: register Prometheus counters/histograms — `notifications_sent_total{channel,type,status}`, `notification_delivery_latency_seconds{channel}`, `kafka_consumer_lag`, `websocket_active_connections`, `http_request_duration_seconds{method,path,status}`.
- [ ] **T-021** Implement `ConfigurationManager`: load from environment variables with sensible defaults, expose typed config object, support feature flags.

## Phase 4 — Integration Layer — HTTP Clients

- [ ] **T-022** Implement `UserServiceClient` wrapping axios + opossum circuit breaker. Methods: `getUserById(userId)`, `getUserContactInfo(userId)`, `getUserLocale(userId)`. Cache results in Redis (5 min TTL).
- [ ] **T-023** Implement `MedicationServiceClient` wrapping axios + opossum. Methods: `getMedicationById(medId)`, `getMedicationSchedule(userId, medId)`. Fallback: return `null` (caller uses generic template).
- [ ] **T-024** Implement `LocalizationServiceClient` wrapping axios + opossum. Methods: `getTranslation(key, locale)`. Cache in Redis (1 hr TTL). Fallback: return English string.

## Phase 5 — Integration Layer — Delivery Adapters

- [ ] **T-025** Define `DeliveryStrategy` interface: `deliver(notification, preference): Promise<DeliveryResult>`, `supports(channel): boolean`, `getMaxRetries(): number`.
- [ ] **T-026** Implement `PushNotificationAdapter` (FCM): send notification payload to device tokens, handle invalid-token cleanup, retry up to 3× with exponential backoff.
- [ ] **T-027** Implement `EmailAdapter` (SES/SendGrid): send HTML email with responsive template, handle bounces, retry up to 3×.
- [ ] **T-028** Implement `SMSAdapter` (Twilio/SNS): send SMS, validate phone number format, handle carrier errors, retry up to 2×.

## Phase 6 — Integration Layer — Kafka

- [ ] **T-029** Configure kafkajs in `src/config/kafka.config.ts`: brokers, consumer group `notification-service-group`, producer config with idempotent mode.
- [ ] **T-030** Implement `KafkaEventConsumer`: subscribe to `medication-events`, `prescription-events`, `user-events`. For each message: check idempotency key in Redis → parse event → route to `NotificationService.processEvent()`. On 3 failures → publish to `notification-events-dlq`.
- [ ] **T-031** Implement `KafkaEventProducer`: `publishNotificationSent()`, `publishNotificationDelivered()`, `publishNotificationFailed()`, `publishPreferenceUpdated()` to `notification-events` topic.
- [ ] **T-032** Define TypeScript interfaces for all consumed and produced Kafka event payloads.

## Phase 7 — Business Layer

- [ ] **T-033** Implement `TemplateService`: `renderTemplate(type, locale, data)` — fetch template from Redis cache (fallback: MongoDB), replace `{{placeholders}}` with data, integrate with `LocalizationServiceClient` for translations.
- [ ] **T-034** Implement `PreferenceService`: `getPreferences(userId)` (Redis-cached), `updatePreferences(userId, update)` (persist + invalidate cache + publish Kafka event), `getDefaultPreferences()`.
- [ ] **T-035** Implement `NotificationBundler`: group notifications by `{userId, type}` within a 5-minute sliding window. Use Redis sorted set. On window close, merge grouped notifications into a single bundled notification.
- [ ] **T-036** Implement `DeliveryStatusTracker`: `recordAttempt(notificationId, channel, result)` → write to `delivery_logs`, update `deliveryStatus` on notification document, decide retry based on `DeliveryStrategy.getMaxRetries()`.
- [ ] **T-037** Implement `SchedulingService`: `scheduleNotification(notification)` → `ZADD` to Redis sorted set with score = scheduledAt timestamp. Polling job (`setInterval`, 60 s) → `ZRANGEBYSCORE` for due notifications → invoke `NotificationService.processScheduled()`.
- [ ] **T-038** Implement `NotificationService` — the core orchestrator. Methods:
  - `processEvent(event)` — full Kafka event → delivery pipeline (user lookup → preferences → rate limit → bundle → template → deliver → track → publish).
  - `sendNotification(request)` — REST-triggered delivery.
  - `sendTestNotification(userId, channel)`.
  - `processBatch(batchRequest)` — async batch processing with progress tracking.
  - `processScheduled(notification)` — for due scheduled notifications.

## Phase 8 — Presentation Layer

- [ ] **T-039** Implement `NotificationController`: `GET /api/v1/notifications` (paginated, filterable by status/type/sort), `GET /{id}`, `PUT /{id}/status`, `DELETE /{id}`, `POST /test`.
- [ ] **T-040** Implement `PreferenceController`: `GET /api/v1/notifications/preferences`, `PUT /preferences`, `GET /channels`, `PUT /channels/{channelId}`.
- [ ] **T-041** Implement `TemplateController`: `GET /api/v1/notifications/templates` with `type` and `language` query filters.
- [ ] **T-042** Implement device endpoints: `GET /api/v1/notifications/devices`, `POST /devices`, `DELETE /devices/{deviceId}`.
- [ ] **T-043** Implement history endpoint: `GET /api/v1/notifications/history` with `startDate`, `endDate`, `type`, pagination.
- [ ] **T-044** Implement batch endpoints: `POST /api/v1/notifications/batch` (admin), `GET /batch/{batchId}/status` (admin).
- [ ] **T-045** Implement `HealthCheckController`: `/health` (full component check), `/health/liveness` (simple), `/health/readiness` (dependency checks: MongoDB, Redis, Kafka, FCM, Email, SMS).
- [ ] **T-046** Implement `NotificationMapper`: entity → response DTO, request DTO → entity.

## Phase 9 — WebSocket

- [ ] **T-047** Implement `WebSocketHandler` using `ws` library: JWT authentication on connection handshake, in-memory Map for `userId → Set<WebSocket>`, heartbeat ping every 30 s, cleanup on disconnect.
- [ ] **T-048** Integrate `WebSocketHandler.pushToUser(userId, notification)` call inside `NotificationService` after successful delivery.

## Phase 10 — Testing

- [ ] **T-049** Unit tests: all domain models — `NotificationEntity` state machine, `NotificationPreference` quiet-hours (overnight wrap, same-day, disabled, timezone edge), template placeholder rendering.
- [ ] **T-050** Unit tests: `NotificationService` orchestration — mock all dependencies, verify correct call sequence for event processing, preference enforcement, bundling, rate-limiting.
- [ ] **T-051** Unit tests: `RateLimiter` (sliding window math), `NotificationBundler` (grouping logic), `SchedulingService` (due-notification retrieval).
- [ ] **T-052** Integration tests: all 18 REST endpoints via supertest against a live Express app with in-memory MongoDB (mongodb-memory-server) and Redis (ioredis-mock).
- [ ] **T-053** Integration tests: `KafkaEventConsumer` and `KafkaEventProducer` with testcontainers (Kafka).
- [ ] **T-054** Integration tests: MongoDB repository CRUD with mongodb-memory-server.
- [ ] **T-055** Contract tests (Pact): `UserServiceClient`, `MedicationServiceClient`, `LocalizationServiceClient`.

## Phase 11 — Observability & Deployment

- [ ] **T-056** Configure structured JSON logging (pino) with `requestId`, `userId`, `service: "notification-service"`, `level`, `timestamp`. Redact PII fields (email, phone).
- [ ] **T-057** Verify all Prometheus metrics are registered and scraped. Create sample Grafana dashboard JSON.
- [ ] **T-058** Create multi-stage Dockerfile: build stage (TypeScript compile), runtime stage (node:18-alpine, non-root user, HEALTHCHECK instruction).
- [ ] **T-059** Create Kubernetes manifests: `Deployment` (4 replicas), `Service` (port 3000), `HPA` (CPU 70% + Kafka lag), `ConfigMap`, `Secret` (references to AWS Secrets Manager), `PodDisruptionBudget` (minAvailable: 2).
- [ ] **T-060** Create Helm chart or Kustomize overlays for dev (1 replica, relaxed limits) vs production (4 replicas, full config).

## Phase 12 — Documentation & Open Items

- [ ] **T-061** Generate OpenAPI 3.0.3 spec from Zod schemas + route decorators. Validate against the reference spec from architecture.
- [ ] **T-062** Write `README.md`: local dev setup (Docker Compose for MongoDB/Redis/Kafka), env vars, test commands, architecture overview.
- [ ] **T-063** Document all Kafka event contracts with TypeScript interface definitions and example payloads.
- [ ] **T-064** **TODO**: Confirm SMS provider selection (Twilio vs AWS SNS) and obtain API credentials — blocked on vendor decision.
- [ ] **T-065** **TODO**: Confirm email provider selection (Amazon SES vs SendGrid) and obtain API credentials — blocked on vendor decision.
- [ ] **T-066** **TODO**: Finalize exact rate-limiting thresholds per channel and priority level with product team (current default: 50/day/channel).
- [ ] **T-067** **TODO**: Clarify notification bundling rules and time windows with product team (current default: 5-minute window, group by userId+type).
- [ ] **T-068** **TODO**: Confirm 90-day notification retention policy and define archival strategy with data governance team.