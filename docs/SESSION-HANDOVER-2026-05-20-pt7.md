# Session handover — 2026-05-20 (pt7: address bug, buyer profile, UX sweep, infra fixes)

**Last commit (HEAD):** `7ae1f288` (`fix: seller dashboard, finance, recommendations 500s + UX screenshot sweep`)
**Commits this block:** 3 since pt6 HEAD `4efcbee6` (5 net new since pt5, 10 unpushed on `main`).
**Gates:** payment-service `66/66`. user-service `116/116`. FE typecheck `0 errors`. FE Playwright UX sweep `15/15 + 1 admin skip` (no admin user seeded).

This block started from a single failing screen (`Profile → Lưu địa chỉ` returning "street is required" with all fields filled) and snowballed into a full FE UX sweep that surfaced four production-affecting bugs. None of them had unit-test coverage; all of them survived the existing 66/66 + 116/116 + e2e-day gates.

## Commits this block (chronological)

| # | Commit | What |
|---|---|---|
| 1 | `445a84ea` | fix(fe): align Address shape with BE — line1 → street, validate district |
| 2 | `6709aab5` | fix(user): always materialise buyer profile on register, tolerate null phone |
| 3 | `7ae1f288` | fix: seller dashboard, finance, recommendations 500s + UX screenshot sweep |

## TL;DR

Five bugs fixed today, all surfaced by **driving the actual UI** — no amount of unit testing would have caught them:

1. **FE Address schema mismatch.** `Profile → Địa chỉ → Lưu địa chỉ` returned "street is required" with every field filled. FE was sending `{line1, line2, ward, district, city, province, country, phone}`, BE expected `{street, ward, district, city, isDefault}`. Jackson silently mapped `line1 → null → IllegalArgumentException("street is required")`. Renamed schema + 5 consumers + i18n + regression test.
2. **Fresh-register buyer profile gap.** `AuthController.register()` only invoked `RegisterBuyerUseCase` when a phone was supplied AND parsed as `+84…` E.164. FE form treats phone as optional, so most fresh self-registrations never materialised a buyer profile row → next `POST /users/me/addresses` 400'd with "buyer profile not found". Always register; let phone be null.
3. **Gateway routing — `/sellers/me/revenue` + `/sellers/me/finance/**`.** Caught by the broad `/sellers/**` catch-all to user-service, which has no such endpoints, so seller dashboard tiles + wallet card all surfaced "No static resource" 500s. Endpoints live on order-service (analytics) + seller-finance-service (wallet/payouts). Added explicit routes ahead of the catch-all.
4. **`seller_wallets` audit column drift.** `SellerWalletJpaEntity extends BaseJpaEntity` (which maps `created_at`/`updated_at`), but V2 migration only declared those columns on `payouts`. Every wallet read failed Hibernate strict-validation with "ERROR: column swje1_0.created_at does not exist". V4 migration backfills.
5. **recommendations-service refused to start.** Spring Boot 4 wasn't materialising an `ObjectMapper` even with `spring-boot-starter-webmvc` on classpath, so `OrderEventListener` `UnsatisfiedDependencyException`'d at boot. FE product detail page's recommendation widgets surfaced 503s on every render. Explicit `@Bean ObjectMapper` guarded by `@ConditionalOnMissingBean` (no-op if upstream is fixed).

## What shipped

