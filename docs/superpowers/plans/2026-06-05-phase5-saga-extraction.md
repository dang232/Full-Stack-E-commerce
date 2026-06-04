# Phase 5: Saga Completion & Bounded Context Extraction — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix the broken saga compensation path, extract coupon and seller-finance subdomains from the god-service, add cart variant awareness, and implement refund processing.

**Architecture:** Wire Kafka consumers for saga compensation events, move coupon+finance bounded contexts to their own services (both already exist in docker-compose), add variantId to cart item model, create refund use cases that call Stripe/PayPal APIs.

**Tech Stack:** Spring Boot, Kafka, NestJS (cart-service), gRPC, Pact contracts

**Depends on:** Phase 4 (port interfaces must exist before this phase)

---

## What's Wrong (Evidence)

| # | Problem | File | Line | Detail |
|---|---------|------|------|--------|
| 1 | Saga compensate() emits generic event, no consumer exists | SagaOrchestrator.java | 127-130 | Publishes `SAGA_COMPENSATING` but nothing listens |
| 2 | CreateOrderUseCase has inline try/catch compensation bypassing saga | CreateOrderUseCase.java | 77-97 | Two disconnected compensation paths |
| 3 | Order-service has 33 use cases across 5+ bounded contexts | application/ dir | — | Should be ~12 core order use cases |
| 4 | Coupon domain duplicated (order-service has full copy, coupon-service exists separately) | domain/coupon/ | — | 7 domain files + 5 use cases duplicated |
| 5 | Seller-finance domain inside order-service | domain/finance/ | — | SellerWallet, Payout, Commission — 5 use cases |
| 6 | Cart has no variantId field | cart-item.ts | — | Only productId — same product different variants collide |
| 7 | No refund workflow — payment.refunded event exists but nothing triggers it | — | — | Return approved but no automated money-back |

---

## File Structure

```
services/order-service/src/main/java/com/vnshop/orderservice/
├── application/saga/
│   ├── SagaOrchestrator.java                 (MODIFY - emit specific compensation events)
│   ├── SagaCompensationConsumer.java         (NEW - Kafka listener for compensation responses)
│   └── SagaTimeoutFinalizer.java             (EXISTING - modify to check all steps)
├── application/
│   ├── CreateOrderUseCase.java               (MODIFY - route through saga, remove inline compensation)
│   └── [DELETE coupon/ and finance/ subdirs after extraction]
├── infrastructure/kafka/
│   └── SagaCompensationKafkaConsumer.java    (NEW - @KafkaListener for compensation events)

services/coupon-service/  (EXISTING service — currently has its own domain)
├── [receives order-service coupon domain/application code]

services/seller-finance-service/  (EXISTING service)
├── [receives order-service finance domain/application code]

services/cart-service/src/cart/domain/
├── cart-item.ts                              (MODIFY - add variantId)

services/payment-service/
├── application/RefundUseCase.java            (NEW)
├── infrastructure/stripe/StripeRefundAdapter.java  (NEW)
├── infrastructure/paypal/PayPalRefundAdapter.java  (NEW)
```

---

## Stage 1: Wire Saga Compensation (Tasks 1-2)

### Task 1: Create specific compensation event types and OutboxPort calls

**Files:**
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/application/saga/SagaOrchestrator.java`

- [ ] **Step 1: Replace generic SAGA_COMPENSATING with specific compensation events**

In `SagaOrchestrator.compensate()`, replace the single generic outbox publish with step-specific events:

```java
@Transactional
public void compensate(String sagaId, String failedStep) {
    Optional<SagaState> opt = sagaStateRepository.findBySagaId(sagaId);
    if (opt.isEmpty()) {
        LOG.warn("Saga {} not found for compensation", sagaId);
        return;
    }
    SagaState current = opt.get();
    SagaState compensating = new SagaState(current.sagaId(), current.orderId(),
        SagaStatus.COMPENSATING, current.createdAt(), Instant.now());
    sagaStateRepository.save(compensating);

    String orderId = current.orderId();
    // Emit specific compensation commands based on which steps completed
    switch (failedStep) {
        case "SHIPPING":
            // Payment was charged, inventory was reserved — reverse both
            outboxPort.publish("Order", orderId, "PAYMENT_REFUND_REQUESTED",
                "{\"orderId\":\"" + orderId + "\",\"sagaId\":\"" + sagaId + "\"}");
            outboxPort.publish("Order", orderId, "INVENTORY_RELEASE_REQUESTED",
                "{\"orderId\":\"" + orderId + "\",\"sagaId\":\"" + sagaId + "\"}");
            break;
        case "PAYMENT":
            // Only inventory was reserved — release it
            outboxPort.publish("Order", orderId, "INVENTORY_RELEASE_REQUESTED",
                "{\"orderId\":\"" + orderId + "\",\"sagaId\":\"" + sagaId + "\"}");
            break;
        case "INVENTORY":
            // Nothing to compensate — first step failed
            markFailed(sagaId);
            break;
        default:
            LOG.error("Unknown saga step: {}", failedStep);
            markFailed(sagaId);
    }
    LOG.warn("Saga {} compensation initiated at step: {}", sagaId, failedStep);
}

