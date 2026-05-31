# Persona Workday Playwright Suite — Design

**Date:** 2026-05-23
**Status:** Approved
**Owner:** dang232
**Related:** [`docs/UI-QA-COVERAGE.md`](../../UI-QA-COVERAGE.md), [`fe/e2e/`](../../../fe/e2e), [`fe/playwright.config.ts`](../../../fe/playwright.config.ts)

## Problem

The repo currently has 27 UI-driven Playwright specs / 76 scenarios that exercise individual surfaces (cart, checkout, admin tabs, etc.) in isolation, plus an API-only `day-simulation.spec.ts` that the user has flagged as "not real UI testing — lying to pass the test."

What is missing: end-to-end **workday journeys** per persona that chain those surfaces into realistic flows a real user would walk through, driven entirely through the browser, with auditable evidence per scenario (screenshots, video, trace, markdown report).

A workday spec catches a different class of regression than a single-surface spec: state continuity (cart → checkout → orders), navigation chains (login → role-redirect → tab → mutation), and cross-feature interactions (theme persistence across login, language carrying through checkout) only fail when steps run in sequence.

## Goal

Three new UI-driven Playwright spec files, one per persona, each producing a self-contained evidence folder under `fe/e2e/evidence/<persona>/`.

- `fe/e2e/workday-buyer.spec.ts`
- `fe/e2e/workday-seller.spec.ts`
- `fe/e2e/workday-admin.spec.ts`

## Non-Goals

- No new test "framework" abstraction. The evidence helper is a thin wrapper around `test.step()` + `page.screenshot()`, not a DSL.
- Not duplicating existing single-surface assertions. The 27 existing UI specs stay as guard rails; workday specs assert *flow*, not field-by-field correctness.
- No payment-gateway sandbox flows (Stripe / PayPal / VNPay redirect). Buyer pays via COD; gateway redirect handling is already covered by `payment-return-ui.spec.ts`.
- Not changing `playwright.config.ts` defaults. `workers: 1` stays; the suite is sequential by design.
- Not touching `day-simulation.spec.ts`. It stays as an API integration smoke; the workday specs supersede its UI claims.

## Scenarios

### Buyer workday (`fe/e2e/workday-buyer.spec.ts`)

A guest discovers the store, registers, shops, and manages an order. Single `test.describe.serial` block; each step builds on the previous.

| # | Step | What it proves |
|---:|---|---|
| 1 | Land on `/`, switch language EN→VI, verify nav copy changes | i18n works on cold-load home |
| 2 | Toggle dark mode, verify `<html class="dark">` flips | dark-mode toggle pre-auth |
| 3 | Search "phone" via header search, apply Price-low-to-high sort | search query + sort wiring |
| 4 | Open first product in result list | product detail mounts |
| 5 | Click "Add to cart" as guest → toast → click login redirect | guest add-to-cart guard |
| 6 | Register fresh buyer (`e2e_workday_<timestamp>@vnshop.local`) via `/register` | registration form happy path |
| 7 | Navigate back to product, add to cart | authed add-to-cart |
| 8 | Open `/cart`, verify VND price > 0, click `+` to qty=2, verify total updates | cart math + qty mutation |
| 9 | Toggle wishlist heart on product detail | wishlist mutation round-trip |
| 10 | Profile → Addresses tab → add new address | address mutation |
| 11 | Checkout → 4-step panel → select address → COD → place order | checkout submission |
| 12 | Orders tab → find new order → click Cancel → toast confirms | order cancel round-trip |
| 13 | Logout via header menu | session cleanup |

### Seller workday (`fe/e2e/workday-seller.spec.ts`)

Login as seeded `seller1`. Walks the seller console.

| # | Step | What it proves |
|---:|---|---|
| 1 | Login as `seller1` → redirect to `/seller` | role-routing |
| 2 | Dashboard tab: 4 KPI cards + Revenue + Orders 30-day headers render | seller analytics schema |
| 3 | Products tab: table chrome (heading + Add CTA + 4 columns) | seller products list |
| 4 | Orders tab: order queue mounts, no Zod leak | nested→flat adapter |
| 5 | Wallet tab: balance + history render; Withdraw disabled at 0 | wallet + payout schemas |
| 6 | Navigate to `/sellers/{seller1Id}` (own public storefront) | public seller view from seller account |
| 7 | Logout | session cleanup |

