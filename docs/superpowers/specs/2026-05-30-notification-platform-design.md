# Notification Platform — Design Spec

**Date:** 2026-05-30  
**Status:** Draft  
**Scope:** pt47 — WebSocket real-time push, expanded event types, threading, enhanced FE inbox

---

## 1. Overview

Enterprise-grade notification platform for VNShop marketplace. Replaces the current polling-based notification bell with a full pipeline: event ingestion → processing → multi-channel delivery → tracking.

**Goals:**
- Real-time in-app delivery via WebSocket (no polling fallback)
- Threaded notifications (group by entity — order, product, return)
- Expanded event coverage (9 types across buyer + seller flows)
- Dedicated `/notifications` page with filtering, pagination, thread expansion
- Enhanced dropdown (type icons, date groups, mark-all-read, toast on new)
- Production-grade reliability: dedup, retry, dead-letter, TTL auto-expiry

**Non-goals (future):**
- Email delivery (stub adapter only)
- Mobile push / FCM / APNs (no mobile app yet)
- User preference UI (engine stubbed, all enabled)
- Admin template CRUD
- Analytics dashboard

---

## 2. System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        EVENT PRODUCERS                               │
│  order │ payment │ product │ user │ seller-finance │ review          │
└────┬───────┬──────────┬────────┬──────────┬────────────┬────────────┘
     │       │          │        │          │            │
     ▼       ▼          ▼        ▼          ▼            ▼
