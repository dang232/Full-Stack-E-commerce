# VNShop Microservices Gap Analysis

This document lists known broken, missing, and incomplete pieces in the VNShop microservices platform. Findings are organized by severity so new developers can understand immediate boot blockers first, then architectural and runtime gaps.

## Quick Reference

| ID | Severity | Finding | Status | Primary impact |
| --- | --- | --- | --- | --- |
| F-01 | 🔴 CRITICAL | Order-service startup crash under `--profile apps` | Broken | `order-service` cannot boot because mandatory outbound port beans are missing. |
| F-02 | 🔴 CRITICAL | Kafka event pipeline broken | Broken | Order events are published but notification consumers never receive them. |
| F-03 | 🟠 HIGH | Order-service god service scope | Incomplete architecture | One service owns too many bounded contexts and controllers. |
| F-04 | 🟠 HIGH | Stub in critical checkout path | Incomplete implementation | Checkout ignores real cart-service data and uses hardcoded cart content. |
| F-05 | 🟡 MEDIUM | Profile/runtime mismatches | Misconfigured runtime | Gateway exposes routes to services that do not run under the `apps` profile. |
| F-06 | 🟡 MEDIUM | Transport architecture split between working and broken channels | Partially working | Product indexing works, but order notification flow has no active consumer. |

## F-01: 🔴 CRITICAL — Startup Crashes: `order-service` Will Not Boot Under `--profile apps`

**Summary:** `CreateOrderUseCase` requires three outbound ports that have no concrete Spring adapter implementations.

### Detailed Explanation

`CreateOrderUseCase` has constructor dependencies for `InventoryReservationPort`, `PaymentRequestPort`, and `ShippingRequestPort`. Lines 28-38 require all dependencies and call `Objects.requireNonNull(...)` for each one. Because Spring cannot find beans for those three ports, application startup under `--profile apps` will fail with `NoSuchBeanDefinitionException` before checkout/order creation can run.

This is not a runtime edge case inside checkout. It is a boot-time wiring failure because required beans are missing from the application context.

### Affected Files

| Path | Notes |
| --- | --- |
| `services/order-service/src/main/java/com/vnshop/orderservice/application/CreateOrderUseCase.java` | Constructor lines 28-38 require all outbound ports. |
| `services/order-service/src/main/java/com/vnshop/orderservice/domain/port/out/` | Contains outbound port interfaces. |
| `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/cart/StubCartRepositoryAdapter.java` | Existing adapter for cart port only. |
| `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/event/OrderEventPublisherAdapter.java` | Existing adapter for order event publishing. |
| `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/event/RefundRequestPublisherAdapter.java` | Existing adapter for refund requests. |
| `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/persistence/DashboardAnalyticsAdapter.java` | Existing adapter for dashboard analytics. |
| `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/storage/S3InvoiceStorageAdapter.java` | Existing adapter for invoice storage when S3 is enabled. |
| `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/storage/UnavailableInvoiceStorageAdapter.java` | Existing fallback adapter for invoice storage when S3 is unavailable. |

### Outbound Port Coverage

The order-service has 13 outbound port interfaces relevant to this gap analysis. Only 6 concrete adapters are present.

| Outbound port | Concrete adapter present? | Existing implementation |
| --- | --- | --- |
| `CartRepositoryPort` | Yes | `StubCartRepositoryAdapter` |
| `DashboardAnalyticsPort` | Yes | `DashboardAnalyticsAdapter` |
| `DisputeRepositoryPort` | No | Missing |
| `InventoryReservationPort` | No | Missing |
| `InvoicePdfRendererPort` | No | Missing |
| `InvoiceRepositoryPort` | No | Missing |
| `InvoiceStoragePort` | Yes | `S3InvoiceStorageAdapter`, `UnavailableInvoiceStorageAdapter` |
| `OrderEventPublisherPort` | Yes | `OrderEventPublisherAdapter` |
| `OrderRepositoryPort` | No | Missing |
| `PaymentRequestPort` | No | Missing |
| `RefundRequestPort` | Yes | `RefundRequestPublisherAdapter` |
| `ReturnRepositoryPort` | No | Missing |
| `ShippingRequestPort` | No | Missing |

### Suggested Remediation

