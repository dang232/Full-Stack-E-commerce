# Session handover — 2026-05-20 (pt8: pt7 deferred queue cleanup, autonomous parallel agents)

**Last commit (HEAD):** `7995cee8` (`refactor(payment): migrate MoMo callback onto PaymentPromotionService`)
**Commits this block:** 2 since pt7 HEAD `467d3f6c`.
**Gates:** payment-service `67/67` (was 66 — added FAILED-IPN regression). FE typecheck `0`. Playwright discovery `38 tests in 10 files` (was `17 tests in 2 files` — see "OneDrive reparse-point gotcha" below). UX sweep admin block ✓ (was skip).

This block executed three parallel deferred items from pt7's "What's still missing" queue. Each landed a different kind of fix:

1. Three sub-agents dispatched in parallel via the `superpowers:dispatching-parallel-agents` pattern.
2. Two of them silent-bailed mid-task (classic pattern from pt5/pt6 memory) — one returned a verbal plan with zero file writes, another stopped right at "let me look" after 24 minutes of file-reads.
3. Finished the bailed work directly. The admin-seeding agent was the only one that actually shipped without intervention.

## Commits this block (chronological)

| # | Commit | What |
|---|---|---|
| 1 | `59b71bcd` | fix(e2e): correct admin login creds in UX sweep (`admin1`/`test`) |
| 2 | `7995cee8` | refactor(payment): migrate MoMo callback onto PaymentPromotionService |

## TL;DR

Pt7 deferred queue had six items. Three are now closed:

1. **Admin slice of UX sweep unblocked.** The realm import already seeded `admin1` / `test` (`infra/keycloak/vnshop-realm.json` from a prior block). The sweep was probing the wrong creds (`admin` / `admin`) and self-skipping. Two-character fix; admin block now produces `50-admin-dashboard.png` through `55-admin-reviews.png` + console JSON each.
2. **MoMo callback migrated onto `PaymentPromotionService`.** Last gateway still doing its own per-step save+ledger+outbox. Now follows the same single-transactional `promote()` path as Stripe / PayPal / VietQR / VNPAY. FAILED IPN branch kept inline (promotion service handles success only) and preserves the original contract — including the FAILED outbox emit, which is louder than VNPAY's FAILED path. Added a regression test for FAILED so the branch can't silently drop the outbox row again.
3. **Playwright spec discovery fixed.** All 8 missing specs now discoverable. Root cause was *not* a Playwright bug, a tsconfig issue, or a `.gitignore` rule — it was OneDrive's "Files On-Demand" reparse-points. See the gotcha section.

Three pt7 items still deferred: PayPal capture round-trip (needs human at the browser), notifications inbox, real GHN/GHTK adapter.

## What shipped

### `fix(e2e): correct admin login creds in UX sweep (admin1/test)`
- `fe/e2e/ux-sweep.spec.ts:270-271` — `admin` → `admin1`, `admin` → `test`. The bouncing-back-to-`/login` self-skip was masking the credential typo as "no admin user seeded."
- `infra/keycloak/vnshop-realm.json` already has the admin user with `realmRoles: ["ADMIN"]` from a prior block; no realm change needed.
- No backend seed needed: admin role doesn't require a `buyer_profiles` or `seller_profiles` row, and user-service auto-provisions on first JWT claim. The 6 admin pages render without crashing the React tree.

### `refactor(payment): migrate MoMo callback onto PaymentPromotionService`
- `services/payment-service/.../MomoCallbackService.java` — replaced direct `LedgerService.recordPayment(...)` + manual save + manual outbox with a `promotionService.promote(PromotionCommand.fromCallback(...))` call. The COMPLETED path is now identical in shape to VNPAY's. FAILED path stays inline (the service only promotes successes) and preserves the original behavior of emitting an outbox row for FAILED — louder than VNPAY's FAILED branch which doesn't emit, but preserving it avoids silently changing downstream consumer expectations.
- `services/payment-service/.../MomoCallbackServiceTest.java` — wires `PaymentPromotionService` through the test factory (constructed against the same in-memory repository so the dedup short-circuit still works). Adds `ipnWithFailedResultPersistsTerminalStatusWithoutCreditingLedger` to lock the FAILED branch: terminal status persisted, ledger empty, outbox carries `status=FAILED`.
- `mvnw test` → `Tests run: 67, Failures: 0, Errors: 0, Skipped: 0` (was 66 — net +1 from the FAILED regression).

### Playwright spec discovery (no diff)
The fix was rewriting the on-disk bytes of 8 reparse-point files to plain bytes — content identical to what's in git, so `git status` shows nothing. Durability is the open question (see gotcha #21).

## Operational gotchas (durable rules — additions to pt5/pt6/pt7)

The pt5 + pt6 + pt7 lists still apply. New rules learned this block:

21. **OneDrive "Files On-Demand" creates reparse-points that look identical to plain files but break tools that don't follow them.** When a file in a OneDrive-synced repo reports `Mode = ...---l` in PowerShell `Get-ChildItem -Force` and `fsutil reparsepoint query <file>` returns a non-zero tag (e.g. `0x9000201a` = OneDrive cloud-only stub), Playwright's file walker silently excludes it from `testMatch`. Vitest, tsc, and most other Node tooling go through `fs.readFile` and follow the reparse-point transparently, so the issue is invisible until you hit a tool that walks the directory itself.

    **Symptoms:** `npx playwright test --list` shows 2 of 10 specs; the other 8 are "files that exist in `git ls-files` but Playwright reports `Total: 0 tests in 0 files` when you target them by exact path." `Read` on the file returns content normally because Read uses `fs.readFile`.

    **Fix on a single file:** copy to a temp, delete original, move temp back — replaces the reparse-point with a plain file. PowerShell one-liner that hydrates an entire directory:
    ```powershell
    Get-ChildItem fe/e2e -Filter "*.spec.ts" -Force | Where-Object { $_.Attributes -band [IO.FileAttributes]::ReparsePoint } | ForEach-Object { $tmp = "$($_.FullName).tmp"; [IO.File]::Copy($_.FullName, $tmp, $true); Remove-Item $_.FullName -Force; Move-Item $tmp $_.FullName }
    ```

    **Durable fix:** right-click the `fe/` folder in Explorer → "Always keep on this device", or set `OneDrive.exe /shutdown` for the duration of the build. Or move the repo out of `OneDrive\Documents\` for CI runs. Or check in a `.gitattributes` rule — actually that doesn't help; reparse-points are a filesystem-level metadata concern, not git. Add a `pretest` step in `fe/package.json` that runs the hydration one-liner if you don't want to fight the OneDrive UI.

    **How to spot it next time:** if a tool that does its own directory walk (Playwright, esbuild glob, find, ripgrep with `--no-ignore`) silently excludes files that obviously exist, run `Get-ChildItem -Force | ForEach-Object { "$($_.Name) $($_.Mode)" }` and check the `l` flag. `fsutil reparsepoint query <file>` confirms.

22. **Sub-agent silent bail is repeatable on Opus when the task involves "explore then implement" — exploration eats the budget, the implementation phase never runs.** Two of three agents this block bailed at the boundary: one returned `"Now I have everything. Let me implement..."` + plan + zero file writes; the other ran 24 minutes of greps and stopped at `"Let me look at fe/.gitignore"`. Pattern-match: when a sub-agent's last message looks like a plan or a "let me check X" probe, immediately verify with `git status` / `git diff --stat HEAD` before trusting any summary it sends. If zero files changed, do the work yourself or re-dispatch with a smaller scope ("you've already explored, now WRITE — make Edit calls, do not just describe"). Memory rule [[detect_silent_bail]] already captured this; re-confirmed here.

23. **`PaymentPromotionService.promote()` is idempotent on replay and lock-free on concurrent IPN.** Already-COMPLETED short-circuits without writing; PENDING transitions to COMPLETED inside one `@Transactional`. Callers do their own dedup via `PaymentCallbackLogStore` *before* calling promote, so a duplicate IPN never reaches the service. When migrating a new gateway onto promote, the migration is mechanical: drop manual save+ledger+outbox, call `promote(PromotionCommand.fromCallback(...))`, keep the FAILED branch inline.

## Test inventory after this block

- Playwright e2e: `38 tests in 10 files` discoverable (was 17 in 2). The 8 newly-discoverable specs were never validated this block — only their *discovery* was unblocked. Whether they pass against the current backend is unknown and a follow-up. Smoke spec is the cheapest to start with.
- Vitest: untouched (no FE schema changes).
- BE: payment-service `67/67` (+1 FAILED-IPN regression for MoMo). user-service `116/116` from pt7, untouched. seller-finance + recommendations untouched.

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `7995cee8`. Working tree should still show `M .gitignore` + `?? opencode.jsonc` (carry-over editor cruft, ignore — they've been around since pt6).
2. **Run the gates:**
   ```powershell
   Set-Location services/payment-service; ./mvnw test     # 67/67
   Set-Location ../user-service; ./mvnw test               # 116/116
   Set-Location ../../fe; npx tsc --noEmit                 # 0
   npx playwright test --list --project=chromium           # Total: 38 tests in 10 files
   npx playwright test e2e/ux-sweep.spec.ts e2e/profile-address.spec.ts --project=chromium  # 17 passed
   ```
3. **Hydrate spec files if `--list` shows fewer than 10 files.** OneDrive may have re-stubbed them; run the gotcha #21 one-liner.

## What's still missing (deferred — pt8 → pt9)

- **PayPal capture round-trip.** Smart Buttons render on the success step (FE creds inlined post-pt6), the BE OAuth + create + capture path is unit-tested, but the live FE → sandbox PayPal popup → `/payment/paypal/capture` round-trip has never been driven by hand. Last unproven payment path. **Needs you at the browser.**
- **Validate the 8 newly-discovered legacy Playwright specs.** Discovery is unblocked; whether `smoke.spec.ts`, `buyer-happy-path.spec.ts`, `guest-cart.spec.ts`, `authenticated-routes.spec.ts`, `role-routes.spec.ts`, `sellers.spec.ts`, `payment-multi-method.spec.ts`, and `network-diagnostic.spec.ts` actually pass against the current backend is a separate exercise. Run them one at a time; the ux-sweep spec covers more surface so the regression risk is contained — but if any of these fail, the failure is information.
- **Notifications inbox.** notification-service consumes Kafka but no inbox endpoint or FE bell yet.
- **Real GHN/GHTK shipping rate adapter.** `LiveCarrierGateway` scaffolding exists; needs API key wiring + integration tests.
- **OneDrive durability.** The reparse-point fix only persists until OneDrive next decides to evict the files. Either pin `fe/e2e/` via Explorer's "Always keep on this device", or add a `pretest` hook in `fe/package.json` that runs the hydration one-liner.

## Resume hint

Next session: **drive the PayPal capture round-trip in the browser** — still the last unproven sandbox path. If you'd rather work autonomously, pick up the legacy Playwright specs validation — running `npx playwright test e2e/smoke.spec.ts --project=chromium` is the cheapest first step and either gives you a clean pass (delete the "still missing" entry) or a failure that points at a real gap. The dispatching-parallel-agents pattern from this block plus the silent-bail recovery loop is the new default for autonomous queue drain.