┌─────────────────────────────────────────────────────────────────────┐
│                    KAFKA CLUSTER                                     │
│  Topics: order.created, order.cancelled, order.shipped,             │
│          order.delivered, payment.completed, payment.refunded,       │
│          product.approved, product.rejected, review.replied,         │
│          return.requested, payout.completed                          │
└────────────────────────────────┬────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│              NOTIFICATION SERVICE (NestJS + MongoDB)                 │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────┐                                               │
│  │ 1. INGESTION    │                                               │
│  │ KafkaConsumer   │  Validates, dedup check (Redis), persists     │
│  │ + REST API      │  NotificationRequest, publishes to internal   │
│  └────────┬────────┘  topic "notification.process"                 │
│           │                                                         │
│           ▼                                                         │
│  ┌─────────────────┐                                               │
│  │ 2. PROCESSOR    │                                               │
│  │ Core Logic      │  Preferences check, template resolution,      │
│  │                 │  thread assignment, channel routing,           │
│  │                 │  priority assignment                           │
│  └────────┬────────┘                                               │
│           │                                                         │
│           ▼                                                         │
│  ┌─────────────────┐                                               │
│  │ 3. DELIVERY     │                                               │
│  │ In-App Worker   │  WebSocket emit to user room                  │
│  │                 │  Offline → Redis catch-up queue                │
│  │                 │  Retry (3x, exp backoff) → DLQ                │
│  └────────┬────────┘                                               │
│           │                                                         │
│           ▼                                                         │
│  ┌─────────────────┐                                               │
│  │ 4. GATEWAY      │                                               │
│  │ WebSocket       │  /ws/notifications, JWT auth,                 │
│  │ (socket.io)     │  room per userId, heartbeat 25s               │
│  └─────────────────┘                                               │
│                                                                     │
│  ┌─────────────────┐  ┌──────────────┐  ┌──────────────────────┐  │
│  │ REST Controller │  │ MongoDB      │  │ Redis                 │  │
│  │ /notifications  │  │ (persistent) │  │ (hot state)           │  │
│  └─────────────────┘  └──────────────┘  └──────────────────────┘  │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│              API GATEWAY (Spring Cloud)                              │
│                                                                     │
│  /notifications/**  → REST (circuit breaker)                        │
│  /ws/notifications  → WebSocket upgrade                             │
└─────────────────────────────────────────────────────────────────────┘
                                 │
                                 ▼
┌─────────────────────────────────────────────────────────────────────┐
│              FRONTEND (React 18 SPA)                                 │
│                                                                     │
│  useNotificationSocket() — BackgroundEffects                        │
│  NotificationBell — enhanced dropdown (10 items, icons, groups)     │
│  /notifications — full page (threads, filters, pagination)          │
│  Sonner toast — on new notification arrival                         │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. Data Model (MongoDB)

**Database:** `notification_db`

### 3.1 Collection: `notifications`

```json
{
  "_id": ObjectId,
  "requestId": "uuid",
  "idempotencyKey": "order-shipped:VN2024-abc",
  "sourceService": "order-service",
  "type": "ORDER_SHIPPED",
  "recipientId": "user-uuid",
  "threadId": "order:VN2024-abc",
  "threadTitle": "Đơn hàng #VN2024-abc",
  "title": "Đơn hàng đang vận chuyển",
  "body": "Đơn hàng #VN2024-abc đã được giao cho đơn vị vận chuyển",
  "deepLink": "/orders/VN2024-abc",
  "priority": "HIGH",
  "payload": {
    "orderId": "VN2024-abc",
    "carrier": "GHN",
    "trackingCode": "GHN123456"
  },
  "read": false,
  "readAt": null,
  "delivery": {
    "channel": "IN_APP",
    "status": "DELIVERED",
    "attempts": 1,
    "sentAt": "2026-05-30T10:00:00Z",
    "deliveredAt": "2026-05-30T10:00:01Z",
    "openedAt": null,
    "lastError": null,
    "nextRetryAt": null
  },
  "createdAt": "2026-05-30T10:00:00Z",
  "expiresAt": "2026-08-28T10:00:00Z"
}
```

**Indexes:**
```javascript
{ recipientId: 1, createdAt: -1 }                    // user's notifications by time
{ recipientId: 1, threadId: 1, createdAt: -1 }       // thread view
{ recipientId: 1, read: 1 }                          // unread count (covered)
{ recipientId: 1, type: 1, createdAt: -1 }           // filter by type
{ idempotencyKey: 1 }, { unique: true }              // dedup
{ expiresAt: 1 }, { expireAfterSeconds: 0 }          // TTL auto-delete (90 days)
{ "delivery.status": 1, "delivery.nextRetryAt": 1 }  // retry queue
```

### 3.2 Collection: `user_preferences`

```json
{
  "_id": "user-uuid",
  "channels": {
    "IN_APP": { "enabled": true },
    "EMAIL": { "enabled": true, "address": "user@example.com" },
    "PUSH": { "enabled": false }
  },
  "typeOverrides": {
    "PROMOTION": { "enabled": false },
    "ORDER_SHIPPED": { "channels": ["IN_APP"] }
  },
  "quietHours": { "start": "23:00", "end": "07:00", "timezone": "Asia/Ho_Chi_Minh" },
  "frequencyCap": { "max": 50, "windowHours": 24 },
  "updatedAt": "2026-05-30T10:00:00Z"
}
```

### 3.3 Collection: `templates`

```json
{
  "_id": ObjectId,
  "type": "ORDER_SHIPPED",
  "locale": "vi",
  "titleTemplate": "Đơn hàng đang vận chuyển",
  "bodyTemplate": "Đơn hàng #{{orderId}} đã được giao cho {{carrier}}",
  "deepLinkTemplate": "/orders/{{orderId}}",
  "defaultChannels": ["IN_APP", "EMAIL"],
  "priority": "HIGH",
  "active": true
}
```

### 3.4 Collection: `delivery_dead_letters`

```json
{
  "_id": ObjectId,
  "notificationId": ObjectId,
  "channel": "IN_APP",
  "attempts": 3,
  "lastError": "WebSocket emit timeout",
  "payload": {},
  "createdAt": "2026-05-30T10:00:00Z",
  "resolvedAt": null
}
```

---

## 4. Code Architecture (DDD Hexagonal + OOP + DRY + SOLID)

### 4.1 Layer Structure (Hexagonal / Ports & Adapters)

```
src/notification/
├── domain/                          # Pure business logic — no framework imports
│   ├── model/
│   │   ├── notification.ts          # Aggregate root (entity with behavior)
│   │   ├── notification-thread.ts   # Value object
│   │   ├── delivery-status.ts       # Value object (state machine)
│   │   ├── notification-type.enum.ts
│   │   └── priority.enum.ts
│   ├── event/
│   │   └── notification-created.event.ts   # Domain event
│   ├── port/
│   │   ├── inbound/
│   │   │   ├── send-notification.port.ts        # Use case interface
│   │   │   ├── query-notifications.port.ts      # Read use case interface
│   │   │   └── manage-delivery.port.ts          # Retry/DLQ use case interface
│   │   └── outbound/
│   │       ├── notification.repository.ts       # Persistence port
│   │       ├── realtime-channel.port.ts         # WebSocket delivery port
│   │       ├── deduplication.port.ts            # Idempotency port
│   │       ├── connection-registry.port.ts      # Online/offline tracking port
│   │       └── event-publisher.port.ts          # Internal event bus port
│   └── service/
│       ├── notification-factory.ts              # Factory: event → Notification aggregate
│       └── delivery-policy.ts                   # Strategy: routing + retry rules
│
├── application/                     # Use case orchestration — depends on domain only
│   ├── command/
│   │   ├── send-notification.use-case.ts
│   │   ├── mark-notification-read.use-case.ts
│   │   ├── mark-all-read.use-case.ts
│   │   └── retry-failed-deliveries.use-case.ts
│   ├── query/
│   │   ├── find-user-notifications.use-case.ts
│   │   ├── find-notification-threads.use-case.ts
│   │   ├── count-unread.use-case.ts
│   │   └── find-thread-notifications.use-case.ts
│   └── event-handler/
│       └── notification-created.handler.ts      # Triggers delivery after persist
│
├── infrastructure/                  # Adapters — implements ports, depends on frameworks
│   ├── persistence/
│   │   ├── mongo-notification.repository.ts     # Implements NotificationRepository
│   │   ├── mongo-notification.schema.ts         # Mongoose schema
│   │   └── notification.mapper.ts               # Domain ↔ Persistence mapping
│   ├── messaging/
│   │   ├── kafka-event.consumer.ts              # Inbound adapter (Kafka → use case)
│   │   └── kafka-event.publisher.ts             # Outbound adapter (domain events)
│   ├── realtime/
│   │   ├── socketio-notification.gateway.ts     # WebSocket gateway (socket.io)
│   │   └── socketio-realtime-channel.adapter.ts # Implements RealtimeChannelPort
│   ├── cache/
│   │   ├── redis-deduplication.adapter.ts       # Implements DeduplicationPort
│   │   └── redis-connection-registry.adapter.ts # Implements ConnectionRegistryPort
│   ├── rest/
│   │   ├── notification.controller.ts           # HTTP inbound adapter
│   │   └── dto/                                 # Request/Response DTOs (infra only)
│   │       ├── notification-response.dto.ts
│   │       ├── thread-response.dto.ts
│   │       └── page-response.dto.ts
│   └── auth/
│       ├── jwt-auth.guard.ts
│       └── jwt.strategy.ts
│
└── notification.module.ts           # DI wiring — binds ports to adapters
```

### 4.2 DDD Principles Applied

| Principle | Application |
|---|---|
| **Aggregate Root** | `Notification` entity owns its `DeliveryStatus`. All state changes go through methods on the aggregate (`markRead()`, `markDelivered()`, `failDelivery()`). No direct field mutation. |
| **Value Objects** | `DeliveryStatus` (state machine), `NotificationThread` (threadId + title), `Priority`. Immutable, equality by value. |
| **Domain Events** | `NotificationCreatedEvent` emitted after persist. Decouples persistence from delivery — handler triggers WebSocket push. |
| **Factory** | `NotificationFactory.fromKafkaEvent(topic, payload)` — encapsulates the mapping from 12 Kafka event shapes into a single `Notification` aggregate. Single Responsibility. |
| **Repository** | Port interface in domain, Mongoose implementation in infrastructure. Domain never imports Mongoose. |
| **Domain Service** | `DeliveryPolicy` — encapsulates retry rules, channel routing, priority logic. Stateless, injectable. |

### 4.3 SOLID Principles Applied

| Principle | Application |
|---|---|
| **S — Single Responsibility** | Each use case does one thing. `SendNotificationUseCase` persists + emits event. `RetryFailedDeliveriesUseCase` handles retry loop. Kafka consumer only maps events → calls use case. |
| **O — Open/Closed** | New notification types: add a handler in `NotificationFactory` mapping, no changes to use cases or delivery pipeline. New channels: implement `RealtimeChannelPort` (or future `EmailChannelPort`), bind in module. |
| **L — Liskov Substitution** | All port implementations are interchangeable. `RedisDeduplicationAdapter` can be swapped for an in-memory version in tests without changing use case code. |
| **I — Interface Segregation** | Ports are narrow: `DeduplicationPort` has `exists(key)` + `mark(key, ttl)` only. `ConnectionRegistryPort` has `register()`, `unregister()`, `isOnline()`, `getOfflineQueue()`. No god-interfaces. |
| **D — Dependency Inversion** | Use cases depend on port interfaces (abstractions). Infrastructure adapters depend on port interfaces. Module wiring (DI) binds concrete → abstract. Domain has zero framework imports. |

### 4.4 DRY Patterns

| Pattern | Where |
|---|---|
| **Shared mapper** | `NotificationMapper.toDomain()` / `.toPersistence()` / `.toResponse()` — one place for all shape conversions. |
| **Base pagination** | Generic `PageResult<T>` and `PaginationParams` reused across all query use cases. |
| **Event-to-notification factory** | Single `NotificationFactory` handles all 12 Kafka event types via a strategy map — no duplicated mapping logic per handler. |
| **Delivery state machine** | `DeliveryStatus.transition(event)` — state transitions defined once, enforced everywhere. Invalid transitions throw domain exceptions. |
| **Response DTO builder** | `NotificationResponseDto.from(notification)` — one static factory, used by controller and WebSocket gateway. |

### 4.5 OOP Patterns

| Pattern | Where |
|---|---|
| **Strategy** | `DeliveryPolicy` selects channel + retry config based on notification type and priority. Swappable per environment. |
| **Factory Method** | `NotificationFactory.fromKafkaEvent()` — polymorphic creation based on topic. |
| **State** | `DeliveryStatus` as a state machine — `QUEUED → SENT → DELIVERED → OPENED` or `QUEUED → FAILED → DLQ`. Each state knows its valid transitions. |
| **Observer** | Domain events (`NotificationCreatedEvent`) decouple persistence from side effects (WebSocket push, future email). |
| **Template Method** | Base `KafkaEventHandler` defines the skeleton (validate → extract → build input → call use case). Each topic handler overrides `extractNotificationInput()`. |
| **Repository** | Abstract persistence behind a domain-owned interface. Concrete adapter is an implementation detail. |

### 4.6 Dependency Rule (strict)

```
domain/ ← application/ ← infrastructure/
   │           │                │
   │           │                ├── imports NestJS, Mongoose, socket.io, ioredis
   │           │                └── implements domain ports
   │           │
   │           ├── imports domain models + ports
   │           └── NO framework imports (only NestJS @Injectable for DI)
   │
   ├── ZERO external imports
   └── Pure TypeScript: interfaces, classes, enums, value objects
```

**Enforcement:** ESLint `no-restricted-imports` rule prevents domain/ from importing anything in infrastructure/ or node_modules (except TypeScript built-ins).

---

## 5. Backend Implementation (notification-service)

### 5.1 Database Migration: PostgreSQL → MongoDB

- Remove `@mikro-orm/postgresql`, `@mikro-orm/nestjs`, `pg`
- Add `@nestjs/mongoose`, `mongoose`
- Replace `NotificationMikroOrmEntity` with Mongoose schemas
- Replace `NotificationMikroOrmRepository` with Mongoose-based repository
- Update `mikro-orm.config.ts` → remove, replace with `MongooseModule.forRoot()`

### 5.2 WebSocket Gateway

**New dependencies:** `@nestjs/websockets`, `@nestjs/platform-socket.io`, `socket.io`

**`NotificationGateway`** (`/ws/notifications`):
- `handleConnection`: validate JWT from `?token=` query param, join room `user:{userId}`, register in Redis `ws:connections:{userId}`
- `handleDisconnect`: remove from Redis, clean up room
- `emitToUser(userId, event, payload)`: emit to room `user:{userId}`
- Heartbeat: socket.io built-in ping/pong (25s interval, 10s timeout)
- Namespace: `/notifications`

**Auth flow:**
```
Client connects → ?token=JWT
Gateway extracts token → validates via Keycloak JWKS
Valid → join room, register in Redis
Invalid → disconnect with error code 4001
```

### 5.3 Expanded Kafka Consumers

| Topic | Handler | Recipient | ThreadId | Type |
|---|---|---|---|---|
| `order.created` | `handleOrderCreated` | buyer | `order:{orderId}` | ORDER_CREATED |
| `order.created` | `handleSellerNewOrder` | seller | `seller-order:{orderId}` | SELLER_NEW_ORDER |
| `order.cancelled` | `handleOrderCancelled` | buyer | `order:{orderId}` | ORDER_CANCELLED |
| `order.shipped` | `handleOrderShipped` | buyer | `order:{orderId}` | ORDER_SHIPPED |
| `order.delivered` | `handleOrderDelivered` | buyer | `order:{orderId}` | ORDER_DELIVERED |
| `payment.completed` | `handlePaymentCompleted` | buyer | `order:{orderId}` | PAYMENT_COMPLETED |
| `payment.refunded` | `handlePaymentRefunded` | buyer | `order:{orderId}` | PAYMENT_REFUNDED |
| `product.approved` | `handleProductApproved` | seller | `product:{productId}` | PRODUCT_APPROVED |
| `product.rejected` | `handleProductRejected` | seller | `product:{productId}` | PRODUCT_REJECTED |
| `review.replied` | `handleReviewReplied` | buyer | `review:{reviewId}` | REVIEW_REPLIED |
| `return.requested` | `handleReturnRequested` | seller | `return:{returnId}` | RETURN_REQUESTED |
| `payout.completed` | `handlePayoutCompleted` | seller | `payout:{payoutId}` | PAYOUT_COMPLETED |

### 5.4 Processing Pipeline

```
KafkaConsumer receives event
  → Build SendNotificationInput (type, recipient, thread, payload)
  → Dedup check: Redis GET dedup:{idempotencyKey}
    → exists: skip (already processed)
    → not exists: SET with 24h TTL, continue
  → Persist to MongoDB (status: QUEUED)
  → Preference check (stub: all enabled for pt47)
  → Template resolution (hardcoded vi templates for pt47)
  → Delivery:
    → Check Redis ws:connections:{recipientId}
    → Online: emit via gateway → update status DELIVERED
    → Offline: push notificationId to Redis offline:{recipientId}
    → On next connect: drain offline list, emit catch-up batch
  → Update delivery status in MongoDB
```

### 5.5 Retry Logic

- Transient failure (socket emit timeout, MongoDB write error): re-queue
- Backoff: 1s → 2s → 4s (3 attempts max)
- After 3 failures: move to `delivery_dead_letters` collection
- Retry implemented via `delivery.nextRetryAt` field + scheduled job (every 5s scans for due retries)

### 5.6 REST API Changes

**Existing (keep):**
- `GET /notifications` — add `?type=` filter param (comma-separated)
- `GET /notifications/unread-count`
- `POST /notifications/:id/read`
- `POST /notifications/mark-all-read`

**New:**
- `GET /notifications/threads?page=0&size=20&type=ORDER` — returns latest notification per thread + unread count per thread
- `GET /notifications?threadId=order:abc` — all notifications in a specific thread

**Thread response shape:**
```json
{
  "content": [
    {
      "threadId": "order:VN2024-abc",
      "threadTitle": "Đơn hàng #VN2024-abc",
      "latestNotification": { /* full notification object */ },
      "unreadCount": 2,
      "totalCount": 5,
      "updatedAt": "2026-05-30T10:00:00Z"
    }
  ],
  "totalElements": 42,
  "totalPages": 3,
  "number": 0,
  "size": 20
}
```

---

## 6. Infrastructure Changes

### 6.1 Docker Compose

```yaml
mongo:
  image: mongo:7
  ports:
    - "27017:27017"
  environment:
    MONGO_INITDB_ROOT_USERNAME: vnshop
    MONGO_INITDB_ROOT_PASSWORD: vnshop123
    MONGO_INITDB_DATABASE: notification_db
  volumes:
    - mongo_data:/data/db
  healthcheck:
    test: ["CMD", "mongosh", "--eval", "db.adminCommand('ping')"]
    interval: 10s
    timeout: 5s
    retries: 5
