# Session handover — 2026-05-18 (FE: hardcoded mock data removal)

**Last commit (HEAD):** `80590d45` — no commits made this session yet (changes uncommitted in the working tree).
**Working tree:** dirty — 13 changes, 879-line file deleted, 3 new files. See "Diff scope" below.
**Continues from:** `SESSION-HANDOVER-2026-05-17-pt3.md` (FE structural refactor).

This session: user flagged that `fe/src/app/components/vnshop-data.ts` (879 lines, 5 mock arrays) and several inline hardcoded arrays on HomePage were standing in for real backend data. Asked to remove all of it.

## TL;DR

Single execution block, no sub-agents (planner-only assist). After the rewire, every render either hits the BE or shows an explicit "coming soon" empty state with i18n. No file in `src/` imports `vnshop-data` (it's deleted). All gates green: 127/127 tests, 0 lint, 0 type errors, build clean. i18n footprint: 160 → 151 `t()` calls across HomePage/ProductPage/SearchPage (−5.6%, well under the 10% threshold).

## What shipped (uncommitted)

| Change | What |
|---|---|
| `fe/src/app/types/ui.ts` (NEW) | `Product` + `UIOrder` lifted from vnshop-data — these UI shapes are wider than BE schemas |
| `fe/src/app/hooks/use-categories.ts` (NEW) | `useCategories()` over `categoryTree()` + `categoryDisplayLabel()` helper |
| `fe/src/app/hooks/use-product-reviews.ts` (NEW) | `useProductReviews(id)` over `reviewsByProduct(id)` |
| `fe/src/app/components/vnshop-data.ts` (DELETED) | 879 lines of mock |
| `fe/src/app/pages/HomePage.tsx` | hero, promo strip, promo banners, seller showcase, trending bar all replaced with `<ComingSoonCard>`. Categories grid pulls from `useCategories()`, drops emoji/color/count. UserWidget hardcoded `orders: 3` / `vouchers: 5` → `"—"`. ~400 lines deleted. |
| `fe/src/app/pages/ProductPage.tsx` | dropped `products.find()` fallback, `reviewsMock.filter()`, `sellers.find()`. Now uses `useProductReviews()` + seller stub. |
| `fe/src/app/pages/SearchPage.tsx` | dropped `?? products` fallback. Categories from `useCategories()`. Drops emoji from category pills. |
| `fe/src/app/pages/OrdersPage.tsx` | `Order as UIOrder` import path → `types/ui` (rename, no semantic change) |
| 5 type-only consumers | path-swapped from `components/vnshop-data` → `types/ui`: vnshop-context, seller-product-modal, use-products, use-search, seller/SellerProducts |
| `fe/src/app/lib/format.ts` | tiny JSDoc tweak (no longer references `vnshop-data`) |
| `fe/src/app/lib/i18n/{en,vi}.json` | 5 new keys: `home.comingSoon.{hero,promo,trending,sellers}`, `product.seller.comingSoon` |

`git diff --stat`: **13 files, 138 insertions, 1477 deletions** (the 879-line delete dominates).

## Verified clean

```bash
cd fe
npm run typecheck   # 0 errors
npm run lint        # 0 warnings, 0 errors
npm run test -- --run   # 127/127 (unchanged from pt3)
npm run build       # clean (~7.2s)
```

i18n threshold check (per pt3 rule):
- `HomePage`: 34 → 32 `t()` calls (−5.9%)
- `ProductPage`: 79 → 72 (−8.9%)
- `SearchPage`: 47 → 47 (0%)
- Total drop: 5.6% (threshold ≤10%).

Final straggler grep: `grep -rn "vnshop-data" src/` → empty.

## User decisions captured this session

User chose explicitly via AskUserQuestion before any rewire:
1. **Sellers** (HomePage SellerShowcase + ProductPage seller card): stub with empty state. No public sellers endpoint exists; admin-only ones are not appropriate for buyer/home views.
2. **Scope**: all hardcoded mock-shaped data, including HomePage inline arrays (heroSlides, PromoStrip, trending, UserWidget hardcoded counters).
3. **Categories**: drop visual extras (emoji, color, count). BE has none of those fields.
4. **Hero / Promo / Trending**: stub all with empty state (consistent with sellers; preserves layout, preserves i18n footprint).

## Architectural answers (for the record)

1. **"Where is `Product`?"** — `fe/src/app/types/ui.ts`. It's a UI shape, intentionally wider than BE `ProductSummary`/`ProductDetail`. `useProducts.fromServer` maps server → UI.
2. **"Where is `UIOrder`?"** — same file. OrdersPage's render shape; flattens BE `Order.subOrders[]` into a single `items[]`.
3. **"What about `Seller` / `Category` / `Review` interfaces?"** — deleted. Server `Category` and `Review` from `types/api` cover all surviving callers. There's no `Seller` UI type because there's no UI consumer of seller fields anymore.
4. **"Why empty states everywhere on HomePage?"** — That's the literal current BE coverage. Hero campaigns, promo tiles, trending searches, seller showcase, voucher counts: none have endpoints. The empty-state cards make the gaps visible instead of papering them with fake data.

## Operational notes (durable)

1. **Empty-state-card pattern**. Lifted in HomePage as a local `<ComingSoonCard icon title description />`. Used 4 times (hero, promo strip, promo banners, seller showcase). If a 4th page needs it, lift to `components/coming-soon-card.tsx`. For now it's local — pt3's "no new component file unless 3+ duplicates" rule is satisfied here because all 4 sites are in the same file.

2. **`useProduct` 404 path**. ProductPage's existing not-found JSX branches on `!product`, which now reflects both "no `id`" and "BE returned 404 / network error". `productById` throws on HTTP 404 → `useProduct.data` stays undefined; the not-found block renders. Verified manually.

3. **Category label fallback**. BE `Category` has `label`, `name`, both optional. New `categoryDisplayLabel(cat)` helper does `cat.label ?? cat.name ?? cat.id`. Use it everywhere — don't reach into the fields directly. Two call sites in SearchPage (pill render + active-filter chip + facet formatter).

4. **`useProducts` default value**. Pages that previously did `data: catalog = products` now do `data: catalog = [] as Product[]`. The `as Product[]` cast is needed because `useProducts<Product[]>` returns `Product[] | undefined` and the default has to match the generic, not `any[]`.

5. **Review-shape mismatch was clean**. Mock had `avatar` and `variant`; BE doesn't. ProductPage's "live reviews" branch was already correct (used `userName ?? userId ?? "?"` and didn't reference avatar/variant). Deleting the mock-fallback branch removed the only `avatar`/`variant`/`date` usages.

## Deferred (BE follow-ups)

These are now visible empty states. Address by either adding a BE endpoint OR removing the section.

- **Public sellers endpoint** — `GET /sellers` (paged, public). Currently only admin-only `GET /admin/sellers` exists (admin.ts:20). Required to populate ProductPage's seller card and HomePage's SellerShowcase.
- **Hero campaigns / promo tiles** — needs a CMS-style endpoint (campaigns + scheduling). Alternative: delete sections wholesale next session.
- **Trending searches** — needs the search service to track + expose top queries. Alternative: read top categories from analytics.
- **User profile counters** — `orders` and `vouchers` counts. Probably exist already on `/users/me` or `/users/me/profile` — check before adding endpoints.
- **5 mock fallbacks to `vnshop-data.ts`** flag from pt3 §"Outstanding follow-ups" — **resolved this session**. All gone.
- **Translation review pass round 2** (pt2 §"Next-session candidates") — still pending.

## File locations to know (post-this-session)

- **API layer:** unchanged from pt3. Public barrel `fe/src/app/lib/api/index.ts`. Pipeline `fe/src/app/lib/api/client.ts` + `interceptors.ts`.
- **UI types:** `fe/src/app/types/ui.ts` (new) — `Product`, `UIOrder`. Wider than BE schemas; mapped via `fromServer`.
- **BE schemas:** `fe/src/app/types/api/{shared,product,user,...}.ts` (unchanged from pt3).
- **Hooks:**
  - `hooks/use-products.ts` — `useProducts()`, `useProduct(id)`
  - `hooks/use-categories.ts` (new) — `useCategories()`, `categoryDisplayLabel()`
  - `hooks/use-product-reviews.ts` (new) — `useProductReviews(id)`
- **Pages:** all unchanged paths from pt3, just internal rewires.

## Quality-pass discipline (durable, applied)

After the main rewires, ran the post-merge sweep per pt3:

- **DRY**: `<ComingSoonCard>` covers all 4 stub sites within HomePage; not duplicated across files.
- **Barrel discipline**: `Product` and `UIOrder` are surfaced via `types/ui`; no consumer reaches into the deleted module.
- **Final grep**: `grep -rn "vnshop-data" src/` → empty.
- **No-comment rule**: removed the long `format.ts` JSDoc that described migration history; kept only a one-liner that describes what the module does.

## How to resume

1. `git status` — should show 13 modified files + 1 deletion + 3 new files. Nothing committed yet.
2. **Decide on commit shape**. Options:
   - **One squashed commit**: `refactor(fe): remove all hardcoded mock data; surface empty states for unimplemented BE endpoints`. Matches the diff (one cohesive intent).
   - **Three commits** (cleaner per-area):
     a. `refactor(fe): lift Product + UIOrder to types/ui and migrate type-only consumers`
     b. `feat(fe): add useCategories + useProductReviews hooks`
     c. `refactor(fe): rewire HomePage/ProductPage/SearchPage to BE; stub uncovered sections`
   The user has not requested a commit — wait for explicit ask before running `git commit`.
3. `cd fe && npm run typecheck && npm run lint && npm run test -- --run && npm run build` — should be green.
4. **Optional smoke (`npm run dev`)**: home page shows category pills from BE, bestsellers from BE, four "coming soon" cards (hero, promo, promo banners, seller showcase, trending). Product page shows reviews from BE and seller stub. Search page facets render.
5. Pick from "Deferred" — public sellers endpoint is the highest-leverage next step (it unlocks both ProductPage seller card and HomePage SellerShowcase to come back as real data).
6. The pt3 rules still apply for any sub-agent work this session: rebase pre-flight onto local main, grep-threshold gate, post-merge DDD/SOLID/DRY + barrel discipline.

## Context for the next agent

The user's pattern across pt2 / pt3 / today:
- Wants the FE honest. If the BE doesn't have it, don't fake it.
- Wants empty states with i18n, not silently-deleted UI (preserves the layout for when the BE catches up).
- Approves "stub with empty state" over "delete wholesale" when in doubt.
- Confirmed (this session) that drop-visual-extras over hardcode-FE-side is the right call when BE schema is narrower than UI design called for.