### `fix(fe): align Address shape with BE — line1 → street`
- `fe/src/app/types/api/shared.ts` — `addressSchema` is now a true mirror of BE `AddressRequest`. Dropped `line1, line2, province, country` (BE never reads or returns them). Kept `phone` as a comment-marked FE-only field; BE record doesn't persist it.
- `fe/src/app/pages/ProfilePage.tsx` — `EMPTY_ADDRESS`, `formatAddressLine`, form field key, validation guard all rebased on `street`. Validation now requires `street && district && city` (BE's three non-blank constraints), not just `street + city`.
- `fe/src/app/pages/{OrdersPage,checkout/CheckoutPage,checkout/format}.tsx` — read `.street`.
- `fe/src/app/lib/api/endpoints/users.test.ts` — fixtures + assertions rebased on the new shape; locks in the contract at vitest time.
- `fe/src/app/lib/i18n/{en,vi}.json` — rename `profile.addresses.fields.line1 → .street`; broaden `validateMissing` copy.
- `fe/src/app/pages/ProfilePage.tsx` (a11y bonus) — added `htmlFor`/`id` pairs to address inputs. Screen readers couldn't associate label/input; Playwright's `getByLabel` couldn't find them either.
- `fe/.dockerignore` — exclude `e2e/`, `playwright-report/`, `test-results/`, `playwright.config.ts` from the FE Docker build context. Some Playwright specs were tripping BuildKit's file-walker on Windows hosts ("invalid file request"). They aren't part of the runtime nginx image anyway.
- `fe/e2e/profile-address.spec.ts` — registers a fresh buyer, fills the address form, asserts `POST /users/me/addresses` returns 200. The schema mismatch is a 400 ("street is required"), so 200 alone proves the FE is shipping the BE-shaped payload.

### `fix(user): always materialise buyer profile on register`
- `services/user-service/.../AuthController.java` — drop the phone-conditional. Always call `registerBuyerUseCase`. Bad phone formats degrade to a logged warning and deferred upsert.
- `services/user-service/.../RegisterBuyerUseCase.java` — only construct a `PhoneNumber` when the command has a non-blank phone. The domain `BuyerProfile` + JPA entity + response DTO already null-check phone, so storing null is safe.
- `116/116` mvn test green.

### `fix: seller dashboard, finance, recommendations 500s + UX screenshot sweep`
- `services/api-gateway/.../RouteConfig.java` — added two routes ahead of `/sellers/**` catch-all: `seller-analytics` → order-service for `/sellers/me/revenue` + `/sellers/me/analytics/**`, `seller-finance-me` → seller-finance-service for `/sellers/me/finance/**`.
- `services/seller-finance-service/.../db/migration/V4__seller_wallets_audit_columns.sql` — `ADD COLUMN IF NOT EXISTS created_at, updated_at` to `seller_wallets`, backfill, then `SET NOT NULL`. Mirrors V3 for payouts.
- `services/recommendations-service/.../config/JacksonConfig.java` — `@Bean ObjectMapper` with `@ConditionalOnMissingBean` so this is a no-op if the upstream auto-config is ever restored. Without this, the recommendations-service container won't start.
- `services/recommendations-service/.dockerignore` — exclude `target/` so the build context isn't a few hundred MB.
- `fe/e2e/ux-sweep.spec.ts` — new spec that screenshots every reachable FE page (27 pages: 11 public, 8 buyer, 1 checkout sub-flow, 6 seller, plus admin gated on a seeded admin user). Each capture writes `{slug}.png` + `{slug}.console.json` (errors / page errors / warnings) to `test-results/ux-sweep/`. Sweep result: **92 console errors → 27** after the three BE fixes; the 27 remaining are exactly one `/auth/refresh` 401 per page, which is the FE auth bootstrap probing for an existing session — expected and benign.

## Sweep methodology + how to drive it

```bash
cd fe && npx playwright test e2e/ux-sweep.spec.ts --project=chromium
ls test-results/ux-sweep/    # *.png + *.console.json per page
```

Each scenario is its own `test()` so a single page crashing the React tree doesn't blank-screen everything that comes after. Console errors are non-fatal — recorded to JSON for offline triage, not asserted on. The screenshot is the strongest signal: if a page crashes its error boundary the PNG shows the boundary copy.

The triage workflow that found four bugs in 30 minutes: file-size pattern revealed which pages bounced (identical bytes = same fallback), `node`-script over the JSON dumps grouped errors by status code, log-greps on the relevant containers pinpointed the root cause for each cluster.

## Operational gotchas (durable rules — additions to pt5/pt6)

The pt5 + pt6 lists still apply. New rules learned this block:

12. **FE↔BE record drift surfaces as cryptic field-name errors.** When a Vietnamese-or-mixed-language form returns "X is required" with X visibly populated, suspect schema mismatch on the wire. Jackson maps unknown JSON keys to `null` silently and the BE domain validator wins. Always check the FE's payload against the BE record before assuming the validator is wrong.
13. **Vite inlines `VITE_*` at *build* time.** (See pt6 #12 — kept for emphasis: this hits *every* container-built FE.)
14. **Three-layer agreement for FE build args.** `.env` → `docker-compose.yml frontend.build.args` → `fe/Dockerfile ARG`+`ENV`. Missing any layer → silently inlined as `undefined` → DCE-eliminated branches → `if (!FLAG)` placeholder UI ships in production. Verify by grepping the rebuilt lazy-loaded chunk for the publishable creds.
15. **Address inputs need `htmlFor`/`id` pairs.** Not just for screen readers — Playwright's `getByLabel` is the most stable selector and it walks the actual `for=` association. Inputs without `id` become unreachable in tests.
16. **Spring Boot 4 + custom `@Configuration` classes can suppress Jackson auto-config.** When `OrderEventListener` (or any `@Service` with `ObjectMapper` injection) `UnsatisfiedDependency`'s at startup with `spring-boot-starter-webmvc` on the classpath, define an explicit `@Bean ObjectMapper` guarded by `@ConditionalOnMissingBean`. The recommendations-service hit this; it's a known SB4 quirk that affects `OrderEventListener`-style classes that aren't on a request-scoped path.
17. **Gateway catch-all ordering matters.** `/sellers/**` reaches user-service, but `/sellers/me/{revenue,finance/**}` lives on other services. Per-segment routes MUST precede the catch-all in `RouteConfig.gatewayRoutes()`. Smell test: if a `@RestController` with a stable `@RequestMapping` returns "No static resource", the gateway is sending the request to the wrong service.
18. **`BaseJpaEntity` migrations are per-table.** When a service adds a JPA superclass with `created_at` + `updated_at`, every concrete `@Entity` that extends it needs the columns in its own migration. Hibernate's strict schema validation surfaces drift as 500s on every read of the affected entity.
19. **CRLF line endings in `services/*/Dockerfile` and source files break BuildKit on Windows hosts.** Surfaces as cryptic `failed to solve: invalid file request {filename}` with no useful context. `tr -d '\r'` over the build context fixes it. If you see this again, check `git config core.autocrlf` and add a `.gitattributes` `* text=auto eol=lf` if the team is mixing Windows + WSL.
20. **API exception → 500 vs 503 vs 401 isn't always meaningful in the FE console.** A page that returns 1×401 (auth bootstrap) is NOT broken; multiple 500s mean a real bug. Triage by per-page error count distribution before drilling into individual messages.

## Test inventory after this block

- Playwright e2e: `profile-address.spec.ts` (1 test) + `ux-sweep.spec.ts` (16 tests, 1 admin-gated skip).
- Vitest: `users.test.ts` regression suite covers the address schema contract.
- BE: payment-service 66/66, user-service 116/116. seller-finance + recommendations migrations + Jackson config covered by start-up smoke (services boot to `actuator/health UP`).

**Note on legacy Playwright specs.** `e2e/{smoke,buyer-happy-path,guest-cart,authenticated-routes,role-routes,sellers,payment-multi-method,network-diagnostic}.spec.ts` are silently excluded from Playwright 1.60's discovery despite identical naming + matching default `testMatch`. Tracked as a follow-up — the new sweep spec already covers more pages than the legacy specs combined, so the regression risk is contained but the specs themselves are dead weight until the discovery issue is debugged.

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `7ae1f288`. 10 commits unpushed on local `main` (pt6's 7 + today's 3). Working tree is clean except for `.gitignore` + `opencode.jsonc` (carry-over editor config, ignore).
2. **Run the gates** (stack must be up + seeded):
   ```bash
   cd services/payment-service && ./mvnw test     # 66/66
   cd services/user-service    && ./mvnw test     # 116/116
   cd fe && npx tsc --noEmit                       # 0
   cd fe && npx playwright test e2e/profile-address.spec.ts e2e/ux-sweep.spec.ts --project=chromium  # 16/16 + 1 skip
   STRIPE_ENABLED=true PAYPAL_ENABLED=true VIETQR_ENABLED=true \
     node infra/scripts/e2e-day.mjs               # 67/67
   ```
3. **Drive the sweep + triage.** Re-running the sweep is the cheapest way to surface anything new that broke — every PR that touches the FE or a buyer/seller surface should re-run it. Output is in `fe/test-results/ux-sweep/`.
4. **Open a PR for the 10 unpushed commits**, or push to `main` if that's the convention. I deliberately did not push without confirmation.

## What's still missing (deferred — pt7 → pt8)

- **PayPal capture round-trip.** Smart Buttons render on the success step (FE creds inlined post-pt6), the BE OAuth + create + capture path is unit-tested, but the live FE → sandbox PayPal popup → `/payment/paypal/capture` round-trip has never been driven by hand. Last unproven payment path.
- **Admin user seeding.** UX sweep skips the admin sub-pages (`/admin/dashboard|sellers|coupons|payouts|disputes|reviews`) because no admin user is seeded into the realm import. Either add one to `infra/keycloak-realm.json` or document that admin coverage requires manual user creation.
- **Legacy Playwright spec discovery.** Eight specs are silently excluded; not blocking but worth ten minutes to debug.
- **MoMo callback migration onto `PaymentPromotionService`.** Smallest deferred backend item from pt6's list. Currently still on its own dedup path; works correctly, just not deduplicated through the unified service.
- **Notifications inbox.** notification-service consumes Kafka but no inbox endpoint or FE bell yet.
- **Real GHN/GHTK shipping rate adapter.** `LiveCarrierGateway` scaffolding exists; needs API key wiring + integration tests.

## Resume hint

Next session: **drive the PayPal capture round-trip in the browser** — it's the last unproven sandbox path and the FE creds are now baked into the bundle (commit `4efcbee6`). Then either seed an admin user to unblock the admin slice of the UX sweep, or migrate MoMo onto `PaymentPromotionService` (the smallest unfinished BE item). The UX sweep + console-triage pattern from this session is the new default for any future session that touches buyer or seller surfaces.
