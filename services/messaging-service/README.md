# messaging-service

Buyer-seller direct messaging MVP. Mirrors notification-service shape (NestJS +
MikroORM + Kafka + JWT-validated REST) and adds a WebSocket gateway for real-time
delivery.

## Endpoints

REST (all `@UseGuards(JwtAuthGuard)`, `userId` derived from `req.user.sub`):

- `GET /messaging/threads` — paginated thread list for the caller
- `GET /messaging/threads/:id/messages?cursor=&limit=` — paginated messages
- `POST /messaging/threads` — `{ recipientId, productId? }`. Idempotent on
  `(buyerId, sellerId, productId)` so opening Chat from a product page twice
  surfaces the same thread.
- `POST /messaging/threads/:id/messages` — `{ body }`. Honours
  `Idempotency-Key` header (in-memory store; deduped per pod).
- `POST /messaging/threads/:id/read` — marks the thread read up to the latest
  message for the caller.

WebSocket: `GET /ws/messaging?token=<JWT>` (or `Authorization: Bearer ...`).
Each socket is bound to the JWT subject; the server pushes `{type:'message', ...}`
events received via the Kafka consumer for `messaging.message.sent`.

## Persistence

`messaging_svc.threads` + `messaging_svc.messages` (see
`src/db/migration/V1__messaging_schema.sql`). Composite index on
`(thread_id, sent_at DESC)` for thread-detail pagination.

## Why a separate service

Keeps Kafka topic ownership and Postgres schema isolated, mirrors the rest of
the polyglot platform, and matches the existing notification-service template
(JWT in, sub-scoped persistence, Kafka fan-out). NestJS is reused for the
WebSocket ergonomics.
