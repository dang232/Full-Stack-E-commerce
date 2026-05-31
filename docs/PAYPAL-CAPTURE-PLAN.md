# PayPal Capture Round-Trip — Implementation Plan

Generated: 2026-05-27. Based on codebase state at HEAD (c8b8c4a8).

---

## Today's Status

The PayPal integration is substantially further along than the pt41 handover implies. Both the FE Smart Buttons component and the BE create/capture endpoints are **fully implemented and wired**. What is missing is the downstream effect of a successful capture: the order-service `paymentStatus` field is never flipped to `COMPLETED` because the `PaymentCallbackOutbox` in payment-service has no relay publisher, and order-service has no `@KafkaListener` for a `payment.completed` event. The refund path is also incomplete: order-service publishes `payment.refund_requested` via its outbox, but payment-service has no listener for that topic and `PayPalGateway` has no `refund()` method.

Concretely:

- `fe/src/app/components/checkout/PayPalPaymentSection.tsx` — real `@paypal/react-paypal-js` Smart Buttons, calls `paypalCreate` then `paypalCapture`, shows error toast on failure, calls `onCompleted()` on success. Gated on `VITE_PAYPAL_ENABLED=true` + `VITE_PAYPAL_CLIENT_ID`.
- `fe/src/app/lib/api/endpoints/payment.ts:58-68` — `paypalCreate` and `paypalCapture` typed and wired to the correct BE paths.
- `fe/src/app/pages/checkout/CheckoutPage.tsx:329-335` — PAYPAL branch routes to the success screen where `PayPalPaymentSection` is rendered.
- `services/payment-service/.../web/PaymentController.java:145-210` — both `POST /payment/paypal/create` and `POST /payment/paypal/capture/{paymentId}/{paypalOrderId}` are implemented, auth-checked, and call `PaymentPromotionService.promote()`.
- `services/payment-service/.../paypal/PayPalGateway.java` — real REST calls to PayPal v2 Checkout API with per-call OAuth bearer, VND→USD FX via `FrankfurterFxAdapter`, capture-id extraction from nested `purchase_units`.
- `services/payment-service/.../paypal/PayPalGatewayTest.java` — 4 unit tests covering createOrder, capture, 4xx error, and fallback capture-id.
- `services/payment-service/src/main/resources/application.yml:39-43` — `PAYPAL_ENABLED`, `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET`, `PAYPAL_MODE` all env-var driven, defaulting off.
- `docker-compose.yml:383-384,747-762` — FE and BE env vars wired, defaulting off.

**Gaps (the actual work):**

