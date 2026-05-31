# Project Sweep — 2026-05-29

What's been done, what's wrong, and what's left.

---

## What's Been Done (pt27 → pt44)

### Security & Access Control (pt35–pt40)
- Payout audit trail with `completedBy` + `completedAt` fields
- Avatar upload with MinIO + R2-swap readiness
- Ship/Accept access-control enforcement (seller-owns-suborder gate)
- order-service IAE-as-403 sweep (consistent error codes)
- payment-service 403 handler sweep
- Status-code oracle close on lookup misses (unknown IDs → 403 not 500)

### Kafka & Event Infrastructure (pt41–pt42)
- Kafka env-override sweep across all services
- PayPal capture round-trip (5-step plan fully committed)
- `PaymentCallbackOutboxRelay` + `PaymentCompletedListener`
- Capture-endpoint dedup (keyed on `paypalOrderId`)
- `@Cacheable` on product-by-id and coupon-by-code (Redis)
- Search discoverability (published products appear in `/search`)

### Refund Saga (pt42–pt44)
- `PayPalGateway.refund()` with PayPal-Request-Id idempotency
- `PayPalRefundListener` → `PaymentRefundedEvent` publish
- `PaymentRefundedListener` (order-service) → Return marked REFUNDED
- `PaymentRefundedFinanceListener` (seller-finance) → wallet debit
- Commission tier propagation through entire refund chain
- `processed_refund` table for idempotent debit
- `sellerId` propagation from order-service → payment-service → seller-finance

### Production Hardening (pt44)
- Kafka producer health indicator (3 services)
- FX fields persisted on Payment domain (V9 columns populated)
- `PaymentResponse` exposes FX details for dispute support

### Frontend & DX (pt27–pt34)
- i18n duplicate key fix
- Migrated 39 files from lucide-react to Tabler Icons
- Dark mode token sweep (47 files, 678 swaps)
- 9 Zod schemas aligned with BE DTOs
- Cart-service wiring: product price + image from variants[]
- BA-grade journey suite (17/17 ACs across 6 chapters)

---

## What's Wrong (Known Issues)

### Critical — Blocks Production

| # | Issue | Impact | Where |
|---|-------|--------|-------|
| 1 | order-service startup crash in `apps` profile | Missing adapters for `InventoryReservationPort`, `PaymentRequestPort`, `ShippingRequestPort` | `docs/GAP-ANALYSIS.md` F-01 |
| ~~2~~ | ~~Kafka event pipeline partially broken~~ | **RESOLVED — documentation error.** All order event topics use dot-notation and are correctly aligned; notification-service is properly wired to Kafka transport. The `order-events` topic name never existed in the code. | F-02 |

### High — Functional Gaps

| # | Issue | Impact | Where |
|---|-------|--------|-------|
| 3 | order-service god-service scope | 10 controllers spanning 6 bounded contexts; hard to reason about | F-03 |
| 4 | Checkout uses stub cart adapter | Hardcoded data, ignores cart-service | F-04 |
| 5 | PayPal saga unproven on wire | All code committed + unit-tested but no sandbox run | Credential-gated |

### Medium — Tech Debt

| # | Issue | Impact | Where |
|---|-------|--------|-------|
| ~~6~~ | ~~Commission tier hardcoded to STANDARD~~ | **RESOLVED (pt45)** — `CommissionTier` enum propagated through refund chain; `RefundRequestPort` accepts enum; no more hardcoded string. | `CompleteReturnUseCase`, `OrderEventPublisherAdapter` |
| ~~7~~ | ~~FX fields not on `PaymentCompletedEvent`~~ | **RESOLVED (pt45)** — FX fields (`fxRate`, `originalCurrency`, `originalAmount`) added to `PaymentCompletedEvent` and populated by payment-service outbox. | payment-service outbox |
| ~~8~~ | ~~Consumer-only services have no Kafka health probe~~ | **RESOLVED (pt45)** — `KafkaConsumerHealthIndicator` with lag check added; consumer health visible via Spring Boot Actuator. | inventory, shipping, search, recommendations, seller-finance |
| 9 | Profile/runtime mismatches | Routes point to services not started in `apps` profile | F-05 |
| 10 | `opencode.jsonc` untracked at repo root | IDE config noise | `.gitignore` |