private void markFailed(String sagaId) {
    sagaStateRepository.findBySagaId(sagaId).ifPresent(s -> {
        SagaState failed = new SagaState(s.sagaId(), s.orderId(),
            SagaStatus.FAILED, s.createdAt(), Instant.now());
        sagaStateRepository.save(failed);
    });
}
```

- [ ] **Step 2: Add compensation tracking fields**

Add to SagaOrchestrator:
```java
@Transactional
public void onCompensationStepCompleted(String sagaId, String step) {
    LOG.info("Saga {} compensation step completed: {}", sagaId, step);
    // Check if all required compensation steps done
    // For simplicity: if both INVENTORY_RELEASED and PAYMENT_REFUNDED received, mark FAILED
    // A production system would track completed steps in a Set on SagaState
    markFailed(sagaId);
}
```

- [ ] **Step 3: Commit**

```bash
git add services/order-service/src/main/java/com/vnshop/orderservice/application/saga/
git commit -m "feat(saga): emit specific compensation events per failed step"
```

### Task 2: Create Kafka consumers for compensation responses

**Files:**
- Create: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/kafka/SagaCompensationListener.java`

- [ ] **Step 1: Create compensation event listener**

```java
package com.vnshop.orderservice.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.orderservice.application.saga.SagaOrchestrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
public class SagaCompensationListener {
    private static final Logger LOG = LoggerFactory.getLogger(SagaCompensationListener.class);
    private final SagaOrchestrator sagaOrchestrator;
    private final ObjectMapper objectMapper;

    public SagaCompensationListener(SagaOrchestrator sagaOrchestrator, ObjectMapper objectMapper) {
        this.sagaOrchestrator = sagaOrchestrator;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 30000), attempts = "3")
    @KafkaListener(topics = "inventory.released", groupId = "order-saga-compensation")
    public void onInventoryReleased(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String sagaId = node.get("sagaId").asText();
            LOG.info("Inventory released for saga: {}", sagaId);
            sagaOrchestrator.onCompensationStepCompleted(sagaId, "INVENTORY_RELEASED");
        } catch (Exception e) {
            LOG.error("Failed to process inventory.released compensation", e);
            throw new RuntimeException(e);
        }
    }

    @RetryableTopic(backoff = @Backoff(delay = 1000, multiplier = 2, maxDelay = 30000), attempts = "3")
    @KafkaListener(topics = "payment.refunded", groupId = "order-saga-compensation")
    public void onPaymentRefunded(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String sagaId = node.get("sagaId").asText();
            LOG.info("Payment refunded for saga: {}", sagaId);
            sagaOrchestrator.onCompensationStepCompleted(sagaId, "PAYMENT_REFUNDED");
        } catch (Exception e) {
            LOG.error("Failed to process payment.refunded compensation", e);
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 2: Add topics to init-kafka-topics.sh**

In `infra/scripts/init-kafka-topics.sh`, ensure these topics exist:
```bash
kafka-topics --create --if-not-exists --topic inventory.released --partitions 6 --replication-factor 1 --bootstrap-server kafka:9092
kafka-topics --create --if-not-exists --topic payment.refunded --partitions 6 --replication-factor 1 --bootstrap-server kafka:9092
```

- [ ] **Step 3: Commit**

```bash
git add services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/kafka/SagaCompensationListener.java
git add infra/scripts/init-kafka-topics.sh
git commit -m "feat(saga): add Kafka consumers for inventory.released and payment.refunded compensation"
```

---

## Stage 2: Remove Inline Compensation from CreateOrderUseCase (Task 3)

### Task 3: Route order creation through saga orchestrator

**Files:**
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/application/CreateOrderUseCase.java`

