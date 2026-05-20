# Session handover — 2026-05-21 (pt18: dead /admin/reviews controller removed)

**Last commit (HEAD):** `271624f5` (`refactor(review): delete dead AdminReviewController + ModerateReviewUseCase`)
**Commits since pt17 HEAD `80fcc03a`:** 1.

**Gates:**
- review-service: 6/6 (unchanged after deletion). All 12 BE services still green.
- Playwright: 13/13 in `day-simulation.spec.ts`.
- FE typecheck: 0. Vitest: 143/143.

This block closed one of the two pt17-deferred cleanliness items: the dead `/admin/reviews` controller in review-service. The dispute `resolvedBy` log-integrity gap remains deferred.

## Commit this block

| # | Commit | What |
|---|---|---|
| 1 | `271624f5` | refactor(review): delete dead AdminReviewController + ModerateReviewUseCase |

## What shipped

### `271624f5` — Dead `/admin/reviews` controller removed from review-service

The pt17 audit flagged that `/admin/reviews/**` was registered in both product-service and review-service. Gateway `RouteConfig.java:204` routes it to product-service exclusively, leaving review-service's `AdminReviewController` unreachable. Pre-deletion trace confirmed:

- `ModerateReviewUseCase`: one caller (`AdminReviewController`), one bean wiring (`UseCaseConfig`). No tests, no other consumers.
- `ReviewRepositoryPort.findByStatus` and `ReviewRepositoryPort.moderate`: only `ModerateReviewUseCase` used them. Both safely removable.

Removed:
- `AdminReviewController.java`
- `ModerateReviewUseCase.java`
- `findByStatus` + `moderate` from the port, JPA impl, and Spring Data interface
- The matching stub overrides in the test-side fake repository (`ReviewImageUploadServiceTest.FakeReviewRepository`)

Net: 7 files changed, 102 deletions, zero insertions.

### What stays

review-service itself (legacy compose profile, k8s manifests in dev/staging/prod). Decommissioning the whole service is an infra decision outside this audit's mandate. The remaining gateway-unreachable controllers (`ReviewController`, `QuestionController`, `ReviewImageUploadController`) are kept because their canonical copies in product-service follow identical shapes — preserving parity simplifies any future decision to actually decommission review-service.

## Operational gotchas (additions)

45. **Dead-code in legacy services compounds the audit cost.** Pt15's `893101ed` shipped a buyerId-impersonation fix on review-service's `POST /reviews` and `POST /questions`. Those endpoints are also gateway-unreachable. The fix was correct (and parallel to product-service's canonical fix) but if the review-service controllers had drifted from product-service in any other way, an audit could miss real holes by assuming the deprecated path was just a duplicate of the live one. **When a service is on a deprecated/legacy profile, every controller in it should be reviewed for dead-code status as a separate audit pass before fixing security findings on it.** Otherwise audit effort is being spent on code that nobody will reach.

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `271624f5`.
2. **Smoke the suite.** `cd fe && npx playwright test e2e/day-simulation.spec.ts --project=chromium --reporter=line` → 13/13. The pt15 admin coupon-CRUD test exercises an admin flow that the gateway routes correctly; if `/admin/reviews/**` were broken by this deletion, day-simulation's admin suite would surface it (it doesn't).

## What's still open

**Genuinely open code work:**
- **Dispute `resolvedBy` log-integrity gap.** Add `resolvedBy: UUID` to `Dispute` domain object, stamp from JWT in `AdminDisputeController.resolve()`, write a Flyway migration. ~30min. Low priority — pure audit-log improvement, not security.
- **OneDrive hydration durability.** `services/*/src/` re-stubs on idle. Either pin those directories in OneDrive ("Always keep on this device") or extend `fe/scripts/hydrate-e2e.mjs` into a top-level `npm run hydrate` so future container rebuilds don't fail mid-flight. ~1hr.

**Genuinely open operational work:**
- **PayPal capture round-trip.** Last unproven payment path. Manual browser test, must run while logged in as the buyer who owns the payment.

## Resume hint

The pt12 → pt18 audit + cleanup arc is **complete from a security perspective**. 14 authorization findings closed across 6 services, durable regression gates in day-simulation, dead-code surface area in review-service trimmed.

Pick one:
- **PayPal capture round-trip** — needs human at the browser.
- **Dispute `resolvedBy`** — last code-side cleanliness item.
- **OneDrive hydration script** — durable engineering improvement; pays back across all future TS rebuilds.
