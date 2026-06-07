# VNShop Production Readiness Fix Plan

**Date:** 2026-06-06 (revised v2)  
**Scope:** All 19 audit findings (Tier 1–3) + 5 Tier 4 capabilities + payment webhook reliability + e-invoice compliance  
**Strategy:** Domain-grouped parallel tracks with sub-agent execution  
**Target:** Local environment first, server deployment follows separately  
**Tracks:** 7 total (Track 5.4 API versioning deprioritized/removed)  
**Timeline:** Open-ended, track by track — no hard deadline  
**Revision notes:** Incorporates BA + Tech Lead reviews — corrected existing implementations, added Kafka compensation fallback, cache-aside pattern, MikroORM choice, webhook reliability track, e-invoice microservice, GDPR audit (not redesign).

---

## Track 1: Security & Secrets

**Issues:** #3 (secrets in env), #4 (no fraud detection), #5 (rate limiting), #17 (dev CORS in prod), #18 (no mTLS)

### 1.1 Secrets Management

- Replace all `.env` defaults containing `changeme` or placeholder credentials
- Create `secrets.env.local` (gitignored) for local dev with real-looking but non-production values
- Add Docker secrets support in `docker-compose.yml` for sensitive values (DB passwords, API keys, JWT secrets)
- Add `infra/secrets/vault-config.yaml` template for production HashiCorp Vault integration
- Validation: services fail-fast on startup if required secrets are missing (no silent fallback)

**Acceptance criteria:**
- `docker compose up` with no `secrets.env.local` → at least one service logs a clear error and exits non-zero
- `git grep -r "changeme"` returns zero matches in any service config file
- `secrets.env.local` is listed in `.gitignore` and does not appear in `git status`
- Vault template exists at `infra/secrets/vault-config.yaml` and references all secret keys

---

### 1.2 Rate Limiting — Refactor Existing RedisRateLimiter to Per-Route Beans

The API Gateway already has `RedisRateLimiter(10, 20, 1)` with `userKeyResolver` at `RouteConfig.java:74-77`. This is not greenfield — replace the single global bean with route-scoped beans and add authenticated-user tiers.

**Why tiers matter:** Vietnamese mobile carriers use CGNAT heavily — many buyers share a single IP. Strict per-IP limits would block real users.

**Per-route rate limiter beans:**
- `paymentRateLimiter`: anonymous 1 req/sec / burst 2; authenticated 5 req/sec / burst 10
- `authRateLimiter`: anonymous 3 req/sec / burst 5; authenticated 10 req/sec / burst 20
- `searchRateLimiter`: anonymous 5 req/sec / burst 10; authenticated 20 req/sec / burst 40
- `generalRateLimiter`: anonymous 10 req/sec / burst 20; authenticated 30 req/sec / burst 60

Key resolver: if `Authorization` header present and valid → key on user ID; otherwise key on IP.
Add `X-RateLimit-Remaining` and `X-RateLimit-Reset` response headers.

**Acceptance criteria:**
- Payment endpoint unauthenticated: 429 after 2 rapid requests; same IP with JWT: allows 10
- Two users behind same IP (CGNAT) can both hit auth at 3 req/sec without blocking each other
- 429 responses include `X-RateLimit-Remaining: 0` and `X-RateLimit-Reset` headers
- Integration test covers payment route anonymous vs authenticated tiers

---

### 1.3 Fraud Detection

- Velocity check: max 3 orders per hour per user
- Amount threshold: flag orders > 10,000,000 VND for manual review
- Device fingerprint logging via `X-Device-Fingerprint` header
- Geographic anomaly: flag if shipping country ≠ IP geolocation country
- Action: `FLAGGED` order status, do not auto-block
- Admin notification via Kafka topic `order.fraud-flagged`

**Timeout coordination with inventory reservation (TTL=15min):**
1. Auto-escalate: flagged orders with reservation age > 10 min → Slack/email admin alert
2. Extended TTL: fraud middleware calls inventory extend-reservation with `ttlMinutes: 60`

**Acceptance criteria:**
- 4th order within 60 min for same user → `FLAGGED` status
- Order > 10M VND produces message on `order.fraud-flagged` Kafka topic
- Flagged order's reservation TTL extended to 60 min (Redis TTL check)
- Auto-escalation fires when reservation age exceeds 10 min

