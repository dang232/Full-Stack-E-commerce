# Session handover — 2026-05-19 (pt4: deferred features burndown + httpOnly cookie auth)

**Last commit (HEAD before this session's commits):** `1986222a` (the pt3 handover commit)
**Commits this session:** 17 (10 since the pt3 handover landed earlier in the same calendar day)
**Working tree:** clean. All gates green.

After pt3 shipped public sellers + production hardening, this block burned through four deferred items from the next-leverage list and landed the largest remaining security migration:

1. **B3 — Coupon validate + apply E2E** + a Redis config fix that had quietly broken /admin/coupons writes since the last stack restart.
2. **S2 — Admin seller approval E2E** covering register → approve → public list visibility.
3. **B7 — Cart guest mode + merge-on-login** — anonymous users can now use the cart; replays into the server cart on first authenticated render. Mirrors the wishlist legacy-storage pattern.
4. **Saga compensation E2E** — cancel-before-fulfilment + return + refund; **surfaced + fixed** a long-dormant V18 audit-columns bug on the returns table.
5. **C1+C2 — httpOnly cookie auth migration** — refresh tokens leave localStorage; user-service hosts a thin /auth proxy issuing the cookie.

## TL;DR

All four gates green at HEAD:
- `node infra/scripts/e2e-day.mjs` → **52/52 PASS** (was 35/35 after pt3, +17 across coupon, admin-seller, saga sections)
- `cd fe && npx playwright test` → **19/19 PASS** (was 17/17, +2 guest-cart scenarios; sellers spec strict-mode locator fix)
- FE typecheck/lint/vitest 143/143/build — clean
- user-service tests 107/107, product-service 25/25

## What shipped (commits in chronological order)

| # | Commit | What |
|---|---|---|
| 1 | `28149587` | docs(readme): rewrite to match current 16-service stack — pre-pt4 cleanup |
| 2 | `600524fb` | fix(coupon-service): wire Redis host/port from REDIS_HOST/REDIS_PORT env |
| 3 | `2ba3cbc8` | test(e2e-day): add coupon validate + apply scenario (B3) |
| 4 | `53ac4c65` | test(e2e-day): add admin seller approval scenario (S2) |
| 5 | `5a274f08` | feat(fe): cart guest mode + merge-on-login (B7) |
| 6 | `cf9236b7` | test(fe): playwright guest-cart spec + sellers strict-mode fix |
| 7 | `7bdddd5e` | fix(order-service): backfill audit columns on returns table (V18) |
| 8 | `efac01ca` | test(e2e-day): add saga compensation scenario (cancel + return + refund) |
| 9 | `aa2d3401` | feat(auth): httpOnly-cookie session proxy on user-service (C1+C2) |
| 10 | `4bfb7bc1` | feat(fe): swap to httpOnly-cookie auth (C1+C2) |

## Bugs surfaced + fixed

1. **coupon-service had no `spring.data.redis.host` binding.** docker-compose was setting `REDIS_HOST=redis` but the application.yml only had defaults pointing at `localhost:6379`. Every `/admin/coupons` write blew up with `RedisConnectionFailureException`; the breaker masked it as 400. Fix: explicit `spring.data.redis.host: ${REDIS_HOST:localhost}` + `${REDIS_PORT:6379}` in application.yml.
2. **`order_svc.returns` was missing audit columns.** `ReturnJpaEntity extends BaseJpaEntity` (which expects `created_at`/`updated_at`) but V4 created the table before the `@MappedSuperclass` refactor and V17 only patched orders/sub_orders/order_items. Every SELECT on returns was 500ing with `column rje1_0.created_at does not exist` — silently broke the entire return + refund saga path since V17 landed. Fix: V18 mirrors the V17 pattern (ADD COLUMN IF NOT EXISTS, backfill from requested_at/resolved_at, ALTER COLUMN SET NOT NULL).
3. **Sellers Playwright strict-mode violation.** `locator("main, [role='main'], body")` matched both `<body>` and `<main>` on pages that rendered both. Fix: `.first()`.

The V18 bug only surfaced because the saga scenario was the first test ever to drive the return endpoints — direct evidence the deferred-list burndown is doing real work.

## What's still missing (deferred — pt4 → pt5)

From the original list, with status:

- ~~B3 — Coupon validate + apply~~ ✅ done
- ~~S2 — Admin seller approval~~ ✅ done
- ~~B7 — Cart guest mode + merge~~ ✅ done
- ~~C1+C2 — httpOnly cookie auth~~ ✅ done
- ~~Saga compensation E2E~~ ✅ done

Remaining:
- **B9 — Live shipping rate quote.** Flagged as highest-leverage BE work three handovers ago. Adds `/shipping/rate-quote` feeding checkout shipping options. Currently the FE pulls a static option list. Pluggable adapter port (stub first, GHN/GHTK later).
- **B11 — Messaging WebSocket E2E coverage.** The `/ws/messaging` handshake takes the JWT via `?token=` (browsers can't set Authorization on `new WebSocket(...)`). Highest-risk untested path. Needs a Playwright scenario covering handshake, real-time delivery, token validation rejection.
- **VNPAY / MOMO IPN.** Endpoints exist; intent + IPN never exercised. Needs a mock provider service to drive an end-to-end scenario without a real PSP.
- **Notifications inbox.** notification-service consumes Kafka; no inbox endpoint or FE surface yet. Multi-day: schema + REST + WebSocket push + FE bell.
- **Native password reset / 2FA.** Currently bounce out to Keycloak's account console.
- **Email verification.** `emailVerified: true` set on register — real verification not wired.
- **Public sellers visual polish.** SellerShowcase and SellerDetailPage are functional but minimal.
- **Hero/promo/trending CMS.** No BE; HomePage `<ComingSoonCard>` stubs in place.

## Production characteristics now in place (delta from pt3)

The pt3 list still applies (Resilience4j, Caffeine, batch endpoints, pinned timeouts, validation, pagination headers). New since pt3:

- **httpOnly cookie auth.** Refresh token left localStorage; lives in `vnshop_rt` (Path=/auth, HttpOnly, SameSite=Lax, configurable Secure). Access token is JS-memory-only. XSS can't bootstrap a new session — the access token alone is insufficient and the cookie is unreadable from JS. KC realm config unchanged.
- **CORS allowCredentials.** Required by the cookie flow. Safe because allowed-origins is a concrete list (`http://localhost:3000`, `http://localhost:5173`), not a wildcard.
- **Cart guest mode.** Anonymous users get a localStorage cart at `vnshop:guest-cart`; one-shot replay on first authenticated render mirrors the wishlist pattern. Failures during merge are logged and swallowed — partial outage doesn't strand the user.
- **Coupon-service Redis wiring.** Was using default localhost; now respects REDIS_HOST.
- **Returns audit columns.** V18 unblocks the entire return + refund path that had been silently 500ing since V17.

## Operational gotchas (durable rules — additions to the pt3 list)

The pt3 list still applies (Hibernate single-row aggregate wrapping, Spring 4 PathPattern regex, AOP-only `@CircuitBreaker`, etc.). New rules learned this session:

1. **Cookie-based auth needs `credentials: "include"` on EVERY auth call.** Without it, the browser strips the cookie on cross-origin requests and refresh 401s forever. Pair this with `setAllowCredentials(true)` on the gateway CORS filter.
2. **Servlet `Cookie` class doesn't expose SameSite.** Compose `Set-Cookie` manually if you need it, or use Spring's `ResponseCookie`.
3. **Test profiles silently exclude DataSourceAutoConfiguration in some service templates.** Don't add health contributors like `db` or `circuitBreakers` to a readiness group without verifying they exist in the test profile — it'll fail context loading.
4. **`@MappedSuperclass` is retroactive.** Adding fields to BaseJpaEntity affects every subclass — including tables that predate the refactor. V17/V18 had to backfill audit columns for older tables one migration at a time.
5. **Long-running containers cache stale config.** coupon-service had been `Up 2 days` when its application.yml hadn't bound REDIS_HOST. The error was hidden by the breaker until the next test fired. After config changes touching env-var bindings, `docker compose build --no-cache <service>` AND `docker compose up -d <service>` both required.

## File locations to know (new since pt3)

### Backend (auth proxy)
- `services/user-service/src/main/java/.../infrastructure/keycloak/KeycloakTokenClient.java` — sibling of KeycloakAdminClient. ROPC password grant, refresh-token grant, revoke. Surfaces invalid_credentials as 401 regardless of KC's underlying status.
- `services/user-service/src/main/java/.../infrastructure/web/AuthSessionController.java` — `/auth/login`, `/auth/refresh`, `/auth/logout`. Composes Set-Cookie manually for SameSite. Configurable via `vnshop.auth.cookie-secure` / `vnshop.auth.cookie-same-site`.

### Backend (V18 migration)
- `services/order-service/src/main/resources/db/migration/V18__returns_audit_columns.sql`

### Backend (coupon-service Redis)
- `services/coupon-service/src/main/resources/application.yml` — adds `spring.data.redis.{host,port}`

### Frontend (auth migration)
- `fe/src/app/lib/auth/native-auth.ts` — rewritten. No more `loadStoredTokenSet` / `saveTokenSet`. Just `passwordLogin` / `refreshTokens` / `revokeTokens`, all using `credentials: "include"`.
- `fe/src/app/hooks/use-auth.tsx` — rehydrate-on-mount unconditionally calls `/auth/refresh` instead of reading localStorage.
- `fe/src/app/lib/api/interceptors.ts` — 401 retry path no longer reads stored tokens; just calls refresh.

### Frontend (cart guest mode)
- `fe/src/app/hooks/use-cart.ts` — gains `isGuest` flag. localStorage-backed cart at `vnshop:guest-cart`. One-shot replay effect runs on first authenticated render.

### Test additions
- `fe/e2e/guest-cart.spec.ts` — 2 anonymous-cart scenarios (the merge-on-login Playwright equivalent was tried and dropped; vitest covers it deterministically).
- `infra/scripts/e2e-day.mjs` — three new sections: `coupon`, `admin_seller`, `saga`.

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `4bfb7bc1`. Working tree clean.
2. **Inspect the diff** — 17 commits since `1986222a`. None pushed (local-only per usual project pattern).
3. **Run the gates** (stack must be up + seeded):
   ```bash
   docker compose --profile apps up -d
   bash infra/scripts/setup-keycloak-admin-client.sh
   node infra/scripts/seed-demo.mjs
   node infra/scripts/e2e-day.mjs                       # 52/52
   cd fe && npx playwright test                         # 19/19
   cd fe && npm run typecheck && npm run lint && npm run test -- --run && npm run build
   ```
4. **Pick from the deferred list above.** Top-leverage items remaining:
   - **B9 (live shipping rate quote)** — flagged three handovers ago.
   - **B11 (messaging WebSocket E2E)** — highest-risk untested path.
   - **Notifications inbox** — multi-day, but visible UX win.

## Test users (Keycloak realm `vnshop`, all password `test`) — unchanged

- `buyer1` — BUYER role
- `seller1` — SELLER role (also has BUYER, owns the demo product, has a SellerProfile row after the admin_seller E2E section runs once)
- `admin1` — ADMIN role (also has BUYER)

## Final tally

- **Started this session:** API E2E 35/35, Playwright 17/17 at HEAD `1986222a`.
- **Ended:** API E2E 52/52 (+17 — coupon, admin_seller, saga), Playwright 19/19 (+2 guest-cart).
- **Commits:** 17, all on `main`, none pushed.
- **Diff vs `1986222a`:** ~+1100 / -250 across 50 files.
- **Bugs found + fixed:** 3 (coupon-service Redis binding, returns audit columns, sellers Playwright strict-mode).
- **Production characteristics added:** httpOnly cookie auth, cart guest mode, V18 returns table fix, coupon Redis wiring.
- **Deferred items closed:** 5 (B3, S2, B7, saga compensation, C1+C2).
