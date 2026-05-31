# Session handover — 2026-05-23 (pt28: dark mode + BE-shape alignment + cart wiring + schema audit sweep)

**Last commit (HEAD):** `5b62048f` (`fix(fe): pageSchema accepts both Spring number and user-service page`)
**Commits pushed since pt27 HEAD `c1ee4014`:** 14.

**Gates (live stack):**
- FE typecheck: 2 errors (pre-existing baseline — PayPalPaymentSection + CheckoutPage. Same since pt24).
- Vitest: 156 / 156 (25 files).
- cart-service jest: 13 / 13 (added 9 ProductHttpClientAdapter specs).
- Playwright `e2e/day-simulation.spec.ts`: **15 / 15 in 16.9s** against the rebuilt stack with all ten commits live.
- `vnshop-frontend` and `vnshop-cart-service` rebuilt and healthy.

This session closed out the dark-mode coverage gap, fixed seven Zod schema mismatches across cart/user/order/checkout/review/seller-finance/admin that were rendering page-wide error fallbacks against real BE data, fixed the long-standing "raw UUID + 0₫" cart bug (docker-compose env wiring + product-service variants[] price/image extraction), and ran a read-only audit across all 21 FE schema files to catch drift before users hit it.

## Commits pushed

| # | SHA | Subject |
|---|---|---|
| 1 | `c5bd13db` | feat(fe-theme): document token contract and fix dark palette elevation |
| 2 | `0d143ba7` | refactor(fe): migrate HomePage and Root to theme tokens for dark mode |
| 3 | `39596de3` | refactor(fe): sweep remaining pages to theme tokens (codemod + inline fixups) |
| 4 | `2ed309b9` | fix(fe): align cart and user Zod schemas with actual BE shape |
| 5 | `47d1fc2c` | docs(pt28): session handover for dark mode + cart/user schema fixes |
| 6 | `ab9eda93` | fix(fe): align order Zod schema with order-service OrderResponse shape |
| 7 | `b9af48b4` | fix(cart): wire PRODUCT_SERVICE_URL and read price/image from variants[] |
| 8 | `35ed317d` | docs(pt28): expand handover with order schema + cart wiring fixes |
| 9 | `f8cd1c21` | fix(fe): align checkout, review, and seller-finance schemas with BE |
| 10 | `017556a4` | fix(fe): align admin schemas with BE DTOs (sellers, disputes, payouts, coupons) |
| 11 | `62310377` | docs(pt28): finalize handover with schema audit sweep + checkout/admin fixes |
| 12 | `6fa83c13` | fix(fe-order): accept the flat OrderListItemResponse shape on /orders list |
| 13 | `0a3c0f8a` | fix(fe-orders): flatten seller pending-orders into PendingSubOrder rows |
| 14 | `5b62048f` | fix(fe): pageSchema accepts both Spring `number` and user-service `page` |

## Why each commit mattered

### 1. Theme token contract (`c5bd13db`)
The dark-mode toggle (`.dark` on `<html>`) was wired through `vnshop-context.tsx` since pt12-ish, but every page hard-coded `bg-white`, `text-gray-900`, `text-gray-500` and inline `#fafbfc` styles. Toggling dark mode only flipped the body background while text and cards stayed white-mode → unreadable.

Two underlying problems on top:
- **Light mode**: `--background` was `#ffffff` and `--card` was `#ffffff` — indistinguishable, so cards never read as elevated.
- **Dark mode**: `--background` and `--card` were both `oklch(0.145 0 0)` — every card was invisible against the body.

Fix established a 3-step elevation scale in both modes (`bg-background` < `bg-card` < `bg-surface-elevated`), readable foreground/muted-foreground tokens, and a doc block at the top of `theme.css` explaining the contract so the rest of the FE can be migrated incrementally.

### 2. HomePage + Root pilot (`0d143ba7`)
Hand-migrated the two most-visited surfaces. HomePage outer wrapper, section cards, product cards, sellers, bestsellers, trending bar, trust bar, user widget all use tokens now. Root.tsx's 30+ `isDark ? "..." : "..."` inline-style ternaries are gone — header gradient is `bg-gradient-to-r from-[#00BFB3] to-[#009990] dark:from-[#0d3d3a] dark:to-[#062523]`, dropdown menu uses `bg-card`/`text-foreground`/`border-border`/`hover:bg-muted`, mobile drawer uses `bg-[#009990]/95 dark:bg-[#062523]/95`. Footer kept dark in both themes (intentional brand choice).

The two remaining `isDark` refs in Root.tsx are correct — they pick the Sun vs Moon icon for the toggle button itself.

