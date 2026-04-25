# Spec: Notification Service (SRV-004)

> Functional specification for the Notification Service — WHAT it does and WHY.

## Overview

The Notification Service is the centralized notification orchestration engine for the Swiss Medication Management System. It receives notification triggers from upstream domain services (via Kafka events and direct REST calls), resolves user preferences, renders localized template content, delivers notifications across push / SMS / email / WebSocket channels, tracks delivery status with retries, and maintains an auditable notification history.

The service directly supports the project's goal of **improving medication adherence by 35%** through timely medication reminders, refill alerts, and health event notifications — personalized per user, per language, per channel.

## User Stories

### US-1: As a patient, I want to receive medication reminders on my preferred channels so that I never miss a dose.
**Acceptance Criteria:**
- When a `MedicationReminderEvent` is published on Kafka, the service delivers a notification to all channels the user has enabled.
- The notification text is rendered in the user's preferred language using the `medication_reminder` template.
- Delivery respects quiet hours unless priority is `urgent`.
- If delivery fails on one channel, remaining channels are still attempted.

### US-2: As a patient, I want to configure which notification channels I receive alerts on, and set quiet hours, so I am not disturbed at night.
**Acceptance Criteria:**
- User can `GET` and `PUT` their preferences at `/api/v1/notifications/preferences`.
- Preferences include: global enabled/disabled, per-type enabled + channel list, quiet hours (start/end in HH:MM), timezone (IANA), language (en/de/fr/it).
- Preference changes take effect immediately (Redis cache is invalidated synchronously).

### US-3: As a patient, I want to view my notification history so I can review past reminders and alerts.
**Acceptance Criteria:**
- `GET /api/v1/notifications/history` returns a paginated list within the 90-day retention window.
- Filterable by `type`, `startDate`, `endDate`.
- Each record shows delivery status per channel.

### US-4: As a patient, I want real-time in-app notifications so I see alerts immediately when the app is open.
**Acceptance Criteria:**
- WebSocket connection at `/ws/notifications` authenticated via JWT.
- New notifications are pushed in real-time to all active sessions for the user.
- Connection supports heartbeat/ping-pong and auto-reconnect.

### US-5: As an administrator, I want to send batch notifications to user segments so I can communicate health updates efficiently.
**Acceptance Criteria:**
- `POST /api/v1/notifications/batch` accepts `userIds` or `userSegment`, and a notification payload.
- Returns `202 Accepted` with a `batchId`.
- Batch status queryable at `GET /api/v1/notifications/batch/{batchId}/status` with counts.

### US-6: As an administrator, I want to manage notification templates so content is consistent and localized.
**Acceptance Criteria:**
- Templates support `{{placeholder}}` syntax.
- Available in 4 locales: en, de, fr, it.
- Templates are versioned and cached in Redis.

## Notification Types

| Type | Trigger Source | Default Priority | Default Channels |
|---|---|---|---|
| `MEDICATION_REMINDER` | Kafka: `medication-events` | high | push, sms |
| `REFILL_REMINDER` | Kafka: `medication-events` | normal | push, email |
| `HEALTH_EVENT` | Kafka: `medication-events` | high | push, email |
| `PRESCRIPTION_EXPIRY` | Kafka: `prescription-events` | normal | push, email |
| `DRUG_INTERACTION` | Kafka: `medication-events` | urgent | push, sms, email |
| `INSURANCE_UPDATE` | Kafka: `prescription-events` | low | email |

## Constraints

- **Retention**: Notifications are retained for 90 days. Delivery logs for 180 days.
- **Rate Limiting**: Per-user, per-channel sliding-window limits enforced via Redis. Default: 50 notifications/day per channel.
- **Bundling Window**: Related notifications within a 5-minute window are grouped before delivery.
- **Supported Locales**: en, de, fr, it.
- **Supported Device Types**: ios, android, web.
- **SLA**: 95% of notifications delivered within 30 seconds of trigger.

## Out of Scope

- User authentication/registration (owned by User Management Service).
- Medication scheduling logic (owned by Medication Management Service).
- Insurance change detection (owned by Health Insurance Integration Service).
- Notification content moderation or approval workflows.
- Reporting dashboards (owned by Reporting Service).

---