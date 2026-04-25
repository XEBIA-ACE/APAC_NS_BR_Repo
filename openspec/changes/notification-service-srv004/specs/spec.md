# Specification: Notification Service (SRV-004)

## Purpose

The Notification Service is the centralized notification orchestration engine for the Swiss Medication Management System. It SHALL receive notification triggers (via REST API and Kafka events), resolve user preferences, render localized templates, deliver notifications across multiple channels, track delivery status, and maintain notification history. The service SHALL operate as a Node.js/Express application backed by MongoDB, Redis, and Apache Kafka.

---

## Technologies and Runtime Stack

| Component | Technology |
|---|---|
| Runtime | Node.js 18, Express |
| Primary Database | MongoDB (via DocumentDB in production) |
| Cache / Rate Limiting / Scheduling | Redis (ElastiCache in production) |
| Event Streaming | Apache Kafka (MSK in production) |
| Push Notifications | Firebase Cloud Messaging (FCM) |
| Email Delivery | Email Service Provider (Amazon SES / SendGrid) |
| SMS Delivery | SMS Gateway (Twilio / AWS SNS) |
| Real-time | WebSocket (ws/socket.io) |
| Container | Docker, deployed on EKS (4 replicas, 0.5 CPU, 1GB RAM) |
| Authentication | OAuth 2.0 / JWT (delegated to API Gateway) |

---

## Components

### Presentation Layer

| Component | Responsibility |
|---|---|
| **NotificationController** | REST API controller for sending, retrieving, and managing notifications. Validates requests, delegates to NotificationService, formats responses. |
| **PreferenceController** | REST API controller for managing user notification preferences (channel, frequency, quiet hours, opt-out). |
| **TemplateController** | REST API controller for CRUD operations on notification templates with versioning and localization. |
| **HealthCheckController** | Exposes `/health`, `/health/liveness`, `/health/readiness`, `/metrics` endpoints. |
| **WebSocketHandler** | Manages real-time WebSocket connections, authentication, and pushing notifications to connected clients. |

### Business Layer

| Component | Responsibility |
|---|---|
| **NotificationService** | Core orchestrator: personalizes notifications, selects channels based on preferences, coordinates multi-channel delivery, publishes lifecycle events. |
| **TemplateService** | Retrieves, renders, and validates notification templates with dynamic data and localization support. |
| **PreferenceService** | Manages user notification preferences with defaults, overrides per notification type, and preference inheritance. |
| **SchedulingService** | Handles time-based notification scheduling, recurring reminders, delayed delivery, and timezone-aware timing via Redis. |
| **NotificationBundler** | Groups related notifications using rules based on notification type, timing, and user behavior patterns. |
| **DeliveryStatusTracker** | Monitors and records delivery status across all channels; supports retry logic, failure handling, and effectiveness reporting. |
| **RateLimiter** | Enforces per-user notification frequency limits based on preferences, priority, and channel constraints using Redis. |

### Data Access Layer

| Component | Responsibility |
|---|---|
| **NotificationRepository** | CRUD operations for notification documents in MongoDB. |
| **TemplateRepository** | CRUD operations for notification templates including version history and locale variants. |
| **PreferenceRepository** | CRUD operations for user notification preferences. |
| **DeliveryLogRepository** | Stores delivery attempts, status updates, and confirmations for auditing and analytics. |

### Integration Layer

| Component | Responsibility |
|---|---|
| **EmailAdapter** | Sends email notifications via ESP. Handles formatting, attachments, delivery tracking. |
| **SMSAdapter** | Sends SMS notifications via gateway. Handles carrier requirements and delivery confirmations. |
| **PushNotificationAdapter** | Sends push notifications via FCM. Handles payload formatting and device token management. |
| **KafkaEventConsumer** | Listens to `medication-events`, `user-events`, `prescription-events` Kafka topics and triggers NotificationService. |
| **KafkaEventProducer** | Publishes `NotificationSentEvent`, `NotificationDeliveredEvent`, `NotificationFailedEvent` to Kafka topics. |
| **UserServiceClient** | REST client to User Management Service for user profiles, contact info, locale. |
| **MedicationServiceClient** | REST client to Medication Management Service for medication details and schedules. |
| **LocalizationServiceClient** | REST client to Localization Service for translations and locale-specific formatting. |

