# Session handover — 2026-05-30 (pt47: Notification Platform — WebSocket, MongoDB, threading, FE inbox)

**Last commit (HEAD):** `26177f3b` (`feat(notification): real-time notification platform with WebSocket, MongoDB, threading`)

**Gates:**
- notification-service jest: 86 / 86 (16 suites — full rewrite this block).
- FE vitest: 169 / 169 (28 suites — +notification components).
- FE typecheck: 0 errors.
- E2E pipeline: 3 / 3 (WebSocket delivery, persistence, dedup).
- order-service mvn: 144 / 144 (unchanged from pt46).
- user-service mvn: 141 / 141 (unchanged).
- payment-service mvn: 89 / 89 (unchanged).
- product-service mvn: 33 / 33 (unchanged).
- seller-finance-service mvn: 20 / 20 (unchanged).
- search-service mvn: 11 / 11 (unchanged).
- recommendations-service mvn: 53 / 53 (unchanged).

## What this block was

Full notification platform build — enterprise-grade real-time notification system replacing the polling-based bell. Single atomic commit: 106 files changed, 9,740 insertions, 2,425 deletions.

### Commit

| # | SHA | Summary |
|---|-----|---------|
| 1 | `26177f3b` | Real-time notification platform with WebSocket, MongoDB, threading |

### Architecture: DDD Hexagonal + OOP + SOLID

```
src/notification/
├── domain/           # Zero framework imports — pure TypeScript
│   ├── model/        # Aggregate root, value objects, enums
│   ├── event/        # Domain events (DomainEvent interface)
│   ├── port/outbound # Repository, RealtimeChannel, Dedup, ConnectionRegistry
│   └── service/      # DeliveryPolicy (Strategy pattern)
├── application/      # Use case orchestration
│   ├── command/      # SendNotification, MarkRead, MarkAllRead, RetryFailed
│   ├── query/        # FindUserNotifications, FindThreads, CountUnread
│   └── event-handler # NotificationCreatedHandler (@OnEvent)
└── infrastructure/   # Framework adapters
    ├── persistence/  # Mongoose schema, mapper, MongoNotificationRepository
    ├── cache/        # Redis dedup (SET NX), connection registry (2min TTL + heartbeat)
    ├── realtime/     # Socket.io gateway + RealtimeChannelAdapter
    ├── messaging/    # Kafka consumer (11 topics, 12 notification types)
    ├── rest/         # Controller + DTOs
    └── auth/         # JWT strategy (Keycloak JWKS)
```

### Backend changes (notification-service, NestJS)

**Database migration: PostgreSQL → MongoDB**
- Removed MikroORM, pg dependencies. Added Mongoose, @nestjs/mongoose.
- Mongoose schema with compound indexes (userId+createdAt, userId+threadId, idempotencyKey unique sparse).
- TTL index on `expiresAt` for 90-day auto-expiry.
- `$facet` aggregation pipeline for thread pagination (no in-memory slicing).

**Domain model (12 notification types):**
- `NotificationType`: ORDER_CREATED, ORDER_CANCELLED, ORDER_SHIPPED, ORDER_DELIVERED, PAYMENT_COMPLETED, PAYMENT_REFUNDED, SELLER_NEW_ORDER, PRODUCT_APPROVED, PRODUCT_REJECTED, REVIEW_REPLIED, RETURN_REQUESTED, PAYOUT_COMPLETED
- `DeliveryStatus` state machine: QUEUED → SENT → DELIVERED → OPENED; failure path FAILED → DLQ
- `Notification` aggregate: markSent(), markDelivered(), markFailed(), retry(), moveToDlq(), markRead(), pullDomainEvents()
- `retryCount` field with `canRetry(max)` and `incrementRetry()` for DLQ gating
- `NotificationThread` value object: threadId + threadTitle for grouping

**WebSocket gateway (socket.io):**
- Namespace: `/ws/notifications`
- Auth: JWT via `handshake.auth.token` (JWKS validation), query param fallback
- Room per userId, heartbeat refresh every 30s (2min TTL on socket registration)
- Offline queue: Redis list, drained on reconnect as `notification:catch-up` batch
- CORS restricted to configured origins (not `*`)

**Redis adapters:**
- `RedisDeduplicationAdapter`: `tryAcquire()` using SET NX (atomic, no TOCTOU race)
- `RedisConnectionRegistryAdapter`: SADD/SREM for sockets, MULTI transaction for atomic drain, LTRIM cap at 500 items, 2min TTL with heartbeat refresh

