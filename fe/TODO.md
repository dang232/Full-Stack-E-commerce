# VNShop Frontend — Outstanding Work

Last updated: 2026-05-16.

## Done

### Foundation
- [x] Removed Supabase artifacts, `pnpm-workspace.yaml`. Added TS configs.
- [x] Deps: `keycloak-js`, `zustand`, `zod`, `uuid`, `@hookform/resolvers`, `vitest`, `happy-dom`, `@testing-library/react`, `@testing-library/jest-dom`, `eslint` v9 + `typescript-eslint` v8 + react-hooks + react-refresh.
- [x] `lib/api/{envelope,client,idempotency}.ts`, `lib/auth/{keycloak,role-guard}.tsx`, `lib/query-client.ts`, `public/silent-check-sso.html`.
- [x] `hooks/{use-auth,use-cart,use-orders,use-wishlist,use-notifications,use-products}.{ts,tsx}`.
- [x] All 14 endpoint modules under `lib/api/endpoints/`.
- [x] `types/api.ts` Zod schemas.

### Pages migrated to live API
- [x] **LoginPage** — Keycloak redirect.
- [x] **HomePage** — `useProducts`.
- [x] **ProductPage** — live reviews + Q&A wired (read + write).
- [x] **SearchPage** — backend `/search` via `useSearch` (Vietnamese full-text on the server); local-catalog fallback only when API errors. Client-side filters (price/rating/free-ship/flash) layer on top.
- [x] **CartPage** — `useCart` end-to-end with optimistic updates.
- [x] **CheckoutPage** — full sequence with `Idempotency-Key` UUID + payment redirect dispatch.
- [x] **OrdersPage** — list, detail, cancel, return modal, dispute escalation, **Mua lại** reorder, "Đánh giá" link.
- [x] **PaymentReturnPage** — polls `paymentStatus`.
- [x] **ProfilePage** — profile + address book CRUD.
- [x] **WishlistPage** — `useWishlist` localStorage shim.
- [x] **SellerPage** — fulfilment queue, accept/reject/ship dialogs, wallet + payouts, **add/edit product modal with multi-image S3 upload**.
- [x] **AdminPage** — dashboard charts, sellers, reviews moderation, **coupon CRUD with full creation dialog**, dispute resolution, payout queue with approval/fail dialogs. Sidebar pending-count badges.

### UX hardening
- [x] **No more `window.prompt` / `window.confirm`** anywhere — replaced with reusable `FormDialog` (textarea, number, validated text fields).
- [x] **`ImageWithFallback` component** rolled out to all product surfaces (Home, Search, Product detail + gallery, Cart, Wishlist, Orders, Seller modal, navbar avatar). Zero raw `<img>` tags remain in app code. Chain: `src` → `fallbackSrc` → placeholder icon.
- [x] **Cart unified + optimistic** — `useCart` now does proper optimistic updates with snapshot rollback for add/update/remove/clear. `useVNShop().addToCart`/`cartCount` route through it. Cart badge updates instantly; rolls back cleanly on server failure.
- [x] **Wishlist unified** — `useVNShop().toggleWishlist`/`isWishlisted` route through the persistent `useWishlist` localStorage store.
- [x] **Notification mark-read with optimistic + rollback** — bell decrements immediately on click; server failure restores prior unread state.
- [x] **Notification dropdown** — popover with relative timestamps, click-to-mark-read via `getNotification` (cache-patched), deep-link navigation, click-outside + Escape close.
- [x] **Ship dialog** — proper carrier picker + tracking number input on seller fulfilment.
- [x] **Coupon dialog** — full create form with PERCENT/FIXED toggle, min order, max discount.
- [x] **Search pagination fixed** — "Xem thêm" button actually loads 20 more on each click (was a no-op due to `[page] = useState(1)` with no setter). Resets when filters change.

### Cross-cutting
- [x] `RequireAuth` and `RequireRole` route guards (full test coverage).
- [x] `ErrorBoundary` surfaces `ApiError.correlationId` for support traces.
- [x] **Code-splitting**: route-level `React.lazy` + `manualChunks`. Main bundle: **232 KB / 65 KB gzip** (was 1.22 MB / 343 KB). Recharts (529 KB) only loads on dashboards.
- [x] **Tests**: Vitest + happy-dom + RTL. **77 tests / 13 files**:
  - `envelope.test.ts` (6) — schema decode + `ApiError`.
  - `client.test.ts` (7) — decoder happy path, 4xx/5xx, malformed JSON, headers, query strings, idempotency.
  - `idempotency.test.ts` (2) — UUIDv4 shape + uniqueness.
  - `use-wishlist.test.ts` (5) — toggle/persist/hydrate/recovery/clear.
  - `role-guard.test.tsx` (7) — RequireAuth + RequireRole branching.
  - `use-cart.test.tsx` (8) — auth gating, derived totals, mutations, error rollback.
  - `use-products.test.tsx` (7) — server normalisation, mock fallback on 5xx, 4xx propagation, placeholder data.
  - `use-notifications.test.tsx` (7) — auth gating, unread counting, markRead cache patching.
  - `use-orders.test.tsx` (6) — pagination, detail gating, cancel cache invalidation, error propagation.
  - `use-search.test.tsx` (5) — params forwarding, normalisation, error surfacing, totals derivation.
  - `use-countdown.test.ts` (4) — formatting, ticking, clamping, cleanup.
  - `form-dialog.test.tsx` (9) — open/close, required-field validation, trim, helper text, isSubmitting.
  - `image-with-fallback.test.tsx` (4) — happy render, empty src, fallback URL chain, placeholder.