---

### 1.4 CORS Configuration — Verify and Document Only

CORS is already env-conditional via `${GATEWAY_CORS_ALLOWED_ORIGINS:...}` in `application.yml:28`. No code changes needed.

**Tasks:** Audit existing config, confirm default doesn't include localhost, document in `docs/api/cors-configuration.md`, add integration test rejecting localhost origin in prod config.

**Acceptance criteria:**
- Default value contains no localhost origins
- Integration test: prod config rejects `Origin: http://localhost:5173`
- Documentation exists at `docs/api/cors-configuration.md`

---

### 1.5 mTLS Between Services

- Verify Istio PeerAuthentication is `STRICT` mode
- Ensure all deployments have sidecar injection enabled
- Add AuthorizationPolicy per service (allow only expected callers)
- Document Docker Compose vs K8s distinction

**Acceptance criteria:**
- `kubectl get peerauthentication` shows `mode: STRICT`
- All deployments have `sidecar.istio.io/inject: "true"`
- At least one AuthorizationPolicy per service exists
- `docs/infra/mtls.md` explains local vs K8s difference

---

## Track 2: Payment & Checkout

**Issues:** #1 (inventory reservation verification), #2 (payment sandbox creds), #10 (no tax), #12 (hardcoded shipping), + chargeback handling  
**Phase:** 1 — runs in parallel with Track 1. Payment credentials already follow `${METHOD_ENABLED:false}` env-var pattern.

### 2.1 Inventory Reservation — Verify and Harden Existing Flow

The reservation infrastructure already exists: `ReserveStockUseCase.java`, `ReleaseStockUseCase.java`, saga calls `inventoryReservationPort.reserve()` as step 1 via gRPC (`GrpcInventoryServer.java`). Saga states: STARTED → INVENTORY_RESERVED → PAYMENT_CHARGED → SHIPPING_CREATED → COMPLETED.

**Work items:**
- Verify gRPC reservation flow works E2E in local Docker Compose
- Add TTL expiry via Redis keyspace notifications (not polling) — on key expiry, publish `inventory.reservation-expired` event
- Add Kafka fallback for compensation when circuit breaker is OPEN:
  - When `release()` throws `CallNotPermittedException`, publish to `inventory.release-requested` Kafka topic
  - Inventory-service consumes this topic and processes releases when it recovers
  - TTL (15 min) remains as absolute backstop if Kafka consumer also fails
- Fix `GrpcClientConfig.java` — add keepalive + `@PreDestroy` shutdown:
  - `.keepAliveTime(30, SECONDS)`, `.keepAliveTimeout(5, SECONDS)`, `.keepAliveWithoutCalls(true)`
  - `@PreDestroy` method to gracefully shut down all gRPC channels
- Add integration tests for both paths:
  - Happy: reserve → pay → confirm → COMPLETED
  - Compensating: reserve → payment failure → release (stock returns)
  - Circuit-open: reserve → payment failure → circuit OPEN → Kafka fallback → eventual release

**Acceptance criteria:**
- `inventoryReservationPort.reserve()` succeeds and returns reservation ID
- Successful order reaches COMPLETED state E2E locally
- Simulated payment failure triggers compensation and releases stock within 30s
- When circuit breaker is OPEN, compensation publishes to `inventory.release-requested` topic (not throw)
- Reservations with expired Redis TTL trigger release via keyspace notification
- gRPC channels have keepalive configured and shut down on service stop

---

### 2.2 Payment Method Enablement

All 7 methods (COD, VietQR, PayPal, SePay, Stripe, VNPay, MoMo) use env-var pattern. **Note:** COD and VietQR default to `enabled: true`; the other 5 default to `false`. Do NOT change the defaults — supply sandbox credentials for the disabled methods instead.

**Work items:**
- Populate `secrets.env.local` with sandbox/test credentials for all 7 methods
- Document which require external sandbox accounts vs work without sign-up
- Commit `secrets.env.local.example` with placeholder values and per-method instructions
- Add startup validation: enabled=true + missing credential → fail fast with descriptive error
- Add `GET /api/v1/payments/methods` returning only enabled+configured methods

