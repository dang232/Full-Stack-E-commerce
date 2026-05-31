# Session handover — 2026-05-20 (pt12: SECURITY FINDING — payment amount tampering)

**Last commit (HEAD):** `83cfe042` (`docs(pt11): handover for /checkout/calculate normalization + Kafka partition bump`)
**Status:** No code changes this block. This handover documents a **high-severity security finding** discovered during a follow-up audit after the pt10/pt11 wire-shape normalization work, and lays out the plan for the next session to close it.

---

# Finding

`POST /payment/{stripe,paypal,vietqr,vnpay,momo}/create` all accept a client-supplied `amount` and pass it directly to the payment gateway. **There is no server-side validation that the amount matches the order's actual total.** A buyer can place an order for any value, then create a payment for any other value, and the gateway will charge the smaller amount. The order will then be marked PAID by the existing IPN/webhook flow.

## Severity

- **Confidentiality:** N/A.
- **Integrity:** HIGH — order state ends up COMPLETED with a payment that doesn't match the actual basket total.
- **Availability:** N/A.
- **Direct financial impact:** YES — real money flows through Stripe / PayPal / VietQR / VNPay / MoMo at the wrong amount.

Same *class* of finding as pt9's `/orders` price-tampering hole (which was closed in pt10 by deleting `unitPriceAmount` from the wire shape). This one is worse because the wire payload is denominated in the same currency the gateway charges — there's no FE-side reconciliation step that would catch it.

## Reproduction (against current HEAD)

1. Authenticate as `buyer1`/`test`. Capture the access token.
2. Place a real order via `POST /orders` for, say, **1,000,000 VND**. Save the `orderId` from the response.
3. Call `POST /payment/stripe/create` with body:
   ```json
   { "orderId": "<from step 2>", "buyerId": "<your buyerId>", "amount": 1 }
   ```
