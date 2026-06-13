# FE Audit Closure — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close all 108 remaining UI/UX audit items from the 2026-05-31 spec.

**Architecture:** 5 parallel workstreams matching spec categories. Each workstream is independent — no cross-dependencies. After all streams complete, run full verification suite (unit + integration + e2e + build).

**Tech Stack:** React 18 + TypeScript + Vite, React Query, Zustand, React Hook Form + Zod, Sonner, Tailwind CSS, Vitest, Playwright

**Already Done:** C-5 (cart stock cap), U-7 (messages UUID → username), U-9 (address key identity)

---

## Workstream Overview

| # | Category | Items | Parallelizable | Key Files |
|---|----------|-------|----------------|-----------|
| WS-1 | Critical Bugs | 15 | Yes (by page/feature) | CheckoutPage, ProductPage, CartPage, SearchPage, HomePage, LoginPage, client.ts, use-cart.ts, SellerProducts, SellerOrders, OrdersPage, vnshop-context |
| WS-2 | State & Data | 8 | Yes | client.ts, use-cart.ts, vnshop-context, CheckoutPage, MessagesPage, error-boundary |
| WS-3 | Dead Buttons | 24 | Yes (all independent) | Root.tsx, ProductPage, HomePage, CartPage, CheckoutPage, LoginPage, ProfilePage, SellerProducts, WishlistPage, AdminDashboard |
| WS-4 | UX Anti-Patterns | 8 | Yes | CartPage, CheckoutPage, SellerOrders, MessagesPage, SearchPage, OrdersPage |
| WS-5 | Accessibility | 38 | Yes (by WCAG criterion) | All pages + components |

---

## WS-1: Critical Bugs (15 items)

### Task 1.1: Payment failure handling (CheckoutPage.tsx)
**Files:** Modify: `fe/src/app/pages/checkout/CheckoutPage.tsx`
**Spec item:** #1 — Payment failure falls through to success

- [ ] Write failing test: mock payment API returning failure status, assert page shows error state (not success redirect)
- [ ] Fix: after payment response, check `response.status === 'PAID'` before navigating to success. On failure → set error state with retry button
- [ ] Run tests, commit

### Task 1.2: Double-click guard on Place Order (CheckoutPage.tsx)
**Spec item:** #2 — No double-click guard

- [ ] Write failing test: simulate rapid double-click on "Place Order", assert API called exactly once
- [ ] Fix: add `isSubmitting` ref guard + disable button while `mutation.isPending`
- [ ] Run tests, commit

### Task 1.3: Variant not passed to addToCart (ProductPage.tsx)
**Spec item:** #3 — Selected variant not in payload

- [ ] Write failing test: select color/size variant, add to cart, assert payload includes variant
- [ ] Fix: thread selected color/size state into addToCart call
- [ ] Run tests, commit

### Task 1.4: Address step allows undefined (CheckoutPage.tsx)
**Spec item:** #4 — Can proceed without address

- [ ] Write failing test: attempt "Next" with no address selected, assert step doesn't advance
- [ ] Fix: disable "Next" when `selectedAddressIndex === null`, validate before advancing
- [ ] Run tests, commit

### Task 1.5: Guest-to-server cart merge (use-cart.ts)
**Spec item:** #6 — Not idempotent

- [ ] Write failing test: call merge twice with same items, assert no duplicates
- [ ] Fix: idempotency key per merge, deduplicate by `productId+variantId`
- [ ] Run tests, commit

### Task 1.6: clearFilters preserves search query (SearchPage.tsx)
**Spec item:** #7 — Wipes search query

- [ ] Write failing test: set query + filters, call clearFilters, assert query preserved
- [ ] Fix: reset filter state only, preserve `q` param
- [ ] Run tests, commit

### Task 1.7: Filters synced to URL (SearchPage.tsx)
**Spec item:** #8 — Not synced to URL

