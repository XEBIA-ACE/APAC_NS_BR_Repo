# Plan: Notification Service (SRV-004)

> Technical implementation plan — HOW to build it.

## Architecture Overview

The service follows a **five-layer architecture**: Presentation → Business → Data Access → Integration → Infrastructure, all running inside a single Node.js/Express process deployed as 4 replicas on EKS.

```
┌─────────────────────────────────────────────────────────────────┐
│  PRESENTATION                                                   │
│  NotificationController · PreferenceController · TemplateController │
│  HealthCheckController · WebSocketHandler                       │
├─────────────────────────────────────────────────────────────────┤
│  BUSINESS                                                       │
│  NotificationService · TemplateService · PreferenceService      │
│  SchedulingService · NotificationBundler · RateLimiter          │
│  DeliveryStatusTracker                                          │
├─────────────────────────────────────────────────────────────────┤
│  DATA ACCESS                                                    │
│  NotificationRepository · TemplateRepository                    │
│  PreferenceRepository · DeliveryLogRepository                   │
├─────────────────────────────────────────────────────────────────┤
│  INTEGRATION                                                    │
│  PushNotificationAdapter · EmailAdapter · SMSAdapter            │
│  KafkaEventConsumer · KafkaEventProducer                        │
│  UserServiceClient · MedicationServiceClient · LocalizationServiceClient │
├─────────────────────────────────────────────────────────────────┤
│  INFRASTRUCTURE                                                 │
│  SecurityFilter · RequestValidator · ErrorHandler               │
│  MetricsCollector · ConfigurationManager · CacheManager         │
└─────────────────────────────────────────────────────────────────┘
       │               │                │
   MongoDB          Redis            Kafka
  (DocumentDB)   (ElastiCache)       (MSK)
```

## Key Design Decisions

| Decision | Choice | Rationale |
|---|---|---|
| Language / Runtime | TypeScript on Node.js 18 | Aligns with HLD spec; excellent for I/O-bound notification processing and native WebSocket support. |
| Primary Database | MongoDB via Mongoose | Flexible document schema for heterogeneous notification metadata; aligns with system polyglot persistence. |
| Cache & Rate Limiter | Redis | Sub-millisecond preference lookups, atomic rate-limit counters, sorted-set scheduling. |
| Event Streaming | Apache Kafka (kafkajs) | At-least-once delivery with idempotency keys; dead-letter queue for poison messages. |
| Channel Delivery | Strategy Pattern | `DeliveryStrategy` interface → `PushNotificationStrategy`, `SmsStrategy`, `EmailStrategy`. New channels are added by implementing the interface. |
| External Service Calls | Circuit Breaker (opossum) | 5 s timeout, 50% failure threshold, 30 s half-open. Prevents cascading failures to User/Medication/Localization services. |
| Notification Scheduling | Redis Sorted Sets | Score = Unix timestamp of `scheduledAt`. Polling job every 60 s retrieves due notifications. |
| Template Rendering | Handlebars-style `{{placeholder}}` | Simple, battle-tested; supports conditional blocks for complex notifications. |

## Data Models (MongoDB Collections)

### `notifications`
```
{
  _id: UUID,
  userId: UUID,
  type: NotificationType,  // MEDICATION_REMINDER | REFILL_REMINDER | ...
  title: string,
  body: string,
  priority: Priority,      // low | normal | high | urgent
  status: "unread" | "read",
  channels: Channel[],     // PUSH | SMS | EMAIL | IN_APP
  scheduledAt?: Date,
  metadata: Record<string, any>,
  actionUrl?: string,
  localization: { [locale]: { title, body } },
  deliveryStatus: { push, sms, email: DeliveryState },
  expiresAt?: Date,
  createdAt: Date,
  updatedAt: Date
}
// Indexes: { userId: 1, createdAt: -1 }, { scheduledAt: 1, status: 1 }, { type: 1 }
// TTL index on createdAt: 90 days
```

### `notification_preferences`
```
{
  _id: UUID,
  userId: UUID,  // unique
  enabled: boolean,
  timezone: string,
  language: "en" | "de" | "fr" | "it",
  quietHours: { enabled, startTime, endTime },
  notificationTypes: {
    medication_reminder: { enabled, channels[], reminderTimes[] },
    refill_reminder: { enabled, channels[], daysBeforeEmpty },
    health_event: { enabled, channels[], severity },
    system: { enabled, channels[] }
  }
}
// Index: { userId: 1 } unique
```

### `notification_templates`
```
{
  _id: string,
  type: NotificationType,
  name: string,
  language: Locale,
  titleTemplate: string,    // "Time to take {{medication_name}}"
  bodyTemplate: string,
  priority: Priority,
  placeholders: string[],
  version: number,
  createdAt: Date,
  updatedAt: Date
}
// Index: { type: 1, language: 1 }
```

### `delivery_logs`
```
{
  _id: UUID,
  notificationId: UUID,
  channel: Channel,
  attempt: number,
  status: "pending" | "sent" | "delivered" | "failed",
  errorMessage?: string,
  timestamp: Date
}
// Index: { notificationId: 1 }, TTL: 180 days
```

### `devices`
```
{
  _id: string,       // deviceId
  userId: UUID,
  deviceType: "ios" | "android" | "web",
  pushToken: string,
  deviceName?: string,
  appVersion?: string,
  osVersion?: string,
  lastActive: Date,
  createdAt: Date
}
// Index: { userId: 1 }, { pushToken: 1 } unique
```

## API Surface

