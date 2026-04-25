# Proposal: Notification Service (SRV-004)

## Overview

The **Notification Service** (SRV-004) is a core infrastructure microservice within the Swiss Medication Management System responsible for managing the delivery of notifications across multiple channels (push, SMS, email) based on user preferences and event triggers. It provides intelligent notification capabilities with support for multi-channel delivery, smart bundling, scheduling, rate limiting, and comprehensive localization across German, French, Italian, and English.

## Business Value

- **Medication Adherence**: Timely medication reminders and refill alerts directly support the project's goal of improving adherence rates by 35%.
- **Patient Safety**: Critical health event notifications (drug interactions, prescription expiry) protect patients from adverse medication events.
- **User Engagement**: Personalized, multi-language notifications across preferred channels strengthen patient engagement and application stickiness.
- **Accessibility**: Multi-channel delivery ensures notifications reach users regardless of their primary device or connectivity status, supporting the 23% of users who rely on accessibility features.

## Scope

### In-Scope

- Multi-channel notification delivery: push (Firebase Cloud Messaging), SMS (SMS Gateway), email (Email Service Provider)
- Real-time WebSocket-based in-app notifications
- User notification preference management (channel selection, quiet hours, frequency, language, opt-out)
- Notification template management with localization (en, de, fr, it) and placeholder-based personalization
- Event-driven notification triggering via Apache Kafka (medication reminders, refill reminders, health events, insurance changes)
- Notification scheduling and timezone-aware delayed delivery
- Smart notification bundling to prevent notification fatigue
- Rate limiting per user based on preferences and priority
- Delivery status tracking with retry mechanisms
- Notification history with 90-day retention
- Batch notification capabilities for administrators
- Device registration and management for push notifications
- Health check and service metrics endpoints

### Out-of-Scope

- User authentication/authorization logic (delegated to API Gateway and User Management Service)
- Medication scheduling logic (owned by Medication Management Service)
- Insurance change detection (owned by Health Insurance Integration Service)
- Content moderation or notification approval workflows
- Analytics/reporting dashboards (owned by Reporting Service)

## Responsibilities

- Schedule, bundle, and deliver notifications across push, SMS, and email channels
- Manage user notification preferences with channel-specific, type-specific, and time-based controls
- Maintain and render localized notification templates with dynamic placeholders
- Consume domain events from Kafka topics (medication-events, user-events, prescription-events) and trigger appropriate notifications
- Publish notification lifecycle events (sent, delivered, failed) to Kafka for downstream consumption
- Track delivery status across all channels with retry/fallback behavior
- Enforce rate limits to prevent notification flooding
- Maintain notification history for user review
- Provide real-time notifications via WebSocket connections
- Expose health, readiness, and metrics endpoints for operational monitoring

## Impacted Systems and Dependencies

| System/Service | Relationship | Protocol |
|---|---|---|
| User Management Service | Retrieves user profiles, contact info, locale | REST API (HTTP) |
| Medication Management Service | Retrieves medication details and schedules for personalization | REST API (HTTP) |
| Localization Service | Retrieves translations and locale-specific formatting | REST API (HTTP) |
| API Gateway (Kong) | Inbound traffic routing, authentication, rate limiting | HTTP/REST, WebSocket |
| Apache Kafka (Event Bus) | Consumes medication/refill/health/insurance events; publishes notification lifecycle events | Kafka Consumer/Producer |
| MongoDB | Persistent storage for notifications, templates, preferences, delivery logs | MongoDB Driver |
| Redis | Caching (templates, preferences), rate limiting, notification scheduling | Redis Client |
| Firebase Cloud Messaging | Push notification delivery to iOS/Android/Web devices | FCM API |
| Email Service Provider | Email notification delivery (e.g., Amazon SES, SendGrid) | SMTP/REST API |
| SMS Gateway | SMS notification delivery (e.g., Twilio, AWS SNS) | REST API |

## Acceptance Criteria

### AC-1: Multi-Channel Notification Delivery
- **Given** a notification creation request with valid userId and type,
- **When** `POST /api/v1/notifications` (or equivalent internal trigger) is invoked,
- **Then** the service SHALL deliver the notification to all user-preferred channels (push, SMS, email) respecting quiet hours and rate limits.
- **And** delivery status SHALL be tracked per channel with statuses: pending → sent → delivered | failed.

### AC-2: User Preference Enforcement
- **Given** a user has configured preferences via `PUT /api/v1/notifications/preferences`,
- **When** a notification is triggered for that user,
- **Then** the service SHALL respect the user's enabled channels, quiet hours, language preference, and notification type settings.
- **And** notifications during quiet hours SHALL be deferred (unless priority is "urgent").

### AC-3: Event-Driven Notification Triggering
- **Given** the Kafka consumer is subscribed to `medication-events`, `user-events`, and `prescription-events` topics,
- **When** a MedicationReminderEvent, RefillReminderEvent, HealthEventNotification, or InsuranceChangeEvent is received,
- **Then** the service SHALL create and deliver an appropriate notification using the matching template and user preferences.

### AC-4: Template Rendering and Localization
- **Given** a notification template exists for a given type and locale,
- **When** the notification is being prepared for delivery,
- **Then** the service SHALL render the template with provided placeholder data in the user's preferred language (en, de, fr, it).

### AC-5: Smart Bundling and Rate Limiting
- **Given** multiple notifications are triggered for the same user in a short time window,
- **When** the NotificationBundler evaluates them,
- **Then** related notifications SHALL be grouped to prevent notification fatigue,
- **And** the RateLimiter SHALL enforce per-user frequency limits based on preferences and channel constraints.

### AC-6: Delivery Retry and Status Tracking
- **Given** a notification delivery attempt fails on a channel,
- **When** the DeliveryStatusTracker detects the failure,
- **Then** the service SHALL retry delivery up to the configured maximum retries,
- **And** the delivery log SHALL record each attempt with timestamp, status, and error details.

### AC-7: Notification History
- **Given** an authenticated user requests `GET /api/v1/notifications/history`,
- **Then** the service SHALL return a paginated list of notifications within the 90-day retention window, filterable by type and date range.

### AC-8: Real-Time WebSocket Delivery
- **Given** a user has an active WebSocket connection at `CONNECT /ws/notifications`,
- **When** a notification is created for that user,
- **Then** the service SHALL push the notification in real-time to all connected sessions.

### AC-9: Batch Notifications (Admin)
- **Given** an admin invokes `POST /api/v1/notifications/batch` with userIds or userSegment,
- **Then** the service SHALL queue and process batch delivery, returning a batchId,
- **And** status SHALL be queryable via `GET /api/v1/notifications/batch/{batchId}/status`.

### AC-10: Health and Observability
- **Given** the health endpoint `GET /api/v1/notifications/health` is invoked,
- **Then** the service SHALL report health status including dependency checks (MongoDB, Redis, Kafka, push/SMS/email providers).

## Related Features

- **F-04**: Notification preferences and reminders
- **F-06**: Multi-channel notification delivery
- **F-07**: Notification scheduling and bundling
- **F-09**: Localization and accessibility

---