**Acceptance criteria:**
- All 7 methods appear in `/api/v1/payments/methods` when sandbox creds set
- COD completes full checkout flow locally
- Stripe sandbox completes authorize → capture locally
- `STRIPE_ENABLED=true` without `STRIPE_SECRET_KEY` → clear startup error

---

### 2.3 Tax Calculation

Vietnam VAT is not flat 10% — Decree 94/2023/NĐ-CP reduces certain categories to 8%.

**Work items:**
- Add configurable tax rate table: `tax_rates(category_code, rate, effective_from, effective_to)`
- VND rounding: round to nearest 1,000₫ per Vietnamese accounting convention
- Add `tax_amount` and `tax_rate` fields to order line items and total
- Tax calculated server-side only; frontend-submitted tax ignored
- Frontend displays tax as separate line item
- Cross-border: out of scope for MVP, documented in `docs/tax-future-work.md`

**Acceptance criteria:**
- Standard-rate item applies rate from config table, not hardcoded constant
- Changing rate in config takes effect without code redeploy
- Tax amount = `roundTo1000(subtotal × rate)`
- Integration test: `order.total = subtotal + tax_amount + shipping_amount`

---

### 2.4 Shipping Rate Integration

- Wire frontend checkout to call shipping-service (port 8093)
- Expose `POST /api/v1/shipping/rates` with weight-based + zone-based pricing
- Replace hardcoded 30,000₫ in `domain-constants.ts`
- Frontend shows shipping options with estimated delivery dates

**Acceptance criteria:**
- Checkout no longer uses hardcoded 30,000₫
- `/api/v1/shipping/rates` returns at least one rate for valid domestic destination
- Frontend renders options and updates total on user selection
- Free shipping threshold triggers correctly

---

### 2.5 Chargeback / Dispute Handling

Existing `Dispute.java` links to `returnId` (return-based). Payment chargebacks are different workflows.

**(a) New `Chargeback` entity — payment-provider disputes:**
- Fields: `{ id, orderId, externalChargebackId, provider, reason, status (OPEN|WON|LOST|ACCEPTED), evidenceJson, dueDate }`
- Add `DISPUTED` order status
- Stripe webhook: `charge.dispute.created` → Chargeback record → order DISPUTED
- PayPal webhook: `CUSTOMER.DISPUTE.CREATED` → same flow
- Admin API: accept or counter (with evidence upload)

**(b) Extend existing `Dispute` model — return-based disputes:**
- Keep existing `Dispute` → `returnId` linkage unchanged
- Extend with any missing status transitions
- No schema rename or entity merge

**Acceptance criteria:**
- Simulated Stripe webhook creates Chargeback record and sets order to DISPUTED
- Existing return-dispute flow unaffected (regression test passes)
- Chargeback and Dispute in separate DB tables, no FK coupling
- Admin can submit counter-evidence via API

---

## Track 3: Data & Reliability

**Issues:** #13 (cart Redis-only), #14 (Kafka replication=1), #15 (no read replicas), #16 (backup untested), #19 (trace sampling 0.1%)

### 3.1 Cart Persistence (Cache-Aside with MikroORM)

**Effort: 3–5 days.** Cart-service (NestJS) currently has NO database module, NO ORM, NO Postgres connection — only `ConfigModule` in `app.module.ts`. This is a full persistence layer addition.

**ORM choice: MikroORM** — matches `messaging-service/src/mikro-orm.config.ts`. Avoids introducing a 3rd ORM (messaging-service uses MikroORM, notification-service uses Mongoose).

**Pattern: Cache-aside (NOT write-through)**
- Write path: write to Postgres FIRST, then invalidate Redis key
- Read path: check Redis → on miss, read from Postgres → populate Redis with 30-day TTL
- This eliminates stale-read risk (if Redis write fails after Postgres succeeds, next read just cache-misses and hydrates fresh)

