# VNShop — Frontend Plan

> Backend audit + complete endpoint inventory + E2E journeys + FE build checklist.
> Source: audit of `.sisyphus/` docs (ARCHITECTURE.md, STATUS.md, microservice-maturity.md, architecture-solid-audit, gap-remediation plan) + direct controller grep across 13 services on 2026-05-14.
> Gateway base URL: `http://localhost:8080`. Auth: Keycloak JWT via `/auth/**`.

---

## 1. Microservice Scorecard

### What's RIGHT ✅

| Area | Evidence |
|---|---|
| No sync inter-service calls | Zero `@FeignClient` / `RestTemplate` between services. Pure Kafka. |
| No shared domain libs | No `common/` / `libs/`. Each service owns its DTOs & entities. |
| Schema-per-service | 7 isolated Postgres schemas (`user_svc`, `product_svc`, `order_svc`, `payment_svc`, `inventory_svc`, `search_svc`, `shipping_svc`). |
| Outbox pattern | `order-service/infrastructure/outbox/OutboxEvent` + `payment-service` callback outbox. |
| Hexagonal layering | 0 domain→framework import violations across 13 services. |
| Polyglot with reason | Java/Spring Boot for transactional (order, payment, inventory, product, user). NestJS for I/O-bound (cart, notification). |
| Gateway pattern | Spring Cloud Gateway with TokenRelay, CircuitBreaker, Redis rate-limit. |
| Keycloak for auth | No homegrown JWT. OIDC/JWKS. |
| Idempotency | `Idempotency-Key` header enforced on `POST /orders`. |
| Standard envelope | `ApiResponse<T>` everywhere except `notification-service /health` (known violation). |
| Per-service Dockerfiles | 13/13 complete. |

### What's WRONG ❌

| # | Issue | Severity | Location |
|---|---|---|---|
| 1 | Single Postgres container serving all schemas — SPOF + resource contention | CRITICAL | `docker-compose.yml` |
| 2 | CQRS is claimed but not implemented — 0 `CommandHandler` / `QueryHandler` found | HIGH | order-service, product-service |
| 3 | Saga orchestration missing — outbox exists, no state machine or compensation coordinator | CRITICAL | order-service |
| 4 | Zero distributed tracing — no OTel, correlation ID not propagated across hops | HIGH | all services |
| 5 | Mid-migration chaos — coupon/review/finance logic exists in BOTH old standalone services AND new embedded locations | HIGH | product-service vs review-service, order-service vs coupon-service |
| 6 | Stub adapters still in prod path — `StubCartRepositoryAdapter`, stub inventory/payment/shipping clients | CRITICAL | order-service (`.sisyphus/boulder.json` active plan) |
| 7 | Gateway uses hardcoded `http://localhost:*` instead of `lb://service-name` — breaks container networking | HIGH | `RouteConfig.java:15-17` |
| 8 | Domain models leak to HTTP — `ProductController`, `FlashSaleController`, `AdminDashboardController` return domain entities | MEDIUM | 5 services (SOLID audit 2026-05-11) |
| 9 | No gRPC mesh yet — ARCHITECTURE claims it, gap-remediation plan confirms "zero gRPC exists today" | HIGH | inter-service |
| 10 | NestJS notification controller injects repository directly — bypasses application layer | MEDIUM | `notification.controller.ts` |
| 11 | Missing critical product features — variants, guest cart, return/refund, real tracking | HIGH | see §5 |
| 12 | Coverage gates only on 4 of 10 active services | MEDIUM | |
| 13 | No rolling update / canary / blue-green defined | MEDIUM | |

**Maturity score (from `.sisyphus/analysis/microservice-maturity.md`): 5.1/10 — "event-driven modular," not "true microservices."**
To reach 7+: fix #1 (db-per-service), #3 (saga), #4 (tracing), #6 (stubs).

---

## 2. Complete Endpoint Inventory

Base URL: `http://localhost:8080` (gateway). All protected routes require `Authorization: Bearer <jwt>`.
Response envelope: `{ success, message, data, errorCode, timestamp }`.

### 🔐 Auth — Keycloak passthrough `/auth/**`
- `POST /auth/realms/vnshop/protocol/openid-connect/token` — login, refresh
- `GET /auth/realms/vnshop/account` — Keycloak profile UI
- Registration, password reset, email verify — Keycloak built-in pages

### 👤 user-service (8081)

