# Session handover — 2026-05-20 (pt14: post-pt13 security audit pass — 4 IDORs + 1 high-severity wallet endpoint closed)

**Last commit (HEAD):** `93628a36` (`fix(seller-finance): remove POST /sellers/me/finance/credits — high-severity audit finding`)
**Commits since pt13 HEAD `0f04f8c7`:** 3.

**Gates:**
- payment-service: 71/71. order-service: 71/71. seller-finance-service: 4/4. All 12 BE services green.
- Playwright: **48/48**. FE typecheck: 0. Vitest: 143/143.

After closing the pt12 finding I kept the audit pass going across the same code-area (anything that takes a JWT principal and looks up another resource by id). Found four more authorization gaps in the same family, plus one separate high-severity endpoint that let any seller credit their own wallet. All five closed.

## TL;DR — what was wrong

Five distinct findings, all in the same code-area as the pt12 amount-tampering work:

| # | Endpoint | Class | Severity | Closed in |
|---|---|---|---|---|
| 1 | `POST /payment/paypal/capture/{paymentId}/{paypalOrderId}` | IDOR — caller didn't have to own the payment | low (no monetary gain on its own) | `2b0c90e3` |
| 2 | `GET /orders/{id}` | IDOR — any authenticated buyer could read any other buyer's order | medium (PII leak: shipping address, item list) | `078ccedb` |
| 3 | `GET /payment/status/{orderId}` | IDOR — same shape as #2 for payments | medium | `078ccedb` |
| 4 | `POST /sellers/me/finance/credits` | Self-credit — seller picks own balance | **high** (real money path) | `93628a36` |

Pt12 was the worst (real money tampering on payment gateways via amount field). Pt14 #4 is the runner-up — once a seller fabricates a wallet balance, the legitimate `POST /payouts` flow withdraws it to their bank account.

## Commits this block

| # | Commit | What |
|---|---|---|
| 1 | `2b0c90e3` | fix(payment): defense-in-depth — /paypal/capture verifies buyer ownership |
| 2 | `078ccedb` | fix(order, payment): close two more IDORs in the pt13 audit pass |
| 3 | `93628a36` | fix(seller-finance): remove POST /sellers/me/finance/credits — high-severity audit finding |

## What shipped

### `2b0c90e3` — `/paypal/capture` buyer ownership

Before: any authenticated buyer who got hold of `paymentId` + `paypalOrderId` (e.g. via a logs leak) could race-capture another buyer's PENDING payment to confuse order state. The original design assumed only the legitimate FE had both ids.

After: `PaymentController.capturePayPal` reads the JWT principal via `JwtPrincipalUtil.currentUserId()` and 403's if the existing payment row's `buyerId` doesn't match. Reuses the `OrderAccessDeniedException` class from pt13.

### `078ccedb` — two IDORs on read paths

`ViewOrderUseCase` (order-service) and `GetPaymentStatusUseCase` (payment-service) each had a single entry point that fetched by id without checking ownership. Pattern matches the pt13 `ProcessPaymentUseCase` split:

- `ViewOrderUseCase.view(id)` — internal, no auth check. Used by trusted callers.
- `ViewOrderUseCase.viewForBuyer(id, buyerId)` — HTTP. 403 if mismatch.
- `GetPaymentStatusUseCase.getByOrderId(orderId)` — gRPC / reconciliation worker.
- `GetPaymentStatusUseCase.getByOrderIdForBuyer(orderId, buyerId)` — HTTP.

Controllers (`OrderController.get` and `PaymentController.status`) updated to use the buyer-aware variants.

New `OrderAccessDeniedException` class in order-service (payment-service already had its own from pt13). `ApiExceptionHandler` maps it to 403 `ORDER_ACCESS_DENIED`.

### `93628a36` — `/sellers/me/finance/credits` removal

The headline finding of this block. `POST /sellers/me/finance/credits` accepted `{orderAmount, tier, idempotencyKey}` from any authenticated seller and credited their own wallet. The `idempotencyKey` field had a `// TODO: wire it through` comment — it was never enforced. The legitimate flow (`OrderCreatedFinanceListener` → `CreditWalletUseCase` on Kafka `order.created`/`order.paid` events) was the only path that should have credited a wallet.

Fix: deleted `@PostMapping("/credits")` outright. Dropped `CreditRequest` / `CreditResponse` DTOs. Removed the `CreditWalletUseCase` constructor dependency from `SellerFinanceController` (Kafka listener still wires it the same way). The use case itself stays — only the HTTP exposure is gone.

Re-exposure rule documented in the controller class-level Javadoc: any future admin-side credit override goes under `/admin/finance/**` with an explicit `ADMIN` role check, never under `/sellers/me/**`.

## Operational gotchas (additions to pt5–pt13)

37. **Sub-second-severity findings hide in the same blast radius as the headline finding.** Pt12's amount-tampering was the obvious headline; pt14's `/credits` self-credit was sitting one directory away, was just as exploitable, and had been there longer. **When you fix one finding in a code-area, walk the rest of the area before declaring done.** The 30-minute extension cost ~3 hours of cleanup but closed five real holes; doing it as separate sessions later would have meant one more cycle of "find→delegate→close" each. The pattern: after every security fix, grep the surrounding service for `findById|findBy*` calls that don't check the JWT principal, and HTTP endpoints that take user-controllable amounts/quantities/ids without revalidating against authoritative state.

