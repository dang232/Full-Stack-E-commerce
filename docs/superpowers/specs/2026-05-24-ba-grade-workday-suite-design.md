# BA-Grade Persona Workday Suite — Design

**Date:** 2026-05-24
**Status:** Draft (awaiting approval)
**Owner:** dang232
**Related:** [`docs/superpowers/specs/2026-05-23-persona-workday-playwright-design.md`](2026-05-23-persona-workday-playwright-design.md), [`fe/e2e/workday-{buyer,seller,admin}.spec.ts`](../../../fe/e2e), [`docs/UI-QA-COVERAGE.md`](../../UI-QA-COVERAGE.md)

## Problem

The pt30 persona-workday suite (3 specs / 32 steps) proves the SPA renders, schemas parse, and click handlers fire. It does **not** prove the platform delivers business outcomes. From a BA / customer-acceptance lens, the gaps are:

- **No money flow.** Nobody pays. Nobody fulfills. Wallets never credit. The single business loop the platform exists to support is untested.
- **Seller is read-only.** 8 tabs, zero mutations. A real seller accepts orders, ships, replies — none of that runs.
- **Admin is read-only.** Approves nothing. Resolves nothing. The Coupons CRUD is the lone mutation; everything else "parses and is empty".
- **Cross-persona invisibility.** What a buyer does isn't observed by the seller. What the seller fulfills isn't observed by admin. The personas could be running against three independent platforms and the suite would still be green.
- **Coupon never applied.** Admin creates one. No buyer ever uses it. Discount math at checkout is unverified end-to-end.
- **Engineer-facing reports.** Step titles read "Sellers approval queue renders" instead of "Admin can see and approve a new seller's application". REPORT.md has no acceptance-criteria mapping, no business-risk framing, nothing a BA could hand to a stakeholder.

## Goal

Replace the three pt30 workday specs with a **single chained business journey** that walks one real customer outcome end-to-end, plus three persona-rooted "console day" specs that exercise real mutations on the surfaces a BA cares about. Each step is named for the business outcome it proves, and the per-persona REPORT.md gains an "Acceptance Criteria" header that BAs can lift directly into stakeholder updates.

## Non-Goals

