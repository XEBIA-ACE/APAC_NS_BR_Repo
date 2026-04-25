# Design: Notification Service (SRV-004)

## Technical Approach

The Notification Service follows a **layered architecture** with five distinct layers: Presentation, Business, Data Access, Integration, and Infrastructure. The service is built on **Node.js 18 with Express** and employs **event-driven architecture** via Apache Kafka for loose coupling with upstream domain services.

### Architecture Decisions

| Decision | Rationale |
|---|---|
| **Node.js/Express** | Aligns with HLD container specification; well-suited for I/O-heavy notification processing and WebSocket support |
| **MongoDB** (DocumentDB in prod) | Flexible document schema accommodates varying notification metadata and template structures; aligns with system's polyglot persistence strategy |
| **Redis** for caching/scheduling/rate-limiting | In-memory performance for sub-millisecond preference lookups, TTL-based scheduling, and atomic rate limit counters |
| **Apache Kafka** for event consumption/production | Decouples notification triggers from source services; supports at-least-once delivery with dead-letter queues |
| **Strategy Pattern** for delivery channels | `DeliveryStrategy` interface with `PushNotificationStrategy`, `SmsStrategy`, `EmailStrategy` implementations enables adding new channels without modifying core logic |
| **Circuit Breaker** for external dependencies | Prevents cascading failures when User Management Service, Medication Service, or delivery providers are degraded |
| **Template rendering with placeholders** | `{{placeholder}}` syntax with locale-specific templates enables personalization without hardcoding content |
| **Redis Sorted Sets** for scheduling | Score-based retrieval of due notifications enables efficient time-based scheduling without polling the database |
| **WebSocket** for real-time delivery | Enables instant in-app notifications for active users, complementing push/SMS/email channels |

### Data Flow

```
[Kafka Topics] → KafkaEventConsumer → NotificationService
                                         ├─→ UserServiceClient (REST) → User Management Service
                                         ├─→ PreferenceService → PreferenceRepository (MongoDB) / CacheManager (Redis)
                                         ├─→ TemplateService → TemplateRepository (MongoDB) / LocalizationServiceClient (REST)
                                         ├─→ MedicationServiceClient (REST) → Medication Management Service
                                         ├─→ RateLimiter (Redis)
                                         ├─→ NotificationBundler
                                         ├─→ EmailAdapter → Email Service Provider
                                         ├─→ SMSAdapter → SMS Gateway
                                         ├─→ PushNotificationAdapter → Firebase Cloud Messaging
                                         ├─→ WebSocketHandler → Connected Clients
                                         ├─→ DeliveryStatusTracker → DeliveryLogRepository (MongoDB)
                                         └─→ KafkaEventProducer → [notification-events topic]

[REST API] → Controllers → NotificationService → (same flow as above)
```

### API Design

The service exposes a RESTful API under the `/api/v1/notifications` base path with the following resource groups:

| Group | Base Path | Auth Scope |
|---|---|---|
| Notifications | `/api/v1/notifications` | `notification:read`, `notification:write` |
| Preferences | `/api/v1/notifications/preferences` | `notification:read`, `notification:write` |
| Channels | `/api/v1/notifications/channels` | `notification:read`, `notification:write` |
| Devices | `/api/v1/notifications/devices` | `notification:read`, `notification:write` |
| Templates | `/api/v1/notifications/templates` | `notification:read` |
| History | `/api/v1/notifications/history` | `notification:read` |
| Batch | `/api/v1/notifications/batch` | `notification:admin` |
| Health | `/api/v1/notifications/health`, `/metrics` | None / `notification:admin` |
| WebSocket | `/ws/notifications` | JWT via query param or header |

All endpoints follow OpenAPI 3.0.3 specification with standard HTTP methods and status codes.

### File/Component Structure (Proposed)

