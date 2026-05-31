# Service Split Assessment

## Reader And Goal

This document is for engineers deciding whether VNShop service boundaries are healthy or drifting toward a distributed monolith. After reading it, they should be able to:

1. Understand current runtime topology.
2. Identify concrete boundary leaks.
3. Assess risk level.
4. Execute a prioritized split-correction plan.

## Assessment Date And Method

Assessment based on direct repository evidence from service source code, gateway routes, and compose profiles. No assumptions from external diagrams are treated as truth unless confirmed in code.

## Current Runtime Topology (As Implemented)

### Active app profile

`docker-compose.yml` `apps` profile runs:

- `api-gateway`
- `user-service`
- `product-service`
- `inventory-service`
- `cart-service`
- `search-service`
- `order-service`
- `payment-service`
- `shipping-service`

### Non-app profiles

- `notification-service` is configured under `legacy` profile, not `apps`.
- `coupon-service`, `review-service`, `seller-finance-service` are under `deprecated` profile.

### Route-to-runtime mismatch

`services/api-gateway/src/main/java/com/vnshop/apigateway/infrastructure/route/RouteConfig.java` still routes:

- `/notifications/**` -> `http://localhost:8087`

When only `apps` profile is running, this route points to a service that is not started.

## Service Boundary Health

## Healthy signals

- Separate service runtimes and dedicated compose DB instances for core services.
- Outbox and Kafka are present in core flow:
  - `services/order-service/.../infrastructure/outbox/OutboxPublisher.java`
  - `services/product-service/.../infrastructure/event/ProductEventPublisher.java`
  - `services/search-service/.../infrastructure/kafka/ProductEventConsumer.java`
- Domain ports exist in order-service for external dependencies.

## Boundary leak signals

### 1) Order-service scope inflation

`order-service` exposes mixed domain surfaces:

- checkout and order lifecycle
- coupon management
- finance admin/seller endpoints
- dispute management
- dashboard endpoints

Evidence: multiple controllers under:

- `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/web/`
- `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/admin/`

This is a strong "God service" trend.

### 2) Declared outbound contracts without concrete adapters

Order domain defines outbound ports:

- `PaymentRequestPort`
- `ShippingRequestPort`
- `InventoryReservationPort`

in:

- `services/order-service/src/main/java/com/vnshop/orderservice/domain/port/out/`

`CreateOrderUseCase` depends on these ports:

- `services/order-service/src/main/java/com/vnshop/orderservice/application/CreateOrderUseCase.java`

But no concrete class implementing these three ports was found in order-service source during AST scan.

### 3) Stub in critical checkout dependency

`CartRepositoryPort` is implemented by:

- `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/cart/StubCartRepositoryAdapter.java`

This adapter returns hardcoded cart data, signaling incomplete real integration in a key orchestration path.

### 4) Event contract topology inconsistency risk

- Order outbox publishes topic `order-events`.
- Notification consumer handlers use message patterns `order.created`, `order.cancelled`, `order.shipped`, `shipment.updated`.

Evidence:

- `services/order-service/.../OutboxPublisher.java`
- `services/notification-service/src/notification/application/kafka-notification.consumer.ts`

Additionally, notification bootstrap (`services/notification-service/src/main.ts`) shows HTTP app start path only; Kafka transport wiring is not visible there.

## Distributed Monolith Risk Assessment

### Verdict

VNShop is not a pure monolith, but current trajectory indicates **medium-high distributed monolith risk**.

### Why medium-high

- Positive: multiple deployable services, separate data stores, eventing exists.
- Negative: oversized central service, incomplete adapter boundaries, profile-route mismatch, stubs in core business flow.

If left unchanged, operational coupling and release coordination cost will increase sharply.

## Split Correction Plan (Priority Ordered)

### P0 — Runtime and contract consistency

1. Align gateway routes with active profile topology.
   - Either run notification in `apps`, or remove/disable `/notifications/**` route in `apps` mode.
2. Standardize event topology for order/notification interaction.
   - Pick canonical topic strategy and enforce producer-consumer compatibility.

### P0 — Complete outbound adapter layer for order-service

Implement concrete adapters for:

- `PaymentRequestPort`
- `ShippingRequestPort`
- `InventoryReservationPort`

Remove placeholder behavior from production orchestration paths.

### P1 — Reduce order-service bounded-context overload

Keep order-service focused on:

- checkout orchestration
- order lifecycle
- returns tied directly to order lifecycle

Move or isolate:

- finance administration surfaces
- dashboard/reporting surfaces
- any non-order ownership endpoints

### P1 — Remove stubs from critical business path

Replace `StubCartRepositoryAdapter` for real integration contract (API/event/read model), with explicit failure semantics.

### P2 — Governance hardening

1. Add boundary checks to review gate:
   - no new cross-domain controller additions in order-service without owner approval.
2. Add contract tests for cross-service event/API compatibility.
3. Keep gateway config environment-driven, not hardcoded localhost URIs for long-term environments.

## Decision Rules For Future Splits

Use these rules before creating or merging domains:

1. One service should have one primary business language.
2. If endpoint set needs different ownership cadence, split.
3. If one service needs coordinated release with many others every sprint, boundary likely wrong.
4. If port interfaces exist but adapters are missing for more than one milestone, treat as architecture debt blocker.

## Exit Criteria For "Healthy Split"

VNShop can be considered on healthy split trajectory when all are true:

- Gateway routes match active runtime profiles.
- Order outbound ports are concretely implemented for payment/shipping/inventory.
- No hardcoded stub adapters in checkout critical path.
- Order-service no longer hosts unrelated finance/admin dashboard concerns.
- Event contracts between order and notification are versioned and verified by tests.
