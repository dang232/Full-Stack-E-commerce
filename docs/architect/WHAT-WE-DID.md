# What We Did (pt27 → pt48)

Chronological work log covering all sessions from pt27 through pt48.

---

## pt27–pt34: Frontend Polish & QA Foundation

**Theme:** Make the FE production-ready with consistent UX and comprehensive testing.

- **i18n duplicate key fix** — resolved conflicting translation keys across components
- **Tabler Icons migration** — replaced lucide-react across 39 files for consistent iconography
- **Dark mode token sweep** — 47 files, 678 CSS variable swaps for full dark mode support
- **9 Zod schemas aligned with BE DTOs** — type-safe API layer (order, payment, notification, etc.)
- **Cart-service wiring** — product price + image from variants[] (was hardcoded)
- **BA-grade journey suite** — 17/17 acceptance criteria across 6 chapters (Playwright E2E)
- **Chapter-6 flake root-cause** — identified and fixed timing-dependent test failures

---

## pt35: Payout Audit Trail

- Added `completedBy` + `completedAt` fields to Payout domain
- Admin completes payout → audit fields stamped automatically
- Seller-finance controller returns audit data in response

---

## pt36: Avatar Upload + R2 Readiness

- S3ObjectStorageAdapter with MinIO backend
- Presigned upload URL generation (SHA256 metadata)
- Public URL serving (path-style vs virtual-host conditional)
- R2-swap checklist documented (`docs/R2-SWAP-CHECKLIST.md`)

---

## pt37: Ship/Accept Access Control

- Seller-owns-suborder gate on ship and accept operations
- JWT userId compared to subOrder.sellerId before allowing mutation
- Consistent 403 on ownership mismatch

---

## pt38: Order-Service IAE-as-403 Sweep

- IllegalArgumentException was returning 500 → now returns 403
- Consistent error codes across all order-service endpoints
- ApiExceptionHandler updated for domain exceptions

---

## pt39: Payment-Service 403 Handler Sweep

- Missing AccessDeniedException handler added
- All payment endpoints return proper 403 on auth failures

---

## pt40: Status-Code Oracle Close

- Unknown IDs in lookup operations now return 403 (not 500)
- Prevents information leakage about which resources exist

---

## pt41: Kafka Env-Override Sweep

- All services use `${KAFKA_BOOTSTRAP_SERVERS:localhost:9092}` consistently
- Removed hardcoded broker addresses

---

## pt42: PayPal Capture + Refund Saga Start

- PayPal capture round-trip (5-step plan fully committed)
- `PaymentCallbackOutboxRelay` + `PaymentCompletedListener`
- Capture-endpoint dedup (keyed on `paypalOrderId`)
- `@Cacheable` on product-by-id and coupon-by-code (Redis)
- Search discoverability (published products appear in `/search`)
- `PayPalGateway.refund()` with PayPal-Request-Id idempotency
- `PayPalRefundListener` → `PaymentRefundedEvent` publish

---

## pt43: Buyer-Visible Refund Saga Closed

- `PaymentRefundedListener` (order-service) → Return marked REFUNDED
- `PaymentRefundedFinanceListener` (seller-finance) → wallet debit
- Commission tier propagation through entire refund chain
- `processed_refund` table for idempotent debit
- `sellerId` propagation from order-service → payment-service → seller-finance

---

## pt44: Production Hardening

- Kafka producer health indicator (3 services)
- FX fields persisted on Payment domain (V9 columns populated)
- `PaymentResponse` exposes FX details for dispute support
- Commission tier as enum (no more hardcoded STANDARD string)

---

## pt45: FX Event Pipeline + Consumer Health

- FX fields (`fxRate`, `originalCurrency`, `originalAmount`) added to `PaymentCompletedEvent`
- Per-seller commission tier support
- `KafkaConsumerHealthIndicator` with lag check across 5 consumer services
- Consumer health visible via Spring Boot Actuator

---

## pt46: F-01/F-04 Fixes + Metrics

- order-service startup crash fixed (missing adapter beans for apps profile)
- Checkout → real cart-service wiring (stub replaced)
- Prometheus metrics endpoints on all services
- Batch commission tier lookups
- R2 swap code verified working

---

## pt47: Notification Platform

**Single atomic commit: 106 files changed, 9,740 insertions.**

Backend (NestJS):
- PostgreSQL → MongoDB migration (Mongoose, TTL indexes, $facet aggregation)
- 12 notification types (ORDER_CREATED through PAYOUT_COMPLETED)
- DeliveryStatus state machine: QUEUED → SENT → DELIVERED → OPENED
- WebSocket gateway (socket.io, /ws/notifications, JWT auth)
- Redis adapters: SET NX dedup, MULTI transaction drain, 2-min TTL + heartbeat
- Kafka consumer (11 topics, Vietnamese copy, deepLink, threading)
- REST controller (paginated list, threads, unread count, mark-read)

Frontend (React):
- useNotificationSocket hook (manual reconnect, cache invalidation)
- NotificationBell with type-specific icons, date grouping, pulse animation
- NotificationToast (Sonner, clickable deepLink, 5s auto-dismiss)
- /notifications page with filters, threads, pagination, URL-driven state
- Zod schemas + API endpoints for all notification operations

Tests: 86/86 BE, 169/169 FE, 3/3 E2E

---

## pt48 (This Session): Security Hardening + Preferences Enforcement

### Notification Preferences Enforcement
- `SendNotificationUseCase`: checks `isChannelEnabled()` before persisting; short-circuits if all channels disabled
- `NotificationCreatedHandler`: checks IN_APP preference before WebSocket delivery
- `SocketioNotificationGateway`: filters catch-up notifications against current preferences on reconnect
- `NotificationCreatedEvent`: added `suppressedChannels` array for downstream awareness

### F-05 Profile Mismatch Fix
- `monitoring-service-v2` added to `profiles: ["apps"]` in docker-compose.yml

### OWASP Security Audit (50 findings)
- Full pentest-methodology scan: 2 CRITICAL, 12 HIGH, 24 MEDIUM, 12 LOW
- Covered: A01 Broken Access Control, A02 Crypto Failures, A03 Injection, A04 Insecure Design, A05 Misconfiguration, A07 Auth Failures, A08 Data Integrity, A09 Logging, A10 SSRF

### Security Hardening Phase 1 (committed: `57a9bab2`)
- product-service: require auth for mutation endpoints
- api-gateway: SELLER role enforcement on `/seller/**`, `/sellers/me/**`
- order-service + seller-finance: ADMIN role on `/admin/**`
- user-service: password min 8 + complexity requirement
- api-gateway: rate limiting on `/auth/**`
- api-gateway: security headers (X-Frame-Options, X-Content-Type-Options)
- user-service: generic error messages in catch-all handler
- product-service + user-service: Swagger disabled by default
- search/product/order: max page size 50
- user-service: removed hardcoded Keycloak admin client secret default

### Security Hardening Phase 2 (in progress)
- Invoice endpoint: ownership check (buyer or seller must own order)
- Question answer: restrict to product's seller only
- PayPal capture: cross-validate paypalOrderId matches stored reference
- Review vote: per-user dedup to prevent manipulation
- DB passwords: externalized via `${DB_PASSWORD:vnshop}` across 10 services
- Redis: `--requirepass` + client password config

### R2 Storage Verified
- Already fully wired from pt46 — no code changes needed
- `.env` has R2 credentials, path-style logic already conditional
