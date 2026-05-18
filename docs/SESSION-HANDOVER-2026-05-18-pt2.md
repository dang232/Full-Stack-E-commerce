# Session handover — 2026-05-18 (pt2: auth + dev-stack + E2E)

**Last commit (HEAD):** `8feed2b5` (on `main`)
**Continues from:** `SESSION-HANDOVER-2026-05-18.md` (mock-data removal). HEAD at the start of this session was `80590d45`.
**Session length:** 40 commits. `git diff --stat 80590d45..HEAD` → **73 files, +3469, −1947**.
**Working tree:** clean.

This was a long block. After the pt2 mock-data work shipped (and the resulting empty-state-everywhere FE rendered honestly against the BE), the user asked for the stack to actually be running, then for a native login/register, then for the whole API surface to be E2E-tested, then for the FE-to-BE flow to be Playwright-tested. Each phase surfaced its own crop of bugs which are catalogued below.

## TL;DR

Five phases shipped end-to-end, all green:

1. **Dev stack stood up** — Docker stack already mostly running; FE container rebuilt with today's code, demo catalog seeded via a new script.
2. **Native auth (FE form, Keycloak backend)** — replaced the Keycloak-redirect login with `/login` + `/register` native forms, ROPC against `vnshop-api` for tokens, `/auth/register` BE endpoint that proxies to Keycloak Admin API.
3. **i18n cleanup** — wired remaining hardcoded VN strings on HomePage/SearchPage/ProductPage; added a real branded landing hero.
4. **Day-in-the-life API E2E** — `infra/scripts/e2e-day.mjs` exercises 33 endpoints (register → catalog → cart → checkout → order → fulfilment → review → admin). 33/33 PASS.
5. **FE-to-BE Playwright** — `fe/e2e/{smoke,buyer-happy-path,authenticated-routes,role-routes}.spec.ts` drives the dockerised FE through real cross-origin browser requests. 13/13 PASS.

12 BE/FE bugs found and fixed along the way — catalogued in `docs/E2E-AUDIT-2026-05-18.md`. None of the deferred scenarios (payment IPN, saga, coupon, websocket messaging, notifications, live shipping rate) were touched in this session.

## What shipped (commits grouped by area)

### 1. Native auth migration (replaced keycloak-js)

| Commit | What |
|---|---|
| `7e2a87b2` | infra(keycloak): add `vnshop-admin-api` confidential client |
| `64c8be59` | feat(user-service): POST `/auth/register` self-registration endpoint |
| `41c3e0f6` | feat(api-gateway): forward `/auth/**` to user-service |
| `56a13e6e` | refactor(fe): replace keycloak-js with native ROPC auth provider |
| `71169530` | feat(fe): native LoginPage + RegisterPage + CSS layout fix |
| `154be94b` | chore(fe): remove keycloak-js dependency |
| `729ae3bb` | fix(api-gateway): wire CORS into the security chain |
| `69471b93` | fix(fe): default Keycloak client id to `vnshop-api` for ROPC |
| `3f3a4988` | fix(fe): map Keycloak `invalid_grant` to `invalid_credentials` + ditch `instanceof` |

### 2. Dev stack + setup

| Commit | What |
|---|---|
| `f7d5ccfd` | infra(scripts): seed-demo for local catalog bootstrap (Node + bash) |
| `91bcf806` | infra: extend `setup-keycloak-admin-client.sh` with webOrigins fix |
| `15b24278` | fix(product-service): accept DB-side trailing zeros on Money amounts |
| `6d854980` | fix(product-service): cast nullable string params in findCatalog JPQL |
| `a9fb21fc` | fix(fe): adapt `/categories` string[] response to Category[] at boundary |
| `390d6fd1` | fix(fe): accept BE product image objects + variant-derived price/stock |

### 3. i18n + UX polish

| Commit | What |
|---|---|
| `36ac6226` | i18n(fe): wire HomePage + SearchPage hardcoded strings + landing hero |
| `265646c6` | i18n(fe): use ratingAtLeast key for active rating filter chip |
| `0f4a3a36` | i18n(fe): wire remaining hardcoded VN strings on product/cards/badges |

### 4. Day-in-the-life API E2E

| Commit | What |
|---|---|
| `5324ce3e` | infra(scripts): add `e2e-day.mjs` smoke suite (33 endpoints) |
| `007812a6` | fix(user-service): align main class package with rest of codebase |
| `8b7bfb86` | fix(order-service): audit columns, jsonb binding, kafka serializer, order-number uniqueness |
| `7ef4cc98` | fix(product-service, gateway): public reads, JWT-derived buyerId, kafka serializer |
| `f45fb4c0` | fix(order-service): preserve sub-order id on save to keep stable BIGSERIAL |
| `17892aea` | infra(scripts): poll orders projection in e2e and accept orderId field |
| `c8f0a2b8`, `a28d4c27` | docs: E2E audit |