**Kafka consumer (11 topics):**
- Each `@MessagePattern` handler builds a `SendNotificationCommand` with Vietnamese copy, deepLink, thread, priority, and idempotency key
- `order.created` fans out to buyer (ORDER_CREATED) + seller (SELLER_NEW_ORDER) with distinct threads
- `sanitizeMetadata()` strips internal IDs (buyerId, sellerId) before persistence
- Error per-notification (one failure doesn't drop the batch)

**Application layer:**
- `SendNotificationUseCase`: atomic dedup via tryAcquire → create aggregate → persist → emit domain event
- `NotificationCreatedHandler`: checks isOnline → sendToUser (stays SENT, no premature DELIVERED) or enqueueOffline
- `MarkNotificationReadUseCase`: findByIdAndUserId → markRead() → save
- `MarkAllReadUseCase`: bulk updateMany (documented domain bypass for performance)
- Query use cases: paginated list with type filter, thread aggregation, unread count

**REST controller:**
- `GET /notifications?type=&threadId=&page=&size=` — paginated, Spring Page envelope
- `GET /notifications/unread-count` — `{ count }`
- `GET /notifications/threads?type=&page=&size=` — thread summaries
- `GET /notifications/threads/:threadId` — all notifications in thread
- `POST /notifications/:id/read` — mark single read
- `POST /notifications/mark-all-read` — bulk mark read
- `POST /notifications/test` — guarded by NODE_ENV (disabled in production)
- Input validation: page ≥ 0, 1 ≤ size ≤ 100

**Infrastructure:**
- MongoDB 7.0 in docker-compose (base service, healthcheck)
- notification-service depends_on mongo + redis
- API gateway: `/ws/notifications` WebSocket upgrade route in RouteConfig.java
- K8s workloads.yaml: PostgreSQL env vars → MONGO_URI + REDIS_URL

### Frontend changes (React 18 + TanStack Query 5)

**Zod schemas + API endpoints:**
- `notificationTypeSchema` (12 enum values), `prioritySchema`, `notificationSchema`, `notificationThreadSchema`
- Page envelope schemas matching Spring Page shape
- Endpoints: `listNotifications`, `listThreads`, `getThreadNotifications`, `markNotificationRead`, `markAllNotificationsRead`, `unreadNotificationCount`

**useNotificationSocket hook:**
- socket.io-client with `auth: { token }` (not query string)
- Events: `notification:new` → cache insert + unread increment + toast; `notification:catch-up` → batch merge
- Manual exponential backoff reconnect (1s → 30s cap)
- Old socket cleanup (removeAllListeners + disconnect) before reconnect
- Mounted in `BackgroundEffects` alongside `useMessagingSocket`

**Enhanced NotificationBell:**
- `NotificationIcon` component: 12 type-specific Tabler icons with color mapping
- Date grouping: "Hôm nay", "Hôm qua", "Trước đó"
- Mark-all-read button in header
- Bell pulse animation on new notification (prevUnreadRef tracking)
- Limited to 10 items, chronological order
- "Xem tất cả" links to `/notifications`
- Polling removed — WebSocket is sole real-time channel

**NotificationToast:**
- Custom Sonner toast with NotificationIcon + title + body
- Clickable → deepLink navigation
- 5s auto-dismiss, position top-right

**/notifications page:**
- `NotificationFilters`: 6 tabs (Tất cả, Đơn hàng, Thanh toán, Vận chuyển, Đánh giá, Người bán)
- `NotificationThreadList`: queries threads, skeleton loading, empty state
- `NotificationThread`: expandable accordion, lazy-fetches items on expand
- `NotificationItem`: type icon, relative time, unread dot, deepLink navigation
- `NotificationPagination`: prev/next with page counter
- URL-driven state: `?type=&page=` (shareable, back-button works)
- Route: `/notifications` (authenticated, lazy-loaded)

### Code review findings (all resolved)

22 issues found and fixed in this session:
- 2 CRITICAL: race condition in offline queue drain (→ MULTI transaction), TOCTOU in dedup (→ SET NX atomic acquire)
- 6 HIGH: unbounded queue (→ LTRIM 500), N+1 catch-up (→ findByIds $in), CORS wildcard (→ env config), token in query (→ auth option), markAllRead bypass (→ documented), duplicate factory (→ deleted)
- 9 MEDIUM: stale sockets (→ 2min TTL + heartbeat), no delivery ACK (→ stays SENT), input validation (→ clamped), in-memory pagination (→ $facet), prevUnreadRef bug (→ always update), socket leak on reconnect (→ cleanup), test endpoint in prod (→ ForbiddenException)
- 5 LOW: narrow event type (→ DomainEvent interface), unregister error (→ try/catch), sort order (→ chronological), no retry limit (→ retryCount field), raw metadata (→ sanitizeMetadata)

## Gotchas this block

**#123. socket.io-client reconnection in React.** Don't use socket.io's built-in `reconnection: true` — the JWT token may refresh between reconnects. Manual backoff with fresh token on each connect attempt is required.

**#124. Mongoose `type` field name is reserved.** Must use `{ type: String }` explicitly in `@Prop` decorator or Mongoose interprets the field as a schema type definition, not a data field.

**#125. EventEmitterModule.forRoot() placement.** Must be imported in the feature module (NotificationModule), not just AppModule, for `@OnEvent` decorators to register correctly.

**#126. MongoDB aggregation null threadId.** Notifications without threads create a null-key group in `$group` stage. Filter with `{ threadId: { $ne: null } }` in `$match` before grouping.

**#127. order.created dual-recipient routing.** The Kafka event has both buyerId and sellerId. The consumer must send separate notifications to each with distinct deepLinks, threadIds, and idempotency keys. A single factory call with shared recipientId doesn't work.

**#128. Redis LRANGE + DEL race condition.** Non-atomic read-then-delete loses notifications enqueued between the two commands. Always use MULTI transaction for drain operations.

**#129. SET NX for deduplication.** Check-then-set (`isDuplicate` → `markProcessed`) has a TOCTOU window under concurrent Kafka consumers. `SET key 1 EX ttl NX` is atomic — first caller wins, second gets null.

**#130. Socket.io emit is fire-and-forget.** Don't mark DELIVERED immediately after emit — the client may never receive it. Keep at SENT until client ACK (future enhancement).

**#131. Redis socket registration TTL.** 24h TTL means stale entries persist after server crash. Use short TTL (2min) with periodic heartbeat refresh from the gateway.

## Open threads for the next session

**Closed by this block:**
- ~~Notifications inbox (FE bell icon)~~ — full platform built.

**Still open (from pt46, unchanged):**
1. **PayPal sandbox manual smoke.** Credentials in `.env`, code complete. Needs `docker compose up` with `apps` profile and a browser walkthrough.
2. **GHN/GHTK shipping adapter.** Gated on third-party API key.
3. **Fix profile/runtime mismatches (F-05).** Routes point to services not started in `apps` profile.
4. **Remove review-service empty shell.** Low priority cleanup.
5. **Mockito self-attach warning on Java 25.** Noisy test output; will break in future JDK.

**New from this block:**
6. **Client-side delivery ACK.** Notifications stay at SENT — add `notification:ack` event from FE to confirm DELIVERED status.
7. **Notification preferences UI.** Engine is stubbed (all enabled). User settings page to toggle types on/off.
8. **Email channel adapter.** SES integration for the existing stub channel.

**Recommended pick for pt48:** PayPal sandbox smoke test (spin up stack, walk through payment). Then client-side delivery ACK (small, closes the SENT→DELIVERED gap).

## How to resume

1. **Verify HEAD.** `git log --oneline -1` shows `26177f3b feat(notification): real-time notification platform...`
2. **Smoke gates:**
   - `cd services/notification-service; npx jest --no-coverage --forceExit` → 86 / 86.
   - `cd fe; npx vitest run` → 169 / 169.
   - `cd fe; npx tsc --noEmit` → 0 errors.
3. **Docker verification:** `docker compose up mongo redis -d` → both healthy.
4. **Full stack:** `docker compose --profile apps up -d` → notification-service connects to mongo + redis.

## Final session ledger (pt27 → pt47)

- **pt27-pt34**: i18n, dark-mode, BA-grade journey suite, chapter-6 flake root-cause.
- **pt35**: payout audit trail.
- **pt36**: avatar upload, MinIO+R2-swap.
- **pt37**: Ship/Accept access-control.
- **pt38**: order-service IAE-as-403 sweep.
- **pt39**: payment-service sweep + missing 403 handler.
- **pt40**: status-code oracle close on lookup misses.
- **pt41**: kafka env-override sweep.
- **pt42**: PayPal refund saga close + 12-commit gap reconciliation.
- **pt43**: PaymentRefunded consumers — buyer-visible refund saga closed.
- **pt44**: Four production-hardening threads (commission tier propagation, idempotency, health probe, FX fields).
- **pt45**: FX event pipeline, per-seller commission tier, consumer health probes.
- **pt46**: F-01/F-04 fixes, R2 swap, Prometheus metrics, batch tier.
- **pt47 (this)**: Notification platform — WebSocket, MongoDB, threading, 12 event types, FE inbox. 106 files, 9740 insertions.