**Steps:**
1. Add MikroORM to cart-service with Postgres connection + connection pooling (pool size: 10)
2. Add `cart-db` Postgres container to docker-compose with health check
3. Set up MikroORM migrations directory
4. Schema: `carts(user_id PK, items JSONB, updated_at, version INTEGER)` — optimistic locking
5. Cache-aside: Postgres is source of truth, Redis is cache layer
6. Add Redis connection pooling via `ioredis` pool (pool size: 5) — single connection bottlenecks at 100+ concurrent users
7. Handle `volatile-lru` eviction: all cart keys MUST have explicit 30-day TTL (docker-compose Redis uses `volatile-lru` policy)

**Acceptance criteria:**
- `docker-compose up` starts `cart-db` and cart-service connects without error
- Cart written while Redis available is readable after `redis-cli FLUSHALL` (hydrated from Postgres)
- Concurrent writes with stale `version` return 409 Conflict
- Unit tests cover Redis-miss → Postgres-read → Redis-populate path
- Redis keys have explicit TTL set (verified via `redis-cli TTL cart:{userId}`)
- Service fails fast if `DATABASE_URL` is unset
- Load test: 100 concurrent cart mutations complete without Redis connection errors

---

### 3.2 Kafka Replication

- **Local docker-compose:** keep single broker, `replication.factor=1` (resource constraint)
- **K8s config:** 3 brokers, `default.replication.factor=3`, `min.insync.replicas=2`
- Add `infra/kafka/server.properties` with production settings
- Add liveness probe for Kafka in docker-compose health checks

**Acceptance criteria:**
- Docker-compose Kafka passes health check within 30 seconds
- `infra/kafka/server.properties` contains `min.insync.replicas=2`, `default.replication.factor=3`
- K8s StatefulSet spec reflects 3 replicas
- Documentation comment explains single-broker dev trade-off

---

### 3.3 Read Replicas

- **Local:** single Postgres per service group (no replica needed for dev)
- **K8s:** read-replica deployment per domain group (order+payment, product+inventory, user+notification)
- Spring: `AbstractRoutingDataSource` routes `@Transactional(readOnly=true)` to replica
- NestJS: TypeORM `replication` option
- Local-first: prepare config with replica support, use same host as primary

**Acceptance criteria:**
- Spring services with `readOnly=true` have `AbstractRoutingDataSource` wired
- TypeORM `replication.slaves` config exists in NestJS services
- K8s manifests include replica Deployment + Service per domain group
- Switching `REPLICA_HOST` requires no code change

---

### 3.4 Backup Verification

- Create `scripts/verify-backup.sh`: backup → restore to throwaway container → smoke queries → assert non-zero → teardown
- GitHub Actions workflow: weekly schedule
- Makefile target: `make verify-backup`

**Acceptance criteria:**
- `make verify-backup` exits 0 on healthy DB, non-zero on corrupted dump
- GitHub Actions workflow exists with `schedule` trigger
- Script cleans up throwaway container even on failure (trap EXIT)
- Smoke queries assert rows > 0 for: users, orders, products

---

### 3.5 Trace Sampling

- Dev: `OTEL_TRACES_SAMPLER_ARG=1.0` (100%)
- Staging: `0.1` (10%)
- Prod: `0.01` (1%)
- Add always-on sampler for error traces (100% of errored spans regardless of ratio)

**Acceptance criteria:**
- docker-compose sets `OTEL_TRACES_SAMPLER_ARG=1.0` for all services
- K8s ConfigMap sets `0.01` for prod namespace
- Error spans sampled at 100% regardless of base ratio
- No service hard-codes a sampling value

---

## Track 4: Frontend & UX

**Issues:** #6 (client-side search), #7 (Coming Soon stubs), #8 (hardcoded Vietnamese), #9 (guest cart ₫0)

### 4.1 Server-Side Search — Verify Frontend Wiring

Search-service API is fully built: `/search`, `/search/suggest`, `/search/facets` in `SearchController.java`. Gateway routes configured. This is a 1-2 hour verification/wiring task.

**Task:** Check if `SearchPage.tsx` and `search-autocomplete.tsx` already call these endpoints.
- If already wired: mark done, no code changes
- If not: connect to `GET /api/v1/search`, `GET /api/v1/search/suggest`, populate facet-list from API

**Acceptance criteria:**
- Network tab confirms search requests go to search-service, not local filter
- Autocomplete hits `/search/suggest`
- Facet counts from API response, not computed client-side

