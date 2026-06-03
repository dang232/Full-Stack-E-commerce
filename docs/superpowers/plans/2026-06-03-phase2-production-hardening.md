# Phase 2: Production Hardening — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` to implement this plan. Dispatch one fresh subagent per task, review between tasks. Tasks 2, 8 are quick wins — run in parallel first. Then Tasks 3, 4, 5, 9, 10. Tasks 1, 6, 7 touch many services — best parallelized with subagents. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Harden all services for horizontal scalability, fault isolation, and operational visibility — ensuring the platform survives real traffic, recovers from failure gracefully, and surfaces problems before users notice.

**Architecture:** Each task targets a specific resilience or operability gap. Tasks are independent unless noted. Changes span infrastructure config (application.yml), JPA entity annotations, Spring AOP/Kafka config, and new Resilience4j wiring. No domain logic or API contract changes.

**Tech Stack:** Spring Boot 4.0.6, Java 25, Resilience4j 2.2, HikariCP, Flyway, Logback + logstash-logback-encoder, Micrometer/Prometheus, Spring Kafka, OpenTelemetry

---

## File Map

| Task | Files Created/Modified |
|------|----------------------|
| 1 - Kafka DLQ | All 10 `@KafkaListener` classes across 5 services + each service `application.yml` |
| 2 - HikariCP tuning | 10 service `application.yml` files |
| 3 - EAGER → LAZY | `OrderJpaEntity.java`, `SubOrderJpaEntity.java`, `OrderJpaRepository.java` |
| 4 - Outbox SKIP LOCKED | `OutboxEventSpringDataRepository.java`, `OutboxPublisher.java` |
| 5 - Payment outbox backoff | `PaymentCallbackOutboxRelay.java`, `PaymentOutboxEntity.java` |
| 6 - Structured logging | `logback-spring.xml` (create in 11 services), each service `pom.xml` |
| 7 - Kafka trace propagation | Each service Kafka config class or `application.yml` |
| 8 - Health probes | 9 service `application.yml` files (user-service already done) |
| 9 - gRPC circuit breakers | `GrpcClientConfig.java`, new `GrpcCircuitBreakerConfig.java`, 3 adapter files |
| 10 - Custom metrics | New `OrderMetrics.java`, `PaymentMetrics.java`, modify use cases |

---

## Task 1: Add Kafka Dead Letter Queue (@RetryableTopic) to All Consumers

**Files:**
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/event/payment/PaymentCompletedListener.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/event/payment/PaymentRefundedListener.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/event/saga/SagaCompensationListener.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/event/finance/OrderCreatedFinanceListener.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/event/projection/OrderProjectionListener.java`
- Modify: `services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/event/PayPalRefundListener.java`
- Modify: `services/seller-finance-service/src/main/java/com/vnshop/sellerfinanceservice/infrastructure/event/OrderCreatedFinanceListener.java`
- Modify: `services/seller-finance-service/src/main/java/com/vnshop/sellerfinanceservice/infrastructure/event/PaymentRefundedFinanceListener.java`
- Modify: `services/search-service/src/main/java/com/vnshop/searchservice/infrastructure/kafka/ProductEventConsumer.java`
- Modify: `services/recommendations-service/src/main/java/com/vnshop/recommendationsservice/infrastructure/event/OrderEventListener.java`

**Why:** No DLQ exists anywhere. A poison pill (malformed JSON, schema mismatch) causes the default Spring Kafka error handler to log and commit — silently losing the message. With `@RetryableTopic`, failed messages retry with exponential backoff, then land in a `.DLT` topic for investigation.

- [ ] **Step 1: Add @RetryableTopic to PaymentCompletedListener**

In `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/event/payment/PaymentCompletedListener.java`, add the annotation above `@KafkaListener`:

```java
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.retry.annotation.Backoff;

@RetryableTopic(
    attempts = "3",
    backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
    dltStrategy = DltStrategy.FAIL_ON_ERROR,
    dltTopicSuffix = ".DLT",
    retryTopicSuffix = ".retry"
)
@KafkaListener(topics = "payment.completed", groupId = "order-service-payment")
```

Add a DLT handler method in the same class:

```java
import org.springframework.kafka.annotation.DltHandler;

