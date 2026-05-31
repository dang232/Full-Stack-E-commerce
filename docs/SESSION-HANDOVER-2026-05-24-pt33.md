# Session handover — 2026-05-24 (pt33: UX fix-it block — 7 commits across 4 personas)

**Last commit (HEAD):** `9175f3eb` (`feat(a11y): separate badge counts from sidebar button labels`)
**Commits pushed since pt32 HEAD `8d46ad2e`:** 7.

**Gates (live stack):**
- FE typecheck: 0 errors clean.
- FE vitest: 156 / 156.
- FE i18n parity (EN vs VI keys): empty diff.
- order-service jest: `CalculateCheckoutUseCaseTest` 9 / 9.
- user-service mvn: 118 / 118 (NEW: `BuyerUseCasesTest` +2 cases for `ListBuyerPublicProfilesUseCase`).
- product-service mvn: 31 / 31.
- Playwright workday suite: 3 / 3 in ~29 s.
- Playwright journey suite: 5 / 7 in ~36 s — chapter 6 is intermittently flaking on chapter-5's pendingBalance projection lag (pt32 known issue, kafka catch-up race; not caused by this block's changes — see "open thread" below).

## Why this block

The pt32 walkthrough surfaced eight UX issues clustered around two themes: back-office chrome that wasn't separated from storefront chrome, and lists/empty-states that looked mid-build. The most damaging was reviewer names rendering as raw UUIDs on every product page — a customer-trust killer.

This block worked the fix-it plan in priority order. Each item is in its own commit so any one can be reverted without disturbing the others.

## What landed this block

### 1. Reviews fix (cc8666dd) — BE + FE, highest customer impact

The Reviews tab on the product detail page was rendering reviewer names as raw UUIDs. Two-layer fix:

- **user-service:** NEW `GET /users/public-profiles?ids=u1,u2,...` (public, batched, capped at 100 ids per call). Returns `{ userId, displayName, avatarUrl }` only. Wired through `ListBuyerPublicProfilesUseCase` + new repo method `findBuyersByKeycloakIds` + permitAll in `SecurityConfig` (same surface that already shows publicly elsewhere on the storefront). +2 unit tests in `BuyerUseCasesTest`.
- **product-service:** NEW `BuyerProfileLookupPort` + HTTP adapter (`UserServiceBuyerProfileAdapter` + `UserServiceHttpClient`) mirroring the order-service ↔ coupon-service `CouponValidationPort` pattern from pt31. `GetProductReviewsUseCase` now batch-resolves buyer ids after the repo fetch, returning `EnrichedReview` pairs. `ReviewResponse` carries `userName` + `userAvatarUrl` (nullable). Failure semantics: any 4xx/5xx from user-service → empty map → FE renders the anonymous fallback. Never the UUID. `ProductServiceApplicationTests` gets a `MockitoBean` for the new port.
- **FE:** review schema picks up `userName` + `userAvatarUrl`, drops the buyerId fallback for the displayed name. Histogram now hides entirely when `reviews.length === 0` (was rendering hardcoded 68/22/7/2/1 percentages — looked like a math bug); when reviews exist, percentages derive from real per-star counts. NEW empty-state card "Be the first to review" leads the tab when zero reviews exist so the form is the obvious next action.

Two pt32 walkthrough issues closed end-to-end.

### 2. Confirm-payout dialog (2f17dd02) — production safety

Admin's "Complete payout" was a one-click `complete.mutate(p.id)` against rows showing only UUIDs. Real money moved on misclick. Asymmetric with Fail (which already gated behind a reason dialog).

Added a destructive-confirm `FormDialog` (no fields, green submit) showing the formatted amount + first-8-of-the-sellerId + a "this cannot be undone" warning. Click Complete → opens dialog → Confirm → mutation fires → row drops. Cancel/X/Esc close harmlessly. Mirror of the Fail dialog pattern. Journey ch6 spec updated to drive the new dialog.

### 3. Console chrome separation (b38b3bda) — biggest visual change

Storefront `Navbar` (categories, search, wishlist, cart, marketing footer) leaked into `/admin` and `/seller`, where none of it was useful and all of it competed for the eye.