---

### 4.2 Remove / Triage Coming Soon Stubs

~10 files contain stubs. Buyer-facing and seller-facing require different handling.

**Disposition rules:**
- Buyer-facing, < 1 hour to implement: implement it
- Buyer-facing, > 1 hour: remove the button entirely
- Seller-facing, < 1 hour: implement it
- Seller-facing, > 1 hour: keep with tooltip showing timeline (e.g. "Available Q3 2026")

**Acceptance criteria:**
- All ~10 stub files audited with disposition documented
- Zero buyer-facing "Coming Soon" toasts remain in production paths
- Seller-facing stubs either implemented or showing timeline tooltip

---

### 4.3 i18n Fallback Strings

- Grep for hardcoded Vietnamese strings in `fe/src/`
- Move all to i18n translation files under `public/locales/{lang}/`
- Priority: `checkout/types.ts`, error messages, toast notifications
- Fix fallback chain: `en → vi`, not reverse

**Acceptance criteria:**
- Zero hardcoded Vietnamese string literals remain in `fe/src/` (grep clean)
- Both `en` and `vi` locale files contain all keys (no missing-key warnings)
- Switching language updates all previously hardcoded strings

---

### 4.4 Guest Cart Loading State

- Add `isHydrating` boolean in `use-cart.ts`
- Don't render price until product data resolves
- Show skeleton placeholders during hydration
- After hydration: if price still 0, show "Price unavailable" instead of ₫0

**Acceptance criteria:**
- ₫0 never appears for unresolved guest cart items
- Skeleton UI visible during hydration (verifiable via network throttling)
- "Price unavailable" renders when product price cannot be resolved
- No regression on authenticated user cart pricing

---

## Track 5: New Services & Standards

**Issues:** Admin dashboard, Email/SMS, GDPR, API versioning, Error standardization

### 5.1 Admin Dashboard Expansion

- Existing admin pages at `fe/src/app/pages/admin/` — expand with:
  - **User management:** lookup by email/phone, view order history, ban/unban
  - **Order management:** cancel, force-refund, change status, view saga timeline
  - **System health:** ping each service health endpoint, show green/red
- Backend: admin-scoped endpoints in user-service and order-service
- Authorization: Keycloak `realm-admin` or `vnshop-admin` role required
- No new service — just new endpoints + frontend pages

**Acceptance criteria:**
- Admin can search user by email and see full order history
- Admin can cancel an order, status change reflected in UI
- Health panel shows green/red per service based on `/actuator/health`

---

### 5.2 Email/SMS — Extend Existing Notification Infrastructure

**Already exists:** `SesEmailChannelAdapter` (AWS SES), 11 Kafka consumers, FCM push.

**Add only:**
- `TwilioSmsChannelAdapter` implementing existing channel adapter interface
- Handlebars template engine for HTML emails (`templates/*.hbs`)
- Minimum templates: `welcome.hbs`, `password-reset.hbs`, `order-confirmed.hbs`, `order-shipped.hbs`
- Audit existing 11 consumers — add only missing ones (`user.registered`, `user.password-reset` if absent)
- Do NOT add Nodemailer — SES already works

**Acceptance criteria:**
- Order confirmation delivers email visible in Mailhog (local SMTP catcher)
- SMS fires for `order.shipped` via Twilio sandbox without errors
- HTML email renders correctly (no broken template tags)
- No duplicate consumers: each Kafka event has exactly one handler

---

### 5.3 GDPR Compliance — Audit Existing Implementation

**The code already exists:** `GdprController.java` with `POST /export/{userId}`, `GET /export/{userId}/status/{requestId}`, `DELETE /delete/{userId}`. Plus `GdprEventListener` in shipping-service, payment-service, and order-service. `GdprExportUseCase` and `GdprDeleteUseCase` are implemented.

**Risk:** If existing endpoints are buggy (e.g., partial deletion across services), triggering them is WORSE than having no GDPR endpoint at all.