1. Add concrete Spring beans for `InventoryReservationPort`, `PaymentRequestPort`, and `ShippingRequestPort` before enabling `CreateOrderUseCase` in `apps` mode.
2. Decide whether each missing adapter should call another service synchronously, publish an event, or use an outbox pattern.
3. Add a startup smoke test for `order-service` under `--profile apps` to catch missing beans.
4. Complete or intentionally profile-gate remaining missing adapters so boot-time dependencies and runtime features match.

## F-02: 🔴 CRITICAL — Kafka Event Pipeline Broken

**Summary:** Order events are published to one topic, notification consumers listen for different patterns, and notification-service is not wired as a Kafka microservice or started in `apps` mode.

### 2a. Topic Mismatch

`OutboxPublisher` publishes order events to topic `order-events` at `OutboxPublisher.java:19`. `KafkaNotificationConsumer` subscribes with NestJS decorators for `@MessagePattern('order.created')`, `@MessagePattern('order.cancelled')`, `@MessagePattern('order.shipped')`, and `@MessagePattern('shipment.updated')`.

These are different Kafka topics or message patterns. Events published to `order-events` will not be consumed by handlers registered for `order.created`, `order.cancelled`, `order.shipped`, or `shipment.updated`.

### 2b. Transport Never Wired

`notification-service` bootstraps with `NestFactory.create(AppModule)` in `main.ts`, which creates an HTTP-only NestJS application. There is no `app.connectMicroservice()` call and no `Transport.KAFKA` configuration.

Because the Kafka transport is not connected, `@MessagePattern` handlers on `KafkaNotificationConsumer` are not active Kafka consumers. They exist in code but are not attached to a Kafka transport layer at runtime.

### 2c. Profile Mismatch

`notification-service` is configured with `profiles: ["legacy"]` in `docker-compose.yml`. When the stack runs with `--profile apps`, `notification-service` will not start.

The gateway still routes `/notifications/**` to `http://localhost:8087`, so the route points at a service that is not running in the expected runtime profile.

### 2d. Working Contrast

The product indexing flow is the reference pattern that does work:

| Producer | Topic | Consumer | Status |
| --- | --- | --- | --- |
| `ProductEventPublisher` | `product-events` | `search-service` with `@KafkaListener` | Working pattern |
| `OutboxPublisher` | `order-events` | No active matching consumer | Broken pattern |

The product-service to search-service path aligns producer topic and consumer listener. The order-service to notification-service path does not.

### Affected Files

| Path | Notes |
| --- | --- |
| `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/outbox/OutboxPublisher.java` | Publishes to `order-events` at line 19. |
| `services/notification-service/src/notification/application/kafka-notification.consumer.ts` | Declares `@MessagePattern(...)` handlers for non-matching patterns. |
| `services/notification-service/src/main.ts` | Creates HTTP NestJS app only; no Kafka microservice transport wiring. |
| `docker-compose.yml` | Places `notification-service` under `legacy` profile while gateway still routes notifications. |
| `services/product-service/src/main/java/com/vnshop/productservice/infrastructure/messaging/ProductEventPublisher.java` | Working producer reference for `product-events`. |
| `services/search-service/` | Working consumer side reference for product indexing with `@KafkaListener`. |

### Suggested Remediation

1. Choose one Kafka contract for order notifications: either publish separate topics such as `order.created` or consume `order-events` and route by event type inside the consumer.
2. Wire `notification-service` with `app.connectMicroservice()` and `Transport.KAFKA` before relying on `@MessagePattern` handlers.
3. Move `notification-service` to the `apps` profile if `/notifications/**` remains active in the gateway.
4. Mirror the product-service to search-service pattern: one known topic, one active consumer, and profile alignment.

## F-03: 🟠 HIGH — Order-Service God Service Scope

**Summary:** `order-service` contains 10 controllers spanning admin, finance, coupons, checkout, orders, returns, disputes, and invoices.

### Detailed Explanation

`order-service` currently owns 10 controllers across at least 6 bounded contexts. This creates a god-service shape: unrelated business capabilities are deployed together, share one boot path, and inherit each other's failures.

Coupon management likely belongs in or near `coupon-service`, which currently sits on a deprecated profile. Finance endpoints likely belong in `seller-finance-service` or a dedicated finance/reporting service. Keeping these surfaces inside `order-service` expands the blast radius of order-service startup failures and makes service ownership unclear.