### 3. Codemod sweep + inline-style fixups (`39596de3`)
`fe/scripts/migrate-dark-tokens.mjs` walks `src/app/{pages,components}` and applies word-boundary regex swaps:
- `bg-white` → `bg-card` (NOT `bg-white/N`, preserved by negative lookahead)
- `bg-gray-50/100` + `hover:` variants → `bg-muted` / `hover:bg-muted`
- `text-gray-700/800/900` → `text-foreground`
- `text-gray-400/500/600` → `text-muted-foreground`
- `border-gray-100/200` → `border-border`

47 files / 678 swaps. Skipped: `HomePage.tsx`, `Root.tsx` (already hand-migrated), `DesignSystemPage.tsx` (it IS the token fixture).

Then 8 inline-style fixups the codemod couldn't catch — the visible bug was the LoginPage/RegisterPage cream gradient (`linear-gradient(#f0fffe, #fff8f0)`) on a dark mode bundle, producing white-on-cream unreadable text. Those wrappers + AdminPage/SellerPage page chrome + CouponsManagement/SellerProducts/ProfilePage muted strips all moved to `bg-background` / `bg-muted`.

### 4. Cart + user schema alignment (`2ed309b9`)
Two Zod schemas had drifted from what the BE actually returns. Both were producing page-wide error fallbacks — visible on `/cart` and `/profile` after the user logged in.

**cart-service** returns:
```
CartResponse {
  items: [{ productId, productName, productImage,
            unitPrice: { amount, currency }, quantity,
            subtotal: { amount, currency }, addedAt }],
  itemCount, uniqueItemCount,
  totalAmount: { amount, currency },
  updatedAt
}
```
FE expected `{ items: [{ name, image, price: number, ... }], totalAmount: number }`. Fix: `cartItemSchema` and `cartSchema` accept the BE shape via a `moneyToNumber` union (`number | { amount, currency? }`) and a transform that aliases `productName → name`, `productImage → image`, `unitPrice → price`. Legacy fields still accepted so the optimistic-update path works with skeleton items.

**user-service** returns:
```
BuyerProfileResponse { keycloakId, name, phone, avatarUrl, addresses }
```
FE schema required `id: z.string()` and `email: z.string()`. Email lives only in Keycloak — consumers already fall back to `useAuth().profile.email`. Fix: `userProfileSchema` treats id/email as optional, accepts `keycloakId`/`avatarUrl`, and transforms `keycloakId → id`, `avatarUrl → avatar`. Existing test fixture already includes `keycloakId`, so the 4 users-endpoint tests still pass.

## Operational gotchas added this block

**55. Codemod-only sweeps miss inline `style={{}}`.** The 47-file codemod swap was clean for Tailwind utilities but left every `style={{ background: "linear-gradient(...)" }}` and `style={{ background: "#f9fafb" }}` untouched. The user spotted the LoginPage cream-gradient bug visually within a minute of the commit. Lesson: after running a Tailwind-only codemod, immediately grep for `style=\{\{[^}]*background` in pages and audit each. The repo grep used to find them:
```
grep -rEn "style=\{\{[^}]*background:" src/app/pages | grep -vE "linear-gradient.*<known-brand-color>|rgba\(0,191|rgba\(0,0,0|rgba\(255,255"
```
Brand colors (teal/orange/red gradients) are intentional and stay; everything else is a candidate.

**56. BE vs FE schema drift only manifests on actual data.** `cartSchema` and `userProfileSchema` both passed every unit test in the repo because the fixtures were stale. The bug only fired against the live BE under a logged-in account hitting `/cart` or `/profile`. Lesson: when adding regression coverage for a schema, the fixture must be derived from the BE response shape (run the endpoint and capture the JSON) — never hand-rolled to match what the FE wishes the BE returned.

**57. Designer agent silent-bail under directed scope.** Spawned the `designer` agent with a tight HomePage + Root + theme.css scope; it output narration ("Now I have a complete picture. Let me execute all three files systematically") and then `<sandbox-stop>` with zero file writes. Resending a forcing message produced the same outcome — exit with no diff. `feedback_detect_silent_bail` already documents this; the failure mode survives explicit "EXECUTE NOW. Use Edit/Write tools" instructions. Took the work over directly. Reason it bails seems to be the agent reasoning about a multi-file plan and timing out before any tool call lands.

