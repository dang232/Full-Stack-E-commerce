# Architecture Fixes Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 3 critical runtime bugs (refund topic mismatch, gRPC Docker networking, notification Redis auth), 4 data integrity issues (saga state, cart price trust, cart cleanup, Kafka auto-create), and lay groundwork for compensation event publishers.

**Architecture:** Targeted patches to existing services — no new services or major refactors. Each task is independently deployable and testable. Fixes are ordered by blast radius (P0 infra first, then P1 data integrity, then P2 saga completion).

**Tech Stack:** Spring Boot 3, Kafka, Redis, Resilience4j, Docker Compose, gRPC, JUnit 5 + Mockito

---

## File Structure

| File | Responsibility | Task |
|------|---------------|------|
| `docker-compose.yml:620` | notification-service Redis URL | Task 1 |
| `docker-compose.yml:702-738` | order-service gRPC env vars | Task 2 |
| `docker-compose.yml:243` | Kafka auto-create-topics | Task 3 |
| `services/order-service/.../RefundRequestPublisherAdapter.java:19` | Refund event type constant | Task 4 |
| `services/payment-service/.../PayPalRefundListener.java:47` | Refund topic subscription | Task 4 |
| `services/order-service/.../saga/SagaOrchestrator.java:92-109` | Missing COMPLETED state write | Task 5 |
| `services/order-service/.../saga/SagaOrchestratorTest.java` | Test for COMPLETED state | Task 5 |
| `services/order-service/.../CalculateCheckoutUseCase.java:39-45` | Cart price re-validation | Task 6 |
| `services/order-service/.../CalculateCheckoutUseCaseTest.java` | Tests for price validation | Task 6 |
| `services/order-service/.../domain/port/out/CartRepositoryPort.java` | Add deleteCart method | Task 7 |
| `services/order-service/.../cart/CartServiceAdapter.java` | Implement deleteCart | Task 7 |
| `services/order-service/.../cart/CartHttpClient.java` | Add DELETE /cart endpoint | Task 7 |
| `services/order-service/.../application/CheckoutOrderUseCase.java` | Call deleteCart on success | Task 7 |
| `services/payment-service/.../event/PaymentRefundedEvent.java` | Add sagaId field | Task 8 |
| `services/payment-service/.../event/PayPalRefundListener.java` | Thread sagaId through | Task 8 |
| `services/inventory-service/.../application/ReleaseStockUseCase.java` | Publish inventory.released | Task 9 |
| `services/inventory-service/.../infrastructure/event/InventoryEventPublisher.java` | New Kafka publisher | Task 9 |
| `services/shipping-service/.../application/CancelShipmentUseCase.java` | New use case | Task 10 |
| `services/shipping-service/.../infrastructure/event/ShippingEventPublisher.java` | Publish shipping.cancelled | Task 10 |

---

## Task 1: Fix notification-service Redis Auth

**Files:**
- Modify: `docker-compose.yml:620`

- [ ] **Step 1: Fix Redis URL to include password**

In `docker-compose.yml`, line 620, change:
```yaml
REDIS_URL: redis://redis:6379
```
to:
```yaml
REDIS_URL: redis://:${REDIS_PASSWORD:-vnshop123}@redis:6379
```

- [ ] **Step 2: Verify docker-compose config is valid**

Run: `docker compose config --quiet`
Expected: No errors, exit code 0

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml
git commit -m "fix(notification): add Redis password to REDIS_URL in docker-compose"
```

---

## Task 2: Fix gRPC Docker Networking for Order-Service

**Files:**
- Modify: `docker-compose.yml:702-738` (order-service environment section)

- [ ] **Step 1: Add gRPC host environment variables**

In `docker-compose.yml`, in the order-service `environment` section (after the existing KEYCLOAK vars, around line 730), add:

```yaml
GRPC_CLIENT_INVENTORY_HOST: inventory-service
GRPC_CLIENT_INVENTORY_PORT: 9093
GRPC_CLIENT_PAYMENT_HOST: payment-service
GRPC_CLIENT_PAYMENT_PORT: 9094
GRPC_CLIENT_SHIPPING_HOST: shipping-service
GRPC_CLIENT_SHIPPING_PORT: 9095
```

- [ ] **Step 2: Verify application-grpc.yml reads these env vars**

Read `services/order-service/src/main/resources/application-grpc.yml` and update it to accept env overrides:

```yaml
grpc:
  client:
    inventory:
      host: ${GRPC_CLIENT_INVENTORY_HOST:localhost}
      port: ${GRPC_CLIENT_INVENTORY_PORT:9093}
    payment:
      host: ${GRPC_CLIENT_PAYMENT_HOST:localhost}
      port: ${GRPC_CLIENT_PAYMENT_PORT:9094}
    shipping:
      host: ${GRPC_CLIENT_SHIPPING_HOST:localhost}
      port: ${GRPC_CLIENT_SHIPPING_PORT:9095}
