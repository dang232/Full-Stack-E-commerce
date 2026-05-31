# Notification Platform Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an enterprise-grade real-time notification platform with WebSocket delivery, threading, expanded event types, and a rich FE inbox.

**Architecture:** DDD Hexagonal (ports & adapters) with strict dependency rule. Domain layer is pure TypeScript with zero framework imports. Application layer orchestrates use cases. Infrastructure layer implements ports via MongoDB, Redis, socket.io, and Kafka adapters.

**Tech Stack:** NestJS 11, MongoDB 7 (Mongoose), Redis (ioredis), socket.io, Kafka. React 18, TanStack Query 5, socket.io-client, Sonner toasts, Tabler icons.

**Spec:** `docs/superpowers/specs/2026-05-30-notification-platform-design.md`

---

## File Structure

### Backend (services/notification-service/src/notification/)

```
domain/
├── model/
│   ├── notification.ts                    # Aggregate root (replaces existing)
│   ├── delivery-status.ts                 # Value object — state machine
│   ├── notification-thread.ts             # Value object
│   ├── notification-type.enum.ts          # Expanded enum (12 types)
│   └── priority.enum.ts                   # HIGH/MEDIUM/LOW
├── event/
│   └── notification-created.event.ts      # Domain event
├── port/
│   ├── inbound/
│   │   ├── send-notification.port.ts
│   │   ├── query-notifications.port.ts
│   │   └── manage-delivery.port.ts
│   └── outbound/
│       ├── notification.repository.ts     # Replaces existing
│       ├── realtime-channel.port.ts
│       ├── deduplication.port.ts
│       └── connection-registry.port.ts
└── service/
    ├── notification-factory.ts
    └── delivery-policy.ts

application/
├── command/
│   ├── send-notification.use-case.ts      # Replaces existing
│   ├── mark-notification-read.use-case.ts
│   ├── mark-all-read.use-case.ts
│   └── retry-failed-deliveries.use-case.ts
├── query/
│   ├── find-user-notifications.use-case.ts
│   ├── find-notification-threads.use-case.ts
│   ├── count-unread.use-case.ts
│   └── find-thread-notifications.use-case.ts
└── event-handler/
    └── notification-created.handler.ts

infrastructure/
├── persistence/
│   ├── mongo-notification.schema.ts
│   ├── mongo-notification.repository.ts
│   └── notification.mapper.ts
├── messaging/
│   ├── kafka-event.consumer.ts
│   └── event-handler-registry.ts
├── realtime/
│   ├── socketio-notification.gateway.ts
│   └── socketio-realtime-channel.adapter.ts
├── cache/
│   ├── redis-deduplication.adapter.ts
│   └── redis-connection-registry.adapter.ts
├── rest/
│   ├── notification.controller.ts
│   └── dto/
│       ├── notification-response.dto.ts
│       └── thread-response.dto.ts
└── auth/
    ├── jwt-auth.guard.ts
    └── jwt.strategy.ts
```

### Frontend (fe/src/app/)

```
hooks/
├── use-notification-socket.ts             # NEW — WebSocket hook
├── use-notifications.ts                   # MODIFY — remove polling, add thread queries

lib/api/endpoints/
├── notifications.ts                       # MODIFY — add thread + filter endpoints

types/api/
├── notification.ts                        # MODIFY — expanded schema + thread type

components/
├── notification-bell.tsx                  # MODIFY — enhanced dropdown
├── notification-toast.tsx                 # NEW — custom toast component
├── notifications/
│   ├── notifications-page.tsx             # NEW — full page
│   ├── notification-filters.tsx           # NEW — tab bar
│   ├── notification-thread-list.tsx       # NEW — thread list
│   ├── notification-thread.tsx            # NEW — single thread row
│   ├── notification-item.tsx              # NEW — single notification
│   ├── notification-icon.tsx              # NEW — type → icon mapping
│   └── notification-pagination.tsx        # NEW — page controls

pages/
├── NotificationsPage.tsx                  # NEW — route entry

App.tsx                                    # MODIFY — add useNotificationSocket
routes.ts                                  # MODIFY — add /notifications route
```

---

## Task Breakdown

### Task 1: Infrastructure Setup — MongoDB + Redis in Docker Compose

**Files:**
- Modify: `docker-compose.yml` (project root)
- Modify: `services/notification-service/.env.example`
- Modify: `services/notification-service/src/config/configuration.ts`

**Steps:**

- [ ] 1. Add `mongo` and `redis` services to `docker-compose.yml`

```yaml
  mongo:
    image: mongo:7.0
    container_name: vnshop-mongo
    restart: unless-stopped
    ports:
      - "27017:27017"
    environment:
      MONGO_INITDB_ROOT_USERNAME: root
      MONGO_INITDB_ROOT_PASSWORD: rootpassword
      MONGO_INITDB_DATABASE: notifications
    volumes:
      - mongo_data:/data/db
    networks:
      - vnshop-network

  redis:
    image: redis:7.2-alpine
    container_name: vnshop-redis
    restart: unless-stopped
    ports:
      - "6379:6379"
    command: redis-server --appendonly yes
    volumes:
      - redis_data:/data
    networks:
      - vnshop-network

# Under volumes: add:
  mongo_data:
  redis_data:
```

- [ ] 2. Update `services/notification-service/.env.example`

```env
MONGODB_URI=mongodb://root:rootpassword@localhost:27017/notifications?authSource=admin
MONGODB_DB_NAME=notifications
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_TTL_SECONDS=86400
```

- [ ] 3. Update `services/notification-service/src/config/configuration.ts`

```typescript
export default () => ({
  port: parseInt(process.env.PORT ?? '3004', 10),
  mongodb: {
    uri: process.env.MONGODB_URI ?? 'mongodb://root:rootpassword@localhost:27017/notifications?authSource=admin',
    dbName: process.env.MONGODB_DB_NAME ?? 'notifications',
  },
  redis: {
    host: process.env.REDIS_HOST ?? 'localhost',
    port: parseInt(process.env.REDIS_PORT ?? '6379', 10),
    ttlSeconds: parseInt(process.env.REDIS_TTL_SECONDS ?? '86400', 10),
  },
  kafka: {
    brokers: (process.env.KAFKA_BROKERS ?? 'localhost:9092').split(','),
    groupId: process.env.KAFKA_GROUP_ID ?? 'notification-service',
  },
  jwt: { secret: process.env.JWT_SECRET ?? 'secret' },
});
```

- [ ] 4. Verify

```bash
docker compose up mongo redis -d
docker exec vnshop-mongo mongosh --eval "db.adminCommand('ping')" -u root -p rootpassword --authenticationDatabase admin
# Expected: { ok: 1 }
docker exec vnshop-redis redis-cli ping
# Expected: PONG
```

**Commit:** `chore(notification-service): add MongoDB and Redis to docker-compose`

---

### Task 2: Domain Layer — Models, Enums, Value Objects

**Files:**
- Create: `services/notification-service/src/notification/domain/model/notification-type.enum.ts`
- Create: `services/notification-service/src/notification/domain/model/priority.enum.ts`
- Create: `services/notification-service/src/notification/domain/model/delivery-status.ts`
- Create: `services/notification-service/src/notification/domain/model/notification-thread.ts`
- Create: `services/notification-service/src/notification/domain/model/notification.ts`
- Create: `services/notification-service/src/notification/domain/event/notification-created.event.ts`
- Test: `services/notification-service/src/notification/domain/model/__tests__/notification.spec.ts`

**Steps:**

- [ ] 1. Create `notification-type.enum.ts`

```typescript
export enum NotificationType {
  // Order lifecycle
  ORDER_PLACED = 'ORDER_PLACED',
  ORDER_CONFIRMED = 'ORDER_CONFIRMED',
  ORDER_SHIPPED = 'ORDER_SHIPPED',
  ORDER_DELIVERED = 'ORDER_DELIVERED',
  ORDER_CANCELLED = 'ORDER_CANCELLED',
  // Payment
  PAYMENT_SUCCESS = 'PAYMENT_SUCCESS',
  PAYMENT_FAILED = 'PAYMENT_FAILED',
  // Promotions
  PROMOTION_FLASH_SALE = 'PROMOTION_FLASH_SALE',
  PROMOTION_COUPON = 'PROMOTION_COUPON',
  // Account
  ACCOUNT_SECURITY = 'ACCOUNT_SECURITY',
  // Reviews
  REVIEW_REPLY = 'REVIEW_REPLY',
  // System
  SYSTEM_ANNOUNCEMENT = 'SYSTEM_ANNOUNCEMENT',
}
```

- [ ] 2. Create `priority.enum.ts`

```typescript
export enum Priority {
  HIGH = 'HIGH',
  MEDIUM = 'MEDIUM',
  LOW = 'LOW',
}
```

- [ ] 3. Create `delivery-status.ts` (state machine value object)

```typescript
export enum DeliveryStatusValue {
  PENDING = 'PENDING',
  DELIVERED = 'DELIVERED',
  FAILED = 'FAILED',
  READ = 'READ',
}

const VALID_TRANSITIONS: Record<DeliveryStatusValue, DeliveryStatusValue[]> = {
  [DeliveryStatusValue.PENDING]: [DeliveryStatusValue.DELIVERED, DeliveryStatusValue.FAILED],
  [DeliveryStatusValue.DELIVERED]: [DeliveryStatusValue.READ],
  [DeliveryStatusValue.FAILED]: [DeliveryStatusValue.PENDING],
  [DeliveryStatusValue.READ]: [],
};

export class DeliveryStatus {
  private constructor(private readonly value: DeliveryStatusValue) {}

  static pending(): DeliveryStatus {
    return new DeliveryStatus(DeliveryStatusValue.PENDING);
  }

  static fromValue(value: DeliveryStatusValue): DeliveryStatus {
    return new DeliveryStatus(value);
  }

  transitionTo(next: DeliveryStatusValue): DeliveryStatus {
    if (!VALID_TRANSITIONS[this.value].includes(next)) {
      throw new Error(`Invalid transition: ${this.value} -> ${next}`);
    }
    return new DeliveryStatus(next);
  }

  getValue(): DeliveryStatusValue { return this.value; }
  isPending(): boolean { return this.value === DeliveryStatusValue.PENDING; }
  isRead(): boolean { return this.value === DeliveryStatusValue.READ; }
}
```

- [ ] 4. Create `notification-thread.ts`

```typescript
export class NotificationThread {
  constructor(
    readonly threadId: string,
    readonly threadTitle: string,
    readonly rootNotificationId: string,
  ) {}

  static create(threadId: string, threadTitle: string, rootNotificationId: string): NotificationThread {
    if (!threadId || !threadTitle) throw new Error('threadId and threadTitle are required');
    return new NotificationThread(threadId, threadTitle, rootNotificationId);
  }
}
```

- [ ] 5. Create `notification.ts` aggregate root

```typescript
import { randomUUID } from 'crypto';
import { NotificationType } from './notification-type.enum';
import { Priority } from './priority.enum';
import { DeliveryStatus, DeliveryStatusValue } from './delivery-status';
import { NotificationThread } from './notification-thread';
import { NotificationCreatedEvent } from '../event/notification-created.event';

export interface CreateNotificationProps {
  userId: string;
  type: NotificationType;
  title: string;
  body: string;
  deepLink?: string;
  priority?: Priority;
  thread?: NotificationThread;
  metadata?: Record<string, unknown>;
  idempotencyKey?: string;
}

export class Notification {
  readonly id: string;
  readonly userId: string;
  readonly type: NotificationType;
  readonly title: string;
  readonly body: string;
  readonly deepLink?: string;
  readonly priority: Priority;
  readonly thread?: NotificationThread;
  readonly metadata: Record<string, unknown>;
  readonly idempotencyKey?: string;
  readonly createdAt: Date;
  private _deliveryStatus: DeliveryStatus;
  private _domainEvents: NotificationCreatedEvent[] = [];

  private constructor(props: {
    id: string;
    userId: string;
    type: NotificationType;
    title: string;
    body: string;
    deepLink?: string;
    priority: Priority;
    thread?: NotificationThread;
    metadata: Record<string, unknown>;
    idempotencyKey?: string;
    createdAt: Date;
    deliveryStatus: DeliveryStatus;
  }) {
    Object.assign(this, props);
    this._deliveryStatus = props.deliveryStatus;
  }

  static create(props: CreateNotificationProps): Notification {
    const notification = new Notification({
      id: randomUUID(),
      userId: props.userId,
      type: props.type,
      title: props.title,
      body: props.body,
      deepLink: props.deepLink,
      priority: props.priority ?? Priority.MEDIUM,
      thread: props.thread,
      metadata: props.metadata ?? {},
      idempotencyKey: props.idempotencyKey,
      createdAt: new Date(),
      deliveryStatus: DeliveryStatus.pending(),
    });
    notification._domainEvents.push(new NotificationCreatedEvent(notification.id, notification.userId, notification.type));
    return notification;
  }

  static reconstitute(props: ConstructorParameters<typeof Notification>[0]): Notification {
    return new Notification(props);
  }

  get deliveryStatus(): DeliveryStatus { return this._deliveryStatus; }

  markDelivered(): void {
    this._deliveryStatus = this._deliveryStatus.transitionTo(DeliveryStatusValue.DELIVERED);
  }

  markFailed(): void {
    this._deliveryStatus = this._deliveryStatus.transitionTo(DeliveryStatusValue.FAILED);
  }

  markRead(): void {
    if (!this._deliveryStatus.isRead()) {
      this._deliveryStatus = this._deliveryStatus.transitionTo(DeliveryStatusValue.READ);
    }
  }

  pullDomainEvents(): NotificationCreatedEvent[] {
    const events = [...this._domainEvents];
    this._domainEvents = [];
    return events;
  }
}
```

- [ ] 6. Create `notification-created.event.ts`

```typescript
import { NotificationType } from '../model/notification-type.enum';

export class NotificationCreatedEvent {
  readonly occurredAt: Date;
  constructor(
    readonly notificationId: string,
    readonly userId: string,
    readonly type: NotificationType,
  ) {
    this.occurredAt = new Date();
  }
}
```

- [ ] 7. Create `__tests__/notification.spec.ts`

```typescript
import { Notification } from '../notification';
import { NotificationType } from '../notification-type.enum';
import { Priority } from '../priority.enum';
import { DeliveryStatusValue } from '../delivery-status';

describe('Notification aggregate', () => {
  const baseProps = {
    userId: 'user-1',
    type: NotificationType.ORDER_PLACED,
    title: 'Order placed',
    body: 'Your order #123 has been placed.',
  };

  it('creates with PENDING delivery status', () => {
    const n = Notification.create(baseProps);
    expect(n.deliveryStatus.getValue()).toBe(DeliveryStatusValue.PENDING);
    expect(n.priority).toBe(Priority.MEDIUM);
  });

  it('emits NotificationCreatedEvent on create', () => {
    const n = Notification.create(baseProps);
    const events = n.pullDomainEvents();
    expect(events).toHaveLength(1);
    expect(events[0].notificationId).toBe(n.id);
  });

  it('transitions PENDING -> DELIVERED -> READ', () => {
    const n = Notification.create(baseProps);
    n.markDelivered();
    expect(n.deliveryStatus.getValue()).toBe(DeliveryStatusValue.DELIVERED);
    n.markRead();
    expect(n.deliveryStatus.getValue()).toBe(DeliveryStatusValue.READ);
  });

  it('throws on invalid transition', () => {
    const n = Notification.create(baseProps);
    expect(() => n.markRead()).toThrow('Invalid transition');
  });
});
```

- [ ] 8. Run tests

```bash
cd services/notification-service
npx jest domain/model --no-coverage
# Expected: 4 tests pass
```

**Commit:** `feat(notification-service): expand domain model with 12 types, priority, delivery state machine`

---

### Task 3: Domain Layer — Ports (Interfaces)