**Work items:**
- Audit `GdprController`, `GdprExportUseCase`, `GdprDeleteUseCase` — verify they work E2E
- Test `GdprEventListener` in each service — confirm all user data is actually purged
- Verify export includes data from ALL services (orders, cart, messages, reviews, payments)
- Test partial failure scenario: what happens if one service's listener fails mid-deletion?
- Add transaction/compensation logic if missing — deletion must be all-or-nothing
- Add integration test covering the full export and deletion flows

**Acceptance criteria:**
- `POST /export/{userId}` returns a ZIP containing user data from at least 5 services
- `DELETE /delete/{userId}` removes user data across all services (verified by querying each DB)
- Simulated failure in one service's event listener does NOT leave partial deletion state
- Existing endpoints return proper error responses (not 500 with stack trace)
- Test coverage exists for both happy path and failure scenarios

---

### ~~5.4 API Versioning~~ — DEPRIORITIZED

> Removed from this plan. No external API consumers yet. Can be added later without breaking anything. Gateway StripPrefix approach documented for future reference but not implemented now.

---

### 5.5 Error Format Standardization

Standard error DTO: `{ code, message, details[], timestamp, traceId }`

- Spring: shared `@ControllerAdvice` in common library, includes OTEL traceId
- NestJS: shared `AllExceptionsFilter`, same format
- Frontend: single error parser utility
- Document codes in `docs/api/error-codes.md`

**Acceptance criteria:**
- At least 3 services (1 Spring, 1 NestJS, gateway) return standard shape on 400/404/500
- `traceId` matches OTEL trace in Jaeger for same request
- Frontend doesn't show raw JSON stack traces

---

## Track 6: Payment Webhook Reliability

**Priority:** HIGH — directly affects revenue. A missed webhook = order stuck in "pending" forever.

### 6.1 Webhook Signature Verification

All payment providers sign webhook payloads. Verify signatures for every provider:
- **Stripe:** `Stripe-Signature` header with HMAC-SHA256 (use Stripe SDK `Webhook.constructEvent`)
- **PayPal:** Verify via PayPal `verify-webhook-signature` API
- **VNPay:** `vnp_SecureHash` field with SHA512 checksum
- **MoMo:** HMAC-SHA256 signature in request body
- **VietQR/SePay:** Provider-specific verification (document and implement)
- **COD:** No webhook (status updated manually or via delivery partner callback)

**Acceptance criteria:**
- Each provider's webhook endpoint rejects requests with invalid/missing signatures (returns 401)
- Integration test per provider simulates valid and tampered payloads
- Signature verification uses constant-time comparison (prevent timing attacks)

---

### 6.2 Webhook Retry & Idempotency

- Add `processed_webhooks` table: `{ webhook_id PK, provider, event_type, processed_at }`
- Before processing any webhook: check if `webhook_id` already exists → return 200 (skip)
- Providers retry failed webhooks (Stripe retries for 3 days). Must handle duplicates gracefully.
- Add retry logic for OUR processing: if internal handler fails, store in `pending_webhooks` for retry

**Acceptance criteria:**
- Same webhook delivered twice → processed only once (verified via DB record count)
- Internal processing failure → webhook stored for retry, not lost
- Provider receives 200 response within 5 seconds (avoid timeout-triggered retries)

---

### 6.3 Dead Letter Queue + Monitoring

- Add DLT (Dead Letter Topic) for failed webhook processing events
- Add alerting: any message in webhook DLT → immediate Slack/email notification
- Dashboard: count of pending/failed/processed webhooks per provider per hour
- Auto-reconciliation job: compare provider settlement reports with internal records daily

**Acceptance criteria:**
- A webhook that fails processing 3 times lands in DLT and triggers an alert
- DLT messages are visible in admin dashboard with retry capability
- No webhook silently disappears — either processed, in retry queue, or in DLT with alert

---

### 6.4 VietQR Webhook Validation (Critical)

VietQR is enabled by default but its callback flow has never been validated E2E. This is the highest-risk single item.

- Set up VietQR sandbox environment
- Trace the full flow: buyer scans QR → bank confirms → callback to VNShop → order confirmed
- Identify: what URL does VietQR call back to? Is it configured? Does signature verification work?
- If callback URL is misconfigured or unreachable: buyers pay but orders stay "pending" indefinitely