@DltHandler
public void handleDlt(String message) {
    log.error("Message sent to DLT after retries exhausted: topic=payment.completed, payload={}", message);
}
```

- [ ] **Step 2: Apply the same pattern to PaymentRefundedListener**

```java
@RetryableTopic(
    attempts = "3",
    backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
    dltStrategy = DltStrategy.FAIL_ON_ERROR,
    dltTopicSuffix = ".DLT",
    retryTopicSuffix = ".retry"
)
@KafkaListener(topics = "payment.refunded", groupId = "order-service-refund")
```

Add `@DltHandler` method:

```java
@DltHandler
public void handleDlt(String message) {
    log.error("Message sent to DLT after retries exhausted: topic=payment.refunded, payload={}", message);
}
```

- [ ] **Step 3: Apply to SagaCompensationListener (3 topics)**

```java
@RetryableTopic(
    attempts = "4",
    backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 30000),
    dltStrategy = DltStrategy.FAIL_ON_ERROR,
    dltTopicSuffix = ".DLT",
    retryTopicSuffix = ".retry"
)
@KafkaListener(
    topics = {"inventory.released", "payment.refunded", "shipping.cancelled"},
    groupId = "order-service-saga-compensation"
)
```

Note: Saga compensation gets 4 attempts with longer backoff — these are critical for data consistency.

```java
@DltHandler
public void handleDlt(String message) {
    log.error("CRITICAL: Saga compensation message sent to DLT — manual intervention required: {}", message);
}
```

- [ ] **Step 4: Apply to OrderCreatedFinanceListener (order-service)**

```java
@RetryableTopic(
    attempts = "3",
    backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
    dltStrategy = DltStrategy.FAIL_ON_ERROR,
    dltTopicSuffix = ".DLT",
    retryTopicSuffix = ".retry"
)
@KafkaListener(topics = {"order.created", "order.paid"}, groupId = "order-service-finance")
```

- [ ] **Step 5: Apply to OrderProjectionListener**

```java
@RetryableTopic(
    attempts = "3",
    backoff = @Backoff(delay = 500, multiplier = 2.0, maxDelay = 5000),
    dltStrategy = DltStrategy.FAIL_ON_ERROR,
    dltTopicSuffix = ".DLT",
    retryTopicSuffix = ".retry"
)
@KafkaListener(
    topics = {"order.created", "order.updated", "order.paid", "order.shipped", "order.cancelled"},
    groupId = "order-service-projection",
    concurrency = "3"
)
```

- [ ] **Step 6: Apply to PayPalRefundListener (payment-service)**

```java
@RetryableTopic(
    attempts = "3",
    backoff = @Backoff(delay = 2000, multiplier = 2.0, maxDelay = 15000),
    dltStrategy = DltStrategy.FAIL_ON_ERROR,
    dltTopicSuffix = ".DLT",
    retryTopicSuffix = ".retry"
)
@KafkaListener(topics = "payment.refund.requested", groupId = "payment-service-paypal-refund")
```

- [ ] **Step 7: Apply to seller-finance OrderCreatedFinanceListener**

```java
@RetryableTopic(
    attempts = "3",
    backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
    dltStrategy = DltStrategy.FAIL_ON_ERROR,
    dltTopicSuffix = ".DLT",
    retryTopicSuffix = ".retry"
)
@KafkaListener(topics = {"order.created", "order.paid"}, groupId = "seller-finance-service")
```

- [ ] **Step 8: Apply to seller-finance PaymentRefundedFinanceListener**

```java
@RetryableTopic(
    attempts = "3",
    backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
    dltStrategy = DltStrategy.FAIL_ON_ERROR,
    dltTopicSuffix = ".DLT",
    retryTopicSuffix = ".retry"
)
@KafkaListener(topics = "payment.refunded", groupId = "seller-finance-service-refund")
```

- [ ] **Step 9: Apply to search-service ProductEventConsumer**

```java
@RetryableTopic(
    attempts = "3",
    backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
    dltStrategy = DltStrategy.FAIL_ON_ERROR,
    dltTopicSuffix = ".DLT",
    retryTopicSuffix = ".retry"
)
@KafkaListener(topics = "product-events", groupId = "search-service")
```

- [ ] **Step 10: Apply to recommendations-service OrderEventListener**

```java
@RetryableTopic(
    attempts = "3",
    backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 10000),
    dltStrategy = DltStrategy.FAIL_ON_ERROR,
    dltTopicSuffix = ".DLT",
    retryTopicSuffix = ".retry"
)
@KafkaListener(topics = "order.created", groupId = "recommendations-service")
```

- [ ] **Step 11: Commit**

```bash
git add services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/event/
git add services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/event/
git add services/seller-finance-service/src/main/java/com/vnshop/sellerfinanceservice/infrastructure/event/
git add services/search-service/src/main/java/com/vnshop/searchservice/infrastructure/kafka/
git add services/recommendations-service/src/main/java/com/vnshop/recommendationsservice/infrastructure/event/
git commit -m "resilience(kafka): add @RetryableTopic DLQ to all 10 consumers

Failed messages now retry with exponential backoff (1s, 2s, 4s) then land
in a .DLT topic for investigation. Saga compensation gets 4 attempts with
longer backoff due to criticality. No more silent message loss."
```

---

## Task 2: Configure HikariCP Connection Pools (All Java Services)

**Files:**
- Modify: All 10 Java service `application.yml` files

**Why:** All services use Spring Boot's default HikariCP pool (maximumPoolSize=10, connectionTimeout=30s). With virtual threads enabled, concurrency far exceeds 10 connections. Under load, pool exhaustion causes 30s hangs per request.

- [ ] **Step 1: Add HikariCP config to order-service**

In `services/order-service/src/main/resources/application.yml`, add under `spring.datasource`:

```yaml
spring:
  datasource:
    # ... existing url, username, password ...
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 3000
      idle-timeout: 600000
      max-lifetime: 1800000
      pool-name: order-svc-pool
```

- [ ] **Step 2: Add to payment-service**

```yaml
    hikari:
      maximum-pool-size: 15
      minimum-idle: 5
      connection-timeout: 3000
      idle-timeout: 600000
      max-lifetime: 1800000
      pool-name: payment-svc-pool
```

- [ ] **Step 3: Add to user-service**

```yaml
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 3000
      idle-timeout: 600000
      max-lifetime: 1800000
      pool-name: user-svc-pool
```

- [ ] **Step 4: Add to product-service**

```yaml
    hikari:
      maximum-pool-size: 15
      minimum-idle: 5
      connection-timeout: 3000
      idle-timeout: 600000
      max-lifetime: 1800000
      pool-name: product-svc-pool
```

- [ ] **Step 5: Add to inventory-service**

```yaml
    hikari:
      maximum-pool-size: 15
      minimum-idle: 5
      connection-timeout: 3000
      idle-timeout: 600000
      max-lifetime: 1800000
      pool-name: inventory-svc-pool
```

- [ ] **Step 6: Add to shipping-service**

```yaml
    hikari:
      maximum-pool-size: 10
      minimum-idle: 3
      connection-timeout: 3000
      idle-timeout: 600000
      max-lifetime: 1800000
      pool-name: shipping-svc-pool