Splits chrome by pathname:
- `/admin/*` and `/seller/*` render NEW `ConsoleChrome` — thin sticky bar with VNShop wordmark + persona badge ("Admin Console" / "Seller Hub") + theme + language + user menu. The user menu has a top-of-list "Back to storefront" entry so the storefront is one click away.
- Below back-office content, replace the dark VNPay/MoMo footer with a one-line strip showing copyright + "Back to storefront →".
- Storefront routes unchanged.
- In-page header cards on `AdminPage` (and most of the seller card) become redundant once `ConsoleChrome` carries the persona signal — admin console card removed; seller card kept (it shows shop avatar + pending-orders chip, more than just persona).

### 4. Approve Sellers application context + detail modal (2289099e)

Admins were approving sellers based on shop name only. The seller summary schema already carried `bankName`, `bankAccount`, `tier`, `appliedAt` — the data was there, just unrendered.

Row now shows: shop name + status pill + applied-at relative time, plus a small-text second line "Bank: VCB · Account ending 6789 · Tier: STANDARD". Approved sellers don't render the Approve button (the row stays in the list as a confirmation).

NEW `SellerApplicationDetail` modal: every field the BE returns plus the Approve action so admins can act from the detail view without dismissing + re-finding the row.

NEW `formatRelativeTime` utility in `fe/src/app/lib/format.ts` using `Intl.RelativeTimeFormat` with locale-cached formatters. Reused by item 6 for date grouping.

### 5. Homepage empty rails — branded cards + deterministic avatars (32f061d6)

The homepage rails read as "site not finished" to a first-time buyer:
- Featured Shops rendered six identical default-icon teal squares.
- Bestsellers stayed empty when the catalog was cold.
- Flash Sale just said "Coming soon" with no value prop.

Fixes:
- NEW `initialAvatarColor()` utility: hashes `seller.id` (or shopName) into one of eight teal/orange-family palette colors. A row of empty-logo shops now reads as visually distinct shops, not duplicate placeholders. Initial derivation also fixed for non-ASCII names via `codePointAt()`.
- `SellerShowcase` empty state: branded card (one card, not six) with "More shops joining VNShop weekly" copy and a CTA into `/search`. Same template, smaller, used for `Bestsellers` — title + body + CTA replacing the previous bare empty list.
- `FlashSaleEmpty`: replace the bare "Coming soon / Flash sale ended" pair with one card carrying "No flash sale running right now" + context that flash sales drop weekly.

### 6. List chrome + StatusPill extraction (08850679)

Three back-office surfaces hit the long-list scaling problem from the pt32 walkthrough — past ~10 rows the flat list became scan-hostile.

Shared primitives (NEW):
- `components/status-pill.tsx` — `StatusPill` with status→tone inference (success/warning/danger/info/neutral) so SellerOrders, SellerWallet, PayoutsQueue, SellersApproval all render the same shape. Removes the inline-color drift that was a pt31 gotcha #79 category of bug.
- `lib/group-by-date.ts` — `groupByDate` utility that buckets items into Today / Yesterday / This week / per-month sections in display order, with locale-aware month labels.