**Acceptance criteria:**
- VietQR sandbox callback received and processed locally (order transitions to CONFIRMED)
- Callback URL is correctly configured in VietQR provider dashboard
- Invalid callback signature → rejected with 401, order stays PENDING (not corrupted)
- Timeout scenario: if callback never arrives within 10 min, order gets `PAYMENT_TIMEOUT` status

---

## Track 7: E-Invoice Service (Hoa Don Dien Tu)

**Priority:** LEGAL REQUIREMENT. Since January 2025, VNShop is legally responsible for issuing e-invoices on behalf of authorized sellers (Decree 123/2020, Circular 78/2021). Fines up to 200M VND.

**Architecture:** New `invoice-service` microservice with direct GDT (General Department of Taxation) integration.

### 7.1 Invoice Service Core

```
order-service → (Kafka: order.confirmed) → invoice-service → GDT API
                                                    ↓
                                          seller-authorization DB
```

**New microservice: `invoice-service`**
- Tech stack: Spring Boot (matches majority of backend services)
- Port: 8094
- Database: Postgres (invoice records, seller authorizations)
- Kafka consumer: `order.confirmed` → generate and submit invoice

**Core entities:**
- `Invoice`: id, orderId, sellerId, buyerTaxCode, items, vatBreakdown, status (DRAFT|SUBMITTED|ACCEPTED|REJECTED), gdtInvoiceNumber, xmlPayload, createdAt
- `SellerAuthorization`: sellerId, authorizedAt, taxCode, digitalCertId, status (ACTIVE|REVOKED)

**Acceptance criteria:**
- `invoice-service` starts and connects to its database
- Kafka consumer receives `order.confirmed` and creates an Invoice record in DRAFT status
- Service exposes `GET /api/v1/invoices/{orderId}` returning invoice details

---

### 7.2 GDT XML Format (TKHDon)