```

- [ ] **Step 7: Add to search-service**

```yaml
    hikari:
      maximum-pool-size: 10
      minimum-idle: 3
      connection-timeout: 3000
      idle-timeout: 600000
      max-lifetime: 1800000
      pool-name: search-svc-pool
```

- [ ] **Step 8: Add to seller-finance-service**

```yaml
    hikari:
      maximum-pool-size: 10
      minimum-idle: 3
      connection-timeout: 3000
      idle-timeout: 600000
      max-lifetime: 1800000
      pool-name: seller-finance-svc-pool
```

- [ ] **Step 9: Add to recommendations-service**

```yaml
    hikari:
      maximum-pool-size: 10
      minimum-idle: 3
      connection-timeout: 3000
      idle-timeout: 600000
      max-lifetime: 1800000
      pool-name: recommendations-svc-pool
```

- [ ] **Step 10: Add to coupon-service**

```yaml
    hikari:
      maximum-pool-size: 10
      minimum-idle: 3
      connection-timeout: 3000
      idle-timeout: 600000
      max-lifetime: 1800000
      pool-name: coupon-svc-pool
```

- [ ] **Step 11: Commit**

```bash
git add services/*/src/main/resources/application.yml
git commit -m "perf(hikari): configure connection pools for all 10 services

Default pool (10 connections, 30s timeout) causes exhaustion under virtual
thread concurrency. Sized per service criticality: order/user=20, product/
payment/inventory=15, others=10. Connection timeout reduced to 3s for
fast-fail behavior."
```

---

## Task 3: Fix Order Entity EAGER Loading → LAZY + JOIN FETCH

**Files:**
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/persistence/OrderJpaEntity.java:98`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/persistence/SubOrderJpaEntity.java:71`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/persistence/OrderJpaRepository.java`
- Create: `services/order-service/src/test/java/com/vnshop/orderservice/infrastructure/persistence/OrderJpaRepositoryTest.java`

**Why:** `OrderJpaEntity` has `@OneToMany(fetch = EAGER)` on subOrders, and `SubOrderJpaEntity` has `@OneToMany(fetch = EAGER)` on items. Loading any Order triggers 3-level eager fetch: Order → N SubOrders → M Items. An order with 3 sellers × 5 items = 1 + 3 + 15 queries. For listing pages this is catastrophic.

- [ ] **Step 1: Change OrderJpaEntity fetch type to LAZY**

In `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/persistence/OrderJpaEntity.java`, line 98:

```java
// BEFORE:
@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
private List<SubOrderJpaEntity> subOrders = new ArrayList<>();

// AFTER:
@OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
private List<SubOrderJpaEntity> subOrders = new ArrayList<>();
```

- [ ] **Step 2: Change SubOrderJpaEntity fetch type to LAZY**

In `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/persistence/SubOrderJpaEntity.java`, line 71:

```java
// BEFORE:
@OneToMany(mappedBy = "subOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
private List<OrderItemJpaEntity> items = new ArrayList<>();

// AFTER:
@OneToMany(mappedBy = "subOrder", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
private List<OrderItemJpaEntity> items = new ArrayList<>();
```

- [ ] **Step 3: Add JOIN FETCH queries to OrderJpaRepository**

In `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/persistence/OrderJpaRepository.java`, add custom queries for use cases that need the full graph:

```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface OrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {

    /**
     * Load full order graph for checkout confirmation, view order detail, etc.
     * Uses JOIN FETCH to avoid N+1 with LAZY associations.
     */
    @Query("""
        SELECT DISTINCT o FROM OrderJpaEntity o
        LEFT JOIN FETCH o.subOrders s
        LEFT JOIN FETCH s.items
        WHERE o.id = :orderId
        """)
    Optional<OrderJpaEntity> findByIdWithSubOrdersAndItems(@Param("orderId") UUID orderId);

    /**
     * Load order with sub-orders only (no items) — for status views.
     */
    @Query("""
        SELECT DISTINCT o FROM OrderJpaEntity o
        LEFT JOIN FETCH o.subOrders
        WHERE o.id = :orderId
        """)
    Optional<OrderJpaEntity> findByIdWithSubOrders(@Param("orderId") UUID orderId);

    /**
     * Load order by idempotency key with full graph (for creation dedup check).
     */
    @Query("""
        SELECT DISTINCT o FROM OrderJpaEntity o
        LEFT JOIN FETCH o.subOrders s
        LEFT JOIN FETCH s.items
        WHERE o.idempotencyKey = :key
        """)
    Optional<OrderJpaEntity> findByIdempotencyKeyWithGraph(@Param("key") String key);
}
```

- [ ] **Step 4: Update the repository adapter to use new queries**

Find the `OrderJpaRepositoryAdapter` (or equivalent port implementation) and update `findById` calls to use `findByIdWithSubOrdersAndItems` where the full graph is needed, and the plain `findById` where only the Order header is needed.

Search for usages:
- Checkout/create: use `findByIdempotencyKeyWithGraph`
- View order detail: use `findByIdWithSubOrdersAndItems`
- Order list: use plain query (no fetch join — subOrders not needed for list)
- Status updates: use `findByIdWithSubOrders`

- [ ] **Step 5: Verify no LazyInitializationException**

```bash
cd services/order-service
mvn test -pl .
# Expected: All tests PASS — if any fail with LazyInitializationException,
# the corresponding repository call needs to switch to a JOIN FETCH query
```

- [ ] **Step 6: Commit**

```bash
git add services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/persistence/
git commit -m "perf(order): switch EAGER to LAZY loading + JOIN FETCH queries

3-level EAGER fetch (Order→SubOrders→Items) loaded 15+ entities per order
read regardless of use case. Now LAZY with explicit JOIN FETCH only where
the full graph is needed (checkout, view-detail). Listing/status paths
load only what they use."
```

---

## Task 4: Outbox Publisher — Add SKIP LOCKED for Horizontal Scale

**Files:**
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/outbox/OutboxEventSpringDataRepository.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/outbox/OutboxPublisher.java`