| Method | Path | Purpose | Actor |
|---|---|---|---|
| GET | `/users/me` | View buyer profile | BUYER |
| PUT | `/users/me` | Update profile | BUYER |
| POST | `/users/me/addresses` | Add address | BUYER |
| PUT | `/users/me/addresses/{index}/default` | Set default | BUYER |
| DELETE | `/users/me/addresses/{index}` | Remove address | BUYER |
| POST | `/sellers/register` | Upgrade to seller | BUYER |
| GET | `/sellers/me` | View shop profile | SELLER |
| GET | `/admin/sellers` | List sellers | ADMIN |
| POST | `/admin/sellers/{id}/approve` | Approve seller | ADMIN |

### 📦 product-service (8082)

| Method | Path | Purpose | Actor |
|---|---|---|---|
| GET | `/products` | List / paginate | public |
| GET | `/products/{id}` | Product detail | public |
| GET | `/categories` | Category tree | public |
| POST | `/sellers/me/products` | Create listing | SELLER |
| PUT | `/sellers/me/products/{id}` | Update listing | SELLER |
| POST | `/sellers/me/products/{productId}/images/upload-url` | Pre-signed upload URL | SELLER |
| POST | `/sellers/me/products/{productId}/images/activate` | Confirm upload complete | SELLER |
| GET | `/reviews/product/{productId}` | Reviews for product | public |
| POST | `/reviews` | Write review | BUYER |
| PUT | `/reviews/{id}/helpful` | Vote helpful | BUYER |
| POST | `/reviews/{reviewId}/images/upload-url` | Review image upload URL | BUYER |
| POST | `/reviews/{reviewId}/images/activate` | Activate image | BUYER |
| GET | `/questions/product/{productId}` | Product Q&A | public |
| POST | `/questions` | Ask question | BUYER |
| PUT | `/questions/{id}/answer` | Seller answer | SELLER |
| GET | `/admin/reviews/pending` | Moderation queue | ADMIN |
| PUT | `/admin/reviews/{id}/approve` | Approve | ADMIN |
| PUT | `/admin/reviews/{id}/reject` | Reject | ADMIN |

### 🛒 cart-service (8084, NestJS)
Requires `x-user-id` header (set by gateway from JWT).

| Method | Path | Purpose |
|---|---|---|
| GET | `/cart` | View cart |
| POST | `/cart/items` | Add item |
| PUT | `/cart/items/{productId}` | Update quantity |
| DELETE | `/cart/items/{productId}` | Remove item |
| DELETE | `/cart` | Clear cart |

### 🔍 search-service (8086)

| Method | Path | Purpose |
|---|---|---|
| GET | `/search?q=&facets=&sort=&page=` | Full-text search + facets |
| GET | `/search/categories` | Category facets |

### 📬 notification-service (8087, NestJS)

| Method | Path | Purpose | Actor |
|---|---|---|---|
| GET | `/notifications` | Inbox list | USER |
| GET | `/notifications/{id}` | Detail (marks read) | USER |
| POST | `/notifications/test` | Send test (dev only) | ADMIN |

### 📋 order-service (8091)

**Buyer orders**

| Method | Path | Purpose |
|---|---|---|
| POST | `/orders` (requires `Idempotency-Key`) | Place order |
| GET | `/orders` | Order history |
| GET | `/orders/{id}` | Order detail |
| DELETE | `/orders/{id}/cancel` | Cancel order |

**Checkout**

| Method | Path | Purpose |
|---|---|---|
| POST | `/checkout/calculate` | Totals + shipping + discount preview |
| GET | `/checkout/payment-methods` | List methods (currently COD) |
| POST | `/checkout/shipping-options` | Shipping quotes |

**Seller fulfillment**

| Method | Path | Purpose |
|---|---|---|
| GET | `/seller/orders/pending` | Pending sub-orders |
| PUT | `/seller/orders/{subOrderId}/accept` | Accept |
| PUT | `/seller/orders/{subOrderId}/reject` | Reject |
| PUT | `/seller/orders/{subOrderId}/ship` | Mark shipped |

**Returns & disputes**

| Method | Path | Purpose |
|---|---|---|
| POST | `/returns` | Request return |
| GET | `/returns` | List my returns |
| POST | `/returns/{returnId}/approve` | Seller approves |
| POST | `/returns/{returnId}/reject` | Seller rejects |
| POST | `/returns/{returnId}/complete` | Complete refund |
| POST | `/returns/{returnId}/disputes` | Escalate to dispute |
| GET | `/admin/disputes/open` | Admin queue |
| POST | `/admin/disputes/{disputeId}/resolve` | Resolve dispute |

**Invoices**

| Method | Path | Purpose |
|---|---|---|
| POST | `/invoices/orders/{orderId}/sub-orders/{subOrderId}` | Generate invoice |
| GET | `/invoices/{invoiceId}/download-url` | Signed download URL |

**Admin dashboard**