### 5. FE-to-BE Playwright

| Commit | What |
|---|---|
| `39aea507` | test(fe): rewrite Playwright suite for the dockerised stack |
| `696f1edf` | test(fe): add authenticated-routes + search coverage |
| `96388827` | test(fe): add role-routes spec + pin Playwright to single worker |
| `3ac9486f` | chore: gitignore Playwright artefacts |
| `af771963`, `8feed2b5` | docs: bump audit |

## Verified clean

```bash
# API E2E (33/33)
node infra/scripts/e2e-day.mjs

# Playwright FE-to-BE (13/13)
cd fe && npx playwright test

# FE local gates
cd fe && npm run typecheck && npm run lint && npm run test -- --run && npm run build
```

All four are green at HEAD `8feed2b5`.

## Bugs fixed this session

Twelve. Cross-referenced with commit shas; all in production code, not test code:

| # | Service | Bug | Commit |
|---|---|---|---|
| 1 | user-service | Main class in wrong package — every JPA query 500'd with `UnknownEntityException` | `007812a6` |
| 2 | order-service | Missing `created_at` / `updated_at` on `orders`/`sub_orders`/`order_items` | `8b7bfb86` |
| 3 | order-service | Outbox `payload` JSONB bound as VARCHAR | `8b7bfb86` |
| 4 | order-service | Kafka producer used StringSerializer for record types | `8b7bfb86` |
| 5 | order-service | Order numbers collided across restarts | `8b7bfb86` |
| 6 | order-service | `SubOrderJpaEntity.fromDomain` dropped id → orphan-replacement on every save | `f45fb4c0` |
| 7 | product-service | Same Kafka serializer issue for `ProductEvent` | `7ef4cc98` |
| 8 | product-service | Reviews/questions required `buyerId` from body instead of JWT | `7ef4cc98` |
| 9 | product-service | `Money` rejected DB-side trailing zeros (DECIMAL(19,2)) | `15b24278` |
| 10 | product-service | `findCatalog` JPQL bound nullable strings as bytea | `6d854980` |
| 11 | api-gateway | `/reviews`, `/questions`, `/recommendations` blocked anonymous GETs | `7ef4cc98` |
| 12 | api-gateway | CORS preflights returned 200 with no Access-Control headers | `729ae3bb` |

Plus three FE-only bugs (Keycloak client id default, error-code mapping, product-image schema rigidity) — all in commits `69471b93`, `3f3a4988`, `390d6fd1`.

## What's still missing (deferred — neither verified nor fixed)

Documented in `docs/E2E-AUDIT-2026-05-18.md` under "Pre-existing endpoints we did NOT exercise". Each needs its own scenario suite:

- **VNPAY / MOMO payment** — intent creation, IPN callbacks, return URL handshake. Needs a mock provider.
- **Saga compensation** — cancel-after-partial-fulfilment, return + refund flow. The order-service has the saga listeners; never exercised end-to-end.
- **Cart guest mode + merge after login** — cart-service supports `x-user-id` from gateway; guest-cart cookie path is unverified.
- **Coupon validate + apply** — `/coupons/validate`, `/checkout/apply-coupon`. Endpoints exist in coupon-service but were not exercised.
- **Messaging WebSocket** (`/ws/messaging`) — JWT-via-`?token=` auth; pt3 handover flagged this is the highest-risk untested path.
- **Admin seller approval** (`POST /admin/sellers/{id}/approve`) — endpoint exists, never called by the suite.
- **Notifications inbox** — Kafka-consuming service, no E2E coverage.
- **Live shipping rate-quote** — flagged as the highest-leverage BE work in the pt2 handover; still TODO.
- **Saved payment methods (F62, F63)** — FE shows "coming later" banner; BE has no endpoint.
- **Hero/promo/trending CMS** — HomePage shows `<ComingSoonCard>` stubs because no BE CMS exists.
- **Public sellers endpoint** — only admin-only `/admin/sellers` exists; storefront SellerShowcase + ProductPage seller card stubbed.
- **Native password reset + 2FA** — currently links out to Keycloak's account console.
- **Email verification flow** — register marks `emailVerified: true`; real flow not wired.
- **Translation review pass round 2** — pt2 handover candidate; still pending.

