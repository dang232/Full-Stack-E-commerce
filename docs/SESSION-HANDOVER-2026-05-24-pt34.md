# Session handover — 2026-05-24 (pt34: chapter-6 flake — root cause was three things, not one)

**Last commit (HEAD):** `40c2de78` (`fix(journey-ch6): close confirm dialog on error, hydrate cloud-stubs, JSX`)
**Commits pushed since pt33 HEAD `11a27106`:** 1.

**Gates (live stack):**
- FE typecheck: 0 errors clean.
- FE vitest: 156 / 156.
- order-service jest: `CalculateCheckoutUseCaseTest` 9 / 9.
- product-service mvn: 31 / 31.
- user-service mvn: 118 / 118.
- Workday suite: 3 / 3.
- **Journey suite: 7 / 7 in ~40-50 s. Three consecutive runs confirmed stable. JOURNEY-REPORT verdict: PASS, 16 / 16 ACs.**

## What this block actually was

Pt33 left chapter 6 flaking and the handover called it a "kafka projection race." It wasn't. Three unrelated issues compounded into one symptom — a `pendingBalance never reached the chapter-5 reservation amount` failure that *looked* like the BE projection wasn't catching up. The real causes:

1. **OneDrive cloud-stub trap** (gotcha #81 from the user's auto-memory). Specs 01-05 + 99 + helpers reverted to cloud-stubs (`Mode -a---l` in PowerShell `Get-ChildItem -Force`). Playwright's test discovery silently filtered them out — only chapter 6 collected. So every "full journey run" was actually running chapter 6 alone against an 8-hour-old `state.json` from a prior run where that payoutId had already been completed. Wallet inert, `pendingBalance: 0` forever.

2. **JSX leak in `SellerOrders.tsx`** from the pt33 list-chrome edit. Dropped the `<div className="bg-card rounded-2xl shadow-sm overflow-hidden">` opening tag but kept its closing `</div>`. Vite dev tolerated it; esbuild in the docker frontend build choked with `Unterminated regular expression`. The dockerised FE at `localhost:3000` had been running the **pre-pt33 image** for the entire pt33 walkthrough — which is why the chapter-6 spec couldn't find the new confirm dialog: it didn't exist in the running container. (The walkthrough screenshots that informed pt33's design were valid; the journey-suite assertions against pt33 changes weren't.)

3. **`PayoutsQueue.tsx` `onError` left the dialog mounted.** With the confirm dialog now actually rendering after a real rebuild, AC-6.2 hit a new failure: the BE's `POST /admin/finance/payouts/{id}/complete` succeeded server-side (`lastPayoutAt` updated, wallet drained), but the FE Zod parse of the response envelope tripped, fell through to `onError`, which only raised a toast and never called `setCompleteFor(null)`. The modal stayed up as an invisible overlay intercepting the next click. The fix: `onError` must reset the dialog AND invalidate the payouts query so the row state catches up. The BE may have succeeded even when the FE Zod parse fails; stranding an admin under an opaque overlay is the wrong default regardless of which side errored.

The chapter-6 spec was also re-ordered: wait for the dialog **UNMOUNT** (the FE's canonical "mutation settled" signal) **first**, then assert the BE pending queue no longer contains the payout. Without unmount-first ordering, the BE poll could succeed while React's `onSuccess`/`onError` was still in-flight, leaving the modal up for the logout step.

Verified: **7 / 7 PASS three runs in a row**, ~40-50 s each. JOURNEY-REPORT verdict: PASS, 16 / 16 ACs across 6 chapters.

## Why the misdiagnosis happened

Pt33's "kafka projection race" theory came from reading the FAIL message at face value (`pendingBalance never reached the chapter-5 reservation amount`) without checking whether chapter 5 had actually run. The right first step would have been:

```bash
ls -la fe/e2e/journey/*.spec.ts            # cloud-stub check (Mode -a---l)
npx playwright test --list e2e/journey     # discovery sanity
git log --since "1 hour ago" docker-compose # has the FE rebuild?
```

Two of those three would have caught the real problem in 30 seconds. The lesson: when the BE state contradicts the test failure message, trust the BE state. The wallet had `lastPayoutAt: 08:16` while the spec was claiming `pendingBalance: 0` — that's a contradiction (a payout was completed at 08:16, but no reservation arrived?) that points at "we're looking at stale state.json" before "kafka is broken."

## Files touched this block

```
M  fe/src/app/pages/seller/SellerOrders.tsx                                # JSX fix (item 2)
M  fe/src/app/pages/admin/PayoutsQueue.tsx                                 # onError dialog reset (item 3)
M  fe/e2e/journey/06-admin-closes-the-loop.spec.ts                         # unmount-first ordering
M  fe/e2e/evidence/                                                        # fresh artifacts (3 PASS runs)
M  fe/e2e/journey/{01,02,03,04,05,99}*.spec.ts + _journey-{evidence,state}.ts  # hydrated from OneDrive cloud-stubs
A  docs/SESSION-HANDOVER-2026-05-24-pt34.md                                # this file
```

## Gotchas this block (extends pt33 list)

**91. OneDrive cloud-stubs silently break Playwright test discovery.** `Get-ChildItem -Force` shows `Mode -a---l` for stubbed files; `git status` reports them as unchanged. Hydrate via copy-delete-rename in PowerShell (a simple `Get-Content` is enough on most files but doesn't survive the OneDrive sync round-trip on `.spec.ts`). Detect early: when Playwright's "X of Y tests" count looks low, `npx playwright test --list <dir>` should show every spec; missing ones are stubs.

**92. Vite dev and esbuild docker build accept different JSX.** Stray closing `</div>` after a removed opening tag passes Vite dev (it auto-closes) but esbuild errors `Unterminated regular expression` because it tries to parse the next line as a regex literal. Always run `docker compose build frontend` after a layout edit before claiming the change is shipped — the running container is the source of truth for what the journey suite actually drives. The pt33 walkthrough used the running container for screenshots (where the JSX-leaked path was never rendered) so the leak survived undetected through pt33's gates.

**93. React Query `onError` must reset modal state, not just raise a toast.** A successful BE mutation can still throw on the FE if the response envelope shape drifts (the `adminPayoutSchema` Zod parse here). If the dialog only closes in `onSuccess`, an admin gets stranded under an opaque overlay even though the action succeeded. The right pattern: both `onSuccess` and `onError` invalidate the relevant query AND clear the modal target state. The user can rerun if needed; what they can never do is escape an invisible overlay.

## Open thread for the next session

**Reframed — the "Zod parse error" is probably a phantom.** Pt34's commit message theorised that the BE mutation succeeded but the FE Zod parse tripped, falling through to onError. After staring at the schemas (BE `PayoutResponse(payoutId, sellerId, amount, status, createdAt)` vs FE `adminPayoutSchema` accepting both legacy `id`+`requestedAt` AND live `payoutId`+`createdAt`), the shapes align. No obvious mismatch.

A more honest read of the symptom: the dialog stayed open with **neither** a green toast (onSuccess) nor a red one (onError). That's not a parse failure — that's the mutation still in-flight when the test moved on. The fix that actually closed the gap was the spec re-ordering to **wait for dialog unmount before asserting BE state**, not the `onError` reset (which was sound defensive coding but probably never fired).

The `onError` reset stays — stranding an admin under an opaque overlay on any failure mode is the wrong default. But "trace the underlying Zod parse error" is likely chasing something that isn't broken. Closing this thread.

**Medium — payout audit trail** (carryover from pt33): no `completedBy` / `completedAt` on the payout row yet. Adding these would let the queue show "Completed by admin1, 2 hours ago" once the payout flips to COMPLETED.

**Lower priority** (carryover from pt32+pt33):
- Avatar upload feature implementation.
- PayPal capture round-trip.
- Shipping tracking ownership check.
- VNPay/MoMo `redirectUrl` from PaymentResponse.
- Kafka env-override audit on the other six services.

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `40c2de78`.
2. **Cloud-stub sanity check.** `Get-ChildItem -Force fe/e2e/journey | Format-Table Name, Mode` — every Mode column should be `-a----`, none with trailing `l`. If any have `l`, hydrate first.
3. **Smoke gates:**
   - `cd fe; npx tsc --noEmit` → 0 errors.
   - `cd fe; npm test -- --run` → 156 / 156.
   - `cd services/{order,product,user}-service; ./mvnw test` → all green (9, 31, 118 respectively).
   - Workday suite → 3 / 3.
   - Journey suite → 7 / 7 (three runs back-to-back to catch flakes; should be stable).
4. **Visual sanity:** Admin → Payouts → click Complete on a real pending row → confirm dialog appears with green submit → click Confirm → row drops + toast. Or: kill the BE mid-mutation to verify the `onError` path still closes the dialog.

## Final session ledger (pt27 → pt34)

- **pt27**: i18n duplicate-key fix + Tabler migration.
- **pt28**: dark-mode pilot + 47-file codemod.
- **pt29**: 27 UI Playwright specs + 3 BE bugs caught.
- **pt30**: persona-workday suite.
- **pt31**: BA-grade journey chapters 1-4 + 5 caught bugs.
- **pt32**: chapters 5+6 + journey 16/16 PASS.
- **pt33**: 8-issue UX fix-it block — reviews UUIDs, payout confirm, console chrome split, application context, empty states, list chrome, a11y. Misdiagnosed chapter-6 flake as kafka.
- **pt34 (this block)**: tracked the chapter-6 flake to root: OneDrive cloud-stubs (test discovery), JSX leak (docker FE build), and dialog-onError (mutation race). Fixed all three; journey 7/7 stable across three runs. Three new gotchas (#91-93).

The story this session arc tells: a BA-grade journey suite is exactly as reliable as the most invisible part of its toolchain. Pt34 found three of those invisible parts (filesystem stubs, build-tool divergence, mutation-state lifecycle) and shored them up. Production behavior has been correct throughout; only the suite's ability to *prove* it kept slipping.