```

- [ ] **Step 3: Verify docker-compose config is valid**

Run: `docker compose config --quiet`
Expected: No errors, exit code 0

- [ ] **Step 4: Commit**

```bash
git add docker-compose.yml services/order-service/src/main/resources/application-grpc.yml
git commit -m "fix(order-service): add gRPC host env vars for Docker networking"
```

---

## Task 3: Disable Kafka Auto-Create Topics

**Files:**
- Modify: `docker-compose.yml:243`

- [ ] **Step 1: Disable auto-topic creation**

In `docker-compose.yml`, line 243, change:
```yaml
KAFKA_AUTO_CREATE_TOPICS_ENABLE: "true"
```
to:
```yaml
KAFKA_AUTO_CREATE_TOPICS_ENABLE: "false"
```

- [ ] **Step 2: Verify init-kafka-topics.sh covers all required topics**

Run: `grep -n "kafka-topics" docker-compose.yml` or find the init script.
Verify topics `payment.refund_requested`, `payment.refunded`, `inventory.released`, `shipping.cancelled` are all pre-created. If not, add them to the init script.

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml
git commit -m "security(kafka): disable auto-create-topics to prevent topic pollution"
```

---

## Task 4: Fix Refund Topic Name Mismatch (CRITICAL)

**Files:**
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/event/RefundRequestPublisherAdapter.java:19`
- Modify: `services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/event/PayPalRefundListener.java:47`

**Context:** `OutboxPublisher.topicFor()` at line 66-68 does `.toLowerCase().replace('_', '.')`. The current `EVENT_TYPE = "payment.refund_requested"` becomes `"payment.refund.requested"` (3 segments). But `PayPalRefundListener` subscribes to `"payment.refund_requested"` (underscore). They never match.

**Strategy:** Align both sides to use the dot-separated format `"payment.refund.requested"` which is consistent with other topics (`payment.completed`, `payment.refunded`).

- [ ] **Step 1: Write a test proving the topic mismatch**

Create or find the test for OutboxPublisher and add:

File: `services/order-service/src/test/java/com/vnshop/orderservice/infrastructure/outbox/OutboxPublisherTest.java`

```java
package com.vnshop.orderservice.infrastructure.outbox;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OutboxPublisherTest {

    @Test
    void topicForConvertsRefundRequestedToExpectedTopic() {
        // The topic PayPalRefundListener subscribes to
        String expectedTopic = "payment.refund.requested";
        String eventType = "PAYMENT_REFUND_REQUESTED";

        String result = OutboxPublisher.topicFor(eventType);

        assertThat(result).isEqualTo(expectedTopic);
    }

    @Test
    void topicForPreservesAlreadyDottedEventTypes() {
        // Verify lowercase+dot events pass through correctly
        assertThat(OutboxPublisher.topicFor("order.created")).isEqualTo("order.created");
    }
}
```

- [ ] **Step 2: Run the test — verify it fails with current EVENT_TYPE**

Run: `cd services/order-service && mvn test -pl . -Dtest=OutboxPublisherTest -DfailIfNoTests=false`
Expected: FAIL — current EVENT_TYPE `"payment.refund_requested"` produces `"payment.refund.requested"` but the test documents the expected behavior.

- [ ] **Step 3: Fix RefundRequestPublisherAdapter EVENT_TYPE**

In `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/event/RefundRequestPublisherAdapter.java`, line 19, change:

```java
private static final String EVENT_TYPE = "payment.refund_requested";
```
to:
```java
private static final String EVENT_TYPE = "PAYMENT_REFUND_REQUESTED";
```

- [ ] **Step 4: Fix PayPalRefundListener topic subscription**

In `services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/event/PayPalRefundListener.java`, line 47, change:

```java
static final String REFUND_REQUESTED_TOPIC = "payment.refund_requested";
```
to:
```java
static final String REFUND_REQUESTED_TOPIC = "payment.refund.requested";
```

- [ ] **Step 5: Run tests to verify alignment**

Run: `cd services/order-service && mvn test -pl . -Dtest=OutboxPublisherTest`
Expected: PASS

- [ ] **Step 6: Verify init-kafka-topics creates the topic with correct name**

Search the kafka init script for the topic name and update if needed to use `payment.refund.requested`.

- [ ] **Step 7: Commit**

```bash
git add services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/event/RefundRequestPublisherAdapter.java
git add services/order-service/src/test/java/com/vnshop/orderservice/infrastructure/outbox/OutboxPublisherTest.java
git add services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/event/PayPalRefundListener.java
git commit -m "fix(refund): align topic name — EVENT_TYPE to PAYMENT_REFUND_REQUESTED, listener to payment.refund.requested"
```

---

## Task 5: Write COMPLETED Saga State

**Files:**
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/application/saga/SagaOrchestrator.java:92-109`
- Create/Modify: `services/order-service/src/test/java/com/vnshop/orderservice/application/saga/SagaOrchestratorTest.java`