### Admin workday (`fe/e2e/workday-admin.spec.ts`)

Login as seeded `admin1`. Walks the admin console + does one mutation round-trip.

| # | Step | What it proves |
|---:|---|---|
| 1 | Login as `admin1` → redirect to `/admin` | role-routing |
| 2 | Dashboard tab loads, no error fallback | admin dashboard schema |
| 3 | Sellers tab: approval queue parses | sellers list schema |
| 4 | Coupons tab → Create FIXED coupon (`E2E-WORKDAY-<timestamp>`) → row appears in table | coupon CRUD round-trip |
| 5 | Same coupon → Deactivate → badge flips to Paused | coupon deactivate round-trip |
| 6 | Disputes tab parses | disputes schema |
| 7 | Payouts tab parses | payouts schema |
| 8 | Logout | session cleanup |

## Architecture

### Evidence layout per scenario

```
fe/e2e/evidence/<persona>/
├── screenshots/
│   ├── 01-home-landing.png
│   ├── 02-language-vi.png
│   ├── 03-dark-mode-on.png
│   └── ...                          # one per test.step
├── video.webm                       # gitignored
├── trace.zip                        # committed
└── REPORT.md                        # committed, generated
```

`<persona>` ∈ `{buyer, seller, admin}`.

### Evidence helper (`fe/e2e/_workday-evidence.ts`)

A small module (~60 lines) wrapping the Playwright primitives. Two exports:

```ts
// Wraps test.step: runs the step, takes a numbered screenshot,
// pushes a row into the report.
export async function step(
  page: Page,
  persona: Persona,
  title: string,
  fn: () => Promise<void>,
): Promise<void>;

// Called from afterAll: writes REPORT.md with embedded
// screenshots + step titles + pass/fail status.
export async function finalizeReport(persona: Persona): Promise<void>;
```

Implementation notes:
- Step counter is module-scoped, reset per persona via `resetCounter(persona)` called in `beforeAll`.
- Screenshot path: `fe/e2e/evidence/<persona>/screenshots/<NN>-<slug>.png` where `NN` is zero-padded and `slug` is the step title kebab-cased.
- `REPORT.md` is regenerated from scratch each run (idempotent), embedding screenshots via relative path: `![](screenshots/01-home-landing.png)`.
- On failure, the helper still writes the screenshot (catch + rethrow) so the evidence reflects the failed state.

### `playwright.config.ts` adjustments

The existing config sets `video: "retain-on-failure"`. Workday specs need video on every run. Two options:

**Chosen: per-spec override via `test.use({ video: "on", trace: "on" })`** at the top of each workday spec. This keeps the global default unchanged (other specs don't pay the cost) and self-documents the intent in the spec file.

Alternative rejected: a separate Playwright project. Would force a second `--project=workday` invocation and split the run line; not worth the indirection for three specs.

After each `test()`, the helper reads the `testInfo.outputDir` (where Playwright dropped video + trace), copies `video.webm` and `trace.zip` to `fe/e2e/evidence/<persona>/`. The Playwright auto-cleanup of `test-results/` then removes the duplicates.

### Persona setup

| Persona | Setup | Why |
|---|---|---|
| Buyer | Registers fresh via the form each run (`e2e_workday_<timestamp>@vnshop.local`) | Day-one registration is part of the buyer workday; deterministic across runs |
| Seller | Logs in as seeded `seller1` (realm-imported, password `test`) | Seller approval is a separate flow; seller1 has SELLER role baked into the realm |
| Admin | Logs in as seeded `admin1` (realm-imported, password `test`) | Admin role provisioning is out of scope; admin1 has ADMIN role in the realm |

### Test execution

Each spec is a single `test.describe.serial` containing one `test()`. Steps inside that test use `step(page, persona, title, fn)`.

Why one test, not many: workday means *continuity*. Splitting into separate `test()` blocks would require re-login + state recreation between each, defeating the point. The trace-viewer hierarchy from `test.step` gives us per-step granularity without per-test overhead.

## Data Flow

```
Test runner spawns chromium
  ↓
beforeAll: resetCounter(persona); ensure evidence dir
  ↓
test():
  step 1 → page action → page.screenshot() → markdown row
  step 2 → page action → page.screenshot() → markdown row
  ...
  step N → page action → page.screenshot() → markdown row
  ↓
afterEach (Playwright built-in): writes video.webm + trace.zip to testInfo.outputDir
  ↓
afterAll:
  - Copy video.webm + trace.zip from outputDir → evidence/<persona>/
  - finalizeReport(persona) → writes REPORT.md
```

## Error Handling

- **Step failure**: helper catches, takes a "failed" screenshot, writes the row with FAIL status, rethrows so the test fails normally.
- **Missing seeded user**: spec fails fast in `beforeAll` with a clear message pointing at `infra/keycloak/vnshop-realm.json` and `bash infra/scripts/setup-keycloak-admin-client.sh`.
- **Stack not running**: existing config's webServer auto-start is opt-in; if backend is down, Playwright's first navigation times out — same failure mode as the rest of the suite.
- **Async projection lag** (Kafka order_summary): cancel verification asserts only on toast + URL change, not on the row's status flipping in the same session. This convention is already established in `orders-cancel-ui.spec.ts`.

## Testing Approach

- **Self-test**: each workday spec passes against the live stack before commit. Evidence folder is inspected manually once to confirm screenshots are sensible.
- **Regression detection**: workday specs are part of the standard run-all command. A flake-prone step (e.g. waiting for a Kafka projection) gets the same treatment as the existing suite — assert on the synchronous user-visible signal, not the eventually-consistent backend state.
- **Run command added to `docs/UI-QA-COVERAGE.md`**:
  ```bash
  cd fe && npx playwright test \
    e2e/workday-buyer.spec.ts \
    e2e/workday-seller.spec.ts \
    e2e/workday-admin.spec.ts \
    --project=chromium --reporter=line
  ```

## Files Created / Modified

```
A  fe/e2e/_workday-evidence.ts                   # helper module
A  fe/e2e/workday-buyer.spec.ts
A  fe/e2e/workday-seller.spec.ts
A  fe/e2e/workday-admin.spec.ts
A  fe/e2e/evidence/buyer/REPORT.md               # generated (committed)
A  fe/e2e/evidence/buyer/screenshots/*.png       # generated (committed)
A  fe/e2e/evidence/buyer/trace.zip               # committed
A  fe/e2e/evidence/seller/...                    # same shape
A  fe/e2e/evidence/admin/...                     # same shape
M  fe/.gitignore                                 # ignore *.webm under evidence/
M  docs/UI-QA-COVERAGE.md                        # add workday section + run command
```

## Open Questions

None — confirmed with user 2026-05-23:

- Evidence artifacts: screenshots + REPORT.md + trace.zip committed; video.webm gitignored.
- Buyer registers fresh each run.
- COD-only checkout (no gateway sandbox).

## Risks

| Risk | Mitigation |
|---|---|
| Workday specs become slow (3 × ~1 min) and discourage local runs | Keep them off the default `npx playwright test` invocation in CI's fast lane; have a dedicated workday job |
| Evidence folder grows large in git over time | Screenshots regenerate each run (overwrite, no history bloat). trace.zip is ~100KB-1MB per scenario, acceptable. Video is gitignored. |
| Seeded `seller1` / `admin1` password rotates | Already a known coupling; same risk as the existing role-routes spec. If rotated, all role-gated specs break together — fix in one place |
| Buyer's fresh-register pollutes Keycloak with throwaway users | Already happens with `buyer-happy-path.spec.ts` and `day-simulation.spec.ts`. Out of scope for this design; if it becomes a problem, add a teardown step that calls the admin DELETE endpoint |