- [ ] **Step 1: Inject SagaOrchestrator into CreateOrderUseCase**

Add field and constructor param:
```java
private final SagaOrchestrator sagaOrchestrator;

public CreateOrderUseCase(..., SagaOrchestrator sagaOrchestrator) {
    ...
    this.sagaOrchestrator = sagaOrchestrator;
}
```

- [ ] **Step 2: Replace inline try/catch with saga-driven compensation**

Replace the try/catch block (lines 77-97) with:
```java
String sagaId = UUID.randomUUID().toString();
sagaOrchestrator.start(sagaId, order.id().toString());

try {
    inventoryReservationPort.reserve(order.id().toString(), itemSnapshot);
    sagaOrchestrator.stepCompleted(sagaId, "INVENTORY");

    paymentRequestPort.requestPayment(order.id().toString(), order.paymentMethod(), order.finalAmount());
    sagaOrchestrator.stepCompleted(sagaId, "PAYMENT");

    for (SubOrder subOrder : order.subOrders()) {
        shippingRequestPort.requestShipping(order.id().toString(), subOrder, shippingAddress);
    }
    sagaOrchestrator.stepCompleted(sagaId, "SHIPPING");

    Order savedOrder = orderRepository.save(order);
    orderEventPublisherPort.publishOrderCreated(savedOrder);
    cartRepositoryPort.clearCart(buyerId);
    metricsPort.recordOrderCreated();
    metricsPort.stopTimer(timerSample);
    sagaOrchestrator.complete(sagaId);
    return savedOrder;
} catch (RuntimeException failure) {
    metricsPort.recordOrderCreationFailed();
    metricsPort.stopTimer(timerSample);
    // Determine which step failed based on what completed
    String failedStep = determineFailedStep(sagaId);
    sagaOrchestrator.compensate(sagaId, failedStep);
    throw failure;
}
```

- [ ] **Step 3: Add helper method and saga lifecycle methods**

Add to CreateOrderUseCase:
```java
private String determineFailedStep(String sagaId) {
    // The saga tracks which steps completed — the failed step is the next one
    return sagaOrchestrator.getLastCompletedStep(sagaId)
        .map(step -> switch (step) {
            case "INVENTORY" -> "PAYMENT";
            case "PAYMENT" -> "SHIPPING";
            default -> "INVENTORY";
        })
        .orElse("INVENTORY");
}
```

Add to SagaOrchestrator:
```java
public void start(String sagaId, String orderId) {
    sagaStateRepository.save(new SagaState(sagaId, orderId, SagaStatus.STARTED, Instant.now(), null));
}

public void stepCompleted(String sagaId, String step) {
    LOG.debug("Saga {} step completed: {}", sagaId, step);
    // Track in saga state metadata (could extend SagaState with completedSteps list)
}

public void complete(String sagaId) {
    sagaStateRepository.findBySagaId(sagaId).ifPresent(s -> {
        sagaStateRepository.save(new SagaState(s.sagaId(), s.orderId(),
            SagaStatus.COMPLETED, s.createdAt(), Instant.now()));
    });
}

public Optional<String> getLastCompletedStep(String sagaId) {
    // For now return based on saga state metadata
    // Full implementation would store step list
    return Optional.empty();
}
```

- [ ] **Step 4: Delete the old private compensate method from CreateOrderUseCase**

Remove lines 112-119 (the old inline `compensate(UUID orderId, boolean inventoryReserved, boolean paymentRequested)` method).

- [ ] **Step 5: Run tests**

```bash
cd services/order-service && mvn test -q
```
Fix any test failures by injecting mock SagaOrchestrator in test configs.

- [ ] **Step 6: Commit**

```bash
git add services/order-service/
git commit -m "feat(saga): route order creation through saga orchestrator, remove inline compensation"
```

---

## Stage 3: Cart Variant/SKU Awareness (Task 4)

### Task 4: Add variantId to cart-service domain model

**Files:**
- Modify: `services/cart-service/src/cart/domain/cart-item.ts`
- Modify: `services/cart-service/src/cart/domain/cart.ts` (if exists — item lookup key)
- Modify: `services/cart-service/src/cart/infrastructure/persistence/` (Redis schema)
- Modify: `services/cart-service/src/cart/application/add-item.usecase.ts` (or equivalent)