- [ ] **Step 1: Write a test asserting COMPLETED state is persisted**

```java
@Test
void onShippingCreated_writesCOMPLETEDStateToDB() {
    // Given
    SagaState shippingCreated = new SagaState(SAGA_ID, ORDER_ID, SagaStatus.PAYMENT_CHARGED, Instant.now(), Instant.now());
    when(sagaStateRepository.findBySagaId(SAGA_ID)).thenReturn(Optional.of(shippingCreated));

    // When
    sagaOrchestrator.onShippingCreated(SAGA_ID);

    // Then
    ArgumentCaptor<SagaState> captor = ArgumentCaptor.forClass(SagaState.class);
    verify(sagaStateRepository, times(2)).save(captor.capture());
    List<SagaState> saved = captor.getAllValues();
    // First save: SHIPPING_CREATED, Second save: COMPLETED
    assertThat(saved.get(0).status()).isEqualTo(SagaStatus.SHIPPING_CREATED);
    assertThat(saved.get(1).status()).isEqualTo(SagaStatus.COMPLETED);
}
```

- [ ] **Step 2: Run test — verify it fails**

Run: `cd services/order-service && mvn test -pl . -Dtest=SagaOrchestratorTest#onShippingCreated_writesCOMPLETEDStateToDB`
Expected: FAIL — only one `.save()` call happens currently

- [ ] **Step 3: Add COMPLETED state write after outbox event**

In `SagaOrchestrator.java`, lines 92-109, after the outbox save (line 104), add:

```java
@Transactional
public void onShippingCreated(String sagaId) {
    Optional<SagaState> opt = sagaStateRepository.findBySagaId(sagaId);
    if (opt.isEmpty()) {
        LOG.warn("Saga {} not found for shipping created", sagaId);
        return;
    }
    SagaState current = opt.get();
    SagaState shippingDone = new SagaState(current.sagaId(), current.orderId(), SagaStatus.SHIPPING_CREATED, current.createdAt(), Instant.now());
    sagaStateRepository.save(shippingDone);

    outboxEventRepository.save(OutboxEventJpaEntity.fromDomain(
        OutboxEvent.pending(AGGREGATE_TYPE, sagaId, "SAGA_COMPLETED",
            "{\"orderId\":\"" + current.orderId() + "\",\"sagaId\":\"" + sagaId + "\",\"step\":\"COMPLETE\"}")
    ));

    // Persist terminal COMPLETED state
    SagaState completed = new SagaState(current.sagaId(), current.orderId(), SagaStatus.COMPLETED, current.createdAt(), Instant.now());
    sagaStateRepository.save(completed);

    LOG.info("Saga {} completed for order {}", sagaId, current.orderId());
}
```

- [ ] **Step 4: Run test — verify it passes**

Run: `cd services/order-service && mvn test -pl . -Dtest=SagaOrchestratorTest#onShippingCreated_writesCOMPLETEDStateToDB`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add services/order-service/src/main/java/com/vnshop/orderservice/application/saga/SagaOrchestrator.java
git add services/order-service/src/test/java/com/vnshop/orderservice/application/saga/SagaOrchestratorTest.java
git commit -m "fix(saga): persist COMPLETED state after outbox event in onShippingCreated"
```

---

## Task 6: Cart Price Re-Validation Against Catalog

**Files:**
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/application/CalculateCheckoutUseCase.java:39-45`
- Modify: `services/order-service/src/test/java/com/vnshop/orderservice/application/CalculateCheckoutUseCaseTest.java`