- [ ] Write failing test: set filters, assert URL params match; reload page, assert filters restored
- [ ] Fix: sync filters to `useSearchParams`, restore on mount
- [ ] Run tests, commit

### Task 1.8: Category tabs from API (HomePage.tsx)
**Spec item:** #9 — Hardcoded IDs

- [ ] Write failing test: mock categories API, assert tabs render from response (not hardcoded)
- [ ] Fix: fetch from API, match by slug
- [ ] Run tests, commit

### Task 1.9: Navigate after auth state (LoginPage.tsx)
**Spec item:** #10 — navigate() before auth propagates

- [ ] Write failing test: submit login, assert navigate only called after auth state is set
- [ ] Fix: await Zustand auth state update (or use mutation onSuccess callback) before navigate
- [ ] Run tests, commit

### Task 1.10: Token refresh race (client.ts)
**Spec item:** #11 — Concurrent 401s cause multiple refreshes

- [ ] Write failing test: trigger 3 concurrent 401s, assert only 1 refresh call made
- [ ] Fix: queue retries behind single refresh promise (mutex pattern — already partially exists via BroadcastChannel, verify and harden)
- [ ] Run tests, commit

### Task 1.11: Request timeout (client.ts)
**Spec item:** #12 — No timeout

- [ ] Write failing test: mock slow response (>30s), assert request aborted
- [ ] Fix: `AbortController` with 30s default timeout on all requests
- [ ] Run tests, commit

### Task 1.12: Seller products filter by sellerId (SellerProducts.tsx)
**Spec item:** #13 — Fetches ALL products

- [ ] Write failing test: render as seller, assert query includes sellerId filter
- [ ] Fix: pass `sellerId` from auth context to query params
- [ ] Run tests, commit

### Task 1.13: Seller products pagination (SellerProducts.tsx)
**Spec item:** #14 — Hard cap 50, no pagination

- [ ] Write failing test: mock 60 products, assert "Load more" rendered and works
- [ ] Fix: add offset pagination with "Load more" button using React Query infinite queries
- [ ] Run tests, commit

### Task 1.14: Operator precedence in status filter (SellerOrders.tsx)
**Spec item:** #15 — Operator precedence bug

- [ ] Write failing test: filter by specific status combo, assert correct results
- [ ] Fix: add explicit parentheses to filter logic
- [ ] Run tests, commit

### Task 1.15: Suspense boundary for useSuspenseQuery (OrdersPage.tsx)
**Spec item:** #16 — No Suspense boundary

- [ ] Write failing test: render OrdersPage with slow query, assert loading skeleton shown (not crash)
- [ ] Fix: wrap with `<Suspense>` + `<ErrorBoundary>`
- [ ] Run tests, commit

### Task 1.16: toggleWishlist auth guard (vnshop-context.tsx)
**Spec item:** #17 — No auth check

- [ ] Write failing test: call toggleWishlist as guest, assert toast + no API call
- [ ] Fix: check auth, toast + redirect if unauthenticated
- [ ] Run tests, commit

### Task 1.17: ProductsSection loading/error states (HomePage.tsx)
**Spec item:** #18 — Ignores loading/error

- [ ] Write failing test: mock loading state, assert skeleton shown; mock error, assert fallback shown
- [ ] Fix: add loading skeleton + error fallback
- [ ] Run tests, commit

---

## WS-2: State & Data (8 items)

### Task 2.1: Token refresh BroadcastChannel mutex (client.ts)
**Spec item:** S-1 — Race across tabs

- [ ] Write test: simulate cross-tab refresh scenario, assert single refresh
- [ ] Fix: verify existing BroadcastChannel impl covers multi-tab mutex (may already be done — audit and harden)
- [ ] Run tests, commit

### Task 2.2: Gate cart mutations behind isSuccess (use-cart.ts)
**Spec item:** S-2 — Optimistic update during load

- [ ] Write test: attempt mutation while cart query loading, assert mutation deferred
- [ ] Fix: gate mutations behind cart query `isSuccess`
- [ ] Run tests, commit