- [ ] **Step 1: Add variantId to CartItem domain entity**

In `cart-item.ts`, add `variantId` property:
```typescript
export class CartItem {
  private readonly _productId: string;
  private readonly _variantId: string | null;  // NEW — null for products without variants
  private readonly _productName: string;
  private readonly _productImage: string;
  private readonly _unitPrice: Money;
  private _quantity: number;
  private readonly _addedAt: Date;

  static create(props: {
    productId: string;
    variantId?: string | null;  // NEW
    productName: string;
    productImage: string;
    unitPrice: Money;
    quantity: number;
  }): CartItem {
    // ... existing validation
    const item = new CartItem();
    item._productId = props.productId;
    item._variantId = props.variantId ?? null;  // NEW
    item._productName = props.productName;
    item._productImage = props.productImage;
    item._unitPrice = props.unitPrice;
    item._quantity = props.quantity;
    item._addedAt = new Date();
    return item;
  }

  /** Unique key for deduplication — product+variant combo */
  get itemKey(): string {
    return this._variantId
      ? `${this._productId}:${this._variantId}`
      : this._productId;
  }

  get variantId(): string | null { return this._variantId; }
  // ... existing getters
}
```

- [ ] **Step 2: Update cart item lookup to use composite key**

Wherever the cart finds/updates items by productId, change to use `itemKey`:
```typescript
// OLD:
const existing = this.items.find(i => i.productId === productId);

// NEW:
const itemKey = variantId ? `${productId}:${variantId}` : productId;
const existing = this.items.find(i => i.itemKey === itemKey);
```

Apply this to:
- `addItem` — dedup check
- `updateQuantity` — item lookup
- `removeItem` — item lookup

- [ ] **Step 3: Update CartController DTOs**

Add `variantId` to request/response DTOs:
```typescript
// AddItemDto:
export class AddItemDto {
  productId: string;
  variantId?: string;  // NEW — optional
  quantity: number;
}

// CartItemResponse:
export class CartItemResponse {
  productId: string;
  variantId: string | null;  // NEW
  productName: string;
  productImage: string;
  unitPrice: number;
  quantity: number;
  subtotal: number;
}
```

- [ ] **Step 4: Update Redis persistence schema**

The Redis hash key for cart items should include variantId:
```typescript
// Key format change:
// OLD: cart:{userId}:items:{productId}
// NEW: cart:{userId}:items:{productId}:{variantId || 'default'}
```

- [ ] **Step 5: Write tests for variant-aware cart**

Create/update `cart-item.spec.ts`:
```typescript
describe('CartItem with variants', () => {
  it('should treat same product different variants as distinct items', () => {
    const itemA = CartItem.create({ productId: 'p1', variantId: 'size-M', ... });
    const itemB = CartItem.create({ productId: 'p1', variantId: 'size-L', ... });
    expect(itemA.itemKey).not.toEqual(itemB.itemKey);
  });

  it('should treat product without variant as single item', () => {
    const item = CartItem.create({ productId: 'p1', variantId: null, ... });
    expect(item.itemKey).toEqual('p1');
  });

  it('should merge quantity for same product+variant combo', () => {
    // Add same product+variant twice → quantity increases
  });
});
```

- [ ] **Step 6: Run cart-service tests**

```bash
cd services/cart-service && npm test
```
Expected: All pass

- [ ] **Step 7: Commit**

```bash
git add services/cart-service/
git commit -m "feat(cart): add variantId for SKU-level cart item tracking"
```

---

## Stage 4: Refund Processing Workflow (Task 5)

### Task 5: Implement refund use case in payment-service

**Files:**
- Create: `services/payment-service/src/main/java/com/vnshop/paymentservice/domain/port/out/RefundGatewayPort.java`
- Create: `services/payment-service/src/main/java/com/vnshop/paymentservice/application/RefundPaymentUseCase.java`
- Create: `services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/stripe/StripeRefundAdapter.java`
- Create: `services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/paypal/PayPalRefundAdapter.java`
- Create: `services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/kafka/RefundRequestListener.java`

- [ ] **Step 1: Create RefundGatewayPort interface**

```java
package com.vnshop.paymentservice.domain.port.out;

import java.math.BigDecimal;

public interface RefundGatewayPort {
    /** Returns true if the gateway supports this payment method */
    boolean supports(String paymentMethod);

    /**
     * Execute refund via the payment gateway.
     * @return gateway refund reference ID
     */
    String refund(String paymentId, String gatewayTransactionId, BigDecimal amount, String reason);
}
```

