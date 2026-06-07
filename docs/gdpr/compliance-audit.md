# GDPR Compliance Audit — VNShop User Service

**Date:** 2026-06-07
**Branch:** feat/production-readiness-fixes
**Service:** `services/user-service` (Spring Boot)

---

## Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `POST` | `/api/v1/gdpr/export/{userId}` | Self or admin | Initiates async data export |
| `GET` | `/api/v1/gdpr/export/{userId}/status/{requestId}` | Self or admin | Polls export status |
| `DELETE` | `/api/v1/gdpr/delete/{userId}` | Self or admin | Initiates deletion and anonymization |

All endpoints are guarded by `@PreAuthorize("#userId == authentication.name or hasRole('admin')")`.

---

## Services Covered

### Data Export (`GdprExportUseCase`)
- Publishes `gdpr.export-requested` Kafka event with `userId` and `requestId`.
- Rate-limited: rejects a second export request within one hour.
- Downstream services are expected to listen to the Kafka topic and respond with their data fragments.
- Export status is tracked in `user_svc.gdpr_export_requests` (V5 schema).

### Data Deletion (`GdprDeleteUseCase`)
- Broadcasts `gdpr.deletion-requested` Kafka event consumed by downstream services.
- Downstream services covered: `order-service`, `payment-service`, `shipping-service`.
- User record is anonymized in-place immediately via `UserRepositoryPort.anonymize()`.
- Per-service status is tracked in `user_svc.gdpr_deletion_service_status` (V5 schema).
- Aggregate request record tracked in `user_svc.gdpr_deletion_requests` (V6 schema).

---

## Database Migrations

| Version | File | Purpose |
|---------|------|---------|
| V5 | `V5__gdpr_deletion_status.sql` | Per-service deletion status rows |
| V6 | `V6__gdpr_deletion_requests.sql` | Aggregate deletion request record with `status`, `created_at`, `completed_at`, `failed_services` |

The `gdpr_deletion_requests` table schema matches the Track 5.3 specification:
- `id UUID PK`, `user_id VARCHAR`, `status VARCHAR`, `created_at TIMESTAMPTZ`, `completed_at TIMESTAMPTZ`, `failed_services TEXT`

---

## Error Handling

`GdprController` wraps all use-case calls in try-catch blocks:
- `IllegalStateException` (rate limit) → HTTP 429 with JSON error body
- `IllegalArgumentException` (not found) → HTTP 404
- All other exceptions → HTTP 500 with generic error message + server-side log (no stack trace leaked to client)

No raw stack traces are returned to callers.

---

## Gaps and Recommendations

### Gap 1 — Export fragments not aggregated with a deadline
The export flow publishes a Kafka event and relies on downstream services to push fragments back. There is no timeout or deadline enforcement: if a downstream service never responds, the export request stays `PENDING` indefinitely.

**Recommendation:** Add a scheduled job that moves export requests older than 24 hours to `PARTIAL` or `FAILED` status and notifies the user.

### Gap 2 — Deletion completion not confirmed
`GdprDeleteUseCase.initiateDelete()` fires the Kafka event and immediately anonymizes the user record, but does not wait for downstream acknowledgment. The `gdpr_deletion_requests` aggregate row is never updated from `PENDING` to `COMPLETED`.

**Recommendation:** Wire `GdprDeletionCompletedListener` to update the aggregate row status once all downstream service statuses in `gdpr_deletion_service_status` reach a terminal state.

### Gap 3 — cart-service not in downstream deletion list
`GdprDeleteUseCase.DOWNSTREAM_SERVICES` lists `order-service`, `payment-service`, and `shipping-service`. The `cart-service` holds user cart data and is not included.

**Recommendation:** Add `"cart-service"` to `DOWNSTREAM_SERVICES` and ensure the cart-service Kafka consumer handles `gdpr.deletion-requested`.

### Gap 4 — No deletion confirmation to user
There is no mechanism to notify the user (email/webhook) when deletion is fully complete across all services.

**Recommendation:** Once the aggregate status transitions to `COMPLETED`, publish a `gdpr.deletion-completed` event that triggers a confirmation notification.

---

## Summary

Core GDPR mechanics are in place: endpoints are authenticated, data export and deletion are event-driven, error handling does not leak internals, and the database schema captures both per-service and aggregate deletion state. The primary gaps are around completion tracking and the missing cart-service coverage, both of which are addressable without redesigning the existing flow.
