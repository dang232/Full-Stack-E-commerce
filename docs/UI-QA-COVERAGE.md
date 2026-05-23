# UI Playwright QA Coverage

**Last updated:** 2026-05-23 (HEAD `c235b289`)

This index lists every UI-driven Playwright spec in `fe/e2e/`. Specs here drive the actual SPA through the browser — clicking buttons, sampling computed styles, asserting visible copy. They are NOT API-only integration tests dressed up as E2E (those are also useful, see `day-simulation.spec.ts`, but they don't catch UI regressions).

If a regression slips past these tests, write a new one. The pattern: seed state via API for speed, then drive the actual SPA for the assertion.

## Specs

| Spec | Scenarios | Locks in | What it proves through the browser |
|---|---:|---|---|
| `orders-cancel-ui.spec.ts` | 2 | `3c094804`, `pt28 order schema` | Pending order rows show item count (not permanent loading text); Cancel button click round-trips to BE + success toast |
| `cart-ui.spec.ts` | 3 | `b9af48b4`, `2ed309b9` | Cart shows real product names + non-zero VND prices (not UUIDs at 0₫); +/- stepper changes total; trash icon empties the cart |
| `profile-ui.spec.ts` | 2 | `pt28 user schema` | `/profile` loads without "Invalid input" fallback; Addresses tab renders empty-state for new buyers |
| `dark-mode-ui.spec.ts` | 2 | `pt28 dark mode sweep` | Click Tối/Dark → `<html class="dark">` lands + body bg changes; dark headings have brightness > 180 (not light-mode dark gray) |
| `i18n-switcher-ui.spec.ts` | 2 | `pt27 duplicate-home key` | Toggle between VI/EN — no `home.*` raw keys leak; switching changes user-visible nav copy |
| `search-product-ui.spec.ts` | 3 | `pt28 product schema audit` | `/search` renders past Suspense; `/product/{id}` shows H1 + Add-to-cart; guest add-to-cart surfaces login toast |
| `checkout-ui.spec.ts` | 3 | `pt28 calculateCheckout schema` | Empty cart shows empty-state CTA; cart-without-address shows profile prompt; cart+address shows 4-step panel |
| `wishlist-ui.spec.ts` | 2 | `pt28 wishlist schema` | Guest redirects to /login; logged-in buyer with wished product sees it |
| `seller-orders-ui.spec.ts` | 2 | `0a3c0f8a` | `/seller` mounts for seller1; Orders tab renders queue (proves nested→flat adapter works) |
| `admin-ui.spec.ts` | 5 | `pt28 admin schemas + coupon envelope` | Dashboard, Sellers, Coupons, Disputes, Payouts tabs all render past Suspense |

**Total UI scenarios: 26.**

Run them all:
```bash
cd fe && npx playwright test \
  e2e/orders-cancel-ui.spec.ts \
  e2e/cart-ui.spec.ts \
  e2e/profile-ui.spec.ts \
  e2e/dark-mode-ui.spec.ts \
  e2e/i18n-switcher-ui.spec.ts \
  e2e/search-product-ui.spec.ts \
  e2e/checkout-ui.spec.ts \
  e2e/wishlist-ui.spec.ts \
  e2e/seller-orders-ui.spec.ts \
  e2e/admin-ui.spec.ts \
  --project=chromium --reporter=line
```
Last green run: **26 / 26 in 32.2s** against the live stack.

## Conventions

**Setup via API, exercise via UI.** Registering a buyer + adding to cart through clicks is six clicks too many for what we're testing. The API setup is fast and reliable; the UI assertion is what catches regressions. The split is documented inline in every spec.

**Tabler icon class anchors for unlabeled buttons.** `+`, `-`, trash, etc. render as icon-only buttons in the cart. Match by `button:has(svg.tabler-icon-plus)` instead of guessing labels or relying on positional locators.

**Avoid coupling to async projection lag.** The `order_summary` read-model updates via Kafka after a write commits. Don't write tests that reload-poll for the row's status to flip in the same session — it races against the consumer. The cancel test verifies the BE round-trip via the success toast and stops there; the cancelled-tab assertion is documented as out-of-scope with rationale.

**No global error fallback.** Every page-level test asserts `getByText(/Có lỗi xảy ra|Something went wrong/i)).toHaveCount(0)` and `getByText(/Invalid input/i)).toHaveCount(0)`. This is the canonical "the page actually rendered" check post-pt28 schema fixes.

**Localized regex, anchored.** Match VI and EN copy with `/^(English label|Vietnamese label)$/i`. Anchor with `^...$` when the regex could otherwise match a substring on a sibling element (the "Cancelled" tab vs. the "Cancel" button caught me once).

## What's still NOT covered

These have FE schemas but no UI spec yet — write one when you next touch the surface:

- Login form happy path (driven by `buyer-happy-path.spec.ts` already, partial)
- Register form happy path (also in `buyer-happy-path.spec.ts`)
- Password-reset request flow
- Messages page
- Notifications bell dropdown
- Design system page
- Flash sale strip on home
- Seller dashboard analytics charts
- Seller wallet + payout-request modal
- Admin dispute resolve modal

The first three are partially covered by the existing `buyer-happy-path.spec.ts`; the rest are new ground.

## Anti-pattern: API-only "UI tests"

`day-simulation.spec.ts` is the canonical day-in-the-life integration suite — it makes raw HTTP calls to test BE flows end-to-end. It's valuable, but it does not catch UI regressions. The cart "raw UUID + 0₫" bug was visible to humans for weeks while day-simulation stayed green, because the API-only tests asserted on `cart.items[0].productId` (which always equals the input) without ever checking what the SPA rendered.

If the bug requires a human eye to catch, write a spec that uses a browser.
