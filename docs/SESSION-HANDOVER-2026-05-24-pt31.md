# Session handover — 2026-05-24 (pt31: BA-grade journey suite + caught BE bug)

**Last commit (HEAD):** `<set-after-this-commit>` (`docs(pt31): final handover — chapters 1-3 green, 3 caught bugs fixed`)
**Commits pushed since pt30 HEAD `01fdd633`:** 14.

**Gates (live stack):**
- FE typecheck: 0 errors clean.
- FE vitest: 156 / 156.
- order-service jest: `CalculateCheckoutUseCaseTest` 9 / 9 (5 prior + 4 new) plus all other affected tests green.
- Playwright workday suite (pt30 carry-over): 3 / 3, unchanged.
- **Playwright journey suite (this block): 4 / 4 in ~21 s. JOURNEY-REPORT verdict: PASS, 8 / 8 ACs across chapters 1-3.**

## Bugs caught + fixed this block

The journey suite caught three real customer-visible defects, each surfaced as an AC FAIL with full receipts in JOURNEY-REPORT.md, then closed end-to-end:

1. **AC-2.2 — Coupon discount math broken at /checkout/calculate.**
   Root cause: order-service's `CalculateCheckoutUseCase.java:64` hard-coded `discount = NO_DISCOUNT`, ignoring the FE's `couponCode`. Worse, the platform has TWO coupon stores (order-service local DB vs coupon-service authoritative). Fix: built a cross-service `CouponValidationPort` + HTTP adapter in order-service that calls coupon-service's `/checkout/validate-coupon`. Failure semantics: any 4xx/5xx → silent zero discount (preview stays interactive; place-order-time apply surfaces real errors).

2. **AC-3.1 (initial) → AC-3.2 — Seller can't ship orders after accepting them.**
   Root cause: `/seller/orders/pending` filtered by `PENDING_ACCEPTANCE` only, but accept transitions the row to `ACCEPTED`. The row left the queue entirely after accept and the FE's Ship button never appeared. Fix: `ListPendingOrdersUseCase` now queries an ACTIONABLE list (PENDING_ACCEPTANCE + ACCEPTED) via a new `findBySellerIdAndFulfillmentStatusIn` repo method.

3. **AC-3.1 (cascading) — Seller's Orders tab crashed on legacy null ward data.**
   Root cause: `addressSchema.ward` was `z.string().optional()` which accepts `undefined` but rejects literal `null`. Old `order_summary` rows persist with `ward: null` and the entire seller queue rendered "Invalid input" (every seller blocked from seeing any orders, not just the ones with null ward — Zod fails the whole list). Fix: `.nullable().optional()` on `ward / district / phone`. This was a true silent-killer regression that was visible in the screenshot but invisible until a journey forced it into view.

The three cascading discoveries are the BA-grade journey working as designed — surface a customer-impact failure, fix the root cause, watch the next layer of consequence appear in the report. None of these would have shown up in pt30's "tour the console" specs (those use a fresh seeded buyer with valid data; legacy null fields and accumulating queue state only appear in journeys that span personas and multiple runs).

## What's in the journey suite

```
fe/e2e/journey/
├── _journey-state.ts                              # state sidecar (load/save/require)
├── _journey-evidence.ts                           # bizStep + AC-coded REPORT.md generator
├── 01-admin-onboards-the-marketplace.spec.ts      # AC-1.1, AC-1.2, AC-1.3 — all PASS
├── 02-buyer-discovers-and-orders.spec.ts          # AC-2.1, AC-2.2, AC-2.3 — all PASS
├── 03-seller-fulfills-the-order.spec.ts           # AC-3.1, AC-3.2 — all PASS
└── 99-aggregate-journey-report.spec.ts            # writes fe/e2e/evidence/JOURNEY-REPORT.md
```

JOURNEY-REPORT.md verdict: PASS, 8 / 8 ACs across 3 chapters. 3 of 6 chapters complete.

## Open thread for next session — chapters 4-6

**Chapter 4 — Buyer receives and reviews (AC-4.1, AC-4.2, AC-4.3)**