### Infrastructure Layer

| Component | Responsibility |
|---|---|
| **SecurityFilter** | Enforces authentication and authorization for all API requests (JWT validation). |
| **RequestValidator** | Validates incoming request data against schemas. |
| **ErrorHandler** | Centralized exception handling with consistent error response format. |
| **MetricsCollector** | Gathers operational metrics (delivery rates, latency, error rates) for Prometheus. |
| **ConfigurationManager** | Manages configuration, feature flags, and environment-specific settings with dynamic reloading. |
| **CacheManager** | Provides Redis-based caching for templates, preferences, and notification history. |

---

## APIs

### Notification Management API

#### Requirement: List Notifications

##### Scenario: Authenticated user retrieves their notifications

- **Given** an authenticated user with a valid JWT token
- **When** a `GET /api/v1/notifications` request is made with optional filters (status, type, page, limit, sort)
- **Then** the service SHALL return a paginated `NotificationList` with `unreadCount`
- **And** results SHALL be filtered by the authenticated user's ID
- **And** the response SHALL include status 200 with `application/json` content type

#### Requirement: Get Notification Details

##### Scenario: User retrieves a specific notification

- **Given** a notification exists with the given `notificationId` owned by the authenticated user
- **When** a `GET /api/v1/notifications/{notificationId}` request is made
- **Then** the service SHALL return the full `Notification` object with delivery status per channel
- **And** if the notification does not exist or is not owned by the user, the service SHALL return 404

#### Requirement: Update Notification Status

##### Scenario: User marks a notification as read

- **Given** a notification exists and is owned by the authenticated user
- **When** a `PUT /api/v1/notifications/{notificationId}/status` request is made with `{"status": "read"}`
- **Then** the service SHALL update the notification status and return the updated `Notification`
- **And** if the request body is invalid, the service SHALL return 400 with a `ValidationError`

#### Requirement: Delete Notification

##### Scenario: User deletes a notification

- **Given** a notification exists and is owned by the authenticated user
- **When** a `DELETE /api/v1/notifications/{notificationId}` request is made
- **Then** the service SHALL soft-delete (or remove) the notification and return 204

#### Requirement: Send Test Notification

##### Scenario: User verifies channel configuration

- **Given** an authenticated user requests `POST /api/v1/notifications/test` with a target `channel`
- **When** the request is processed
- **Then** the service SHALL send a test notification through the specified channel and return 202 with `NotificationDeliveryStatus`

### Notification Preferences API

#### Requirement: Get Preferences

##### Scenario: User retrieves their notification preferences

- **Given** an authenticated user
- **When** a `GET /api/v1/notifications/preferences` request is made
- **Then** the service SHALL return the user's `NotificationPreferences` including enabled state, quiet hours, timezone, language, and per-type channel settings
- **And** if no preferences exist, the service SHALL return default preferences

#### Requirement: Update Preferences

##### Scenario: User updates notification preferences

- **Given** an authenticated user submits `PUT /api/v1/notifications/preferences` with a `NotificationPreferencesUpdate` body
- **When** the request is validated
- **Then** the service SHALL persist the updated preferences and return the full `NotificationPreferences`
- **And** cached preferences in Redis SHALL be invalidated
- **And** a `PreferenceUpdatedEvent` SHALL be published to Kafka

### Notification Channels API

#### Requirement: List Channels

##### Scenario: User views available channels

- **Given** an authenticated user
- **When** `GET /api/v1/notifications/channels` is called
- **Then** the service SHALL return a `NotificationChannelList` with channel status (enabled, verificationStatus)

#### Requirement: Update Channel

##### Scenario: User enables/disables a channel

- **Given** an authenticated user
- **When** `PUT /api/v1/notifications/channels/{channelId}` is called with `{"enabled": true/false}`
- **Then** the service SHALL update the channel configuration and return the updated `NotificationChannel`

### Device Management API

#### Requirement: Register Device

##### Scenario: User registers a device for push notifications

- **Given** an authenticated user submits `POST /api/v1/notifications/devices` with `deviceType`, `pushToken`, and optional metadata
- **When** the device is not already registered
- **Then** the service SHALL register the device and return 201 with the `Device` object
- **And** if the device is already registered, the service SHALL return 409

#### Requirement: List Devices

##### Scenario: User views registered devices

