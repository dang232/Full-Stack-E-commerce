# Session handover — 2026-05-25 (pt37: shipping/accept access-control hardening)

**Last commit (HEAD before this block):** `fd11ba17` (`docs(pt36): avatar upload block — MinIO-first, R2-swappable`)

**Gates (live stack):**
- order-service mvn: 118 / 118 (was 108; +10 new across `ShipOrderUseCaseTest` + `AcceptOrderUseCaseTest`).
- FE typecheck clean. vitest 165 / 165 (untouched this block).
- Workday suite: 3 / 3 in 26.0 s.
- Journey suite: 7 / 7 in 42.9 s. Chapter 3 (seller fulfills the order) drives the corrected `ShipOrderUseCase` path under the seller's JWT and lands SHIPPED as before — the access-control tightening did not regress the happy path.

## What this block was

Pt32-pt35 carryover thread: "shipping tracking ownership check." On inspection it turned out the ownership check was *present* but raised the wrong exception type — the lookup `findSellerSubOrder()` threw `IllegalArgumentException` when the suborder didn't belong to the calling seller, which the controller mapped to **400 Bad Request** instead of **403 Forbidden**. Buyer-side `ViewOrderUseCase.viewForBuyer` had been corrected to throw `OrderAccessDeniedException` back in pt14; the two seller-side write paths (`ShipOrderUseCase`, `AcceptOrderUseCase`) had been overlooked.

The deeper issue, only visible once I read the message string, was an **authorization-test oracle**: the old message embedded the requested sellerId verbatim — `"subOrder not found for seller: " + sellerId`. A malicious seller could iterate sellerIds in the request payload and the response body would tell them whether each one had a suborder on a given orderId. Even if the status code were 403, the message body alone leaks the answer.

So the fix is two-pronged:
1. **Throw `OrderAccessDeniedException`**, not `IllegalArgumentException`, so the 403 mapping is correct (matches buyer-side parity from pt14).
2. **Use a generic constant message** — `"not authorized to ship this order"` / `"not authorized to accept this order"` — so the response body is identical for "unknown order", "wrong seller", or "right seller, wrong order". Probing reveals nothing.

The `OrderAccessDeniedException` javadoc now documents both points so a future re-implementer doesn't accidentally re-introduce the leak by adding a "helpful" detail.

## Tests added

`ShipOrderUseCaseTest` (6 cases):
- happy path — seller owns the suborder, status flips to SHIPPED, event published
- access denied for foreign seller — `OrderAccessDeniedException` with the constant message
- **message doesn't leak the requested sellerId** (the audit pin — assert `hasMessageNotContaining("guess-target-seller-x")`)
- unknown order → 400 (`IllegalArgumentException`, distinguishable from 403)
- blank carrier rejected
- blank tracking rejected

`AcceptOrderUseCaseTest` (4 cases): same shape, narrower scope (no carrier/tracking).

The leak-prevention test is the most load-bearing: the access-denied test alone wouldn't catch a future refactor that re-added the sellerId to the message.

## Files touched this block

```
M  services/order-service/src/main/java/com/vnshop/orderservice/application/ShipOrderUseCase.java
M  services/order-service/src/main/java/com/vnshop/orderservice/application/AcceptOrderUseCase.java
M  services/order-service/src/main/java/com/vnshop/orderservice/application/OrderAccessDeniedException.java
A  services/order-service/src/test/java/com/vnshop/orderservice/application/ShipOrderUseCaseTest.java
A  services/order-service/src/test/java/com/vnshop/orderservice/application/AcceptOrderUseCaseTest.java
A  docs/SESSION-HANDOVER-2026-05-25-pt37.md
```

## Gotchas this block (extends pt36 list, #97-100)

**101. `IllegalArgumentException` for "not your record" is the wrong exception.** Spring's default mapping (or a project-local `ApiExceptionHandler`) typically maps IAE to 400. That's right for "you sent a malformed payload" but wrong for "you sent a well-formed payload referring to a record you don't own." The 400-vs-403 distinction matters for clients (a retry policy on 400 vs an auth-renewal policy on 403) and for log-mining (operators tracking unauthorized-access attempts). Audit any other use case that does ownership-check-via-`orElseThrow(IllegalArgumentException)` on a record lookup — same shape, same bug.

**102. Authorization-error messages must be constant strings, never include the requested id.** Any time you build an error message with `"... " + userInputId`, you're handing the caller an oracle. The status code can be 403 and the message can still leak the answer to "does X exist for Y?" Pin generic messages with a `hasMessageNotContaining` test so a sympathetic future PR doesn't "improve" the message and reopen the leak.

## Open thread for the next session

**Still on the carryover list:**
- **PayPal capture round-trip** (auth → capture → order status flip → refund hook). Largest of the payment threads.
- **VNPay/MoMo `redirectUrl` from PaymentResponse** — surface the field so the FE can drive the gateway redirect instead of envelope-shape guessing.
- **Kafka env-override audit** on the other six services — read-only sweep first, fixes only where drift is real.
- **R2 swap for avatar storage** (gated on R2 credentials landing). One open question on URL style for virtual-host vs path-style.

**Worth also auditing** (lifted from gotcha #101 — what else is doing this?):
- Other `*UseCase` classes in order-service that do `orElseThrow(... IllegalArgumentException("not found for seller: " + sellerId))` or similar. A grep run is enough; if any are caller-facing write paths they likely have the same 400-vs-403 bug.
- product-service / seller-finance-service / cart-service for the same pattern.

## How to resume

1. **Verify HEAD.** `git log --oneline -3` shows pt37 commits at the top.
2. **Smoke gates:**
   - `cd services/order-service; ./mvnw test` → 118 / 118.
   - `cd fe; npx tsc --noEmit` → 0 errors.
   - `cd fe; npm test -- --run` → 165 / 165.
   - Workday suite → 3 / 3.
   - Journey suite → 7 / 7.
3. **Manual probe (optional):** log in as seller1, get a JWT, then `POST /sellers/me/orders/{order-belonging-to-seller2}/ship` with body `{carrier:"GHN",trackingNumber:"x"}`. Should return **403** with `{"success":false,"message":"not authorized to ship this order"}` — never seller2's id, never seller1's id, just the constant message.

## Final session ledger (pt27 → pt37)

- **pt27-pt34**: i18n, dark-mode, BA-grade journey suite, chapter-6 flake root-cause (cloud-stubs, JSX leak, dialog-onError).
- **pt35**: payout completion audit trail end-to-end. Three new gotchas (#94-96).
- **pt36**: avatar upload via MinIO with R2-swap path. Four new gotchas (#97-100).
- **pt37 (this block)**: tightened seller-side write-path access control. Two new gotchas (#101-102) — the IAE-as-403 anti-pattern and the constant-message-for-auth-errors discipline. +10 BE tests.

The story this arc tells: pt35 added an audit trail that records who acted; pt37 makes sure the acts themselves are gated correctly. The two together are the honest answer to "can we trust the seller-side write path" — the gate denies foreign callers with the right status code, the audit trail records the legitimate ones, and neither the gate nor the trail leaks identifiers a malicious caller could use to probe.