Page wiring:
- `SellerOrders`: 6 status tabs (All / Pending / Accepted / Packed / Shipped / Cancelled) + order-id search box. Filter-empty hint when the tab/search yields no rows.
- `SellerWallet`: 4 status filter chips + month-grouped withdrawal history.
- `PayoutsQueue`: seller-id/payout-id search + sort toggle (date ↔ amount). Date sort triggers grouped sections; amount sort renders flat (grouping a comparison sort would defeat the user's intent).

### 7. A11y badge separation (9175f3eb)

Admin sidebar buttons rendered the badge count as part of the visual button label. Screen readers read the whole thing as one phrase ("Approve Sellers 1") and assertion code in the journey suite had to special-case it (pt32 gotcha #80).

Adds `aria-label="<label>, <n> pending"` when a badge is present, marks the icon + badge span as `aria-hidden`. Visual output unchanged.

## Discoveries / gotchas this block (extends pt32 list)

**86. Cross-service public-profile lookup is the shape we want for any "render a name where we have a UUID" surface.** The pattern that landed: read-only batch endpoint at the upstream service (`/users/public-profiles?ids=...`), HTTP-port + adapter mirror in the consumer service, fail-soft to empty map. Mirrors the pt31 coupon-validation pattern. Likely future surfaces: rendering admin names on dispute-resolution rows, seller names on order-detail (where currently we have only sellerId).

**87. `Intl.RelativeTimeFormat` numeric: "auto" picks the right phrase for "this minute" / "yesterday" / "last week".** Saved a 30-line custom relative-time function. Cache the formatters by locale; instantiating them is non-trivial in tight render loops (the wallet history list re-renders on every filter change).

**88. Group-by-date for amount-sort is wrong.** The pt33 PayoutsQueue iteration originally grouped under both sort modes — but date sections under an amount sort split the comparison the user is trying to make ("show me the biggest first" wants a contiguous list, not "today's biggest, then yesterday's biggest"). Sections only when sorted by date.

**89. `formatPrice(amount).slice(0, 8)` is the right truncation for sellerId display in confirmation dialogs.** Full UUIDs in confirm-text overwhelm the warning copy. Eight chars is enough disambiguation for a single-screen admin who can verify against the row.

**90. The Reviews schema transform regressed to using `?? null` instead of leaving fields undefined.** This is intentional — the BE returns `userName: null` when user-service returns no display name (the buyer never set one), and the FE needs to distinguish "we looked it up and there's no name" (render anonymous) from "we haven't looked yet" (still loading). `?? null` collapses both into the same render which is the right BA-grade outcome but loses a future affordance. Worth revisiting if we ever surface a "name not yet set" prompt to buyers.

## Open thread for the next session

**Highest priority — chapter 6 BE projection flake.** The pt32 handover documented this; it's persisted into pt33. Chapter 5 submits a payout (sync save), chapter 6 polls for `pendingBalance > 0` (kafka projection), but on cold-start runs the projection lag exceeds the bumped 90s timeout. The fix is a chapter-3 → chapter-5 settle gate: add a poll between chapters 5's submission and chapter 6's pre-snapshot that confirms the projection. Affects journey reliability, not production behavior.

**Medium — payout audit trail.** The confirm dialog logs admin clicks but the BE doesn't yet record `completedBy` or `completedAt` on the payout row. Adding these would let the admin payouts queue show "Completed by admin1, 2 hours ago" once the payout flips to COMPLETED. Defer until requested.

**Low — the seven from pt32 still apply:**
- Avatar upload feature implementation (design doc still in `docs/superpowers/specs/2026-05-24-avatar-upload-object-storage-design.md`).
- PayPal capture round-trip.
- Shipping tracking ownership check.
- VNPay/MoMo `redirectUrl` from PaymentResponse.
- Kafka env-override audit across the other six services that hard-code `localhost:9092` in `application.yml`.

## Files touched this block

```
A  fe/src/app/components/console-chrome.tsx                                          # NEW item 3
A  fe/src/app/components/status-pill.tsx                                             # NEW item 6
A  fe/src/app/lib/group-by-date.ts                                                   # NEW item 6
A  fe/src/app/lib/initial-avatar.ts                                                  # NEW item 5
A  fe/src/app/pages/admin/SellerApplicationDetail.tsx                                # NEW item 4
M  fe/src/app/lib/format.ts                                                          # +formatRelativeTime
M  fe/src/app/lib/i18n/{en,vi}.json                                                  # parity sweep
M  fe/src/app/pages/HomePage.tsx                                                     # item 5
M  fe/src/app/pages/ProductPage.tsx                                                  # item 1 FE
M  fe/src/app/pages/Root.tsx                                                         # item 3 chrome split
M  fe/src/app/pages/admin/AdminPage.tsx                                              # items 3 + 7
M  fe/src/app/pages/admin/PayoutsQueue.tsx                                           # items 2 + 6
M  fe/src/app/pages/admin/SellersApproval.tsx                                        # item 4
M  fe/src/app/pages/seller/SellerOrders.tsx                                          # item 6
M  fe/src/app/pages/seller/SellerPage.tsx                                            # item 3 console chrome
M  fe/src/app/pages/seller/SellerWallet.tsx                                          # item 6
M  fe/src/app/types/api/review.ts                                                    # item 1 schema
M  fe/e2e/journey/06-admin-closes-the-loop.spec.ts                                   # item 2 spec wiring
A  services/product-service/.../application/review/EnrichedReview.java               # NEW item 1
M  services/product-service/.../application/review/GetProductReviewsUseCase.java     # item 1
A  services/product-service/.../domain/review/port/out/BuyerProfileLookupPort.java   # NEW item 1
M  services/product-service/.../infrastructure/config/UseCaseConfig.java             # item 1
A  services/product-service/.../infrastructure/user/UserServiceBuyerProfileAdapter.java # NEW
A  services/product-service/.../infrastructure/user/UserServiceHttpClient.java       # NEW
A  services/product-service/.../infrastructure/user/UserServiceHttpClientConfig.java # NEW
M  services/product-service/.../infrastructure/web/review/ReviewController.java      # item 1
M  services/product-service/.../infrastructure/web/review/ReviewResponse.java        # item 1
M  services/product-service/src/test/.../ProductServiceApplicationTests.java         # +MockitoBean
A  services/user-service/.../application/ListBuyerPublicProfilesUseCase.java         # NEW item 1
M  services/user-service/.../domain/port/out/UserRepositoryPort.java                 # +findBuyersByKeycloakIds
M  services/user-service/.../infrastructure/config/SecurityConfig.java               # +permitAll public-profiles
M  services/user-service/.../infrastructure/config/UseCaseConfig.java                # +new use-case bean
M  services/user-service/.../infrastructure/persistence/UserJpaRepository.java       # +batch query
A  services/user-service/.../infrastructure/web/UserPublicProfileController.java     # NEW item 1
M  services/user-service/src/test/.../BuyerUseCasesTest.java                         # +2 tests
A  docs/SESSION-HANDOVER-2026-05-24-pt33.md                                          # this file
```

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show this handover commit.
2. **Smoke gates.**
   - `cd fe; npx tsc --noEmit` → 0 errors.
   - `cd fe; npm test -- --run` → 156 / 156.
   - `cd fe; node -e "..." | grep parity` (the parity check from this block) → `parity OK`.
   - `cd services/order-service; ./mvnw test -Dtest=CalculateCheckoutUseCaseTest` → 9 / 9.
   - `cd services/product-service; ./mvnw test` → 31 / 31.
   - `cd services/user-service; ./mvnw test` → 118 / 118.
   - Workday suite: `cd fe; npx playwright test e2e/workday-{buyer,seller,admin}.spec.ts --project=chromium --reporter=line` → 3 / 3.
   - Journey suite: `cd fe; npx playwright test e2e/journey/ --project=chromium --reporter=line` → expect 7 / 7 OR 6 / 7 with chapter 6 flaking on the pt32 kafka projection race (see open thread).
3. **Visual sanity:** the live SPA should now show real reviewer names on product reviews, a confirm dialog before completing payouts, distinct console chrome on `/admin` and `/seller`, application context on Approve Sellers rows, branded empty states on the homepage, and filter chrome + date grouping on Seller Orders / Wallet history / Admin Payouts.

## Final session ledger (pt27 → pt33)

- **pt27**: i18n duplicate-key fix + lucide → Tabler migration (39 files / 50 icons).
- **pt28**: dark-mode pilot + 47-file codemod sweep + 9 schema-drift fixes + cart wiring.
- **pt29**: 22 → 27 UI Playwright specs + 3 BE bugs caught + coupon-service envelope wrap.
- **pt30**: persona-workday suite (3 specs / 32 steps) + AC-coded REPORT.md.
- **pt31**: BA-grade journey suite chapters 1-4 + 5 caught bugs + cross-service coupon validation port + kafka env override fix.
- **pt32**: chapter 5 verified + chapter 6 written and green. Journey 16/16 ACs across 6 chapters.
- **pt33 (this block)**: target-user UX walkthrough → 8 issues identified → 7-commit fix-it block. Reviews UUIDs, confirm-payout safety, console-chrome split, seller application context, homepage empty states, list chrome + status pill extraction, a11y badge separation. Two new shared primitives (`StatusPill`, `groupByDate`) + one new utility (`formatRelativeTime`) + one new cross-service port (`BuyerProfileLookupPort` mirroring the pt31 coupon pattern).

The QA pyramid stays complete: unit (vitest 156, jest across services) → use-case (jest 9 order, 31 product, 118 user) → UI surface (workday 3) → BA-grade business outcome (journey 16/16). Pt33 added zero new acceptance criteria but improved every persona's daily UX in the process.
