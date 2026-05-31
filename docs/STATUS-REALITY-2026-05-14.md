# VNShop Status Reality - 2026-05-14

This is the current single source of truth for the findings previously listed in `docs/GAP-ANALYSIS.md`, `docs/FE-PLAN.md`, and `docs/WHATS-WRONG.md`.

Read this before using the older docs. `GAP-ANALYSIS.md` and the "What's Wrong" section in `FE-PLAN.md` contain stale findings from an earlier architecture state. `WHATS-WRONG.md` is the newest audit and is closest to the current codebase, but this file reconciles it against the working tree.

## Executive Status

Current state: the original P0/P1 boot and event-pipeline blockers are mostly resolved, but a few production-readiness gaps remain.

- Order-service no longer has the original missing-bean boot blocker for inventory, payment, and shipping ports.
- Checkout no longer uses the old hardcoded cart adapter; it calls cart-service with bounded timeouts.
- Notification-service is now started in the `apps` profile and wires Kafka transport.
- Docker Compose now has per-service Postgres containers for active core services.
- Order-service now has saga state, outbox retry/dead-letter behavior, projection listeners, and compensation-confirmation hooks.
- Still open: downstream compensation confirmation events, cart-service circuit breaker, partial CQRS scope, and production orchestration hardening.
- Important regression risk: order-service still declares stub beans for inventory, payment, and shipping ports in config while real gRPC adapters also exist. That must be resolved or verified by startup tests.

## Which Docs Are Stale

| Document | Current usefulness | Notes |
|---|---|---|
| `docs/GAP-ANALYSIS.md` | Historical only | Its F-01 through F-06 findings describe the old broken state. Most are fixed or partially fixed now. |
| `docs/FE-PLAN.md` | Useful for endpoint inventory and frontend checklist | Its backend "What's Wrong" scorecard is stale. Use this file for backend status. |
| `docs/WHATS-WRONG.md` | Mostly current | Correctly lists remaining open issues, but this file adds a direct reconciliation table and flags the duplicate stub-bean risk. |

## Reconciled Finding Matrix

| Old ID / topic | Current status | Reality |
|---|---|---|
| F-01: order-service startup crash from missing outbound ports | Partially fixed / needs verification | Real gRPC adapters exist for inventory reservation, payment request, and shipping request. However, config still declares stub beans for the same three ports. If Spring creates both sets, startup may fail with duplicate beans or inject the wrong adapter unless profile/conditional behavior is added. |
| F-02: Kafka order notification pipeline broken | Mostly fixed | Outbox topic naming now maps event types to dotted topics such as `order.created`. Notification-service now connects Kafka transport. Order projection and finance listeners consume dotted order topics. Need runtime smoke test to prove notification side effects. |
| F-03: order-service god-service scope | Mostly fixed / still watch | Coupon and seller-finance standalone services are back in `apps`; old duplicate controllers were reported deleted. Review-service remains legacy while review APIs appear owned by product-service. Admin/order/returns/invoice still live in order-service by design. |
| F-04: stub cart in checkout path | Fixed, with reliability gap | `CartServiceAdapter` calls cart-service over HTTP and removed the hardcoded cart snapshot path. It has connect/read timeouts and typed failure. It does not yet have a circuit breaker. |
| F-05: profile/runtime mismatch | Mostly fixed | notification-service, coupon-service, and seller-finance-service are now in `apps`. review-service remains `legacy`, matching migration toward product-service review ownership. Gateway routes use Docker DNS defaults through `vnshop.routes.*`. |
| F-06: transport architecture split | Partially fixed | Kafka is wired for notification-service and product/search remains the working reference. gRPC proto and order-side gRPC adapters exist. End-to-end transport smoke tests are still needed. |
| FE-PLAN issue: single Postgres SPOF | Fixed for core services | Compose defines dedicated Postgres containers for user, product, order, payment, inventory, search, and shipping. Some ancillary services still use `postgres-legacy`. |
| FE-PLAN issue: CQRS claimed but absent | Partial | Order-service now has order summary projection, query handler, projection listener, and read-side order list path. Product-service still lacks comparable projection/query split. |
| FE-PLAN issue: saga orchestration missing | Mostly fixed | Saga orchestrator, saga state, compensation timeout finalizer, and compensation listener exist. Downstream compensation completion publishers are still missing. |
| FE-PLAN issue: zero tracing | Mostly fixed | Jaeger is in compose and active services have OTLP tracing env vars. Full trace quality still needs runtime verification. |
| FE-PLAN issue: gateway hardcoded localhost | Fixed | Gateway route defaults use container DNS names such as service-name:port and are externally configurable through `vnshop.routes.*`. |
| FE-PLAN issue: no gRPC mesh | Partial | Proto files and order-side clients exist. Confirm server implementations and runtime coverage before calling it complete. |
| FE-PLAN issue: coverage gates too low | Reported fixed | `WHATS-WRONG.md` says Java and NestJS coverage gates now exist. This was not fully re-verified in this pass. |
| Production ops: K8s / rolling / canary | Partial | K8s and Helm scaffolding exists under infra, but rolling/canary production strategy is not proven by this pass. |