- Implement XML payload generation per GDT specification (TKHDon format)
- Required fields: seller name/address/tax code, buyer info, item descriptions, unit prices, quantities, VAT breakdown by rate (0%, 5%, 8%, 10%), payment amount, invoice number (sequential per GDT format)
- Digital signature integration (seller's digital certificate or platform certificate for authorized sellers)
- Validate XML against GDT schema before submission

**Acceptance criteria:**
- Generated XML validates against GDT TKHDon XSD schema
- Invoice number follows GDT sequential format: template code + symbol + year + number
- VAT breakdown correctly separates items by applicable rate
- XML includes digital signature placeholder (actual signing in 7.3)

---

### 7.3 GDT API Integration

- Direct integration with General Department of Taxation transmission API
- Submit authenticated e-invoices (with tax authority verification code)
- Handle responses: ACCEPTED (store GDT invoice number), REJECTED (store reason, flag for retry)
- Retry policy: rejected invoices queued for correction and resubmission
- 10-year storage obligation: store all submitted XMLs and GDT responses

**Acceptance criteria:**
- Invoice submission to GDT sandbox API returns acceptance/rejection response
- Accepted invoices receive and store GDT verification code
- Rejected invoices are flagged with reason and available for admin correction
- All XML payloads stored permanently (not deleted by any cleanup job)

---

### 7.4 Seller Authorization Management

Since Jan 2025: sellers can authorize VNShop to issue invoices on their behalf. This requires:
- Seller signs authorization agreement (stored in DB)
- Both parties register arrangement with tax department
- VNShop deducts tax at source from seller payments
- Sellers without authorization must handle invoicing independently

**Work items:**
- Admin UI: manage seller authorization status
- API: `POST /api/v1/sellers/{id}/authorize-invoicing`, `DELETE /api/v1/sellers/{id}/revoke-invoicing`
- On order confirmation: check if seller is authorized → if yes, invoice-service handles; if no, notify seller to issue their own

**Acceptance criteria:**
- Authorized seller's order → invoice auto-generated by invoice-service
- Non-authorized seller's order → notification sent to seller (no invoice generated by platform)
- Admin can view/manage authorization status per seller
- Tax deduction calculated correctly on seller payouts for authorized sellers

---

## Execution Order & Dependencies

```
Phase 1: Track 1 + Track 2 + Track 3 + Track 4 + Track 6 — all parallel
  Track 1: infra/secrets, api-gateway config, payment-service middleware
  Track 2: inventory-service, order-service saga, payment-service, shipping-service
  Track 3: cart-service, docker-compose, infra/kafka, scripts/
  Track 4: fe/src/ only (no backend conflicts)
  Track 6: payment-service webhooks (overlaps with Track 2 on payment-service — same agent)
  
  Track 2 + Track 6 assigned to SAME agent (both touch payment-service).
  All other tracks touch different codepaths with only 3 hotspot files.

Phase 2: Track 5 (New Services & Standards)
  Depends on: stable APIs from Tracks 1-4 (credentials, routes, error formats)
  Touches: notification-service, user-service, fe/admin, api-gateway routes

Phase 3: Track 7 (E-Invoice Service)
  Depends on: Track 2.3 (tax calculation) complete, seller authorization data model
  New microservice — minimal conflicts with other tracks
  Longest lead time: GDT sandbox registration, digital certificate procurement
```

**Timeline:** Open-ended, track by track. No hard deadline. Ship tracks as they complete.

---

## Merge Conflict Hotspots

| File | Touched by | Mitigation |
|------|-----------|------------|
| `RouteConfig.java` | Track 1.2 (rate limiting) | Only one track now (5.4 removed). No conflict. |
| `docker-compose.yml` | Track 1.1, Track 3.1, Track 3.2 | One agent owns docker-compose, or merge all in single commit |
| `order-service` domain/saga | Track 2.1, 2.3, 2.5 | Single agent handles all Track 2 — saga never half-edited |
| `payment-service` | Track 2.2, 2.5, Track 6.1-6.4 | Same agent handles Track 2 + Track 6 |
| `fe/src/app/pages/CartPage.tsx` | Track 4.4 (hydration), Track 2.4 (shipping) | Track 4 agent finishes cart changes before Track 2 touches checkout |
| `proto/` definitions | Track 2.1 (extend-reservation TTL field) | Regenerate stubs in ALL consuming services after proto change |
| Gateway `application.yml` | Track 1.2 (rate limits), Track 1.4 (CORS verify) | Same agent (Track 1) handles both |

---

## Sub-Agent Execution Strategy

- **Track 1:** Security agent — owns api-gateway config, secrets, fraud middleware
- **Track 2 + Track 6:** Payment agent — owns order-service, payment-service, inventory proto, webhooks (sequenced internally: 2.1 → 2.2 → 6.1-6.4 → 2.3 → 2.4 → 2.5)
- **Track 3:** Reliability agent — owns cart-service, docker-compose, infra/kafka, scripts
- **Track 4:** Frontend agent — owns fe/src/ exclusively
- **Track 5:** Services agent — launches after Phase 1 merged, owns notification-service, user-service, fe/admin
- **Track 7:** Invoice agent — launches after Track 2.3 (tax) complete, new microservice with no conflicts

Worktree base ref: branch from latest merged main (not stale origin/main).

---

## Success Criteria

| Check | Verification |
|-------|-------------|
| Inventory reservation | E2E: reserve→pay→confirm AND reserve→fail→release AND circuit-open→Kafka fallback |
| Payment methods | All 7 have sandbox creds, startup health check passes |
| Webhook reliability | VietQR callback E2E validated; all providers reject invalid signatures |
| Search | Network tab shows XHR to `/api/v1/search`, not client-side filter |
| Coming Soon | Zero buyer-facing "Coming Soon" toasts reachable |
| Cart durability | Stop Redis → cart still loads from Postgres (cache-aside) |
| Cart concurrency | 100 concurrent mutations complete without connection errors |
| Rate limiting | Authenticated users get higher limits than anonymous; CGNAT scenario passes |
| Error format | 400 from Spring + NestJS + gateway all return standard shape |
| Admin | Admin can search user by email, view order history |
| Email | Order placement triggers email in Mailhog within 30s |
| GDPR | Existing export/delete endpoints work E2E; partial failure doesn't corrupt data |
| E-Invoice | invoice-service generates valid TKHDon XML; GDT sandbox accepts submission |
| Fraud | 4th order in 60 min flagged; reservation TTL extended; Kafka fallback on circuit-open |
