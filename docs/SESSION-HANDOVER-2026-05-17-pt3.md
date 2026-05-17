# Session handover — 2026-05-17 (pt3: FE structural refactor)

**Last commit:** `baac3aa4` (HEAD on main)
**Session length:** 18 commits on top of `8ec5939d` (the pt2 handover's tip)
**Branch state:** clean — only `.claude/worktrees/` directory is unstaged (locked agent worktrees)

This file extends `SESSION-HANDOVER-2026-05-17-pt2.md` (still in this folder). Same date, same project, but a separate working block: the user flagged the FE for SOLID violations ("class in class"), missing interceptor, buried `ApiResponse`, duplicated zod schemas across endpoints, and confirmed BFF should NOT exist. Three parallel sub-agents fixed the structural debt; one bailed (caught + redone), and a 4-commit quality pass collapsed the duplication that survived.

## TL;DR

Three parallel sub-agent waves on isolated worktrees, plus one retry, plus a 2-commit quality pass:

- **Agent A — API interceptor chain.** `lib/api/client.ts` was a monolithic `request()` doing auth-refresh + correlation-id + envelope-parse + 401-retry inline. Lifted into composable interceptors (`correlationId`, `contentType`, `idempotency`, `auth`, `jsonParse`, `errorStatus`, `envelope`, `unauthorized`) with a small pipeline runner. Public `api.get/post/...` surface byte-for-byte identical so endpoint files needed zero changes. New `fe/src/app/lib/api/index.ts` barrel surfaces `ApiResponse<T>`, `ApiError`, `apiResponseSchema`, plus the four interceptor types. **15 new interceptor tests** (including 3 paths for the 401-refresh scenario).
- **Agent B — Schema consolidation.** Old `fe/src/app/types/api.ts` was one 196-line file holding 11 schemas, BUT 7 endpoint files declared their own inline `z.object(...)` (admin had 8 inline schemas alone). Split into `fe/src/app/types/api/{shared,product,admin,checkout,messaging,user,cart,order,review,notification,coupon,payment,flash-sale,wishlist,shipping,search,seller-analytics,seller-finance,category}.ts` plus `index.ts` barrel. **Zero `z.object` left in `endpoints/*.ts`** (verified by grep). Caller imports unchanged — barrel re-exports keep `from "../../../types/api"` resolving.
- **Agent C — Page decomposition (TWO ATTEMPTS).** First run silent-bailed in a way the pt2 handover warned about (see operational notes). Second run rebased onto post-A+B main, then split `AdminPage.tsx` (1172 lines, 11 nested components), `SellerPage.tsx` (957 lines), `CheckoutPage.tsx` (1076 lines) into per-feature folders under `pages/admin/`, `pages/seller/`, `pages/checkout/`. Single-component-per-file. i18n verified preserved with grep thresholds (≥110/85/85 `t()` calls per cluster) BEFORE merging.
- **Quality pass.** `AdminKPICard` and `SellerKPICard` were 95% identical → lifted to `fe/src/app/components/kpi-card.tsx`. 23 consumers were still importing from `lib/api/envelope.ts` directly, defeating the point of Agent A's barrel — migrated all to `lib/api`.

After everything, a complete `npm run verify` is green: **127/127 tests** (was 109 — 18 new across interceptor + schemas + i18n moves), 0 lint warnings, 0 type errors, build clean.

## What shipped (commit map)

| Commit | What |
|---|---|
| `baac3aa4` | refactor(fe): migrate ApiError consumers from envelope.ts to lib/api barrel |
| `48db0cb9` | refactor(fe): dedupe KPICard between admin and seller dashboards |
| `7bb25624` | chore(fe): delete leftover monolith AdminPage.tsx and SellerPage.tsx after split |
| `0a90d4b8` | Merge branch 'worktree-agent-a7c8044c78c4ac1e6' (split CheckoutPage into pages/checkout/) |
| `43f436ba` | Merge branch 'worktree-agent-a9f30530ce44abb35' (split SellerPage into pages/seller/) |
| `a2d4b1a5` | Merge branch 'worktree-agent-aec39022bb78136cc' (split AdminPage into pages/admin/) |
| `885a52af` | refactor(fe): delete original CheckoutPage.tsx after split |
| `cd0ada17` | refactor(fe): split CheckoutPage into pages/checkout/ per-feature files |
| `491c2ea1` | refactor(fe): split SellerPage into pages/seller/ per-feature files |
| `77a1c936` | refactor(fe): split AdminPage into pages/admin/ per-feature files |
| `4422a9e6` | Merge branch 'worktree-agent-a7749c696cc70d207' (FE schema consolidation, domain-split types/api/) |
| `b339efb4` | Merge branch 'worktree-agent-a0be7640a49e258f2' (FE API interceptor chain + ApiResponse barrel) |
| `98d58cb6` | refactor(fe): move remaining endpoint inline schemas into types/api |
| `85a11ebd` | refactor(fe): move checkout + coupon inline schemas into types/api |
| `79e673c7` | refactor(fe): move admin endpoint inline schemas into types/api/admin.ts |
| `92e4c826` | refactor(fe): split types/api.ts into domain-scoped types/api/ folder |
| `6bfdeb13` | feat(fe): surface ApiResponse and ApiError via lib/api/index.ts barrel |
| `ccff5715` | refactor(fe): extract API interceptor chain (auth, correlation-id, envelope, 401-retry) |

## Verified clean

```bash
cd fe
npm run typecheck   # ✓ (0 errors)
npm run lint        # ✓ (0 warnings, 0 errors)
npm run test        # ✓ 127/127 (was 109 last session)
npm run build       # ✓ ~5.8s
```

i18n preservation verified post-split:
- `pages/admin/`: 115 `t(` calls (threshold ≥110)
- `pages/seller/`: 90 `t(` calls (threshold ≥85)
- `pages/checkout/`: 89 `t(` calls (threshold ≥85)

Inline schema verification post-consolidation:
- `grep -rln "z\.object" fe/src/app/lib/api/endpoints/ | grep -v ".test.ts"` → empty.

## Architectural answers to the user's questions (for the record)

1. **"BFF for a single line?"** — There is NO BFF in the FE. Confirmed via grep for `/api/proxy`, Next.js routes, node middleware. FE calls the gateway directly through `lib/api/client.ts`. ✓ already as the user wanted.
2. **"Where is the interceptor?"** — Now exists at `fe/src/app/lib/api/interceptors.ts`. Three chains (request, response, error). Adding a new behavior (logging, error toasts, retry-with-jitter) is now a one-file change against a single chain entry, not a surgical edit inside a god function.
3. **"Why are we doing this for the API type?"** — That was the inline `z.object` per endpoint pattern. Gone. Every schema now lives under `fe/src/app/types/api/{domain}.ts` with one source of truth.
4. **"Where is `ApiResponse`?"** — Surfaced via `fe/src/app/lib/api/index.ts` barrel. All 23 prior consumers now import from `lib/api` (not from `lib/api/envelope`). Including the type re-export, `import { ApiError, type ApiResponse } from ".../lib/api"` is the public-facing way.
5. **"Shared schema so we fix one place?"** — `types/api/{shared,product,admin,coupon,...}.ts`. Agent B explicitly noted one intentional non-dedupe: admin's strict `couponSchema` vs buyers' looser `buyerCouponSchema` (the BE is mid-migration; pt2 handover §1 issue #5). Both kept, both clearly documented in their respective files.

## Operational notes / gotchas (durable for next session)

1. **Sub-agent silent bail — happened AGAIN this session, in a new flavor.** First Agent C reported "page decomposition done" with 3 commits + green verify on its worktree. The work was real, BUT the worktree was based on `origin/main` which is **38 commits behind local main** (the pt2 handover's tip). Its split files contained the *pre-i18n* page contents. Merging would have silently deleted ~290 `t()` calls. Caught by spot-checking grep counts on agent's split files BEFORE finishing the merge. Aborted, re-spec'd Agent C2 with explicit `git rebase main` pre-flight + i18n grep threshold gate. C2 itself hit a Cloudflare 524 timeout mid-work (Opus, single big task). Re-split into three smaller sonnet agents, one per page — they all landed cleanly.
   - **Durable rule from this:** when delegating to a worktree-isolated agent, the rebase-onto-local-main pre-flight is mandatory if local main has diverged from origin. Don't trust the worktree's base ref to be current. Add a "verify with grep against current main" gate to the brief.
   - **Plus the existing rule from pt2:** verify with `git -C <worktree> log main..HEAD` before trusting the agent's report text.

2. **Cloudflare 524 timeouts on Opus.** The first Agent C2 (single Opus agent doing all three page splits) hit `error_code:524` after ~7 min. The fleet's proxy seems to have a 120s read timeout on origin response. Switched to three smaller Sonnet agents in parallel, each scoped to one page — all completed in 5–17 min without timeouts. **Durable rule:** if the spec is "Opus + 30+ files of structural work", split into multiple smaller agents instead of betting on one big run.

3. **Worktree base-ref divergence math.** Local main was 38 commits ahead of origin/main when this session started. `git diff main..<branch> --stat` looks alarming if the branch was based on `origin/main` because it shows the 38-commit diff PLUS the agent's diff. To audit JUST the agent's work: `git diff <commit-before>^..<latest-commit> --stat`.

4. **`-X theirs` merge strategy with modify/delete conflicts.** Agent B deleted `types/api.ts` (replacing it with `types/api/`). Local main had recently modified that file (`.passthrough()` → `.loose()` polish). `git merge -X theirs` does NOT auto-resolve modify/delete — you have to `git rm` the deleted side manually, then commit.

5. **DRY survives sub-agents only if you re-check.** Agents A/B/C are scoped, and that's correct. But the user's "fix the inconsistency once" goal needs a post-merge sweep: A surfaced `ApiResponse` via the barrel, but 23 consumers still imported from `envelope.ts` directly — a one-shot `find … -exec sed` migrated them. Two near-identical KPICard files came out of the page-split; lifted to `components/kpi-card.tsx`. **Always do the cross-cutting deduplication AFTER all agents merge**, not during their individual passes.

6. **Carryover from pt2:** the worktree-isolation bleed-through on Windows + OneDrive is still real. `git status --short` on main showed `?? .claude/` for every agent worktree dir — `git add -A fe/` instead of bare `git add -A` keeps the worktrees out of the staged set. (Pt2's `--include-untracked` warning still applies — don't.)

## File locations to know

- **API layer:**
  - Public barrel: `fe/src/app/lib/api/index.ts`
  - Pipeline runner: `fe/src/app/lib/api/client.ts`
  - Interceptors: `fe/src/app/lib/api/interceptors.ts`
  - Envelope schema: `fe/src/app/lib/api/envelope.ts` (consumers import from `lib/api`, not `lib/api/envelope`, post-`baac3aa4`)
  - Tests: `fe/src/app/lib/api/{client,envelope,idempotency,interceptors}.test.ts`
- **Schemas:** `fe/src/app/types/api/{index,shared,product,user,cart,order,review,question,notification,checkout,coupon,payment,flash-sale,wishlist,shipping,search,seller-analytics,seller-finance,category,admin}.ts`
- **Pages — admin:** `fe/src/app/pages/admin/{AdminPage,AdminDashboard,SellersApproval,ReviewsModeration,CouponDialog,CouponsManagement,DisputesQueue,PayoutsQueue}.tsx` + `index.ts`
- **Pages — seller:** `fe/src/app/pages/seller/{SellerPage,SellerDashboard,SellerProducts,ShipDialog,SellerOrders,SellerWallet,SellerReviews,SellerSettings}.tsx` + `index.ts`
- **Pages — checkout:** `fe/src/app/pages/checkout/{CheckoutPage,CheckoutAddressStep,CheckoutShippingStep,CheckoutPaymentStep,CheckoutReviewStep,CheckoutSuccess,CheckoutSummary,types,format,index}.ts(x)`
- **Shared component:** `fe/src/app/components/kpi-card.tsx` (used by `admin/AdminDashboard.tsx` and `seller/SellerDashboard.tsx`)
- **Routes:** `fe/src/app/routes.ts` — three updated lazy imports for the split pages

## Outstanding follow-ups (flagged, not acted on)

### FE — structural

1. **`pages/checkout/CheckoutPage.tsx` is 489 lines** — the largest of the split shells. It's now pure orchestration (queries, mutations, step nav, layout shell), but if it grows, the next split would be a `useCheckout` hook for the data layer. Not the hill today.
2. **One-line wrapper components.** `pages/admin/CouponDialog.tsx` and `pages/seller/ShipDialog.tsx` keep an `XxxDialog` wrapper that just gates rendering on a truthy id, then renders `XxxDialogBody`. Pattern is fine and Agent C noted it intentionally; flag if a third dialog needs the same shape — would justify lifting to a tiny `<MountWhen open>` helper.
3. **Cross-page DRY candidates.** Coupon-dialog body (admin) and ship-dialog body (seller) share the same modal/form scaffold but have different fields. The repo already has `fe/src/app/components/form-dialog.tsx` doing exactly this — neither dialog adopts it. If a third bespoke dialog appears, migrate all three onto `FormDialog` instead of writing dialog #3.

### FE — strategic (carryover from pt2)

4. **5 mock fallbacks to `vnshop-data.ts`** still exist — same as previous handover, untouched.
5. Translation review pass round 2 still pending (pt2 §"Next-session candidates").

### BE (carryover from pt2)

- Live shipping rate-quote endpoint (highest-leverage next BE work).
- Messaging E2E smoke test (highest-risk untested path: Kafka + WebSocket gateway).
- Recs: backfill on first deploy, no SecurityConfig (acceptable for public reads), N+1 in `FrequentlyBoughtTogetherUseCase`.

## Quality-pass discipline (durable rule, extended)

After every sub-agent merges, do a clean-code / DDD / DRY / SOLID review **before** declaring done. This session's findings:

- `refactor(fe)`: KPICard duplicated between admin and seller — one shared component (DRY).
- `refactor(fe)`: 23 consumers importing past the public barrel — migrated to `lib/api` (encapsulation / consistency).

What I did NOT touch (logged as deferred):
- The `XxxDialog` wrapper pattern (mentioned above) — fine for 2 cases.
- Coupon `Coupon` re-aliased between `endpoints/coupons.ts` and `types/api/admin.ts` for back-compat — tracked in Agent B's report; will be deletable once the BE finishes its mid-migration on coupon shape.

**Updated rule** (additive on top of pt2's): when sub-agents finish, do TWO passes — (a) the standard DDD/SOLID/DRY review, AND (b) a "barrel discipline" sweep: if an agent surfaced a public API via a barrel, audit consumers and migrate any that still import the old internal modules. The barrel only delivers value if it's the only entry point.

## How to resume

1. `git log --oneline -20` — verify HEAD is `baac3aa4` and the last 18 commits match the table above.
2. `cd fe && npm run typecheck && npm run lint && npm run test && npm run build` — should be green (0/0 lint, 127/127 tests).
3. Pick from "Next-session candidates" or the live shipping rate-quote endpoint (still the highest-leverage BE work, carrying over from pt2).
4. **For sub-agent delegation:** the rebase-onto-local-main pre-flight is now mandatory when local main has diverged from origin/main. Brief should include: (a) `git fetch && git rebase main` first, (b) a grep-based sanity check of the source files after the rebase to confirm recent work is present, (c) a grep-threshold gate the agent must pass before reporting done. Plus the existing rule from pt2: verify diff with `git log main..HEAD` before trusting the agent's report text.
5. **For long Opus tasks:** if the work touches 30+ files or is expected to take >5 min, split into multiple smaller Sonnet agents to dodge the proxy 524 timeout.
6. **For post-agent merges:** always run the clean-code / DDD / DRY / SOLID quality pass AND the barrel-discipline sweep. Don't accept slop just because tests are green — and don't trust that a "consolidated module" is actually consolidated until you've audited consumers.