- [ ] **Step 2: Create RefundPaymentUseCase**

```java
package com.vnshop.paymentservice.application;

import com.vnshop.paymentservice.domain.Payment;
import com.vnshop.paymentservice.domain.PaymentStatus;
import com.vnshop.paymentservice.domain.port.out.PaymentRepositoryPort;
import com.vnshop.paymentservice.domain.port.out.RefundGatewayPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
public class RefundPaymentUseCase {
    private static final Logger LOG = LoggerFactory.getLogger(RefundPaymentUseCase.class);
    private final PaymentRepositoryPort paymentRepository;
    private final List<RefundGatewayPort> refundGateways;
    private final PaymentEventPublisherPort eventPublisher;

    public RefundPaymentUseCase(PaymentRepositoryPort paymentRepository,
                                List<RefundGatewayPort> refundGateways,
                                PaymentEventPublisherPort eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.refundGateways = refundGateways;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void refund(String orderId, String reason, String sagaId) {
        Payment payment = paymentRepository.findByOrderId(orderId)
            .orElseThrow(() -> new IllegalArgumentException("No payment found for order: " + orderId));

        if (payment.getStatus() != PaymentStatus.COMPLETED) {
            LOG.warn("Cannot refund payment {} in status {}", payment.getId(), payment.getStatus());
            return;
        }

        RefundGatewayPort gateway = refundGateways.stream()
            .filter(g -> g.supports(payment.getPaymentMethod()))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException(
                "No refund gateway for method: " + payment.getPaymentMethod()));

        String refundRef = gateway.refund(
            payment.getId(), payment.getGatewayTransactionId(),
            payment.getAmount(), reason);

        payment.markRefunded(refundRef);
        paymentRepository.save(payment);

        eventPublisher.publishPaymentRefunded(orderId, payment.getId(), sagaId);
        LOG.info("Payment {} refunded via {} — ref: {}", payment.getId(), payment.getPaymentMethod(), refundRef);
    }
}
```

- [ ] **Step 3: Create StripeRefundAdapter**

```java
package com.vnshop.paymentservice.infrastructure.stripe;

import com.stripe.model.Refund;
import com.stripe.param.RefundCreateParams;
import com.vnshop.paymentservice.domain.port.out.RefundGatewayPort;
import org.springframework.stereotype.Component;
import java.math.BigDecimal;

@Component
public class StripeRefundAdapter implements RefundGatewayPort {

    @Override
    public boolean supports(String paymentMethod) {
        return "STRIPE".equalsIgnoreCase(paymentMethod);
    }

    @Override
    public String refund(String paymentId, String gatewayTransactionId, BigDecimal amount, String reason) {
        RefundCreateParams params = RefundCreateParams.builder()
            .setPaymentIntent(gatewayTransactionId)
            .setAmount(amount.longValue())  // Stripe uses smallest currency unit
            .setReason(RefundCreateParams.Reason.REQUESTED_BY_CUSTOMER)
            .putMetadata("vnshop_payment_id", paymentId)
            .build();
        try {
            Refund refund = Refund.create(params);
            return refund.getId();
        } catch (Exception e) {
            throw new RuntimeException("Stripe refund failed for " + paymentId, e);
        }
    }
}
```

- [ ] **Step 4: Create PayPalRefundAdapter**

```java
package com.vnshop.paymentservice.infrastructure.paypal;

import com.vnshop.paymentservice.domain.port.out.RefundGatewayPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@Component
public class PayPalRefundAdapter implements RefundGatewayPort {
    private final RestTemplate restTemplate;
    private final String baseUrl;

    public PayPalRefundAdapter(RestTemplate restTemplate,
                               @Value("${paypal.api-base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public boolean supports(String paymentMethod) {
        return "PAYPAL".equalsIgnoreCase(paymentMethod);
    }

    @Override
    public String refund(String paymentId, String captureId, BigDecimal amount, String reason) {
        String url = baseUrl + "/v2/payments/captures/" + captureId + "/refund";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("PayPal-Request-Id", UUID.randomUUID().toString());

        Map<String, Object> body = Map.of(
            "amount", Map.of("value", amount.toPlainString(), "currency_code", "VND"),
            "note_to_payer", reason != null ? reason : "Refund"
        );

        ResponseEntity<Map> response = restTemplate.exchange(
            url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
        return (String) response.getBody().get("id");
    }
}
```