**Files:**
- Create: `services/notification-service/src/notification/domain/port/outbound/notification.repository.ts`
- Create: `services/notification-service/src/notification/domain/port/outbound/realtime-channel.port.ts`
- Create: `services/notification-service/src/notification/domain/port/outbound/deduplication.port.ts`
- Create: `services/notification-service/src/notification/domain/port/outbound/connection-registry.port.ts`
- Create: `services/notification-service/src/notification/domain/service/notification-factory.ts`
- Create: `services/notification-service/src/notification/domain/service/delivery-policy.ts`
- Test: `services/notification-service/src/notification/domain/service/__tests__/notification-factory.spec.ts`

**Steps:**

- [ ] 1. Create `notification.repository.ts`

```typescript
import { Notification } from '../model/notification';
import { NotificationType } from '../model/notification-type.enum';

export interface FindUserNotificationsOptions {
  userId: string;
  type?: NotificationType;
  threadId?: string;
  page?: number;
  limit?: number;
}

export interface NotificationRepository {
  save(notification: Notification): Promise<void>;
  findById(id: string): Promise<Notification | null>;
  findByIdempotencyKey(key: string): Promise<Notification | null>;
  findByUser(options: FindUserNotificationsOptions): Promise<{ items: Notification[]; total: number }>;
  findThreadsByUser(userId: string, page: number, limit: number): Promise<{ threadId: string; threadTitle: string; unreadCount: number; latestAt: Date }[]>;
  findByThread(threadId: string, userId: string): Promise<Notification[]>;
  countUnread(userId: string): Promise<number>;
  markAllReadForUser(userId: string): Promise<void>;
}

export const NOTIFICATION_REPOSITORY = Symbol('NOTIFICATION_REPOSITORY');
```

- [ ] 2. Create `realtime-channel.port.ts`

```typescript
import { Notification } from '../model/notification';

export interface RealtimeChannelPort {
  sendToUser(userId: string, notification: Notification): Promise<void>;
  sendBatchToUser(userId: string, notifications: Notification[]): Promise<void>;
  isUserConnected(userId: string): Promise<boolean>;
}

export const REALTIME_CHANNEL_PORT = Symbol('REALTIME_CHANNEL_PORT');
```

- [ ] 3. Create `deduplication.port.ts`

```typescript
export interface DeduplicationPort {
  isDuplicate(idempotencyKey: string): Promise<boolean>;
  markProcessed(idempotencyKey: string, ttlSeconds?: number): Promise<void>;
}

export const DEDUPLICATION_PORT = Symbol('DEDUPLICATION_PORT');
```

- [ ] 4. Create `connection-registry.port.ts`

```typescript
export interface ConnectionRegistryPort {
  register(userId: string, socketId: string): Promise<void>;
  unregister(userId: string, socketId: string): Promise<void>;
  getSocketIds(userId: string): Promise<string[]>;
  enqueueOffline(userId: string, notificationId: string): Promise<void>;
  drainOfflineQueue(userId: string): Promise<string[]>;
}

export const CONNECTION_REGISTRY_PORT = Symbol('CONNECTION_REGISTRY_PORT');
```

- [ ] 5. Create `notification-factory.ts`

```typescript
import { Notification, CreateNotificationProps } from '../model/notification';
import { NotificationType } from '../model/notification-type.enum';
import { Priority } from '../model/priority.enum';

export interface KafkaEventPayload {
  userId: string;
  type: NotificationType;
  metadata: Record<string, unknown>;
  idempotencyKey?: string;
}

const TYPE_CONFIG: Record<NotificationType, { titleFn: (m: Record<string, unknown>) => string; bodyFn: (m: Record<string, unknown>) => string; priority: Priority; deepLinkFn?: (m: Record<string, unknown>) => string }> = {
  [NotificationType.ORDER_PLACED]: {
    titleFn: () => 'Đặt hàng thành công',
    bodyFn: (m) => `Đơn hàng #${m['orderId']} đã được đặt.`,
    priority: Priority.HIGH,
    deepLinkFn: (m) => `/orders/${m['orderId']}`,
  },
  [NotificationType.ORDER_CONFIRMED]: {
    titleFn: () => 'Đơn hàng đã xác nhận',
    bodyFn: (m) => `Đơn hàng #${m['orderId']} đã được xác nhận.`,
    priority: Priority.HIGH,
    deepLinkFn: (m) => `/orders/${m['orderId']}`,
  },
  [NotificationType.ORDER_SHIPPED]: {
    titleFn: () => 'Đơn hàng đang giao',
    bodyFn: (m) => `Đơn hàng #${m['orderId']} đang trên đường giao.`,
    priority: Priority.HIGH,
    deepLinkFn: (m) => `/orders/${m['orderId']}`,
  },
  [NotificationType.ORDER_DELIVERED]: {
    titleFn: () => 'Đơn hàng đã giao',
    bodyFn: (m) => `Đơn hàng #${m['orderId']} đã được giao thành công.`,
    priority: Priority.HIGH,
    deepLinkFn: (m) => `/orders/${m['orderId']}`,
  },
  [NotificationType.ORDER_CANCELLED]: {
    titleFn: () => 'Đơn hàng đã hủy',
    bodyFn: (m) => `Đơn hàng #${m['orderId']} đã bị hủy.`,
    priority: Priority.HIGH,
    deepLinkFn: (m) => `/orders/${m['orderId']}`,
  },
  [NotificationType.PAYMENT_SUCCESS]: {
    titleFn: () => 'Thanh toán thành công',
    bodyFn: (m) => `Thanh toán ${m['amount']} VND thành công.`,
    priority: Priority.HIGH,
    deepLinkFn: (m) => `/orders/${m['orderId']}`,
  },
  [NotificationType.PAYMENT_FAILED]: {
    titleFn: () => 'Thanh toán thất bại',
    bodyFn: (m) => `Thanh toán cho đơn hàng #${m['orderId']} thất bại.`,
    priority: Priority.HIGH,
    deepLinkFn: (m) => `/orders/${m['orderId']}`,
  },
  [NotificationType.PROMOTION_FLASH_SALE]: {
    titleFn: () => 'Flash Sale đang diễn ra!',
    bodyFn: (m) => `${m['promotionName']} — Giảm đến ${m['discountPercent']}%`,
    priority: Priority.MEDIUM,
    deepLinkFn: (m) => `/promotions/${m['promotionId']}`,
  },
  [NotificationType.PROMOTION_COUPON]: {
    titleFn: () => 'Bạn có mã giảm giá mới',
    bodyFn: (m) => `Mã: ${m['couponCode']} — Giảm ${m['discountPercent']}%`,
    priority: Priority.LOW,
    deepLinkFn: () => '/promotions',
  },
  [NotificationType.ACCOUNT_SECURITY]: {
    titleFn: () => 'Cảnh báo bảo mật',
    bodyFn: (m) => `${m['message']}`,
    priority: Priority.HIGH,
    deepLinkFn: () => '/account/security',
  },
  [NotificationType.REVIEW_REPLY]: {
    titleFn: () => 'Phản hồi đánh giá',
    bodyFn: (m) => `Đánh giá của bạn về "${m['productName']}" đã có phản hồi.`,
    priority: Priority.LOW,
    deepLinkFn: (m) => `/products/${m['productId']}#reviews`,
  },
  [NotificationType.SYSTEM_ANNOUNCEMENT]: {
    titleFn: () => 'Thông báo hệ thống',
    bodyFn: (m) => `${m['message']}`,
    priority: Priority.LOW,
  },
};

export class NotificationFactory {
  static fromKafkaEvent(payload: KafkaEventPayload): Notification {
    const config = TYPE_CONFIG[payload.type];
    if (!config) throw new Error(`Unknown notification type: ${payload.type}`);

    const props: CreateNotificationProps = {
      userId: payload.userId,
      type: payload.type,
      title: config.titleFn(payload.metadata),
      body: config.bodyFn(payload.metadata),
      deepLink: config.deepLinkFn?.(payload.metadata),
      priority: config.priority,
      metadata: payload.metadata,
      idempotencyKey: payload.idempotencyKey,
    };
    return Notification.create(props);
  }
}
```

- [ ] 6. Create `delivery-policy.ts`

```typescript
import { NotificationType } from '../model/notification-type.enum';
import { Priority } from '../model/priority.enum';

export interface DeliveryPolicy {
  shouldDeliverRealtime(type: NotificationType): boolean;
  getRetryDelayMs(attemptNumber: number): number;
  getMaxRetries(priority: Priority): number;
}

export class DefaultDeliveryPolicy implements DeliveryPolicy {
  private static readonly REALTIME_TYPES = new Set<NotificationType>([
    NotificationType.ORDER_PLACED,
    NotificationType.ORDER_CONFIRMED,
    NotificationType.ORDER_SHIPPED,
    NotificationType.ORDER_DELIVERED,
    NotificationType.ORDER_CANCELLED,
    NotificationType.PAYMENT_SUCCESS,
    NotificationType.PAYMENT_FAILED,
    NotificationType.ACCOUNT_SECURITY,
  ]);

  shouldDeliverRealtime(type: NotificationType): boolean {
    return DefaultDeliveryPolicy.REALTIME_TYPES.has(type);
  }

  getRetryDelayMs(attemptNumber: number): number {
    return Math.min(1000 * Math.pow(2, attemptNumber), 30000);
  }

  getMaxRetries(priority: Priority): number {
    return priority === Priority.HIGH ? 5 : priority === Priority.MEDIUM ? 3 : 1;
  }
}
```

- [ ] 7. Create `__tests__/notification-factory.spec.ts`

```typescript
import { NotificationFactory } from '../notification-factory';
import { NotificationType } from '../../model/notification-type.enum';
import { Priority } from '../../model/priority.enum';
import { DeliveryStatusValue } from '../../model/delivery-status';

describe('NotificationFactory', () => {
  it('creates ORDER_PLACED notification with correct fields', () => {
    const n = NotificationFactory.fromKafkaEvent({
      userId: 'u1',
      type: NotificationType.ORDER_PLACED,
      metadata: { orderId: 'ORD-001' },
      idempotencyKey: 'key-1',
    });
    expect(n.title).toBe('Đặt hàng thành công');
    expect(n.deepLink).toBe('/orders/ORD-001');
    expect(n.priority).toBe(Priority.HIGH);
    expect(n.deliveryStatus.getValue()).toBe(DeliveryStatusValue.PENDING);
  });

  it('throws for unknown type', () => {
    expect(() =>
      NotificationFactory.fromKafkaEvent({
        userId: 'u1',
        type: 'UNKNOWN' as NotificationType,
        metadata: {},
      }),
    ).toThrow('Unknown notification type');
  });
});
```

- [ ] 8. Run tests

```bash
cd services/notification-service
npx jest domain/service --no-coverage
# Expected: 2 tests pass
```

**Commit:** `feat(notification-service): add domain ports, factory, and delivery policy`

---

### Task 4: Infrastructure — MongoDB Persistence (Mongoose)

**Files:**
- Modify: `services/notification-service/package.json` (remove MikroORM, add mongoose)
- Create: `services/notification-service/src/notification/infrastructure/persistence/mongo-notification.schema.ts`
- Create: `services/notification-service/src/notification/infrastructure/persistence/notification.mapper.ts`
- Create: `services/notification-service/src/notification/infrastructure/persistence/mongo-notification.repository.ts`
- Modify: `services/notification-service/src/app.module.ts`
- Test: `services/notification-service/src/notification/infrastructure/persistence/__tests__/mongo-notification.repository.spec.ts`

**Steps:**

- [ ] 1. Update `package.json` — remove MikroORM, add Mongoose

```bash
cd services/notification-service
npm uninstall @mikro-orm/core @mikro-orm/nestjs @mikro-orm/postgresql @mikro-orm/migrations
npm install mongoose @nestjs/mongoose
npm install --save-dev mongodb-memory-server
```

- [ ] 2. Create `mongo-notification.schema.ts`

```typescript
import { Prop, Schema, SchemaFactory } from '@nestjs/mongoose';
import { HydratedDocument } from 'mongoose';
import { NotificationType } from '../../domain/model/notification-type.enum';
import { Priority } from '../../domain/model/priority.enum';
import { DeliveryStatusValue } from '../../domain/model/delivery-status';

export type NotificationDocument = HydratedDocument<NotificationSchemaClass>;

@Schema({ collection: 'notifications', timestamps: false })
export class NotificationSchemaClass {
  @Prop({ required: true, index: true }) id: string;
  @Prop({ required: true, index: true }) userId: string;
  @Prop({ required: true, enum: NotificationType }) type: NotificationType;
  @Prop({ required: true }) title: string;
  @Prop({ required: true }) body: string;
  @Prop() deepLink?: string;
  @Prop({ required: true, enum: Priority, default: Priority.MEDIUM }) priority: Priority;
  @Prop({ required: true, enum: DeliveryStatusValue, default: DeliveryStatusValue.PENDING }) deliveryStatus: DeliveryStatusValue;
  @Prop() threadId?: string;
  @Prop() threadTitle?: string;
  @Prop() threadRootId?: string;
  @Prop({ type: Object, default: {} }) metadata: Record<string, unknown>;
  @Prop({ sparse: true, index: true }) idempotencyKey?: string;
  @Prop({ required: true }) createdAt: Date;
}

export const NotificationSchema = SchemaFactory.createForClass(NotificationSchemaClass);
NotificationSchema.index({ userId: 1, createdAt: -1 });
NotificationSchema.index({ userId: 1, threadId: 1 });
NotificationSchema.index({ idempotencyKey: 1 }, { unique: true, sparse: true });
```

- [ ] 3. Create `notification.mapper.ts`

```typescript
import { Notification } from '../../domain/model/notification';
import { NotificationThread } from '../../domain/model/notification-thread';
import { DeliveryStatus, DeliveryStatusValue } from '../../domain/model/delivery-status';
import { NotificationSchemaClass } from './mongo-notification.schema';

export class NotificationMapper {
  static toDomain(doc: NotificationSchemaClass): Notification {
    const thread = doc.threadId
      ? NotificationThread.create(doc.threadId, doc.threadTitle!, doc.threadRootId!)
      : undefined;

    return Notification.reconstitute({
      id: doc.id,
      userId: doc.userId,
      type: doc.type,
      title: doc.title,
      body: doc.body,
      deepLink: doc.deepLink,
      priority: doc.priority,
      thread,
      metadata: doc.metadata ?? {},
      idempotencyKey: doc.idempotencyKey,
      createdAt: doc.createdAt,
      deliveryStatus: DeliveryStatus.fromValue(doc.deliveryStatus as DeliveryStatusValue),
    });
  }

  static toPersistence(notification: Notification): Partial<NotificationSchemaClass> {
    return {
      id: notification.id,
      userId: notification.userId,
      type: notification.type,
      title: notification.title,
      body: notification.body,
      deepLink: notification.deepLink,
      priority: notification.priority,
      deliveryStatus: notification.deliveryStatus.getValue(),
      threadId: notification.thread?.threadId,
      threadTitle: notification.thread?.threadTitle,
      threadRootId: notification.thread?.rootNotificationId,
      metadata: notification.metadata,
      idempotencyKey: notification.idempotencyKey,
      createdAt: notification.createdAt,
    };
  }
}
```

- [ ] 4. Create `mongo-notification.repository.ts`

```typescript
import { Injectable } from '@nestjs/common';
import { InjectModel } from '@nestjs/mongoose';
import { Model } from 'mongoose';
import { NotificationRepository, FindUserNotificationsOptions } from '../../domain/port/outbound/notification.repository';
import { Notification } from '../../domain/model/notification';
import { NotificationSchemaClass } from './mongo-notification.schema';
import { NotificationMapper } from './notification.mapper';
import { DeliveryStatusValue } from '../../domain/model/delivery-status';

@Injectable()
export class MongoNotificationRepository implements NotificationRepository {
  constructor(
    @InjectModel(NotificationSchemaClass.name)
    private readonly model: Model<NotificationSchemaClass>,
  ) {}