### Controller Paths

| Controller | Path |
| --- | --- |
| `AdminDashboardController` | `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/admin/AdminDashboardController.java` |
| `AdminDisputeController` | `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/web/AdminDisputeController.java` |
| `AdminFinanceController` | `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/web/finance/AdminFinanceController.java` |
| `SellerFinanceController` | `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/web/finance/SellerFinanceController.java` |
| `CouponController` | `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/web/coupon/CouponController.java` |
| `CheckoutController` | `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/web/CheckoutController.java` |
| `OrderController` | `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/web/OrderController.java` |
| `ReturnController` | `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/web/ReturnController.java` |
| `SellerOrderController` | `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/web/SellerOrderController.java` |
| `InvoiceController` | `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/web/InvoiceController.java` |

### Bounded Context Spread

| Bounded context | Current order-service surface |
| --- | --- |
| Admin/reporting | `AdminDashboardController` |
| Disputes | `AdminDisputeController` |
| Finance | `AdminFinanceController`, `SellerFinanceController` |
| Coupons | `CouponController` |
| Checkout | `CheckoutController` |
| Orders/fulfillment | `OrderController`, `SellerOrderController` |
| Returns | `ReturnController` |
| Invoices | `InvoiceController` |

### Suggested Remediation

1. Keep core order placement, order status, and seller fulfillment in `order-service`.
2. Move coupon APIs to `coupon-service` or a service adjacent to coupon ownership.
3. Move finance APIs to `seller-finance-service` or a dedicated finance/reporting service.
4. Split admin/reporting read models from transactional order flows to reduce boot and runtime blast radius.

## F-04: 🟠 HIGH — Stub in Critical Checkout Path

**Summary:** Checkout uses `StubCartRepositoryAdapter`, which returns hardcoded cart data and ignores `cart-service`.

### Detailed Explanation

`CartRepositoryPort` exists, but the only concrete adapter is `StubCartRepositoryAdapter`. Its `findByCartId(String cartId)` method returns a `CartSnapshot` containing one hardcoded item:

| Field | Hardcoded value |
| --- | --- |
| Product ID | `phase-1-product` |
| SKU | `STANDARD` |
| Name | `Phase 1 checkout item` |
| Quantity | `1` |
| Price | `100000` |

This means checkout does not read buyer cart contents from `cart-service`. Any order created from checkout data would not reflect real cart state.

### Affected Files

| Path | Notes |
| --- | --- |
| `services/order-service/src/main/java/com/vnshop/orderservice/domain/port/out/CartRepositoryPort.java` | Checkout dependency abstraction exists. |
| `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/cart/StubCartRepositoryAdapter.java` | Placeholder adapter returns hardcoded cart. |

### Suggested Remediation

1. Replace `StubCartRepositoryAdapter` with a real adapter that reads cart snapshots from `cart-service`.
2. Define failure behavior when a cart is missing, stale, empty, or owned by a different buyer.
3. Add integration coverage for checkout using real cart-service data.
4. Remove or profile-gate the stub so production-like `apps` runs cannot silently create placeholder orders.

## F-05: 🟡 MEDIUM — Profile/Runtime Mismatches

**Summary:** Several services are absent from `apps` runtime while gateway routes still expose paths that imply those capabilities exist.

### Detailed Explanation

The Docker Compose profiles and gateway routing are not aligned. `notification-service` is under the `legacy` profile, while `/notifications/**` still routes to `http://localhost:8087`. `coupon-service`, `review-service`, and `seller-finance-service` are under the `deprecated` profile, while gateway routes still point to active fallback services such as `order-service` or `product-service`.

This creates runtime confusion: API routes appear available, but their intended service owners are not running under the normal app profile.

### Affected Files

| Path | Notes |
| --- | --- |
| `docker-compose.yml` | Defines `notification-service` under `legacy`; defines `coupon-service`, `review-service`, and `seller-finance-service` under `deprecated`. |
| `services/api-gateway/` | Gateway still exposes routes for notifications and fallback routes for deprecated service capabilities. |

### Runtime Mismatch Table