### Task 2.3: refetchOnWindowFocus for cart (use-cart.ts)
**Spec item:** S-3 — staleTime=30s, no refocus refetch

- [ ] Fix: add `refetchOnWindowFocus: true` to cart query options
- [ ] Verify existing test still passes, commit

### Task 2.4: Dark mode persistence (vnshop-context.tsx)
**Spec item:** S-4 — Resets on reload

- [ ] Write test: set dark mode, reload, assert dark mode persisted
- [ ] Fix: persist to localStorage, read on mount (check if already done — codebase notes suggest theme.ts handles this)
- [ ] Run tests, commit

### Task 2.5: Checkout idempotency key (CheckoutPage.tsx)
**Spec item:** S-5 — Never regenerated

- [ ] Write test: change cart after failed checkout, assert new idempotency key generated
- [ ] Fix: fresh key per checkout attempt, reset on cart change (use content-hash of cart items)
- [ ] Run tests, commit

### Task 2.6: MessagesPage threadId race (MessagesPage.tsx)
**Spec item:** S-6 — No guard

- [ ] Write test: render with undefined threadId, assert no crash + fallback UI shown
- [ ] Fix: optional chaining + fallback UI for missing thread
- [ ] Run tests, commit

### Task 2.7: ErrorBoundary retry backoff (error-boundary.tsx)
**Spec item:** S-7 — Flash loop

- [ ] Write test: trigger 4 consecutive errors, assert exponential delay between retries, stops at max
- [ ] Fix: add exponential backoff (1s, 2s, 4s) + max-retry counter (3)
- [ ] Run tests, commit

### Task 2.8: Guest wishlist migration (vnshop-context.tsx)
**Spec item:** S-8 — Partial failure unhandled

- [ ] Write test: mock migration with 1 item failing, assert successful items kept + toast for failures
- [ ] Fix: try/catch per item, toast partial failures, keep successful items
- [ ] Run tests, commit

---

## WS-3: Dead Buttons (24 items)

**Pattern:** Use existing `comingSoon()` from `lib/ui/coming-soon.ts`. For social links, open URL in new tab. For decorative elements, add `aria-hidden`.

### Task 3.1: Root.tsx navigation dead buttons (7 items)
**Files:** Modify: `fe/src/app/pages/Root.tsx`, `fe/src/app/components/navbar.tsx`, `fe/src/app/components/footer.tsx`

- [ ] Wire "Support" → `comingSoon("Support center")`
- [ ] Social icons → `window.open(url, '_blank')`
- [ ] 15 footer links → `comingSoon("{linkName}")`
- [ ] Footer badges → `aria-hidden="true"`
- [ ] "Notifications" dropdown → `navigate('/notifications')`
- [ ] "Settings" dropdown → `comingSoon("Settings")`
- [ ] Run tests, commit

### Task 3.2: Product & Shopping dead buttons (6 items)
**Files:** Modify: `ProductPage.tsx`, `HomePage.tsx`, `CartPage.tsx`

- [ ] Share button → copy URL + toast "Link copied!"
- [ ] "Size Guide" → `comingSoon("Size guide")`
- [ ] "(X reviews)" count → scroll to reviews section
- [ ] App Store / Google Play → `comingSoon("Mobile app")`
- [ ] Voucher block → copy code + toast "Coupon copied!"
- [ ] Payment badges → `aria-hidden="true"`
- [ ] Run tests, commit

### Task 3.3: Checkout & Auth dead buttons (2 items)
**Files:** Modify: `CheckoutPage.tsx`, `LoginPage.tsx`

- [ ] Step progress circles → clickable to navigate completed steps
- [ ] "Remember me" checkbox → bind to localStorage
- [ ] Run tests, commit

### Task 3.4: Profile dead buttons (3 items)
**Files:** Modify: `ProfilePage.tsx`