  async save(notification: Notification): Promise<void> {
    const doc = NotificationMapper.toPersistence(notification);
    await this.model.findOneAndUpdate({ id: notification.id }, doc, { upsert: true, new: true });
  }

  async findById(id: string): Promise<Notification | null> {
    const doc = await this.model.findOne({ id }).lean();
    return doc ? NotificationMapper.toDomain(doc as NotificationSchemaClass) : null;
  }

  async findByIdempotencyKey(key: string): Promise<Notification | null> {
    const doc = await this.model.findOne({ idempotencyKey: key }).lean();
    return doc ? NotificationMapper.toDomain(doc as NotificationSchemaClass) : null;
  }

  async findByUser(options: FindUserNotificationsOptions): Promise<{ items: Notification[]; total: number }> {
    const { userId, type, threadId, page = 1, limit = 20 } = options;
    const filter: Record<string, unknown> = { userId };
    if (type) filter['type'] = type;
    if (threadId) filter['threadId'] = threadId;

    const [docs, total] = await Promise.all([
      this.model.find(filter).sort({ createdAt: -1 }).skip((page - 1) * limit).limit(limit).lean(),
      this.model.countDocuments(filter),
    ]);
    return { items: docs.map((d) => NotificationMapper.toDomain(d as NotificationSchemaClass)), total };
  }

  async findThreadsByUser(userId: string, page: number, limit: number) {
    const skip = (page - 1) * limit;
    return this.model.aggregate([
      { $match: { userId, threadId: { $exists: true, $ne: null } } },
      { $sort: { createdAt: -1 } },
      { $group: { _id: '$threadId', threadTitle: { $first: '$threadTitle' }, unreadCount: { $sum: { $cond: [{ $ne: ['$deliveryStatus', DeliveryStatusValue.READ] }, 1, 0] } }, latestAt: { $max: '$createdAt' } } },
      { $sort: { latestAt: -1 } },
      { $skip: skip },
      { $limit: limit },
      { $project: { threadId: '$_id', threadTitle: 1, unreadCount: 1, latestAt: 1, _id: 0 } },
    ]);
  }

  async findByThread(threadId: string, userId: string): Promise<Notification[]> {
    const docs = await this.model.find({ threadId, userId }).sort({ createdAt: 1 }).lean();
    return docs.map((d) => NotificationMapper.toDomain(d as NotificationSchemaClass));
  }

  async countUnread(userId: string): Promise<number> {
    return this.model.countDocuments({ userId, deliveryStatus: { $ne: DeliveryStatusValue.READ } });
  }

  async markAllReadForUser(userId: string): Promise<void> {
    await this.model.updateMany(
      { userId, deliveryStatus: { $ne: DeliveryStatusValue.READ } },
      { $set: { deliveryStatus: DeliveryStatusValue.READ } },
    );
  }
}
```

- [ ] 5. Update `app.module.ts` — replace MikroORM with MongooseModule

```typescript
import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { MongooseModule } from '@nestjs/mongoose';
import configuration from './config/configuration';
import { NotificationModule } from './notification/notification.module';

@Module({
  imports: [
    ConfigModule.forRoot({ isGlobal: true, load: [configuration] }),
    MongooseModule.forRootAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        uri: config.get<string>('mongodb.uri'),
        dbName: config.get<string>('mongodb.dbName'),
      }),
    }),
    NotificationModule,
  ],
})
export class AppModule {}
```

- [ ] 6. Create `__tests__/mongo-notification.repository.spec.ts`

```typescript
import { MongooseModule } from '@nestjs/mongoose';
import { Test } from '@nestjs/testing';
import { MongoMemoryServer } from 'mongodb-memory-server';
import { MongoNotificationRepository } from '../mongo-notification.repository';
import { NotificationSchemaClass, NotificationSchema } from '../mongo-notification.schema';
import { Notification } from '../../../domain/model/notification';
import { NotificationType } from '../../../domain/model/notification-type.enum';

describe('MongoNotificationRepository', () => {
  let mongod: MongoMemoryServer;
  let repo: MongoNotificationRepository;

  beforeAll(async () => {
    mongod = await MongoMemoryServer.create();
    const module = await Test.createTestingModule({
      imports: [
        MongooseModule.forRoot(mongod.getUri()),
        MongooseModule.forFeature([{ name: NotificationSchemaClass.name, schema: NotificationSchema }]),
      ],
      providers: [MongoNotificationRepository],
    }).compile();
    repo = module.get(MongoNotificationRepository);
  });

  afterAll(async () => { await mongod.stop(); });

  it('saves and retrieves a notification', async () => {
    const n = Notification.create({ userId: 'u1', type: NotificationType.ORDER_PLACED, title: 'T', body: 'B' });
    await repo.save(n);
    const found = await repo.findById(n.id);
    expect(found?.id).toBe(n.id);
    expect(found?.userId).toBe('u1');
  });

  it('counts unread', async () => {
    const count = await repo.countUnread('u1');
    expect(count).toBeGreaterThan(0);
  });
});
```

- [ ] 7. Run tests

```bash
cd services/notification-service
npx jest infrastructure/persistence --no-coverage
# Expected: 2 tests pass
```

**Commit:** `feat(notification-service): replace MikroORM with Mongoose, implement MongoNotificationRepository`

---

### Task 5: Infrastructure — Redis Adapters

**Files:**
- Modify: `services/notification-service/package.json` (add ioredis)
- Create: `services/notification-service/src/notification/infrastructure/cache/redis.module.ts`
- Create: `services/notification-service/src/notification/infrastructure/cache/redis-deduplication.adapter.ts`
- Create: `services/notification-service/src/notification/infrastructure/cache/redis-connection-registry.adapter.ts`
- Test: `services/notification-service/src/notification/infrastructure/cache/__tests__/redis-deduplication.adapter.spec.ts`

**Steps:**

- [ ] 1. Install dependencies

```bash
cd services/notification-service
npm install ioredis
npm install --save-dev ioredis-mock
```

- [ ] 2. Create `redis.module.ts`

```typescript
import { Module, Global } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import Redis from 'ioredis';

export const REDIS_CLIENT = 'REDIS_CLIENT';

@Global()
@Module({
  providers: [
    {
      provide: REDIS_CLIENT,
      inject: [ConfigService],
      useFactory: (config: ConfigService) =>
        new Redis({
          host: config.get<string>('redis.host'),
          port: config.get<number>('redis.port'),
          lazyConnect: true,
        }),
    },
  ],
  exports: [REDIS_CLIENT],
})
export class RedisModule {}
```

- [ ] 3. Create `redis-deduplication.adapter.ts`

```typescript
import { Inject, Injectable } from '@nestjs/common';
import Redis from 'ioredis';
import { DeduplicationPort } from '../../domain/port/outbound/deduplication.port';
import { REDIS_CLIENT } from './redis.module';

@Injectable()
export class RedisDeduplicationAdapter implements DeduplicationPort {
  constructor(@Inject(REDIS_CLIENT) private readonly redis: Redis) {}

  async isDuplicate(idempotencyKey: string): Promise<boolean> {
    const result = await this.redis.exists(`dedup:${idempotencyKey}`);
    return result === 1;
  }

  async markProcessed(idempotencyKey: string, ttlSeconds = 86400): Promise<void> {
    await this.redis.set(`dedup:${idempotencyKey}`, '1', 'EX', ttlSeconds);
  }
}
```

- [ ] 4. Create `redis-connection-registry.adapter.ts`

```typescript
import { Inject, Injectable } from '@nestjs/common';
import Redis from 'ioredis';
import { ConnectionRegistryPort } from '../../domain/port/outbound/connection-registry.port';
import { REDIS_CLIENT } from './redis.module';

@Injectable()
export class RedisConnectionRegistryAdapter implements ConnectionRegistryPort {
  constructor(@Inject(REDIS_CLIENT) private readonly redis: Redis) {}

  async register(userId: string, socketId: string): Promise<void> {
    await this.redis.sadd(`sockets:${userId}`, socketId);
    await this.redis.expire(`sockets:${userId}`, 86400);
  }

  async unregister(userId: string, socketId: string): Promise<void> {
    await this.redis.srem(`sockets:${userId}`, socketId);
  }

  async getSocketIds(userId: string): Promise<string[]> {
    return this.redis.smembers(`sockets:${userId}`);
  }

  async enqueueOffline(userId: string, notificationId: string): Promise<void> {
    await this.redis.rpush(`offline:${userId}`, notificationId);
    await this.redis.expire(`offline:${userId}`, 604800); // 7 days
  }

  async drainOfflineQueue(userId: string): Promise<string[]> {
    const ids = await this.redis.lrange(`offline:${userId}`, 0, -1);
    if (ids.length > 0) await this.redis.del(`offline:${userId}`);
    return ids;
  }
}
```

- [ ] 5. Create `__tests__/redis-deduplication.adapter.spec.ts`

```typescript
import { Test } from '@nestjs/testing';
import RedisMock from 'ioredis-mock';
import { RedisDeduplicationAdapter } from '../redis-deduplication.adapter';
import { REDIS_CLIENT } from '../redis.module';

describe('RedisDeduplicationAdapter', () => {
  let adapter: RedisDeduplicationAdapter;

  beforeEach(async () => {
    const module = await Test.createTestingModule({
      providers: [
        RedisDeduplicationAdapter,
        { provide: REDIS_CLIENT, useValue: new RedisMock() },
      ],
    }).compile();
    adapter = module.get(RedisDeduplicationAdapter);
  });

  it('returns false for new key', async () => {
    expect(await adapter.isDuplicate('key-1')).toBe(false);
  });

  it('returns true after markProcessed', async () => {
    await adapter.markProcessed('key-2');
    expect(await adapter.isDuplicate('key-2')).toBe(true);
  });
});
```

- [ ] 6. Run tests

```bash
cd services/notification-service
npx jest infrastructure/cache --no-coverage
# Expected: 2 tests pass
```

**Commit:** `feat(notification-service): add Redis deduplication and connection registry adapters`

---

### Task 6: Infrastructure — WebSocket Gateway

**Files:**
- Modify: `services/notification-service/package.json` (add socket.io deps)
- Create: `services/notification-service/src/notification/infrastructure/realtime/socketio-notification.gateway.ts`
- Create: `services/notification-service/src/notification/infrastructure/realtime/socketio-realtime-channel.adapter.ts`
- Test: `services/notification-service/src/notification/infrastructure/realtime/__tests__/socketio-notification.gateway.spec.ts`

**Steps:**

- [ ] 1. Install dependencies

```bash
cd services/notification-service
npm install @nestjs/websockets @nestjs/platform-socket.io socket.io
npm install --save-dev socket.io-client
```

- [ ] 2. Create `socketio-notification.gateway.ts`

```typescript
import {
  WebSocketGateway, WebSocketServer, OnGatewayConnection, OnGatewayDisconnect,
} from '@nestjs/websockets';
import { Server, Socket } from 'socket.io';
import { Inject, Logger } from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import { ConnectionRegistryPort, CONNECTION_REGISTRY_PORT } from '../../domain/port/outbound/connection-registry.port';
import { NotificationRepository, NOTIFICATION_REPOSITORY } from '../../domain/port/outbound/notification.repository';
import { NotificationMapper } from '../persistence/notification.mapper';

@WebSocketGateway({ namespace: '/ws/notifications', cors: { origin: '*' } })
export class SocketioNotificationGateway implements OnGatewayConnection, OnGatewayDisconnect {
  @WebSocketServer() server: Server;
  private readonly logger = new Logger(SocketioNotificationGateway.name);

  constructor(
    private readonly jwtService: JwtService,
    @Inject(CONNECTION_REGISTRY_PORT) private readonly registry: ConnectionRegistryPort,
    @Inject(NOTIFICATION_REPOSITORY) private readonly notificationRepo: NotificationRepository,
  ) {}

  async handleConnection(client: Socket): Promise<void> {
    try {
      const token = client.handshake.query['token'] as string;
      if (!token) { client.disconnect(); return; }

      const payload = this.jwtService.verify<{ sub: string }>(token);
      const userId = payload.sub;
      client.data['userId'] = userId;

      await this.registry.register(userId, client.id);
      client.join(`user:${userId}`);

      // Drain offline queue
      const offlineIds = await this.registry.drainOfflineQueue(userId);
      if (offlineIds.length > 0) {
        const notifications = await Promise.all(offlineIds.map((id) => this.notificationRepo.findById(id)));
        const valid = notifications.filter(Boolean);
        if (valid.length > 0) {
          client.emit('notification:catch-up', valid.map((n) => NotificationMapper.toPersistence(n!)));
        }
      }

      this.logger.log(`Client connected: ${client.id} userId=${userId}`);
    } catch {
      client.disconnect();
    }
  }

  async handleDisconnect(client: Socket): Promise<void> {
    const userId = client.data['userId'] as string | undefined;
    if (userId) {
      await this.registry.unregister(userId, client.id);
      this.logger.log(`Client disconnected: ${client.id} userId=${userId}`);
    }
  }
}
```

- [ ] 3. Create `socketio-realtime-channel.adapter.ts`

```typescript
import { Injectable } from '@nestjs/common';
import { Server } from 'socket.io';
import { InjectSocketIoServer } from '@nestjs/websockets';
import { RealtimeChannelPort } from '../../domain/port/outbound/realtime-channel.port';
import { Notification } from '../../domain/model/notification';
import { NotificationMapper } from '../persistence/notification.mapper';

@Injectable()
export class SocketioRealtimeChannelAdapter implements RealtimeChannelPort {
  constructor(@InjectSocketIoServer() private readonly server: Server) {}

  async sendToUser(userId: string, notification: Notification): Promise<void> {
    this.server.to(`user:${userId}`).emit('notification:new', NotificationMapper.toPersistence(notification));
  }

  async sendBatchToUser(userId: string, notifications: Notification[]): Promise<void> {
    this.server.to(`user:${userId}`).emit('notification:catch-up', notifications.map(NotificationMapper.toPersistence));
  }

  async isUserConnected(userId: string): Promise<boolean> {
    const sockets = await this.server.in(`user:${userId}`).fetchSockets();
    return sockets.length > 0;
  }
}
```

- [ ] 4. Create `__tests__/socketio-notification.gateway.spec.ts`

```typescript
import { Test } from '@nestjs/testing';
import { io, Socket } from 'socket.io-client';
import { INestApplication } from '@nestjs/common';
import { SocketIoAdapter } from '@nestjs/platform-socket.io';
import { JwtModule } from '@nestjs/jwt';
import { SocketioNotificationGateway } from '../socketio-notification.gateway';
import { CONNECTION_REGISTRY_PORT } from '../../../domain/port/outbound/connection-registry.port';
import { NOTIFICATION_REPOSITORY } from '../../../domain/port/outbound/notification.repository';