```

**notification-service env update:**
```yaml
MONGO_URI: mongodb://vnshop:vnshop123@mongo:27017/notification_db?authSource=admin
REDIS_URL: redis://redis:6379
```

### 6.2 API Gateway Route

Add WebSocket upgrade route in `RouteConfig.java`:
```java
.route("notifications-ws", route -> route.path("/ws/notifications")
    .filters(filters -> filters.tokenRelay())
    .uri("ws://notification-service:8087"))
```

### 6.3 K8s Updates

- Add MongoDB StatefulSet (or use managed Atlas in production)
- Update notification-service deployment env vars
- Update NetworkPolicy: allow egress to MongoDB (27017)

---

## 7. Frontend Changes

### 7.1 WebSocket Hook — `use-notification-socket.ts`

**Mount:** Inside `BackgroundEffects` component (alongside `useMessagingSocket`)

**Lifecycle:**
1. User authenticates → hook activates
2. Connect to `/ws/notifications?token={accessToken}`
3. On `notification:new` → update QueryCache + fire toast
4. On disconnect → exponential backoff reconnect (1s → 30s cap)
5. On token refresh → reconnect with new token
6. User logs out → disconnect

**Cache update on new notification:**
```typescript
// Insert at top of list
queryClient.setQueryData(["notifications", "list"], prev => ({
  ...prev,
  content: [newNotification, ...prev.content].slice(0, pageSize)
}));