**Why:** `OutboxPublisher` polls every 1s using `findDuePendingEvents()`. When order-service runs multiple replicas, each instance polls the same table — two instances grab the same rows, producing duplicate Kafka publishes. `SELECT FOR UPDATE SKIP LOCKED` ensures each instance claims a distinct batch.

- [ ] **Step 1: Add native query with SKIP LOCKED to the repository**

In `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/outbox/OutboxEventSpringDataRepository.java`, add:

```java
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

@Query(value = """
    SELECT * FROM outbox_events
    WHERE status = 'PENDING'
    AND (next_attempt_at IS NULL OR next_attempt_at <= :now)
    ORDER BY created_at ASC
    LIMIT :batchSize
    FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
List<OutboxEventJpaEntity> findAndLockPendingEvents(
    @Param("now") java.time.Instant now,
    @Param("batchSize") int batchSize
);
```

- [ ] **Step 2: Update OutboxPublisher to use the new locked query**

In `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/outbox/OutboxPublisher.java`, find the `publishPending()` method and replace the repository call:

```java
// BEFORE:
List<OutboxEventJpaEntity> events = repository.findDuePendingEvents(Instant.now(), PageRequest.of(0, batchSize));

// AFTER:
List<OutboxEventJpaEntity> events = repository.findAndLockPendingEvents(Instant.now(), batchSize);
```

- [ ] **Step 3: Ensure the method runs in a transaction**

The `publishPending()` method must be `@Transactional` for `FOR UPDATE` to work. Verify it already has `@Transactional` or add it:

```java
@Transactional
@Scheduled(fixedDelayString = "${outbox.publisher.poll-interval-ms:1000}")
public void publishPending() {
    // ...
}
```

- [ ] **Step 4: Run tests**

```bash
cd services/order-service
mvn test -pl . -Dtest="*Outbox*"
# Expected: All outbox tests PASS
```

- [ ] **Step 5: Commit**

```bash
git add services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/outbox/
git commit -m "resilience(outbox): add SKIP LOCKED to prevent duplicate publishes

When multiple order-service replicas poll the outbox table, they previously
grabbed the same rows — causing duplicate Kafka events. FOR UPDATE SKIP LOCKED
ensures each instance claims a distinct batch atomically."
```

---

## Task 5: Payment Outbox — Add Backoff and DEAD State

**Files:**
- Modify: `services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/event/PaymentCallbackOutboxRelay.java`
- Modify: Payment outbox entity (add attempt tracking columns if missing)
- Create: `services/payment-service/src/main/resources/db/migration/V__payment_outbox_backoff.sql` (next migration number)

**Why:** `PaymentCallbackOutboxRelay` catches `RuntimeException`, logs WARN, and the record stays pending — retried every 1 second forever. An undeliverable event creates an infinite retry loop with zero visibility. Adopt order-service's exponential backoff + DEAD state pattern.

- [ ] **Step 1: Check current payment outbox table schema**

Check if the payment outbox table already has `attempt_count`, `next_attempt_at`, and `status` columns. If not, create a migration. Expected migration (adjust number to be the next in sequence):

```sql
-- V4__payment_outbox_backoff.sql (adjust version number)
ALTER TABLE payment_outbox ADD COLUMN IF NOT EXISTS attempt_count INT NOT NULL DEFAULT 0;
ALTER TABLE payment_outbox ADD COLUMN IF NOT EXISTS next_attempt_at TIMESTAMPTZ;
ALTER TABLE payment_outbox ADD COLUMN IF NOT EXISTS status VARCHAR(20) NOT NULL DEFAULT 'PENDING';
ALTER TABLE payment_outbox ADD COLUMN IF NOT EXISTS last_error TEXT;

CREATE INDEX IF NOT EXISTS idx_payment_outbox_status_next ON payment_outbox (status, next_attempt_at);
```

- [ ] **Step 2: Add backoff logic to PaymentCallbackOutboxRelay**

In `services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/event/PaymentCallbackOutboxRelay.java`, replace the error handling:

```java
private static final int MAX_ATTEMPTS = 8;

private void recordFailure(PaymentOutboxEntity event, Exception cause) {
    int attempts = event.getAttemptCount() + 1;
    event.setAttemptCount(attempts);
    event.setLastError(cause.getMessage());

    if (attempts >= MAX_ATTEMPTS) {
        event.setStatus("DEAD");
        log.error("Payment outbox event DEAD after {} attempts: paymentId={}, error={}",
                attempts, event.getPaymentId(), cause.getMessage());
    } else {
        // Exponential backoff: 2^attempt seconds, capped at 300s
        long backoffSeconds = Math.min((long) Math.pow(2, attempts), 300);
        event.setNextAttemptAt(Instant.now().plusSeconds(backoffSeconds));
        event.setStatus("PENDING");
        log.warn("Payment outbox event retry scheduled: paymentId={}, attempt={}, nextRetryIn={}s",
                event.getPaymentId(), attempts, backoffSeconds);
    }
    outboxRepository.save(event);
}
```

- [ ] **Step 3: Update the polling query to respect next_attempt_at and status**

```java
// In the scheduled method, change the query to:
List<PaymentOutboxEntity> pending = outboxRepository.findByStatusAndNextAttemptAtBefore(
    "PENDING", Instant.now()
);
```

And in the repository interface:

```java
List<PaymentOutboxEntity> findByStatusAndNextAttemptAtBeforeOrNextAttemptAtIsNull(
    String status, Instant now
);
```

- [ ] **Step 4: Wrap the send logic in try-catch that calls recordFailure**