describe('SocketioNotificationGateway', () => {
  let app: INestApplication;
  let client: Socket;

  const mockRegistry = { register: jest.fn(), unregister: jest.fn(), getSocketIds: jest.fn(), enqueueOffline: jest.fn(), drainOfflineQueue: jest.fn().mockResolvedValue([]) };
  const mockRepo = { findById: jest.fn() };

  beforeAll(async () => {
    const module = await Test.createTestingModule({
      imports: [JwtModule.register({ secret: 'test-secret' })],
      providers: [
        SocketioNotificationGateway,
        { provide: CONNECTION_REGISTRY_PORT, useValue: mockRegistry },
        { provide: NOTIFICATION_REPOSITORY, useValue: mockRepo },
      ],
    }).compile();

    app = module.createNestApplication();
    app.useWebSocketAdapter(new SocketIoAdapter(app));
    await app.listen(0);
    const port = (app.getHttpServer().address() as { port: number }).port;

    const { JwtService } = await import('@nestjs/jwt');
    const jwt = module.get(JwtService);
    const token = jwt.sign({ sub: 'user-test' });

    client = io(`http://localhost:${port}/ws/notifications`, { query: { token }, transports: ['websocket'] });
    await new Promise<void>((resolve) => client.on('connect', resolve));
  });

  afterAll(async () => { client.disconnect(); await app.close(); });

  it('connects successfully with valid JWT', () => {
    expect(client.connected).toBe(true);
  });

  it('disconnects with invalid token', async () => {
    const bad = io(`http://localhost:${(app.getHttpServer().address() as { port: number }).port}/ws/notifications`, { query: { token: 'bad' }, transports: ['websocket'] });
    await new Promise<void>((resolve) => bad.on('disconnect', () => resolve()));
    expect(bad.connected).toBe(false);
  });
});
```

- [ ] 5. Run tests

```bash
cd services/notification-service
npx jest infrastructure/realtime --no-coverage
# Expected: 2 tests pass
```

**Commit:** `feat(notification-service): add socket.io WebSocket gateway and realtime channel adapter`

---

### Task 7: Application Layer — Command Use Cases

**Files:**
- Create: `services/notification-service/src/notification/application/command/send-notification.use-case.ts`
- Create: `services/notification-service/src/notification/application/command/mark-notification-read.use-case.ts`
- Create: `services/notification-service/src/notification/application/command/mark-all-read.use-case.ts`
- Create: `services/notification-service/src/notification/application/command/retry-failed-deliveries.use-case.ts`
- Create: `services/notification-service/src/notification/application/event-handler/notification-created.handler.ts`
- Test: `services/notification-service/src/notification/application/command/__tests__/send-notification.use-case.spec.ts`

**Steps:**

- [ ] 1. Create `send-notification.use-case.ts`

```typescript
import { Inject, Injectable, Logger } from '@nestjs/common';
import { EventEmitter2 } from '@nestjs/event-emitter';
import { Notification } from '../../domain/model/notification';
import { NotificationType } from '../../domain/model/notification-type.enum';
import { Priority } from '../../domain/model/priority.enum';
import { NotificationThread } from '../../domain/model/notification-thread';
import { NOTIFICATION_REPOSITORY, NotificationRepository } from '../../domain/port/outbound/notification.repository';
import { DEDUPLICATION_PORT, DeduplicationPort } from '../../domain/port/outbound/deduplication.port';

export interface SendNotificationCommand {
  userId: string;
  type: NotificationType;
  title: string;
  body: string;
  deepLink?: string;
  priority?: Priority;
  thread?: { threadId: string; threadTitle: string; rootNotificationId: string };
  metadata?: Record<string, unknown>;
  idempotencyKey?: string;
}

@Injectable()
export class SendNotificationUseCase {
  private readonly logger = new Logger(SendNotificationUseCase.name);

  constructor(
    @Inject(NOTIFICATION_REPOSITORY) private readonly repo: NotificationRepository,
    @Inject(DEDUPLICATION_PORT) private readonly dedup: DeduplicationPort,
    private readonly eventEmitter: EventEmitter2,
  ) {}

  async execute(command: SendNotificationCommand): Promise<Notification> {
    if (command.idempotencyKey) {
      const isDup = await this.dedup.isDuplicate(command.idempotencyKey);
      if (isDup) {
        const existing = await this.repo.findByIdempotencyKey(command.idempotencyKey);
        if (existing) return existing;
      }
    }

    const thread = command.thread
      ? NotificationThread.create(command.thread.threadId, command.thread.threadTitle, command.thread.rootNotificationId)
      : undefined;

    const notification = Notification.create({
      userId: command.userId,
      type: command.type,
      title: command.title,
      body: command.body,
      deepLink: command.deepLink,
      priority: command.priority,
      thread,
      metadata: command.metadata,
      idempotencyKey: command.idempotencyKey,
    });

    await this.repo.save(notification);

    if (command.idempotencyKey) {
      await this.dedup.markProcessed(command.idempotencyKey);
    }

    const events = notification.pullDomainEvents();
    for (const event of events) {
      this.eventEmitter.emit('notification.created', event);
    }

    this.logger.log(`Notification sent: id=${notification.id} userId=${notification.userId} type=${notification.type}`);
    return notification;
  }
}
```

- [ ] 2. Create `mark-notification-read.use-case.ts`

```typescript
import { Inject, Injectable, NotFoundException } from '@nestjs/common';
import { NOTIFICATION_REPOSITORY, NotificationRepository } from '../../domain/port/outbound/notification.repository';

@Injectable()
export class MarkNotificationReadUseCase {
  constructor(@Inject(NOTIFICATION_REPOSITORY) private readonly repo: NotificationRepository) {}

  async execute(notificationId: string, userId: string): Promise<void> {
    const notification = await this.repo.findById(notificationId);
    if (!notification) throw new NotFoundException(`Notification ${notificationId} not found`);
    if (notification.userId !== userId) throw new NotFoundException(`Notification ${notificationId} not found`);
    notification.markRead();
    await this.repo.save(notification);
  }
}
```

- [ ] 3. Create `mark-all-read.use-case.ts`

```typescript
import { Inject, Injectable } from '@nestjs/common';
import { NOTIFICATION_REPOSITORY, NotificationRepository } from '../../domain/port/outbound/notification.repository';

@Injectable()
export class MarkAllReadUseCase {
  constructor(@Inject(NOTIFICATION_REPOSITORY) private readonly repo: NotificationRepository) {}

  async execute(userId: string): Promise<void> {
    await this.repo.markAllReadForUser(userId);
  }
}
```

- [ ] 4. Create `retry-failed-deliveries.use-case.ts`

```typescript
import { Inject, Injectable, Logger } from '@nestjs/common';
import { NOTIFICATION_REPOSITORY, NotificationRepository } from '../../domain/port/outbound/notification.repository';
import { REALTIME_CHANNEL_PORT, RealtimeChannelPort } from '../../domain/port/outbound/realtime-channel.port';
import { DeliveryStatusValue } from '../../domain/model/delivery-status';

@Injectable()
export class RetryFailedDeliveriesUseCase {
  private readonly logger = new Logger(RetryFailedDeliveriesUseCase.name);

  constructor(
    @Inject(NOTIFICATION_REPOSITORY) private readonly repo: NotificationRepository,
    @Inject(REALTIME_CHANNEL_PORT) private readonly channel: RealtimeChannelPort,
  ) {}

  async execute(userId: string): Promise<void> {
    const { items } = await this.repo.findByUser({ userId, limit: 50 });
    const failed = items.filter((n) => n.deliveryStatus.getValue() === DeliveryStatusValue.FAILED);
    for (const notification of failed) {
      try {
        await this.channel.sendToUser(userId, notification);
        notification.markDelivered();
        await this.repo.save(notification);
      } catch (err) {
        this.logger.warn(`Retry failed for notification ${notification.id}: ${err}`);
      }
    }
  }
}
```

- [ ] 5. Create `notification-created.handler.ts`

```typescript
import { Inject, Injectable, Logger } from '@nestjs/common';
import { OnEvent } from '@nestjs/event-emitter';
import { NotificationCreatedEvent } from '../../domain/event/notification-created.event';
import { NOTIFICATION_REPOSITORY, NotificationRepository } from '../../domain/port/outbound/notification.repository';
import { REALTIME_CHANNEL_PORT, RealtimeChannelPort } from '../../domain/port/outbound/realtime-channel.port';
import { CONNECTION_REGISTRY_PORT, ConnectionRegistryPort } from '../../domain/port/outbound/connection-registry.port';

@Injectable()
export class NotificationCreatedHandler {
  private readonly logger = new Logger(NotificationCreatedHandler.name);

  constructor(
    @Inject(NOTIFICATION_REPOSITORY) private readonly repo: NotificationRepository,
    @Inject(REALTIME_CHANNEL_PORT) private readonly channel: RealtimeChannelPort,
    @Inject(CONNECTION_REGISTRY_PORT) private readonly registry: ConnectionRegistryPort,
  ) {}

  @OnEvent('notification.created')
  async handle(event: NotificationCreatedEvent): Promise<void> {
    const notification = await this.repo.findById(event.notificationId);
    if (!notification) return;

    const isConnected = await this.channel.isUserConnected(event.userId);
    if (isConnected) {
      try {
        await this.channel.sendToUser(event.userId, notification);
        notification.markDelivered();
        await this.repo.save(notification);
      } catch (err) {
        notification.markFailed();
        await this.repo.save(notification);
        await this.registry.enqueueOffline(event.userId, notification.id);
        this.logger.warn(`Realtime delivery failed for ${notification.id}: ${err}`);
      }
    } else {
      await this.registry.enqueueOffline(event.userId, notification.id);
      this.logger.log(`User ${event.userId} offline — queued notification ${notification.id}`);
    }
  }
}
```

- [ ] 6. Create `__tests__/send-notification.use-case.spec.ts`

```typescript
import { Test } from '@nestjs/testing';
import { EventEmitter2 } from '@nestjs/event-emitter';
import { SendNotificationUseCase } from '../send-notification.use-case';
import { NOTIFICATION_REPOSITORY } from '../../../domain/port/outbound/notification.repository';
import { DEDUPLICATION_PORT } from '../../../domain/port/outbound/deduplication.port';
import { NotificationType } from '../../../domain/model/notification-type.enum';

describe('SendNotificationUseCase', () => {
  let useCase: SendNotificationUseCase;
  const mockRepo = { save: jest.fn(), findByIdempotencyKey: jest.fn() };
  const mockDedup = { isDuplicate: jest.fn().mockResolvedValue(false), markProcessed: jest.fn() };
  const mockEmitter = { emit: jest.fn() };

  beforeEach(async () => {
    jest.clearAllMocks();
    const module = await Test.createTestingModule({
      providers: [
        SendNotificationUseCase,
        { provide: NOTIFICATION_REPOSITORY, useValue: mockRepo },
        { provide: DEDUPLICATION_PORT, useValue: mockDedup },
        { provide: EventEmitter2, useValue: mockEmitter },
      ],
    }).compile();
    useCase = module.get(SendNotificationUseCase);
  });

  it('saves notification and emits domain event', async () => {
    const n = await useCase.execute({ userId: 'u1', type: NotificationType.ORDER_PLACED, title: 'T', body: 'B', idempotencyKey: 'k1' });
    expect(mockRepo.save).toHaveBeenCalledWith(n);
    expect(mockDedup.markProcessed).toHaveBeenCalledWith('k1');
    expect(mockEmitter.emit).toHaveBeenCalledWith('notification.created', expect.any(Object));
  });

  it('skips duplicate idempotency key', async () => {
    mockDedup.isDuplicate.mockResolvedValue(true);
    mockRepo.findByIdempotencyKey.mockResolvedValue({ id: 'existing' });
    const result = await useCase.execute({ userId: 'u1', type: NotificationType.ORDER_PLACED, title: 'T', body: 'B', idempotencyKey: 'k1' });
    expect(result).toEqual({ id: 'existing' });
    expect(mockRepo.save).not.toHaveBeenCalled();
  });
});
```

- [ ] 7. Run tests

```bash
cd services/notification-service
npx jest application/command --no-coverage
# Expected: 2 tests pass
```

**Commit:** `feat(notification-service): implement command use cases with dedup and event-driven delivery`

---

### Task 8: Application Layer — Query Use Cases

**Files:**
- Create: `services/notification-service/src/notification/application/query/find-user-notifications.use-case.ts`
- Create: `services/notification-service/src/notification/application/query/find-notification-threads.use-case.ts`
- Create: `services/notification-service/src/notification/application/query/find-thread-notifications.use-case.ts`
- Create: `services/notification-service/src/notification/application/query/count-unread.use-case.ts`
- Test: `services/notification-service/src/notification/application/query/__tests__/find-user-notifications.use-case.spec.ts`

**Steps:**

- [ ] 1. Create `find-user-notifications.use-case.ts`

```typescript
import { Inject, Injectable } from '@nestjs/common';
import { NOTIFICATION_REPOSITORY, NotificationRepository, FindUserNotificationsOptions } from '../../domain/port/outbound/notification.repository';
import { Notification } from '../../domain/model/notification';

export interface FindUserNotificationsQuery {
  userId: string;
  type?: string;
  threadId?: string;
  page?: number;
  limit?: number;
}

@Injectable()
export class FindUserNotificationsUseCase {
  constructor(@Inject(NOTIFICATION_REPOSITORY) private readonly repo: NotificationRepository) {}

  async execute(query: FindUserNotificationsQuery): Promise<{ items: Notification[]; total: number; page: number; limit: number }> {
    const page = query.page ?? 1;
    const limit = Math.min(query.limit ?? 20, 100);
    const options: FindUserNotificationsOptions = {
      userId: query.userId,
      type: query.type as any,
      threadId: query.threadId,
      page,
      limit,
    };
    const { items, total } = await this.repo.findByUser(options);
    return { items, total, page, limit };
  }
}
```

- [ ] 2. Create `find-notification-threads.use-case.ts`

```typescript
import { Inject, Injectable } from '@nestjs/common';
import { NOTIFICATION_REPOSITORY, NotificationRepository } from '../../domain/port/outbound/notification.repository';

export interface ThreadSummary {
  threadId: string;
  threadTitle: string;
  unreadCount: number;
  latestAt: Date;
}

@Injectable()
export class FindNotificationThreadsUseCase {
  constructor(@Inject(NOTIFICATION_REPOSITORY) private readonly repo: NotificationRepository) {}

  async execute(userId: string, page = 1, limit = 20): Promise<ThreadSummary[]> {
    return this.repo.findThreadsByUser(userId, page, Math.min(limit, 50));
  }
}
```

- [ ] 3. Create `find-thread-notifications.use-case.ts`

```typescript
import { Inject, Injectable } from '@nestjs/common';
import { NOTIFICATION_REPOSITORY, NotificationRepository } from '../../domain/port/outbound/notification.repository';
import { Notification } from '../../domain/model/notification';

@Injectable()
export class FindThreadNotificationsUseCase {
  constructor(@Inject(NOTIFICATION_REPOSITORY) private readonly repo: NotificationRepository) {}

  async execute(threadId: string, userId: string): Promise<Notification[]> {
    return this.repo.findByThread(threadId, userId);
  }
}
```

- [ ] 4. Create `count-unread.use-case.ts`

```typescript
import { Inject, Injectable } from '@nestjs/common';
import { NOTIFICATION_REPOSITORY, NotificationRepository } from '../../domain/port/outbound/notification.repository';

@Injectable()
export class CountUnreadUseCase {
  constructor(@Inject(NOTIFICATION_REPOSITORY) private readonly repo: NotificationRepository) {}

  async execute(userId: string): Promise<number> {
    return this.repo.countUnread(userId);
  }
}
```

- [ ] 5. Create `__tests__/find-user-notifications.use-case.spec.ts`

```typescript
import { Test } from '@nestjs/testing';
import { FindUserNotificationsUseCase } from '../find-user-notifications.use-case';
import { NOTIFICATION_REPOSITORY } from '../../../domain/port/outbound/notification.repository';
import { NotificationType } from '../../../domain/model/notification-type.enum';

describe('FindUserNotificationsUseCase', () => {
  let useCase: FindUserNotificationsUseCase;
  const mockRepo = { findByUser: jest.fn().mockResolvedValue({ items: [], total: 0 }) };

  beforeEach(async () => {
    const module = await Test.createTestingModule({
      providers: [
        FindUserNotificationsUseCase,
        { provide: NOTIFICATION_REPOSITORY, useValue: mockRepo },
      ],
    }).compile();
    useCase = module.get(FindUserNotificationsUseCase);
  });

  it('passes type filter to repository', async () => {
    await useCase.execute({ userId: 'u1', type: NotificationType.ORDER_PLACED });
    expect(mockRepo.findByUser).toHaveBeenCalledWith(expect.objectContaining({ type: NotificationType.ORDER_PLACED }));
  });

  it('caps limit at 100', async () => {
    await useCase.execute({ userId: 'u1', limit: 999 });
    expect(mockRepo.findByUser).toHaveBeenCalledWith(expect.objectContaining({ limit: 100 }));
  });
});
```

- [ ] 6. Run tests

```bash
cd services/notification-service
npx jest application/query --no-coverage
# Expected: 2 tests pass
```

**Commit:** `feat(notification-service): add query use cases for notifications and threads`

---

### Task 9: Infrastructure — Kafka Consumer (Expanded)

**Files:**
- Create: `services/notification-service/src/notification/infrastructure/messaging/kafka-event.consumer.ts`
- Create: `services/notification-service/src/notification/infrastructure/messaging/event-handler-registry.ts`
- Test: `services/notification-service/src/notification/infrastructure/messaging/__tests__/kafka-event.consumer.spec.ts`

**Steps:**

- [ ] 1. Create `event-handler-registry.ts`

```typescript
import { NotificationType } from '../../domain/model/notification-type.enum';