| Capability/service | Compose profile | Gateway/runtime status | Gap |
| --- | --- | --- | --- |
| `notification-service` | `legacy` | `/notifications/**` routes to `http://localhost:8087` | Route targets service not started by `--profile apps`. |
| `coupon-service` | `deprecated` | Coupon routes fall back to `order-service` | Ownership unclear and service split incomplete. |
| `review-service` | `deprecated` | Review routes fall back to `product-service` | Intended review service not active. |
| `seller-finance-service` | `deprecated` | Finance routes fall back to `order-service` | Finance ownership remains inside order-service. |

### Suggested Remediation

1. Decide which services are part of the supported `apps` runtime.
2. Remove or disable gateway routes for services not started in that runtime.
3. Move deprecated services back into `apps` only after their dependencies, health checks, and routes are functional.
4. Avoid fallback routing that hides service ownership and makes boundaries ambiguous.

## F-06: 🟡 MEDIUM — Transport Architecture Overview

**Summary:** VNShop currently has one Kafka broker and no RabbitMQ or gRPC transport, with one working Kafka channel and one broken channel.

### Detailed Explanation

The platform uses a single Kafka broker with Confluent `8.2.0`. There is no RabbitMQ and no gRPC transport in the current architecture described here.

The working asynchronous channel is product indexing: `product-service` publishes to `product-events`, and `search-service` consumes that topic. The broken asynchronous channel is order notifications: `order-service` publishes to `order-events`, but there is no active consumer wired to receive that topic.

`notification-service` contains Kafka consumer code, but that code is not connected to the NestJS transport layer and the service is not active in the `apps` profile.

### Transport Matrix

| Channel | Producer | Topic/transport | Consumer | Status |
| --- | --- | --- | --- | --- |
| Product indexing | `product-service` | Kafka topic `product-events` | `search-service` | Working |
| Order notifications | `order-service` | Kafka topic `order-events` | No active matching consumer | Broken |
| Notification consumer code | `notification-service` | `@MessagePattern(...)` decorators | Not connected to `Transport.KAFKA` | Incomplete |

### Affected Files

| Path | Notes |
| --- | --- |
| `docker-compose.yml` | Defines single Kafka broker using Confluent `8.2.0`; service profiles affect runtime availability. |
| `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/outbox/OutboxPublisher.java` | Publishes order events to Kafka topic `order-events`. |
| `services/notification-service/src/notification/application/kafka-notification.consumer.ts` | Kafka consumer handlers exist but do not match `order-events`. |
| `services/notification-service/src/main.ts` | No Kafka transport connection. |
| `services/product-service/src/main/java/com/vnshop/productservice/infrastructure/messaging/ProductEventPublisher.java` | Reference producer for working product event pipeline. |
| `services/search-service/` | Reference consumer side for working product event pipeline. |

### Suggested Remediation

1. Document Kafka as the only supported async transport for now.
2. Standardize topic names and payload envelopes across Java Spring services and NestJS services.
3. Use the product-service to search-service path as the implementation reference.
4. Add smoke tests or local scripts that publish a sample event and verify consumer side effects for each Kafka channel.

## Recommended Remediation Order

| Priority | Fix | Reason |
| --- | --- | --- |
| 1 | Add or profile-gate missing `CreateOrderUseCase` adapters for `InventoryReservationPort`, `PaymentRequestPort`, and `ShippingRequestPort`. | This blocks `order-service` startup under `--profile apps`. |
| 2 | Replace checkout cart stub with a real `cart-service` adapter or disable checkout in production-like profiles until ready. | Current checkout can create orders from hardcoded cart data. |
| 3 | Align order Kafka topic contract and notification consumer patterns. | Events currently publish to `order-events` with no matching active consumer. |
| 4 | Wire `notification-service` with `Transport.KAFKA` and move it into `apps` if gateway notifications stay enabled. | Consumer code cannot receive Kafka events and service does not run in normal app profile. |
| 5 | Align gateway routes with Compose profiles. | Routes should not point to inactive or deprecated service owners. |
| 6 | Split order-service controller responsibilities by bounded context. | Reduces blast radius and clarifies ownership for coupons, finance, admin/reporting, and returns. |
| 7 | Add startup, route, and event-pipeline smoke tests. | Prevents regressions in dependency wiring, gateway runtime shape, and Kafka contracts. |