```java
for (PaymentOutboxEntity event : pending) {
    try {
        kafkaTemplate.send(topic, event.getPaymentId(), event.getPayload()).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
        event.setStatus("PUBLISHED");
        event.setPublishedAt(Instant.now());
        outboxRepository.save(event);
    } catch (Exception e) {
        recordFailure(event, e);
    }
}
```

- [ ] **Step 5: Commit**

```bash
git add services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/event/
git add services/payment-service/src/main/resources/db/migration/
git commit -m "resilience(payment-outbox): add exponential backoff and DEAD state

Payment outbox previously retried every 1s forever with no visibility.
Now uses exponential backoff (2^n seconds, max 300s) with max 8 attempts.
Undeliverable events marked DEAD with last_error for investigation."
```

---

## Task 6: Add Structured JSON Logging (All Services)

**Files:**
- Modify: Each service `pom.xml` (add logstash-logback-encoder dependency)
- Create: `logback-spring.xml` in each service's `src/main/resources/`

**Why:** All services use plain-text default logging — unparseable by log aggregation tools. Structured JSON with traceId/spanId/correlationId enables cross-service query in Loki/ELK.

- [ ] **Step 1: Add logstash-logback-encoder dependency**

In each Java service's `pom.xml`, add to `<dependencies>`:

```xml
<dependency>
    <groupId>net.logstash.logback</groupId>
    <artifactId>logstash-logback-encoder</artifactId>
    <version>8.0</version>
</dependency>
```

Services: api-gateway, user-service, product-service, order-service, payment-service, shipping-service, inventory-service, search-service, seller-finance-service, recommendations-service, coupon-service

- [ ] **Step 2: Create logback-spring.xml template**

Create in each service's `src/main/resources/logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <!-- Console (dev): human-readable -->
    <springProfile name="!prod">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="CONSOLE"/>
        </root>
    </springProfile>

    <!-- Production: structured JSON -->
    <springProfile name="prod">
        <appender name="JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <includeMdcKeyName>traceId</includeMdcKeyName>
                <includeMdcKeyName>spanId</includeMdcKeyName>
                <includeMdcKeyName>correlationId</includeMdcKeyName>
                <customFields>{"service":"SERVICE_NAME_HERE"}</customFields>
                <timeZone>UTC</timeZone>
            </encoder>
        </appender>
        <root level="INFO">
            <appender-ref ref="JSON"/>
        </root>
        <!-- Reduce noise from framework loggers -->
        <logger name="org.apache.kafka" level="WARN"/>
        <logger name="org.hibernate.SQL" level="WARN"/>
        <logger name="io.grpc" level="WARN"/>
    </springProfile>