// Increment unread
queryClient.setQueryData(["notifications", "unread-count"], prev => ({
  count: (prev?.count ?? 0) + 1
}));

// Invalidate thread queries (if on /notifications page)
queryClient.invalidateQueries({ queryKey: ["notifications", "threads"] });
```

**Toast:**
```typescript
toast.custom((t) => <NotificationToast notification={newNotification} toastId={t} />, {
  duration: 5000,
  position: "top-right",
});
```

### 7.2 Enhanced Dropdown (`NotificationBell`)

Changes from current:
- Add "Đánh dấu tất cả đã đọc" button in header (visible when unreadCount > 0)
- Type-specific Tabler icons per notification type
- Date grouping: "Hôm nay", "Hôm qua", "Trước đó"
- Limit to 10 items (down from 30)
- Unread items float to top within each date group
- Bell icon pulse animation on new WebSocket notification
- "Xem tất cả" links to `/notifications` (not `/profile`)

**Icon mapping:**
| Type | Icon |
|---|---|
| ORDER_CREATED, SELLER_NEW_ORDER | `IconShoppingCart` |
| ORDER_SHIPPED | `IconTruck` |
| ORDER_DELIVERED | `IconPackageCheck` |
| ORDER_CANCELLED | `IconX` |
| PAYMENT_COMPLETED | `IconCreditCard` |
| PAYMENT_REFUNDED | `IconReceiptRefund` |
| PRODUCT_APPROVED | `IconCircleCheck` |
| PRODUCT_REJECTED | `IconCircleX` |
| REVIEW_REPLIED | `IconMessage` |
| RETURN_REQUESTED | `IconArrowBack` |
| PAYOUT_COMPLETED | `IconWallet` |

### 7.3 Notifications Page (`/notifications`)

**Route:** `/notifications` (authenticated, top-level)

**Components:**
| Component | Responsibility |
|---|---|
| `NotificationsPage` | Route-level, URL-driven state (?type=&page=) |
| `NotificationFilters` | Tab bar for type filtering |
| `NotificationThreadList` | Renders threads with expand/collapse |
| `NotificationThread` | Single thread row — latest item + unread badge + expand |
| `NotificationItem` | Single notification — icon, title, body, time, unread dot |
| `Pagination` | Page controls (extract/reuse from admin if exists) |

**Filter tabs:**
| Tab | Types |
|---|---|
| Tất cả | all |
| Đơn hàng | ORDER_CREATED, ORDER_CANCELLED, ORDER_DELIVERED, SELLER_NEW_ORDER |
| Thanh toán | PAYMENT_COMPLETED, PAYMENT_REFUNDED, PAYOUT_COMPLETED |
| Vận chuyển | ORDER_SHIPPED |
| Đánh giá | REVIEW_REPLIED |
| Người bán | PRODUCT_APPROVED, PRODUCT_REJECTED, RETURN_REQUESTED |

**Behavior:**
- Page size: 20 threads per page
- URL state: `/notifications?type=ORDER&page=2` (shareable, back-button works)
- Click thread → expand inline (accordion) showing all notifications chronologically
- Click individual notification → mark read + navigate to deepLink
- "Đánh dấu tất cả đã đọc" → marks all (not just current page)
- Empty state per filter: Tabler icon + "Không có thông báo nào"
- Skeleton loading: 5 placeholder rows

### 7.4 Remove Polling

- Delete `refetchInterval: visible ? POLL_INTERVAL_MS : false` from `useNotifications`
- Keep `refetchOnWindowFocus: true` (catches up on tab switch)
- Initial fetch on mount remains (hydrates cache on page load)

### 7.5 Zod Schema Updates

```typescript
// Expanded notification schema
export const notificationSchema = z.object({
  _id: z.string(),
  type: z.enum([
    "ORDER_CREATED", "ORDER_CANCELLED", "ORDER_SHIPPED", "ORDER_DELIVERED",
    "PAYMENT_COMPLETED", "PAYMENT_REFUNDED", "SELLER_NEW_ORDER",
    "PRODUCT_APPROVED", "PRODUCT_REJECTED", "REVIEW_REPLIED",
    "RETURN_REQUESTED", "PAYOUT_COMPLETED"
  ]),
  title: z.string(),
  body: z.string(),
  read: z.boolean(),
  readAt: z.string().nullable(),
  threadId: z.string().nullable(),
  threadTitle: z.string().nullable(),
  deepLink: z.string().nullable(),
  priority: z.enum(["HIGH", "MEDIUM", "LOW"]),
  createdAt: z.string(),
});