| Method | Path | Purpose |
|---|---|---|
| GET | `/admin/dashboard/summary` | KPI cards |
| GET | `/admin/dashboard/revenue` | Time-series |
| GET | `/admin/dashboard/top-products` | Ranked products |
| GET | `/admin/dashboard/top-sellers` | Ranked sellers |

### 🎟️ coupon-service (8088) — mid-migration, reachable via `/coupons/**`

| Method | Path | Purpose |
|---|---|---|
| POST | `/coupons` or `/admin/coupons` | Create coupon |
| GET | `/coupons` | Public coupon list |
| GET | `/admin/coupons` | Admin list |
| PUT | `/admin/coupons/{id}` | Update |
| POST | `/admin/coupons/{id}/deactivate` | Deactivate |
| POST | `/coupons/validate` or `/checkout/validate-coupon` | Pre-apply validation |
| POST | `/checkout/apply-coupon` | Apply to checkout |

### 💳 payment-service (8092)

| Method | Path | Purpose |
|---|---|---|
| POST | `/payment/cod/confirm` | Mark COD paid |
| POST | `/payment/vnpay/create` | Init VNPay |
| GET | `/payment/vnpay/return` | Browser return |
| GET | `/payment/vnpay/ipn` | Server webhook |
| POST | `/payment/momo/create` | Init MoMo |
| POST | `/payment/momo/ipn` | Server webhook |
| GET | `/payment/status/{orderId}` | Poll payment status |

### 🚚 shipping-service (8093)
No HTTP controller. Kafka-only. Tracking visible via `GET /orders/{id}` → `subOrders[*].trackingCode`.
Gap: no `GET /shipping/track/{code}` endpoint exposed yet.

### 💰 seller-finance-service (8090)

| Method | Path | Purpose |
|---|---|---|
| GET | `/sellers/me/finance/wallet` | Wallet balance |
| GET | `/sellers/me/finance/payouts` | Payout history |
| POST | `/sellers/me/finance/payouts` | Request payout |
| POST | `/sellers/me/finance/credits` | Internal credit (system) |
| GET | `/admin/finance/payouts/pending` | Admin queue |
| POST | `/admin/finance/payouts/{id}/complete` | Mark paid |
| POST | `/admin/finance/payouts/{id}/fail` | Mark failed |

### ⚡ inventory-service (8083)

| Method | Path | Purpose |
|---|---|---|
| POST | `/flash-sale/reserve` | Redis atomic DECR |
| GET | `/flash-sale/stock/{productId}` | Current stock |
| POST | `/flash-sale/release/{reservationId}` | Release hold |

---

## 3. E2E User Journeys

### Buyer — happy path
1. **Sign up / sign in** → Keycloak `/auth/**` → JWT
2. **Browse** → `GET /products` + `GET /search` + `GET /categories`
3. **Product detail** → `GET /products/{id}` + `GET /reviews/product/{id}` + `GET /questions/product/{id}`
4. **Add to cart** → `POST /cart/items`
5. **View cart** → `GET /cart`
6. **Checkout preview** → `POST /checkout/calculate` + `POST /checkout/shipping-options` + `POST /checkout/validate-coupon`
7. **Place order** → `POST /orders` with `Idempotency-Key`
8. **Pay** → `POST /payment/vnpay/create` → redirect → `/payment/vnpay/return`
9. **Track** → `GET /orders/{id}` (poll status)
10. **Post-delivery** → `POST /reviews`, optionally `POST /returns`
11. **Inbox** → `GET /notifications`

### Seller — happy path
1. **Onboard** → `POST /sellers/register` → wait for ADMIN approval
2. **List product** → `POST /sellers/me/products/{id}/images/upload-url` → S3 → `POST /.../activate` → `POST /sellers/me/products`
3. **Poll orders** → `GET /seller/orders/pending`
4. **Accept + ship** → `PUT /seller/orders/{id}/accept` → `PUT /seller/orders/{id}/ship`
5. **Q&A / reviews** → `PUT /questions/{id}/answer`
6. **Money** → `GET /sellers/me/finance/wallet` → `POST /sellers/me/finance/payouts`
7. **Returns** → `POST /returns/{id}/approve` or `/reject`

### Admin — happy path
1. **Approve sellers** → `GET /admin/sellers` → `POST /admin/sellers/{id}/approve`
2. **Moderate** → `GET /admin/reviews/pending` → approve/reject
3. **Coupons** → `POST /admin/coupons`
4. **Dashboard** → `GET /admin/dashboard/{summary|revenue|top-products|top-sellers}`
5. **Disputes** → `GET /admin/disputes/open` → resolve
6. **Payouts** → `GET /admin/finance/payouts/pending` → complete/fail

---

## 4. Frontend Build Checklist