## What should be an upgrade (technical debt + suggested next moves)

Ordered by leverage. Take from the top.

1. **Move auth tokens off `localStorage` to httpOnly cookies.** ROPC is OAuth-2.1 deprecated and tokens in `localStorage` are XSS-vulnerable. Concretely: add a session-proxy route on user-service that holds the refresh token in an httpOnly cookie and proxies bearer tokens to the FE via a short-lived in-memory cache. Bigger lift but removes the largest known security trade-off.
2. **Stop using `ListPendingOrdersUseCase` as a sub-order lookup helper.** It currently doubles as the ship/accept endpoint's id resolver. The fromDomain id-preservation patch (#6 above) made it work, but the API surface is muddled. Move the sub-order lookup into a dedicated query and let the ship use case validate seller-id itself.
3. **Public sellers endpoint** unlocks the SellerShowcase and ProductPage seller card to come back as real data. The audit doc lists this first because the BE only has `/admin/sellers` (gated to ADMIN role); a buyer-facing `GET /sellers/{id}` and `GET /sellers` (paged) is small surface and high visual return.
4. **Per-route gateway rate limits.** Resilience4j currently trips on bursts of register/login (Playwright workers had to be pinned to 1). Splitting `/auth/**` and `/orders` into separate breakers, and bumping the per-IP register limit, would let parallel test workers run.
5. **Move the realm-import patches into the JSON.** `infra/scripts/setup-keycloak-admin-client.sh` does three post-import fixups (fullScopeAllowed, resource-access mapper, `vnshop-api` webOrigins). All three could live in `vnshop-realm.json` so a fresh stack is a single `docker compose up`. Keycloak 26 quirks made it easier to patch live; revisit on a Keycloak upgrade.
6. **Saga + outbox visibility.** With the Kafka serializer fix landed, projections work, but there's still no admin view of the outbox, the saga state machine, or the Dead-Letter pile. A small `/admin/outbox` page with retry/replay buttons would unblock production debugging.
7. **Migrate from `vnshop-api` (PUBLIC + directAccessGrants) to a confidential auth proxy** when we move tokens off `localStorage`. ROPC stays acceptable for dev, gets replaced with code/PKCE through the cookie-session route in production.
8. **Native password-reset form** — currently links out to Keycloak's account console. A small reset flow (`POST /auth/reset-request` → Keycloak Admin API send-email + `POST /auth/reset` to validate the OTP) would close the only seam where Keycloak chrome leaks through.
9. **Order-summary projection lag visibility.** The `e2e-day.mjs` polls /orders for up to 5s after POST. Production callers need to know there's a CQRS read-side window. Either expose the projection state via header or document the contract.
10. **Increase test coverage on the deferred scenarios** in priority order: (a) coupon validate (small surface, high signal), (b) cart guest+merge, (c) admin seller approval, (d) saga cancel-after-partial. Payment IPN and websocket messaging are bigger lifts and need mock providers.
11. **Productionise the seed script.** `seed-demo.mjs` uses the seller1/test password literally; works locally but a CI-friendly version would read creds from env and emit deterministic product ids.
12. **Re-run the FE post-merge quality pass per pt3 rule.** Three things to look at: the `<ComingSoonCard>` lift candidate (used 4x in HomePage; if a 5th appears, lift to `components/`), the duplicate brand panels in LoginPage/RegisterPage (could become `<AuthBrandPanel>`), and the duplicate role-guard check in role-routes.spec.

## Operational gotchas (durable rules — pin in the next session's mental model)