```
src/
├── presentation/
│   ├── controllers/
│   │   ├── NotificationController.ts
│   │   ├── PreferenceController.ts
│   │   ├── TemplateController.ts
│   │   └── HealthCheckController.ts
│   ├── websocket/
│   │   └── WebSocketHandler.ts
│   └── mappers/
│       └── NotificationMapper.ts
├── business/
│   ├── services/
│   │   ├── NotificationService.ts
│   │   ├── TemplateService.ts
│   │   ├── PreferenceService.ts
│   │   └── SchedulingService.ts
│   ├── bundler/
│   │   └── NotificationBundler.ts
│   ├── delivery/
│   │   ├── DeliveryStatusTracker.ts
│   │   └── RateLimiter.ts
│   └── models/
│       ├── NotificationEntity.ts
│       ├── NotificationPreference.ts
│       ├── NotificationTemplate.ts
│       ├── Device.ts
│       ├── DeliveryResult.ts
│       └── enums.ts  (NotificationType, Channel, Priority, etc.)
├── data/
│   ├── repositories/
│   │   ├── NotificationRepository.ts
│   │   ├── TemplateRepository.ts
│   │   ├── PreferenceRepository.ts
│   │   └── DeliveryLogRepository.ts
│   └── schemas/
│       ├── notification.schema.ts
│       ├── template.schema.ts
│       ├── preference.schema.ts
│       └── deliveryLog.schema.ts
├── integration/
│   ├── adapters/
│   │   ├── EmailAdapter.ts
│   │   ├── SMSAdapter.ts
│   │   └── PushNotificationAdapter.ts
│   ├── kafka/
│   │   ├── KafkaEventConsumer.ts
│   │   └── KafkaEventProducer.ts
│   └── clients/
│       ├── UserServiceClient.ts
│       ├── MedicationServiceClient.ts
│       └── LocalizationServiceClient.ts
├── infrastructure/
│   ├── security/
│   │   └── SecurityFilter.ts
│   ├── validation/
│   │   └── RequestValidator.ts
│   ├── errors/
│   │   └── ErrorHandler.ts
│   ├── metrics/
│   │   └── MetricsCollector.ts
│   ├── config/
│   │   └── ConfigurationManager.ts
│   └── cache/
│       └── CacheManager.ts
├── config/
│   ├── database.config.ts
│   ├── kafka.config.ts
│   ├── redis.config.ts
│   └── app.config.ts
└── index.ts
```

### Resiliency Patterns

| Pattern | Implementation |
|---|---|
| **Circuit Breaker** | opossum library wrapping UserServiceClient, MedicationServiceClient, LocalizationServiceClient, and all delivery adapters. 5s timeout, 50% failure threshold, 30s reset. |
| **Retry with Backoff** | Exponential backoff for delivery adapters: base 1s, max 3 retries for push/email, max 2 for SMS. |
| **Dead-Letter Queue** | Kafka DLQ for failed event processing after 3 consumer retries. |
| **Rate Limiting** | Redis-based sliding window rate limiter per userId with configurable limits per channel. |
| **Bulkhead** | Separate connection pools for MongoDB, Redis, and each external HTTP client. |
| **Timeout** | 5s for internal service calls, 10s for external provider calls. |
| **Graceful Degradation** | If Localization Service unavailable → fallback to English. If Medication Service unavailable → use generic template. |

### Caching Strategy

| Data | TTL | Storage | Invalidation |
|---|---|---|---|
| User preferences | 15 min | Redis | On preference update (explicit invalidation + Kafka event) |
| Notification templates | 1 hour | Redis | On template CRUD operation |
| Translations | 1 hour | Redis | TTL-based expiry |
| User contact info | 5 min | Redis | TTL-based expiry |

### Deployment Configuration

- **Replicas**: 4 (production), 1 (development)
- **Resources**: 0.5 CPU, 1GB RAM per replica
- **Port**: 3000
- **Health Probes**: Liveness at `/health/liveness`, Readiness at `/health/readiness`
- **Auto-scaling**: HPA based on CPU (70% threshold) and custom Kafka consumer lag metric

---