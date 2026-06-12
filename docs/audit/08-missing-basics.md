# 08 — Missing Basics

> Features that any production e-commerce platform needs but this project lacks.
> Not bugs — just incomplete implementation.

---

## MISS-01: No Pagination on List Endpoints

**Affected services and files:**

| Service | Endpoint | File |
|---------|----------|------|
| coupon-service | GET /coupons, GET /admin/coupons | `CouponController.java:57-65` |
| user-service | GET /admin/users/search | `AdminUserController.java:25-34` |
| user-service | GET /admin/sellers/pending | `ListPendingSellersUseCase.java:16-18` |
| seller-finance-service | GET /seller/payouts | `SellerFinanceController.java:49-52` |
| invoice-service | GET /seller/{id}/invoices | `InvoiceController.java:41-47` |
| notification-service | GET /notifications (by thread) | `MongoNotificationRepository.java:141-152` |
| product-service | GET /reviews, GET /questions | `ReviewController.java:43-44` |
| order-service | GET /admin/orders | `AdminOrderController.java:30-35` |
| order-service | GET /seller/orders/pending | `SellerOrderController.java:43-48` |

**What's wrong:**  
All these endpoints call `findAll()` or equivalent, loading EVERY record into memory. No `Pageable` parameter, no `page`/`size` query params, no limit.

**What happens at scale:**  
With 10,000 coupons or 50,000 orders, these endpoints cause OOM errors or 30+ second response times.

**Fix pattern (Spring Boot):**
```java
@GetMapping
public ResponseEntity<Page<CouponDto>> list(
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size) {
    size = Math.min(size, 100); // hard cap
    Page<CouponDto> result = couponRepo.findAll(PageRequest.of(page, size));
    return ResponseEntity.ok(result);
}
```

**Fix pattern (NestJS):**
```typescript
@Get()
async list(@Query('page') page = 0, @Query('size') size = 20) {
  size = Math.min(size, 100);
  return this.repo.find({ skip: page * size, take: size });
}
```

---

## MISS-02: No Audit Trail (createdBy/updatedBy)

**Affected services:**
- coupon-service — who created/deactivated a coupon?
- seller-finance-service — who approved/failed a payout?
- invoice-service — who submitted to GDT?

**What's wrong:**  
No `@CreatedBy`, `@LastModifiedBy`, `@CreatedDate`, `@LastModifiedDate` annotations. When an admin performs a destructive action, there's no record of who did it.

**Fix (Spring Boot JPA Auditing):**
```java
@EntityListeners(AuditingEntityListener.class)
public class CouponJpaEntity {
    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;
}
```
Plus `@EnableJpaAuditing` and an `AuditorAware<String>` bean that reads from SecurityContext.

---

## MISS-03: No Soft Delete for Coupons

**Service:** coupon-service  
**File:** `services/coupon-service/src/main/java/com/vnshop/couponservice/domain/Coupon.java`  
**Lines:** 137-139

**What's wrong:**  
`deactivate()` sets `active=false` but there's no delete endpoint and no soft-delete (`deleted_at`) column. Deactivated coupons remain in the database and admin list forever.

**Impact:** Admin list grows unbounded. `findAll()` performance degrades over time.

**Fix:**  
Add `deletedAt` column + `@Where(clause = "deleted_at IS NULL")` for default queries. Add an archive endpoint for cleanup.

---

## MISS-04: Notification Retry Is a No-Op Placeholder

**Service:** notification-service  
**File:** `services/notification-service/src/notification/application/command/retry-failed-deliveries.use-case.ts`  
**Lines:** 29-35

**What's wrong:**  
The retry use case exists but its implementation is empty — failed notifications are never retried. SMS and email delivery failures are permanent.

**Fix:**  
Implement exponential backoff retry:
```typescript
async execute(): Promise<void> {
  const failed = await this.repo.findByStatus('FAILED', { maxRetries: 3 });
  for (const notification of failed) {
    const delay = Math.pow(2, notification.retryCount) * 1000;
    await this.queue.add('retry-notification', { id: notification.id }, { delay });
  }
}
```

---

## MISS-05: No User Blocking in Messaging

**Service:** messaging-service  
**File:** `services/messaging-service/src/messaging/application/send-message.use-case.ts`  
**Lines:** 34-81

**What's wrong:**  
Service description lists "blocking" as a feature, but no block mechanism exists. Any user can message any other user indefinitely with no way to stop harassment.

**Fix:**  
1. Add `blocked_users` table: `(blocker_id, blocked_id, created_at)`
2. In `SendMessageUseCase`, check before sending:
```typescript
const isBlocked = await this.blockRepo.exists(recipientId, senderId);
if (isBlocked) throw new ForbiddenException('You are blocked by this user');
```
3. Add `POST /threads/:id/block` and `DELETE /threads/:id/block` endpoints.

---

## MISS-06: No File Attachments in Messaging

**Service:** messaging-service  
**File:** `services/messaging-service/src/messaging/domain/message.ts`  
**Lines:** 1-37

**What's wrong:**  
Message entity has only `body: string`. No attachment support despite being listed in service responsibilities.

**Fix:**  
Add attachments field and upload endpoint:
```typescript
// Domain:
attachments?: Array<{ url: string; filename: string; mimeType: string; sizeBytes: number }>;

// Controller:
@Post(':threadId/messages/:messageId/attachments')
@UseInterceptors(FileInterceptor('file', { limits: { fileSize: 10_000_000 } }))
async uploadAttachment(@UploadedFile() file: Express.Multer.File) { ... }
```