**58. Schema-drift bugs land as page-wide error fallbacks; chase the actual BE shape, not the error string.** Three different schemas (cart, user, order) had drifted from what the corresponding services emit, each producing an "Invalid input" Zod error popup that obscured the real bug. `userProfileSchema` was the worst because email lives in Keycloak — the FE schema demanded it from `user-service` which has no idea what email is. Lesson: when a Zod parse fails, the fix is to find the actual BE DTO (`*Response.java`/`*-response.ts`) and either match the shape exactly or layer a `.transform()` that aliases the legacy fields. **Do not** loosen the schema to `.passthrough()` and call it done — that just buries the drift until a consumer crashes downstream.

**59. Offline-mode fallbacks in service adapters need loud failure modes.** cart-service's `ProductHttpClientAdapter` had this branch:
```ts
if (!this.productServiceUrl) {
  return { productId, productName: productId, productImage: '', unitPrice: Money.zero('VND') };
}
```
That was meant for unit testing but kicked in *in production* because `PRODUCT_SERVICE_URL` was missing from `docker-compose.yml`. Result: every cart item rendered as a UUID with 0₫. The container logs showed nothing because no exception was thrown. Lesson: silent offline-mode branches in BE adapters need either (a) a `LOG.warn` on construction so it shows up in startup logs, (b) a feature flag separate from the URL so the branch only fires when explicitly enabled for tests, or (c) hard-fail at module init when the URL is unset and the env isn't `test`/`development`. Adding a comment in compose pointing at the adapter is the bare minimum.

**60. order-service has no order-level status field — it must be derived from sub-orders.** `OrderResponse` only carries `paymentStatus` plus `subOrders[].fulfillmentStatus`. The FE always assumed `order.status: string` and burned every parse. Fix: a `deriveOrderStatus()` helper in the FE schema transform that maps `(subStatuses[], paymentStatus) → "pending" | "confirmed" | "shipping" | "delivered" | "cancelled" | "returned"`, called in the Zod transform so all consumers see a stable derived status without rewriting. If a future order-service ever ships a top-level `status`, it's accepted as-is and overrides the derivation.

**61. After fixing one schema, audit ALL of them — drift clusters.** `cart` led us to `user`, which led to `order`, which led to `checkout`/`review`/`seller-finance`/`admin`. Every Java DTO that wraps `BigDecimal`/`Long`/uses `*Id` field naming is a candidate. Dispatched an explore agent to read all 21 FE schemas vs their BE DTOs in one pass; turned up 4 more HIGH-risk drifts (checkout breakdown, shipping options, review, wallet) and 4 admin-surface drifts. Verified each agent finding by reading the BE source before patching — agents reliably describe BE shapes correctly when they cite specific record fields. Worth doing a similar audit any time a BE service evolves its DTOs without coordinated FE updates.

**62. VNPay/MoMo create endpoints have no redirectUrl — that's missing BE functionality, not schema drift.** FE does `window.location.href = init.redirectUrl` after `POST /payment/vnpay/create` but the BE's `PaymentResponse` carries `paymentId, orderId, buyerId, amount, method, status, transactionRef, createdAt` with no `redirectUrl`. So checkout currently redirects to `undefined`. This needs product-side direction: should the BE generate the gateway redirect URL server-side and return it, or does the FE construct it from the gateway sandbox config? Out of scope for the schema-alignment work; flagging here so the next session knows it's the next checkout-flow blocker.

**63. /orders list and /orders/{id} are different shapes — same schema can't fit both.** GET /orders returns `Page<OrderListItemResponse>` (flat: `orderId`, `status: String`, `totalAmount: BigDecimal`, `itemCount`). GET /orders/{id} returns `OrderResponse` (nested: `id`, `subOrders[]`, `finalAmount: Money`, no top-level status). The orderSchema absorbs both via the same transform, but the status logic has to handle three cases:
  1. Already a UI value (FE optimistic-updates pass `"pending"` directly)
  2. Raw FulfillmentStatus enum string (the list endpoint emits this — `"PENDING_ACCEPTANCE"`)
  3. No status (the detail endpoint — derive from `subOrders[].fulfillmentStatus + paymentStatus`)
   The transform output is always one of the six UI status values so STATUS_CONFIG indexing on OrdersPage continues working.

**64. /seller/orders/pending returns `List<OrderResponse>`, not `List<PendingSubOrder>`.** The seller-orders queue page renders one row per sub-order (each gets its own accept/reject/ship buttons) but the BE returns full nested orders. Fix: do the flattening inside the endpoint adapter (`fe/.../endpoints/orders.ts`), keep the page contract as `PendingSubOrder[]`. The `firstSubOrder()` helper for the mutations is acceptable because the page invalidates the list after each mutation and re-renders from fresh server state — no need to track which sub-order the seller actually clicked for the optimistic-update path.