</configuration>
```

Replace `SERVICE_NAME_HERE` with the actual service name in each file:
- `api-gateway`, `user-service`, `product-service`, `order-service`, `payment-service`, `shipping-service`, `inventory-service`, `search-service`, `seller-finance-service`, `recommendations-service`, `coupon-service`

- [ ] **Step 3: Add correlation ID MDC filter to all services**

Create a shared filter pattern. In each service that doesn't already have it, add a filter class:

```java
package com.vnshop.SERVICE.infrastructure.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class MdcCorrelationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                     FilterChain filterChain) throws ServletException, IOException {
        try {
            String correlationId = request.getHeader("X-Correlation-Id");
            if (correlationId != null) {
                MDC.put("correlationId", correlationId);
            }
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
```

Note: OpenTelemetry auto-instrumentation already populates `traceId` and `spanId` in MDC.

- [ ] **Step 4: Commit**

```bash
git add services/*/pom.xml services/*/src/main/resources/logback-spring.xml
git add services/*/src/main/java/**/config/MdcCorrelationFilter.java
git commit -m "observability(logging): add structured JSON logging for all services

Dev profile: human-readable console. Prod profile: JSON with traceId,
spanId, correlationId fields. Uses logstash-logback-encoder for Loki/ELK
compatibility. Correlation ID propagated from gateway X-Correlation-Id header."
```

---

## Task 7: Add Kafka Trace Context Propagation (W3C TraceContext)

**Files:**
- Create: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/kafka/TracingProducerInterceptor.java`
- Create: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/kafka/TracingConsumerInterceptor.java`
- Modify: Each service `application.yml` (add interceptor config)

**Why:** Traces break at every Kafka boundary. A request that creates an order, triggers payment, and updates search creates 3+ disconnected traces. W3C TraceContext propagation via Kafka headers connects them into one distributed trace.

- [ ] **Step 1: Verify OpenTelemetry auto-instrumentation handles Kafka**

Spring Boot 4.x with `spring-boot-starter-actuator` + `io.opentelemetry.instrumentation:opentelemetry-spring-boot-starter` should auto-instrument Kafka producers and consumers. Check if the OTel agent is already wiring trace context.

If auto-instrumentation is active (check `io.micrometer:micrometer-tracing-bridge-otel` in pom.xml), Kafka headers `traceparent` and `tracestate` are already propagated. In that case, this task only needs to verify and enable.

- [ ] **Step 2: Add Micrometer Tracing Kafka integration if not present**

Check each service's `pom.xml` for `micrometer-tracing-bridge-otel`. If present, add the Kafka observation config. In each service's `application.yml`:

```yaml
spring:
  kafka:
    producer:
      properties:
        # Enable observation for trace propagation
        spring.kafka.producer.observation-enabled: true
    consumer:
      properties:
        spring.kafka.consumer.observation-enabled: true
    # Top-level observation toggle
    listener:
      observation-enabled: true
    template:
      observation-enabled: true
```

This tells Spring Kafka to participate in Micrometer observations, which the OTel bridge converts to W3C TraceContext headers.

- [ ] **Step 3: Add observation config to all 8 Kafka-connected services**

Apply the above YAML to:
- order-service
- payment-service
- inventory-service
- product-service
- shipping-service
- search-service
- seller-finance-service
- recommendations-service

- [ ] **Step 4: Verify traces connect across Kafka boundary**

```bash
# Start order-service and search-service
# Create an order → triggers order.created event → search-service consumes
# Check Jaeger UI: the trace should show spans from both services in one trace
curl http://localhost:16686/api/traces?service=order-service&limit=5
```

- [ ] **Step 5: Commit**

```bash
git add services/*/src/main/resources/application.yml
git commit -m "observability(tracing): enable Kafka trace context propagation

Spring Kafka observation-enabled connects producer and consumer spans
into a single distributed trace via W3C TraceContext headers (traceparent,
tracestate). Traces now flow end-to-end across Kafka boundaries."
```

---

## Task 8: Enable Health Probes (Readiness/Liveness) on All Services

**Files:**
- Modify: 9 service `application.yml` files (user-service already done)

**Why:** Most services lack liveness/readiness probe separation. K8s uses these to decide when to route traffic (readiness) vs restart a pod (liveness). Without them, a service with a broken DB connection still receives traffic.

- [ ] **Step 1: Add probe config to order-service**

In `services/order-service/src/main/resources/application.yml`, add under `management`:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when_authorized
      group:
        readiness:
          include: readinessState,db,kafka
        liveness:
          include: livenessState
  health:
    circuitbreakers:
      enabled: true
```

- [ ] **Step 2: Add to payment-service**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when_authorized
      group:
        readiness:
          include: readinessState,db,kafka
        liveness:
          include: livenessState
```

- [ ] **Step 3: Add to product-service**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when_authorized
      group:
        readiness:
          include: readinessState,db
        liveness:
          include: livenessState
```

- [ ] **Step 4: Add to inventory-service**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when_authorized
      group:
        readiness:
          include: readinessState,db,redis
        liveness:
          include: livenessState
```

- [ ] **Step 5: Add to shipping-service, search-service, seller-finance-service, recommendations-service, coupon-service**

Same pattern as product-service (readiness: db, liveness: livenessState). For search-service add kafka to readiness group. For seller-finance and recommendations add kafka to readiness group.

- [ ] **Step 6: Add to api-gateway**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,gateway,prometheus
  endpoint:
    health:
      probes:
        enabled: true
      show-details: when_authorized
      group:
        readiness:
          include: readinessState
        liveness:
          include: livenessState
  health:
    circuitbreakers:
      enabled: true
```

- [ ] **Step 7: Commit**

```bash
git add services/*/src/main/resources/application.yml
git commit -m "observability(health): enable readiness/liveness probes on all services

K8s can now distinguish 'not ready for traffic' (readiness) from 'needs
restart' (liveness). Readiness checks include DB and Kafka connectivity
where applicable. Liveness only checks the JVM is responsive."
```

---

## Task 9: Add Circuit Breakers to gRPC Adapters (Resilience4j)

**Files:**
- Create: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/grpc/GrpcCircuitBreakerConfig.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/grpc/GrpcInventoryReservationAdapter.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/grpc/GrpcPaymentRequestAdapter.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/grpc/GrpcShippingRequestAdapter.java`
- Modify: `services/order-service/src/main/resources/application.yml` (Resilience4j config)

**Why:** gRPC adapters now have deadlines (Phase 1), but repeated failures still hammer a dead service. A circuit breaker stops sending requests after a threshold, letting the downstream recover and failing fast locally.

- [ ] **Step 1: Add Resilience4j circuit breaker config to application.yml**

In `services/order-service/src/main/resources/application.yml`:

```yaml
resilience4j:
  circuitbreaker:
    configs:
      grpcDefault:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        permitted-number-of-calls-in-half-open-state: 3
        slow-call-duration-threshold: 4s
        slow-call-rate-threshold: 80
        record-exceptions:
          - io.grpc.StatusRuntimeException
          - java.util.concurrent.TimeoutException
    instances:
      inventoryService:
        base-config: grpcDefault
      paymentService:
        base-config: grpcDefault
        wait-duration-in-open-state: 15s
      shippingService:
        base-config: grpcDefault
```

- [ ] **Step 2: Create GrpcCircuitBreakerConfig**

Create `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/grpc/GrpcCircuitBreakerConfig.java`:

```java
package com.vnshop.orderservice.infrastructure.grpc;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcCircuitBreakerConfig {

    @Bean
    public CircuitBreaker inventoryCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("inventoryService");
    }

    @Bean
    public CircuitBreaker paymentCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("paymentService");
    }

    @Bean
    public CircuitBreaker shippingCircuitBreaker(CircuitBreakerRegistry registry) {
        return registry.circuitBreaker("shippingService");
    }
}
```

- [ ] **Step 3: Wrap GrpcInventoryReservationAdapter with circuit breaker**

In `GrpcInventoryReservationAdapter.java`, inject the circuit breaker and wrap calls:

```java
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;

// Add to constructor:
private final CircuitBreaker circuitBreaker;

public GrpcInventoryReservationAdapter(
        InventoryServiceGrpc.InventoryServiceBlockingStub inventoryStub,
        CircuitBreaker inventoryCircuitBreaker) {
    this.inventoryStub = Objects.requireNonNull(inventoryStub);
    this.circuitBreaker = Objects.requireNonNull(inventoryCircuitBreaker);
}

// Wrap the reserve call:
@Override
public void reserve(String orderId, List<OrderItem> items) {
    // ... validation unchanged ...

    try {
        ReserveResponse response = circuitBreaker.executeSupplier(() ->
            inventoryStub
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .reserve(request)
        );
        // ... success handling unchanged ...
    } catch (CallNotPermittedException e) {
        LOGGER.error("Circuit breaker OPEN for inventory-service: {}", e.getMessage());
        throw new RuntimeException("Inventory service unavailable (circuit open)", e);
    } catch (StatusRuntimeException e) {
        // ... existing error handling ...
    }
}
```

Apply same pattern to `release()` method.

- [ ] **Step 4: Wrap GrpcPaymentRequestAdapter with circuit breaker**

```java
private final CircuitBreaker circuitBreaker;

public GrpcPaymentRequestAdapter(
        PaymentServiceGrpc.PaymentServiceBlockingStub paymentStub,
        CircuitBreaker paymentCircuitBreaker) {
    this.paymentStub = paymentStub;
    this.circuitBreaker = paymentCircuitBreaker;
}

@Override
public void requestPayment(String orderId, String paymentMethod, Money amount) {
    // ... request building unchanged ...

    try {
        var response = circuitBreaker.executeSupplier(() ->
            paymentStub
                .withDeadlineAfter(10, TimeUnit.SECONDS)
                .requestPayment(request)
        );
        // ... success handling unchanged ...
    } catch (CallNotPermittedException e) {
        log.error("Circuit breaker OPEN for payment-service: {}", e.getMessage());
        throw new PaymentRequestFailedException("Payment service unavailable (circuit open)", e);
    } catch (StatusRuntimeException e) {
        // ... existing error handling ...
    }
}
```

- [ ] **Step 5: Wrap GrpcShippingRequestAdapter with circuit breaker**

```java
private final CircuitBreaker circuitBreaker;

public GrpcShippingRequestAdapter(
        ShippingServiceGrpc.ShippingServiceBlockingStub shippingStub,
        CircuitBreaker shippingCircuitBreaker) {
    this.shippingStub = shippingStub;
    this.circuitBreaker = shippingCircuitBreaker;
}

@Override
public void requestShipping(String orderId, SubOrder subOrder, Address shippingAddress) {
    // ... request building unchanged ...

    try {
        ShippingResponse response = circuitBreaker.executeSupplier(() ->
            shippingStub
                .withDeadlineAfter(5, TimeUnit.SECONDS)
                .requestShipping(request)
        );
        // ... response handling unchanged ...
    } catch (CallNotPermittedException e) {
        LOGGER.error("Circuit breaker OPEN for shipping-service: {}", e.getMessage());
        throw new RuntimeException("Shipping service unavailable (circuit open)", e);
    } catch (StatusRuntimeException e) {
        // ... existing error handling ...
    }
}
```

- [ ] **Step 6: Run tests**

```bash
cd services/order-service
mvn test -pl .
# Expected: All tests PASS. If DI errors occur, update UseCaseConfig to
# inject the circuit breaker beans into the adapters.
```

- [ ] **Step 7: Commit**

```bash
git add services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/grpc/
git add services/order-service/src/main/resources/application.yml
git commit -m "resilience(grpc): add Resilience4j circuit breakers to all gRPC adapters

After 50% failure rate over 10 calls, the breaker opens for 10s (15s for
payment). Requests fail fast with CallNotPermittedException instead of
hammering a dead downstream. Auto-recovers via half-open state."
```

---

## Task 10: Add Custom Prometheus Business Metrics

**Files:**
- Create: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/metrics/OrderMetrics.java`
- Create: `services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/metrics/PaymentMetrics.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/application/CreateOrderUseCase.java`
- Modify: `services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/event/PaymentCallbackOutboxRelay.java`

**Why:** Alert rules reference `vnshop_payment_failures_total` and `vnshop_payment_attempts_total` but these counters don't exist. Without custom business metrics, Prometheus only has JVM/HTTP metrics — no insight into order volume, payment success rate, or saga health.

- [ ] **Step 1: Create OrderMetrics**

Create `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/metrics/OrderMetrics.java`:

```java
package com.vnshop.orderservice.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class OrderMetrics {

    private final Counter ordersCreated;
    private final Counter ordersCancelled;
    private final Counter ordersFailedCreation;
    private final Timer orderCreationDuration;

    public OrderMetrics(MeterRegistry registry) {
        this.ordersCreated = Counter.builder("vnshop_orders_created_total")
                .description("Total orders successfully created")
                .register(registry);

        this.ordersCancelled = Counter.builder("vnshop_orders_cancelled_total")
                .description("Total orders cancelled")
                .register(registry);

        this.ordersFailedCreation = Counter.builder("vnshop_orders_creation_failed_total")
                .description("Total order creation failures")
                .register(registry);

        this.orderCreationDuration = Timer.builder("vnshop_order_creation_duration_seconds")
                .description("Order creation latency")
                .register(registry);
    }

    public void recordOrderCreated() { ordersCreated.increment(); }
    public void recordOrderCancelled() { ordersCancelled.increment(); }
    public void recordOrderCreationFailed() { ordersFailedCreation.increment(); }
    public Timer.Sample startTimer() { return Timer.start(); }
    public void stopTimer(Timer.Sample sample) { sample.stop(orderCreationDuration); }
}
```

- [ ] **Step 2: Create PaymentMetrics**

Create `services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/metrics/PaymentMetrics.java`:

```java
package com.vnshop.paymentservice.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class PaymentMetrics {

    private final Counter paymentAttempts;
    private final Counter paymentFailures;
    private final Counter paymentSuccesses;
    private final Counter paymentRefunds;

    public PaymentMetrics(MeterRegistry registry) {
        this.paymentAttempts = Counter.builder("vnshop_payment_attempts_total")
                .description("Total payment attempts")
                .register(registry);

        this.paymentFailures = Counter.builder("vnshop_payment_failures_total")
                .description("Total payment failures")
                .register(registry);

        this.paymentSuccesses = Counter.builder("vnshop_payment_successes_total")
                .description("Total successful payments")
                .register(registry);

        this.paymentRefunds = Counter.builder("vnshop_payment_refunds_total")
                .description("Total payment refunds processed")
                .register(registry);
    }

    public void recordAttempt() { paymentAttempts.increment(); }
    public void recordFailure() { paymentFailures.increment(); }
    public void recordSuccess() { paymentSuccesses.increment(); }
    public void recordRefund() { paymentRefunds.increment(); }
}
```

- [ ] **Step 3: Wire OrderMetrics into CreateOrderUseCase**

In `services/order-service/src/main/java/com/vnshop/orderservice/application/CreateOrderUseCase.java`, add the metrics as a constructor parameter and instrument:

```java
import com.vnshop.orderservice.infrastructure.metrics.OrderMetrics;

// Add field:
private final OrderMetrics orderMetrics;

// Add to constructor (add to UseCaseConfig bean wiring too):
public CreateOrderUseCase(/* existing params */, OrderMetrics orderMetrics) {
    // ... existing assignments ...
    this.orderMetrics = Objects.requireNonNull(orderMetrics, "orderMetrics is required");
}

// In the create method, wrap:
public Order create(CreateOrderCommand command) {
    var timerSample = orderMetrics.startTimer();
    try {
        // ... existing logic ...
        orderMetrics.recordOrderCreated();
        orderMetrics.stopTimer(timerSample);
        return order;
    } catch (Exception e) {
        orderMetrics.recordOrderCreationFailed();
        orderMetrics.stopTimer(timerSample);
        throw e;
    }
}
```

- [ ] **Step 4: Wire PaymentMetrics into the payment callback flow**

In `PaymentCallbackOutboxRelay.java` or the payment processing use case, record success/failure:

```java
// After successful payment publish:
paymentMetrics.recordAttempt();
paymentMetrics.recordSuccess();

// On failure:
paymentMetrics.recordAttempt();
paymentMetrics.recordFailure();
```

- [ ] **Step 5: Verify metrics appear in Prometheus**

```bash
docker compose up order-service -d
sleep 10
curl -s http://localhost:8086/actuator/prometheus | grep vnshop_orders
# Expected: vnshop_orders_created_total, vnshop_orders_cancelled_total, etc.

docker compose up payment-service -d
sleep 10
curl -s http://localhost:8087/actuator/prometheus | grep vnshop_payment
# Expected: vnshop_payment_attempts_total, vnshop_payment_failures_total, etc.
```

- [ ] **Step 6: Commit**

```bash
git add services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/metrics/
git add services/order-service/src/main/java/com/vnshop/orderservice/application/CreateOrderUseCase.java
git add services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/metrics/
git commit -m "observability(metrics): add custom business metrics for orders and payments

vnshop_orders_created_total, vnshop_orders_cancelled_total,
vnshop_orders_creation_failed_total, vnshop_order_creation_duration_seconds,
vnshop_payment_attempts_total, vnshop_payment_failures_total,
vnshop_payment_successes_total, vnshop_payment_refunds_total.

These feed the existing Prometheus alert rules that previously had no data."
```

---

## Execution Order & Dependencies

All 10 tasks are **independent** — execute in any order or parallel. Recommended sequence:

```
┌──────────────────────────────────────────────────────────────────────────────────┐
│ Quick wins (< 1 hour each):                                                       │
│  Task 2: HikariCP tuning (30 min — YAML only)                                    │
│  Task 8: Health probes (30 min — YAML only)                                       │
├──────────────────────────────────────────────────────────────────────────────────┤
│ Medium effort (2-4 hours):                                                         │
│  Task 3: EAGER → LAZY (2 hours)                                                   │
│  Task 4: Outbox SKIP LOCKED (1 hour)                                              │
│  Task 5: Payment outbox backoff (2 hours)                                         │
│  Task 7: Kafka trace propagation (1 hour)                                         │
│  Task 9: gRPC circuit breakers (3 hours)                                          │
│  Task 10: Custom metrics (2 hours)                                                │
├──────────────────────────────────────────────────────────────────────────────────┤
│ Multi-service (4+ hours, parallelize with subagents):                              │
│  Task 1: Kafka DLQ for all consumers (4 hours — 10 files, 5 services)            │
│  Task 6: Structured JSON logging (4 hours — 11 services, pom + logback + filter) │
└──────────────────────────────────────────────────────────────────────────────────┘
```

---

## Verification Checklist (Run After All Tasks Complete)

```bash
# 1. Kafka DLQ topics exist:
docker compose exec kafka kafka-topics --bootstrap-server localhost:9092 \
  --command-config /etc/kafka/admin.properties --list | grep DLT
# Expected: payment.completed.DLT, order.created.DLT, etc.

# 2. HikariCP pools sized correctly:
curl -s http://localhost:8086/actuator/metrics/hikaricp.connections.max | jq .measurements
# Expected: value = 20 for order-service

# 3. No EAGER loading:
grep -r "FetchType.EAGER" services/order-service/src/main/java/
# Expected: No matches

# 4. Health probes available:
curl -s http://localhost:8086/actuator/health/readiness | jq .status
curl -s http://localhost:8086/actuator/health/liveness | jq .status
# Expected: Both return "UP"

# 5. Circuit breaker metrics:
curl -s http://localhost:8086/actuator/prometheus | grep resilience4j_circuitbreaker
# Expected: inventoryService, paymentService, shippingService state metrics

# 6. Custom business metrics:
curl -s http://localhost:8086/actuator/prometheus | grep vnshop_orders
curl -s http://localhost:8087/actuator/prometheus | grep vnshop_payment
# Expected: Counter values present

# 7. Structured logging in prod profile:
docker compose exec order-service sh -c 'SPRING_PROFILES_ACTIVE=prod java -jar app.jar' &
# Expected: JSON log lines with traceId, spanId, service fields

# 8. Kafka trace propagation:
# Create order → check Jaeger for connected trace across order-service and payment-service
```