- [ ] "Notifications" sidebar → fix href to `/notifications`
- [ ] "Reviews" sidebar → navigate to `/orders?tab=reviews` or `comingSoon("Reviews")`
- [ ] Payment Methods tab → disabled state with "Coming soon" label
- [ ] Run tests, commit

### Task 3.5: Seller/Admin & Wishlist dead buttons (4 items)
**Files:** Modify: `SellerProducts.tsx`, `AdminDashboard.tsx`, `WishlistPage.tsx`

- [ ] Filter button (SellerProducts) → `comingSoon("Filtering")`
- [ ] "Export Report" (AdminDashboard) → `comingSoon("Export")`
- [ ] Share button (WishlistPage) → copy URL + toast
- [ ] Filter button (WishlistPage) → `comingSoon("Filtering")`
- [ ] Run tests, commit

---

## WS-4: UX Anti-Patterns (8 remaining items)

### Task 4.1: Guest cart experience (CartPage.tsx)
**Spec item:** UX-1 — Blocks guest entirely

- [ ] Write test: render as guest, assert localStorage cart shown with "Login to checkout" CTA
- [ ] Fix: show localStorage cart for guests, replace checkout button with login CTA
- [ ] Run tests, commit

### Task 4.2: Checkout address inline form (CheckoutAddressStep.tsx)
**Spec item:** UX-2 — Navigates away

- [ ] Write test: click "Add address", assert modal/drawer appears (no navigation)
- [ ] Fix: use inline modal for address form (Modal component exists in ui/)
- [ ] Run tests, commit (NOTE: may already be done — verify current state)

### Task 4.3: Touch device action buttons (global CSS)
**Spec item:** UX-3 — Hover-only on mobile

- [ ] Fix: add `@media (hover: none)` rules to always show action buttons on touch devices
- [ ] Test visually, commit

### Task 4.4: Seller order accept confirmation (SellerOrders.tsx)
**Spec item:** UX-4 — No confirmation

- [ ] Write test: click accept, assert confirmation dialog shown before API call
- [ ] Fix: add confirmation dialog (use existing Modal component)
- [ ] Run tests, commit

### Task 4.5: Admin reject sellers (SellersApproval.tsx)
**Spec item:** UX-5 — Can't reject

- [ ] Write test: render seller approval, assert "Reject" button exists with reason field
- [ ] Fix: add Reject button + reason textarea + API call
- [ ] Run tests, commit

### Task 4.6: PayoutsQueue scoped loading (SellerWallet.tsx)
**Spec item:** UX-6 — Shared isPending

- [ ] Write test: trigger payout for row 1, assert only row 1 shows spinner
- [ ] Fix: scope loading state per row using row ID in mutation key
- [ ] Run tests, commit

### Task 4.7: Search scroll restoration (SearchPage.tsx)
**Spec item:** UX-8 — Position lost

- [ ] Fix: cache scroll position in sessionStorage, restore on back-navigation
- [ ] Run tests, commit

### Task 4.8: Orders pagination (OrdersPage.tsx)
**Spec item:** UX-10 — Capped at 50

- [ ] Write test: mock 60 orders, assert pagination rendered and functional
- [ ] Fix: add offset pagination with "Load more" (same pattern as seller products)
- [ ] Run tests, commit

---

## WS-5: Accessibility (38 items)

### Task 5.1: WCAG 4.1.2 — Name, Role, Value (15 items)
**Files:** Multiple pages + components