- **Given** an authenticated user
- **When** `GET /api/v1/notifications/devices` is called
- **Then** the service SHALL return a `DeviceList` for the user

#### Requirement: Unregister Device

##### Scenario: User removes a device

- **Given** a device exists for the authenticated user
- **When** `DELETE /api/v1/notifications/devices/{deviceId}` is called
- **Then** the service SHALL unregister the device and return 204

### Template API

#### Requirement: List Templates

##### Scenario: Retrieve available templates

- **Given** an authenticated user (or admin)
- **When** `GET /api/v1/notifications/templates` is called with optional `type` and `language` filters
- **Then** the service SHALL return a `NotificationTemplateList`

### Notification History API

#### Requirement: Get History

##### Scenario: User reviews past notifications

- **Given** an authenticated user
- **When** `GET /api/v1/notifications/history` is called with optional `startDate`, `endDate`, `type`, `page`, `limit`
- **Then** the service SHALL return a paginated `NotificationHistoryList` within the 90-day retention window

### Batch Notifications API (Admin)

#### Requirement: Send Batch

##### Scenario: Admin sends batch notifications

- **Given** an admin user submits `POST /api/v1/notifications/batch` with a `BatchNotification` payload
- **When** the request is validated
- **Then** the service SHALL queue the batch for async processing and return 202 with `BatchNotificationStatus` including a `batchId`

#### Requirement: Get Batch Status

##### Scenario: Admin checks batch progress

- **Given** a batch exists with the given `batchId`
- **When** `GET /api/v1/notifications/batch/{batchId}/status` is called
- **Then** the service SHALL return the current `BatchNotificationStatus` with counts (total, success, failure, pending)

### Health and Metrics API

#### Requirement: Health Check

##### Scenario: Operational health verification

- **Given** the service is running
- **When** `GET /api/v1/notifications/health` is called (no auth required)
- **Then** the service SHALL return `HealthStatus` with component-level checks (database, kafka, redis, pushProvider, smsProvider, emailProvider)
- **And** SHALL return 200 if healthy, 503 if unhealthy

#### Requirement: Service Metrics

##### Scenario: Admin retrieves operational metrics

- **Given** an admin user
- **When** `GET /api/v1/notifications/metrics` is called
- **Then** the service SHALL return `ServiceMetrics` including notification counts by type, delivery stats by channel, performance metrics, and resource usage

### WebSocket API

#### Requirement: Real-Time Notification Push

##### Scenario: Connected user receives real-time notification

- **Given** a user has established a WebSocket connection at `CONNECT /ws/notifications` with valid authentication
- **When** a notification is created for that user
- **Then** the service SHALL push the notification payload to all active WebSocket sessions for that user
- **And** connection lifecycle (connect/disconnect/reconnect) SHALL be managed with heartbeat

---

## Data Models

### NotificationEntity (Domain)

| Field | Type | Required | Description |
|---|---|---|---|
| id | UUID | Yes | Unique notification identifier |
| userId | UUID | Yes | Target user ID |
| type | NotificationType | Yes | MEDICATION_REMINDER, REFILL_REMINDER, HEALTH_EVENT, PRESCRIPTION_EXPIRY, DRUG_INTERACTION, INSURANCE_UPDATE |
| title | String | Yes | Notification title |
| body | String | Yes | Notification body content |
| content | String | No | Full rendered content |
| priority | Priority | Yes | low, normal, high, urgent |
| status | NotificationStatus | Yes | unread, read |
| channels | List\<Channel\> | Yes | PUSH_NOTIFICATION, SMS, EMAIL, IN_APP |
| scheduledAt | DateTime | No | Scheduled delivery time |
| metadata | Map\<String, Object\> | No | Additional context (medicationId, dosage, etc.) |
| actionUrl | URI | No | Deep link URL |
| localization | Map\<String, LocalizedContent\> | No | Localized title/body per locale |
| deliveryStatus | DeliveryStatusMap | No | Per-channel status (push, sms, email) |
| expiresAt | DateTime | No | Notification expiration |
| createdAt | DateTime | Yes | Creation timestamp |
| updatedAt | DateTime | Yes | Last update timestamp |

**Invariants:**
- `type` MUST be one of the defined NotificationType values
- `priority` MUST be one of: low, normal, high, urgent
- `status` MUST be one of: unread, read
- `userId` MUST reference a valid user in User Management Service

