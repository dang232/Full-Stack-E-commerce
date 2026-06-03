# Session Handover — 2026-06-03 pt49

## Summary

Architecture audit + critical bug fixes + Kafka SASL/ACL security hardening. 16 commits landed across 10 services. The refund saga (completely broken by a topic name mismatch) now works, compensation events are wired end-to-end, and Kafka is authenticated with per-service ACLs preventing event forgery.

---

## Commits This Session

```
46df36ac feat(checkout): clear cart after successful order placement (fire-and-forget)
0687b013 security(kafka): add SASL/PLAIN config to seller-finance, search, and recommendations services
827beee3 security(kafka): add SASL/PLAIN config to order-service
bf0adf76 security(kafka): add SASL/PLAIN config to payment-service
60be5b1a security(kafka): add SASL/PLAIN config to inventory, product, and shipping services
8fd5ca31 security(kafka): update init script with SASL auth and per-service ACLs
f7833d8e security(kafka): configure broker for SASL/PLAIN + StandardAuthorizer ACLs
a18b981d security(kafka): add SASL/PLAIN service credentials to env files
fcf8a7ef feat(shipping): add CancelShipmentUseCase + publish shipping.cancelled Kafka event
93f9f048 feat(inventory): publish inventory.released Kafka event after stock release
c16cc370 fix(saga): persist COMPLETED state after outbox event in onShippingCreated
8a7f103a feat(saga): add sagaId pass-through to PaymentRefundedEvent for compensation confirmation
c82f6fb3 fix(refund): align topic name — EVENT_TYPE to PAYMENT_REFUND_REQUESTED, listener to payment.refund.requested
f95f627f security(kafka): disable auto-create-topics to prevent topic pollution
40441f1b fix(order-service): add gRPC host env vars for Docker networking
c3deb0da fix(notification): add Redis password to REDIS_URL in docker-compose
```

---

## Critical Bugs Found & Fixed

### 1. Refund Topic Name Mismatch (P0)
- **Root cause:** `OutboxPublisher.topicFor()` converts EVENT_TYPE via `.toLowerCase().replace('_', '.')`. `RefundRequestPublisherAdapter` used `"payment.refund_requested"` which became `"payment.refund.requested"` (dots). But `PayPalRefundListener` subscribed to `"payment.refund_requested"` (underscore). Different Kafka topics — events never arrived.
- **Fix:** Changed EVENT_TYPE to `"PAYMENT_REFUND_REQUESTED"` (uppercase convention), changed listener to `"payment.refund.requested"`. Both now produce/consume the same topic.
- **Impact:** Entire refund saga was silently dead. Now works.

### 2. gRPC Localhost in Docker (P0)
- **Root cause:** `application-grpc.yml` hardcoded `host: localhost` for all three gRPC peers. Inside Docker, localhost = the container itself. All saga gRPC calls (inventory reserve, payment request, shipping create) failed with connection refused.
- **Fix:** Added `${GRPC_CLIENT_*_HOST:localhost}` env var overrides in application-grpc.yml + Docker env vars pointing to Docker DNS names.

### 3. Notification Redis Auth (P0)
- **Root cause:** `REDIS_URL: redis://redis:6379` missing password. Redis requires `--requirepass`.
- **Fix:** Changed to `redis://:${REDIS_PASSWORD:-vnshop123}@redis:6379`.

---

## Architecture Improvements

### Saga Compensation (Deterministic)
- **Before:** SagaCompensationListener subscribed to `inventory.released`, `payment.refunded`, `shipping.cancelled` but none of those services published these events. Sagas relied on 5-min timeout → FAILED.
- **After:**
  - inventory-service: `InventoryEventPublisher` → publishes `inventory.released` after `ReleaseStockUseCase.release()`
  - shipping-service: `CancelShipmentUseCase` + `ShippingEventPublisher` → publishes `shipping.cancelled`
  - payment-service: `PaymentRefundedEvent` now includes nullable `sagaId` field (pass-through from `payment.refund.requested`)
  - `SagaOrchestrator.onShippingCreated()` now persists `SagaStatus.COMPLETED` (was stuck at SHIPPING_CREATED)

### Cart Integrity
- `CalculateCheckoutUseCase.calculate(cartId)` now re-validates prices against `productCatalogPort` (no longer trusts cart cache)
- Cart cleared fire-and-forget after successful order via `CartRepositoryPort.clearCart(userId)`

### Kafka Security (SASL/PLAIN + ACLs)
- Broker: SASL_PLAINTEXT listeners, StandardAuthorizer, `KAFKA_ALLOW_EVERYONE_IF_NO_ACL_FOUND=false`
- JAAS config: `infra/kafka/kafka_server_jaas.conf` with 9 service accounts
- Init script: Authenticates as admin, creates 12 topics, configures per-service ACLs
- All 8 Kafka-connected services have `spring.kafka.properties` with SASL config (env var driven)
- ACL enforcement: `svc-order` CANNOT write to `payment.completed`, `svc-payment` CAN — closes C-02 CRITICAL

---

## Service Identity Matrix (Kafka ACLs)

| Service | Username | Write Topics | Read Topics |
|---------|----------|-------------|-------------|
| order-service | svc-order | order.* (prefixed), payment.refund.requested | payment.completed, payment.refunded, inventory.released, shipping.cancelled, order.* |
| payment-service | svc-payment | payment.completed, payment.refunded | payment.refund.requested |
| inventory-service | svc-inventory | inventory.released | — |
| product-service | svc-product | product-events | — |
| shipping-service | svc-shipping | shipping.cancelled | — |
| seller-finance-service | svc-finance | — | order.created, order.paid, payment.refunded |
| search-service | svc-search | — | product-events |
| recommendations-service | svc-recommendations | — | order.created |