**Context:** The `calculate(cartId)` path at lines 39-45 sums `CartItemSnapshot.total()` directly from the cart-service cache without calling `productCatalogPort` to get authoritative prices. The `calculate(lineItems, couponCode, userId)` path correctly resolves from catalog. This is a price integrity gap.

- [ ] **Step 1: Write a test proving stale prices are rejected**

```java
@Test
void cartSnapshotPathResolvesAuthoritativePricesFromCatalog() {
    // Given: cart has stale price (100,000) but catalog has current price (150,000)
    CartSnapshot cart = new CartSnapshot("user-1", List.of(
        new CartItemSnapshot("prod-1", "", "Widget", 2, new BigDecimal("100000"))
    ));
    when(fakeCartRepo.findByCartId("user-1")).thenReturn(cart);

    // Catalog returns authoritative price
    ProductSnapshot catalogProduct = new ProductSnapshot("prod-1", "Widget", new BigDecimal("150000"), null);
    when(productCatalogPort.findById("prod-1")).thenReturn(Optional.of(catalogProduct));

    // When
    CheckoutBreakdown result = useCase.calculate("user-1");

    // Then: uses catalog price (150,000 × 2 = 300,000) + shipping
    assertThat(result.itemsTotal()).isEqualByComparingTo(new BigDecimal("300000"));
    assertThat(result.total()).isEqualByComparingTo(new BigDecimal("330000")); // +30,000 shipping
}
```

- [ ] **Step 2: Run test — verify it fails**

Run: `cd services/order-service && mvn test -pl . -Dtest=CalculateCheckoutUseCaseTest#cartSnapshotPathResolvesAuthoritativePricesFromCatalog`
Expected: FAIL — currently uses cached price (200,000 + 30,000 = 230,000)

- [ ] **Step 3: Modify calculate(cartId) to re-validate prices**

Replace lines 39-45 of `CalculateCheckoutUseCase.java`:

```java
public CheckoutBreakdown calculate(String cartId) {
    CartSnapshot cart = cartRepositoryPort.findByCartId(cartId);
    BigDecimal itemsTotal = cart.items().stream()
            .map(item -> {
                BigDecimal authoritativePrice = productCatalogPort.findById(item.productId())
                        .map(ProductSnapshot::price)
                        .orElse(item.unitPrice()); // fallback to cart price if product not found
                return authoritativePrice.multiply(BigDecimal.valueOf(item.quantity()));
            })
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return summarize(itemsTotal, NO_DISCOUNT);
}
```

- [ ] **Step 4: Run all CalculateCheckoutUseCase tests**

Run: `cd services/order-service && mvn test -pl . -Dtest=CalculateCheckoutUseCaseTest`
Expected: ALL PASS (update existing `cartSnapshotPathSumsLineTotalsAndAddsStandardShipping` test to mock the catalog port too)

- [ ] **Step 5: Fix existing test to mock catalog**

Update `cartSnapshotPathSumsLineTotalsAndAddsStandardShipping` test to provide the catalog mock returning the same price as the cart (so the test still validates the summation logic).

- [ ] **Step 6: Commit**

```bash
git add services/order-service/src/main/java/com/vnshop/orderservice/application/CalculateCheckoutUseCase.java
git add services/order-service/src/test/java/com/vnshop/orderservice/application/CalculateCheckoutUseCaseTest.java
git commit -m "fix(checkout): re-validate cart prices against product catalog before calculation"
```

---

## Task 7: Clear Cart After Successful Order Placement

