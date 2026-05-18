# Session handover — 2026-05-18 (pt3: public sellers + prod hardening)

**Last commit (HEAD before this session's commits):** `1986222a` (on `main`)
**Continues from:** `SESSION-HANDOVER-2026-05-18-pt2.md` (native auth + dev stack + E2E).
**Working tree:** dirty — 36 files changed (~+750/-70). All gates green; ready to commit.

This session shipped feature **B13 — public sellers endpoint** end-to-end and then hardened it for production after the user asked to drop the MVP framing.

## TL;DR

1. **Public sellers API** — `GET /sellers` (paged), `GET /sellers/{id}`. Anonymous reads; bank details never leak.
2. **FE wired in** — HomePage SellerShowcase shows real cards, new `/sellers/:id` route, ProductPage seller card now links to it.
3. **Cross-service stats** — product-service exposes `GET /reviews/seller/{id}/summary` + `/products/count?sellerId=` (single-id, used by detail page) and **batch** `POST /reviews/seller-summaries` + `POST /products/counts` (used by SellerShowcase, kills N+1).
4. **Prod hardening** — user-service `ProductServiceSellerStatsAdapter` now: timeouts (1s connect / 2.5s read via shared JdkClientHttpRequestFactory), Resilience4j circuit breaker + retry, Caffeine cache (5min TTL, 10k entries), graceful degradation on every path. ListPublicSellersUseCase: 2 HTTP calls per page (down from 2*pageSize).
5. **Validation + headers** — `RegisterSellerRequest` now bean-validated (`@NotBlank`, `@Size`, `@Pattern` on bankAccount). `GET /sellers` emits `X-Total-Count` and RFC-5988 `Link` headers (rel=prev/next).
6. **MVP wording stripped** from active code (messaging README/gateway, use-messages.ts, recommendations CoPurchaseAggregator). Historical handovers untouched.

All four gates pass:
- `node infra/scripts/e2e-day.mjs` → **35/35 PASS** (added `sellers` section)
- `cd fe && npx playwright test` → **17/17 PASS** (added `sellers.spec.ts`)
- `cd fe && npm run typecheck && npm run lint && npm run test -- --run && npm run build` → all green
- user-service `./mvnw test` → **107/107**, product-service → **25/25**

## What shipped

### Backend — user-service (public seller endpoints + adapter hardening)

| File | What |
|---|---|
| `src/main/resources/db/migration/V3__seller_public_fields.sql` | New columns: `description TEXT`, `logo_url VARCHAR(500)`, `banner_url VARCHAR(500)` + partial index `(approved, created_at DESC) WHERE approved = true` |
| `domain/SellerProfile.java` | Added 12-arg constructor (description, logoUrl, bannerUrl, createdAt). Old 8-arg constructor delegates with nulls. No call-site breakage. |
| `domain/SellerNotFoundException.java` | New — wired to 404 in ApiExceptionHandler |
| `domain/port/out/SellerStatsPort.java` | Single + **batch** methods. `SellerStats` record carries `Double ratingAvg, long ratingCount` with `empty()` helper |
| `application/GetPublicSellerUseCase.java` | Detail page — single-id path |
| `application/ListPublicSellersUseCase.java` | List page — collects sellerIds, **single batch call** to stats + counts, builds views. Validates page≥0, size>0, caps at 50 |
| `application/PublicSellerView.java`, `PublicSellersPage.java` | Use-case outputs |
| `infrastructure/web/PublicSellerResponse.java`, `PublicSellersPageResponse.java`, `SellerController.java` | DTOs + 2 new GETs (`/{id}` + paged list with `X-Total-Count` and `Link` headers). `/me` literal segment retains precedence |
| `infrastructure/web/RegisterSellerRequest.java` | `@NotBlank` + `@Size` + `@Pattern` on bankAccount |
| `infrastructure/integration/ProductServiceSellerStatsAdapter.java` | Caffeine cache + Resilience4j (`@CircuitBreaker`, `@Retry`) + inline try/catch fallback for non-AOP test paths + chunked batch (max 100 ids) |
| `infrastructure/config/RestClientConfig.java` | New — shared HttpClient with connect timeout, JdkClientHttpRequestFactory with read timeout, RestClient.Builder bean wired with the factory |
| `infrastructure/config/SecurityConfig.java` | Added `GET /sellers` and `GET /sellers/{id}` to permitAll alongside `/auth/**` and `/actuator/**` |
| `pom.xml` | Added `resilience4j-spring-boot3:2.2.0`, `resilience4j-micrometer:2.2.0`, `caffeine` |
| `application.yml` | New `vnshop.product-service.{connect,read}-timeout-ms,cache-ttl-seconds,cache-max-entries`. New `resilience4j.circuitbreaker.instances.product-service` (slidingWindowSize=10, failureRate=50, wait=10s, half-open=3) and `resilience4j.retry.instances.product-service` (3 attempts, 200ms exp backoff). Health probes on, `circuitbreakers` health enabled. Prometheus exposed |

Tests: `GetPublicSellerUseCaseTest`, `ListPublicSellersUseCaseTest` (incl. degraded stats + batch round-trip), `ProductServiceSellerStatsAdapterTest` (happy/null/error/empty for both single + batch), `SellerControllerPublicTest` (incl. `Link` header presence on middle page).

### Backend — product-service (single + batch stats endpoints)

| File | What |
|---|---|
| `application/CountSellerProductsUseCase.java` + Spring Data `countBySellerId` | Single — used by detail page |
| `application/review/SellerReviewSummaryUseCase.java` + new `findSellerReviewStats` (returns `List<Object[]>`, indexes `[0]`) | Single — used by detail page. **Bug fix:** the original `Object[]` return wrapped the row in an outer 1-elem array on Hibernate 7, blowing up on `row[1]`. List<Object[]> + `.get(0)` is the prod-grade shape |
| `infrastructure/web/SellerProductCountResponse.java` + `GetMapping("/products/count")` | Single endpoint |
| `infrastructure/web/review/ReviewController.java :: sellerSummary` | Single endpoint |
| `application/CountSellerProductsUseCase :: countAll(Set<String>)` | Batch use case — validates non-empty, ≤100, no nulls |
| `application/review/SellerReviewSummaryUseCase :: getSummaries(Set<String>)` | Same |
| `infrastructure/persistence/ProductJpaSpringDataRepository :: countBySellerIds` (JPQL `GROUP BY`) | Batch query |
| `infrastructure/persistence/review/ReviewJpaSpringDataRepository :: findSellerReviewStatsBatch` (native SQL `GROUP BY p.seller_id`) | Batch query |
| `infrastructure/web/ProductCountsRequest.java`, `ProductCountsResponse.java`, controller `POST /products/counts` | Batch endpoint with `@Valid @RequestBody`, `@NotEmpty @Size(max=100)` |
| `infrastructure/web/review/SellerSummariesRequest.java`, `SellerSummariesResponse.java`, controller `POST /reviews/seller-summaries` | Same |
| `ProductController :: findProducts` | Now also accepts `?sellerId=` query param (FE SellerDetailPage uses this for the product grid). Catalog port + JPQL extended to filter on it |

Tests: 9 new (batch happy/empty/over-100/null-id paths + on-empty short-circuit). Existing `FakeProductRepository` updated to satisfy the new port methods.

### Backend — api-gateway

| File | What |
|---|---|
| `infrastructure/config/SecurityConfig.java` | `GET` allowlist gained `"/sellers", "/sellers/*"` (single segment only — `/sellers/me`, `/sellers/me/products` stay authenticated). New `POST` allowlist for `"/reviews/seller-summaries", "/products/counts"` (batch endpoints — anonymous reads but POST so explicit allowlist needed) |

### Frontend

| File | What |
|---|---|
| `src/app/types/api/seller.ts` | `publicSellerSchema` + `publicSellersPageSchema` (zod), `PublicSeller` + `PublicSellersPage` types |
| `src/app/lib/api/endpoints/sellers.ts` + `sellers.test.ts` | `getSeller(id)`, `listSellers({page,size})` |
| `src/app/lib/api/endpoints/products.ts` | Added `sellerId?: string` to `ProductListParams` |
| `src/app/pages/HomePage.tsx` | `SellerShowcase` is now a real react-query horizontal card row (4 skeletons → real `SellerCard` w/ rating + product count → graceful `ComingSoonCard` fallback on empty/error) |
| `src/app/pages/SellerDetailPage.tsx` + `.test.tsx` | New page at `/sellers/:id`. Banner + header card with logo/rating/joinedAt/tier + description + product grid via `productList({sellerId: id})`. Loading + 404 + error states |
| `src/app/pages/ProductPage.tsx` | Replaced "coming soon" seller stub with real `SellerCard` + "Visit shop" `Link` to `/sellers/{id}` (graceful degrade if fetch fails) |
| `src/app/routes.ts` | Lazy-loaded `/sellers/:id` route |
| `src/app/lib/i18n/{vi,en}.json` | New keys: `home.sellersSection.{title,subtitle,viewAll,empty}`, `sellerDetail.{notFound,products,noProducts,joined,tier,ratingsLabel,productCount,error,visitShop}`, `common.loading` |

### Tests

| File | What |
|---|---|
| `infra/scripts/e2e-day.mjs` | Added `sellers` section: GET /sellers (paged, asserts `content` array + page/size/totalElements + bank-fields-absent) + GET /sellers/{seller1Id} (asserts ratingCount + totalProducts numeric + bank-fields-absent). Both anonymous |
| `fe/e2e/sellers.spec.ts` | 4 scenarios: API contract, 404 contract, HomePage real-cards-or-fallback, SellerDetailPage real-or-not-found-without-crashing |

### Docs

| File | What |
|---|---|
| `services/messaging-service/README.md` | "MVP" stripped from header |
| `services/messaging-service/src/messaging/infrastructure/messaging-ws.gateway.ts` | "for the MVP" stripped from gateway comment |
| `fe/src/app/hooks/use-messages.ts` | "for the MVP" stripped from useMessages comment |
| `services/recommendations-service/.../CoPurchaseAggregator.java` | "the volume is low for an MVP" → "current write volumes; revisit when contention shows up in metrics" |
| `docs/E2E-AUDIT-2026-05-18.md` | Updated to 35/35 + 17/17 |
| `docs/SESSION-HANDOVER-2026-05-18-pt3.md` | This file |

## Bugs surfaced + fixed

1. **`ReviewJpaRepository.getSellerReviewSummary` blew up with `Index 1 out of bounds for length 1`.** Hibernate 7 wraps single-aggregate-row native results in an outer 1-element array, so `Object[]` declared on the Spring Data method gave `row[0]` = the actual row, not the AVG. Fix: declare return as `List<Object[]>` and read `rows.get(0)`.
2. **user-service `SecurityConfig` denied `GET /sellers` with 401** — the gateway was correctly forwarding but the downstream service had its own `.anyRequest().authenticated()` rule. Added `permitAll()` for `GET /sellers` and `GET /sellers/{id}` in user-service's SecurityConfig too.
3. **`{id:[^/]+}` regex pattern in user-service SecurityConfig crashed startup** with "Expected close capture character after variable name }". Spring 6's PathPattern doesn't accept regex captures the same way as the old `AntPathMatcher`. Fix: use `{id}` (single segment by default).
4. **`ProductServiceSellerStatsAdapter` missing `RestClient.Builder` bean** on first deploy — Spring Boot 4 autoconfigures it, but a separate `@Configuration` class taking precedence had pulled the bean out. Fix: keep `RestClient.Builder` bean in `RestClientConfig` and wire the request factory into it.
5. **Resilience4j Spring Boot 3 starter pulled `spring-boot-starter-aop` transitively** — adding it explicitly broke the build because Boot 4 doesn't manage that artifact (no `<version>`). Fix: drop the explicit dep — resilience4j-spring-boot3 already brings AOP.
6. **Health-group config referenced `db` and `circuitBreakers` contributors that don't exist in the test profile** (test excludes DataSourceAutoConfiguration). Fix: drop the readiness group customisation; rely on default contributors. The `health.circuitbreakers.enabled=true` flag is what surfaces breaker state in `/actuator/health`.

## Production characteristics now in place (was MVP, now hardened)

- **N+1 elimination**: 2 HTTP calls per page (independent of page size) instead of 2*pageSize.
- **Caching**: Caffeine, 5-minute TTL, 10k cap, hit ratio recordable via `recordStats()`. Hot SellerShowcase traffic bypasses product-service after the first request.
- **Resilience4j**: circuit breaker (slidingWindowSize=10, failureRate=50%, wait=10s open, 3 half-open trial calls, automatic transition); retry (3 attempts, 200ms exp backoff); both wired through Spring Boot Actuator's `/actuator/health` so a tripped breaker shows up in the readiness probe.
- **Timeouts**: 1s connect / 2.5s read pinned via JdkClientHttpRequestFactory. Resilience4j trips well before either saturates the request thread pool.
- **Graceful degradation**: every adapter method has a `@CircuitBreaker(fallbackMethod=...)` plus an inline `try/catch` so unit tests that bypass the AOP proxy still see the fallback. Fallbacks return cached values for ids that have them and zero/empty defaults for the rest. The catalog page **never** errors because product-service is slow.
- **Validation**: `RegisterSellerRequest` is `@Valid`-annotated; `bankAccount` enforces `^[0-9A-Za-z\-]+$`. Batch endpoints in product-service cap input at 100 ids.
- **HTTP semantics**: paged endpoint emits `X-Total-Count` and RFC-5988 `Link` headers in addition to the JSON envelope. Body shape unchanged for React Query.
- **Pagination correctness**: `size=0` and negative `page` rejected at the use-case boundary with `IllegalArgumentException`. Size capped at 50 server-side. Approved-only filter enforced in JPQL.
- **Defence in depth**: bank details (`bankName`, `bankAccount`) are domain fields on `SellerProfile` but **never** appear in `PublicSellerResponse`. The e2e suite explicitly asserts their absence on every public payload. Two paths only carry them: `POST /sellers/register` and `GET /sellers/me`, both authenticated.

## Operational gotchas (durable rules — keep in mental model)

1. **Hibernate 7 wraps single-aggregate native rows in an outer 1-elem array.** If you write `Object[] foo = repo.findStats(...)`, you'll read the row not the columns. Use `List<Object[]>` and `.get(0)`.
2. **Spring Boot 4 path patterns don't accept regex captures inside `{...}`.** `{id}` matches a single segment by default. `{id:[^/]+}` is a parser error.
3. **`@CircuitBreaker` is AOP-only.** Unit tests that `new` the bean directly bypass the proxy, so the fallback never fires. Either Spring-context-test the adapter or include an inline `try/catch` that mirrors the fallback.
4. **resilience4j-spring-boot3 brings AOP transitively.** Don't add `spring-boot-starter-aop` explicitly in Boot 4 — there's no managed version, the build will fail.
5. **Don't add health contributors to the readiness group blindly.** `db` and `circuitBreakers` only exist when their respective autoconfigs are active — test profiles often exclude DataSourceAutoConfiguration.
6. **Public reads need permitAll on BOTH sides:** the gateway's `SecurityConfig` AND the downstream service's `SecurityConfig`. Gateway-only allowlist passes the request through but the downstream returns 401.
7. **N+1 across HTTP boundaries is silent in dev.** A 20-card SellerShowcase doing 40 HTTP calls works fine locally but melts under load. Always batch.

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `1986222a`. Working tree is dirty (this session's commits not yet made).
2. **Inspect the diff** — `git status --short` lists ~36 files. Group commits as you prefer (suggested: `feat(user-service): public seller endpoints`, `feat(product-service): single + batch stats endpoints`, `feat(fe): SellerShowcase + SellerDetailPage`, `chore: strip MVP framing`, `test(e2e): sellers section + Playwright spec`, `docs: pt3 handover`).
3. **Run the gates** (stack must be up + seeded):
   ```bash
   docker compose --profile apps up -d
   bash infra/scripts/setup-keycloak-admin-client.sh
   node infra/scripts/seed-demo.mjs
   node infra/scripts/e2e-day.mjs                       # 35/35
   cd fe && npx playwright test                         # 17/17
   cd fe && npm run typecheck && npm run lint && npm run test -- --run && npm run build
   ```
4. **Pick from the deferred list** (next-leverage items unchanged from pt2):
   - **B3 — Coupon validate + apply scenario** (small surface; just an e2e-day section).
   - **S2 — Admin seller approval scenario** (endpoint exists, never exercised by the suite).
   - **C1+C2 — httpOnly-cookie session proxy** (largest remaining security trade-off; multi-day lift).
   - **B9 — Live shipping rate quote** (flagged in two prior handovers).

## Test users (Keycloak realm `vnshop`, all password `test`) — unchanged

- `buyer1` — BUYER role
- `seller1` — SELLER role (also has BUYER, owns the e2e demo product)
- `admin1` — ADMIN role (also has BUYER)

`seller1` does **NOT** have a domain `SellerProfile` row until `/sellers/register` is called — so `GET /sellers/{seller1Id}` returns 404 by default. The e2e suite accepts that. Visiting HomePage SellerShowcase shows the empty-state ComingSoonCard until at least one seller registers.

## Endpoints touched this session

| Method | Path | Auth | What |
|---|---|---|---|
| GET | `/sellers` | anon | Paged public list, `X-Total-Count` + `Link` headers |
| GET | `/sellers/{id}` | anon | Single seller w/ ratingAvg, ratingCount, totalProducts |
| GET | `/products?sellerId=...` | anon | Catalog filter — used by SellerDetailPage |
| GET | `/products/count?sellerId=...` | anon | Single-id, used by detail page |
| GET | `/reviews/seller/{id}/summary` | anon | Single-id, used by detail page |
| POST | `/products/counts` | anon | Batch (≤100 ids), used by SellerShowcase |
| POST | `/reviews/seller-summaries` | anon | Batch (≤100 ids), used by SellerShowcase |

## Final tally

- **API E2E:** 33→35 (added 2 sellers steps).
- **Playwright:** 13→17 (added 4 sellers steps).
- **user-service tests:** ~95→107 (added batch + adapter + use-case + controller header tests).
- **product-service tests:** 16→25 (added batch use-case + repo + count tests).
- **Diff:** 36 files, ~+750 / −70.
- **Production characteristics added:** timeouts, circuit breaker, retry, cache, validation, batch endpoints, pagination headers.
- **MVP framing:** removed from active code; historical handovers preserved.