- [ ] Logo button → `aria-label="VNShop home"`
- [ ] Hamburger menu → `aria-label="Open menu"` + `aria-expanded`
- [ ] User dropdown → `aria-label="Account menu"` + `aria-expanded` + `aria-haspopup`
- [ ] Social icons → `aria-label="{platform}"`
- [ ] Cart +/- buttons → `aria-label="Increase/Decrease quantity"`
- [ ] Cart delete buttons → `aria-label="Remove {product} from cart"`
- [ ] Checkout address selection → `role="radiogroup"` + `role="radio"` + `aria-checked`
- [ ] Checkout shipping selection → same radiogroup pattern
- [ ] Checkout payment selection → same radiogroup pattern
- [ ] Product gallery prev/next → `aria-label="Previous/Next image"`
- [ ] Tab interfaces → WAI-ARIA tabs pattern (`role="tablist"`, `role="tab"`, `role="tabpanel"`, `aria-selected`)
- [ ] Search input → `aria-label` or visible label
- [ ] Modal dialogs → `role="dialog"` + `aria-modal="true"` + `aria-labelledby`
- [ ] Breadcrumbs → `aria-label="Breadcrumb"` + `aria-current="page"`
- [ ] Pagination → `aria-label="Pagination"` + `aria-current="page"`
- [ ] Run axe-core audit, commit

### Task 5.2: WCAG 4.1.3 — Status Messages (8 items)
**Files:** Multiple pages

- [ ] Login/Register error messages → `role="alert"`
- [ ] Cart update confirmations → `aria-live="polite"`
- [ ] Search results count → `aria-live="polite"`
- [ ] Verify Sonner uses `role="alert"` (it does by default — confirm)
- [ ] Form validation errors → `role="alert"` + `aria-describedby`
- [ ] Error boundary message → `role="alert"`
- [ ] Order status changes → `aria-live="polite"`
- [ ] Run axe-core audit, commit

### Task 5.3: WCAG 2.1.1 — Keyboard (10 items)
**Files:** Multiple components

- [ ] User dropdown → `onKeyDown` for ArrowUp/Down/Escape
- [ ] Add-to-cart button → ensure focusable + visible on focus
- [ ] Search grid cards → `<a>` or `tabIndex={0}` + `role="link"` + Enter handler
- [ ] Product image gallery → keyboard arrow navigation
- [ ] Quantity stepper → ensure `<button>` elements (not divs)
- [ ] Mobile menu → focus trap when open
- [ ] Modal dialogs → focus trap + return focus on close
- [ ] Checkout steps → Tab order through form fields
- [ ] Filter checkboxes → native `<input type="checkbox">`
- [ ] Dropdown menus → Escape to close + focus return
- [ ] Run keyboard navigation tests, commit

### Task 5.4: WCAG 1.4.3 — Contrast & Visual (5 items)
**Files:** Tailwind config + global CSS

- [ ] Disabled button contrast → ensure 3:1 minimum
- [ ] Placeholder text → ensure 4.5:1 contrast
- [ ] Focus indicators → `:focus-visible` ring on all interactive elements
- [ ] Error text color → verify 4.5:1 on light/dark
- [ ] Link differentiation → underline or 3:1 contrast vs text
- [ ] Run axe-core contrast check, commit

---

## WS-6: Verification (after all workstreams complete)

### Task 6.1: Full unit test suite
- [ ] Run `npm run test` — all tests pass
- [ ] Check coverage report for new code

### Task 6.2: TypeScript build
- [ ] Run `npx tsc --noEmit` — zero errors
- [ ] Run `npm run build` — clean production build

### Task 6.3: Lint + Format
- [ ] Run `npx eslint src/ --max-warnings=0`
- [ ] Run `npx prettier --check src/`

### Task 6.4: E2E tests
- [ ] Run `npm run test:e2e` against production build
- [ ] Review any failures, fix regressions

### Task 6.5: Manual log review
- [ ] Check browser console for React warnings
- [ ] Check network tab for failed requests
- [ ] Verify no unhandled promise rejections

---

## Execution Strategy

Run WS-1 through WS-5 as **parallel subagents** (one per workstream). Each subagent:
1. Reads the spec section
2. Implements fixes with tests
3. Runs `npx tsc --noEmit` + `npx vitest run` after each task
4. Commits atomically per task

After all 5 complete → run WS-6 verification as a single pass.