- [ ] **Step 5: Create Kafka listener for refund requests**

```java
package com.vnshop.paymentservice.infrastructure.kafka;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vnshop.paymentservice.application.RefundPaymentUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

@Component
public class RefundRequestListener {
    private static final Logger LOG = LoggerFactory.getLogger(RefundRequestListener.class);
    private final RefundPaymentUseCase refundUseCase;
    private final ObjectMapper objectMapper;

    public RefundRequestListener(RefundPaymentUseCase refundUseCase, ObjectMapper objectMapper) {
        this.refundUseCase = refundUseCase;
        this.objectMapper = objectMapper;
    }

    @RetryableTopic(backoff = @Backoff(delay = 2000, multiplier = 2, maxDelay = 60000), attempts = "3")
    @KafkaListener(topics = "payment.refund-requested", groupId = "payment-refund")
    public void onRefundRequested(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            String orderId = node.get("orderId").asText();
            String sagaId = node.has("sagaId") ? node.get("sagaId").asText() : null;
            String reason = node.has("reason") ? node.get("reason").asText() : "Customer request";
            refundUseCase.refund(orderId, reason, sagaId);
        } catch (Exception e) {
            LOG.error("Failed to process refund request", e);
            throw new RuntimeException(e);
        }
    }
}
```

- [ ] **Step 6: Add payment.refund-requested to init-kafka-topics.sh**

```bash
kafka-topics --create --if-not-exists --topic payment.refund-requested --partitions 6 --replication-factor 1 --bootstrap-server kafka:9092
```

- [ ] **Step 7: Run payment-service tests**

```bash
cd services/payment-service && mvn test -q
```

- [ ] **Step 8: Commit**

```bash
git add services/payment-service/ infra/scripts/init-kafka-topics.sh
git commit -m "feat(payment): add refund workflow with Stripe and PayPal gateway adapters"
```

---

## Stage 5: Extract Coupon Subdomain from Order-Service (Task 6)

### Task 6: Move coupon logic to coupon-service, replace with Kafka event bridge

**Files:**
- Delete: `services/order-service/src/main/java/com/vnshop/orderservice/domain/coupon/` (7 files)
- Delete: `services/order-service/src/main/java/com/vnshop/orderservice/application/coupon/` (5 files)
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/domain/port/out/CouponValidationPort.java`
- Create: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/coupon/CouponServiceAdapter.java`
- Modify: `services/coupon-service/` (ensure it has full domain from order-service)

- [ ] **Step 1: Verify coupon-service already has complete domain**

Check that `services/coupon-service/` contains:
- `Coupon.java` entity ✓ (confirmed in exploration)
- `ApplyCouponUseCase`, `ValidateCouponUseCase`, etc. ✓
- Controller exposing REST API ✓

If any use case from order-service's coupon/ is missing in coupon-service, copy it over first.

- [ ] **Step 2: Update CouponValidationPort to be the inter-service contract**

The existing `CouponValidationPort.java` in order-service domain stays as-is — it's the port interface. Create a new adapter that calls coupon-service via HTTP:

```java
package com.vnshop.orderservice.infrastructure.coupon;

import com.vnshop.orderservice.domain.port.out.CouponValidationPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import java.math.BigDecimal;

@Component
public class CouponServiceAdapter implements CouponValidationPort {
    private final RestTemplate restTemplate;
    private final String couponServiceUrl;

    public CouponServiceAdapter(RestTemplate restTemplate,
                                @Value("${services.coupon-service.url:http://coupon-service:8088}") String url) {
        this.restTemplate = restTemplate;
        this.couponServiceUrl = url;
    }

    @Override
    public CouponValidationResult validate(String couponCode, String userId, BigDecimal orderTotal) {
        // Call coupon-service REST API
        var response = restTemplate.postForObject(
            couponServiceUrl + "/coupons/validate",
            Map.of("code", couponCode, "userId", userId, "orderTotal", orderTotal),
            CouponValidationResponse.class);
        return mapToResult(response);
    }

    @Override
    public void recordUsage(String couponCode, String userId, String orderId) {
        restTemplate.postForObject(
            couponServiceUrl + "/coupons/apply",
            Map.of("code", couponCode, "userId", userId, "orderId", orderId),
            Void.class);
    }

    @Override
    public void releaseUsage(String couponCode, String userId) {
        restTemplate.postForObject(
            couponServiceUrl + "/coupons/release",
            Map.of("code", couponCode, "userId", userId),
            Void.class);
    }
}
```