export interface KafkaTopic {
  topic: string;
  notificationType: NotificationType;
}

export const KAFKA_TOPIC_REGISTRY: KafkaTopic[] = [
  { topic: 'order.placed', notificationType: NotificationType.ORDER_PLACED },
  { topic: 'order.confirmed', notificationType: NotificationType.ORDER_CONFIRMED },
  { topic: 'order.shipped', notificationType: NotificationType.ORDER_SHIPPED },
  { topic: 'order.delivered', notificationType: NotificationType.ORDER_DELIVERED },
  { topic: 'order.cancelled', notificationType: NotificationType.ORDER_CANCELLED },
  { topic: 'payment.success', notificationType: NotificationType.PAYMENT_SUCCESS },
  { topic: 'payment.failed', notificationType: NotificationType.PAYMENT_FAILED },
  { topic: 'promotion.flash-sale', notificationType: NotificationType.PROMOTION_FLASH_SALE },
  { topic: 'promotion.coupon', notificationType: NotificationType.PROMOTION_COUPON },
  { topic: 'account.security', notificationType: NotificationType.ACCOUNT_SECURITY },
  { topic: 'review.reply', notificationType: NotificationType.REVIEW_REPLY },
  { topic: 'system.announcement', notificationType: NotificationType.SYSTEM_ANNOUNCEMENT },
];
```

- [ ] 2. Create `kafka-event.consumer.ts`

```typescript
import { Controller, Logger } from '@nestjs/common';
import { MessagePattern, Payload, Ctx, KafkaContext } from '@nestjs/microservices';
import { SendNotificationUseCase } from '../../application/command/send-notification.use-case';
import { NotificationFactory } from '../../domain/service/notification-factory';
import { NotificationType } from '../../domain/model/notification-type.enum';

interface KafkaMessage {
  userId: string;
  metadata: Record<string, unknown>;
  idempotencyKey?: string;
}

@Controller()
export class KafkaEventConsumer {
  private readonly logger = new Logger(KafkaEventConsumer.name);

  constructor(private readonly sendNotification: SendNotificationUseCase) {}

  private async handleEvent(type: NotificationType, message: KafkaMessage, ctx: KafkaContext): Promise<void> {
    const topic = ctx.getTopic();
    try {
      const notification = NotificationFactory.fromKafkaEvent({
        userId: message.userId,
        type,
        metadata: message.metadata,
        idempotencyKey: message.idempotencyKey ?? `${topic}:${ctx.getPartition()}:${ctx.getMessage().offset}`,
      });
      await this.sendNotification.execute({
        userId: notification.userId,
        type: notification.type,
        title: notification.title,
        body: notification.body,
        deepLink: notification.deepLink,
        priority: notification.priority,
        metadata: notification.metadata,
        idempotencyKey: notification.idempotencyKey,
      });
    } catch (err) {
      this.logger.error(`Failed to process ${topic}: ${err}`);
    }
  }

  @MessagePattern('order.placed')
  async onOrderPlaced(@Payload() msg: KafkaMessage, @Ctx() ctx: KafkaContext) {
    await this.handleEvent(NotificationType.ORDER_PLACED, msg, ctx);
  }

  @MessagePattern('order.confirmed')
  async onOrderConfirmed(@Payload() msg: KafkaMessage, @Ctx() ctx: KafkaContext) {
    await this.handleEvent(NotificationType.ORDER_CONFIRMED, msg, ctx);
  }

  @MessagePattern('order.shipped')
  async onOrderShipped(@Payload() msg: KafkaMessage, @Ctx() ctx: KafkaContext) {
    await this.handleEvent(NotificationType.ORDER_SHIPPED, msg, ctx);
  }

  @MessagePattern('order.delivered')
  async onOrderDelivered(@Payload() msg: KafkaMessage, @Ctx() ctx: KafkaContext) {
    await this.handleEvent(NotificationType.ORDER_DELIVERED, msg, ctx);
  }

  @MessagePattern('order.cancelled')
  async onOrderCancelled(@Payload() msg: KafkaMessage, @Ctx() ctx: KafkaContext) {
    await this.handleEvent(NotificationType.ORDER_CANCELLED, msg, ctx);
  }

  @MessagePattern('payment.success')
  async onPaymentSuccess(@Payload() msg: KafkaMessage, @Ctx() ctx: KafkaContext) {
    await this.handleEvent(NotificationType.PAYMENT_SUCCESS, msg, ctx);
  }

  @MessagePattern('payment.failed')
  async onPaymentFailed(@Payload() msg: KafkaMessage, @Ctx() ctx: KafkaContext) {
    await this.handleEvent(NotificationType.PAYMENT_FAILED, msg, ctx);
  }

  @MessagePattern('promotion.flash-sale')
  async onFlashSale(@Payload() msg: KafkaMessage, @Ctx() ctx: KafkaContext) {
    await this.handleEvent(NotificationType.PROMOTION_FLASH_SALE, msg, ctx);
  }

  @MessagePattern('promotion.coupon')
  async onCoupon(@Payload() msg: KafkaMessage, @Ctx() ctx: KafkaContext) {
    await this.handleEvent(NotificationType.PROMOTION_COUPON, msg, ctx);
  }

  @MessagePattern('account.security')
  async onAccountSecurity(@Payload() msg: KafkaMessage, @Ctx() ctx: KafkaContext) {
    await this.handleEvent(NotificationType.ACCOUNT_SECURITY, msg, ctx);
  }

  @MessagePattern('review.reply')
  async onReviewReply(@Payload() msg: KafkaMessage, @Ctx() ctx: KafkaContext) {
    await this.handleEvent(NotificationType.REVIEW_REPLY, msg, ctx);
  }

  @MessagePattern('system.announcement')
  async onSystemAnnouncement(@Payload() msg: KafkaMessage, @Ctx() ctx: KafkaContext) {
    await this.handleEvent(NotificationType.SYSTEM_ANNOUNCEMENT, msg, ctx);
  }
}
```

- [ ] 3. Create `__tests__/kafka-event.consumer.spec.ts`

```typescript
import { Test } from '@nestjs/testing';
import { KafkaEventConsumer } from '../kafka-event.consumer';
import { SendNotificationUseCase } from '../../../application/command/send-notification.use-case';
import { NotificationType } from '../../../domain/model/notification-type.enum';

const mockCtx = (topic: string) => ({
  getTopic: () => topic,
  getPartition: () => 0,
  getMessage: () => ({ offset: '1' }),
});

describe('KafkaEventConsumer', () => {
  let consumer: KafkaEventConsumer;
  const mockSend = { execute: jest.fn().mockResolvedValue({}) };

  beforeEach(async () => {
    jest.clearAllMocks();
    const module = await Test.createTestingModule({
      providers: [
        KafkaEventConsumer,
        { provide: SendNotificationUseCase, useValue: mockSend },
      ],
    }).compile();
    consumer = module.get(KafkaEventConsumer);
  });

  it('handles order.placed and calls SendNotificationUseCase', async () => {
    await consumer.onOrderPlaced({ userId: 'u1', metadata: { orderId: 'O1' } }, mockCtx('order.placed') as any);
    expect(mockSend.execute).toHaveBeenCalledWith(expect.objectContaining({ type: NotificationType.ORDER_PLACED, userId: 'u1' }));
  });

  it('handles payment.failed', async () => {
    await consumer.onPaymentFailed({ userId: 'u2', metadata: { orderId: 'O2' } }, mockCtx('payment.failed') as any);
    expect(mockSend.execute).toHaveBeenCalledWith(expect.objectContaining({ type: NotificationType.PAYMENT_FAILED }));
  });
});
```

- [ ] 4. Run tests

```bash
cd services/notification-service
npx jest infrastructure/messaging --no-coverage
# Expected: 2 tests pass
```

**Commit:** `feat(notification-service): expand Kafka consumer to handle all 12 notification types`

---

### Task 10: Infrastructure — REST Controller Updates

**Files:**
- Create: `services/notification-service/src/notification/infrastructure/rest/dto/notification-response.dto.ts`
- Create: `services/notification-service/src/notification/infrastructure/rest/dto/thread-response.dto.ts`
- Create: `services/notification-service/src/notification/infrastructure/rest/notification.controller.ts`
- Test: `services/notification-service/src/notification/infrastructure/rest/__tests__/notification.controller.spec.ts`

**Steps:**

- [ ] 1. Create `notification-response.dto.ts`

```typescript
import { Notification } from '../../../domain/model/notification';
import { DeliveryStatusValue } from '../../../domain/model/delivery-status';

export class NotificationResponseDto {
  id: string;
  userId: string;
  type: string;
  title: string;
  body: string;
  deepLink?: string;
  priority: string;
  deliveryStatus: DeliveryStatusValue;
  threadId?: string;
  threadTitle?: string;
  metadata: Record<string, unknown>;
  createdAt: string;
  isRead: boolean;

  static fromDomain(n: Notification): NotificationResponseDto {
    const dto = new NotificationResponseDto();
    dto.id = n.id;
    dto.userId = n.userId;
    dto.type = n.type;
    dto.title = n.title;
    dto.body = n.body;
    dto.deepLink = n.deepLink;
    dto.priority = n.priority;
    dto.deliveryStatus = n.deliveryStatus.getValue();
    dto.threadId = n.thread?.threadId;
    dto.threadTitle = n.thread?.threadTitle;
    dto.metadata = n.metadata;
    dto.createdAt = n.createdAt.toISOString();
    dto.isRead = n.deliveryStatus.isRead();
    return dto;
  }
}
```

- [ ] 2. Create `thread-response.dto.ts`

```typescript
export class ThreadResponseDto {
  threadId: string;
  threadTitle: string;
  unreadCount: number;
  latestAt: string;

  static fromRaw(raw: { threadId: string; threadTitle: string; unreadCount: number; latestAt: Date }): ThreadResponseDto {
    const dto = new ThreadResponseDto();
    dto.threadId = raw.threadId;
    dto.threadTitle = raw.threadTitle;
    dto.unreadCount = raw.unreadCount;
    dto.latestAt = raw.latestAt.toISOString();
    return dto;
  }
}
```

- [ ] 3. Create `notification.controller.ts`

```typescript
import { Controller, Get, Patch, Param, Query, Req, UseGuards, HttpCode, HttpStatus, ParseIntPipe, DefaultValuePipe } from '@nestjs/common';
import { Request } from 'express';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';
import { FindUserNotificationsUseCase } from '../../application/query/find-user-notifications.use-case';
import { FindNotificationThreadsUseCase } from '../../application/query/find-notification-threads.use-case';
import { FindThreadNotificationsUseCase } from '../../application/query/find-thread-notifications.use-case';
import { CountUnreadUseCase } from '../../application/query/count-unread.use-case';
import { MarkNotificationReadUseCase } from '../../application/command/mark-notification-read.use-case';
import { MarkAllReadUseCase } from '../../application/command/mark-all-read.use-case';
import { NotificationResponseDto } from './dto/notification-response.dto';
import { ThreadResponseDto } from './dto/thread-response.dto';

@Controller('notifications')
@UseGuards(JwtAuthGuard)
export class NotificationController {
  constructor(
    private readonly findUserNotifications: FindUserNotificationsUseCase,
    private readonly findThreads: FindNotificationThreadsUseCase,
    private readonly findThreadNotifications: FindThreadNotificationsUseCase,
    private readonly countUnread: CountUnreadUseCase,
    private readonly markRead: MarkNotificationReadUseCase,
    private readonly markAllRead: MarkAllReadUseCase,
  ) {}

  @Get()
  async getNotifications(
    @Req() req: Request,
    @Query('type') type?: string,
    @Query('threadId') threadId?: string,
    @Query('page', new DefaultValuePipe(1), ParseIntPipe) page = 1,
    @Query('limit', new DefaultValuePipe(20), ParseIntPipe) limit = 20,
  ) {
    const userId = (req.user as { userId: string }).userId;
    const result = await this.findUserNotifications.execute({ userId, type, threadId, page, limit });
    return {
      items: result.items.map(NotificationResponseDto.fromDomain),
      total: result.total,
      page: result.page,
      limit: result.limit,
    };
  }

  @Get('threads')
  async getThreads(
    @Req() req: Request,
    @Query('page', new DefaultValuePipe(1), ParseIntPipe) page = 1,
    @Query('limit', new DefaultValuePipe(20), ParseIntPipe) limit = 20,
  ) {
    const userId = (req.user as { userId: string }).userId;
    const threads = await this.findThreads.execute(userId, page, limit);
    return threads.map(ThreadResponseDto.fromRaw);
  }

  @Get('threads/:threadId')
  async getThreadNotifications(@Req() req: Request, @Param('threadId') threadId: string) {
    const userId = (req.user as { userId: string }).userId;
    const items = await this.findThreadNotifications.execute(threadId, userId);
    return items.map(NotificationResponseDto.fromDomain);
  }

  @Get('unread-count')
  async getUnreadCount(@Req() req: Request) {
    const userId = (req.user as { userId: string }).userId;
    const count = await this.countUnread.execute(userId);
    return { count };
  }

  @Patch(':id/read')
  @HttpCode(HttpStatus.NO_CONTENT)
  async markNotificationRead(@Req() req: Request, @Param('id') id: string) {
    const userId = (req.user as { userId: string }).userId;
    await this.markRead.execute(id, userId);
  }

  @Patch('read-all')
  @HttpCode(HttpStatus.NO_CONTENT)
  async markAllNotificationsRead(@Req() req: Request) {
    const userId = (req.user as { userId: string }).userId;
    await this.markAllRead.execute(userId);
  }
}
```

- [ ] 4. Create `__tests__/notification.controller.spec.ts`

```typescript
import { Test } from '@nestjs/testing';
import { NotificationController } from '../notification.controller';
import { FindUserNotificationsUseCase } from '../../../application/query/find-user-notifications.use-case';
import { FindNotificationThreadsUseCase } from '../../../application/query/find-notification-threads.use-case';
import { FindThreadNotificationsUseCase } from '../../../application/query/find-thread-notifications.use-case';
import { CountUnreadUseCase } from '../../../application/query/count-unread.use-case';
import { MarkNotificationReadUseCase } from '../../../application/command/mark-notification-read.use-case';
import { MarkAllReadUseCase } from '../../../application/command/mark-all-read.use-case';
import { JwtAuthGuard } from '../auth/jwt-auth.guard';

