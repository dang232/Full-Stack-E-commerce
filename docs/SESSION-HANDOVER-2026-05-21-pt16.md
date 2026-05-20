# Session handover — 2026-05-21 (pt16: seller-side IDOR negative-path tests locked)

**Last commit (HEAD):** `3f51dca7` (`test(e2e): pt15 seller-side IDOR negative-paths in day-simulation`)
**Commits since pt15 HEAD `f4821ffa`:** 1.

**Gates:**
- payment-service: 71/71. order-service: 71/71. review-service: 6/6 + container boots. seller-finance-service: 4/4. All 12 BE services green.
- Playwright: **12/12** in `day-simulation.spec.ts` (11 → 12). Full suite still **49+** (pt15 baseline plus this addition).
- FE typecheck: 0. Vitest: 143/143.

This block closed the last item from pt15's "genuinely open code work" list — explicit `wrong-X → 403` regression tests for the four ReturnController IDORs fixed in pt15 (`6007dff9`, `e036d7ae`). The pt15 fixes were correct on the day they shipped; this commit makes the contract durable.

## Commit this block

| # | Commit | What |
|---|---|---|
| 1 | `3f51dca7` | test(e2e): pt15 seller-side IDOR negative-paths in day-simulation |

## What shipped

### `3f51dca7` — Seller-side IDOR negative-path tests
New test in `fe/e2e/day-simulation.spec.ts` at line 454. Drives the full real-world setup so the assertions are meaningful:

1. Buyer A places COD order on a real seeded product (`seller1`'s).
2. Buyer A confirms COD payment (advances order past `PENDING`).
3. `seller1` accepts the suborder.
4. `seller1` ships the suborder (`carrier` + `trackingNumber` are required by `RequestReturnUseCase` — without them the return submit 4xx's).
5. Buyer A submits the return via `POST /returns`.
6. Then fires four IDOR probes:
   - `POST /returns/{id}/approve` with a wrong-seller JWT → 403.
   - `POST /returns/{id}/reject` with a wrong-seller JWT → 403.
   - `POST /returns/{id}/complete` with a wrong-seller JWT → 403. (Critical — `/complete` triggers a real refund via `RefundRequestPort`.)
   - `POST /returns/{id}/disputes` with a wrong-buyer JWT → 403.

**Why a fresh buyer doubles as the wrong-seller attacker.** Only `seller1` is seeded in `infra/keycloak/vnshop-realm.json`. The gateway has no role gate on `/returns/**` (only `.anyExchange().authenticated()` covers it), so a buyer's JWT reaches order-service. `JwtPrincipalUtil.currentSellerId()` returns `jwt.sub` — the buyer's UUID, which is not `seller1`'s. `ReturnAuthorization.requireSellerOwnsReturn` throws `OrderAccessDeniedException` → mapped to 403 by `ApiExceptionHandler`. **The auth check is the first line of each use case**, so the 403 fires even though the return is in `REQUESTED` state and would be state-rejected later.

This proves the contract: *the IDOR gate runs before any state check or business logic.* That's the durable property — even if someone refactors the use case and breaks state validation, the auth check survives independently.

**Side benefit.** The setup walks `PUT /seller/orders/{subOrderId}/accept` and `PUT /seller/orders/{subOrderId}/ship` — neither was exercised by day-simulation before. The seller's full happy-path is now under test as a side effect.

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `3f51dca7`.
2. **Smoke the suite.** `cd fe && npx playwright test e2e/day-simulation.spec.ts --project=chromium --reporter=line` → 12/12 in ~13s. If the new pt15 IDOR test fails (test #11 in the run order), the most likely cause is someone reverting one of the pt15 commits in `services/order-service/src/main/java/com/vnshop/orderservice/application/`.
3. **Audit count is now 13 findings closed + 1 durable regression gate added.** The arc is complete.

## What's still missing (deferred — pt16 → pt17)

**Genuinely open code work:**
- **Audit pass on `/admin/**` endpoints.** Gateway enforces `hasRole("ADMIN")` so unauthorized callers can't reach them. Each admin handler should still verify it's not silently scoped (e.g. accidentally using `JwtPrincipalUtil.currentSellerId()` with the admin's own id). Cleanliness pass, not a security fix per se. ~1-2hr.
- **Audit pass on cart-service and notification-service.** Both take JWTs; both have stateful endpoints. Quick sweep for the same anti-patterns.

**Genuinely open operational work:**
- **PayPal capture round-trip.** Last unproven payment path. Manual browser test must run while logged in as the buyer who owns the payment (pt13/pt14 tightened the buyer check).
- **OneDrive durability.** Pretest hook handles the auto-test path; direct `npx playwright test` invocation may still need occasional rehydration if files end up as cloud-only stubs.

## Resume hint

Pick one:
- **Audit `/admin/**` and the remaining services** for the three anti-patterns from pt15. Pattern is well-established. ~2hr.
- **PayPal capture round-trip** — needs human at the browser, no code work. The pt13/pt14 fixes mean the capturer must be logged in as the buyer who placed the order.

## Anti-pattern recap (carried forward from pt15)

The merge gate for any new HTTP endpoint asks three questions:

1. **Does the wire shape contain anything authoritative?** (price, sellerId, buyerId, status, role) — if yes, delete and resolve server-side from JWT or a port lookup.
2. **Does the use case look up another resource by id?** — if yes, cross-check ownership against the JWT principal at the use-case boundary, not the controller.
3. **Is there a "wrong-X → 403" test in day-simulation?** — if no, write one.

Pt16 satisfied (3) for the four pt15 ReturnController endpoints. The contract is now self-enforcing: any future change that breaks one of these gates lights up the suite within ~13 seconds.