- Not deleting the pt30 specs in the first commit — they stay during the transition. The BA-grade suite supersedes them once the gaps close, then the old ones are removed in a separate commit so the diff is reviewable.
- No payment-gateway sandbox flows. COD-only money loop, the same constraint pt30 had. Stripe / VNPay / PayPal sandbox runs need a separate design (gotcha #62 from pt28).
- No load-test framing. This is correctness coverage, not performance.
- No flaky-test heroics. Where Kafka projection lag would race a UI assertion, the convention from pt29 (assert on the synchronous toast / mutation success, not the projection-rendered row) still applies.

## The journey-shape change

Instead of "one persona walks their console", we test **one outcome** with the personas as collaborators. Each spec is a chapter; the chapters share state through the actual platform.

```
Chapter 1 (admin):  approves seller1 + creates a SAVE50 coupon
   ↓
Chapter 2 (buyer):  registers, shops, applies SAVE50 at checkout, places COD order, confirms discount
   ↓
Chapter 3 (seller): sees the new sub-order, accepts, ships, marks delivered
   ↓
Chapter 4 (buyer):  sees Delivered status, leaves a 5-star review
   ↓
Chapter 5 (seller): sees positive wallet balance, requests payout
   ↓
Chapter 6 (admin):  sees pending payout, completes it, sees seller's wallet drained
```

**State carried across chapters:** `coupon code`, `buyer email`, `order id`, `sub-order id`, `payout id`. Held in a JSON sidecar (`fe/e2e/evidence/journey/state.json`) regenerated each run; chapters refuse to start if the previous chapter didn't write the keys they need.

**Why chapters as separate specs, not one giant test:** workers + retries. A single chained test that fails at chapter 4 retries from chapter 1 (Playwright re-runs the full test) — wasteful and slow. As `test.describe.serial` files run in declared order with a single worker (already our config), each chapter can be retried independently. State sidecar keys what they need; missing keys → fail fast with "previous chapter must run first" message.

## Spec layout

```
fe/e2e/journey/
├── _journey-state.ts                     # state sidecar: load/save/require helpers
├── _journey-evidence.ts                  # extends _workday-evidence with acceptance-criteria support
├── 01-admin-onboards-the-marketplace.spec.ts
├── 02-buyer-discovers-and-orders.spec.ts
├── 03-seller-fulfills-the-order.spec.ts
├── 04-buyer-receives-and-reviews.spec.ts
├── 05-seller-cashes-out.spec.ts
└── 06-admin-closes-the-loop.spec.ts
```

The pt30 single-persona specs (`workday-{buyer,seller,admin}.spec.ts`) stay one more block as a regression net; they're removed in a final cleanup commit once the new suite is green.

### Chapter 1 — Admin onboards the marketplace
**File:** `01-admin-onboards-the-marketplace.spec.ts`
**Acceptance criteria:**
- AC-1.1 Admin can review a seller's pending application and approve them
- AC-1.2 An approved seller appears in the public sellers list within 30 s (projection lag-tolerant)
- AC-1.3 Admin can publish a percentage-discount coupon that is immediately redeemable

**Steps (named for outcomes, not clicks):**
1. Login as admin1 → admin console mounts
2. Open the pending sellers queue → at least one application visible (seeded fixture creates `seller_pending_<ts>`)
3. Approve the pending seller → success toast → seller leaves the queue
4. Public sellers list shows the new approved seller within 30 s
5. Open coupons → publish `SAVE50-<ts>` (50,000 ₫ off, valid 30 days)
6. Coupon row visible in the active coupons table

**State written:** `approvedSellerId`, `couponCode`.

### Chapter 2 — Buyer discovers and orders
**File:** `02-buyer-discovers-and-orders.spec.ts`
**Acceptance criteria:**
- AC-2.1 A new visitor can register and shop in under 90 s of browser time
- AC-2.2 A coupon code applied at checkout reduces the total by exactly the published discount
- AC-2.3 A placed COD order is visible in the buyer's order history within 30 s

**Steps:**
1. Land on home → register fresh `e2e_journey_<ts>@vnshop.local`
2. Search for an in-stock product → open detail
3. Add to cart, set quantity 2, verify line total
4. Open checkout → save default address
5. Enter coupon code from chapter 1 → discount line appears, total drops by 50,000 ₫
6. Place COD order → success toast with order id
7. Open `/orders` → new order present with the discounted total

**State written:** `buyerEmail`, `buyerPassword`, `productId`, `orderId`, `subOrderId` (resolved via API after place).

### Chapter 3 — Seller fulfills the order
**File:** `03-seller-fulfills-the-order.spec.ts`
**Acceptance criteria:**
- AC-3.1 A seller sees a buyer's new order in their queue within 30 s of placement
- AC-3.2 A seller can accept, ship (with tracking), and mark delivered
- AC-3.3 A delivered order credits the seller's wallet at the discounted (post-coupon) revenue

**Steps:**
1. Login as the seller approved in chapter 1
2. Pending orders queue shows chapter 2's order (poll up to 30 s)
3. Accept the sub-order → status flips to ACCEPTED
4. Ship the sub-order with tracking code `JRN-<ts>` → status flips to SHIPPED
5. Mark delivered → status flips to DELIVERED
6. Open Wallet tab → available balance ≥ chapter 2's discounted line total

### Chapter 4 — Buyer receives and reviews
**File:** `04-buyer-receives-and-reviews.spec.ts`
**Acceptance criteria:**
- AC-4.1 A buyer sees Delivered status when the seller marks delivery
- AC-4.2 A buyer can leave a star + text review on a delivered product
- AC-4.3 The review is visible on the public product page within 30 s

**Steps:**
1. Login as the buyer from chapter 2
2. Open `/orders` → status badge for chapter 2's order is Delivered
3. Click Review on the line item → 5-star + 30-char review → submit
4. Open `/product/{productId}` → the new review is visible (poll up to 30 s)

### Chapter 5 — Seller cashes out
**File:** `05-seller-cashes-out.spec.ts`
**Acceptance criteria:**
- AC-5.1 A seller with positive balance can request a payout
- AC-5.2 The payout shows up in admin's payout queue immediately

**Steps:**
1. Login as seller
2. Wallet → Withdraw enabled (balance > 0 from chapter 3)
3. Submit payout request for the full available balance
4. Payout history row appears with status PENDING

**State written:** `payoutId`.

### Chapter 6 — Admin closes the loop
**File:** `06-admin-closes-the-loop.spec.ts`
**Acceptance criteria:**
- AC-6.1 An admin sees the seller's payout request in the queue
- AC-6.2 An admin can mark a payout completed
- AC-6.3 The seller's available balance drops to 0 after admin completes the payout

**Steps:**
1. Login as admin1
2. Payouts tab → row for chapter 5's payout, status PENDING
3. Mark complete → status flips to COMPLETED
4. (Cross-persona check) Login as seller → wallet shows 0 balance, payout history shows the COMPLETED row

## Helper additions (`_journey-evidence.ts`)

Extends `_workday-evidence.ts` with three new exports:

```ts
// Same step() pattern, but also tags each row with an AC code so REPORT.md
// can group by acceptance criterion in the stakeholder section.
export async function bizStep(
  page: Page,
  persona: Persona,
  acCode: string,        // "AC-2.2"
  outcome: string,       // "Coupon reduces total by exactly 50,000 ₫"
  fn: () => Promise<void>,
): Promise<void>;

// Read/write the cross-chapter state sidecar.
export async function readJourneyState(): Promise<JourneyState>;
export async function requireJourneyState(keys: (keyof JourneyState)[]): Promise<JourneyState>;
export async function writeJourneyState(patch: Partial<JourneyState>): Promise<void>;
```

`requireJourneyState` is what gates a chapter on its predecessor — if chapter 3 calls `requireJourneyState(["orderId", "subOrderId"])` and the file isn't there, the spec fails with `"Chapter 3 requires Chapter 2 to have run first; missing key(s): orderId, subOrderId"`. This is the explicit fail-fast contract.

## REPORT.md upgrade

Each persona REPORT.md gets two new sections at the top, before the existing per-step block:

```markdown
# Buyer journey — Discovery → Order

**Verdict:** PASS
**Run time:** 1m 47s
**Generated:** 2026-05-24T...

## Business outcomes verified

| AC | Outcome | Status |
|---|---|---|
| AC-2.1 | A new visitor can register and shop in under 90 s | PASS |
| AC-2.2 | Coupon SAVE50-... reduced total by exactly 50,000 ₫ | PASS |
| AC-2.3 | Placed COD order visible in /orders within 30 s | PASS |

## Stakeholder summary

The new-buyer journey is healthy. A registered guest applied a published
coupon at checkout and the platform recorded the discounted total against
the placed order. No business-rule regressions detected this run.

## Steps (engineer view)

[existing per-step rows + screenshots — unchanged]
```

A new top-level `fe/e2e/evidence/JOURNEY-REPORT.md` aggregates all six chapters with the journey-wide PASS/FAIL roll-up — that's the BA-facing artifact.

## Data Flow

```
[seed]              [browser]               [shared platform]            [evidence]
seed pending ──► chapter 1 admin            │
seller via API   approves, creates coupon ──┼─► coupon row + approved
                                            │   seller in DB
                ◄───── state.json ──────────┤
                                            │
                 chapter 2 buyer            │
                 registers, applies coupon ─┼─► order_summary row,
                 places COD order           │   coupon usage row
                ◄───── state.json ──────────┤
                                            │
                 chapter 3 seller           │
                 accept → ship → deliver ───┼─► sub-order status flips,
                                            │   wallet credit projected
                ◄───── state.json ──────────┤
                 ... chapters 4-6
```

## Error Handling

- **Previous chapter didn't run:** fail fast with the missing-keys error described above. The stakeholder REPORT.md picks it up as `BLOCKED — Chapter X did not run`, distinguishing it from a real outcome failure.
- **Projection lag (Kafka order_summary, wallet credit):** poll up to 30 s with the existing `expect.poll` pattern. If the projection genuinely doesn't catch up, the failure shows the AC code so a BA reading the report can tell at a glance whether it's "the platform broke" vs "a UI selector broke".
- **Cross-persona session bleed:** every chapter starts with `await page.context().clearCookies()` before login to prevent the previous chapter's session leaking through.
- **Seeded pending seller** (chapter 1 needs one): seed via `/auth/register` + manual SELLER role assignment in a `beforeAll` API call. Convention: `e2e_journey_seller_pending_<ts>@vnshop.local`. Approved each run; cleaned up by being a fresh user each time, the same way buyer is.

## Testing Approach

- **Live-stack run:** the journey runs against a healthy live stack (gateway 200, FE 200) like the existing workday suite. Same `--project=chromium --reporter=line` invocation.
- **Per-chapter time budget:** 3 min each. Total journey ~10 min. Slower than pt30's 50 s for one good reason — it actually exercises business flows.
- **CI lane:** runs nightly, NOT on every PR. PR lane keeps the surface suite (76 / 76 in 2.5 min) + the existing pt30 workday (3 / 3 in 50 s). The full journey is a 10-min commitment that adds nightly stakeholder confidence without slowing the dev loop.
- **Failure isolation:** when chapter 4 fails, the team gets `JOURNEY-REPORT.md` showing exactly which AC broke and at what step. No more "the workday spec failed" with 32 lines of stack trace.

## Files Created / Modified

```
A  fe/e2e/journey/_journey-evidence.ts            # bizStep + state helpers
A  fe/e2e/journey/_journey-state.ts               # JourneyState type + IO
A  fe/e2e/journey/01-admin-onboards-the-marketplace.spec.ts
A  fe/e2e/journey/02-buyer-discovers-and-orders.spec.ts
A  fe/e2e/journey/03-seller-fulfills-the-order.spec.ts
A  fe/e2e/journey/04-buyer-receives-and-reviews.spec.ts
A  fe/e2e/journey/05-seller-cashes-out.spec.ts
A  fe/e2e/journey/06-admin-closes-the-loop.spec.ts
A  fe/e2e/evidence/journey/                       # screenshots + per-chapter REPORT.md (committed)
A  fe/e2e/evidence/JOURNEY-REPORT.md              # top-level stakeholder roll-up (committed)
M  fe/e2e/_workday-evidence.ts                    # extract bizStep into the existing helper instead of duplicating
M  fe/.gitignore                                  # already ignores **/evidence/**/*.webm; verify journey videos covered
M  docs/UI-QA-COVERAGE.md                         # add journey section + run command + AC matrix
M  fe/playwright.config.ts                        # NO change — sequential workers + retries already correct

# Cleanup commit (separate, after journey is green for a week):
D  fe/e2e/workday-buyer.spec.ts                   # superseded by chapters 2 + 4
D  fe/e2e/workday-seller.spec.ts                  # superseded by chapters 3 + 5
D  fe/e2e/workday-admin.spec.ts                   # superseded by chapters 1 + 6
```

## Open Questions

1. **Stakeholder-summary copy** in REPORT.md — should it be auto-generated from the AC pass rate, or hand-curated per chapter? Auto is honest but bland; curated is BA-friendly but rots. **Proposal:** template-generated with the AC list rendered in. BA edits the template once, not every run.
2. **What's the seller-finance-service end state for the wallet credit timing?** Chapter 3 polls 30 s. If the credit projection is slower than that under load, AC-3.3 flakes. Confirm worst-case projection lag with the seller-finance team before committing to 30 s.
3. **Pending seller seeding** — call `POST /auth/register` + admin's role-assign endpoint each run? Or pre-seed via realm import (faster but couples journey runs to a realm rebuild)? **Proposal:** runtime API seeding — same approach as buyer registration, no realm coupling.
4. **AC numbering scheme** — `AC-{chapter}.{ordinal}` or `BA-{epic}-{ordinal}` to align with whatever the BA team uses in tickets? Default to chapter.ordinal for self-containment; rename later if the BA team has a tracker convention.

## Risks

| Risk | Mitigation |
|---|---|
| 10-minute journey discourages local dev runs | Keep PR lane unchanged; journey is nightly + on-demand. README documents the local invocation for when devs want to verify a cross-cutting change. |
| Chapter 1 seeds a seller that lingers in the realm if chapter 1 fails halfway | Fresh-per-run namespace (`e2e_journey_seller_pending_<ts>`); a periodic admin cleanup script (TODO, separate task) sweeps anything older than 7 days. Same risk model as the existing buyer registration spec. |
| Cross-persona state in a JSON file feels hacky compared to a fixture | It's hacky on purpose — the alternative is a Playwright fixture spanning multiple spec files, which Playwright doesn't natively support. JSON sidecar is explicit, debuggable, and survives test interrupts. |
| AC matrix in REPORT.md drifts from the implementation | Each `bizStep` declares its AC code at the call site; REPORT generator reads from the actual run state. No separate AC list to maintain — the test file IS the source of truth. |
| Coupon discount math regression slips past us | AC-2.2 asserts the EXACT pre-coupon total minus discount equals the rendered post-coupon total. Not a soft "discount applied" check. |
| Wallet credit timing flake | If the credit doesn't land in 30 s, AC-3.3 fails with a clear "wallet credit projection slower than expected" message. Project owner + workday timing become the lever, not a stronger sleep. |
