# Session handover — 2026-05-23 (pt28: dark mode coverage + cart/user schema alignment)

**Last commit (HEAD):** `2ed309b9` (`fix(fe): align cart and user Zod schemas with actual BE shape`)
**Commits pushed since pt27 HEAD `c1ee4014`:** 4.

**Gates (live stack):**
- FE typecheck: 2 errors (pre-existing baseline — PayPalPaymentSection + CheckoutPage. Same since pt24).
- Vitest: 156 / 156 (25 files).
- Playwright `e2e/day-simulation.spec.ts`: **15 / 15 in 23.0s** against the rebuilt stack with all four commits live.
- `vnshop-frontend` rebuilt and healthy at `localhost:3000`.

This session closed out the dark-mode coverage gap and fixed two Zod schema mismatches that were rendering page-wide error fallbacks.

## Commits pushed

| # | SHA | Subject |
|---|---|---|
| 1 | `c5bd13db` | feat(fe-theme): document token contract and fix dark palette elevation |
| 2 | `0d143ba7` | refactor(fe): migrate HomePage and Root to theme tokens for dark mode |
| 3 | `39596de3` | refactor(fe): sweep remaining pages to theme tokens (codemod + inline fixups) |
| 4 | `2ed309b9` | fix(fe): align cart and user Zod schemas with actual BE shape |

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
M  fe/src/app/types/api/{cart,user}.ts                    # schema alignment
M  fe/src/app/hooks/use-cart.ts                           # skeleton item shape fix
```

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `2ed309b9`.
2. **Smoke gates.**
   - `cd fe && npm run typecheck` → 2 baseline errors (PayPalPaymentSection + CheckoutPage).
   - `cd fe && npm test` → 156 / 156.
   - `cd fe && npx playwright test e2e/day-simulation.spec.ts --project=chromium` → 15 / 15.
3. **Manual UI check.** Login as `buyer1 / buyer1`, hit `/cart` and `/profile`, toggle dark mode in the top bar. None of these should show the page-wide error fallback or unreadable text.

## What's still imperfect

- **Inline hex colors in JSX** — `style={{ color: "#374151" }}`, SVG `stroke="#374151"`, and `text-gray-300` are scattered across pages. They render fine in light and acceptably in dark; can be migrated when a page is next touched.
- **Inline tinted card backgrounds** like `style={{ background: "#FEF3C7" }}` on success/warning callouts. These aren't on the brand spectrum and aren't theme-aware, but they read OK against `bg-card` in dark mode.
- **`DesignSystemPage`** intentionally still uses explicit colors — it IS the design fixture.
- **PayPal capture round-trip** — still deferred since pt22.
- **Shipping tracking ownership check** — still deferred since pt22 with three documented reasons.

The audit, regression-test, gotcha-memory, modern-feature, i18n, icon, dark-mode, and schema-alignment arcs are now closed end-to-end. Next-level investments per pt26: distributed tracing, request-id propagation, structured logging, CI matrix.
