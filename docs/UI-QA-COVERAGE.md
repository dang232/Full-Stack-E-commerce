# UI Playwright QA Coverage

**Last updated:** 2026-05-23 (HEAD `2670ee19`)

This index lists every UI-driven Playwright spec in `fe/e2e/`. Specs here drive the actual SPA through the browser — clicking buttons, sampling computed styles, asserting visible copy. They are NOT API-only integration tests dressed up as E2E (those are also useful, see `day-simulation.spec.ts`, but they don't catch UI regressions).

If a regression slips past these tests, write a new one. The pattern: seed state via API for speed, then drive the actual SPA for the assertion.

## Specs

| Spec | Scenarios | Locks in | What it proves through the browser |
|---|---:|---|---|
| `home-page-ui.spec.ts` | 4 | pt27 i18n + Tabler migration | Home mounts past Suspense; hero H1 has localized copy (no `home.hero.*` raw keys); section headers all render; footer Design System link has Tabler IconPalette svg |
| `flash-sale-ui.spec.ts` | 2 | activeFlashSaleCampaignSchema + IconBolt migration | Flash sale section header renders with Tabler IconBolt; body renders one of countdown/empty/product-strip |
| `navbar-ui.spec.ts` | 3 | global navbar across routes | Logo from /search → /; nav links navigate to /search variants; mobile drawer opens at 380px viewport |
| `route-smoke-ui.spec.ts` | 6 | route config + cross-page schema breadth | / · /search · /login · /register · /password-reset · /design-system all mount past Suspense, no Zod 'Invalid input' leaks |
| `auth-forms-ui.spec.ts` | 6 | Login/Register/Reset forms | Mismatched + short password rejected inline; happy-path register lands on /; bad login shows inline error; reset disabled-when-empty + happy path |
| `i18n-switcher-ui.spec.ts` | 2 | pt27 duplicate-home key | No `home.*` raw keys ever leak; switching VI/EN changes nav copy |
| `theme-i18n-persistence-ui.spec.ts` | 2 | localStorage + module-state durability | EN survives reload via i18nextLng cache; dark mode does NOT persist (documented behaviour) |
| `dark-mode-ui.spec.ts` | 2 | pt28 dark mode sweep | Toggle flips `<html class="dark">` + body bg changes; dark headings have brightness > 180 |
| `search-product-ui.spec.ts` | 3 | pt28 product schema | Search renders past Suspense; product detail H1 + Add-to-cart visible; guest add surfaces login toast |
| `search-filters-ui.spec.ts` | 3 | search filters | Query submit updates header; sort radio activates; Clear-all only renders with active filter |
| `cart-ui.spec.ts` | 3 | b9af48b4 + 2ed309b9 | Cart shows real names + non-zero VND prices; +/- changes total; trash empties cart |
| `checkout-ui.spec.ts` | 3 | pt28 calculateCheckout schema | Empty-cart CTA; cart-no-address profile prompt; cart+address shows 4-step panel |
| `orders-cancel-ui.spec.ts` | 2 | 3c094804, pt28 order schema | Item-count line (not permanent loading); Cancel click round-trips to BE + toast |
| `orders-tabs-ui.spec.ts` | 2 | order status derive + tab filter | All/Pending tabs both show pending order; Delivered tab shows empty state |
| `wishlist-toggle-ui.spec.ts` | 2 | wishlist heart toggle round-trip | First click adds + toast; second click removes + toast (after first dismisses) |
| `wishlist-ui.spec.ts` | 2 | pt28 wishlist schema | Guest redirects to /login; buyer with wished product sees it |
| `profile-ui.spec.ts` | 2 | pt28 user schema | /profile loads without "Invalid input"; Addresses tab empty-state |
| `profile-addresses-ui.spec.ts` | 4 | address mutation round-trips | Add through form → row appears; empty submit blocks; set-default flips badge; trash removes row |
| `messages-notifications-ui.spec.ts` | 3 | pt28 messages + bell | Guest /messages → /login; authed thread-list mounts; bell click opens panel |
| `payment-return-ui.spec.ts` | 2 | gateway redirect handling | VNPay/MoMo /payment/return without orderId shows error state, no global crash |
| `sellers-public-ui.spec.ts` | 3 | publicSellerSchema | Home seller showcase header renders; /sellers/{id} mounts; bogus id surfaces BE 404, no Zod leak |
| `seller-orders-ui.spec.ts` | 2 | 0a3c0f8a | /seller mounts; Orders tab queue parses (nested→flat adapter works) |
| `seller-wallet-ui.spec.ts` | 2 | pt28 wallet + payout schemas | Wallet tab renders balance + history; Withdraw correctly disabled at 0 |
| `seller-dashboard-ui.spec.ts` | 2 | pt28 seller-analytics | 4 KPI cards render; Revenue + Orders 30-day section headers parse |
| `seller-products-ui.spec.ts` | 1 | seller products list | Products tab table chrome renders (heading + Add CTA + 4 columns) |
| `admin-ui.spec.ts` | 5 | pt28 admin schemas + coupon envelope | Dashboard, Sellers, Coupons, Disputes, Payouts tabs all parse |
| `admin-coupon-crud-ui.spec.ts` | 3 | 2670ee19 (coupon wire-shape fix) | Empty code blocks with toast; FIXED submit round-trips + row appears; deactivate flips badge to Paused |

**Total UI scenarios: 76** across 27 spec files.

Last green run: **76 / 76 in 1.6 min** against the live stack.

Run them all:
```bash
cd fe && npx playwright test \
  e2e/home-page-ui.spec.ts \
  e2e/flash-sale-ui.spec.ts \
  e2e/route-smoke-ui.spec.ts \
  e2e/auth-forms-ui.spec.ts \
  e2e/i18n-switcher-ui.spec.ts \
  e2e/dark-mode-ui.spec.ts \
  e2e/search-product-ui.spec.ts \
  e2e/cart-ui.spec.ts \
  e2e/checkout-ui.spec.ts \
  e2e/orders-cancel-ui.spec.ts \
  e2e/profile-ui.spec.ts \
  e2e/profile-addresses-ui.spec.ts \
  e2e/wishlist-ui.spec.ts \
  e2e/messages-notifications-ui.spec.ts \
  e2e/payment-return-ui.spec.ts \
  e2e/sellers-public-ui.spec.ts \
  e2e/seller-orders-ui.spec.ts \
  e2e/seller-wallet-ui.spec.ts \
  e2e/seller-dashboard-ui.spec.ts \
  e2e/seller-products-ui.spec.ts \
  e2e/admin-ui.spec.ts \
  e2e/admin-coupon-crud-ui.spec.ts \
  --project=chromium --reporter=line
```

## Bugs caught while writing these specs

The QA pass found and fixed real defects. Documenting them here so the next time
someone wonders "what does it really test," they can see the receipts:

- **`3c094804`** — Pending orders showed permanent "Loading product details..." copy.
  Caught while writing `orders-cancel-ui.spec.ts`; fixed by surfacing the list
  endpoint's `itemCount` instead of the missing `items[]`.
- **`b9af48b4`** + cart-service env wiring — Cart rendered raw productId UUIDs at
  0₫ because cart-service's offline-mode adapter branch fired in production
  (missing `PRODUCT_SERVICE_URL` env var). Caught visually pre-spec; the
  `cart-ui.spec.ts` "non-zero VND price" assertion locks it in.
- **`c235b289`** — Admin /admin/coupons GET returned a bare array, FE envelope
  interceptor rejected it as "Invalid input." Caught while writing the
  `admin-ui.spec.ts` Coupons-tab scenario; fixed by wrapping every
  CouponController endpoint in `ApiResponse.ok(...)` to match the platform-wide
  envelope.
- **`2670ee19`** — Admin couponDialog submit failed because (a) it never sent
  `maxUses`/`validUntil` (BE primitives, Jackson rejected null) and (b) it sent
  `"PERCENT"` while BE enum is `PERCENTAGE`. Both caught by writing
  `admin-coupon-crud-ui.spec.ts` and watching the create button silently fail.
  Fixed the FE wire mapper at the endpoint adapter layer.

## Conventions

**Setup via API, exercise via UI.** Registering a buyer + adding to cart through clicks is six clicks too many for what we're testing. The API setup is fast and reliable; the UI assertion is what catches regressions. The split is documented inline in every spec.

**Tabler icon class anchors for unlabeled buttons.** `+`, `-`, trash, etc. render as icon-only buttons in the cart. Match by `button:has(svg.tabler-icon-plus)` instead of guessing labels or relying on positional locators.

**Avoid coupling to async projection lag.** The `order_summary` read-model updates via Kafka after a write commits. Don't write tests that reload-poll for the row's status to flip in the same session — it races against the consumer. The cancel test verifies the BE round-trip via the success toast and stops there; the cancelled-tab assertion is documented as out-of-scope with rationale.

**No global error fallback.** Every page-level test asserts `getByText(/Có lỗi xảy ra|Something went wrong/i)).toHaveCount(0)` and `getByText(/Invalid input/i)).toHaveCount(0)`. This is the canonical "the page actually rendered" check post-pt28 schema fixes.

**Localized regex, anchored.** Match VI and EN copy with `/^(English label|Vietnamese label)$/i`. Anchor with `^...$` when the regex could otherwise match a substring on a sibling element (the "Cancelled" tab vs. the "Cancel" button caught me once).

**Route guards redirect, they don't render empty states.** `/wishlist` and `/messages` redirect guests to `/login`. Assert `URL pathname matches /^\/login/`, not the empty-state copy.

## What's still NOT covered

These have FE schemas but no UI spec yet — write one when you next touch the surface:

- Design system page (intentional — it IS the token fixture, not a target for regression tests)
- Order detail page tracking modal + return-request modal (only list cancel is covered)
- Profile address remove + set-default actions (add is covered)
- Seller settings tab (currently a "coming soon" banner; nothing to test)
- Seller reviews tab (also "coming soon")
- Admin dispute resolve modal
- Admin coupon update + deactivate (create is covered)
- Stripe / PayPal / VietQR payment forms (mocked sandbox flows)
- WebSocket message delivery in /messages (presence + new-thread events)

## Anti-pattern: API-only "UI tests"

`day-simulation.spec.ts` is the canonical day-in-the-life integration suite — it makes raw HTTP calls to test BE flows end-to-end. It's valuable, but it does not catch UI regressions. The cart "raw UUID + 0₫" bug was visible to humans for weeks while day-simulation stayed green, because the API-only tests asserted on `cart.items[0].productId` (which always equals the input) without ever checking what the SPA rendered.

If the bug requires a human eye to catch, write a spec that uses a browser.