1. **Resilience4j circuit breakers latch.** When earlier 500s tripped the breaker, every subsequent request returned 503 (`Service temporarily unavailable`). After fixing root causes, `docker compose restart api-gateway` to reset.
2. **Docker build cache lies.** Maven layers cached old class files multiple times this session. Use `docker compose build --no-cache <service>` after structural Java changes (package moves, annotation adds). Verify with `docker exec <container> sh -c "unzip -p /app/app.jar BOOT-INF/classes/.../X.class | strings | grep <expected-symbol>"`.
3. **Keycloak realm-import is one-shot.** Once the realm exists, JSON edits are ignored. Either wipe the keycloak postgres volume (loses test users) or kcadm-patch live; the setup script does the latter.
4. **`@SpringBootApplication` package = JPA scan root.** If the main class lives in `com.vnshop.user_service` but entities are in `com.vnshop.userservice`, default JPA scanning misses every entity. Spring Boot 4 made `@EntityScan` import paths different too; safest fix is moving the main class to the right package.
5. **Spring Cloud Gateway's `globalcors` doesn't expose a `CorsConfigurationSource` bean.** `.cors(withDefaults())` finds nothing on the Spring Security chain. Define the bean explicitly + permit OPTIONS on `/**`.
6. **Vite chunk splitting kills `instanceof` across modules.** When the page lives in one chunk and the error class in another, the imported constructor and the thrown one have separate identities. Duck-type on `errorCode` instead.
7. **Hibernate JSONB binding needs `@JdbcTypeCode(SqlTypes.JSON)`.** `columnDefinition = "jsonb"` only affects schema generation, not parameter binding.
8. **Kafka producers default to StringSerializer.** Records of any kind (`OutboxEvent`, `ProductEvent`) need an explicit `JsonSerializer` in `application.yml`.
9. **JPA `@OneToMany(orphanRemoval=true)` + missing id on fromDomain = silent re-insertion.** Always copy the existing id when mapping domain → entity for updates.
10. **Per pt3 rule, post-merge quality pass.** After every batch of fixes: typecheck → lint → test → build → `Grep` for stragglers. Done this session for FE; BE Java verified by happy-path E2E.

## File locations to know (new since pt2)

- **API E2E suite:** `infra/scripts/e2e-day.mjs` (Node, no deps)
- **Playwright suite:** `fe/e2e/{smoke,buyer-happy-path,authenticated-routes,role-routes}.spec.ts`
- **Playwright config:** `fe/playwright.config.ts` (defaults to `http://localhost:3000`, `workers: 1`)
- **Audit doc:** `docs/E2E-AUDIT-2026-05-18.md` (two suites, all 12 bugs, deferred list)
- **Setup script:** `infra/scripts/setup-keycloak-admin-client.sh` (idempotent; fix 12 lives here)
- **Seed script:** `infra/scripts/seed-demo.mjs` (13 products across 5 categories; FORCE=1 to overwrite)
- **Native auth core:** `fe/src/app/lib/auth/native-auth.ts` (passwordLogin, refreshTokens, revokeTokens)
- **Auth provider:** `fe/src/app/hooks/use-auth.tsx` (rewrite — same `useAuth()` shape as before)
- **Pages:** `fe/src/app/pages/{LoginPage,RegisterPage}.tsx` (native forms)

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `8feed2b5`. Working tree should be clean.
2. **Start the stack** if down:
   ```bash
   docker compose --profile apps up -d
   bash infra/scripts/setup-keycloak-admin-client.sh   # idempotent; runs 3 Keycloak fixups
   node infra/scripts/seed-demo.mjs                    # skips if catalog non-empty
   ```
3. **Run the gates:**
   ```bash
   node infra/scripts/e2e-day.mjs        # 33/33
   cd fe && npx playwright test          # 13/13
   ```
   Both should be green at HEAD. If you see 503s in either suite, the gateway breakers latched — `docker compose restart api-gateway` and re-run.
4. **Pick from "What's still missing"** above. Top three by leverage:
   - Public sellers endpoint (unblocks SellerShowcase visual)
   - Coupon validate + apply scenario (closes a known gap with small surface)
   - httpOnly-cookie token migration (largest remaining security trade-off)
5. **Per durable rule:** when delegating work to sub-agents, rebase-onto-local-main pre-flight is mandatory if local is ahead of origin. The pt3 rule still stands.

## Test users (Keycloak realm `vnshop`, all password `test`)

- `buyer1` — BUYER role
- `seller1` — SELLER role (also has BUYER, used in role-routes.spec)
- `admin1` — ADMIN role (also has BUYER, used in role-routes.spec)

Plus any number of `e2e_buyer_<timestamp>@vnshop.local` users created by Playwright runs. They're harmless but accumulate; wipe with the keycloak postgres volume reset if needed.

## Endpoints (running stack)

| URL | What |
|---|---|
| http://localhost:3000 | FE (today's bundle, native auth, real BE wiring) |
| http://localhost:8080 | API gateway |
| http://localhost:8085 | Keycloak admin (admin / admin) |
| http://localhost:16686 | Jaeger UI |
| http://localhost:9200 | Elasticsearch |

## Final tally for the session

- **Started:** 16/33 API endpoints passing on first dry run, FE login a Keycloak redirect, no FE-to-BE tests.
- **Ended:** 33/33 API + 13/13 Playwright. Native FE auth. 12 BE/FE bugs fixed across 5 services + gateway + FE. Audit doc + handover doc current.
- **Commits:** 40, all on `main`. None pushed (local-only per usual project pattern).
- **Diff:** +3469 / −1947 across 73 files.
