# Session handover — 2026-05-25 (pt38: IAE-as-403 sweep across order-service)

**Last commit (HEAD before this block):** `f3e9ec6c` (`docs(pt37): seller-side access-control hardening block`)

**Gates (live stack):**
- order-service mvn: 122 / 122 (was 118; +4 net — new `RejectOrderUseCaseTest`, plus an updated case in `ApproveReturnUseCaseTest` that pins the new constant-message behavior).
- FE typecheck clean. vitest 165 / 165 (untouched this block).
- Workday suite: 3 / 3 in 40.2 s.
- Journey suite: 7 / 7 in 57.4 s. Chapter 3 (seller fulfills) and the implicit return-flow paths still pass through the tightened gates.

## What this block was

Pt37 closed the IAE-as-403 anti-pattern on `ShipOrderUseCase` + `AcceptOrderUseCase`. Gotcha #101 noted that the same shape probably existed elsewhere; pt38 is the sweep that confirms it does, fixes what's confirmed, and pins it with tests.

The audit ran a single grep across all seven services for `orElseThrow(() -> new IllegalArgumentException(... + sellerId|buyerId|userId|keycloakId))`. Results triaged into four buckets:

1. **Confirmed pt37-shape — fixed in this block:**
   - `RejectOrderUseCase.findSellerSubOrder` — exact pt37 shape (IAE + `+ sellerId`).
   - `ReturnAuthorization.requireSellerOwnsReturn` — already 403 (good), but message embedded both sellerId and returnId.
   - `RequestReturnUseCase.request` — three sibling lookups raising three distinct IAE bodies, which together formed an existence-probe oracle for subOrderIds. Collapsed to one OAD with a constant message.

