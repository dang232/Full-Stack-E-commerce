# Session handover — 2026-05-25 (pt40: status-code oracle closed on order-service lookup misses)

**Last commit (HEAD before this block):** `b6bb96eb` (`docs(pt39): payment-service audit block + missing 403 handler`)

**Gates (live stack):**
- order-service mvn: 122 / 122 (test count unchanged — 4 cases re-pinned to the new contract).
- payment-service mvn: 76 / 76 (untouched).
- FE typecheck clean. vitest 165 / 165 (untouched).
- Workday suite: 3 / 3 in 26.0 s.
- Journey suite: 7 / 7 in 35.4 s.

## What this block was

Pt39's gotcha #106 named the status-code oracle: even with identical message bodies, two different status codes for two different auth-rejection branches still leak existence info to a probe. Pt40 applies that finding to the six order-service caller-facing use cases that still had the split — `findOrder/findReturn` raised IAE→400 on lookup miss, while the seller-ownership branch raised OAD→403. The two outcomes were distinguishable from status code alone.

The fix: fold the lookup-miss branches into the same OAD throw the use case already uses for the ownership-rejection branch. Six use cases, identical fix shape, no new tests added — four existing tests had been pinning the prior IAE contract and were re-pinned to the new constant-message OAD.

## The interesting nuance — gotcha #107

`CompleteReturnUseCase` has *three* lookups in sequence:

1. `returnRepository.findById(returnId)` — caller-facing, can be probed.
2. `ReturnAuthorization.requireSellerOwnsReturn(...)` — the gate.
3. `orderRepository.findById(returnObject.orderId())` + the subOrder filter — happens *after* the gate passed.

Lookup #1 is a probe channel — folded into OAD.

Lookups #2/#3 happen only after the seller proved ownership of a Return that points at an Order. If the Order is then missing, **the caller has no information advantage** — they already proved ownership of a row that references the missing referent. That's a referential-integrity bug, not a probe channel. Switched those throws to `IllegalStateException` ("return points at missing order") which the existing handler maps to 503. A gate-passed caller seeing 503 here is correct: "your request was authorized, but the BE is in an inconsistent state and can't fulfill it."

This is the **post-gate vs pre-gate** distinction: error visibility *before* an auth gate is a probe channel and must be flattened; error visibility *after* an auth gate is debug surface and should stay specific.

## Files touched this block

```
M  services/order-service/src/main/java/com/vnshop/orderservice/application/ShipOrderUseCase.java
M  services/order-service/src/main/java/com/vnshop/orderservice/application/AcceptOrderUseCase.java
M  services/order-service/src/main/java/com/vnshop/orderservice/application/RejectOrderUseCase.java
M  services/order-service/src/main/java/com/vnshop/orderservice/application/ApproveReturnUseCase.java
M  services/order-service/src/main/java/com/vnshop/orderservice/application/RejectReturnUseCase.java
M  services/order-service/src/main/java/com/vnshop/orderservice/application/CompleteReturnUseCase.java
M  services/order-service/src/test/java/com/vnshop/orderservice/application/ShipOrderUseCaseTest.java
M  services/order-service/src/test/java/com/vnshop/orderservice/application/AcceptOrderUseCaseTest.java
M  services/order-service/src/test/java/com/vnshop/orderservice/application/ApproveReturnUseCaseTest.java
M  services/order-service/src/test/java/com/vnshop/orderservice/application/CompleteReturnUseCaseTest.java
A  docs/SESSION-HANDOVER-2026-05-25-pt40.md
```

## Gotchas this block (extends pt39 list, #105-106)

**107. Pre-gate errors are probe channels, post-gate errors are debug surface.** Once an auth gate has passed, downstream lookup misses (`return.orderId()` doesn't resolve, the SubOrder embedded in the order can't be found by id) are referential-integrity bugs, not authorization-test oracles — the caller has already proved ownership of a row that references the missing referent. Don't reflexively flatten *every* `orElseThrow` to OAD; doing so loses the diagnostic surface for real BE bugs (stale projections, missing referents, race-corrupted writes). The discipline: only flatten errors that fire *before* an auth gate. Errors *after* a gate that's passed should stay specific (IllegalStateException, NPE — anything that surfaces "your request was authorized, the BE is broken"). The `CompleteReturnUseCase` body is the canonical shape.

