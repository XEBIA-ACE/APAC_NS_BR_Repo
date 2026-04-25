# Tasks: Notification Service (SRV-004)

## Phase 1: Foundation and Domain Models

- [ ] **T-001**: Set up Node.js/Express project scaffold with TypeScript, ESLint, Prettier, and Jest configuration
- [ ] **T-002**: Define domain model `NotificationEntity` in `src/business/models/NotificationEntity.ts` with all fields, validation invariants, and `markAsSent()` / `markAsFailed()` state transitions
- [ ] **T-003**: Define domain model `NotificationPreference` in `src/business/models/NotificationPreference.ts` with `isActiveAt()` quiet hours validation and `supportsChannel()` logic
- [ ] **T-004**: Define domain model `NotificationTemplate` in `src/business/models/NotificationTemplate.ts` with placeholder extraction and validation
- [ ] **T-005**: Define domain model `Device` in `src/business/models/Device.ts`
- [ ] **T-006**: Define enumerations `NotificationType`, `Channel`, `Priority`, `NotificationStatus`, `DeliveryStatus` in `src/business/models/enums.ts`
- [ ] **T-007**: Define value object `DeliveryResult` in `src/business/models/DeliveryResult.ts`
- [ ] **T-008**: Define request/response DTOs: `CreateNotificationRequest`, `NotificationResponse`, `NotificationPreferencesUpdate`, `BatchNotification`, `TestNotification`, etc.

## Phase 2: Data Access Layer

- [ ] **T-009**: Configure MongoDB/Mongoose connection in `src/config/database.config.ts` with connection pooling and retry
- [ ] **T-010**: Define MongoDB schemas in `src/data/schemas/` for notifications, templates, preferences, and delivery logs
- [ ] **T-011**: Implement `NotificationRepository` with `save()`, `findById()`, `findByUserId()`, `findPendingNotifications()`, `updateStatus()` operations and pagination support
- [ ] **T-012**: Implement `TemplateRepository` with CRUD operations, version history support, and locale-based queries
- [ ] **T-013**: Implement `PreferenceRepository` with `findByUserId()`, `save()`, `deleteByUserId()` operations
- [ ] **T-014**: Implement `DeliveryLogRepository` with append-only delivery attempt logging and query by notificationId

## Phase 3: Infrastructure Layer

- [ ] **T-015**: Configure Redis connection in `src/config/redis.config.ts` with cluster mode support
- [ ] **T-016**: Implement `CacheManager` with `get()`, `set()`, `invalidate()` operations and TTL configuration per data type (preferences: 15min, templates: 1hr, translations: 1hr)
- [ ] **T-017**: Implement `RateLimiter` using Redis sliding window algorithm with per-user, per-channel limits
- [ ] **T-018**: Implement `SecurityFilter` middleware for JWT validation (delegated from API Gateway)
- [ ] **T-019**: Implement `RequestValidator` middleware using Joi or Zod schemas for all API request bodies
- [ ] **T-020**: Implement `ErrorHandler` middleware with standard error response format, request ID correlation, and sanitized error messages
- [ ] **T-021**: Implement `MetricsCollector` exposing Prometheus-compatible metrics (notification_sent_total, delivery_latency_seconds, delivery_failure_total, etc.)
- [ ] **T-022**: Implement `ConfigurationManager` with environment variable loading, feature flags, and dynamic reload support

## Phase 4: Integration Layer — External Clients

- [ ] **T-023**: Implement `UserServiceClient` with circuit breaker (opossum), 5s timeout, Redis caching for user profiles and contact info
- [ ] **T-024**: Implement `MedicationServiceClient` with circuit breaker, 5s timeout, and fallback to generic notification content
- [ ] **T-025**: Implement `LocalizationServiceClient` with circuit breaker, Redis-cached translations (1hr TTL), fallback to English locale
- [ ] **T-026**: Implement `PushNotificationAdapter` integrating with Firebase Cloud Messaging: send notification, handle invalid tokens, retry with exponential backoff (max 3 retries)
- [ ] **T-027**: Implement `EmailAdapter` integrating with Email Service Provider (SES/SendGrid): send HTML email, handle bounces, retry with backoff (max 3 retries)
- [ ] **T-028**: Implement `SMSAdapter` integrating with SMS Gateway (Twilio/SNS): send SMS, handle carrier errors, retry with backoff (max 2 retries), validate phone numbers

## Phase 5: Integration Layer — Kafka

- [ ] **T-029**: Configure Kafka connection in `src/config/kafka.config.ts` with consumer group, producer config, and topic definitions
- [ ] **T-030**: Implement `KafkaEventConsumer` subscribing to `medication-events`, `user-events`, `prescription-events` topics with idempotency checks and dead-letter queue routing
- [ ] **T-031**: Implement `KafkaEventProducer` publishing `NotificationSentEvent`, `NotificationDeliveredEvent`, `NotificationFailedEvent`, `PreferenceUpdatedEvent` to `notification-events` topic
- [ ] **T-032**: Define Kafka event schemas/contracts for all consumed and produced events

## Phase 6: Business Layer — Core Services