### NotificationPreference (Domain)

| Field | Type | Required | Description |
|---|---|---|---|
| userId | UUID | Yes | User identifier |
| enabled | Boolean | Yes | Global notification enable/disable |
| channel | Channel | Yes | PUSH_NOTIFICATION, SMS, EMAIL |
| quietHours | QuietHoursConfig | No | Start/end time, enabled flag |
| frequency | Frequency | No | Frequency limit settings |
| language | Locale | Yes | Preferred notification language (en, de, fr, it) |
| timezone | String | Yes | IANA timezone |
| notificationTypes | Map\<Type, TypePreference\> | Yes | Per-type settings (enabled, channels, type-specific config) |

**Invariants:**
- `language` MUST be one of: en, de, fr, it
- Quiet hours `startTime` and `endTime` MUST be in HH:MM 24-hour format
- Each notification type preference MUST specify at least one channel if enabled

### NotificationTemplate

| Field | Type | Required | Description |
|---|---|---|---|
| templateId | String | Yes | Template identifier |
| type | NotificationType | Yes | Notification type |
| name | String | Yes | Template name |
| titleTemplate | String | Yes | Title with `{{placeholder}}` syntax |
| bodyTemplate | String | Yes | Body with `{{placeholder}}` syntax |
| priority | Priority | Yes | Default priority |
| language | Locale | Yes | Template language |
| placeholders | List\<String\> | No | Expected placeholder names |
| createdAt | DateTime | Yes | Creation timestamp |
| updatedAt | DateTime | No | Update timestamp |

### Device

| Field | Type | Required | Description |
|---|---|---|---|
| deviceId | String | Yes | Unique device identifier |
| userId | UUID | Yes | Owning user |
| deviceType | DeviceType | Yes | ios, android, web |
| pushToken | String | Yes | FCM push token |
| deviceName | String | No | User-friendly name |
| appVersion | String | No | App version |
| osVersion | String | No | OS version |
| lastActive | DateTime | No | Last activity |
| createdAt | DateTime | Yes | Registration timestamp |

### DeliveryResult (Value Object)

| Field | Type | Description |
|---|---|---|
| success | Boolean | Whether delivery succeeded |
| channel | Channel | Delivery channel |
| deliveredAt | DateTime | Delivery timestamp |
| errorMessage | String | Error details if failed |
| retryCount | Integer | Number of attempts |

---

## Interactions with Dependencies

### User Management Service (REST/HTTP)
- **Call**: `GET /api/users/{userId}` to retrieve user profile, contact info, and locale
- **Call**: `GET /api/users/{userId}/contact` for phone number and email
- **Error**: If unavailable, use cached user data from Redis; if no cache, fail the notification with a retryable error
- **Circuit Breaker**: Enabled with 5s timeout, 50% failure threshold

### Medication Management Service (REST/HTTP)
- **Call**: `GET /api/medications/{medicationId}` to retrieve medication name, dosage, schedule
- **Call**: `GET /api/medications/schedule/{userId}` for personalization of reminders
- **Error**: If unavailable, deliver notification with generic template (no medication-specific personalization)
- **Circuit Breaker**: Enabled with 5s timeout

### Localization Service (REST/HTTP)
- **Call**: `GET /api/translations/{key}/{locale}` for template translations
- **Error**: Fall back to English (en) if target locale translation unavailable
- **Cache**: Translations cached in Redis with 1-hour TTL

### Apache Kafka (Event Bus)
- **Consumed Topics**: `medication-events`, `user-events`, `prescription-events`
- **Consumed Events**: `MedicationReminderEvent`, `RefillReminderEvent`, `HealthEventNotification`, `InsuranceChangeEvent`
- **Published Topics**: `notification-events`
- **Published Events**: `NotificationSentEvent`, `NotificationDeliveredEvent`, `NotificationFailedEvent`, `PreferenceUpdatedEvent`
- **Delivery Guarantee**: At-least-once delivery with idempotency checks
- **Error**: Dead-letter queue for failed event processing after 3 retries

### Firebase Cloud Messaging (FCM)
- **Call**: Send push notification with device token, title, body, data payload
- **Error**: Retry up to 3 times with exponential backoff; if token invalid, mark device for cleanup
- **Payload**: JSON with `title`, `body`, `data` (actionUrl, notificationId, type)