- [x] **ESLint**: flat config v9 with TS + react-hooks + react-refresh. **0 problems**.
- [x] `Dockerfile`, `nginx.conf`, `.dockerignore`, `frontend` service in `docker-compose.yml`, `.env.example`, README.

### Deep-audit fixes (2026-05-16)
- [x] **Seller product create flow rewritten** — was previously broken: hardcoded `productId="draft"` for image upload meant create-mode uploads couldn't work. Now follows proper `create → upload → update` sequence with phased status indicator (`creating` / `uploading` / `finalising`). Object URLs revoked correctly on close. Skips final PUT if nothing changed in edit mode. JPEG/PNG/WebP enforced.
- [x] **`use-products` cleaned** — removed runtime mock fallback that masked 5xx errors and would land users on dead-link products. Errors now propagate; UI shows real empty state.
- [x] **`vnshop-context` trimmed** — removed dead fields (`activePage`, `setActivePage`, `selectedProductId`, `setSelectedProductId`, `cartItems`, `cartTotal`, `removeFromCart`, `updateQuantity`, `clearCart`, `login`) after cart/auth unification. Context surface is now exactly what's still consumed.
- [x] **`use-search` error semantics fixed** — was hiding 5xx errors; now exposes verbatim so callers can fall back to local catalog explicitly.
- [x] All `useEffect` cleanup paths verified: notification visibility listener, cart polling, image preview URL revocation, countdown intervals.

---

## Not done (all blocked or low priority)

### Backend-blocked
- [ ] **Seller catalog filtered by ownership** — needs `GET /sellers/me/products`. UI shows full catalog with banner explaining the gap.
- [ ] **Seller reviews inbox** — needs per-seller filter from backend.
- [ ] **Variant selector** on `ProductPage` — needs **BE-1** (variants schema).
- [ ] **Guest cart** — needs **BE-2** (cart-service guest mode + merge endpoint).
- [ ] **Live tracking** — needs **BE-3** (`GET /shipping/track/{code}`).
- [ ] **Real-time bell** — currently 30s polling; needs **BE-4** (SSE/WebSocket).
- [ ] **Wishlist sync across devices** — needs **BE-8** (`/users/me/wishlist`).
- [ ] **Invoice download** — needs **BE-10** wired through gateway.

### Auth-flow blocked
- [ ] Playwright E2E for buyer happy path — blocked on **BE-6** (Keycloak realm seed for `vnshop-web`).
- [ ] End-to-end smoke test (compose up → login → place order → verify trace in Jaeger) — same blocker.

### Marginal polish
- [ ] Roll out `ImageWithFallback` across the high-traffic surfaces (Home product cards, Cart, Search, OrdersPage). Component + tests already shipped; pickup is a per-page mechanical swap.
- [ ] `use-auth` token-refresh test (requires deep keycloak-js mocking).

---

## Backend prerequisites (FE-drives-BE)

| # | Task | Blocks |
|---|---|---|
| BE-1 | Product variants model + API (F23) | Variant selector |
| BE-2 | Guest cart merge (F35) | Add-to-cart pre-login |
| BE-3 | `GET /shipping/track/{trackingCode}` | Live tracking |
| BE-4 | SSE/WebSocket on `/notifications/stream` | Real-time bell |
| BE-5 | Cart-service circuit breaker | Cart degradation |
| BE-6 | **Seed Keycloak `vnshop-web` public client** (PKCE, redirects `http://localhost:{3000,5173}/*`) | **All auth — without this nothing logs in** |
| BE-7 | Gateway CORS env-driven | Production hardening |
| BE-8 | `GET/POST/DELETE /users/me/wishlist` | Wishlist cross-device sync |
| BE-9 | Rotate the leaked Supabase anon key | Security hygiene |
| BE-10 | Invoice download URL (F102) | Order-detail "Download invoice" |

---

## Scripts

```bash
npm run dev        # Vite dev server
npm run build      # tsc -b && vite build (with code-splitting)
npm run typecheck  # tsc -b --noEmit
npm test           # vitest run
npm run test:watch # vitest watch
npm run lint       # eslint .
```

## Verification artifacts (current run)

| Check | Result |
|---|---|
| `npm run typecheck` | clean |
| `npm test` | **77 tests / 13 files, all pass** |
| `npm run lint` | **0 problems** |
| `npm run build` | clean; main bundle 232 KB / 65 KB gzip; recharts isolated to dashboards |
| `window.prompt` / `window.confirm` calls | **0** |