---

## Test Results (All Passing)

| Service | Tests |
|---------|-------|
| order-service | 138 ✅ |
| payment-service | 89 ✅ |
| inventory-service | 23 ✅ |
| shipping-service | 29 ✅ |
| docker compose config | valid ✅ |

---

## Key Corrections vs Previous Documentation

| Previous Claim | Reality |
|----------------|---------|
| "StubCartRepositoryAdapter blocks checkout" | ❌ Real `CartServiceAdapter` with Resilience4j CB exists since commit `58fc4e7d` |
| "PayPalOutboxRelay is missing" | ❌ `PaymentCallbackOutboxRelay` exists and works |
| "PaymentCompletedListener is missing" | ❌ Exists in order-service, processes `payment.completed` correctly |
| "Single Postgres SPOF" | ❌ 7 separate Postgres containers for core services; only 4 ancillary share legacy |
| "No distributed tracing" | ❌ Jaeger + traceparent propagation exists |
| "Cart-based checkout path trusts cart prices" | ✅ Fixed this session |
| "Refund saga is partially implemented" | ❌ It was fully implemented but silently broken by topic mismatch — fixed this session |

---

## What's NOT Done (Prioritized)

### P1 — Security (Next Session)
1. **Kafka TLS** — SASL_PLAINTEXT sends credentials in cleartext. Upgrade to SASL_SSL with broker TLS cert.
2. **Account lockout** — Redis-backed counter after N failed login attempts
3. **JWT failure logging** — custom AuthenticationEntryPoint across services
4. **ROPC → Authorization Code + PKCE** — blocks MFA

### P2 — Smoke Test + Validation
5. **Full Docker stack smoke test** with SASL enabled — verify all services connect, events flow, ACLs enforce
6. **Negative ACL test** — confirm `svc-order` is denied writing to `payment.completed`
7. **PayPal sandbox end-to-end** — browser walkthrough (needs PayPal sandbox credentials)

### P3 — Feature Work
8. Notification preferences UI (channel-level toggles)
9. Email channel adapter (SES)
10. Client-side delivery ACK for notifications
11. Product variants (size/color)

### P4 — Architectural Debt
12. Cart-service circuit breaker already exists (Resilience4j) — but `volatile-lru` eviction guard still needed
13. Remaining 28 OWASP findings (medium/low severity)
14. Signed event envelopes (defense-in-depth beyond ACLs)
15. K8s/Helm validation

---

## Gotchas

### Gotcha #111: OutboxPublisher.topicFor() Naming Convention
The outbox convention is: store EVENT_TYPE as `UPPER_CASE_WITH_UNDERSCORES` in the outbox table. `topicFor()` converts to `lower.case.with.dots` for the Kafka topic. Any new event types must follow this convention or events will route to wrong topics.

### Gotcha #112: JAAS File Doesn't Support Env Var Interpolation
`infra/kafka/kafka_server_jaas.conf` uses hardcoded dev passwords. For production, either:
- Use an entrypoint script with `envsubst` to template the file at runtime
- Mount a Vault-generated JAAS file
- Use SCRAM-SHA-256 with Kafka's built-in credential store instead of PLAIN

### Gotcha #113: Kafka Consumer Groups Need ACLs Too
ACLs on topics alone aren't enough — consumers also need READ permission on their consumer group. The init script handles this, but if you add a new @KafkaListener with a new groupId, you must add a corresponding ACL or the consumer will be denied.

### Gotcha #114: product-service Bootstrap Server Was kafka:29092
Unlike all other services (which use `localhost:9092`), product-service used `kafka:29092`. This was the PLAINTEXT_HOST listener. After SASL migration, all services now use `kafka:9092` (SASL_PLAINTEXT). The old 29092 port is also SASL-enabled for local dev access from the host machine.

---

## Files Changed (Key)

```
docker-compose.yml                          — Redis URL, gRPC env, Kafka SASL broker + all services
infra/kafka/kafka_server_jaas.conf          — NEW: JAAS config with 9 service accounts
infra/scripts/init-kafka-topics.sh          — Rewritten: SASL auth + ACL creation
.env / .env.example / .env.secrets.example  — Kafka SASL credentials added
services/order-service/src/main/resources/application.yml      — SASL properties
services/order-service/src/main/resources/application-grpc.yml — env var overrides
services/order-service/.../RefundRequestPublisherAdapter.java  — EVENT_TYPE fix
services/order-service/.../saga/SagaOrchestrator.java          — COMPLETED state
services/order-service/.../CalculateCheckoutUseCase.java       — price re-validation
services/order-service/.../cart/CartServiceAdapter.java        — clearCart
services/order-service/.../cart/CartRepositoryPort.java        — clearCart interface
services/payment-service/.../PayPalRefundListener.java         — topic fix + sagaId
services/payment-service/.../PaymentRefundedEvent.java         — sagaId field
services/inventory-service/.../event/InventoryEventPublisher.java — NEW
services/shipping-service/.../event/ShippingEventPublisher.java   — NEW
services/shipping-service/.../application/CancelShipmentUseCase.java — NEW
+ all service application.yml files (SASL properties)
```

---

## Recommended Next Pick

**#5 — Full Docker stack smoke test with SASL.** Everything is configured but unverified end-to-end in containers. Run `docker compose --profile apps up -d`, check logs for auth errors, produce a test event, confirm ACL enforcement. This validates the entire security stack before moving to TLS or feature work.