// Thread response
export const notificationThreadSchema = z.object({
  threadId: z.string(),
  threadTitle: z.string(),
  latestNotification: notificationSchema,
  unreadCount: z.number(),
  totalCount: z.number(),
  updatedAt: z.string(),
});
```

---

## 8. Notification Flow (end-to-end)

```
1. order-service publishes { orderId, buyerId, sellerId, ... } to "order.shipped"

2. INGESTION (notification-service KafkaConsumer):
   → Receives event from "order.shipped" topic
   → Builds idempotencyKey: "order-shipped:{orderId}"
   → Redis GET dedup:{key} → not found → SET with 24h TTL
   → Persists to MongoDB notifications collection:
     { type: ORDER_SHIPPED, recipientId: buyerId, threadId: "order:{orderId}", ... }
   → Status: QUEUED

3. PROCESSING:
   → Preference check: stub returns "all enabled" (pt47)
   → Template: hardcoded vi string interpolation
   → Channel routing: IN_APP (only channel for pt47)
   → Priority: HIGH (order events)

4. DELIVERY (in-app worker):
   → Redis SMEMBERS ws:connections:{buyerId}
   → User online (socket exists):
     → gateway.emitToUser(buyerId, "notification:new", notification)
     → Update MongoDB: delivery.status = DELIVERED, delivery.deliveredAt = now
   → User offline (no socket):
     → Redis RPUSH offline:{buyerId} notificationId
     → Update MongoDB: delivery.status = SENT (persisted, awaiting delivery)

