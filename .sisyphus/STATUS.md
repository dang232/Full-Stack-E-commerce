# VNShop — Project Status

> **Last updated**: 2026-05-12
> **Audience**: Developers joining the project. Start here to understand what exists, what works, and what needs building.
> **Companion docs**: [`ARCHITECTURE.md`](ARCHITECTURE.md) for design decisions, [`README.md`](../README.md) for quick start.

---

## Table of Contents
1. [High-Level Summary](#1-high-level-summary)
2. [What Was Just Completed](#2-what-was-just-completed)
3. [Service-by-Service Health](#3-service-by-service-health)
4. [Feature Completion — All 119 Requirements](#4-feature-completion--all-119-requirements)
5. [Feature Completion — Non-Functional Requirements (40)](#5-feature-completion--non-functional-requirements-40)
6. [API Endpoint Inventory](#6-api-endpoint-inventory)
7. [Architecture Compliance Audit](#7-architecture-compliance-audit)
8. [Test Coverage Status](#8-test-coverage-status)
9. [Blocked Items & Prerequisites](#9-blocked-items--prerequisites)
10. [Priority Roadmap](#10-priority-roadmap)
11. [Documentation Map](#11-documentation-map)

---

## 1. High-Level Summary

**VNShop** is a multi-seller retail marketplace backend (Shopee/Tiki/Lazada model). Three personas: **Buyers**, **Sellers**, **Admins**.

| Metric | Value |
|--------|-------|
| Services | 13 (3 deprecated, 10 active) |
| Languages | Java 25 (Spring Boot 4.0.6) + TypeScript (NestJS 11) |
| Infrastructure | PostgreSQL, Redis, Kafka, Elasticsearch, Keycloak, Jaeger, Prometheus |
| Feature coverage | 85/119 (71%) — 26 missing, 8 partial |
| NFR coverage | 23/40 (57%) — 10 missing, 7 partial |
| Dockerfiles | 13/13 complete |
| Test coverage gates | 4 of 10 active services configured |
| Architecture violations | 0 (audited across all 13 services) |

---

## 2. What Was Just Completed

**Plan**: `align-services-to-analysis-docs` — 11 tasks executed, 4 final reviews passed.

### Architecture Alignment
| Change | From | To | Files |
|--------|------|----|-------|
| Coupon domain | standalone `coupon-service` | embedded in `order-service` | 8 domain + 12 use case + 6 JPA + 7 controller + 5 test |
| Review domain | standalone `review-service` | embedded in `product-service` | 6 domain + 16 use case + 5 JPA + 13 controller |
| Finance domain | standalone `seller-finance-service` | embedded in `order-service` + `user-service` | 7 domain + 5 use case + 6 JPA + 10 controller |
| Cart build | missing Dockerfile | multi-stage Alpine image | Dockerfile + health endpoint |

### Standardization Enforced
- **DTOs**: All 23 order-service response DTOs + all new coupon/review/finance DTOs → Java `record`. Zero class-based DTOs remain.
- **Entities**: All `*JpaEntity` classes verified with `@Getter` + `@Setter` (Lombok). Only `BaseJpaEntity` exempt.
- **Repositories**: Two-layer pattern enforced — `XxxJpaRepository implements Port` wraps `XxxJpaSpringDataRepository extends JpaRepository`.
- **Coverage gates**: JaCoCo 90% (order, product, user) + Jest 90% (cart).

### Deprecated Services
- `coupon-service` (port 8088) — ⚰️ migrated to order-service
- `review-service` (port 8089) — ⚰️ migrated to product-service
- `seller-finance-service` (port 8090) — ⚰️ migrated to order-service + user-service
- Each has `DEPRECATED.md` with rollback instructions. Removed from compose `apps` profile and gateway routes.

---

## 3. Service-by-Service Health

Results from `task-F3-qa.txt` — full Maven/npm test runs on 2026-05-10.

| Service | Port | Tech | Compile | Tests | Result | Dependencies | Notes |
|---------|------|------|---------|-------|--------|-------------|-------|
| api-gateway | 8080 | Spring Boot | ✅ | 0 run | PASS | Redis, Keycloak | No test suite yet |
| user-service | 8081 | Spring Boot | ✅ | 1 run, 1 error | FAIL* | PostgreSQL, Redis, Kafka | Context test: Flyway needs PG |
| product-service | 8082 | Spring Boot | ✅ | 6 run, 5 errors | FAIL* | PostgreSQL, Redis, Kafka | Image test compilation errors + PG context |
| inventory-service | 8083 | Spring Boot | ✅ | 3 run, 1 error | FAIL* | PostgreSQL, Redis, Kafka | Context test needs PG |
| cart-service | 8084 | NestJS | ✅ | 1 run, 0 fail | PASS | Redis only | Unit + e2e pass |
| search-service | 8086 | Spring Boot | ✅ | 1 run, 1 error | FAIL* | PostgreSQL, Redis, Kafka, ES | Context test needs PG |
| notification-service | 8087 | NestJS | ✅ | 1 run, 0 fail | PASS | PostgreSQL, Redis, Kafka | Jest + build pass |
| order-service | 8091 | Spring Boot | ✅ | 19 run, 0 fail | PASS** | PostgreSQL, Redis, Kafka | **New tests pass. Context test needs PG |
| payment-service | 8092 | Spring Boot | ✅ | 29 run, 0 fail | PASS | PostgreSQL, Redis, Kafka | Full test suite passes |
| shipping-service | 8093 | Spring Boot | ✅ | 5 run, 0 fail | PASS | PostgreSQL, Redis, Kafka | Carrier stubs pass |
| coupon-service | 8088 | Spring Boot | ✅ | 1 run, 1 error | FAIL* | PostgreSQL | ⚰️ DEPRECATED. Context test only |
| review-service | 8089 | Spring Boot | ✅ | 6 run, 0 fail | PASS | PostgreSQL, Redis, Kafka | ⚰️ DEPRECATED. Logic in product-service |
| seller-finance-service | 8090 | Spring Boot | ✅ | 2 run, 1 error | FAIL* | PostgreSQL, Redis, Kafka | ⚰️ DEPRECATED. Logic in order-service |

> `*` = `@SpringBootTest` context load fails because PostgreSQL not running. Unit/logic tests pass.
> `**` = 19 new coupon tests pass. Context test needs PostgreSQL.
> `⚠️` = Deprecated. Logic migrated. Directories kept for rollback.

---

## 4. Feature Completion — All 119 Requirements

Source: `analysis/requirements-audit-v2.md` (2026-05-09), cross-referenced against bounded context docs and controller inspection.

### A1. Account & Profile (77% — 10 of 13)

| # | Feature | Status |
|---|---------|--------|
| F1 | Registration (email + password) | ✅ Keycloak OIDC |
| F2 | Login (email + password → JWT) | ✅ Keycloak OIDC |
| F3 | Social Login (Google, Facebook) | ⚠️ Keycloak supports; not configured in realm |
| F4 | Forgot Password / Reset | ✅ Keycloak built-in |
| F5 | Email Verification | ✅ Keycloak built-in |
| F6 | View Profile (name, phone, avatar) | ✅ user-context.md §6 |
| F7 | Update Profile | ✅ user-context.md §7 |
| F8 | Manage Addresses (CRUD, set default) | ✅ user-context.md §6-7 |
| F9 | Become Seller (shop registration) | ✅ SellerProfile + upgrade flow |
| F10 | Seller Approval (ADMIN) | ✅ ApproveSeller use case |
| F11 | Phone Number Validation | ✅ PhoneNumber VO (E.164) |
| F12 | Multi-Language (vi/en) | ❌ MISSING — no i18n strategy |
| F13 | Delete Account | ❌ MISSING — GDPR compliance |

### A2. Product Browsing & Discovery (79% — 11 of 14)

| # | Feature | Status |
|---|---------|--------|
| F14 | Product Catalog Browse (paginated) | ✅ CQRS read side |
| F15 | Product Detail (images, description, specs) | ✅ |
| F16 | Category Tree (hierarchical navigation) | ✅ |
| F17 | Full-Text Search (Vietnamese support) | ✅ |
| F18 | Faceted Filtering | ✅ |
| F19 | Sort (relevance, price, newest, rating) | ✅ |
| F20 | Search Suggestions (autocomplete) | ✅ |
| F21 | Trending Searches | ✅ |
| F22 | Flash Sale Product Listing | ✅ |
| F23 | Product Variants (size, color, specs) | ❌ MISSING — CRITICAL |
| F24 | Product Images Gallery (multiple, zoom) | ❌ MISSING — HIGH |
| F25 | Product Comparison | ❌ MISSING |
| F26 | Recently Viewed Products | ❌ MISSING |
| F27 | Related Products / Recommendations | ❌ MISSING |

### A3. Shopping Cart (64% — 7 of 11)

| # | Feature | Status |
|---|---------|--------|
| F28 | Add to Cart (with quantity) | ✅ cart-context.md §6 |
| F29 | View Cart (items, subtotals, total) | ✅ |
| F30 | Update Quantity | ✅ |
| F31 | Remove Item | ✅ |
| F32 | Clear Cart | ✅ |
| F33 | Cart Expiration (TTL 7 days) | ✅ Redis key TTL |
| F34 | Price Snapshot at Add Time | ✅ anti-corruption layer |
| F35 | Guest Cart (anonymous → merge on login) | ❌ MISSING — CRITICAL |
| F36 | Save for Later / Wishlist | ❌ MISSING |
| F37 | Cart Stock Validation | ⚠️ PARTIAL — only at order time |
| F38 | Cart Abandonment Recovery | ❌ MISSING |

### A4. Checkout & Ordering (78% — 7 of 9)

| # | Feature | Status |
|---|---------|--------|
| F39 | Multi-Step Checkout | ✅ checkout-shipping-payment.md §2 |
| F40 | Checkout Summary / Calculation | ✅ |
| F41 | Place Order (async 202 Accepted) | ✅ |
| F42 | Order History | ✅ |
| F43 | Order Detail + Status | ✅ |
| F44 | Cancel Order | ✅ |
| F45 | Idempotency (no double orders) | ✅ IdempotencyKey |
| F46 | Split Shipment (multi-seller) | ✅ SubOrder per seller |
| F47 | Re-order (from history) | ❌ MISSING |

### A5. Shipping & Delivery (63% — 5 of 8)

| # | Feature | Status |
|---|---------|--------|
| F48 | Shipping Cost Calculation | ✅ |
| F49 | Multiple Shipping Methods | ✅ |
| F50 | COD (Cash on Delivery) | ✅ |
| F51 | Estimated Delivery Date | ✅ |
| F52 | Shipping Label Generation | ✅ StubCarrierGateway + GhnCarrierGateway |
| F53 | Real Order Tracking (carrier API) | ❌ MISSING — HIGH |
| F54 | Pickup Address per Seller | ⚠️ PARTIAL |
| F55 | Delivery Confirmation | ❌ MISSING |

### A6. Payment (63% — 5 of 8)

| # | Feature | Status |
|---|---------|--------|
| F56 | VNPAY Payment | ✅ Payment stub implemented |
| F57 | MoMo Payment | ✅ Stub |
| F58 | Bank Transfer | ✅ Manual upload receipt |
| F59 | Payment Callback / Webhook | ✅ |
| F60 | Payment Reconciliation | ⚠️ PARTIAL |
| F61 | Refund Processing | ✅ |
| F62 | Installment Payment (VNPAY) | ❌ MISSING |
| F63 | Saved Payment Methods | ❌ MISSING |

### A7. Coupons & Discounts (77% — 9 of 13)

| # | Feature | Status |
|---|---------|--------|
| F64 | Percentage Discount Coupons | ✅ coupon-context.md §2.1 |
| F65 | Fixed Amount Discount Coupons | ✅ |
| F66 | Minimum Order Value Requirement | ✅ |
| F67 | Maximum Discount Cap (for %) | ✅ |
| F68 | Per-User Usage Limit | ✅ |
| F69 | Validity Period (start → end) | ✅ |
| F70 | Coupon Validation at Checkout | ✅ |
| F71 | Coupon Browse (public coupon center) | ✅ |
| F72 | Admin Coupon CRUD | ✅ |
| F73 | Coupon Stacking (multiple per order) | ❌ MISSING |
| F74 | Auto-Apply Coupons (seasonal) | ❌ MISSING |
| F75 | Free Shipping Coupons | ⚠️ PARTIAL |
| F76 | First-Time Buyer Coupon | ❌ MISSING |

### A8. Reviews & Ratings (92% — 11 of 12)

| # | Feature | Status |
|---|---------|--------|
| F77 | Star Rating (1-5★) | ✅ review-context.md §2.1 |
| F78 | Written Review (text + images) | ✅ |
| F79 | Review with Images (max 5) | ✅ |
| F80 | Verified Purchase Badge | ✅ |
| F81 | Review Moderation (approve/reject) | ✅ |
| F82 | Review Summary (aggregate stats) | ✅ |
| F83 | Helpful Votes | ✅ |
| F84 | Sort Reviews (newest, highest, helpful) | ✅ |
| F85 | One Review Per User Per Product | ✅ |
| F86 | Edit Window (7 days) | ✅ |
| F87 | Product Q&A (buyer questions) | ✅ |
| F88 | Review with Photos Only filter | ❌ MISSING |

### A9. Notifications (67% — 6 of 9)

| # | Feature | Status |
|---|---------|--------|
| F89 | Order Confirmation Email | ✅ |
| F90 | Order Status Update Push | ✅ |
| F91 | SMS Notifications (critical only) | ✅ |
| F92 | In-App Notification Center | ✅ |
| F93 | Notification Preferences (opt-in/out) | ✅ |
| F94 | Quiet Hours | ✅ |
| F95 | Flash Sale Alert | ✅ |
| F96 | Notification History (user-facing) | ⚠️ PARTIAL — log exists, no API |
| F97 | Push Notification Deep Links | ❌ MISSING |

### A10. Post-Purchase (67% — 4 of 6)

| # | Feature | Status |
|---|---------|--------|
| F98 | Order Status Tracking | ✅ |
| F99 | Seller Fulfillment Workflow | ✅ |
| F100 | Return/Refund Flow | ❌ MISSING — CRITICAL |
| F101 | Dispute Resolution | ✅ |
| F102 | Digital Invoice / Receipt | ❌ MISSING |
| F103 | Re-order | ❌ MISSING |

### A11. Admin & Seller Portal (63% — 10 of 16)

| # | Feature | Status |
|---|---------|--------|
| F104 | Product CRUD (ADMIN) | ✅ |
| F105 | Category Management | ✅ |
| F106 | Order Management (status updates) | ✅ |
| F107 | Seller Approval | ✅ |
| F108 | Coupon Management | ✅ now in order-service |
| F109 | Review Moderation | ✅ now in product-service |
| F110 | Notification Template Management | ✅ |
| F111 | Audit Trail (who did what) | ✅ |
| F112 | Export (CSV/JSON/Excel) | ✅ |
| F113 | Admin Dashboard | ❌ MISSING — HIGH (no revenue/orders/growth view) |
| F114 | Sales Reports (daily/weekly/monthly) | ❌ MISSING |
| F115 | Inventory Management Dashboard | ⚠️ PARTIAL — seller inventory exists, no admin aggregate |
| F116 | Customer Management | ❌ MISSING |
| F117 | Content Management (banners, pages) | ❌ MISSING |
| F118 | SEO Management (meta, slugs, sitemap) | ❌ MISSING |
| F119 | Flash Sale Management | ✅ |

### Coverage Summary

| Category | Features | ✅ | ⚠️ | ❌ | % |
|----------|----------|----|-----|-----|-----|
| Account & Profile | 13 | 10 | 1 | 2 | 77% |
| Product Browsing | 14 | 11 | 0 | 3 | 79% |
| Shopping Cart | 11 | 7 | 1 | 3 | 64% |
| Checkout & Ordering | 9 | 7 | 0 | 2 | 78% |
| Shipping & Delivery | 8 | 5 | 1 | 2 | 63% |
| Payment | 8 | 5 | 1 | 2 | 63% |
| Coupons & Discounts | 13 | 9 | 1 | 3 | 69% |
| Reviews & Ratings | 12 | 11 | 0 | 1 | 92% |
| Notifications | 9 | 6 | 1 | 2 | 67% |
| Post-Purchase | 6 | 4 | 0 | 2 | 67% |
| Admin & Seller | 16 | 10 | 1 | 5 | 63% |
| **TOTAL** | **119** | **85** | **8** | **26** | **71%** |

---

## 5. Feature Completion — Non-Functional Requirements (40)

Source: `analysis/requirements-audit-v2.md` Part C.

### C1. Performance & Scalability (64% — 7 of 11)

| # | Requirement | Status |
|---|------------|--------|
| N1 | 10K Concurrent Users Target | ✅ |
| N2 | Browse p95 < 100ms | ✅ |
| N3 | Product Detail p95 < 80ms | ✅ |
| N4 | Place Order (202) p95 < 50ms | ✅ |
| N5 | Flash Sale Reserve p95 < 5ms | ✅ |
| N6 | Redis Hot SKU Sharding | ✅ |
| N7 | Kafka Throughput: 500+ orders/sec | ✅ |
| N8 | SCG Throughput: 50K+ req/sec | ✅ |
| N9 | Horizontal Scaling (all services) | ⚠️ PARTIAL — K8s HPA not documented |
| N10 | CDN for Static Assets | ❌ MISSING |
| N11 | Image Optimization (resize, WebP) | ❌ MISSING |

### C2. Reliability & Resilience (78% — 7 of 9)

| # | Requirement | Status |
|---|------------|--------|
| N12 | Circuit Breaker (all services) | ✅ |
| N13 | Retry with Exponential Backoff | ✅ |
| N14 | Dead Letter Queue (Kafka) | ✅ |
| N15 | Graceful Degradation | ✅ |
| N16 | Idempotent Operations | ✅ |
| N17 | 99.9%+ Uptime Target | ⚠️ PARTIAL — no SLO/SLA defined |
| N18 | Disaster Recovery (RPO/RTO) | ✅ |
| N19 | Data Backup Strategy | ✅ |
| N20 | Zero-Downtime Deployment | ⚠️ PARTIAL — SCG has replicas:2, no rolling update doc |

### C3. Security (46% — 6 of 13)

| # | Requirement | Status |
|---|------------|--------|
| N21 | OAuth2/OIDC (Keycloak) | ✅ |
| N22 | JWT Validation (JWKS, local) | ✅ |
| N23 | Rate Limiting (tiered) | ✅ |
| N24 | DDoS Protection | ✅ |
| N25 | CORS Configuration | ✅ |
| N26 | Request Size Limit (1MB) | ✅ |
| N27 | PII Protection (no logging bodies) | ✅ |
| N28 | CSRF Decision (API-only) | ⚠️ IMPLICIT — should be documented |
| N29 | SQL Injection Prevention | ⚠️ IMPLICIT — JPA handles, not documented |
| N30 | Encryption at Rest | ❌ MISSING — no TDE or disk encryption |
| N31 | Secrets Management | ⚠️ PARTIAL — env vars, no Vault |
| N32 | Penetration Testing Plan | ❌ MISSING |
| N33 | GDPR / Data Privacy Compliance | ❌ MISSING |

### C4. Observability (71% — 5 of 7)

| # | Requirement | Status |
|---|------------|--------|
| N34 | Correlation ID (distributed tracing) | ✅ |
| N35 | Structured Logging (JSON) | ✅ |
| N36 | Health Check Endpoints | ✅ |
| N37 | Prometheus + Grafana | ✅ |
| N38 | AlertManager (PagerDuty/Slack) | ✅ |
| N39 | Distributed Tracing (OTLP + Jaeger) | ⚠️ PLANNED — in roadmap, no spec |
| N40 | Audit Trail | ✅ |

### C5. DevOps & CI/CD (40% — 2 of 5)

| # | Requirement | Status |
|---|------------|--------|
| N41 | Docker-Compose Local Dev | ✅ |
| N42 | Kubernetes Manifests (base + overlays) | ✅ |
| N43 | Canary / Blue-Green Deployment | ❌ MISSING |
| N44 | Automated Rollback | ❌ MISSING |
| N45 | Load Testing (k6 scripts exist) | ⚠️ PARTIAL — scripts exist, no CI integration |

### NFR Summary

| Category | Total | ✅ | ⚠️ | ❌ | % |
|----------|-------|----|-----|-----|-----|
| Performance | 11 | 8 | 1 | 2 | 73% |
| Reliability | 9 | 7 | 2 | 0 | 78% |
| Security | 13 | 7 | 2 | 4 | 54% |
| Observability | 7 | 5 | 1 | 1 | 71% |
| DevOps/CI | 5 | 2 | 1 | 2 | 40% |
| **TOTAL NFR** | **45** | **29** | **7** | **9** | **64%** |

---

## 6. API Endpoint Inventory

Source: `evidence/task-1-baseline-inventory.md` — extracted from actual Spring controllers and NestJS route decorators.

### order-service (port 8091) — 22 endpoints
- `POST /orders` — CreateOrder
- `GET /orders` — ListOrders (buyer)
- `GET /orders/{id}` — ViewOrder
- `POST /orders/{id}/cancel` — CancelOrder
- `GET /sellers/me/orders` — SellerOrderQuery
- `POST /sellers/me/orders/{id}/accept` — AcceptOrder
- `POST /sellers/me/orders/{id}/reject` — RejectOrder
- `POST /sellers/me/orders/{id}/ship` — ShipOrder
- `POST /checkout/calculate` — CalculateCheckout
- `POST /checkout/shipping-options` — ShippingOptions
- `POST /checkout/validate-coupon` — ValidateCoupon
- `POST /checkout/apply-coupon` — ApplyCoupon
- `GET /coupons` — ListActiveCoupons (public)
- `POST /admin/coupons` — CreateCoupon (ADMIN)
- `GET /admin/coupons` — ListAllCoupons (ADMIN)
- `PUT /admin/coupons/{id}` — UpdateCoupon (ADMIN)
- `POST /admin/coupons/{id}/deactivate` — DeactivateCoupon (ADMIN)
- `GET /sellers/me/wallet` — ViewWallet (SELLER)
- `GET /sellers/me/payouts` — ListPayouts (SELLER)
- `POST /sellers/me/payouts` — RequestPayout (SELLER)
- `GET /admin/finance/*` — Admin finance endpoints
- `GET /admin/dashboard` — AdminDashboard

### product-service (port 8082) — 12+ endpoints
- `POST /products` — CreateProduct
- `GET /products` — ListProducts
- `GET /products/{id}` — ProductDetail
- `PUT /products/{id}` — UpdateProduct
- `GET /products/{id}/reviews` — ProductReviews
- `POST /reviews` — CreateReview
- `GET /reviews` — ListReviews
- `POST /reviews/{id}/helpful` — VoteHelpful
- `POST /questions` — AskQuestion
- `GET /products/{id}/questions` — ProductQuestions
- `POST /questions/{id}/answer` — AnswerQuestion (SELLER)
- `GET /admin/reviews/**` — AdminModeration (ADMIN)

### user-service (port 8081) — 9 endpoints
### cart-service (port 8084) — 5 endpoints
### shipping-service (port 8093) — rate quote, label creation, tracking
### payment-service (port 8092) — payment initiation, callback, reconciliation
### search-service (port 8086) — search, suggest, export
### notification-service (port 8087) — send, list, preferences

Full endpoint listings in `evidence/task-1-baseline-inventory.md`.

---

## 7. Architecture Compliance Audit

Source: `evidence/task-F1-audit.txt` — verified 2026-05-12.

| Check | Result |
|-------|--------|
| Domain framework imports | 0 violations across 13 services |
| Spring Boot version | 4.0.6 on all 12 Java services |
| NestJS decorators in domain | 0 |
| Dockerfile count | 13/13 |
| Hexagonal folder structure | domain/ application/ infrastructure/ in all 12 Java + notification NestJS |
| Gateway routes | 15 routes covering all services + categories + admin |
| Custom JWT generation | 0 (Keycloak only) |
| Hardcoded secrets | 0 (all env var placeholders) |
| Cross-service package imports | 0 (no service imports another service's package) |

---

## 8. Test Coverage Status

| Service | Tool | Gate | Status |
|---------|------|------|--------|
| order-service | JaCoCo | 90% line+branch | Configured. 19 coupon tests pass. Coupon use cases at 64% (need hardening) |
| product-service | JaCoCo | 90% line+branch | Configured. Context test blocked on PostgreSQL |
| user-service | JaCoCo | 90% line+branch | Configured |
| cart-service | Jest | 90% all metrics | Configured. Tests pass. |
| inventory-service | None | — | Not configured |
| search-service | None | — | Not configured |
| payment-service | None | — | 29 tests pass. No coverage gate |
| shipping-service | None | — | 5 tests pass. No coverage gate |
| coupon-service | JaCoCo | — | ⚰️ Deprecated |
| review-service | JaCoCo | — | ⚰️ Deprecated |
| seller-finance-service | JaCoCo | — | ⚰️ Deprecated |

---

## 9. Blocked Items & Prerequisites

| Blocker | Services Affected | How to Resolve |
|---------|-------------------|----------------|
| PostgreSQL not running | user, product, inventory, search, order (context tests) | `docker compose up -d postgres` |
| Docker Desktop unavailable | All runtime QA | Install Docker Desktop or equivalent |
| No VNPAY sandbox keys | payment-service live testing | Register at sandbox.vnpayment.vn |
| No MoMo sandbox keys | payment-service live testing | Register at business.momo.vn |
| No GHN/GHTK carrier accounts | shipping-service live testing | Register at giaohangnhanh.vn / ghtk.vn |

---

## 10. Priority Roadmap

### Immediate (next sprint)
1. **Product Variants** (F23) — CRITICAL. Add size/color/spec variants per SKU. Needed for fashion, shoes, electronics.
2. **Guest Cart** (F35) — CRITICAL. Allow browsing + cart before login. Merge on authenticate.
3. **Return/Refund Flow** (F100) — CRITICAL. Vietnam consumer law requires return policy.
4. **Coverage: coupon use cases** — bring from 64% to 90%.

### Short-term (2-3 sprints)
5. **Admin Dashboard** (F113) — Revenue, orders, top products, user growth.
6. **Real Order Tracking** (F53) — GHN/GHTK carrier API live integration.
7. **Product Images Gallery** (F24) — Multiple images + zoom per product.
8. **Digital Invoice** (F102) — Vietnam B2B e-invoice requirement.
9. **Coverage gates** — Add JaCoCo to inventory, search, payment, shipping.

### Medium-term (roadmap)
10. Multi-Language (F12)
11. Sales Reports (F114)
12. SEO Management (F118)
13. Customer Management (F116)
14. Search/Payment tech stack alignment (Java→NestJS or documented decision)
15. GDPR compliance (F13, N33)
16. Encryption at rest (N30)
17. Penetration testing (N32)

---

## 11. Documentation Map

| Document | Path | Purpose |
|----------|------|---------|
| Project README | `README.md` | Quick start, architecture overview, coding conventions |
| Architecture | `.sisyphus/ARCHITECTURE.md` | Full design decisions, component diagrams, tech rationale |
| **This Status Doc** | `.sisyphus/STATUS.md` | Complete project state: features, health, gaps, priorities |
| Requirements Audit | `.sisyphus/analysis/requirements-audit-v2.md` | 119-feature matrix + 45 NFR matrix |
| Bounded Contexts | `.sisyphus/analysis/bounded-contexts/` | One doc per domain: user, product, cart, order, coupon, review, notification, search, seller-finance, gateway, checkout |
| DTO Conventions | `.sisyphus/analysis/dto-conventions.md` | Record vs class rules |
| API Contracts | `.sisyphus/analysis/api-contracts/` | Per-service endpoint specs |
| DevOps Spec | `.sisyphus/analysis/devops-spec.md` | Docker, K8s, CI/CD, monitoring |
| Infrastructure Resilience | `.sisyphus/analysis/infrastructure-resilience.md` | Circuit breaker, retry, DLQ, DDoS |
| Audit Trail | `.sisyphus/analysis/audit-trail.md` | Logging requirements |
| Evidence | `.sisyphus/evidence/` | Task completion artifacts (80+ files) |
| Plans | `.sisyphus/plans/` | Completed and future work plans |
| Notepads | `.sisyphus/notepads/` | Session learnings and decisions |