## Fixed Since The Old Gap Analysis

### Boot wiring

The old claim that `CreateOrderUseCase` had no concrete inventory/payment/shipping adapters is no longer accurate. The codebase now contains gRPC adapters for all three outbound ports.

Caveat: there are still stub `@Bean` definitions for the same ports in order-service config. This is the highest-priority verification item because it can cause ambiguous beans or accidentally use stub behavior.

### Cart integration

Checkout no longer relies on the old hardcoded `StubCartRepositoryAdapter` path. The current adapter calls cart-service, passes `x-user-id`, parses the standard API response envelope, and bounds latency with connect/read timeouts.

Remaining reliability gap: no circuit breaker around cart-service yet.

### Kafka and notification runtime

The old mismatch around HTTP-only notification-service is stale. Notification-service now connects a Kafka microservice transport before listening on HTTP. Docker Compose starts notification-service under the `apps` profile.

Order-service now publishes outbox events to dotted topics derived from event type names. Order projection and finance listeners consume these dotted topics.

### Runtime profiles

The earlier `apps` profile mismatch is mostly resolved. Notification, coupon, seller-finance, and core Java/Nest services are now in `apps`. Review-service is intentionally left in `legacy` while review APIs are owned elsewhere.

### Database split

The old "single Postgres container serving all schemas" claim is stale for core services. Compose now defines dedicated Postgres services for core bounded contexts.

Ancillary services such as notification, coupon, and seller-finance still use the legacy Postgres container, so do not overclaim full database-per-service purity across every service.

### Tracing and observability

Jaeger is present in Compose and active services have OTLP tracing endpoints configured. This upgrades the old "zero distributed tracing" finding from broken to mostly fixed.

Runtime trace propagation still needs an end-to-end smoke test.

## Still Open

### 1. Downstream compensation confirmation events

Order-service is ready to consume compensation confirmations on `inventory.released`, `payment.refunded`, and `shipping.cancelled`. The current code search only found those topics in order-service listener/comments, not in downstream publishers.

Impact: compensating sagas still rely on timeout finalization unless downstream services publish the confirmation events.

Required fix: inventory-service, payment-service, and shipping-service should publish confirmation events after successful release/refund/cancel compensation work.

### 2. Cart-service circuit breaker

Cart integration now has timeouts, but a sustained cart-service outage still fails request-by-request. A circuit breaker would short-circuit during outage windows.

Required decision: choose Resilience4j or another standard fault-tolerance layer, then apply consistently to synchronous edges.

### 3. CQRS scope is partial

Order list reads have moved toward projection/query handling. Product-service and other hot read paths have not clearly adopted the same pattern.

Required decision: either extend CQRS/read projections beyond order-service or rename the architecture claim to "outbox-backed order projections" to avoid overclaiming.

### 4. gRPC mesh is partial until runtime-proven

Proto files and order-side clients exist. The remaining question is whether every target service exposes the expected server implementation and whether compose startup proves the mesh works.

Required verification: boot the apps profile and exercise order placement through inventory, payment, and shipping edges.

### 5. Production orchestration is not proven

K8s and Helm scaffolding exists, but this pass did not prove rolling update, canary, production secrets, ingress, autoscaling, or release rollback behavior.

Required fix: make deployment strategy explicit and testable before production launch.

## Feature Gaps Still Relevant For Frontend

These remain valid frontend/product gaps unless separately implemented later:

| Area | Status |
|---|---|
| Product variants | Open |
| Guest cart | Open |
| Wishlist | Open |
| Real carrier tracking endpoint | Open |
| Digital invoice UI | Open |
| Saved payment methods / installments | Open |
| Re-order flow | Open |
| i18n vi/en | Open |
| Push/SSE/WebSocket notifications | Open; polling remains safest assumption |
| GDPR delete-account / compliance hardening | Open |

## Recommended Next Verification Order

1. Run an order-service startup test under the `apps` configuration and resolve duplicate/stub port beans.
2. Run full compile/tests for order, payment, inventory, shipping, notification, and gateway.
3. Run a local apps-profile smoke test: place order, publish/consume order event, verify projection and notification behavior.
4. Add downstream compensation confirmation publishers and test COMPENSATING to FAILED without waiting for timeout.
5. Decide circuit-breaker library and apply to cart-service adapter.
6. Decide whether CQRS is an actual architecture target or only an order-service read projection pattern.

## Current Bottom Line

The platform has moved from "critical boot/event blockers" to "mostly wired, needs runtime proof and production hardening." The old gap documents should not be used as current truth without this reconciliation.