2. **Trusted internal paths** (skipped — these aren't directly caller-facing): `ViewOrderUseCase.view` (the trusted twin of `viewForBuyer`, used by other use cases that have already authorized), `OrderQueryUseCase`, `ListPendingOrdersUseCase`, `InvoiceUseCase.findSubOrder`.

3. **Legitimate 400s** (the id is yours, not someone else's): `ApproveReturnUseCase.approve(returnId)` — `"return not found: " + returnId` is a 400 because returnId came from the path; the seller submitted it, the seller already knows it. Same for `RejectReturnUseCase`, `CompleteReturnUseCase`, `DisputeUseCase`.

4. **Different shape — separate audit:** `payment-service`'s `PaymentController.capturePayPal` and `AdminVietQrController` have IAE+id-leak on lookups, but the buyer/admin auth check happens *after* the lookup via `existing.buyerId().equals(caller)`. Same problem (different status codes for "doesn't exist" vs "not yours") but the fix is to move the auth check before the lookup or fold both into one constant-message OAD. Out of scope for pt38; flagged for a follow-up.

## The interesting finding

`RequestReturnUseCase` was the most consequential of the three. It had **three** distinct error bodies:
- `"subOrder not found: " + subOrderId` (lookup miss on the first `findBySubOrderId`)
- `"return buyer does not own order"` (ownership check fails)
- `"subOrder not found: " + subOrderId` (filter miss inside the order — different code path, same message)

A buyer iterating subOrderIds could distinguish "doesn't exist anywhere" (response 1) from "exists but not yours" (response 2) just from the message text. Even with all three returning 400, the response body is the oracle. Collapsed into a single OAD with one constant message — the response is now identical regardless of which condition tripped. Still 403, still rejected, but no more inference channel.

## Files touched this block

```
M  services/order-service/src/main/java/com/vnshop/orderservice/application/RejectOrderUseCase.java
M  services/order-service/src/main/java/com/vnshop/orderservice/application/RequestReturnUseCase.java
M  services/order-service/src/main/java/com/vnshop/orderservice/application/ReturnAuthorization.java
A  services/order-service/src/test/java/com/vnshop/orderservice/application/RejectOrderUseCaseTest.java        # +3 tests
M  services/order-service/src/test/java/com/vnshop/orderservice/application/ApproveReturnUseCaseTest.java     # +1 audit-pin test, +1 updated
A  docs/SESSION-HANDOVER-2026-05-25-pt38.md
```

## Gotchas this block (extends pt37 list, #101-102)

**103. Existence-probe oracles can hide in branch-distinct messages.** Two 403 responses with two different error bodies are no better than 200/404 for inference. The audit signal isn't "does this throw OAD" — it's "do all the failure-mode bodies look identical to a network observer." `RequestReturnUseCase`'s three distinct messages were the clearest case: same status code, three branches, three readable strings. Collapse all auth-rejection branches into one constant string, even at the cost of slightly less helpful debug info. (If the developer needs the branch info, structured logging at the throw site is the right channel — it doesn't go on the wire.)

**104. The trusted-internal-twin pattern is a load-bearing distinction.** `ViewOrderUseCase` has `view(orderId)` (trusted, for internal callers that already authorized) and `viewForBuyer(orderId, buyerId)` (HTTP-facing, ownership-checked). The audit must distinguish these — `view()` is fine raising IAE because it's never a caller-facing endpoint; only the caller-facing twin needs the constant-message-OAD treatment. Mistaking trusted internal paths for HTTP-facing ones leads to over-correction (and gratuitous 403s for legitimate internal lookups).

## Open thread for the next session

**Higher priority:**
- **payment-service IAE+id-leak audit** (out-of-scope for pt38, separate fix shape):
  - `PaymentController.capturePayPal` — `existing.buyerId().equals(caller)` check happens AFTER the IAE lookup. Either move the check before the lookup, or fold the two errors into one constant-message OAD.
  - `AdminVietQrController.something` — same pattern with admin role check.
  - `HandleVnpayIpnUseCase` — IPN endpoint, may not need 403 (gateway-driven), but the message still leaks `paymentId`.

**Carryover from pt32-pt37:**
- PayPal capture round-trip (the larger feature, distinct from the auth-pattern audit above).
- VNPay/MoMo `redirectUrl` from PaymentResponse.
- Kafka env-override audit.
- R2 swap for avatar storage.

## How to resume

1. **Verify HEAD.** `git log --oneline -3` shows pt38 commits at the top.
2. **Smoke gates:**
   - `cd services/order-service; ./mvnw test` → 122 / 122.
   - `cd fe; npx tsc --noEmit` → 0 errors.
   - `cd fe; npm test -- --run` → 165 / 165.
   - Workday suite → 3 / 3.
   - Journey suite → 7 / 7.
3. **Manual probe (optional):** as seller2, `POST /sellers/me/orders/{order-belonging-to-seller1}/reject` should return 403 `{"success":false,"message":"not authorized to reject this order"}`. As any buyer, `POST /returns` against a foreign subOrderId or a real-but-foreign one should return identical bodies.

## Final session ledger (pt27 → pt38)

- **pt27-pt34**: i18n, dark-mode, BA-grade journey suite, chapter-6 flake root-cause.
- **pt35**: payout completion audit trail. Three new gotchas (#94-96).
- **pt36**: avatar upload via MinIO with R2-swap path. Four new gotchas (#97-100).
- **pt37**: seller-side access-control hardening on Ship/Accept. Two new gotchas (#101-102).
- **pt38 (this block)**: completed the order-service sweep that pt37's gotcha #101 implied was waiting. Three more confirmed instances fixed; payment-service has the same anti-pattern in a different fix-shape, flagged for a separate block. Two new gotchas (#103-104). +4 BE tests.

The story this block tells: writing down the anti-pattern (gotcha #101) and the discipline (#102) at the moment of the first fix means the sweep is cheap when you do it next session — the grep takes seconds, the triage takes minutes, the fixes are shape-identical. The auto-memory's "post-agent quality pass" preference is what made this block possible: pt37 didn't just fix two files, it noted the *shape* of what was fixed, and that note is what made the pt38 audit a 30-minute job instead of a re-discovery.