### Email Service Provider (SMTP/REST)
- **Call**: Send email with recipient, subject, HTML body, optional attachments
- **Error**: Retry up to 3 times; log failure in delivery log
- **Format**: HTML email with responsive template

### SMS Gateway (REST)
- **Call**: Send SMS with phone number and text body (160-char limit with concatenation)
- **Error**: Retry up to 2 times; respect carrier rate limits

---

## Key Flows

### Flow 1: Event-Driven Notification Delivery

1. **KafkaEventConsumer** receives a `MedicationReminderEvent` from `medication-events` topic.
2. Consumer extracts `userId`, `medicationId`, `scheduledTime`, and `notificationType`.
3. **NotificationService** is invoked with the event payload.
4. NotificationService calls **UserServiceClient** to retrieve user profile and preferences (with Redis cache).
5. NotificationService calls **PreferenceService** to check if the notification type is enabled and resolve active channels.
6. **RateLimiter** checks whether the user has exceeded their notification frequency limit.
7. **TemplateService** retrieves the appropriate template for the notification type and user's locale, rendering it with **MedicationServiceClient** data (medication name, dosage).
8. **NotificationBundler** evaluates if this notification should be grouped with pending notifications.
9. For each enabled channel, NotificationService delegates to the appropriate adapter (**PushNotificationAdapter**, **EmailAdapter**, **SMSAdapter**).
10. **DeliveryStatusTracker** records delivery attempts and results; **KafkaEventProducer** publishes `NotificationSentEvent`.

### Flow 2: User Preference Update

1. Authenticated user sends `PUT /api/v1/notifications/preferences` with updated preferences.
2. **PreferenceController** validates the request body via **RequestValidator**.
3. **PreferenceService** persists the updated preferences via **PreferenceRepository** to MongoDB.
4. **CacheManager** invalidates the cached preferences in Redis.
5. **KafkaEventProducer** publishes a `PreferenceUpdatedEvent` for downstream services.
6. Updated `NotificationPreferences` are returned to the caller.

### Flow 3: Batch Notification Processing

1. Admin sends `POST /api/v1/notifications/batch` with target userIds/segment and notification content.
2. **NotificationController** validates the request and creates a batch record with status `queued`.
3. Batch is published to an internal Kafka topic for async processing.
4. **NotificationService** processes each recipient: resolving preferences, rendering templates, and delivering per-channel.
5. **DeliveryStatusTracker** updates batch progress (successCount, failureCount, pendingCount).
6. Admin can poll `GET /api/v1/notifications/batch/{batchId}/status` for progress.

### Flow 4: Real-Time WebSocket Notification

1. User establishes WebSocket connection at `CONNECT /ws/notifications` with JWT authentication.
2. **WebSocketHandler** validates the JWT, registers the session in an in-memory connection registry.
3. When a new notification is created for the user, **NotificationService** checks the WebSocket registry.
4. If the user has active sessions, the notification payload is pushed in real-time.
5. On disconnect, the session is removed from the registry; heartbeat pings maintain connection health.

### Flow 5: Scheduled Notification Delivery

1. **SchedulingService** stores a notification in Redis sorted set with the scheduled delivery timestamp as score.
2. A periodic job (every 60 seconds) queries Redis for notifications whose scheduled time has passed.
3. For each due notification, **SchedulingService** invokes **NotificationService** for standard delivery processing.
4. If delivery fails, the notification is rescheduled with backoff or marked as failed after max retries.

---

## Authentication and Authorization

- All API endpoints (except `/health`, `/health/liveness`, `/health/readiness`) MUST require a valid JWT Bearer token.
- Scopes: `notification:read`, `notification:write`, `notification:admin`.
- Batch and metrics endpoints MUST require `notification:admin` scope.
- User-scoped endpoints MUST enforce that the authenticated user can only access their own data.

## Error Handling

- All errors MUST follow the standard `Error` response schema with `code`, `message`, `timestamp`, and `requestId`.
- Validation errors MUST return 400 with `ValidationError` schema including per-field details.
- Authentication errors MUST return 401 with `WWW-Authenticate` header.
- Authorization errors MUST return 403.
- Rate limit exceeded MUST return 429 with `Retry-After` header.
- Sensitive information MUST NOT be exposed in error messages.

---