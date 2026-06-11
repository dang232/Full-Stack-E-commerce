# VNShop Microservices Gap Analysis

This document lists known broken, missing, and incomplete pieces in the VNShop microservices platform. Findings are organized by severity so new developers can understand immediate boot blockers first, then architectural and runtime gaps.

## Quick Reference

| ID | Severity | Finding | Status | Primary impact |
| --- | --- | --- | --- | --- |
| F-01 | ~~🔴 CRITICAL~~ | ~~Order-service startup crash under `--profile apps`~~ | **Resolved** | All 24 outbound ports now have concrete adapters (gRPC for saga steps, JPA for persistence, HTTP for cross-service calls). |
| F-02 | ~~🔴 CRITICAL~~ | ~~Kafka event pipeline broken~~ | **Resolved — documentation error** | Investigation confirmed all order event topics use dot-notation and are correctly aligned; notification-service is properly wired to Kafka. |
| F-03 | 🟠 HIGH | Order-service god service scope | Incomplete architecture | One service owns too many bounded contexts and controllers. |
| F-04 | ~~🟠 HIGH~~ | ~~Stub in critical checkout path~~ | **Resolved** | `CartServiceAdapter` calls real cart-service via HTTP with Resilience4j circuit breaker. |
| F-05 | ~~🟡 MEDIUM~~ | ~~Profile/runtime mismatches~~ | **Resolved** | All services now run under `profiles: ["apps"]`; gateway routes target their dedicated services directly. |
| F-06 | ~~🟡 MEDIUM~~ | ~~Transport architecture split between working and broken channels~~ | **Resolved** | `notification-service` moved to `apps` profile; both Kafka channels (product indexing + order notifications) are fully active. |

## F-01: ~~🔴 CRITICAL~~ — ~~Startup Crashes: `order-service` Will Not Boot Under `--profile apps`~~ — RESOLVED

**Status: RESOLVED.** All 24 outbound ports now have concrete adapter implementations. The service boots successfully under `--profile apps`.

### Resolution Details

The previously missing adapters have been implemented with appropriate transport strategies:

| Outbound port | Adapter | Transport |
| --- | --- | --- |
| `InventoryReservationPort` | `GrpcInventoryReservationAdapter` | gRPC (saga step) |
| `PaymentRequestPort` | `GrpcPaymentRequestAdapter` | gRPC (saga step) |
| `ShippingRequestPort` | `GrpcShippingRequestAdapter` | gRPC (saga step) |
| `CartRepositoryPort` | `CartServiceAdapter` | HTTP + circuit breaker |
| `OrderRepositoryPort` | `ProjectionPortAdapter` | JPA persistence |
| `DisputeRepositoryPort` | (JPA persistence) | JPA persistence |
| `InvoiceRepositoryPort` | (JPA persistence) | JPA persistence |
| `InvoicePdfRendererPort` | (infrastructure adapter) | Local rendering |
| `ReturnRepositoryPort` | (JPA persistence) | JPA persistence |

Additional adapters beyond the original 13 ports were also added: `AuditPortAdapter`, `MetricsPortAdapter`, `OutboxPortAdapter`, `CommissionTierJpaAdapter`, `FraudOrderCountJpaAdapter`, `OrderSummaryQueryPortAdapter`, `TaxRateJpaAdapter`, `ProductCatalogAdapter`, `ShippingServiceQuoteAdapter`, and `CouponServiceAdapter`.

## F-02: ~~🔴 CRITICAL~~ — ~~Kafka Event Pipeline Broken~~ — RESOLVED (Documentation Error)

**Status: NOT AN ISSUE.** Investigation of the actual codebase found that the original analysis was based on incorrect assumptions. No remediation is needed.

### What the original analysis claimed

The original finding claimed that `OutboxPublisher` publishes to a topic named `order-events` while `KafkaNotificationConsumer` subscribes to `order.created`, `order.cancelled`, etc., creating a mismatch. It also claimed `notification-service` had no Kafka transport wiring.

### What the code actually shows