---

## MISS-07: No Rate Limiting on Message Sending

**Service:** messaging-service  
**File:** `services/messaging-service/src/messaging/application/send-message.use-case.ts`

**What's wrong:**  
No per-user rate limiting. A user (or bot) can send thousands of messages per second, flooding recipients and overwhelming the database.

**Fix:**  
Add a rate limiter (Redis-backed):
```typescript
const key = `msg-rate:${senderId}`;
const count = await redis.incr(key);
if (count === 1) await redis.expire(key, 60); // 60-second window
if (count > 30) throw new TooManyRequestsException('Message rate limit exceeded');
```

---

## MISS-08: No WebSocket Connection Limit Per User

**Services:** messaging-service, notification-service  
**Files:**  
- `messaging-ws.gateway.ts:117-125`
- `socketio-notification.gateway.ts`

**What's wrong:**  
No limit on how many WebSocket connections a single user can open. A malicious user opens 10,000 connections, exhausting server memory.

**Fix:**
```typescript
const MAX_CONNECTIONS_PER_USER = 5;
const userConnections = this.connectionStore.countByUserId(userId);
if (userConnections >= MAX_CONNECTIONS_PER_USER) {
  socket.disconnect(true);
  return;
}
```

---

## MISS-09: Shipping Service Is Stateless — No Persistence

**Service:** shipping-service  
**File:** `services/shipping-service/src/main/resources/db/migration/V1__initial_schema.sql`  
**Line:** 1

**What's wrong:**  
The migration file exists but the `shipments` table is never actually populated. The service generates tracking codes in gRPC responses but never stores them. Tracking queries hit the carrier API directly with no local cache.

**Impact:**  
- Generated tracking codes cannot be looked up later
- No shipment history
- GDPR listener references non-existent `shipments` table → runtime SQL exceptions

**Fix:**  
Implement the persistence layer: entity, repository, and save on creation.

---

## MISS-10: ddl-auto: update in Production (Coupon Service)

**Service:** coupon-service  
**File:** `services/coupon-service/src/main/resources/application.yml`  
**Line:** 19

**What's wrong:**  
`spring.jpa.hibernate.ddl-auto: update` means Hibernate auto-applies schema changes. In production this can lose data (column type changes), cannot be rolled back, and conflicts with Flyway (which is a dependency but has no migration files — only `.gitkeep`).

**Fix:**  
Set `ddl-auto: validate` (fail-fast if schema doesn't match) and write proper Flyway migrations:
```yaml
spring:
  jpa:
    hibernate:
      ddl-auto: validate
  flyway:
    enabled: true
```

---

## MISS-11: No Dead-Letter Queue on Kafka Consumers

**Affected services:**
- notification-service (`kafka-event.consumer.ts:295-303`) — swallows all exceptions
- search-service (`ProductEventConsumer.java`) — crashes on poison pills
- recommendations-service (`OrderEventListener.java:60-64`) — silently drops events

**What's wrong:**  
When a Kafka message cannot be processed (malformed payload, business logic exception), it's either:
- Silently swallowed (notification, recommendations) → permanent data loss
- Thrown as RuntimeException (search, inventory) → infinite retry loop

**Fix:**  
Use Spring Kafka's `@RetryableTopic` with DLT:
```java
@RetryableTopic(attempts = "3", backoff = @Backoff(delay = 5000, multiplier = 2))
@KafkaListener(topics = "product.updated")
public void onProductUpdated(ProductEvent event) { ... }

@DltHandler
public void handleDlt(ProductEvent event) {
    log.error("Permanent failure processing event: {}", event);
    deadLetterRepo.save(new DeadLetter(event));
    alertService.notifyOps("DLT event", event);
}
```

---

## MISS-12: No HTTP Client Timeouts in Multiple Services

**Affected:**
- invoice-service (`RestTemplateConfig.java:10-14`) — GDT API call can hang forever
- cart-service (`product-http-client.adapter.ts:91-115`) — no timeout on product-service call
- shipping-service (`RestCarrierHttpClient.java:19-37`) — no timeout/retry/circuit breaker

**Fix (Spring Boot):**
```java
@Bean
public RestTemplate restTemplate() {
    var factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofSeconds(5));
    factory.setReadTimeout(Duration.ofSeconds(10));
    return new RestTemplate(factory);
}
```

**Fix (NestJS/fetch):**
```typescript
const controller = new AbortController();
const timeout = setTimeout(() => controller.abort(), 10_000);
const response = await fetch(url, { signal: controller.signal });
clearTimeout(timeout);
```

---

## MISS-13: Processed Events Tables Grow Unbounded

**Services:** search-service, recommendations-service  
**Files:**  
- `services/search-service/src/main/resources/db/migration/V2__processed_events.sql`
- `services/recommendations-service/src/main/resources/db/migration/V1__co_purchases.sql:19-23`

**What's wrong:**  
`processed_events` / `processed_orders` tables track idempotency but have no TTL or cleanup. They grow forever.

**Fix:**  
Add a scheduled cleanup job:
```java
@Scheduled(cron = "0 0 3 * * *") // daily at 3am
public void cleanupProcessedEvents() {
    processedEventRepo.deleteByCreatedAtBefore(Instant.now().minus(7, ChronoUnit.DAYS));
}
```
Or add a `created_at` column with a partial index for recent lookups only.