**Files:**
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/domain/port/out/CartRepositoryPort.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/cart/CartServiceAdapter.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/cart/CartHttpClient.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/application/CheckoutOrderUseCase.java` (or equivalent order-creation use case)

- [ ] **Step 1: Add deleteCart to CartRepositoryPort**

In `CartRepositoryPort.java`, add:

```java
public interface CartRepositoryPort {
    CartSnapshot findByCartId(String cartId);
    void clearCart(String userId);
}
```

- [ ] **Step 2: Add DELETE method to CartHttpClient**

In `CartHttpClient.java`, add:

```java
@DeleteMapping("/cart")
void clearCart(@RequestHeader("x-user-id") String userId);
```

- [ ] **Step 3: Implement clearCart in CartServiceAdapter**

In `CartServiceAdapter.java`, add:

```java
@Override
public void clearCart(String userId) {
    try {
        cartHttpClient.clearCart(userId);
    } catch (Exception e) {
        LOG.warn("Failed to clear cart for user {} — non-fatal, cart will expire via TTL", userId, e);
    }
}
```

Note: Cart-clear is fire-and-forget. A failure here should NOT fail the order placement.

- [ ] **Step 4: Call clearCart after successful order creation**

Find the order creation use case (likely `CheckoutOrderUseCase` or `CreateOrderUseCase`) and add after the order is successfully persisted:

```java
// Clear cart after successful order — best-effort, non-blocking
cartRepositoryPort.clearCart(userId);
```

- [ ] **Step 5: Update FakeCartRepository in test to implement clearCart**

In `CalculateCheckoutUseCaseTest.java`, update the inner `FakeCartRepository` class:

```java
@Override
public void clearCart(String userId) {
    // no-op in tests
}
```

- [ ] **Step 6: Write a test verifying cart is cleared post-order**

```java
@Test
void cartIsClearedAfterSuccessfulOrderPlacement() {
    // Given: valid order request
    // When: order is placed successfully
    // Then: verify cartRepositoryPort.clearCart(userId) was called
    verify(cartRepositoryPort).clearCart(userId);
}
```

- [ ] **Step 7: Run all tests**

Run: `cd services/order-service && mvn test`
Expected: ALL PASS

- [ ] **Step 8: Commit**

```bash
git add services/order-service/src/main/java/com/vnshop/orderservice/domain/port/out/CartRepositoryPort.java
git add services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/cart/CartServiceAdapter.java
git add services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/cart/CartHttpClient.java
git add services/order-service/src/main/java/com/vnshop/orderservice/application/CheckoutOrderUseCase.java
git commit -m "feat(checkout): clear cart after successful order placement (fire-and-forget)"
```

---

## Task 8: Add sagaId to PaymentRefundedEvent

**Files:**
- Modify: `services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/event/PaymentRefundedEvent.java`
- Modify: `services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/event/PayPalRefundListener.java`

- [ ] **Step 1: Add sagaId field to PaymentRefundedEvent**

Replace the record definition:

```java
public record PaymentRefundedEvent(
        String provider,
        UUID paymentId,
        String orderId,
        String returnId,
        String sellerId,
        String refundId,
        String captureId,
        String status,
        BigDecimal amount,
        String currency,
        String commissionTier,
        String sagaId) {
}
```

- [ ] **Step 2: Thread sagaId through PayPalRefundListener**

In `PayPalRefundListener.java`, the incoming `payment.refund.requested` event payload needs a `sagaId` field. Extract it and pass through when constructing `PaymentRefundedEvent`:

Find the line where `PaymentRefundedEvent` is constructed (around line 143) and add `sagaId` from the incoming event payload. If the incoming payload doesn't have sagaId (which it currently doesn't), pass `null`:

```java
String sagaId = incomingPayload.has("sagaId") ? incomingPayload.get("sagaId").asText(null) : null;

PaymentRefundedEvent event = new PaymentRefundedEvent(
    "PAYPAL", payment.id(), orderId, returnId, sellerId,
    refundId, captureId, "COMPLETED", amount, currency, commissionTier, sagaId);