### Public / buyer app (MVP)
- [ ] Auth (Keycloak redirect + silent refresh + logout)
- [ ] Home (featured products, categories, flash sale carousel)
- [ ] Category / search results (filters, sort, pagination)
- [ ] Product detail (gallery, variants, reviews, Q&A, add-to-cart)
- [ ] Cart drawer + cart page
- [ ] Checkout flow (address → shipping → payment → review) with `Idempotency-Key`
- [ ] Payment redirect handlers (VNPay return, MoMo return)
- [ ] Order history + order detail + tracking
- [ ] Profile + address book
- [ ] Notification bell + inbox
- [ ] Review write + return request modals

### Seller portal
- [ ] Seller onboarding wizard (becomes seller → pending approval state)
- [ ] Dashboard (orders pending, recent sales, wallet)
- [ ] Product list + create/edit with pre-signed image upload
- [ ] Order fulfillment queue (accept / reject / ship)
- [ ] Returns queue
- [ ] Q&A inbox
- [ ] Wallet + payout history + request payout
- [ ] Shop-level coupons

### Admin portal
- [ ] Dashboard KPIs + charts (revenue, top products, top sellers)
- [ ] Seller approval queue
- [ ] Review moderation queue
- [ ] Coupon CRUD
- [ ] Disputes resolution
- [ ] Payout approval queue

---

## 5. Backend Gaps Blocking FE Work

| Gap | Impact on FE |
|---|---|
| ❌ Product variants (size/color) | Cannot build variant selector on product detail |
| ❌ Guest cart (cart requires `x-user-id`) | No "add to cart before login" UX |
| ❌ Wishlist | No save-for-later feature |
| ❌ Real carrier tracking (`/shipping/track/*`) | Tracking UI limited to status polling |
| ⚠️ Coupon / finance / review mid-migration | Endpoints may move between services — expect path churn |
| ❌ Saved payment methods, installments | Can't offer these at checkout |
| ❌ Re-order, digital invoice UI | Missing common post-purchase flows |
| ❌ i18n (vi/en) | Hardcode Vietnamese for now, plan for later |
| ⚠️ Gateway uses hardcoded `localhost:*` | FE must use `NEXT_PUBLIC_API_URL=http://localhost:8080` locally; need config for prod |
| ❌ Push / SSE / WebSocket | Notifications must use polling for now |

---

## 6. Suggested FE Stack

> **Implementation note (2026-05-16):** the buyer/seller/admin app under `fe/` ships with **Vite 6 + react-router 7 (SPA)** rather than Next.js. The framework recommendation in this section was a starting point; the deviation is intentional and tracked here so future contributors don't churn on it.
>
> | Concern | Plan recommendation | Implemented | Trade-off |
> |---|---|---|---|
> | Framework | Next.js 15 (app router) | Vite 6 SPA + react-router 7 | No SSR on PDPs / category pages — meta tags + sitemap need to handle SEO. Revisit if marketing-led SEO becomes a hard requirement. |
> | UI kit | shadcn/ui | `@figma/astraui` | Locked to a Figma-aligned kit; trade-off is reduced shadcn ecosystem leverage in exchange for design parity. |
>
> Everything else (TanStack Query, Zod, keycloak-js, react-hook-form, Zustand, Recharts, Tailwind v4) matches the plan.

| Concern | Choice | Why |
|---|---|---|
| Framework | Next.js 15 (app router) | SSR for SEO on product pages, RSC for data fetching |
| Styling | Tailwind + shadcn/ui | Fast iteration, accessible components |
| Data fetching | TanStack Query | Cache management, background refetch, request dedup |
| Validation | Zod | Validate `ApiResponse<T>` envelope + form schemas |
| Auth | `keycloak-js` or `next-auth` w/ Keycloak provider | Matches backend OIDC |
| Forms | react-hook-form + Zod resolver | Standard pairing |
| State | Zustand (minimal) | For cart mirror, UI state |
| Charts (admin) | Recharts | Simple, composable |
| i18n (future) | next-intl | Works with app router |

---

## 7. References

- `.sisyphus/ARCHITECTURE.md` — design decisions
- `.sisyphus/STATUS.md` — feature completion matrix (85/119 FRs, 23/40 NFRs)
- `.sisyphus/analysis/microservice-maturity.md` — 5.1/10 maturity audit
- `.sisyphus/analysis/architecture-solid-audit-2026-05-11.md` — SOLID findings
- `.sisyphus/plans/gap-remediation.md` — active remediation plan (gRPC mesh, saga, service revival)
- `.sisyphus/analysis/spring-cloud-gateway.md` — full gateway route spec
- `docs/GAP-ANALYSIS.md` — gap inventory