- All order event producers use dot-notation topics: `order.created`, `order.updated`, `order.paid`, `order.shipped`, `order.cancelled`. The topic name `order-events` does not exist anywhere in the codebase.
- All consumers subscribe to matching dot-notation topics. Producer and consumer topic names are correctly aligned.
- `notification-service/src/main.ts` does wire the Kafka transport via `app.connectMicroservice()` with `Transport.KAFKA`. The service is properly connected as a Kafka microservice.

The `order-events` topic referenced in the original analysis was a documentation error. The profile mismatch noted in section 2c (F-05) remains a separate, valid concern.

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

## F-04: ~~🟠 HIGH~~ — ~~Stub in Critical Checkout Path~~ — RESOLVED

**Status: RESOLVED.** `CartServiceAdapter` replaces the former `StubCartRepositoryAdapter` and calls real cart-service via HTTP with a Resilience4j circuit breaker.

### Resolution Details

The new `CartServiceAdapter` (`services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/cart/CartServiceAdapter.java`):

- Calls `cart-service` over HTTP via `CartHttpClient`.
- Wraps calls in a Resilience4j `CircuitBreaker` (qualified as `cartServiceCircuitBreaker`).
- Handles 404 gracefully as "empty cart" (does not trip the circuit breaker).
- Throws `CartUnavailableException` on network failures or non-404 errors.
- Maps the `ApiResponse<CartResponse>` JSON shape from cart-service into the domain `CartSnapshot`.
- Provides a `clearCart(userId)` method with non-fatal failure semantics (logs and continues).

## F-05: ~~🟡 MEDIUM~~ — ~~Profile/Runtime Mismatches~~ — RESOLVED

**Status: RESOLVED.** All services referenced by gateway routes now run under the `apps` Docker Compose profile. No fallback routing remains.

### Resolution Details

| Service | Previous profile | Current profile | Gateway routing |
| --- | --- | --- | --- |
| `notification-service` | `legacy` | `apps` | `/notifications/**` → `notification-service:8087` (dedicated) |
| `coupon-service` | `deprecated` | `apps` | `/coupons/**` → `coupon-service:8088` (dedicated) |
| `seller-finance-service` | `deprecated` | `apps` | `/seller-finance/**` → `seller-finance-service:8090` (dedicated) |
| `review-service` | `deprecated` | Removed | Reviews served by `product-service` directly (`/reviews/**` → `product-service`) |

The gateway `RouteConfig` now targets each service at its own URI with no ambiguous fallback routing. The former `review-service` was eliminated; product-service owns review endpoints directly.

## F-06: ~~🟡 MEDIUM~~ — ~~Transport Architecture Split~~ — RESOLVED

**Status: RESOLVED.** With `notification-service` moved to the `apps` profile (see F-05), both Kafka channels are fully active under the standard `--profile apps` runtime.

### Current Transport Matrix

| Channel | Producer | Topic/transport | Consumer | Status |
| --- | --- | --- | --- | --- |
| Product indexing | `product-service` | Kafka topic `product-events` | `search-service` | Working |
| Order notifications | `order-service` | Kafka topics `order.*` (dot-notation) | `notification-service` | Working — profile mismatch resolved |

The platform uses a single Kafka broker (Confluent `8.2.0`). Both asynchronous channels now have active producers and consumers in the same runtime profile.

## Recommended Remediation Order

| Priority | Fix | Reason |
| --- | --- | --- |
| ~~1~~ | ~~Add or profile-gate missing `CreateOrderUseCase` adapters.~~ | **Resolved** — all outbound ports have concrete adapters. |
| ~~2~~ | ~~Replace checkout cart stub with a real `cart-service` adapter.~~ | **Resolved** — `CartServiceAdapter` with circuit breaker. |
| ~~3~~ | ~~Move `notification-service` to the `apps` profile.~~ | **Resolved** — now under `profiles: ["apps"]`. |
| ~~4~~ | ~~Align gateway routes with Compose profiles.~~ | **Resolved** — all routes target dedicated services running in `apps`. |
| 5 | Split order-service controller responsibilities by bounded context. | Reduces blast radius and clarifies ownership for coupons, finance, admin/reporting, and returns. |
| 6 | Add startup, route, and event-pipeline smoke tests. | Prevents regressions in dependency wiring, gateway runtime shape, and Kafka contracts. |