**65. Spring Page<T> uses `number` for the index; user-service `PublicSellersPageResponse` uses `page`.** Two different paged-response shapes from the same backend stack. Schema accepts both via `.optional()` and the transform surfaces the index under both keys, so consumers can read `.page` or `.number` without runtime probing. Also added `page?` to the `Page<T>` TS interface.

## Files touched this block

```
M  fe/src/styles/theme.css                                # token contract + 3-step elevation + doc block
M  fe/src/app/pages/HomePage.tsx                          # hand migration
M  fe/src/app/pages/Root.tsx                              # hand migration
A  fe/scripts/migrate-dark-tokens.mjs                     # codemod
M  fe/src/app/components/{form-dialog,image-with-fallback,kpi-card,
                          notification-bell,search-autocomplete,
                          seller-product-modal,error-boundary,facet-list,
                          ui/modal,checkout/{PayPalPaymentSection,
                          StripePaymentSection,VietQrPaymentSection}}.tsx
M  fe/src/app/pages/{CartPage,DesignSystemPage,LoginPage,MessagesPage,
                     OrdersPage,PasswordResetPage,PaymentReturnPage,
                     ProductPage,ProfilePage,RegisterPage,SearchPage,
                     SellerDetailPage,WishlistPage}.tsx
M  fe/src/app/pages/admin/{AdminDashboard,AdminPage,CouponDialog,
                           CouponsManagement,DisputesQueue,PayoutsQueue,
                           ReviewsModeration,SellersApproval}.tsx
M  fe/src/app/pages/checkout/{CheckoutAddressStep,CheckoutPage,
                              CheckoutPaymentStep,CheckoutReviewStep,
                              CheckoutShippingStep,CheckoutSuccess,
                              CheckoutSummary}.tsx
M  fe/src/app/pages/seller/{SellerDashboard,SellerOrders,SellerPage,
                            SellerProducts,SellerReviews,SellerSettings,
                            SellerWallet,ShipDialog}.tsx
M  fe/src/app/types/api/{cart,user,order}.ts               # schema alignment
M  fe/src/app/hooks/use-cart.ts                           # skeleton item shape fix
M  docker-compose.yml                                     # PRODUCT_SERVICE_URL on cart-service
M  services/cart-service/src/cart/infrastructure/product-http-client.adapter.ts
A  services/cart-service/src/cart/infrastructure/product-http-client.adapter.spec.ts  # 9 specs locking in BE shape
```

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `b9af48b4`.
2. **Smoke gates.**
   - `cd fe && npm run typecheck` → 2 baseline errors (PayPalPaymentSection + CheckoutPage).
   - `cd fe && npm test` → 156 / 156.
   - `cd services/cart-service && npm test` → 13 / 13.
   - `cd fe && npx playwright test e2e/day-simulation.spec.ts --project=chromium` → 15 / 15.
3. **Manual UI check.** Login as `buyer1 / buyer1`, add 2 products to cart, hit `/cart` and `/checkout`. Cart items should now show real product names, images, and prices (not UUIDs and 0₫). Order review at `/checkout` should render without the page-wide error fallback. Toggle dark mode in the top bar — Login, Register, Cart, Profile, Checkout should all be readable.

## And memory

Two new feedback files were *not* written this block — the gotchas are intentionally captured in this handover instead of the persistent memory directory because they're tied to specific BE DTOs that may evolve. If `OrderResponse` or `BuyerProfileResponse` ever changes shape on the BE side, the FE schema transform is the single point to update; nothing in the persistent memory should encode the current shape. The general lessons (#58 schema drift, #59 silent offline-mode, #60 derive-don't-demand) are reusable; the specific field names are not.

## What's still imperfect

- **Inline hex colors in JSX** — `style={{ color: "#374151" }}`, SVG `stroke="#374151"`, and `text-gray-300` are scattered across pages. They render fine in light and acceptably in dark; can be migrated when a page is next touched.
- **Inline tinted card backgrounds** like `style={{ background: "#FEF3C7" }}` on success/warning callouts. These aren't on the brand spectrum and aren't theme-aware, but they read OK against `bg-card` in dark mode.
- **`DesignSystemPage`** intentionally still uses explicit colors — it IS the design fixture.
- **PayPal capture round-trip** — still deferred since pt22.
- **Shipping tracking ownership check** — still deferred since pt22 with three documented reasons.

The audit, regression-test, gotcha-memory, modern-feature, i18n, icon, dark-mode, and schema-alignment arcs are now closed end-to-end. Next-level investments per pt26: distributed tracing, request-id propagation, structured logging, CI matrix.
