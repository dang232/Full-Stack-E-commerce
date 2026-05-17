# Session handover — 2026-05-17 (continuation: ambitious-features wave)

**Last commit:** `348c07f0` (HEAD on main)
**Session length:** 16 commits on top of `93a7ff2f` (the previous handover's tip)
**Branch state:** clean — only `.claude/worktrees/` directory is unstaged (locked agent worktrees)

This file extends the original `SESSION-HANDOVER-2026-05-17.md` (still in this folder). Same date, same project, but a separate working block: 4 ambitious features delegated to sub-agents in parallel worktrees, then merged + quality-passed.

## TL;DR

Four parallel sub-agent waves, all merged with green verification:

- **Live GHN/GHTK tracking events** — agent landed cleanly, stub-style `MISSING-*` → 404 mapping ported to live adapters.
- **Recommendations service stub** — full new Spring Boot service (`services/recommendations-service`, port 8094) with co-purchase aggregator + same-category recommender. Two REST endpoints, gateway routed.
- **i18n with react-i18next** — agent silent-bailed twice (see operational notes); finished by Claude. `vi.json`/`en.json` catalogs, language switcher, ~50 strings flowing through `t()`.
- **Buyer-seller messaging MVP** — agent silent-bailed but produced complete code; salvaged + merged. New NestJS `services/messaging-service` (port **8095**, conflict-resolved), REST + WebSocket, FE MessagesPage, Chat buttons no longer toast "coming soon".

Two post-agent quality passes shipped as their own `refactor(...)` commits:

- carrier `require()` helper deduped across GHN/GHTK adapters → `services/shipping-service/.../CarrierConfig.java`
- messaging use-cases switched from raw `Error` to `BadRequestException` (was returning HTTP 500 instead of 400)
- recs `YouMayAlsoLikeUseCase` cleaned up 6× FQN `java.math.BigDecimal` references

## What shipped (commit map)

| Commit | What |
|---|---|
| `348c07f0` | feat(fe): translate OrdersPage strings via i18n |
| `dd84f7c0` | feat(fe): translate CartPage strings via i18n |
| `f1b7f1ad` | docs: SESSION-HANDOVER pt2 for ambitious-features wave |
| `47c312f8` | refactor(messaging): use BadRequestException + drop dead import |
| `9b56d8b3` | Merge branch 'worktree-agent-a6fce52349f812e62' (messaging MVP) |
| `179fd17d` | feat(messaging): NestJS messaging-service + FE thread/message UI |
| `3dde1c64` | feat(fe): translate recommendation grid titles via i18n |
| `da7ac0ac` | refactor(recommendations): import BigDecimal instead of FQN |
| `98c35e5d` | Merge branch 'recommendations-service' |
| `1de57370` | feat(fe): wire ProductPage to recommendations-service |
| `4cd51c69` | feat(api-gateway): route /recommendations/** to recommendations-service |
| `ef884a12` | feat(recommendations-service): co-purchase + same-category recommender |
| `90587e38` | feat(fe): wire i18n into Root header/footer + HomePage flash sale |
| `2dcfa1af` | feat(fe): add react-i18next scaffold with vi/en catalogs |
| `69f9bb93` | refactor(shipping-service): dedupe live-carrier require() helper |
| `e18d0788` | feat(shipping): populate events in GHN/GHTK tracking responses |
| `533c05c9` | feat(fe): flash-sale cards show real product image + name via batch fetch |

## Verified clean

```bash
# FE
cd fe
npm run typecheck   # ✓
npm run lint        # 0 warnings, 0 errors ✓
npm run test        # 112/112 ✓ (was 94 — 18 new tests across recs + messaging)
npm run build       # ✓

# BE per-service
cd services/shipping-service       && ./mvnw.cmd test -Djacoco.skip=true   # 23/23 ✓
cd services/recommendations-service && ./mvnw.cmd test -Djacoco.skip=true  # 48/48 ✓
cd services/api-gateway            && ./mvnw.cmd compile                   # BUILD SUCCESS ✓
cd services/messaging-service      && npm test                             # 28/28 ✓
```

## Outstanding follow-ups (flagged, not acted on)

### BE — recommendations-service
1. **Cold-start fallback.** Until enough orders flow through `order.created`, `frequently-bought-together` returns `[]`. FE renders nothing — graceful but uninformative. Consider a popularity fallback (top sold in same category) as a third use case rather than complicating the aggregator.
2. **Backfill on first deploy.** `OrderEventListener` defaults to Kafka consumer reset = `latest`. Historical orders won't seed `co_purchases`. Either re-publish from order-service's outbox, or add a one-off backfill job.
3. **No SecurityConfig.** Both endpoints are public reads. If/when personalised recs land, mirror `services/seller-finance-service/.../SecurityConfig.java` and add `spring-boot-starter-oauth2-resource-server`.
4. **`RestProductServiceAdapter` doesn't propagate Authorization.** Product-service is `permitAll()` today; if that flips to authenticated, this needs a service-account token.
5. **N+1 enrichment in `FrequentlyBoughtTogetherUseCase`.** Per-row `productById` per top-N call. Default N=4 is fine; revisit if N grows.

### BE — messaging-service
1. **Port collision resolved at merge time.** Agent shipped on 8094 (already taken by recs); moved to **8095** in `Dockerfile`, `main.ts`, `docker-compose.yml`, gateway `RouteConfig`. Spot-check both services boot together before next deploy.
2. **WebSocket on Spring Cloud Gateway.** `/ws/messaging` is routed without a circuit breaker (intentional — Resilience4j's reactive breaker doesn't reliably wrap HTTP-Upgrade lifecycles). FE handles reconnects with backoff.
3. **Out of MVP scope (deliberate cuts):** typing indicators, attachments, group chats, read receipts beyond simple `read_at`.

### BE — shipping-service
1. **Pre-existing FU still open.** Live GHN/GHTK rate-quote endpoint (separate from tracking) is still TODO — see commit `a812bc6` notes from the previous handover.

### FE
1. **Recs cold-start banner.** When both `useFrequentlyBoughtTogether` and `useYouMayAlsoLike` return empty (cold start), the page just shows nothing. Worth a placeholder card so users know recs exist but aren't ready.
2. **i18n coverage — done so far:** Root header/footer, HomePage flash sale, CartPage (full), OrdersPage (full incl. tracking modal, return modal, status pills, tabs). **Still hardcoded:** CheckoutPage (1036 lines, intricate saga + payment dispatch — recommend a dedicated session, do NOT half-translate the highest-traffic path), SearchPage, ProductPage detail copy, ProfilePage, SellerPage, AdminPage, WishlistPage, MessagesPage. Pattern is mechanical; ~150 strings remain.
3. **5 mock fallbacks to `vnshop-data.ts`** still exist — same as previous handover, untouched.

## Operational notes / gotchas

1. **Sub-agent silent bail — happened TWICE this session.**
   - i18n agent reported "Now creating the i18n files. First the translation catalogs..." as a completion summary; deps installed + catalogs scaffolded but no commits, no wiring. Verified via `git -C <worktree> log main..HEAD` showing zero commits + `git status --short` showing untracked files. Resumed via SendMessage with explicit finish-or-blocker prompt → bailed *again* with 0 tool uses on resume. Finished manually.
   - Messaging agent ran 291 tool uses over ~66 min, then reported "OneDrive seems to have eaten the source files at the main-repo path. Let me rewrite everything into the worktree from scratch." as completion. The files were actually intact. Worktree had 0 commits but a complete working implementation. Salvaged by committing on the worktree branch, then merging.
   - **Always verify**: `git -C <worktree-path> log --oneline main..HEAD` (must show ≥1 commit) + `git status --short` (clean = good, dirty = bailed). The agent's "result" text alone is not trustworthy.

2. **Worktree-isolation bleed-through on Windows + OneDrive.** Untracked files from agent worktrees show up in main's `git status --short` (`fe/src/app/lib/i18n/`, `services/messaging-service/`). Visible to `git`, but `ls`/`cat` from a normal shell can't always see them — OneDrive sync timing. **Workaround**: copy from `<worktree-path>/...` explicitly when salvaging, don't trust auto-replication.

3. **`git stash --include-untracked` chokes on agent worktrees.** Read-only `node_modules` / `target` files in untracked worktree dirs make stash creation succeed but stash *cleanup* fail with "Permission denied" — and crucially, the stash is then a partial duplicate that, on `stash drop`, will delete files you wanted to keep. **Don't use `--include-untracked` while agent worktrees are locked.**

4. **Port collisions during merge.** Two agents both claimed port 8094. Recs landed first (kept 8094); messaging moved to 8095 across `Dockerfile`, `main.ts`, `docker-compose.yml`, gateway route default.

5. **Carrier dedupe pattern.** Three live-carrier adapters had identical `private static String require(String, String)`. Lifted to `infrastructure/carrier/CarrierConfig` package-private utility. Apply the same pattern next time you see the duplication signal.

6. **`@SpringBootTest` context-load gotcha** (carryover from prior handover): excludes JPA autoconfig in this monorepo. `@ConditionalOnBean(EntityManagerFactory.class)` is the workaround. The recs service's `JpaConfig.java` mirrors `services/user-service/.../config/JpaConfig.java`.

## File locations to know

- **New BE services this session:** `services/recommendations-service/` (8094), `services/messaging-service/` (8095)
- **Schemas:** `infra/postgres/init/001-create-schemas.sql` — added `recommendations_svc` + `messaging_svc`
- **Compose:** `docker-compose.yml` — entries near lines 615 (recs) and 724 (messaging)
- **Gateway routes:** `services/api-gateway/.../RouteConfig.java` — `/recommendations/**` (resilient), `/messaging/**` (resilient), `/ws/messaging` (raw, no breaker)
- **i18n catalogs:** `fe/src/app/lib/i18n/{vi,en}.json` + initializer at `fe/src/app/lib/i18n/index.ts`, language switcher at `fe/src/app/components/language-switcher.tsx`
- **FE recs hooks:** `fe/src/app/hooks/use-recommendations.ts`, endpoint at `fe/src/app/lib/api/endpoints/recommendations.ts`
- **FE messaging:** `fe/src/app/pages/MessagesPage.tsx`, hooks `use-threads.ts` / `use-messages.ts` / `use-messaging-socket.ts`, endpoint client `messaging.ts`

## Next-session candidates (ranked)

| Effort | Item | Why |
|---|---|---|
| ~30 min | Translate ProfilePage / WishlistPage / SearchPage tab labels | Smaller pages, mechanical extraction; same pattern as CartPage/OrdersPage |
| ~1 hour | CheckoutPage i18n — **dedicated session** | 1036 lines, payment-dispatch + saga + idempotency. Don't rush; this is the buyer's most critical flow. Suggested: write all keys first under `checkout.*`, then wire in step-by-step (address → shipping → payment → review → success) so a partial translation is at least atomically scoped to one step. |
| ~1 hour | Recs cold-start fallback (popularity-by-category) + cold-start banner on FE | Makes recs feel real on day-1 deploy; today they're empty until orders accrue |
| ~half day | Messaging E2E smoke (compose up, real Kafka, send a message between two users) | Highest-risk untested path — Kafka wiring + WebSocket gateway are the bits hardest to unit-test |
| ~1 day | OpenSearch migration (Phase 4B from original plan) — only when catalog grows past ~100K |
| ~1-2 days | SMS / push notification channel — handover-flagged "Amazon-class" gap |
| ~2-3 days | Saved payment methods on Profile (F62/F63 pending in original handover) |

## Quality-pass discipline (durable rule from this session)

After every sub-agent merges, do a clean-code / DDD / DRY / SOLID review **before** declaring done. Findings from this session that turned into commits:

- `refactor(shipping-service)`: deduped 3× `require()` helper (DRY)
- `refactor(recommendations)`: 6× FQN `java.math.BigDecimal` → import (clean code)
- `refactor(messaging)`: raw `Error` → `BadRequestException` (correctness — wrong HTTP status), plus dead `void ForbiddenException` import deleted

What I did NOT touch (logged as deferred, not silently accepted):
- Recs application services import directly from `infrastructure/persistence` (Spring Data `Pageable` leaking up). Strict hexagonal would extract a `CoPurchaseStore` port. Codebase already mixes these layers elsewhere; not the hill.
- Messaging idempotency-store is in-memory (no Redis backing). Fine for MVP single-pod; flag when scaling beyond 1.

## How to resume

1. `git log --oneline -20` — verify HEAD is `348c07f0` and last 16 commits match the table above.
2. `cd fe && npm run verify` — should be green (0 lint, 112/112 tests).
3. Pick from "Next-session candidates" — the ProfilePage / WishlistPage / SearchPage i18n is the lowest-friction follow-up. CheckoutPage deserves its own session.
4. **For sub-agent delegation:** include the rule "report blockers loudly, don't silently bail, green commits + report OR no commits + clear blocker report." It's saved this session multiple times AND been violated twice. Verify the diff before trusting the report.
5. **For post-agent merges:** always run the clean-code / DDD / DRY / SOLID quality pass and fix violations as a `refactor(...)` commit on top of the merge. Don't accept slop just because tests are green.