```

- [ ] **Step 3: Update RefundRequestPublisherAdapter to include sagaId in payload**

In `RefundRequestPublisherAdapter.java`, add `sagaId` to the outbox event payload JSON:

```java
// Add sagaId from the saga context (nullable — only present when triggered by saga compensation)
String sagaId = /* look up from SagaState by orderId, or null if not saga-triggered */;
```

- [ ] **Step 4: Run payment-service tests**

Run: `cd services/payment-service && mvn test`
Expected: ALL PASS (existing tests may need sagaId=null added to record construction)

- [ ] **Step 5: Commit**

```bash
git add services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/event/PaymentRefundedEvent.java
git add services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/event/PayPalRefundListener.java
git add services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/event/RefundRequestPublisherAdapter.java
git commit -m "feat(saga): add sagaId pass-through to PaymentRefundedEvent for compensation confirmation"
```

---

## Task 9: Inventory-Service Compensation Event Publisher

**Files:**
- Create: `services/inventory-service/src/main/java/com/vnshop/inventoryservice/infrastructure/event/InventoryEventPublisher.java`
- Create: `services/inventory-service/src/main/resources/db/migration/V3__add_outbox_table.sql` (if using outbox) OR direct Kafka publish
- Modify: `services/inventory-service/src/main/java/com/vnshop/inventoryservice/application/ReleaseStockUseCase.java:32-51`
- Create: `services/inventory-service/src/test/java/com/vnshop/inventoryservice/application/ReleaseStockUseCaseTest.java`

- [ ] **Step 1: Write test asserting event is published after release**

```java
@Test
void release_publishesInventoryReleasedEventToKafka() {
    // Given: active reservation exists for orderId
    when(reservationRepository.findByOrderIdAndStatus(ORDER_ID, ACTIVE))
        .thenReturn(List.of(reservation));

    // When
    boolean result = releaseStockUseCase.release(ORDER_ID);

    // Then
    assertTrue(result);
    verify(inventoryEventPublisher).publishReleased(eq(ORDER_ID), isNull(), anyList());
}
```

- [ ] **Step 2: Run test — verify it fails**

Expected: FAIL — no `inventoryEventPublisher` dependency exists yet

- [ ] **Step 3: Create InventoryEventPublisher**

```java
package com.vnshop.inventoryservice.infrastructure.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Component
public class InventoryEventPublisher {

    private static final String TOPIC = "inventory.released";
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public InventoryEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishReleased(String orderId, String sagaId, List<ReleasedItem> items) {
        Map<String, Object> payload = Map.of(
            "orderId", orderId,
            "sagaId", sagaId != null ? sagaId : "",
            "releasedItems", items,
            "timestamp", Instant.now().toString()
        );
        try {
            kafkaTemplate.send(TOPIC, orderId, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            // Log but don't fail the release — event is best-effort confirmation
            LOG.warn("Failed to publish inventory.released for order {}", orderId, e);
        }
    }

    public record ReleasedItem(String productId, int quantity) {}
}
```

- [ ] **Step 4: Add KafkaTemplate bean to inventory-service config**

Check if `spring.kafka.producer` config exists in `application.yml`. If not, add:

```yaml
spring:
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
```

- [ ] **Step 5: Wire publisher into ReleaseStockUseCase**

Modify `ReleaseStockUseCase.java` to inject and call the publisher:

```java
private final InventoryEventPublisher eventPublisher;

public boolean release(String orderId) {
    // ... existing logic ...
    List<InventoryEventPublisher.ReleasedItem> releasedItems = reservations.stream()
        .map(r -> new InventoryEventPublisher.ReleasedItem(r.productId(), r.quantity()))
        .toList();
    eventPublisher.publishReleased(orderId, null, releasedItems);
    return true;
}
```

- [ ] **Step 6: Run tests**

Run: `cd services/inventory-service && mvn test`
Expected: ALL PASS

- [ ] **Step 7: Commit**

```bash
git add services/inventory-service/
git commit -m "feat(inventory): publish inventory.released Kafka event after stock release"
```

---

## Task 10: Shipping-Service Compensation Event Publisher

**Files:**
- Create: `services/shipping-service/src/main/java/com/vnshop/shippingservice/application/CancelShipmentUseCase.java`
- Create: `services/shipping-service/src/main/java/com/vnshop/shippingservice/infrastructure/event/ShippingEventPublisher.java`
- Create: `services/shipping-service/src/test/java/com/vnshop/shippingservice/application/CancelShipmentUseCaseTest.java`

- [ ] **Step 1: Write test for CancelShipmentUseCase**

```java
@Test
void cancel_marksShipmentCancelledAndPublishesEvent() {
    // Given
    when(shipmentRepository.findByOrderId(ORDER_ID)).thenReturn(Optional.of(activeShipment));

    // When
    cancelShipmentUseCase.cancel(ORDER_ID, null, "SAGA_COMPENSATION");

    // Then
    verify(shipmentRepository).save(argThat(s -> s.status() == ShipmentStatus.CANCELLED));
    verify(shippingEventPublisher).publishCancelled(eq(ORDER_ID), isNull(), eq("SAGA_COMPENSATION"));
}
```

- [ ] **Step 2: Run test — verify it fails**

Expected: FAIL — classes don't exist yet

- [ ] **Step 3: Create ShippingEventPublisher**

```java
package com.vnshop.shippingservice.infrastructure.event;

import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;

@Component
public class ShippingEventPublisher {