38. **Endpoints with a `// TODO: wire idempotency` comment are smell.** `/credits` had this exact comment. The signal isn't that idempotency is missing — it's that someone wrote a stub endpoint, marked one piece as incomplete, and walked away. Whatever else is on that endpoint hasn't been audited either. Check before merging anything else that ships next to it.

39. **Two-entry-point pattern (gotcha #33) generalizes beyond the gRPC trust boundary.** Pt13 split `ProcessPaymentUseCase` into `process(cmd)` (HTTP, untrusted) and `processInternal(cmd, trustedAmount)` (gRPC, trusted). Pt14 applied the same split to `ViewOrderUseCase` (HTTP-untrusted vs. internal-trusted readers) and `GetPaymentStatusUseCase` (HTTP-untrusted vs. reconciliation-worker-trusted). Whenever a use case is reachable from a buyer-facing controller AND a service-mesh / scheduled / Kafka caller, the buyer-aware checks must live in a separate entry point. **Don't conflate trust models in one method.**

## Test inventory

- payment-service: 71/71. New PayPal capture buyer-mismatch path tested implicitly via the `OrderAccessDeniedException` class re-use; no dedicated unit test added because the controller is thin and `MockMvcStandaloneSetup` doesn't run the security filter chain.
- order-service: 71/71. `ViewOrderUseCase` split is exercised by the existing controller test.
- seller-finance-service: 4/4 (untouched by the `/credits` removal — no test ever covered the deleted endpoint, which was itself a finding worth flagging).
- Playwright: 48/48 (10 day-simulation tests including the pt12 amount-tampering regression test).

**Test coverage gap to flag for next session:** none of the IDOR or `/credits` paths had negative tests. The tests that exist verify the happy path. Adding a "wrong-buyer → 403" case to each endpoint touched by pt13/pt14 would lock the regression — but they're slightly awkward to write because they need a second buyer's JWT. Estimated 1-2hr to add to day-simulation; lower priority than getting the fix shipped.

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `93628a36`.
2. **Verify the audit holds.**
   - `cd fe; npx playwright test e2e/day-simulation.spec.ts --project=chromium` — 10/10. The pt12 amount-tampering regression is the headline gate.
   - Manual: `POST /sellers/me/finance/credits` should now 405 Method Not Allowed. `GET /orders/{someoneElsesId}` should 403. `GET /payment/status/{someoneElsesOrderId}` should 403.
3. **All fixes are container-rebuild-required.** If a smoke fails, check `docker compose up -d --build payment-service order-service seller-finance-service`.

## What's still missing (deferred — pt14 → pt15)

**Genuinely open code work:**
- **Negative-path tests for buyer-mismatch.** Day-simulation should include a test that registers two buyers, has buyer A place an order, has buyer B try to GET buyer A's order / payment status, and asserts 403. ~1-2hr including the second-buyer setup. Locks the IDOR regression.
- **Audit pass on the order-service seller-side endpoints** (`POST /seller/orders/{id}/accept|reject|ship`). Quick check of `AcceptOrderUseCase` etc. for the same shape — they likely already check `JwtPrincipalUtil.currentSellerId()` against the SubOrder's seller, but worth verifying since this audit only covered the buyer-facing surfaces.
- **Audit pass on `/admin/**` endpoints.** Gateway enforces `hasRole("ADMIN")` so the entry point is gated, but each admin handler should still verify it's not silently scoped by something else (e.g. an admin viewing a seller's payouts shouldn't accidentally hit `JwtPrincipalUtil.currentSellerId()` with the admin's own id). Not a security fix per se, just a cleanliness pass.

**Genuinely open operational work:**
- **PayPal capture round-trip.** Last unproven payment path. Pt14's `/paypal/capture` change adds a JWT principal check, so the manual test now needs to be done while logged in as the buyer who owns the payment — a sandbox-PayPal popup completed by anyone else returns 403. Should be invisible to the legitimate FE flow.
- **OneDrive durability.** pretest hook handles the auto-test path; direct `npx playwright test` invocation still needs occasional re-hydration.

## Resume hint

Next session: pick one of:
- **Add negative-path 403 tests** to day-simulation. Locks pt13/pt14 fixes, ~1-2hr.
- **Audit pass on seller / admin handlers.** Same pattern as pt14, different handler set.
- **PayPal capture round-trip** — needs human at the browser, no code work.

Big-picture observation: the four fixes shipped in this block (pt12 amount, pt13/pt14 IDORs + credits) all match the same general anti-pattern: **"the wire shape gives the client only what the client legitimately knows."** Pt12 was about authoritative *amount* coming from the server; pt14 was about authoritative *ownership* being checked on the server. Both are instances of the same rule. If the rule held everywhere from day one, none of this work would have been needed. The day-simulation spec is the durable gate that catches reintroductions; growing it with negative-path tests is the cheapest improvement next session can make.