1. `PaymentCallbackOutbox` in payment-service writes records to `payment_svc.payment_callback_outbox` but there is no scheduled relay publisher in payment-service (unlike order-service's `OutboxPublisher`). Records accumulate; nothing publishes them to Kafka.
2. Order-service has no `@KafkaListener` for `payment.completed`. The only listeners are `OrderProjectionListener` (topics: order.*) and `OrderCreatedFinanceListener` (topics: order.created, order.paid). `Order.markPaymentCompleted()` exists but is never called in the happy path.
3. `PayPalGateway` has no `refund()` method. Payment-service has no listener for `payment.refund_requested` (published by order-service's `RefundRequestPublisherAdapter`).
4. `Payment` domain object has no `externalAmount`/`externalCurrency`/`fxRate` fields despite V9 migration adding those columns. The JPA entity presumably maps them but the domain record does not surface them.
5. No journey or workday test exercises PayPal. The buyer workday uses COD (`workday-buyer.spec.ts:322`).

---

## Architecture Decision Points

### 1. Order-status flip delivery mechanism

**Option A — payment-service outbox relay (matches order-service pattern)** Add a `@Scheduled` publisher in payment-service that polls `payment_callback_outbox` for unpublished rows and sends them to Kafka topic `payment.completed`. Order-service adds a `@KafkaListener` for that topic and calls `order.markPaymentCompleted()`. Trade-off: consistent with the existing outbox pattern; adds ~80 LOC; requires the Kafka env-override fix already applied to payment-service.

**Option B — synchronous gRPC callback from payment-service to order-service** After `promotionService.promote()` succeeds, payment-service calls a new gRPC method on order-service to flip the status inline. Trade-off: simpler for the happy path; couples the two services synchronously; a slow order-service call blocks the buyer's capture response.

**Option C — FE polls `/payment/status/{orderId}` and order-service reads from payment-service on demand** Order-service derives `paymentStatus` from a gRPC call to payment-service rather than storing it locally. Trade-off: no new event infrastructure; adds latency to every order read; the order domain model already has `paymentStatus` as a local field.

**Recommendation: Option A.** Consistent with the existing outbox pattern, decoupled, and the infrastructure already exists in order-service.

### 2. Idempotency on the capture endpoint

The current capture endpoint uses `UUID.randomUUID()` as `callbackId` and `capture.captureId()` as `eventId`. `PaymentPromotionService` handles ALREADY_COMPLETED gracefully (returns the stored payment). However, a double-tap from the FE would call PayPal's capture API twice. PayPal's capture endpoint is idempotent (second call returns the same capture record), so this is safe, but the `PaymentCallbackLogStore` dedup used by Stripe is not applied here.

**Option A — add `PaymentCallbackLogStore` dedup to the capture endpoint.** Keyed on `provider=PAYPAL` + `paypalOrderId`. Matches the Stripe pattern. Trade-off: ~20 LOC; prevents double outbox rows.

**Option B — rely on `PaymentPromotionService.ALREADY_COMPLETED` guard.** The promotion service already short-circuits on a second call. PayPal is idempotent. Accept the extra outbox row on a double-tap. Trade-off: simpler; leaves a cosmetic duplicate outbox row.

**Recommendation: Option A** for consistency with Stripe.

### 3. Refund webhook vs polling

PayPal refunds can be initiated server-side via `POST /v2/payments/captures/{captureId}/refund`. The result is synchronous (refund record returned immediately in sandbox). There is no webhook needed for the refund itself — the refund API call is the confirmation.

**Option A — synchronous refund on `payment.refund_requested`.** Payment-service listens for `payment.refund_requested`, calls `PayPalGateway.refund(captureId, amount)`, publishes `payment.refunded`. Trade-off: straightforward; requires storing `captureId` (already stored as `transactionRef`).

**Option B — admin-initiated refund (manual).** No listener; admin triggers refund via a new admin endpoint. Trade-off: no automation; acceptable for MVP.

**Recommendation: Option A** if the full refund loop is in scope; Option B if only the capture round-trip is the target for this PR.

### 4. Sandbox vs production env handling

`PayPalProperties.baseUrl()` already switches on `mode=live` vs `sandbox`. `PAYPAL_MODE` defaults to `sandbox`. No code change needed; this is a config decision.

**Decision needed:** Which sandbox app credentials to use for local dev and CI? The sandbox client-id must also be set in `VITE_PAYPAL_CLIENT_ID` for the FE Smart Buttons to load.

### 5. Currency handling

All order amounts are VND. PayPal requires USD. `PayPalGateway.createOrder()` converts via `FrankfurterFxAdapter` (ECB-sourced, 24h TTL, fallback rate 25,500 VND/USD). V9 migration stores `external_amount`, `external_currency`, `fx_rate`, `fx_rate_at` in the DB. The `Payment` domain object does not expose these fields. The `PaymentCallbackOutboxRecord` hardcodes `currency = "VND"` — this is the VND amount, which is correct for the ledger, but the outbox record does not carry the USD amount or rate.

**Decision needed:** Does the order-service `payment.completed` consumer need the USD amount, or is the VND amount sufficient?

### 6. PayPal order-id ↔ internal payment-id mapping

`PayPalGateway.createOrder()` stashes `paymentId` in both `reference_id` and `custom_id` of the PayPal order's `purchase_units`. The FE passes both `paymentId` (from the create response) and `paypalOrderId` (from PayPal's `onApprove` callback) to the capture endpoint. No reverse-lookup query is needed. This design is already correct.

---

## File-by-File Change Plan

### payment-service (BE)

**[BE] `infrastructure/paypal/PayPalGateway.java`** — Current: `createOrder()` and `capture()` implemented. No `refund()` method. Add: `refund(String captureId, BigDecimal amount)` calling `POST /v2/payments/captures/{captureId}/refund` with `{"amount":{"value":"...","currency_code":"USD"}}`. Returns a `PayPalRefund` record with `refundId` and `status`. Why: required for the refund hook path.

**[BE] `infrastructure/paypal/PayPalGatewayTest.java`** — Add: `refundCallsCorrectEndpointAndReturnsRefundId()` test. Why: maintain unit coverage parity with create/capture.

**[BE] `infrastructure/web/PaymentController.java`** — Current: capture endpoint uses `UUID.randomUUID()` for `callbackId`, no `PaymentCallbackLogStore` dedup. Change: inject `PaymentCallbackLogStore`, add dedup check keyed on `provider=PAYPAL` + `paypalOrderId` before calling `gateway.capture()`. Why: prevents double-capture outbox rows on FE retry.

**[BE] `infrastructure/paypal/PayPalOutboxRelay.java` (NEW)** — Current: `PaymentCallbackOutbox` writes to DB but nothing publishes. Add: `@Scheduled` publisher (same pattern as order-service `OutboxPublisher`) that polls `payment_callback_outbox` for rows where `published_at IS NULL`, publishes each to Kafka topic `payment.completed`, marks `published_at`. Why: this is the critical missing link. Without it, order-service never learns that payment succeeded. Note: `PaymentCallbackOutboxSpringDataRepository` needs a `findByPublishedAtIsNull(Pageable)` query method added.

**[BE] `infrastructure/paypal/PayPalRefundListener.java` (NEW, optional)** — Current: no listener for `payment.refund_requested`. Add: `@KafkaListener(topics = "payment.refund_requested")` that reads `returnId`, `orderId`, `buyerId`, `amount`; looks up the payment by `orderId`; calls `PayPalGateway.refund(captureId, usdAmount)`; publishes `payment.refunded` event. Why: closes the refund loop for the saga compensation path. Mark: defer to a follow-up PR if only capture is in scope.

**[BE] `domain/Payment.java`** — Current: no `externalAmount`, `externalCurrency`, `fxRate` fields despite V9 migration columns. Change: add optional fields (nullable) and a `withFxDetails(...)` factory method. Update `PaymentJpaEntity` mapping. Why: enables the outbox relay to carry USD amount for dispute resolution. Mark: nice-to-have; not blocking for the capture round-trip.

**[BE] `infrastructure/persistence/PaymentCallbackOutboxSpringDataRepository.java`** — Current: plain `JpaRepository<PaymentCallbackOutboxJpaEntity, Long>`. Add: `List<PaymentCallbackOutboxJpaEntity> findByPublishedAtIsNull(Pageable pageable)`. Why: required by the new `PayPalOutboxRelay`.

**[BE] `infrastructure/config/UseCaseConfig.java`** — Change: register `PayPalOutboxRelay` bean (or rely on `@Component` + `@Scheduled`). Why: ensure the relay is active when `payment.paypal.enabled=true`.

### order-service (BE)

**[BE] `infrastructure/event/PaymentCompletedListener.java` (NEW)** — Current: no listener for `payment.completed`. Add: `@KafkaListener(topics = "payment.completed", groupId = "order-service-payment")`. Reads `orderId` and `status` from the event payload. Loads the order, calls `order.markPaymentCompleted()`, saves, publishes `order.paid` outbox event. Why: this is the order-status flip. Without it, the order stays `PENDING` forever after a successful PayPal capture.

**[BE] `domain/port/out/OrderEventPublisherPort.java`** — Add: `publishOrderPaid(Order order)`. Why: the `PaymentCompletedListener` needs to emit `order.paid` so the finance listener and projection listener pick it up.

**[BE] `infrastructure/event/OrderEventPublisherAdapter.java`** — Add: `publishOrderPaid()` implementation — writes an `ORDER_PAID` outbox event. Why: implements the new port method.

### FE

**[FE] `fe/src/app/components/checkout/PayPalPaymentSection.tsx`** — Current: fully implemented. No changes needed for the happy path. Optional: add a loading spinner between `createOrder` and `onApprove` to prevent the user from seeing a blank state during the PayPal popup.

**[FE] `fe/.env.example` or `fe/.env.local.example` (NEW or update)** — Add: document `VITE_PAYPAL_ENABLED` and `VITE_PAYPAL_CLIENT_ID` with sandbox values. Why: developer onboarding.

### Config / infra

**[CONFIG] `docker-compose.yml`** — Current: `PAYPAL_ENABLED: ${PAYPAL_ENABLED:-false}`. No change needed for structure; sandbox creds must be supplied via `.env`.

**[CONFIG] `services/payment-service/src/main/resources/application.yml`** — Current: `payment.paypal.enabled: ${PAYPAL_ENABLED:false}`. No change needed.

### Tests

**[TEST] `services/payment-service/src/test/java/.../paypal/PayPalGatewayTest.java`** — Add: `refund` test (see above).

**[TEST] `services/payment-service/src/test/java/.../web/PaymentControllerPayPalTest.java` (NEW)** — Add MockMvc tests for the capture endpoint: happy path (PENDING → COMPLETED, outbox row written), double-capture (second call returns ALREADY_COMPLETED, no second PayPal call), wrong buyer (403), wrong method (403), PayPal API 4xx (500 surfaced as error). Mock `PayPalGateway` and `PaymentPromotionService`.

**[TEST] `services/order-service/src/test/java/.../PaymentCompletedListenerTest.java` (NEW)** — Add: unit test for the new listener — verifies `markPaymentCompleted()` is called and `order.paid` outbox event is written.

**[TEST] `fe/e2e/journey/02-buyer-discovers-and-orders.spec.ts`** — Current: uses COD. PayPal requires a real sandbox popup which Playwright cannot drive without the PayPal JS SDK mock. Add: a PayPal-mocked variant using `page.route()` to intercept `/payment/paypal/create` and `/payment/paypal/capture/**` and return fixture responses. This avoids the sandbox popup entirely. Why: proves the FE→BE wiring without real PayPal credentials.

---

## Sequence Diagram (happy path)

```
Buyer browser          FE (React)          payment-service         PayPal API          order-service
     |                     |                      |                     |                    |
     | click "Place Order" |                      |                     |                    |
     |-------------------->|                      |                     |                    |
     |                     | POST /orders         |                     |                    |
     |                     |--------------------->| (order-service)     |                    |
     |                     |<-- orderId           |                     |                    |
     |                     |                      |                     |                    |
     |                     | [step="success", PayPalPaymentSection rendered]                |
     |                     |                      |                     |                    |
     | [Smart Buttons load]|                      |                     |                    |
     |<--------------------|                      |                     |                    |
     |                     |                      |                     |                    |
     | click PayPal button |                      |                     |                    |
     |-------------------->|                      |                     |                    |
     |                     | POST /payment/paypal/create                                    |
     |                     | {orderId, Idempotency-Key}                                     |
     |                     |--------------------->|                     |                    |
     |                     |                      | save Payment(PENDING)                    |
     |                     |                      | gateway.createOrder(payment)             |
     |                     |                      |-------------------->|                    |
     |                     |                      |<-- {paypalOrderId}  |                    |
     |                     |<-- {payment, paypalOrderId, externalAmount, fxRate}           |
     |                     |                      |                     |                    |
     | [PayPal popup, approve]                    |                     |                    |
     |-------------------->|                      |                     |                    |
     |                     | POST /payment/paypal/capture/{paymentId}/{paypalOrderId}        |
     |                     |--------------------->|                     |                    |
     |                     |                      | gateway.capture(paypalOrderId)           |
     |                     |                      |-------------------->|                    |
     |                     |                      |<-- {COMPLETED, captureId}                |
     |                     |                      | promotionService.promote()               |
     |                     |                      | → payment.status = COMPLETED             |
     |                     |                      | → payment_callback_outbox row written    |
     |                     |<-- {payment:{COMPLETED}}                  |                    |
     |                     | onCompleted() → /orders/id                                     |
     |<--------------------|                      |                     |                    |
     |                                                                                       |
     |          [async — PayPalOutboxRelay @Scheduled, ~1s later]                           |
     |                     |                      | Kafka: payment.completed → order-service|
     |                     |                      |                     |  PaymentCompleted  |
     |                     |                      |                     |  Listener:         |
     |                     |                      |                     |  order.markPayment |
     |                     |                      |                     |  Completed()       |
     |                     |                      |                     |  publish order.paid|
```

---

## Failure Modes

1. **PayPal capture API 4xx/5xx** — today: `IllegalStateException` → 500. FE shows toast, payment stays PENDING, retry safe (PayPal capture is idempotent). Should-do: typed 502 so FE can distinguish PayPal outage.
2. **Double-capture** — today: `ALREADY_COMPLETED` short-circuit; PayPal idempotent; one extra outbox row. Should-do: add `PaymentCallbackLogStore` dedup keyed on `paypalOrderId`.
3. **Network drop after PayPal capture but before DB write** — today: `@Transactional`. Buyer retry hits PayPal again (idempotent) and promotion succeeds on retry. No code change needed.
4. **Network drop after DB write but before outbox publishes** — today: outbox row in same txn as status update; relay publishes on next poll (~1s). Order shows PENDING briefly then flips. Acceptable.
5. **Order-service listener fails** — must be idempotent. Add a guard in `Order.markPaymentCompleted()` for already-COMPLETED.
6. **Refund requested before capture completes** — listener should verify `status == COMPLETED && method == PAYPAL`; nack/retry until capture completes or timeout.
7. **FX rate unavailable at capture time** — rate locked at order-creation; capture does not re-fetch. No change needed.
8. **`PAYPAL_ENABLED=false` BE but FE enabled** — today: `IllegalStateException` → 500. Should-do: 503 with typed error code.

---

## Test Strategy

- **Unit (payment-service)**: extend `PayPalGatewayTest` with `refund()`; new `PaymentControllerPayPalTest` (happy/double/403/4xx); new `PayPalOutboxRelayTest`.
- **Unit (order-service)**: new `PaymentCompletedListenerTest` (call → markPaymentCompleted, outbox write, idempotent on second call).
- **Integration (Kafka)**: embedded Kafka, publish `payment.completed`, assert `payment_status=COMPLETED` and `order.paid` outbox row.
- **Journey (FE e2e)**: PayPal-mocked variant of chapter 2 using `page.route()` to intercept `/payment/paypal/create`, `/payment/paypal/capture/**`; block `https://www.paypal.com/**`; assert order detail shows COMPLETED.
- **Workday**: no change required — mocked journey chapter is sufficient.
- **Manual sandbox**: full popup flow + refund flow require real sandbox creds; not CI-able.

---

## Unknowns / Decisions for the User

1. **Sandbox credentials** — do we have a PayPal sandbox app at developer.paypal.com? `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET`, and `VITE_PAYPAL_CLIENT_ID` must all match.
2. **Refund scope** — refund hook in this PR, or capture-only? Refund adds ~150 LOC and a new listener.
3. **Order-status flip urgency** — is ~1s async flip acceptable, or must order page show COMPLETED immediately on navigation?
4. **`Payment` domain FX fields** — surface `externalAmount`/`externalCurrency`/`fxRate` in `Payment` domain + `PaymentResponse`?
5. **`PAYPAL_ENABLED=false` → 503 vs 500** — return 503 with typed code, or accept current 500?
6. **CI mock layer** — `page.route()` (FE-only) or WireMock stub for BE PayPal calls?

---

## Suggested Implementation Order

| Step | Description | Size | Files |
|------|-------------|------|-------|
| 1 | `PayPalOutboxRelay` + `findByPublishedAtIsNull` query | single-block | `PayPalOutboxRelay.java` (new), `PaymentCallbackOutboxSpringDataRepository.java` |
| 2 | `PaymentCompletedListener` + `publishOrderPaid` in order-service | single-block | `PaymentCompletedListener.java` (new), `OrderEventPublisherPort.java`, adapter impl |
| 3 | `PaymentCallbackLogStore` dedup on capture endpoint | single-block | `PaymentController.java` |
| 4 | Journey mock test for PayPal capture | single-block | `02-buyer-discovers-and-orders.spec.ts` |
| 5 | `PayPalGateway.refund()` + `PayPalRefundListener` | multi-block | `PayPalGateway.java`, `PayPalRefundListener.java` (new) |
| 6 | Surface FX fields in `Payment` domain + `PaymentResponse` | single-block | `Payment.java`, `PaymentJpaEntity.java`, `PaymentResponse.java` |

Steps 1–4 are the capture round-trip. Steps 5–6 are follow-on. Steps 1 and 2 are the critical path — without them the order status never flips.

---

## Summary

- **Rough total LOC estimate**: ~350 LOC new (relay ~80, order-service listener ~60, capture dedup ~30, journey mock ~80, refund path ~100 if in scope).
- **Biggest unknown**: PayPal sandbox credentials. Without them, steps 1–4 can be built and unit/journey-mock tested, but the end-to-end popup flow cannot be manually verified.
- **What's already done**: the two endpoints (`/paypal/create`, `/paypal/capture`) and the FE Smart Buttons are fully implemented and correct. Missing pieces are the outbox relay in payment-service and the `payment.completed` listener in order-service.
- **Suggested first PR**: Steps 1 + 2 + 3 + 4 together — relay + listener + dedup + mocked journey test. Self-contained "capture round-trip closes" PR with no sandbox dependency and full test coverage.