A scoping note from chapter 3's research: `FulfillmentStatus` enum has **no DELIVERED state** (PENDING_ACCEPTANCE, ACCEPTED, PACKED, SHIPPED, REJECTED, CANCELLED). The platform's order domain ends at SHIPPED; "delivered" lives in shipping-service's `TrackingStatus` and is presumably surfaced to buyers via tracking events, not via the order's fulfillmentStatus. AC-4.1 needs to be reframed:

- Original design: "Buyer sees Delivered status when seller marks delivery."
- Reality: There's no seller-side mark-as-delivered action. Delivery is reported by the carrier asynchronously.
- Reframed AC-4.1: "Buyer's order page shows the tracking-derived status (Shipped → Delivered) and offers a Review CTA on the line item."

The Review CTA may gate on shipping-service's tracking status reaching DELIVERED. Alternative: the OrdersPage may render Review on any SHIPPED row. Read `fe/src/app/pages/OrdersPage.tsx` to confirm before committing chapter 4's spec.

**Chapter 5 — Seller cashes out (AC-5.1, AC-5.2):** wallet credit projection lag is a known issue (seller-finance-service via Kafka). The chapter polls until the balance is non-zero, then submits a payout request. Endpoints already exist (`POST /seller/payouts`).

**Chapter 6 — Admin closes the loop (AC-6.1, AC-6.2, AC-6.3):** admin sees the payout, marks complete, seller's wallet drops to 0. Endpoints already exist (`POST /admin/payouts/{id}/complete`).