- [ ] **T-033**: Implement `TemplateService` with template retrieval, `{{placeholder}}` rendering, locale resolution, version management, and Redis caching
- [ ] **T-034**: Implement `PreferenceService` with preference CRUD, default preference generation, type-specific override logic, and cache invalidation
- [ ] **T-035**: Implement `NotificationBundler` with grouping rules based on notification type, user, and time window (configurable window: 5 minutes)
- [ ] **T-036**: Implement `DeliveryStatusTracker` with per-channel status recording, retry decision logic, and delivery log persistence
- [ ] **T-037**: Implement `SchedulingService` using Redis sorted sets for scheduled notifications, periodic polling job (60s interval), timezone-aware delivery time resolution
- [ ] **T-038**: Implement `NotificationService` — the core orchestrator:
  - Receive notification request (API or Kafka event)
  - Resolve user preferences (PreferenceService + CacheManager)
  - Check rate limits (RateLimiter)
  - Evaluate bundling (NotificationBundler)
  - Render template (TemplateService + LocalizationServiceClient + MedicationServiceClient)
  - Deliver to each enabled channel (PushNotificationAdapter, EmailAdapter, SMSAdapter)
  - Record delivery status (DeliveryStatusTracker)
  - Push real-time (WebSocketHandler)
  - Publish lifecycle events (KafkaEventProducer)
  - Handle scheduling if `scheduledAt` is in the future (SchedulingService)

## Phase 7: Presentation Layer — REST Controllers

- [ ] **T-039**: Implement `NotificationController` with endpoints: `GET /api/v1/notifications`, `GET /{id}`, `PUT /{id}/status`, `DELETE /{id}`, `POST /test`, pagination, filtering, and sorting
- [ ] **T-040**: Implement `PreferenceController` with endpoints: `GET /api/v1/notifications/preferences`, `PUT /preferences`, `GET /channels`, `PUT /channels/{channelId}`
- [ ] **T-041**: Implement `TemplateController` with endpoints: `GET /api/v1/notifications/templates` with type/language filtering
- [ ] **T-042**: Implement device management endpoints: `GET /api/v1/notifications/devices`, `POST /devices`, `DELETE /devices/{deviceId}`
- [ ] **T-043**: Implement notification history endpoint: `GET /api/v1/notifications/history` with date range filtering and pagination
- [ ] **T-044**: Implement batch notification endpoints: `POST /api/v1/notifications/batch`, `GET /batch/{batchId}/status` (admin-only)
- [ ] **T-045**: Implement `HealthCheckController` with `/health`, `/health/liveness`, `/health/readiness`, `/metrics` endpoints including dependency health checks
- [ ] **T-046**: Implement `NotificationMapper` for entity ↔ DTO conversion

## Phase 8: WebSocket

- [ ] **T-047**: Implement `WebSocketHandler` with JWT-authenticated connection, in-memory session registry, heartbeat/ping-pong, and notification push to connected sessions
- [ ] **T-048**: Integrate WebSocketHandler with NotificationService so new notifications are pushed to active sessions

## Phase 9: Testing

- [ ] **T-049**: Write unit tests for all domain models (NotificationEntity state transitions, NotificationPreference quiet hours logic, template rendering)
- [ ] **T-050**: Write unit tests for NotificationService orchestration logic with mocked dependencies
- [ ] **T-051**: Write unit tests for RateLimiter, NotificationBundler, and SchedulingService
- [ ] **T-052**: Write integration tests for all REST API endpoints using supertest
- [ ] **T-053**: Write integration tests for KafkaEventConsumer and KafkaEventProducer with embedded Kafka
- [ ] **T-054**: Write integration tests for MongoDB repositories with in-memory MongoDB
- [ ] **T-055**: Write contract tests for UserServiceClient, MedicationServiceClient, and LocalizationServiceClient

## Phase 10: Observability and Deployment

- [ ] **T-056**: Configure structured JSON logging with correlation IDs (requestId) across all components
- [ ] **T-057**: Add Prometheus metrics for: `notifications_sent_total{channel, type}`, `notification_delivery_latency_seconds{channel}`, `notification_delivery_failures_total{channel, reason}`, `kafka_consumer_lag`, `websocket_active_connections`
- [ ] **T-058**: Create Dockerfile with multi-stage build, health check instruction, and non-root user
- [ ] **T-059**: Create Kubernetes manifests: Deployment (4 replicas), Service, HPA (CPU + Kafka lag), ConfigMap, Secrets, PodDisruptionBudget
- [ ] **T-060**: Create Helm chart or Kustomize overlay for environment-specific configuration (dev/prod)

## Phase 11: Documentation and Cleanup

- [ ] **T-061**: Generate OpenAPI 3.0.3 specification from code annotations and validate against the reference spec
- [ ] **T-062**: Write README.md with setup instructions, environment variables, and local development guide
- [ ] **T-063**: Document Kafka event contracts (consumed and produced) with schema examples
- [ ] **T-064**: **TODO**: Clarify exact notification bundling rules and time windows with product team
- [ ] **T-065**: **TODO**: Confirm SMS provider selection (Twilio vs AWS SNS) and obtain API credentials
- [ ] **T-066**: **TODO**: Confirm email provider selection (Amazon SES vs SendGrid) and obtain API credentials
- [ ] **T-067**: **TODO**: Define exact rate limiting thresholds per channel and priority level with product team
- [ ] **T-068**: **TODO**: Clarify notification retention policy (90-day assumption) and archival strategy with data governance team