describe('NotificationController', () => {
  let controller: NotificationController;
  const mockFind = { execute: jest.fn().mockResolvedValue({ items: [], total: 0, page: 1, limit: 20 }) };
  const mockThreads = { execute: jest.fn().mockResolvedValue([]) };
  const mockThreadItems = { execute: jest.fn().mockResolvedValue([]) };
  const mockCount = { execute: jest.fn().mockResolvedValue(3) };
  const mockMarkRead = { execute: jest.fn() };
  const mockMarkAll = { execute: jest.fn() };
  const mockReq = { user: { userId: 'u1' } } as any;

  beforeEach(async () => {
    const module = await Test.createTestingModule({
      controllers: [NotificationController],
      providers: [
        { provide: FindUserNotificationsUseCase, useValue: mockFind },
        { provide: FindNotificationThreadsUseCase, useValue: mockThreads },
        { provide: FindThreadNotificationsUseCase, useValue: mockThreadItems },
        { provide: CountUnreadUseCase, useValue: mockCount },
        { provide: MarkNotificationReadUseCase, useValue: mockMarkRead },
        { provide: MarkAllReadUseCase, useValue: mockMarkAll },
      ],
    }).overrideGuard(JwtAuthGuard).useValue({ canActivate: () => true }).compile();
    controller = module.get(NotificationController);
  });

  it('GET / passes type filter', async () => {
    await controller.getNotifications(mockReq, 'ORDER_PLACED');
    expect(mockFind.execute).toHaveBeenCalledWith(expect.objectContaining({ type: 'ORDER_PLACED' }));
  });

  it('GET /unread-count returns count', async () => {
    const result = await controller.getUnreadCount(mockReq);
    expect(result).toEqual({ count: 3 });
  });
});
```

- [ ] 5. Run tests

```bash
cd services/notification-service
npx jest infrastructure/rest --no-coverage
# Expected: 2 tests pass
```

**Commit:** `feat(notification-service): update REST controller with type filter, threads, and DTOs`

---

### Task 11: Module Wiring + API Gateway

**Files:**
- Create: `services/notification-service/src/notification/notification.module.ts`
- Modify: `services/notification-service/src/main.ts`
- Modify: `services/api-gateway/src/config/RouteConfig.java` (or equivalent gateway config)
- Test: `services/notification-service/src/notification/__tests__/notification.module.spec.ts`

**Steps:**

- [ ] 1. Create `notification.module.ts`

```typescript
import { Module } from '@nestjs/common';
import { MongooseModule } from '@nestjs/mongoose';
import { JwtModule } from '@nestjs/jwt';
import { ConfigService } from '@nestjs/config';
import { EventEmitterModule } from '@nestjs/event-emitter';

import { NotificationSchemaClass, NotificationSchema } from './infrastructure/persistence/mongo-notification.schema';
import { MongoNotificationRepository } from './infrastructure/persistence/mongo-notification.repository';
import { RedisModule } from './infrastructure/cache/redis.module';
import { RedisDeduplicationAdapter } from './infrastructure/cache/redis-deduplication.adapter';
import { RedisConnectionRegistryAdapter } from './infrastructure/cache/redis-connection-registry.adapter';
import { SocketioNotificationGateway } from './infrastructure/realtime/socketio-notification.gateway';
import { SocketioRealtimeChannelAdapter } from './infrastructure/realtime/socketio-realtime-channel.adapter';
import { KafkaEventConsumer } from './infrastructure/messaging/kafka-event.consumer';
import { NotificationController } from './infrastructure/rest/notification.controller';

import { SendNotificationUseCase } from './application/command/send-notification.use-case';
import { MarkNotificationReadUseCase } from './application/command/mark-notification-read.use-case';
import { MarkAllReadUseCase } from './application/command/mark-all-read.use-case';
import { RetryFailedDeliveriesUseCase } from './application/command/retry-failed-deliveries.use-case';
import { FindUserNotificationsUseCase } from './application/query/find-user-notifications.use-case';
import { FindNotificationThreadsUseCase } from './application/query/find-notification-threads.use-case';
import { FindThreadNotificationsUseCase } from './application/query/find-thread-notifications.use-case';
import { CountUnreadUseCase } from './application/query/count-unread.use-case';
import { NotificationCreatedHandler } from './application/event-handler/notification-created.handler';

import { NOTIFICATION_REPOSITORY } from './domain/port/outbound/notification.repository';
import { REALTIME_CHANNEL_PORT } from './domain/port/outbound/realtime-channel.port';
import { DEDUPLICATION_PORT } from './domain/port/outbound/deduplication.port';
import { CONNECTION_REGISTRY_PORT } from './domain/port/outbound/connection-registry.port';

@Module({
  imports: [
    MongooseModule.forFeature([{ name: NotificationSchemaClass.name, schema: NotificationSchema }]),
    JwtModule.registerAsync({
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({ secret: config.get<string>('jwt.secret') }),
    }),
    RedisModule,
    EventEmitterModule.forRoot(),
  ],
  controllers: [NotificationController, KafkaEventConsumer],
  providers: [
    // Port bindings
    { provide: NOTIFICATION_REPOSITORY, useClass: MongoNotificationRepository },
    { provide: REALTIME_CHANNEL_PORT, useClass: SocketioRealtimeChannelAdapter },
    { provide: DEDUPLICATION_PORT, useClass: RedisDeduplicationAdapter },
    { provide: CONNECTION_REGISTRY_PORT, useClass: RedisConnectionRegistryAdapter },
    // Infrastructure
    MongoNotificationRepository,
    RedisDeduplicationAdapter,
    RedisConnectionRegistryAdapter,
    SocketioNotificationGateway,
    SocketioRealtimeChannelAdapter,
    // Application — commands
    SendNotificationUseCase,
    MarkNotificationReadUseCase,
    MarkAllReadUseCase,
    RetryFailedDeliveriesUseCase,
    // Application — queries
    FindUserNotificationsUseCase,
    FindNotificationThreadsUseCase,
    FindThreadNotificationsUseCase,
    CountUnreadUseCase,
    // Event handlers
    NotificationCreatedHandler,
  ],
})
export class NotificationModule {}
```

- [ ] 2. Update `main.ts` to attach socket.io adapter

```typescript
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';
import { SocketIoAdapter } from '@nestjs/platform-socket.io';
import { MicroserviceOptions, Transport } from '@nestjs/microservices';
import { ConfigService } from '@nestjs/config';