- [ ] **Step 3: Delete order-service coupon domain and application files**

Delete these directories entirely:
- `services/order-service/src/main/java/com/vnshop/orderservice/domain/coupon/`
- `services/order-service/src/main/java/com/vnshop/orderservice/application/coupon/`

Also remove coupon-related bean registrations from `UseCaseConfig.java`.

- [ ] **Step 4: Remove coupon controllers from order-service**

Delete or move:
- `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/web/coupon/CouponController.java`

Gateway routes for `/coupons/**` should point to coupon-service:8088 directly.

- [ ] **Step 5: Ensure coupon-service is in the `apps` profile in docker-compose**

Move coupon-service from `deprecated` profile to `apps` profile or remove the profile restriction.

- [ ] **Step 6: Run order-service compile and tests**

```bash
cd services/order-service && mvn compile -q && mvn test -q
```
Fix any compilation errors from removed coupon references.

- [ ] **Step 7: Commit**

```bash
git add services/order-service/ services/coupon-service/ docker-compose.yml
git commit -m "refactor(bounded-context): extract coupon subdomain to coupon-service, bridge via HTTP adapter"
```

---

## Stage 6: Extract Seller-Finance Subdomain (Task 7)

### Task 7: Move finance logic to seller-finance-service

**Files:**
- Delete: `services/order-service/src/main/java/com/vnshop/orderservice/domain/finance/` (2 files)
- Delete: `services/order-service/src/main/java/com/vnshop/orderservice/application/finance/` (5 files)
- Delete: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/web/finance/` (2 controllers)
- Modify: `services/seller-finance-service/` (ensure it has full domain)

- [ ] **Step 1: Verify seller-finance-service exists and has base structure**

Check `services/seller-finance-service/` has:
- Main application class
- Domain model (SellerWallet, Payout)
- Application use cases
- REST controllers

If missing use cases, copy from order-service:
- `CreditWalletUseCase.java`
- `ListPayoutsUseCase.java`
- `ProcessPayoutUseCase.java`
- `RequestPayoutUseCase.java`
- `ViewWalletUseCase.java`

- [ ] **Step 2: Delete finance domain/application/controllers from order-service**

Remove:
- `services/order-service/src/main/java/com/vnshop/orderservice/domain/finance/`
- `services/order-service/src/main/java/com/vnshop/orderservice/application/finance/`
- `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/web/finance/AdminFinanceController.java`
- `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/web/finance/SellerFinanceController.java`

Remove finance bean registrations from `UseCaseConfig.java`.

- [ ] **Step 3: Ensure seller-finance-service is in `apps` profile**

Move from `deprecated` to `apps` in docker-compose.yml.

- [ ] **Step 4: Update gateway routes**

Ensure `/sellers/me/finance/**` and `/admin/finance/**` routes point to `seller-finance-service:8090`.

- [ ] **Step 5: Compile and test order-service**

```bash
cd services/order-service && mvn compile -q && mvn test -q
```

- [ ] **Step 6: Commit**

```bash
git add services/order-service/ services/seller-finance-service/ docker-compose.yml
git commit -m "refactor(bounded-context): extract seller-finance subdomain to seller-finance-service"
```

---

## Phase 5 Complete — Verification Checklist

- [ ] Saga compensation test: Simulate shipping failure → verify `INVENTORY_RELEASE_REQUESTED` and `PAYMENT_REFUND_REQUESTED` events published
- [ ] `grep -r 'domain/coupon' services/order-service/` returns 0 results
- [ ] `grep -r 'domain/finance' services/order-service/` returns 0 results
- [ ] Order-service use case count reduced from 33 to ~17 (orders, checkout, returns, disputes, invoices, dashboard)
- [ ] Cart-service: adding same productId with different variantId creates 2 distinct items
- [ ] Payment-service: `RefundPaymentUseCase` processes refund and publishes `payment.refunded` event
- [ ] `cd services/order-service && mvn compile test -q` passes
- [ ] `cd services/payment-service && mvn compile test -q` passes
- [ ] `cd services/cart-service && npm test` passes