## Audit chapter — closed

Five-block arc spanning pt37-pt40 closes the IAE-as-403 + id-leak audit:
- **pt37**: pattern named (gotchas #101-102), 2 fixes in order-service.
- **pt38**: order-service sweep (gotchas #103-104), 3 more fixes.
- **pt39**: payment-service sweep (gotchas #105-106), 4 fixes including a missing exception handler.
- **pt40 (this)**: residual status-code oracle on lookup misses, 6 folds, gotcha #107.

A grep for the original anti-pattern across all seven services now returns:
- order-service: only trusted internal paths remain (ViewOrderUseCase.view, OrderQueryUseCase, ListPendingOrdersUseCase, InvoiceUseCase) — these aren't HTTP-facing.
- payment-service: only HandleVnpayIpnUseCase remains, which is gateway-driven and signature-verified upstream (IAE is correct for a wire-protocol error).
- review-service / product-service: review/question endpoints are public-readable, no IDOR risk.
- cart-service / inventory-service / messaging-service / user-service / seller-finance-service: no caller-facing matches.

The audit is substantively closed. Any future find should slot into one of the seven gotchas (#101-107) rather than uncover a new failure mode.

## Open thread for the next session

**Carryover from pt32-pt38 (the audit blocks didn't touch these):**
- **PayPal capture round-trip** — wire the FE Smart Buttons → BE capture → order-status flip → refund hook. The largest of the payment threads.
- **VNPay/MoMo `redirectUrl` from PaymentResponse** — surface the field cleanly so the FE drives the gateway redirect.
- **Kafka env-override audit** on the other six services — read-only sweep.
- **R2 swap for avatar storage** — gated on R2 credentials.

## How to resume

1. **Verify HEAD.** `git log --oneline -5` shows pt40 commits at the top.
2. **Smoke gates:**
   - `cd services/order-service; ./mvnw test` → 122 / 122.
   - `cd services/payment-service; ./mvnw test` → 76 / 76.
   - `cd fe; npx tsc --noEmit` → 0 errors.
   - `cd fe; npm test -- --run` → 165 / 165.
   - Workday suite → 3 / 3.
   - Journey suite → 7 / 7.
3. **Manual probe:** as seller2, `POST /sellers/me/orders/{nonexistent-uuid}/ship` and `POST /sellers/me/orders/{order-belonging-to-seller1}/ship`. Both should return **403** with `{"success":false,"message":"not authorized to ship this order"}` — same status, same body, indistinguishable.

## Final session ledger (pt27 → pt40)

- **pt27-pt34**: i18n, dark-mode, BA-grade journey suite, chapter-6 flake root-cause.
- **pt35**: payout audit trail. Gotchas #94-96.
- **pt36**: avatar upload, MinIO+R2-swap. Gotchas #97-100.
- **pt37**: Ship/Accept access-control. Gotchas #101-102.
- **pt38**: order-service IAE-as-403 sweep. Gotchas #103-104.
- **pt39**: payment-service sweep + missing 403 handler. Gotchas #105-106.
- **pt40 (this)**: status-code oracle close on lookup misses. Gotcha #107. Audit chapter closed.

The arc landed seven gotchas in four blocks. The discipline that made it possible is the auto-memory's "post-agent quality pass" — without writing down the *shape* of each fix at the moment it landed, every subsequent block would have rediscovered the previous one's failure mode. With the discipline, each block was smaller than the last (pt37: 2 files; pt38: 3; pt39: 4 files but one was a missing handler; pt40: 6 mechanical folds). The audit is closed not because we ran out of bugs but because we ran out of *kinds* of bugs to find — every match for the original grep now slots into one of seven named patterns.