async function bootstrap() {
  const app = await NestFactory.create(AppModule);
  const config = app.get(ConfigService);

  app.useWebSocketAdapter(new SocketIoAdapter(app, { cors: { origin: '*' } }));

  app.connectMicroservice<MicroserviceOptions>({
    transport: Transport.KAFKA,
    options: {
      client: { brokers: config.get<string[]>('kafka.brokers')! },
      consumer: { groupId: config.get<string>('kafka.groupId')! },
    },
  });

  await app.startAllMicroservices();
  await app.listen(config.get<number>('port')!);
}
bootstrap();
```

- [ ] 3. Add WebSocket route to API Gateway (`services/api-gateway/src/main/resources/application.yml` or Spring Cloud Gateway config)

```yaml
# In spring cloud gateway routes, add:
- id: notification-ws
  uri: ws://notification-service:3004
  predicates:
    - Path=/ws/notifications/**
  filters:
    - RewritePath=/ws/notifications/(?<segment>.*), /ws/notifications/$\{segment}
```

- [ ] 4. Create `__tests__/notification.module.spec.ts`

```typescript
import { Test } from '@nestjs/testing';
import { NotificationModule } from '../notification.module';
import { ConfigModule } from '@nestjs/config';
import { MongooseModule } from '@nestjs/mongoose';
import { MongoMemoryServer } from 'mongodb-memory-server';
import RedisMock from 'ioredis-mock';
import { REDIS_CLIENT } from '../infrastructure/cache/redis.module';
import configuration from '../../config/configuration';

describe('NotificationModule wiring', () => {
  let mongod: MongoMemoryServer;

  beforeAll(async () => { mongod = await MongoMemoryServer.create(); });
  afterAll(async () => { await mongod.stop(); });

  it('compiles without errors', async () => {
    const module = await Test.createTestingModule({
      imports: [
        ConfigModule.forRoot({ isGlobal: true, load: [configuration] }),
        MongooseModule.forRoot(mongod.getUri()),
        NotificationModule,
      ],
    })
      .overrideProvider(REDIS_CLIENT).useValue(new RedisMock())
      .compile();
    expect(module).toBeDefined();
    await module.close();
  });
});
```

- [ ] 5. Run tests

```bash
cd services/notification-service
npx jest notification.module --no-coverage
# Expected: 1 test passes
```

**Commit:** `feat(notification-service): wire NotificationModule with all ports, adapters, and WebSocket adapter`

---

### Task 12: FE — Zod Schemas + API Endpoints

**Files:**
- Modify: `fe/src/types/api/notification.ts`
- Modify: `fe/src/lib/api/endpoints/notifications.ts`
- Test: `fe/src/types/api/__tests__/notification.spec.ts`

**Steps:**

- [ ] 1. Update `fe/src/types/api/notification.ts`

```typescript
import { z } from 'zod';

export const NotificationTypeSchema = z.enum([
  'ORDER_PLACED', 'ORDER_CONFIRMED', 'ORDER_SHIPPED', 'ORDER_DELIVERED', 'ORDER_CANCELLED',
  'PAYMENT_SUCCESS', 'PAYMENT_FAILED',
  'PROMOTION_FLASH_SALE', 'PROMOTION_COUPON',
  'ACCOUNT_SECURITY', 'REVIEW_REPLY', 'SYSTEM_ANNOUNCEMENT',
]);
export type NotificationType = z.infer<typeof NotificationTypeSchema>;

export const DeliveryStatusSchema = z.enum(['PENDING', 'DELIVERED', 'FAILED', 'READ']);
export type DeliveryStatus = z.infer<typeof DeliveryStatusSchema>;

export const PrioritySchema = z.enum(['HIGH', 'MEDIUM', 'LOW']);
export type Priority = z.infer<typeof PrioritySchema>;

export const NotificationSchema = z.object({
  id: z.string(),
  userId: z.string(),
  type: NotificationTypeSchema,
  title: z.string(),
  body: z.string(),
  deepLink: z.string().optional(),
  priority: PrioritySchema,
  deliveryStatus: DeliveryStatusSchema,
  threadId: z.string().optional(),
  threadTitle: z.string().optional(),
  metadata: z.record(z.unknown()).default({}),
  createdAt: z.string(),
  isRead: z.boolean(),
});
export type Notification = z.infer<typeof NotificationSchema>;

export const NotificationPageSchema = z.object({
  items: z.array(NotificationSchema),
  total: z.number(),
  page: z.number(),
  limit: z.number(),
});
export type NotificationPage = z.infer<typeof NotificationPageSchema>;

export const NotificationThreadSchema = z.object({
  threadId: z.string(),
  threadTitle: z.string(),
  unreadCount: z.number(),
  latestAt: z.string(),
});
export type NotificationThread = z.infer<typeof NotificationThreadSchema>;
```

- [ ] 2. Update `fe/src/lib/api/endpoints/notifications.ts`

```typescript
import { apiClient } from '../client';
import { NotificationPage, NotificationThread, Notification } from '../../../types/api/notification';

export interface GetNotificationsParams {
  type?: string;
  threadId?: string;
  page?: number;
  limit?: number;
}

export const notificationsApi = {
  getNotifications: (params?: GetNotificationsParams) =>
    apiClient.get<NotificationPage>('/notifications', { params }),

  getThreads: (params?: { page?: number; limit?: number }) =>
    apiClient.get<NotificationThread[]>('/notifications/threads', { params }),

  getThreadNotifications: (threadId: string) =>
    apiClient.get<Notification[]>(`/notifications/threads/${threadId}`),

  getUnreadCount: () =>
    apiClient.get<{ count: number }>('/notifications/unread-count'),

  markRead: (id: string) =>
    apiClient.patch<void>(`/notifications/${id}/read`),

  markAllRead: () =>
    apiClient.patch<void>('/notifications/read-all'),
};
```

- [ ] 3. Create `fe/src/types/api/__tests__/notification.spec.ts`

```typescript
import { NotificationSchema, NotificationPageSchema } from '../notification';

describe('NotificationSchema', () => {
  it('parses a valid notification', () => {
    const raw = {
      id: '1', userId: 'u1', type: 'ORDER_PLACED', title: 'T', body: 'B',
      priority: 'HIGH', deliveryStatus: 'PENDING', metadata: {}, createdAt: new Date().toISOString(), isRead: false,
    };
    expect(() => NotificationSchema.parse(raw)).not.toThrow();
  });

  it('rejects unknown type', () => {
    const raw = { id: '1', userId: 'u1', type: 'UNKNOWN', title: 'T', body: 'B', priority: 'HIGH', deliveryStatus: 'PENDING', metadata: {}, createdAt: new Date().toISOString(), isRead: false };
    expect(() => NotificationSchema.parse(raw)).toThrow();
  });
});
```

- [ ] 4. Run tests

```bash
cd fe
npx vitest run src/types/api/__tests__/notification.spec.ts
# Expected: 2 tests pass
```

**Commit:** `feat(fe): expand notification Zod schemas and API endpoints for threads and type filter`

---

### Task 13: FE — WebSocket Hook

**Files:**
- Create: `fe/src/app/hooks/use-notification-socket.ts`
- Modify: `fe/src/App.tsx` (add to BackgroundEffects)
- Test: `fe/src/app/hooks/__tests__/use-notification-socket.spec.ts`

**Steps:**

- [ ] 1. Create `fe/src/app/hooks/use-notification-socket.ts`

```typescript
import { useEffect, useRef, useCallback } from 'react';
import { io, Socket } from 'socket.io-client';
import { useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { useAuthStore } from '../stores/auth.store';
import { Notification, NotificationSchema, NotificationPageSchema } from '../types/api/notification';

const WS_URL = import.meta.env.VITE_NOTIFICATION_WS_URL ?? 'http://localhost:3004';
const RECONNECT_BASE_MS = 1000;
const RECONNECT_MAX_MS = 30000;
const RECONNECT_FACTOR = 2;

export function useNotificationSocket() {
  const socketRef = useRef<Socket | null>(null);
  const reconnectAttemptRef = useRef(0);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const queryClient = useQueryClient();
  const token = useAuthStore((s) => s.token);

  const updateCache = useCallback(
    (notification: Notification) => {
      queryClient.setQueryData<{ items: Notification[]; total: number; page: number; limit: number }>(
        ['notifications'],
        (old) => {
          if (!old) return old;
          const exists = old.items.some((n) => n.id === notification.id);
          if (exists) return old;
          return { ...old, items: [notification, ...old.items], total: old.total + 1 };
        },
      );
      queryClient.invalidateQueries({ queryKey: ['notifications', 'unread-count'] });
    },
    [queryClient],
  );

  const connect = useCallback(() => {
    if (!token) return;
    if (socketRef.current?.connected) return;

    const socket = io(WS_URL, {
      path: '/ws/notifications',
      query: { token },
      transports: ['websocket'],
      reconnection: false, // manual backoff
    });

    socket.on('connect', () => {
      reconnectAttemptRef.current = 0;
    });

    socket.on('notification:new', (raw: unknown) => {
      const result = NotificationSchema.safeParse(raw);
      if (!result.success) return;
      const notification = result.data;
      updateCache(notification);
      toast.custom((id) => (
        <div
          className="cursor-pointer rounded-lg bg-white p-3 shadow-lg"
          onClick={() => { toast.dismiss(id); if (notification.deepLink) window.location.href = notification.deepLink; }}
        >
          <p className="text-sm font-medium">{notification.title}</p>
          <p className="text-xs text-gray-500">{notification.body}</p>
        </div>
      ), { duration: 5000 });
    });

    socket.on('notification:catch-up', (raws: unknown[]) => {
      const notifications = raws.flatMap((r) => {
        const result = NotificationSchema.safeParse(r);
        return result.success ? [result.data] : [];
      });
      notifications.forEach(updateCache);
    });

    socket.on('disconnect', () => {
      const delay = Math.min(RECONNECT_BASE_MS * Math.pow(RECONNECT_FACTOR, reconnectAttemptRef.current), RECONNECT_MAX_MS);
      reconnectAttemptRef.current += 1;
      reconnectTimerRef.current = setTimeout(connect, delay);
    });

    socketRef.current = socket;
  }, [token, updateCache]);

  useEffect(() => {
    connect();
    return () => {
      if (reconnectTimerRef.current) clearTimeout(reconnectTimerRef.current);
      socketRef.current?.disconnect();
      socketRef.current = null;
    };
  }, [connect]);
}
```

- [ ] 2. Modify `fe/src/App.tsx` — add `useNotificationSocket()` to `BackgroundEffects`

```typescript
// In BackgroundEffects component, add:
import { useNotificationSocket } from './app/hooks/use-notification-socket';

function BackgroundEffects() {
  useMessagingSocket();
  useNotificationSocket(); // ADD THIS LINE
  return null;
}
```

- [ ] 3. Create `fe/src/app/hooks/__tests__/use-notification-socket.spec.ts`

```typescript
import { renderHook } from '@testing-library/react';
import { vi, describe, it, expect, beforeEach } from 'vitest';
import { useNotificationSocket } from '../use-notification-socket';

vi.mock('socket.io-client', () => ({
  io: vi.fn(() => ({
    connected: false,
    on: vi.fn(),
    disconnect: vi.fn(),
  })),
}));

vi.mock('../stores/auth.store', () => ({
  useAuthStore: vi.fn((selector: (s: { token: string }) => string) => selector({ token: 'test-token' })),
}));

vi.mock('@tanstack/react-query', () => ({
  useQueryClient: vi.fn(() => ({ setQueryData: vi.fn(), invalidateQueries: vi.fn() })),
}));

describe('useNotificationSocket', () => {
  it('calls io() with token when token is present', async () => {
    const { io } = await import('socket.io-client');
    renderHook(() => useNotificationSocket());
    expect(io).toHaveBeenCalledWith(expect.any(String), expect.objectContaining({ query: { token: 'test-token' } }));
  });

  it('does not call io() when token is absent', async () => {
    const { useAuthStore } = await import('../stores/auth.store');
    (useAuthStore as any).mockImplementation((sel: any) => sel({ token: null }));
    const { io } = await import('socket.io-client');
    (io as any).mockClear();
    renderHook(() => useNotificationSocket());
    expect(io).not.toHaveBeenCalled();
  });
});
```

- [ ] 4. Run tests

```bash
cd fe
npx vitest run src/app/hooks/__tests__/use-notification-socket.spec.ts
# Expected: 2 tests pass
```

**Commit:** `feat(fe): add useNotificationSocket hook with exponential backoff and cache integration`

---

### Task 14: FE — Enhanced NotificationBell

**Files:**
- Modify: `fe/src/app/components/notification-bell.tsx`
- Create: `fe/src/app/components/notifications/notification-icon.tsx`
- Modify: `fe/src/app/hooks/use-notifications.ts` (remove polling)
- Test: `fe/src/app/components/__tests__/notification-bell.spec.tsx`

**Steps:**

- [ ] 1. Create `fe/src/app/components/notifications/notification-icon.tsx`

```typescript
import {
  IconShoppingCart, IconTruck, IconCheck, IconX, IconCreditCard,
  IconAlertTriangle, IconTag, IconTicket, IconShield, IconStar,
  IconBell, IconSpeakerphone,
} from '@tabler/icons-react';
import { NotificationType } from '../../types/api/notification';

const ICON_MAP: Record<NotificationType, React.ComponentType<{ size?: number; className?: string }>> = {
  ORDER_PLACED: IconShoppingCart,
  ORDER_CONFIRMED: IconCheck,
  ORDER_SHIPPED: IconTruck,
  ORDER_DELIVERED: IconCheck,
  ORDER_CANCELLED: IconX,
  PAYMENT_SUCCESS: IconCreditCard,
  PAYMENT_FAILED: IconAlertTriangle,
  PROMOTION_FLASH_SALE: IconTag,
  PROMOTION_COUPON: IconTicket,
  ACCOUNT_SECURITY: IconShield,
  REVIEW_REPLY: IconStar,
  SYSTEM_ANNOUNCEMENT: IconSpeakerphone,
};

const COLOR_MAP: Record<NotificationType, string> = {
  ORDER_PLACED: 'text-blue-500',
  ORDER_CONFIRMED: 'text-green-500',
  ORDER_SHIPPED: 'text-indigo-500',
  ORDER_DELIVERED: 'text-green-600',
  ORDER_CANCELLED: 'text-red-500',
  PAYMENT_SUCCESS: 'text-green-500',
  PAYMENT_FAILED: 'text-red-500',
  PROMOTION_FLASH_SALE: 'text-orange-500',
  PROMOTION_COUPON: 'text-purple-500',
  ACCOUNT_SECURITY: 'text-yellow-500',
  REVIEW_REPLY: 'text-amber-500',
  SYSTEM_ANNOUNCEMENT: 'text-gray-500',
};

interface NotificationIconProps {
  type: NotificationType;
  size?: number;
}

export function NotificationIcon({ type, size = 16 }: NotificationIconProps) {
  const Icon = ICON_MAP[type] ?? IconBell;
  const colorClass = COLOR_MAP[type] ?? 'text-gray-400';
  return <Icon size={size} className={colorClass} />;
}
```

- [ ] 2. Update `fe/src/app/hooks/use-notifications.ts` — remove polling

```typescript
// Remove refetchInterval: 30_000 from useQuery options
// Before:
// useQuery({ queryKey: ['notifications'], queryFn: ..., refetchInterval: 30_000 })
// After:
// useQuery({ queryKey: ['notifications'], queryFn: ... })
// WebSocket hook handles real-time updates instead
```

- [ ] 3. Update `fe/src/app/components/notification-bell.tsx`

```typescript
import { useState, useRef, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { IconBell } from '@tabler/icons-react';
import { formatDistanceToNow, isToday, isYesterday } from 'date-fns';
import { vi } from 'date-fns/locale';
import { useNotifications } from '../hooks/use-notifications';
import { NotificationIcon } from './notifications/notification-icon';
import { Notification } from '../types/api/notification';
import { cn } from '../lib/utils';

function groupByDate(items: Notification[]): { label: string; items: Notification[] }[] {
  const groups: Record<string, Notification[]> = {};
  for (const item of items) {
    const d = new Date(item.createdAt);
    const key = isToday(d) ? 'Hôm nay' : isYesterday(d) ? 'Hôm qua' : 'Trước đó';
    (groups[key] ??= []).push(item);
  }
  return ['Hôm nay', 'Hôm qua', 'Trước đó']
    .filter((k) => groups[k]?.length)
    .map((label) => ({ label, items: groups[label] }));
}

export function NotificationBell() {
  const [open, setOpen] = useState(false);
  const [hasNew, setHasNew] = useState(false);
  const ref = useRef<HTMLDivElement>(null);
  const navigate = useNavigate();
  const { notifications, unreadCount, markRead, markAllRead } = useNotifications();

  const displayed = notifications.slice(0, 10);
  const groups = groupByDate(displayed);

  // Pulse on new notification
  useEffect(() => {
    if (unreadCount > 0) { setHasNew(true); }
  }, [unreadCount]);

  // Close on outside click
  useEffect(() => {
    function handler(e: MouseEvent) {
      if (ref.current && !ref.current.contains(e.target as Node)) setOpen(false);
    }
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  return (
    <div ref={ref} className="relative">
      <button
        aria-label="Thông báo"
        onClick={() => { setOpen((o) => !o); setHasNew(false); }}
        className="relative p-2 rounded-full hover:bg-gray-100 transition-colors"
      >
        <IconBell
          size={22}
          className={cn('text-gray-600', hasNew && 'animate-pulse text-blue-500')}
        />
        {unreadCount > 0 && (
          <span className="absolute -top-0.5 -right-0.5 flex h-4 w-4 items-center justify-center rounded-full bg-red-500 text-[10px] font-bold text-white">
            {unreadCount > 99 ? '99+' : unreadCount}
          </span>
        )}
      </button>

      {open && (
        <div className="absolute right-0 top-full mt-2 w-80 rounded-xl border border-gray-200 bg-white shadow-xl z-50">
          <div className="flex items-center justify-between px-4 py-3 border-b border-gray-100">
            <span className="font-semibold text-sm text-gray-800">Thông báo</span>
            {unreadCount > 0 && (
              <button onClick={() => markAllRead()} className="text-xs text-blue-500 hover:underline">
                Đánh dấu tất cả đã đọc
              </button>
            )}
          </div>

          <div className="max-h-96 overflow-y-auto">
            {groups.length === 0 && (
              <p className="py-8 text-center text-sm text-gray-400">Không có thông báo</p>
            )}
            {groups.map(({ label, items }) => (
              <div key={label}>
                <p className="px-4 py-1.5 text-xs font-medium text-gray-400 bg-gray-50">{label}</p>
                {items.map((n) => (
                  <button
                    key={n.id}
                    onClick={() => { markRead(n.id); if (n.deepLink) navigate(n.deepLink); setOpen(false); }}
                    className={cn('flex w-full gap-3 px-4 py-3 text-left hover:bg-gray-50 transition-colors', !n.isRead && 'bg-blue-50/40')}
                  >
                    <div className="mt-0.5 shrink-0">
                      <NotificationIcon type={n.type} size={16} />
                    </div>
                    <div className="min-w-0 flex-1">
                      <p className={cn('text-sm truncate', !n.isRead && 'font-medium')}>{n.title}</p>
                      <p className="text-xs text-gray-500 line-clamp-2">{n.body}</p>
                      <p className="mt-0.5 text-[11px] text-gray-400">
                        {formatDistanceToNow(new Date(n.createdAt), { addSuffix: true, locale: vi })}
                      </p>
                    </div>
                    {!n.isRead && <span className="mt-1.5 h-2 w-2 shrink-0 rounded-full bg-blue-500" />}
                  </button>
                ))}
              </div>
            ))}
          </div>

          <div className="border-t border-gray-100 px-4 py-2.5">
            <button onClick={() => { navigate('/notifications'); setOpen(false); }} className="w-full text-center text-xs text-blue-500 hover:underline">
              Xem tất cả
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
```

- [ ] 4. Create `fe/src/app/components/__tests__/notification-bell.spec.tsx`

```typescript
import { render, screen, fireEvent } from '@testing-library/react';
import { vi, describe, it, expect } from 'vitest';
import { NotificationBell } from '../notification-bell';
import { MemoryRouter } from 'react-router-dom';

vi.mock('../hooks/use-notifications', () => ({
  useNotifications: () => ({
    notifications: [
      { id: '1', type: 'ORDER_PLACED', title: 'Order placed', body: 'Body', isRead: false, createdAt: new Date().toISOString(), priority: 'HIGH', deliveryStatus: 'PENDING', userId: 'u1', metadata: {} },
    ],
    unreadCount: 1,
    markRead: vi.fn(),
    markAllRead: vi.fn(),
  }),
}));

describe('NotificationBell', () => {
  it('shows unread badge', () => {
    render(<MemoryRouter><NotificationBell /></MemoryRouter>);
    expect(screen.getByText('1')).toBeInTheDocument();
  });

  it('opens dropdown on click', () => {
    render(<MemoryRouter><NotificationBell /></MemoryRouter>);
    fireEvent.click(screen.getByRole('button', { name: /thông báo/i }));
    expect(screen.getByText('Order placed')).toBeInTheDocument();
  });
});
```

- [ ] 5. Run tests

```bash
cd fe
npx vitest run src/app/components/__tests__/notification-bell.spec.tsx
# Expected: 2 tests pass
```

**Commit:** `feat(fe): enhance NotificationBell with date grouping, type icons, mark-all-read, and bell pulse`

---

### Task 15: FE — Notification Toast Component

**Files:**
- Create: `fe/src/app/components/notification-toast.tsx`
- Test: `fe/src/app/components/__tests__/notification-toast.spec.tsx`

**Steps:**

- [ ] 1. Create `fe/src/app/components/notification-toast.tsx`

```typescript
import { useNavigate } from 'react-router-dom';
import { toast } from 'sonner';
import { NotificationIcon } from './notifications/notification-icon';
import { Notification } from '../types/api/notification';

interface NotificationToastProps {
  notification: Notification;
  toastId: string | number;
}

export function NotificationToast({ notification, toastId }: NotificationToastProps) {
  const navigate = useNavigate();

  function handleClick() {
    toast.dismiss(toastId);
    if (notification.deepLink) navigate(notification.deepLink);
  }

  return (
    <button
      onClick={handleClick}
      className="flex w-full max-w-sm gap-3 rounded-lg bg-white p-3 shadow-lg border border-gray-100 text-left hover:bg-gray-50 transition-colors"
    >
      <div className="mt-0.5 shrink-0">
        <NotificationIcon type={notification.type} size={18} />
      </div>
      <div className="min-w-0 flex-1">
        <p className="text-sm font-medium text-gray-900 truncate">{notification.title}</p>
        <p className="text-xs text-gray-500 line-clamp-2">{notification.body}</p>
      </div>
    </button>
  );
}

export function showNotificationToast(notification: Notification) {
  toast.custom(
    (id) => <NotificationToast notification={notification} toastId={id} />,
    { duration: 5000 },
  );
}
```

- [ ] 2. Ensure Sonner `<Toaster />` has `richColors` and `position` set in `App.tsx` (already mounted — just verify):

```typescript
// In App.tsx, confirm:
<Toaster position="top-right" richColors closeButton />
```

- [ ] 3. Update `use-notification-socket.ts` to use `showNotificationToast` instead of inline `toast.custom`:

```typescript
// Replace the inline toast.custom block in the 'notification:new' handler with:
import { showNotificationToast } from '../components/notification-toast';
// ...
socket.on('notification:new', (raw: unknown) => {
  const result = NotificationSchema.safeParse(raw);
  if (!result.success) return;
  updateCache(result.data);
  showNotificationToast(result.data);
});
```

- [ ] 4. Create `fe/src/app/components/__tests__/notification-toast.spec.tsx`

```typescript
import { render, screen, fireEvent } from '@testing-library/react';
import { vi, describe, it, expect } from 'vitest';
import { NotificationToast } from '../notification-toast';
import { MemoryRouter } from 'react-router-dom';

vi.mock('sonner', () => ({ toast: { dismiss: vi.fn() } }));

const mockNavigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual('react-router-dom');
  return { ...actual, useNavigate: () => mockNavigate };
});

const notification = {
  id: '1', userId: 'u1', type: 'ORDER_PLACED' as const, title: 'Order placed', body: 'Your order is placed.',
  priority: 'HIGH' as const, deliveryStatus: 'PENDING' as const, metadata: {}, createdAt: new Date().toISOString(),
  isRead: false, deepLink: '/orders/1',
};

describe('NotificationToast', () => {
  it('renders title and body', () => {
    render(<MemoryRouter><NotificationToast notification={notification} toastId="t1" /></MemoryRouter>);
    expect(screen.getByText('Order placed')).toBeInTheDocument();
    expect(screen.getByText('Your order is placed.')).toBeInTheDocument();
  });

  it('navigates to deepLink on click', () => {
    render(<MemoryRouter><NotificationToast notification={notification} toastId="t1" /></MemoryRouter>);
    fireEvent.click(screen.getByRole('button'));
    expect(mockNavigate).toHaveBeenCalledWith('/orders/1');
  });
});
```

- [ ] 5. Run tests

```bash
cd fe
npx vitest run src/app/components/__tests__/notification-toast.spec.tsx
# Expected: 2 tests pass
```

**Commit:** `feat(fe): add NotificationToast component with deepLink navigation and auto-dismiss`

---

### Task 16: FE — Notifications Page

**Files:**
- Create: `fe/src/app/components/notifications/notifications-page.tsx`
- Create: `fe/src/app/components/notifications/notification-filters.tsx`
- Create: `fe/src/app/components/notifications/notification-thread-list.tsx`
- Create: `fe/src/app/components/notifications/notification-thread.tsx`
- Create: `fe/src/app/components/notifications/notification-item.tsx`
- Create: `fe/src/app/components/notifications/notification-pagination.tsx`
- Create: `fe/src/pages/NotificationsPage.tsx`
- Modify: `fe/src/routes.ts`
- Test: `fe/src/app/components/notifications/__tests__/notifications-page.spec.tsx`

**Steps:**

- [ ] 1. Create `notification-filters.tsx`

```typescript
import { useSearchParams } from 'react-router-dom';
import { NotificationType } from '../../types/api/notification';
import { cn } from '../../lib/utils';

const TABS: { label: string; value: string }[] = [
  { label: 'Tất cả', value: '' },
  { label: 'Đơn hàng', value: 'ORDER_PLACED,ORDER_CONFIRMED,ORDER_SHIPPED,ORDER_DELIVERED,ORDER_CANCELLED' },
  { label: 'Thanh toán', value: 'PAYMENT_SUCCESS,PAYMENT_FAILED' },
  { label: 'Khuyến mãi', value: 'PROMOTION_FLASH_SALE,PROMOTION_COUPON' },
  { label: 'Tài khoản', value: 'ACCOUNT_SECURITY' },
];

export function NotificationFilters() {
  const [params, setParams] = useSearchParams();
  const current = params.get('type') ?? '';

  function select(value: string) {
    setParams((p) => { const next = new URLSearchParams(p); if (value) next.set('type', value); else next.delete('type'); next.set('page', '1'); return next; });
  }

  return (
    <div className="flex gap-1 overflow-x-auto pb-1">
      {TABS.map((tab) => (
        <button
          key={tab.value}
          onClick={() => select(tab.value)}
          className={cn('shrink-0 rounded-full px-4 py-1.5 text-sm font-medium transition-colors', current === tab.value ? 'bg-blue-500 text-white' : 'bg-gray-100 text-gray-600 hover:bg-gray-200')}
        >
          {tab.label}
        </button>
      ))}
    </div>
  );
}
```

- [ ] 2. Create `notification-item.tsx`

```typescript
import { formatDistanceToNow } from 'date-fns';
import { vi } from 'date-fns/locale';
import { useNavigate } from 'react-router-dom';
import { Notification } from '../../types/api/notification';
import { NotificationIcon } from './notification-icon';
import { cn } from '../../lib/utils';

interface NotificationItemProps {
  notification: Notification;
  onMarkRead: (id: string) => void;
}

export function NotificationItem({ notification, onMarkRead }: NotificationItemProps) {
  const navigate = useNavigate();

  function handleClick() {
    if (!notification.isRead) onMarkRead(notification.id);
    if (notification.deepLink) navigate(notification.deepLink);
  }

  return (
    <button
      onClick={handleClick}
      className={cn('flex w-full gap-3 rounded-lg p-3 text-left transition-colors hover:bg-gray-50', !notification.isRead && 'bg-blue-50/40')}
    >
      <div className="mt-0.5 shrink-0 rounded-full bg-gray-100 p-2">
        <NotificationIcon type={notification.type} size={16} />
      </div>
      <div className="min-w-0 flex-1">
        <p className={cn('text-sm', !notification.isRead && 'font-medium')}>{notification.title}</p>
        <p className="mt-0.5 text-xs text-gray-500">{notification.body}</p>
        <p className="mt-1 text-[11px] text-gray-400">
          {formatDistanceToNow(new Date(notification.createdAt), { addSuffix: true, locale: vi })}
        </p>
      </div>
      {!notification.isRead && <span className="mt-2 h-2 w-2 shrink-0 rounded-full bg-blue-500" />}
    </button>
  );
}
```

- [ ] 3. Create `notification-thread.tsx`

```typescript
import { useState } from 'react';
import { IconChevronDown, IconChevronUp } from '@tabler/icons-react';
import { useQuery } from '@tanstack/react-query';
import { notificationsApi } from '../../lib/api/endpoints/notifications';
import { NotificationItem } from './notification-item';
import { NotificationThread as ThreadType } from '../../types/api/notification';

interface NotificationThreadProps {
  thread: ThreadType;
  onMarkRead: (id: string) => void;
}

export function NotificationThread({ thread, onMarkRead }: NotificationThreadProps) {
  const [expanded, setExpanded] = useState(false);

  const { data: items = [] } = useQuery({
    queryKey: ['notifications', 'thread', thread.threadId],
    queryFn: () => notificationsApi.getThreadNotifications(thread.threadId).then((r) => r.data),
    enabled: expanded,
  });

  return (
    <div className="rounded-lg border border-gray-200 overflow-hidden">
      <button
        onClick={() => setExpanded((e) => !e)}
        className="flex w-full items-center justify-between px-4 py-3 hover:bg-gray-50 transition-colors"
      >
        <div className="flex items-center gap-2">
          <span className="text-sm font-medium">{thread.threadTitle}</span>
          {thread.unreadCount > 0 && (
            <span className="rounded-full bg-blue-500 px-1.5 py-0.5 text-[10px] font-bold text-white">{thread.unreadCount}</span>
          )}
        </div>
        {expanded ? <IconChevronUp size={16} /> : <IconChevronDown size={16} />}
      </button>
      {expanded && (
        <div className="divide-y divide-gray-100 border-t border-gray-100">
          {items.map((n) => <NotificationItem key={n.id} notification={n} onMarkRead={onMarkRead} />)}
        </div>
      )}
    </div>
  );
}
```

- [ ] 4. Create `notification-thread-list.tsx`

```typescript
import { useQuery } from '@tanstack/react-query';
import { notificationsApi } from '../../lib/api/endpoints/notifications';
import { NotificationThread } from './notification-thread';

interface NotificationThreadListProps {
  onMarkRead: (id: string) => void;
}

export function NotificationThreadList({ onMarkRead }: NotificationThreadListProps) {
  const { data: threads = [], isLoading } = useQuery({
    queryKey: ['notifications', 'threads'],
    queryFn: () => notificationsApi.getThreads().then((r) => r.data),
  });

  if (isLoading) return <div className="py-8 text-center text-sm text-gray-400">Đang tải...</div>;
  if (threads.length === 0) return <div className="py-8 text-center text-sm text-gray-400">Không có luồng thông báo</div>;

  return (
    <div className="space-y-2">
      {threads.map((t) => <NotificationThread key={t.threadId} thread={t} onMarkRead={onMarkRead} />)}
    </div>
  );
}
```

- [ ] 5. Create `notification-pagination.tsx`

```typescript
import { useSearchParams } from 'react-router-dom';
import { IconChevronLeft, IconChevronRight } from '@tabler/icons-react';

interface NotificationPaginationProps {
  total: number;
  page: number;
  limit: number;
}

export function NotificationPagination({ total, page, limit }: NotificationPaginationProps) {
  const [, setParams] = useSearchParams();
  const totalPages = Math.ceil(total / limit);
  if (totalPages <= 1) return null;

  function goTo(p: number) {
    setParams((prev) => { const next = new URLSearchParams(prev); next.set('page', String(p)); return next; });
  }

  return (
    <div className="flex items-center justify-center gap-2 py-4">
      <button onClick={() => goTo(page - 1)} disabled={page <= 1} className="rounded p-1 hover:bg-gray-100 disabled:opacity-40">
        <IconChevronLeft size={16} />
      </button>
      <span className="text-sm text-gray-600">{page} / {totalPages}</span>
      <button onClick={() => goTo(page + 1)} disabled={page >= totalPages} className="rounded p-1 hover:bg-gray-100 disabled:opacity-40">
        <IconChevronRight size={16} />
      </button>
    </div>
  );
}
```

- [ ] 6. Create `notifications-page.tsx`

```typescript
import { useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { notificationsApi } from '../../lib/api/endpoints/notifications';
import { useNotifications } from '../../hooks/use-notifications';
import { NotificationFilters } from './notification-filters';
import { NotificationItem } from './notification-item';
import { NotificationThreadList } from './notification-thread-list';
import { NotificationPagination } from './notification-pagination';

export function NotificationsPage() {
  const [params] = useSearchParams();
  const type = params.get('type') ?? undefined;
  const page = parseInt(params.get('page') ?? '1', 10);
  const { markRead } = useNotifications();

  const { data, isLoading } = useQuery({
    queryKey: ['notifications', { type, page }],
    queryFn: () => notificationsApi.getNotifications({ type, page, limit: 20 }).then((r) => r.data),
  });

  return (
    <div className="mx-auto max-w-2xl px-4 py-6">
      <h1 className="mb-4 text-xl font-semibold text-gray-900">Thông báo</h1>
      <NotificationFilters />

      <div className="mt-4 space-y-1">
        {isLoading && <div className="py-8 text-center text-sm text-gray-400">Đang tải...</div>}
        {!isLoading && data?.items.length === 0 && (
          <div className="py-8 text-center text-sm text-gray-400">Không có thông báo</div>
        )}
        {data?.items.map((n) => <NotificationItem key={n.id} notification={n} onMarkRead={markRead} />)}
      </div>

      {data && <NotificationPagination total={data.total} page={data.page} limit={data.limit} />}

      <div className="mt-8">
        <h2 className="mb-3 text-base font-medium text-gray-700">Luồng thông báo</h2>
        <NotificationThreadList onMarkRead={markRead} />
      </div>
    </div>
  );
}
```

- [ ] 7. Create `fe/src/pages/NotificationsPage.tsx`

```typescript
import { NotificationsPage } from '../app/components/notifications/notifications-page';

export default function NotificationsPageRoute() {
  return <NotificationsPage />;
}
```

- [ ] 8. Add route to `fe/src/routes.ts`

```typescript
// Add to createBrowserRouter routes array:
{
  path: '/notifications',
  lazy: () => import('./pages/NotificationsPage').then((m) => ({ Component: m.default })),
},
```

- [ ] 9. Create `__tests__/notifications-page.spec.tsx`

```typescript
import { render, screen } from '@testing-library/react';
import { vi, describe, it, expect } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { NotificationsPage } from '../notifications-page';

vi.mock('../../lib/api/endpoints/notifications', () => ({
  notificationsApi: {
    getNotifications: vi.fn().mockResolvedValue({ data: { items: [], total: 0, page: 1, limit: 20 } }),
    getThreads: vi.fn().mockResolvedValue({ data: [] }),
  },
}));

vi.mock('../../hooks/use-notifications', () => ({
  useNotifications: () => ({ markRead: vi.fn(), unreadCount: 0, notifications: [], markAllRead: vi.fn() }),
}));

describe('NotificationsPage', () => {
  it('renders heading', () => {
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    render(
      <QueryClientProvider client={qc}>
        <MemoryRouter><NotificationsPage /></MemoryRouter>
      </QueryClientProvider>,
    );
    expect(screen.getByText('Thông báo')).toBeInTheDocument();
  });
});
```

- [ ] 10. Run tests

```bash
cd fe
npx vitest run src/app/components/notifications/__tests__/notifications-page.spec.tsx
# Expected: 1 test passes
```

**Commit:** `feat(fe): add /notifications page with filters, threads, pagination, and URL-driven state`

---

### Task 17: Integration Testing + Cleanup

**Files:**
- Create: `services/notification-service/src/notification/__tests__/e2e/notification-pipeline.e2e.spec.ts`
- Modify: `docker-compose.yml` (remove postgres from notification-service)
- Delete: `services/notification-service/src/notification/infrastructure/persistence/notification.entity.ts` (old MikroORM entity)
- Delete: `services/notification-service/src/notification/infrastructure/persistence/mikro-orm-notification.repository.ts` (old repo)
- Modify: `k8s/notification-service/deployment.yaml` (update env vars)

**Steps:**

- [ ] 1. Create E2E test `notification-pipeline.e2e.spec.ts`

```typescript
import { Test } from '@nestjs/testing';
import { INestApplication } from '@nestjs/common';
import { MongoMemoryServer } from 'mongodb-memory-server';
import { io, Socket } from 'socket.io-client';
import { ConfigModule } from '@nestjs/config';
import { MongooseModule } from '@nestjs/mongoose';
import { SocketIoAdapter } from '@nestjs/platform-socket.io';
import { JwtService } from '@nestjs/jwt';
import RedisMock from 'ioredis-mock';
import { AppModule } from '../../app.module';
import { REDIS_CLIENT } from '../infrastructure/cache/redis.module';
import { SendNotificationUseCase } from '../application/command/send-notification.use-case';
import { NotificationType } from '../domain/model/notification-type.enum';

describe('Notification Pipeline E2E', () => {
  let app: INestApplication;
  let mongod: MongoMemoryServer;
  let client: Socket;
  let jwt: JwtService;

  beforeAll(async () => {
    mongod = await MongoMemoryServer.create();

    const module = await Test.createTestingModule({
      imports: [
        ConfigModule.forRoot({ isGlobal: true, load: [() => ({ port: 0, mongodb: { uri: mongod.getUri(), dbName: 'test' }, redis: { host: 'localhost', port: 6379, ttlSeconds: 86400 }, kafka: { brokers: ['localhost:9092'], groupId: 'test' }, jwt: { secret: 'test-secret' } })] }),
        MongooseModule.forRoot(mongod.getUri()),
        AppModule,
      ],
    })
      .overrideProvider(REDIS_CLIENT).useValue(new RedisMock())
      .compile();

    app = module.createNestApplication();
    app.useWebSocketAdapter(new SocketIoAdapter(app));
    await app.listen(0);

    jwt = module.get(JwtService);
    const token = jwt.sign({ sub: 'e2e-user' });
    const port = (app.getHttpServer().address() as { port: number }).port;

    client = io(`http://localhost:${port}/ws/notifications`, { query: { token }, transports: ['websocket'] });
    await new Promise<void>((resolve) => client.on('connect', resolve));
  });

  afterAll(async () => { client.disconnect(); await app.close(); await mongod.stop(); });

  it('sends notification via use case and receives it over WebSocket', async () => {
    const sendUseCase = app.get(SendNotificationUseCase);

    const received = new Promise<unknown>((resolve) => client.on('notification:new', resolve));

    await sendUseCase.execute({
      userId: 'e2e-user',
      type: NotificationType.ORDER_PLACED,
      title: 'E2E Test',
      body: 'Pipeline works',
      metadata: { orderId: 'E2E-001' },
      idempotencyKey: `e2e-${Date.now()}`,
    });

    const payload = await Promise.race([received, new Promise((_, reject) => setTimeout(() => reject(new Error('timeout')), 3000))]);
    expect((payload as any).title).toBe('E2E Test');
    expect((payload as any).userId).toBe('e2e-user');
  });
});
```

- [ ] 2. Remove postgres dependency from notification-service in `docker-compose.yml`

```yaml
# Remove or comment out from notification-service service:
#   depends_on:
#     - postgres   # REMOVE THIS LINE
# Remove postgres env vars from notification-service environment block
```

- [ ] 3. Delete old MikroORM files

```bash
# Delete these files if they exist:
rm services/notification-service/src/notification/infrastructure/persistence/notification.entity.ts
rm services/notification-service/src/notification/infrastructure/persistence/mikro-orm-notification.repository.ts
rm services/notification-service/mikro-orm.config.ts
```

- [ ] 4. Update `k8s/notification-service/deployment.yaml` — replace postgres env vars with MongoDB/Redis

```yaml
env:
  - name: MONGODB_URI
    valueFrom:
      secretKeyRef:
        name: notification-service-secrets
        key: mongodb-uri
  - name: MONGODB_DB_NAME
    value: notifications
  - name: REDIS_HOST
    valueFrom:
      configMapKeyRef:
        name: notification-service-config
        key: redis-host
  - name: REDIS_PORT
    value: "6379"
  # Remove DATABASE_HOST, DATABASE_PORT, DATABASE_NAME, DATABASE_USER, DATABASE_PASSWORD
```

- [ ] 5. Run full test suite

```bash
cd services/notification-service
npx jest --no-coverage --forceExit
# Expected: all tests pass

cd fe
npx vitest run
# Expected: all tests pass
```

- [ ] 6. Final smoke test with docker compose

```bash
docker compose up mongo redis notification-service -d
# Wait for service to start
curl -s http://localhost:3004/health
# Expected: {"status":"ok"}
```

**Commit:** `feat(notification-service): E2E pipeline test, remove MikroORM, update k8s manifests`

---