### Low — Polish

| # | Issue | Impact | Where |
|---|-------|--------|-------|
| 11 | Mockito self-attach warning on Java 25 | Noisy test output; will break in future JDK | All services |
| 12 | CRLF warnings on Windows | Git autocrlf noise | `.gitattributes` |
| 13 | `review-service` is an empty shell | Exists for backward compat only | Can be removed |

---

## What's Left (Roadmap)

### Credential-Gated (Ready to Ship When Creds Arrive)

| Item | Effort | Checklist |
|------|--------|-----------|
| R2 swap for avatar storage | 1 block | `docs/R2-SWAP-CHECKLIST.md` |
| PayPal sandbox manual smoke | 2 blocks | Need `PAYPAL_CLIENT_ID`/`SECRET` + `VITE_PAYPAL_CLIENT_ID` |
| Real GHN/GHTK shipping adapter | 1 block | Port exists, stub in place, need API key |

### Business-Decision-Gated

| Item | Effort | Decision Needed |
|------|--------|-----------------|
| Per-seller commission tier | 1 block | When do sellers get tiered pricing? |
| VNPay / MoMo payment methods | Multi-block | Business registration (MST + GPKD) |
| Native password reset / 2FA | 2 blocks | Build in-app or keep Keycloak console? |
| Email verification flow | 1 block | Auto-verify or require click? |
| Hero/promo/trending CMS | Multi-block | Content strategy |

### No Blockers (Can Ship Anytime)

| Item | Effort | Priority |
|------|--------|----------|
| Notifications inbox (FE bell) | 2 blocks | Medium |
| Fix order-service startup crash (F-01) | 2 blocks | High (if deploying) |
| Move notification-service to `apps` profile (F-02 profile gap, F-05) | 1 block | High (if deploying) |
| Checkout → real cart-service wiring (F-04) | 1 block | High |

---

## Test Coverage Summary (2026-05-29)

| Service | Tests | Key Coverage |
|---------|-------|-------------|
| order-service | 135 | Saga, returns, coupons, checkout, access control, outbox, PaymentRefundedListener |
| payment-service | 89 | All gateways (PayPal/Stripe/VNPay/MoMo/VietQR), refund listener, FX, health probe |
| product-service | 33 | Catalog, images, reviews, batch stats, health probe |
| seller-finance-service | 17 | Wallet credit/debit, commission tiers, idempotent refund, payouts |
| user-service | 107 | Auth, profiles, addresses, wishlist, seller stats |
| FE vitest | 165 | Components, hooks, API layer |
| FE Playwright | 19 | Browser E2E: smoke, auth, roles, search, cart |
| E2E API | 55 | Full day-in-the-life flow |

---

## Architecture Decisions Log (pt35–pt44)

| Decision | Rationale | Trade-off |
|----------|-----------|-----------|
| PayPal-Request-Id for refund idempotency | PayPal's own dedup = no local store needed | Relies on PayPal's guarantee; local `processed_refund` added for seller-finance as belt-and-braces |
| Commission tier as String on events | Enum coupling across services is fragile; String + default-to-STANDARD is backward-compat | Typos possible; mitigated by `valueOf` + catch |
| FX fields nullable on Payment | Only PayPal payments have FX; COD/VietQR/Stripe don't | Nullable fields in response (FE must handle) |
| Health probe via `partitionsFor` | Metadata-only call, no message produced, lightweight | Doesn't detect authorization issues (ACL) |
| `@Transactional` on finance listener | Atomic debit + processed_refund insert | Requires same datasource (true for seller-finance) |
| Outbox pattern for refund-requested | Reliable publish from order-service | Extra table + polling; acceptable for money-path |
| Direct `KafkaTemplate.send` for payment.refunded | Payment-service doesn't have outbox infra | At-least-once only; mitigated by consumer idempotency |