5. CLIENT RECEIVES (if online):
   → socket.on("notification:new") fires
   → Insert into TanStack Query cache at position 0
   → Increment unread count in cache
   → Invalidate thread queries
   → Fire Sonner toast (title + body, 5s auto-dismiss, clickable)
   → Bell icon pulse animation

6. CATCH-UP (on reconnect):
   → Client connects → server drains Redis offline:{userId}
   → Emits batch: "notification:catch-up" with array of missed notifications
   → Client merges into cache, updates unread count

7. USER INTERACTION:
   → Click notification → mark read (optimistic) + navigate deepLink
   → Mark all read → POST /notifications/mark-all-read → cache update
```

---

## 9. Failure Modes

| Failure | Behavior | Recovery |
|---|---|---|
| WebSocket disconnected | User sees stale data | Reconnect (exp backoff 1s→30s), catch-up on reconnect |
| Kafka consumer lag | Notifications delayed | Never lost (Kafka retention 7d), processes on recovery |
| notification-service crash | Gateway circuit breaker trips | Kafka retains events, service restarts, drains backlog |
| MongoDB down | Persist fails | Kafka offset not committed → reprocessed on recovery |
| Redis down | No dedup/connection tracking | Graceful degradation: skip dedup, assume offline |
| Gateway emit timeout | Delivery fails | Retry 3x → DLQ |

---

## 10. Testing Strategy

| Layer | What | How |
|---|---|---|
| BE unit | Mongoose schemas + repository | Jest + mongodb-memory-server |
| BE unit | Kafka consumers (12 handlers) | Jest mocks |
| BE unit | Dedup logic, retry logic | Jest + ioredis-mock |
| BE integration | WebSocket connect + auth + receive | socket.io-client + supertest |
| BE integration | Full pipeline: Kafka → persist → emit | Testcontainers (Kafka + Mongo + Redis) |
| FE unit | `useNotificationSocket` hook | vitest + mock socket |
| FE unit | `NotificationsPage` render + filter + pagination | vitest + testing-library |
| FE unit | `NotificationBell` enhanced dropdown | vitest + testing-library |
| FE unit | Toast on new notification | vitest + Sonner spy |
| FE component | Thread expand/collapse | vitest + testing-library |
| E2E | Bell shows count, click navigates, page filters | Playwright |

---

## 11. Implementation Scope (pt47)

### Build now:
- MongoDB setup (docker-compose + Mongoose schemas + indexes)
- Database migration (PostgreSQL → MongoDB for notification-service)
- WebSocket gateway with JWT auth + room management
- Redis integration (connections, dedup, offline queue)
- Expanded Kafka consumers (12 handlers)
- Processing pipeline (dedup → persist → deliver)
- Retry logic + DLQ
- REST API updates (type filter, thread endpoints)
- API gateway WebSocket route
- FE: `use-notification-socket.ts`
- FE: Enhanced `NotificationBell` (icons, groups, mark-all, pulse)
- FE: `/notifications` page (threads, filters, pagination)
- FE: Toast on new notification
- FE: Remove polling
- FE: Updated Zod schemas

### Stub for later:
- Preference engine (returns "all enabled")
- Email channel adapter (logs only)
- Push channel (skip entirely)
- Template collection (hardcoded strings, no DB lookup)

---

## 12. API Gateway Security

- `/notifications/**` remains authenticated (no change)
- `/ws/notifications` added to WebSocket upgrade routes (token validated by notification-service gateway, not Spring Security)
- No permitAll changes needed

---

## 13. Metrics (Prometheus)

```
notification_received_total{source, type}
notification_processed_total{type, channel, status}
notification_delivery_latency_seconds{channel}
notification_retry_total{channel, attempt}
notification_dlq_depth{channel}
notification_ws_connections_active
notification_offline_queue_depth
```

Exposed at `/actuator/prometheus` (existing pattern from pt46).