    private static final String TOPIC = "shipping.cancelled";
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public ShippingEventPublisher(KafkaTemplate<String, String> kafkaTemplate, ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    public void publishCancelled(String orderId, String sagaId, String reason) {
        Map<String, Object> payload = Map.of(
            "orderId", orderId,
            "sagaId", sagaId != null ? sagaId : "",
            "reason", reason,
            "timestamp", Instant.now().toString()
        );
        try {
            kafkaTemplate.send(TOPIC, orderId, objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            LOG.warn("Failed to publish shipping.cancelled for order {}", orderId, e);
        }
    }
}
```

- [ ] **Step 4: Create CancelShipmentUseCase**

```java
package com.vnshop.shippingservice.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CancelShipmentUseCase {

    private final ShipmentRepository shipmentRepository;
    private final ShippingEventPublisher shippingEventPublisher;

    public CancelShipmentUseCase(ShipmentRepository shipmentRepository, ShippingEventPublisher shippingEventPublisher) {
        this.shipmentRepository = shipmentRepository;
        this.shippingEventPublisher = shippingEventPublisher;
    }

    @Transactional
    public boolean cancel(String orderId, String sagaId, String reason) {
        return shipmentRepository.findByOrderId(orderId)
            .map(shipment -> {
                shipment.cancel();
                shipmentRepository.save(shipment);
                shippingEventPublisher.publishCancelled(orderId, sagaId, reason);
                return true;
            })
            .orElse(false);
    }
}
```

- [ ] **Step 5: Add Kafka producer config to shipping-service application.yml**

```yaml
spring:
  kafka:
    producer:
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.apache.kafka.common.serialization.StringSerializer
```

- [ ] **Step 6: Run tests**

Run: `cd services/shipping-service && mvn test`
Expected: ALL PASS

- [ ] **Step 7: Commit**

```bash
git add services/shipping-service/
git commit -m "feat(shipping): add CancelShipmentUseCase + publish shipping.cancelled Kafka event"
```

---

## Verification Checklist

After all tasks are complete, run the following end-to-end verification:

- [ ] **Docker compose starts cleanly:**
  ```bash
  docker compose --profile apps up -d
  docker compose ps  # All services healthy
  ```

- [ ] **No Kafka topic creation errors** (auto-create disabled, so init script must pre-create all topics)

- [ ] **order-service connects to gRPC peers:**
  Check logs: `docker compose logs order-service | grep -i grpc`
  Expected: No "connection refused" errors

- [ ] **notification-service connects to Redis:**
  Check logs: `docker compose logs notification-service | grep -i redis`
  Expected: No auth errors

- [ ] **Run all Java service tests:**
  ```bash
  cd services/order-service && mvn test
  cd services/payment-service && mvn test
  cd services/inventory-service && mvn test
  cd services/shipping-service && mvn test
  ```
  Expected: ALL PASS

- [ ] **Refund flow integration test (manual or scripted):**
  1. Create an order
  2. Complete payment (mock PayPal capture)
  3. Trigger return → refund_requested event
  4. Verify PayPalRefundListener receives the event on `payment.refund.requested`
  5. Verify payment.refunded event includes sagaId field

---

## Task Dependencies

```
Task 1 (notification Redis) ──── independent
Task 2 (gRPC Docker env)    ──── independent
Task 3 (Kafka auto-create)  ──── must run AFTER verifying init script has all topics
Task 4 (refund topic fix)   ──── independent (CRITICAL, do first)
Task 5 (saga COMPLETED)     ──── independent
Task 6 (cart price validation) ── independent
Task 7 (cart clear)         ──── independent
Task 8 (sagaId pass-through) ─── depends on Task 4 (topic must be aligned first)
Task 9 (inventory.released) ──── independent (but validates with Task 8)
Task 10 (shipping.cancelled) ─── independent (but validates with Task 8)
```

**Recommended parallel execution:**
- Lane A: Tasks 1, 2, 3 (infra/docker — one agent)
- Lane B: Tasks 4, 8 (refund topic + sagaId — one agent, same services)
- Lane C: Tasks 5, 6, 7 (order-service improvements — one agent)
- Lane D: Tasks 9, 10 (compensation publishers — one agent)