**Lower-priority follow-ups:**
- Avatar upload feature (design doc filed; user-service ObjectStoragePort + MinIO bucket + FE camera-button wire-up).
- PayPal capture round-trip (manual gateway test, deferred since pt22).
- Shipping tracking ownership check (deferred since pt22).
- VNPay/MoMo `redirectUrl` missing from PaymentResponse (gotcha #62 from pt28).

## Final session ledger (pt27 → pt31)

- **pt27**: i18n duplicate-key fix + Tabler migration.
- **pt28**: dark-mode pilot + 47-file codemod sweep + 9 schema-drift fixes + cart wiring.
- **pt29**: 27 UI Playwright specs / 76 scenarios + 3 BE bugs caught + coupon-service envelope wrap.
- **pt30**: persona-workday suite (3 specs / 32 steps) + AC-coded REPORT.md.
- **pt31 (this block)**: BA-style review of pt30 → BA-grade journey suite chapters 1-3 (8/8 ACs PASS) + 3 caught customer-impact bugs all fixed end-to-end (coupon discount math, seller queue filter, schema null tolerance) + cross-service coupon validation port + avatar wiring fix + 2 design docs (avatar upload + BA-grade workday).

The QA pyramid now has a fourth layer that earns its place: **business-outcome journey** with AC codes, stakeholder-facing PASS/FAIL, and a track record of catching bugs the surface specs missed. Three real defects caught + three real defects fixed, all visible to a BA reading JOURNEY-REPORT.md.

**Gates (live stack):**
- FE typecheck: 0 errors clean.
- FE vitest: 156 / 156 unchanged.
- order-service jest: `CalculateCheckoutUseCaseTest` 8 / 8 (5 prior + 3 new coupon-aware paths).
- Playwright workday suite (pt30 carry-over): 3 / 3 in ~50 s, unchanged.
- **Playwright journey suite (new this block): 2 chapters + aggregator wired.**
  - Chapter 1: 5 / 5 step PASS, AC-1.1 / AC-1.2 / AC-1.3 verified.
  - Chapter 2: AC-2.1 PASS, **AC-2.2 FAIL (intentional, see below)**, AC-2.3 NOT_RUN.
  - JOURNEY-REPORT.md verdict: FAIL with AC-2.2 named on the front.

This block was the BA-style review pass on pt30's work plus an avatar bug fix. The user pushed back: pt30's "tour the console" specs are technical, not business. That feedback turned into two designs (avatar upload via MinIO; BA-grade workday rewrite) and the first two chapters of the journey suite.

The journey suite caught a real customer-visible defect that pt30's surface tests missed.

## What landed this session

### Avatar bug (`9830a9f5`)
`vnshop-context.tsx` hard-coded `avatar: ""` and `phone: ""` so even users with `avatarUrl` saved on `/users/me` (BE has had the field since pt28) showed the initial-letter placeholder forever. The provider now mounts `useProfile()`, gates on `auth.ready && auth.authenticated`, and pulls `avatar` + `phone` from the BuyerProfile. The avatar UPLOAD path (camera button on /profile is still a no-op) needs object-storage wiring — design doc filed below.

### Designs (`33510b0f`)
- `docs/superpowers/specs/2026-05-24-avatar-upload-object-storage-design.md` — mirrors product-service's ObjectStoragePort pattern. New `vnshop-avatars` bucket in the existing MinIO bootstrap. Two-phase upload (presigned PUT → activate). FE camera button + `useAvatarUpload` mutation. Implementation pending.
- `docs/superpowers/specs/2026-05-24-ba-grade-workday-suite-design.md` — six chapter specs that chain ONE business outcome end-to-end across buyer / seller / admin. Each step carries an AC code. Per-chapter REPORT.md + top-level JOURNEY-REPORT.md as the BA-facing artifact.

### BA-grade journey suite, chapters 1+2 (`2fbcbef8`)
```
fe/e2e/journey/
├── _journey-state.ts                              # state sidecar (load/save/require)
├── _journey-evidence.ts                           # bizStep + AC-coded REPORT.md generator
├── 01-admin-onboards-the-marketplace.spec.ts      # AC-1.1, AC-1.2, AC-1.3 — all PASS
├── 02-buyer-discovers-and-orders.spec.ts          # AC-2.1 PASS, AC-2.2 FAIL (caught bug), AC-2.3 NOT_RUN
└── 99-aggregate-journey-report.spec.ts            # writes fe/e2e/evidence/JOURNEY-REPORT.md
```

Helper highlights:
- `bizStep(page, chapterId, acCode, outcome, fn)` wraps `test.step()` with a numbered fullPage screenshot, AC tag, and PASS/FAIL/BLOCKED status. The BLOCKED status fires when a chapter's predecessor didn't run (`requireJourneyState(["couponCode", ...])` throws a recognisable error message).
- `report.json` machine-readable sidecar per chapter so the aggregator works regardless of run order.
- Reports written from the test body's `finally` (not just `afterAll`) so a failing chapter still produces its REPORT.md — critical for the BA-grade artifact.
- Manual tracing (start in test body, stop in `finally`) — same pattern from pt30 to avoid the trace.zip / afterAll race.

### Caught BE bug — AC-2.2 FAIL
**Discovered while running chapter 2 against the live stack:**

A buyer applied the coupon admin published in chapter 1. The "Applied: <code>" badge rendered (FE `appliedCoupon` state set optimistically), but the order total stayed at pre-coupon. Root cause: `services/order-service/.../CalculateCheckoutUseCase.java:64` hard-coded `BigDecimal discount = NO_DISCOUNT;` and ignored the FE's `couponCode` parameter entirely.

This is **exactly** what AC-2.2 was designed to catch. pt30's "tour the console" specs would not have surfaced it — the admin coupon-create test only verifies the row appears in the table, not that a buyer can actually redeem it.

### BE coupon wiring (`25452745`)
Wired `CouponValidator` into `CalculateCheckoutUseCase`. New 3-arg overload `calculate(lineItems, couponCode, userId)`. Three new unit tests: happy-path discount, invalid-code-silently-zero, blank-as-absent. Order-service `CalculateCheckoutUseCaseTest` 8 / 8.

**Architectural caveat — caught while verifying the live fix:**

The platform has two coupon stores. `coupon-service` owns admin coupon CRUD + `/checkout/{validate,apply}-coupon`. `order-service` has its own local `CouponValidator` backed by a separate DB. The validator wired this session queries order-service's DB only — so coupons admin publishes via the FE admin UI (which lands in coupon-service) won't be found here.

**The fix is locally correct but the live behavior still needs a cross-service port.** AC-2.2 stays red until that port lands. The red state in JOURNEY-REPORT is the BA-facing tracking artifact for the open gap.

This caveat is documented in CalculateCheckoutUseCase's javadoc, the chapter 2 spec header, and the commit message — anyone touching the area gets the receipts.

## Gotchas this block (extends pt30 list)

**76. AC code tagged at every `bizStep` is the discipline.** The journey helper rolls AC statuses up by AC code, NOT by step. If you mark step 7 as `AC-2.2` and then forget to add `AC-2.2` to the chapter's declared `acceptanceCriteria` list, REPORT.md will silently drop the result. Conversely an AC declared but never tagged in any step shows as `NOT_RUN` — that's how the report distinguishes "we didn't test it" from "it's broken". Both are useful, both are intentional.

**77. Two coupon stores live in the platform.** order-service has `CouponRepository` + `CouponValidator` (legacy place-order path). coupon-service has its own. Admin coupon CRUD (FE admin UI) writes to coupon-service. /checkout/apply-coupon goes through coupon-service. /checkout/calculate goes through order-service. The two DBs do not share rows. A cross-service port is the proper fix; in the meantime a buyer can only redeem coupons that exist in BOTH stores (none, currently — admin only writes to one).

**78. `test.use({ trace: "on" })` was the wrong primitive for the journey suite too.** Same lesson as pt30 gotcha #70: Playwright finalises that trace during BrowserContext close, AFTER afterAll, so any sidecar work in the test body races against an empty zip. The journey helper drives `tracing.start({snapshots: true, sources: true})` manually inside the test body and `tracing.stop({path: dest})` in the `finally`. trace.zip is on-disk before afterAll touches anything.

**79. Failing tests skip Playwright's `afterAll` cleanly.** Or rather: they run, but if the test body crashed at certain points, hooks may run with empty in-memory state. The fix is to write reports from the test body's `finally` block too. The afterAll path becomes idempotent belt-and-braces. Without this, a chapter that fails at step 3 produces no REPORT.md, which defeats the whole point of the BA-grade artifact (red is more important to the BA than green).

**80. UI buttons with badge counts break strict regex anchors.** Admin Sellers tab button is named "Approve Sellers 1" (label + pending count badge), not "Approve Sellers". `/^Approve Sellers$/i` doesn't match — drop the `$` anchor, match the prefix.

**81. Multiple Approve buttons in a list need disambiguation.** Pending sellers admin list shows one Approve button per row. Strict mode rejects `getByRole("button", {name: "Approve"})` when more than one exists. Locator strategy that worked: `.divide-y > div` row container with `hasText: "Journey Pending Shop <stamp>"`, then `.first()` Approve inside. Re-run-safe because each run's stamp is unique.

## Files touched this block

```
A  docs/superpowers/specs/2026-05-24-avatar-upload-object-storage-design.md
A  docs/superpowers/specs/2026-05-24-ba-grade-workday-suite-design.md
M  fe/src/app/components/vnshop-context.tsx                                          # avatar + phone wiring
A  fe/e2e/journey/_journey-state.ts                                                  # cross-chapter state sidecar
A  fe/e2e/journey/_journey-evidence.ts                                               # bizStep + AC-coded reports
A  fe/e2e/journey/01-admin-onboards-the-marketplace.spec.ts
A  fe/e2e/journey/02-buyer-discovers-and-orders.spec.ts
A  fe/e2e/journey/99-aggregate-journey-report.spec.ts
A  fe/e2e/evidence/JOURNEY-REPORT.md                                                 # generated, top-level BA artifact
A  fe/e2e/evidence/journey/                                                          # screenshots + per-chapter REPORT.md
M  services/order-service/.../CalculateCheckoutUseCase.java                          # honour couponCode
M  services/order-service/.../web/CheckoutController.java                            # pass couponCode + userId
M  services/order-service/.../infrastructure/config/UseCaseConfig.java               # inject CouponValidator
M  services/order-service/src/test/.../CalculateCheckoutUseCaseTest.java             # 3 new coupon-aware tests
A  docs/SESSION-HANDOVER-2026-05-24-pt31.md                                          # this file
```

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show this handover commit.
2. **Smoke gates.**
   - `cd fe && npx tsc --noEmit` → 0 errors.
   - `cd fe && npm test -- --run` → 156 / 156.
   - `cd services/order-service && ./mvnw test -Dtest=CalculateCheckoutUseCaseTest` → 8 / 8.
   - Workday suite (pt30): `cd fe && npx playwright test e2e/workday-{buyer,seller,admin}.spec.ts --project=chromium --reporter=line` → 3 / 3.
   - Journey suite (new): `cd fe && npx playwright test e2e/journey/01-* e2e/journey/02-* e2e/journey/99-* --project=chromium --reporter=line` → expect 2 / 3 with chapter 2 FAIL on AC-2.2 until the cross-service coupon port lands.
3. **Read `fe/e2e/evidence/JOURNEY-REPORT.md`** — that's the BA-facing artifact. Top of the file shows the FAIL verdict + the failing AC code.

## What's still open

**Highest priority — chapters 3-6 of the journey:**
1. Chapter 3 (seller fulfills accept → ship → deliver, wallet credits).
2. Chapter 4 (buyer reviews delivered product, review visible on public page).
3. Chapter 5 (seller payout request).
4. Chapter 6 (admin completes payout, seller wallet → 0).

**Carryover from pt29/pt30:**
**Lower priority:**
5. **Avatar upload feature.** Implementation plan in the design doc; user-service ObjectStoragePort + MinIO bucket + FE camera-button wire-up.
6. PayPal capture round-trip (manual gateway test, deferred since pt22).
5. Shipping tracking ownership check (deferred since pt22).
6. VNPay/MoMo `redirectUrl` missing from PaymentResponse (gotcha #62 from pt28).
7. Out-of-scope-for-the-spec UI coverage gaps (admin dispute resolve modal, admin coupon update, profile address full CRUD beyond add, order detail modals, gateway payment forms, WebSocket message delivery).

## Final session ledger (pt27 → pt31)

- **pt27**: i18n duplicate-key fix + lucide → Tabler migration (39 files / 50 icons).
- **pt28**: dark-mode pilot + 47-file codemod sweep + 9 schema-drift fixes + cart wiring + product-service variants[] adapter.
- **pt29**: 22 → 27 UI Playwright specs / 46 → 76 scenarios + 3 real bugs caught + coupon-service envelope wrap + dialog wire-shape fix + design doc for the persona-workday suite.
- **pt30**: persona-workday suite (3 specs / 32 steps) + evidence helper + per-persona REPORT.md + screenshots + traces + DRY refactor (loginAsSeededUser, logoutViaUserMenu).
- **pt31 (this block)**: BA-style review of pt30 → two designs (avatar upload, BA-grade workday) → avatar wiring fix → journey suite chapters 1+2+aggregator → caught a real coupon-discount BE bug AC-2.2 was designed to catch → traced the bug to the platform's two-coupon-store architecture (order-service local DB vs coupon-service authoritative) → built cross-service `CouponValidationPort` + HTTP adapter so order-service consults coupon-service for /checkout/calculate previews → closed AC-2.2 end-to-end with a Total-row reader fix in the test helper. Journey 3 / 3, JOURNEY-REPORT verdict: PASS, 6 / 6 ACs.

**The journey suite caught a customer-impact bug, watched it through diagnosis, drove the fix across services, and confirmed closure. That's the BA-grade artifact working as designed — the value pt30's "tour the console" specs could not deliver.**

## Open thread for the next session — chapters 3-6

Chapters 1-2 are green and prove the helper / state-sidecar / AC-coded report pattern. The remaining four chapters compose the rest of the business journey:

- **Chapter 3 (seller fulfills):** seller logs in, sees the buyer's new order in the pending queue, accepts, ships with tracking, marks delivered. Wallet credits the discounted line total. AC-3.1 / AC-3.2 / AC-3.3.
- **Chapter 4 (buyer reviews):** buyer sees Delivered status, leaves a 5-star review, review appears on public product page. AC-4.1 / AC-4.2 / AC-4.3.
- **Chapter 5 (seller cashes out):** seller requests payout against the credited balance, payout shows up in admin's queue. AC-5.1 / AC-5.2.
- **Chapter 6 (admin closes loop):** admin completes the payout, seller's wallet drops to 0. AC-6.1 / AC-6.2 / AC-6.3.

Each chapter follows the same pattern: `requireJourneyState([keys])` to fail BLOCKED if predecessor didn't run, `bizStep(page, chapterId, acCode, outcome, fn)` for each step, write next chapter's keys via `writeJourneyState`. Helper is done; the work is the spec body.

Reference for the BE endpoints each chapter needs:
- `PUT /seller/orders/{subOrderId}/{accept,ship}` — seller fulfilment
- `POST /reviews` — buyer review (reviewController in product-service)
- `POST /seller/payouts` — seller request payout
- `POST /admin/payouts/{payoutId}/complete` — admin closes loop
