# Session handover — 2026-05-25 (pt39: payment-service IAE+id-leak audit closed)

**Last commit (HEAD before this block):** `0bafa619` (`docs(pt38): IAE-as-403 sweep block — three more instances closed`)

**Gates (live stack):**
- payment-service mvn: 76 / 76 (test count unchanged — one removed, two added).
- order-service mvn: 122 / 122 (regression check; untouched this block).
- FE typecheck clean. vitest 165 / 165 (untouched).
- Workday + payment-multi-method spec: 4 / 4 in 47.2 s.
- Journey suite: 7 / 7 in 1m 06s.

## What this block was

Pt37 closed two instances of the IAE-as-403 + id-leak anti-pattern. Pt38 swept order-service and closed three more, then flagged that **payment-service had the same shape** in a different fix configuration — the auth check happens *after* the lookup (you need the payment row to know the buyer), so the pt38 collapse-into-one-OAD pattern needed adapting.

This block closes the four confirmed payment-service instances and surfaces an unrelated **functional** bug along the way: payment-service's `ApiExceptionHandler` never had a `@ExceptionHandler(OrderAccessDeniedException.class)`. Every OAD throw was falling through to the `Exception.class` catch-all and returning **500** instead of 403. Pt14 added the OAD class, pt13/pt14 added the throws, but the matching handler was never added. Tested live — capture path with a wrong buyer now correctly returns 403 + the constant message.

## The four payment-service fixes

1. **`PaymentController.capturePayPal`** — three error bodies collapsed into one OAD. Lookup ordering preserved (structural: need the row to know the buyer). The fold takes "not found / wrong method / not your buyer" and makes them a single response body, so a probe gets the same `403 not authorized to capture this payment` regardless of which branch tripped.
2. **`GetPaymentStatusUseCase.getByOrderIdForBuyer`** — same fold, plus the lookup miss switched from IAE/400 to OAD/403 so status-code-alone can't distinguish "doesn't exist" from "exists, not yours."
3. **`AdminVietQrController.confirm`** — lower-priority (admin-only), but the three messages were still embedding paymentId. Constants throughout.
4. **`ApiExceptionHandler`** — added the missing OAD → 403 handler. The fix in pt37/pt38 worked because order-service's handler had it; payment-service's didn't, so any OAD throw silently became a 500. The audit-pin tests in this block would have caught it on the next CI run anyway, but this block fixes it directly.

## Skipped (correctly)

- **`HandleVnpayIpnUseCase`** — gateway-driven, signature-verified upstream. IAE for "VNPay sent us a paymentId we don't recognize" is the right shape (wire-protocol error, not auth). The endpoint isn't caller-facing in the IDOR sense.
- **`GetPaymentStatusUseCase.getByOrderId`** — the trusted internal twin used by the gRPC server (order-service polling). No JWT in scope, no IDOR risk. The audit gate is explicitly NOT here; it's in `getByOrderIdForBuyer`.

## Files touched this block

```
M  services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/web/PaymentController.java
M  services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/web/AdminVietQrController.java
M  services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/web/ApiExceptionHandler.java       # missing 403 handler added
M  services/payment-service/src/main/java/com/vnshop/paymentservice/application/OrderAccessDeniedException.java       # javadoc pin
M  services/payment-service/src/main/java/com/vnshop/paymentservice/application/GetPaymentStatusUseCase.java
M  services/payment-service/src/test/java/com/vnshop/paymentservice/application/GetPaymentStatusUseCaseTest.java     # 1 case removed, 2 added
A  docs/SESSION-HANDOVER-2026-05-25-pt39.md
```

## Gotchas this block (extends pt38 list, #103-104)

**105. A 403 throw with no matching `@ExceptionHandler` becomes a 500.** Spring's `@ResponseStatus` annotation on a custom exception only takes effect if the framework can route to it; a `@RestControllerAdvice` with handlers for `IAE/IllegalStateException/Exception` but not for the specific exception class will catch the OAD on the `Exception.class` lane and return whatever status THAT handler declares (typically 500). The discipline: every custom exception that's meant to map to a non-default status needs an explicit `@ExceptionHandler`. The audit signal is "does the live integration test see 403, or does it see 500?" — unit tests on the use case alone can't catch this.

**106. Status-code-alone is also an oracle.** Pt38 covered the message-body oracle (gotcha #102). Pt39 surfaces the status-code oracle: `getByOrderIdForBuyer` previously threw IAE→400 for "doesn't exist" and OAD→403 for "exists, not yours". A caller can distinguish the two outcomes from status code alone — they don't even need to read the body. Auth-rejection branches must produce the SAME status code AND the same body, regardless of why they fired.

## Open thread for the next session

**Carryover from pt32-pt38:**
- PayPal capture round-trip (the larger feature — wire the FE Smart Buttons → BE capture → order status flip → refund hook).
- VNPay/MoMo `redirectUrl` from PaymentResponse.
- Kafka env-override audit on the other six services.
- R2 swap for avatar storage.

**Audit follow-ups (lower priority, the security audit is now substantially closed):**
- Sweep cart-service / inventory-service / messaging-service for the same anti-pattern. Likely-low yield but worth a grep.
- Audit FE error envelopes — the BE now returns identical bodies for "doesn't exist" and "exists, not yours"; verify the FE doesn't surface different toasts based on error-code subfields that themselves leak the distinction.

## How to resume

1. **Verify HEAD.** `git log --oneline -5` shows pt39 commits at the top.
2. **Smoke gates:**
   - `cd services/payment-service; ./mvnw test` → 76 / 76.
   - `cd services/order-service; ./mvnw test` → 122 / 122.
   - `cd fe; npx tsc --noEmit` → 0 errors.
   - `cd fe; npm test -- --run` → 165 / 165.
   - Workday suite → 3 / 3 (or 4 / 4 with payment-multi-method).
   - Journey suite → 7 / 7.
3. **Manual probe (optional):** as buyer1, `POST /payment/paypal/capture/{nonexistent-uuid}/some-paypal-id` → should be 403 `{"success":false,"message":"not authorized to capture this payment"}`. As buyer1, `GET /payment/status/{order-belonging-to-buyer2}` → should be 403 `{"success":false,"message":"not authorized to read this payment"}`. Identical body, identical status, regardless of which condition tripped.

## Final session ledger (pt27 → pt39)

- **pt27-pt34**: i18n, dark-mode, BA-grade journey suite, chapter-6 flake root-cause.
- **pt35**: payout completion audit trail. Three new gotchas (#94-96).
- **pt36**: avatar upload via MinIO with R2-swap path. Four new gotchas (#97-100).
- **pt37**: seller-side access-control hardening on Ship/Accept. Two new gotchas (#101-102).
- **pt38**: order-service sweep, three more instances closed. Two new gotchas (#103-104).
- **pt39 (this block)**: payment-service sweep complete. Functional bug found and fixed (missing `@ExceptionHandler` made every OAD a 500). Two new gotchas (#105-106). +1 BE test case net (one removed, two added).

The pt37→pt39 arc is the audit play in slow motion: spot the anti-pattern, fix the first instance, write the gotcha, sweep the rest. Each block was smaller than the last (pt37 was 2 files, pt38 was 3, pt39 is 4 files but one was a missing handler), and each surfaced one new failure mode (existence-probe oracle, status-code oracle, missing exception handler) that the prior block hadn't enumerated. The auto-memory's "post-agent quality pass" preference is what turned what could have been "find one bug, ship one fix" into "find one bug, audit the codebase, ship the audit." Worth the discipline.