| Method | Path | Auth | Description |
|---|---|---|---|
| `GET` | `/api/v1/notifications` | `notification:read` | List user notifications (paginated, filterable) |
| `GET` | `/api/v1/notifications/{id}` | `notification:read` | Get notification detail |
| `PUT` | `/api/v1/notifications/{id}/status` | `notification:write` | Mark read/unread |
| `DELETE` | `/api/v1/notifications/{id}` | `notification:write` | Delete notification |
| `POST` | `/api/v1/notifications/test` | `notification:write` | Send test notification |
| `GET` | `/api/v1/notifications/preferences` | `notification:read` | Get user preferences |
| `PUT` | `/api/v1/notifications/preferences` | `notification:write` | Update user preferences |
| `GET` | `/api/v1/notifications/channels` | `notification:read` | List channels |
| `PUT` | `/api/v1/notifications/channels/{id}` | `notification:write` | Update channel |
| `GET` | `/api/v1/notifications/devices` | `notification:read` | List devices |
| `POST` | `/api/v1/notifications/devices` | `notification:write` | Register device |
| `DELETE` | `/api/v1/notifications/devices/{id}` | `notification:write` | Unregister device |
| `GET` | `/api/v1/notifications/templates` | `notification:read` | List templates |
| `GET` | `/api/v1/notifications/history` | `notification:read` | Notification history |
| `POST` | `/api/v1/notifications/batch` | `notification:admin` | Send batch |
| `GET` | `/api/v1/notifications/batch/{id}/status` | `notification:admin` | Batch status |
| `GET` | `/health`, `/health/liveness`, `/health/readiness` | None | Health probes |
| `GET` | `/metrics` | `notification:admin` | Prometheus metrics |
| `WS` | `/ws/notifications` | JWT | Real-time push |

## Kafka Topics

| Direction | Topic | Events |
|---|---|---|
| **Consume** | `medication-events` | `MedicationReminderEvent`, `RefillReminderEvent`, `HealthEventNotification`, `DrugInteractionAlert` |
| **Consume** | `prescription-events` | `PrescriptionExpiryEvent`, `InsuranceChangeEvent` |
| **Consume** | `user-events` | `UserProfileUpdatedEvent` (for contact info refresh) |
| **Produce** | `notification-events` | `NotificationSentEvent`, `NotificationDeliveredEvent`, `NotificationFailedEvent`, `PreferenceUpdatedEvent` |
| **DLQ** | `notification-events-dlq` | Failed events after 3 consumer retries |

## Core Flow: Event → Delivery

```
Kafka Event → KafkaEventConsumer
  → idempotency check (Redis: eventId)
  → NotificationService.processEvent()
    → UserServiceClient.getUserById(userId)        [circuit breaker, Redis cache 5m]
    → PreferenceService.getPreferences(userId)      [Redis cache 15m]
    → RateLimiter.check(userId, channels)           [Redis sliding window]
    → NotificationBundler.evaluate(notification)    [5-min grouping window]
    → TemplateService.render(type, locale, data)    [Redis cache 1h]
      → LocalizationServiceClient (fallback: en)
      → MedicationServiceClient (fallback: generic)
    → For each channel:
        → DeliveryStrategy.deliver(notification, preference)
          → PushNotificationAdapter | EmailAdapter | SMSAdapter
    → DeliveryStatusTracker.record(results)         [MongoDB delivery_logs]
    → WebSocketHandler.push(userId, notification)   [in-memory registry]
    → KafkaEventProducer.publish(NotificationSentEvent)
    → NotificationRepository.save(notification)     [MongoDB]
```

## Resilience

| Component | Pattern | Config |
|---|---|---|
| UserServiceClient | Circuit Breaker | timeout: 5s, threshold: 50%, reset: 30s |
| MedicationServiceClient | Circuit Breaker | timeout: 5s, threshold: 50%, reset: 30s |
| LocalizationServiceClient | Circuit Breaker + Fallback | Fallback → English locale |
| PushNotificationAdapter | Retry w/ backoff | max 3, base 1s |
| EmailAdapter | Retry w/ backoff | max 3, base 1s |
| SMSAdapter | Retry w/ backoff | max 2, base 1s |
| KafkaEventConsumer | DLQ after retries | max 3 consumer retries → DLQ |

## Caching Strategy

| Data | TTL | Invalidation |
|---|---|---|
| User preferences | 15 min | Explicit on `PUT /preferences` |
| Templates | 1 hr | On template CRUD |
| Translations | 1 hr | TTL-based |
| User contact info | 5 min | TTL-based |

## Project Structure

```
src/
├── presentation/
│   ├── controllers/        # REST controllers (thin)
│   ├── websocket/          # WebSocketHandler
│   └── mappers/            # DTO ↔ Entity mappers
├── business/
│   ├── services/           # NotificationService, TemplateService, PreferenceService, SchedulingService
│   ├── bundler/            # NotificationBundler
│   ├── delivery/           # DeliveryStatusTracker, RateLimiter
│   └── models/             # Domain entities, enums, value objects
├── data/
│   ├── repositories/       # Mongoose repositories
│   └── schemas/            # Mongoose schemas
├── integration/
│   ├── adapters/           # PushNotificationAdapter, EmailAdapter, SMSAdapter
│   ├── kafka/              # KafkaEventConsumer, KafkaEventProducer
│   └── clients/            # UserServiceClient, MedicationServiceClient, LocalizationServiceClient
├── infrastructure/
│   ├── security/           # SecurityFilter (JWT)
│   ├── validation/         # Zod schemas + RequestValidator
│   ├── errors/             # ErrorHandler
│   ├── metrics/            # Prometheus MetricsCollector
│   ├── config/             # ConfigurationManager
│   └── cache/              # CacheManager (Redis)
├── config/                 # database, kafka, redis, app configs
└── index.ts                # Express app bootstrap
```

---