4. Stripe returns a `clientSecret` for a 1-cent (1-VND-equivalent) PaymentIntent.
5. Confirm that intent on the FE (or via Stripe's confirm endpoint with a test card).
6. Stripe webhook fires; `StripeWebhookController` records the payment as COMPLETED.
7. The order is now marked PAID for **1 VND** when the buyer actually owes 1,000,000 VND.

The same shape works for PayPal (Smart Buttons confirm whatever amount the BE Order has), VietQR (the QR encodes the BE-supplied amount), VNPay/MoMo (the redirect URL is signed with the BE-supplied amount).

## Code path (file:line)

- **FE call site:** `fe/src/app/lib/api/endpoints/payment.ts:53-73` — `stripeCreate`, `paypalCreate`, `vietqrCreate` all accept `{orderId, buyerId, amount}` and POST it.
- **BE wire shape:** `services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/web/PaymentRequest.java` — `record PaymentRequest(orderId, buyerId, amount)` with `@NotBlank @Positive` validation but no cross-check.
- **BE controllers (5 endpoints):** `services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/web/PaymentController.java`
  - `createVnpay` — line 74-80
  - `createMomo` — line 94-100
  - `createVietQr` — line 116-130
  - `createPayPal` — line 144-165
  - `createStripe` — line 209-230
  - All five call `processPaymentUseCase.process(new ProcessPaymentCommand(request.orderId(), request.buyerId(), request.amount(), ...))` — `request.amount()` is the client-supplied value.
- **Use case:** `services/payment-service/src/main/java/com/vnshop/paymentservice/application/ProcessPaymentUseCase.java:80-143`. The amount only flows into `Payment.pending(...)` and the gateway call. `computeRequestHash()` (line 156-170) hashes it for idempotency-key dedup, but never validates against any other source of truth.
- **No order lookup:** `grep -r "OrderClient\|orderTotal\|finalAmount\|GrpcOrder" services/payment-service/src/main/java` returns zero matches. payment-service has no dependency on order-service today.

## Auxiliary issues

- **buyerId is also client-supplied.** `request.buyerId()` is whatever the client sent, never compared against the JWT's `sub`. A malicious buyer could submit another user's `buyerId` and (combined with the amount tampering above) cause confusion in payment records or admin tooling. Lower-severity than the amount issue, but should be fixed in the same pass.
- **`paypalCapture(paymentId, paypalOrderId)`** at `PaymentController.java:177-200` is fine — it looks up the existing payment by `paymentId` and validates it's a PayPal payment, so the gateway charge has already been gated by the prior `createPayPal` call (which is the vulnerable one).
- **`codConfirm`** is COD — no real money involved at this step, just a status flip. Lower priority but should still drop `amount` for consistency.

---

# Plan for next session

Goal: BE looks up the authoritative order amount + buyerId on every `*Create` call and uses those values, ignoring whatever the client sent. Wire shape slimmed to `{orderId}` only. Buyer authorization checked from JWT. Gateway charges always match the order total.

Estimate: 4-6 hours focused work including tests. Touches the security boundary and real money paths — should NOT be cut short or merged without the day-simulation tampering test passing.

## Step 1 — Add an order-lookup port to payment-service

New domain port:
```java
// services/payment-service/src/main/java/com/vnshop/paymentservice/domain/port/out/OrderCatalogPort.java
public interface OrderCatalogPort {
    Optional<OrderSnapshot> findByOrderId(String orderId);
}
```

`OrderSnapshot` carries `{orderId, buyerId, finalAmount: Money, status}`. Status is needed so we can refuse payments on already-PAID, CANCELLED, or non-existent orders.

## Step 2 — Implement `OrderCatalogAdapter` (HTTP, mirrors `ProductCatalogAdapter` from pt10)

- HTTP client to `http://order-service:8091/orders/{id}` (verify port via `docker port vnshop-order-service` — pt10 burned time on this with product-service). gRPC is also available (`GrpcPaymentRequestAdapter` in order-service is the existing direction; we'd be adding the reverse), but HTTP is simpler and the call is rare (once per checkout).
- Behind a resilience4j circuit breaker, same config as pt10's `productServiceCircuitBreaker`.
- 1s connect, 2s read timeout.
- 404 → `Optional.empty()`, anything else → `OrderCatalogUnavailableException` → 503.
- DTOs use `@JsonIgnoreProperties(ignoreUnknown = true)` — order-service uses the standard `{success, message, data, ...}` envelope (gotcha #30).
- Note: order-service's `GET /orders/{id}` requires JWT and checks `JwtPrincipalUtil.currentUserId()`. payment-service may need to forward the buyer's JWT, OR we expose a service-to-service endpoint that validates by service-account claim. Easier path: forward the incoming `Authorization` header — payment-service's controllers already receive the JWT from the gateway.

## Step 3 — Slim `PaymentRequest`

```diff
 public record PaymentRequest(
-        @NotBlank String orderId,
-        @NotBlank String buyerId,
-        @NotNull @Positive BigDecimal amount
+        @NotBlank String orderId
 ) {
 }
```

`buyerId` and `amount` deleted. Buyer is resolved from the JWT in the controller; amount is resolved from the order lookup.

## Step 4 — Rewrite `ProcessPaymentUseCase.process`

```java
public Payment process(ProcessPaymentCommand command) {
    OrderSnapshot order = orderCatalogPort.findByOrderId(command.orderId())
        .orElseThrow(() -> new OrderNotFoundException(command.orderId()));
    if (!order.buyerId().equals(command.buyerId())) {
        throw new OrderAccessDeniedException(...);
    }
    if (order.status() != PENDING) {
        throw new OrderNotPayableException("order status: " + order.status());
    }
    BigDecimal authoritativeAmount = order.finalAmount().amount();
    // ... rest of the existing flow, using authoritativeAmount instead of command.amount() ...
}
```

`ProcessPaymentCommand` keeps `buyerId` (sourced from JWT in the controller — see Step 6) and drops `amount`. The idempotency-hash composition uses `authoritativeAmount` so legitimate retries with the same JWT still hit the same hash.

## Step 5 — Map new exceptions in `ApiExceptionHandler`

- `OrderNotFoundException` → 404 `ORDER_NOT_FOUND`
- `OrderAccessDeniedException` → 403 `ORDER_ACCESS_DENIED`
- `OrderNotPayableException` → 409 `ORDER_NOT_PAYABLE` (with the status in the message)
- `OrderCatalogUnavailableException` → 503 `ORDER_CATALOG_UNAVAILABLE`

## Step 6 — Update all 5 `*Create` controllers

Every `processPaymentUseCase.process(...)` call site changes from:
```java
new ProcessPaymentCommand(request.orderId(), request.buyerId(), request.amount(), method, idempotencyKey)
```
to:
```java
new ProcessPaymentCommand(
    request.orderId(),
    JwtPrincipalUtil.currentUserId(),  // or whatever the existing pattern is
    method,
    idempotencyKey)
```

Verify the `JwtPrincipalUtil` path — payment-service may not have the same util as order-service. If not, decode the buyer from `Authentication` directly.

## Step 7 — Tests

New `ProcessPaymentUseCaseSecurityTest`:
1. Happy path: client sends `{orderId}`, BE resolves a 100,000-VND order, gateway is called with 100,000 VND.
2. Tampering attempt: client sends `{orderId}` for someone else's order → 403, gateway never called.
3. Status check: order is COMPLETED already → 409, gateway never called.
4. Status check: order is CANCELLED → 409, gateway never called.
5. Order not found → 404, gateway never called.
6. order-service down → 503, gateway never called, circuit breaker opens after threshold.
7. Idempotent retry with same JWT and same orderId → returns the cached payment, no second gateway call.

The "gateway never called" assertion is the critical one. Use a recording stub (the existing `CapturingPaymentGateway` pattern from `MomoCallbackServiceTest` is the model).

## Step 8 — Update FE `payment.ts`

```diff
 export const stripeCreate = (
-  body: { orderId: string; buyerId: string; amount: number },
+  body: { orderId: string },
   idempotencyKey?: string,
 ) => api.post("/payment/stripe/create", stripeCreateSchema, body, { idempotencyKey });
```

Same for `paypalCreate`, `vietqrCreate`, `vnpayCreate`, `momoCreate`. `codConfirm` already only takes `{orderId}` so it's untouched.

## Step 9 — Update FE call sites in `CheckoutPage.tsx`

`fe/src/app/pages/checkout/CheckoutPage.tsx` calls these in `handlePlaceOrder` after `placeOrder` returns. The `amount` argument that was being passed in is the same one the BE now resolves itself, so just delete the field. `buyerId` was being filled from `profile?.userId` — also delete.

## Step 10 — Add the regression test to `day-simulation.spec.ts`

```ts
test("payment-create rejects client-supplied amount mismatch", async ({ request }) => {
  const auth = await registerBuyer(request);
  const headers = authHeaders(auth);
  // Place a real order...
  // Attempt to pay with amount=1 instead of order.finalAmount...
  // Assert: 400 or 403, payment NOT created.
});
```

This is the durable gate. Once it's green, the finding is closed.

## Step 11 — Verify the existing reconciliation worker doesn't paper over rejections

`PaymentReconciliationWorker` (mentioned in pt5/pt6 notes — find via Glob `**/PaymentReconciliation*`) iterates over PENDING payments and queries the gateway. It must not silently re-attempt rejected payments or mask the new 4xx responses as transient. Worth a quick trace.

## Step 12 — Update handover

Write `SESSION-HANDOVER-2026-05-20-pt13.md` summarising the fix, with before/after diff snippets and the `day-simulation` test result. Mark the finding closed in this doc.

---

# Out of scope for this fix (note for future)

- **VNPay/MoMo IPN handlers** are already correct — they use the BE-stored `Payment.amount()` from the `pending` row, not anything client-supplied. The fix above tightens the input gate; the IPN gate is fine.
- **Stripe webhook signature verification** is correct — `StripeWebhookController` validates the signature before promoting the payment. Once the create-path uses the authoritative amount, the webhook's `paymentIntent.amount` will match.
- **PayPal capture round-trip** (the long-standing pt7+ deferred item) is independently still needed. The fix here makes it cleaner — the FE no longer needs to know the amount on the capture side, just the IDs. But the round-trip itself still needs a human at the browser.
- **gRPC instead of HTTP** for the order lookup. Order-service exposes a gRPC server already. HTTP is simpler for a one-shot lookup; gRPC would be more consistent if payment-service ever needs richer order data. Defer to a later cleanup.

---

# Gates to confirm before claiming done

- `cd services/payment-service && ./mvnw test` — 67/67 + the new security tests = 74/74 (or whatever the exact count is).
- `cd services/order-service && ./mvnw test` — 71/71 untouched.
- `cd fe && npx tsc --noEmit` — 0.
- `cd fe && npx playwright test e2e/day-simulation.spec.ts --project=chromium` — 10/10 (was 9, +1 for the new tampering test).
- Manual: place an order, attempt the exploit from the "Reproduction" section above, confirm 4xx.

---

# Hand-off notes from operator

<!-- USER: add anything specific you want done / not done / clarified for next session here -->

---

# Resume hint

Next session opens this doc, executes the plan top-to-bottom, runs the gates, writes pt13. The finding is severe enough that it deserves the entire session — don't try to bundle it with other deferred work. Once closed, the only remaining headline item from the pt7+ deferred queues is the PayPal capture round-trip (browser-driven, human-required).
