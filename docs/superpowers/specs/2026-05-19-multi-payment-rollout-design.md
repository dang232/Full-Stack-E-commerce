# Multi-payment rollout — design

**Date:** 2026-05-19
**Owner:** dang232
**Status:** v2 — revised after review, ready to execute

## Revisions (v2)

- Added **Step 1.5: extract `PaymentPromotionService`** so VietQR/Stripe/PayPal/SePay/VNPay all promote PENDING → COMPLETED through one path instead of five.
- Clarified that the create endpoints use `ProcessPaymentUseCase` only to persist the PENDING row; the SDK call (PaymentIntent / PayPal order) is invoked separately by the controller, mirroring the VietQR pattern already in tree. `GatewayPaymentResult` stays narrow.
- Step 1 now explicitly **retrofits the in-flight VietQR work** behind `payment.vietqr.enabled` before introducing the composite gateway. Effort estimate revised from 30 min to ~½ day.
- SePay verification: **outbound polling** uses `Authorization: Apikey ${SEPAY_API_KEY}` per docs.sepay.vn. The earlier "HMAC X-SePay-Signature" note was wrong for this direction. Inbound webhook (deferred) is what carries a shared-secret header.
- Stripe webhook needs gateway permit-list entry — added to architecture + executing-plan steps.
- Migration V9 widened: also stores `fx_rate` + `fx_rate_at` for dispute reconciliation, not just `external_amount` + `external_currency`.
- `FX_FALLBACK_RATE` pinned to **25500** (USD→VND) and the adapter logs a WARN whenever the fallback fires.
- PayPal FE polling cut. Capture endpoint returns synchronously after promotion commits — no race, no poll.
- Property form (`payment.<method>.enabled`) is canonical in YAML; env-var form (`<METHOD>_ENABLED`) only appears in `.env` / `docker-compose.yml`.
- e2e-day Stripe scenario file gets a header comment pointing to the manual smoke command (`stripe trigger payment_intent.succeeded ...`).
- Order of implementation rewritten to match the above.

## Summary

Bring four payment paths to working state in parallel, each at the maturity its KYC posture allows:

| # | Method | Target maturity this rollout | Gate |
|---|---|---|---|
| 1 | VietQR | Production (real VCB account, manual confirm) | None |
| 1.5 | SePay | Production (auto-confirm of VietQR via REST polling) | SePay API key |
| 2 | Stripe | Sandbox-ready end-to-end (FE Elements drives a successful test-card charge → BE webhook → ledger) | Stripe Atlas / business reg for prod |
| 2 | PayPal | Sandbox-ready end-to-end (FE Buttons drives a successful test-account capture → BE synchronous capture → ledger) | Business registration for prod |

"Sandbox-ready end-to-end" specifically means: a developer running `docker compose --profile apps up -d` plus `stripe listen` plus the FE dev server can complete a real test-card / sandbox-account purchase and see the order flip to PAID without code changes.

VNPay stays Phase 3 deferred (sandbox unreachable without merchant onboarding). MoMo stays Phase 2 deferred for a follow-up rollout.

## Goals

- Buyers in VN can pay end-to-end via VietQR today (manual confirm).
- Auto-confirm via SePay flips on with a single env-var change once the API key is in place.
- Stripe + PayPal sandbox flows demonstrably work end-to-end against test creds, ready for cutover when business is registered.
- Manual `AdminVietQrController` fallback remains live regardless of which adapters are flagged on — no order ever stuck because an aggregator hiccupped.
- Each method's enable flag is independent. The current global `payment.mode=stub|live` is replaced by per-method `payment.<method>.enabled=true|false`.

## Non-goals

- VNPay code path, controllers, properties, signers, callback log, V8 audit — all stay untouched. Re-enabling VNPay later is a config flip.
- MoMo wiring. Code stays as-is; addressed in a separate rollout.
- FX hedging, multi-currency price display in the catalog, USD prices stored on products. Order totals remain VND; cross-currency conversion happens only when handing the amount to Stripe/PayPal.
- Production cutover for Stripe/PayPal. We document the exact steps in the roadmap, but don't execute until incorporation.

## Architecture

### Per-method enable flags

`StubPaymentGateway` (`@ConditionalOnProperty(payment.mode=stub, matchIfMissing=true)`) and `LivePaymentGateway` (`@ConditionalOnProperty(payment.mode=live)`) are replaced by a single `CompositePaymentGateway` that delegates to a `PaymentMethodHandler` per method. Each handler is `@ConditionalOnProperty(payment.<method>.enabled=true)`.

Spring's relaxed binding maps env-var names like `STRIPE_ENABLED` and `PAYMENT_STRIPE_ENABLED` to the property `payment.stripe.enabled` interchangeably. The codebase uses the property form in `@ConditionalOnProperty` and the env-var form (`STRIPE_ENABLED`) in `.env` and `docker-compose.yml`. Both are equivalent — pick whichever reads better in context.

```
PaymentGatewayPort  (existing)
  └── CompositePaymentGateway  (new — single bean, always present)
        ├── CodHandler             enabled=always
        ├── VietQrHandler          enabled=true if VIETQR_ENABLED=true
        ├── StripeHandler          enabled=true if STRIPE_ENABLED=true
        ├── PayPalHandler          enabled=true if PAYPAL_ENABLED=true
        ├── VnpayHandler           enabled=false (stays disabled — Phase 3)
        └── MomoHandler            enabled=false (stays disabled — Phase 2 follow-up)
```

If a method is selected at checkout but its handler isn't enabled, `CompositePaymentGateway` returns `PaymentStatus.FAILED` with reason `METHOD_DISABLED`. `/checkout/payment-methods` filters its response to only enabled methods so the FE never offers a disabled option.

This refactor is the foundation for every other change. Realistic effort is **~½ day**, not 30 min, because:

- The current working tree has VietQR wired but not flag-gated — `VietQrService` is `@Service` (always-on) and `PaymentController` injects it directly. Step 1 must retrofit VietQR onto `payment.vietqr.enabled` (default `true` since the bank account is already configured) before introducing the composite gateway.
- `PaymentControllerHeaderTest` and other slice tests pin `payment.mode=stub`. Each needs to switch to per-method flags (`payment.cod.enabled=true`, etc.) or they break.
- `application.yml` defaults need sensible per-method flags so dev/CI flows don't regress.

Tests update to inject the handler under test directly; `CompositePaymentGateway` gets a small unit test for the dispatch + disabled-method branch.

### PaymentPromotionService — single promotion path

The plan introduces five callers that all execute the same sequence: find PENDING payment → guard idempotency → `payment.withResult(COMPLETED, ref)` → `paymentRepository.save` → `ledgerService.recordPayment` → outbox emit. Without consolidation, each adapter rolls its own race-condition story.

```
application/PaymentPromotionService
  promote(paymentId: UUID, providerRef: String, eventDedupKey: String) → Payment
    Idempotent on eventDedupKey via PaymentCallbackLogStore.
    Looks up the PENDING payment; if already COMPLETED, returns the stored row (idempotent).
    Otherwise: paymentRepository.save(payment.withResult(COMPLETED, providerRef))
              ledgerService.recordPayment(saved)
              outbox emit (already inside the same TX via existing infra).
```

Step 1.5 extracts this service and migrates `AdminVietQrController` + `VnpayCallbackService` onto it before any Stripe/PayPal/SePay code lands. `MomoCallbackService` retrofit is optional follow-up — its existing path is already correct, just not deduplicated.

### Create-endpoint pattern (Stripe / PayPal / VietQR)

`GatewayPaymentResult` stays narrow `(status, transactionRef)`. Stripe needs `clientSecret`, PayPal needs `paypalOrderId` — but those are response shapes, not gateway-result shapes. Mirror the VietQR pattern already in the working tree:

1. Controller calls `processPaymentUseCase.process(...)` to persist a PENDING `Payment` row.
2. Controller calls the provider-specific service (`stripeGateway.createIntent(payment, fxRate)` / `paypalGateway.createOrder(payment, fxRate)`) which returns the SDK-specific extras.
3. Controller assembles the response DTO carrying both `PaymentResponse.fromDomain(payment)` and the provider extras.

This keeps `PaymentGatewayPort` from growing a `Map<String,String>` extras bag and matches the shape of `VietQrController.createVietQr` already in tree.

### FX rate provider

Stripe and PayPal need amounts in USD. Order totals are VND. New port:

```
domain/port/out/FxRatePort
  BigDecimal rate(Currency from, Currency to)

infrastructure/fx/FrankfurterFxAdapter implements FxRatePort
  GET https://api.frankfurter.app/latest?from=USD&to=VND
  Caffeine cache, 24h TTL, 100 entries
  Falls back to FX_FALLBACK_RATE on adapter failure (logged at WARN every time)
```

frankfurter.app is free, no API key, ECB-sourced. Cache is 24h because exchange rates don't move enough intraday for sandbox to care; production can shorten this.

`FX_FALLBACK_RATE` is pinned to **25500** (USD→VND, mid-2025 ballpark) in `application.yml`. The adapter logs a WARN on every fallback hit so we notice if Frankfurter is actually down rather than silently shipping a stale rate.

`StripeHandler` and `PayPalHandler` both inject `FxRatePort`, convert VND→USD before calling the SDK, and store the converted figure plus the rate that produced it on the payment record (see V9 below).

### VietQR

Already implemented. Flag flip:
- `.env` populated with `VIETQR_BANK_BIN=970436`, `VIETQR_ACCOUNT_NO=1062277438`, `VIETQR_ACCOUNT_NAME=NGO HAI DANG`.
- `VIETQR_ENABLED=true` (was implicit; now explicit).
- `/checkout/payment-methods` includes `VIETQR`.
- FE checkout page adds a VietQR option that calls `POST /payment/vietqr/create`, renders the returned QR image, shows "Sau khi chuyển xong, đơn hàng sẽ tự động cập nhật trong vòng 1 phút" hint.
- Admin order-detail page gets a "Xác nhận chuyển khoản" button that POSTs to `/admin/vietqr/confirm/{paymentId}`.

### SePay auto-confirm

New `infrastructure/sepay/` package:

```
SepayProperties (apiKey, accountId, baseUrl, pollIntervalSeconds, enabled)
SepayClient — RestClient, /transactions endpoint
  Outbound auth: Authorization: Apikey ${SEPAY_API_KEY}  (per docs.sepay.vn)
SepayPoller — @Scheduled(fixedRateString="${payment.sepay.poll-interval-seconds:30}000")
  Reads cursor (last-seen-tx-id) from sepay_cursor table.
  GET /transactions?account_id=...&since=<cursor>
  For each credit: extract memo, regex out payment ID, find PENDING VietQR payment.
    If match: PaymentPromotionService.promote(paymentId, sepayTxId, "SEPAY:" + sepayTxId)
    If no match: log + drop (the manual AdminVietQrController fallback covers this)
  Update cursor.
```

Outbound polling uses `Authorization: Apikey ${SEPAY_API_KEY}` per the SePay docs — there is no HMAC of the response body to verify (that pattern belongs to inbound webhooks, which are deferred). The earlier "X-SePay-Signature" note in v1 was wrong for this direction.

Promotion goes through `PaymentPromotionService` (Step 1.5), so the dedup key (`"SEPAY:" + sepayTxId`) lands in the same `PaymentCallbackLogStore` Stripe and VNPay use. Replays of the same SePay tx are no-ops.

`SEPAY_ENABLED=false` by default — the poller stays dormant until the user gets a key. Startup-time assertion: if enabled, `apiKey` must be non-blank.

### Stripe — Embedded Payment Intents + Elements

New `infrastructure/stripe/` package:

```
StripeProperties (secretKey, publishableKey, webhookSecret, enabled)
StripeGateway — uses Stripe Java SDK (com.stripe:stripe-java:25.x)
  createPaymentIntent(payment, fxRate) → PaymentIntent.create({
      amount: usdAmountInCents,
      currency: "usd",
      metadata: { paymentId, orderId, vndAmount }
  })
  Returns clientSecret to FE.

StripeWebhookController @ POST /payment/stripe/webhook
  Verifies Stripe-Signature header via Webhook.constructEvent(payload, sigHeader, webhookSecret)
  Handles event types: payment_intent.succeeded, payment_intent.payment_failed
  Idempotent on event.id via PaymentPromotionService dedup (eventDedupKey = "STRIPE:" + event.id).
  On succeeded: PaymentPromotionService.promote(paymentId, intent.charges[0].id, "STRIPE:" + event.id)
```

**Gateway permit-list:** `/payment/stripe/webhook` must be added to the API gateway's permit-list. Stripe's CLI POSTs without a buyer JWT — the webhook signature is the auth, not OAuth. Update `services/api-gateway/src/main/java/com/vnshop/apigateway/infrastructure/route/RouteConfig.java` to permit this single path. Same applies to a future PayPal webhook.

Local dev:
- User runs `stripe listen --forward-to http://localhost:8092/payment/stripe/webhook`. Stripe CLI prints the webhook signing secret. User pastes into `STRIPE_WEBHOOK_SECRET` in `.env`. Documented in `docs/PAYMENT-ROADMAP.md`.
- No public URL needed.

FE (fe/src/components/checkout/StripeElement.tsx):
- Calls `POST /payment/stripe/create` to get `{clientSecret}`.
- Wraps `<Elements>` provider with the secret.
- Renders `<PaymentElement>` (Stripe's auto-card-form).
- On submit calls `stripe.confirmPayment({elements, confirmParams: {return_url}})`.
- Webhook handles the actual completion server-side; FE shows "Đang xác nhận thanh toán…" then polls `/payment/status/{orderId}` until COMPLETED or 5min timeout.

Adds `@stripe/stripe-js` + `@stripe/react-stripe-js` to fe/package.json. `VITE_STRIPE_PUBLISHABLE_KEY` baked into the bundle at build time.

### PayPal — Embedded Smart Payment Buttons

New `infrastructure/paypal/` package:

```
PayPalProperties (clientId, clientSecret, mode=sandbox|live, enabled)
PayPalGateway — uses PayPal Server SDK (com.paypal.sdk:checkout-sdk:2.x or REST + RestClient)
  createOrder(payment, fxRate) → POST /v2/checkout/orders {
    intent: "CAPTURE",
    purchase_units: [{ amount: { currency_code: "USD", value: usdAmount } }],
    application_context: { return_url, cancel_url }
  }
  capture(orderId) → POST /v2/checkout/orders/{id}/capture
  Returns paypalOrderId to FE.

PayPalCaptureController @ POST /payment/paypal/capture/{paypalOrderId}
  Calls PayPalGateway.capture(paypalOrderId).
  PaymentPromotionService.promote(paymentId, captureId, "PAYPAL:" + captureId)
  Returns the promoted Payment synchronously — no FE polling needed.
```

No webhook for sandbox. FE Smart Buttons drive the flow:
- FE `<PayPalScriptProvider options={{clientId, currency: "USD"}}>` wraps the checkout page.
- `<PayPalButtons createOrder={() => fetch('/payment/paypal/create')} onApprove={(data) => fetch(`/payment/paypal/capture/${data.orderID}`)}>`.
- `onApprove` resolves with the captured `Payment` directly. No polling — capture commits the promotion before the response returns.

Adds `@paypal/react-paypal-js` to fe/package.json. `VITE_PAYPAL_CLIENT_ID` baked into the bundle.

For production redundancy a PayPal webhook (`PAYMENT.CAPTURE.COMPLETED`) is documented as deferred — it needs the same gateway permit-list entry as Stripe and a public URL, neither of which is available in sandbox.

## Data flow examples

### Stripe happy path

```
FE: POST /payment/stripe/create { orderId, amount, buyerId }
BE: ProcessPaymentUseCase → StripeHandler.processPayment
    → FxRatePort.rate(VND, USD) = 0.000040
    → Stripe.PaymentIntent.create(amount=usdCents, currency=usd, metadata=...)
    → Save Payment(status=PENDING, externalRef=intent.id, externalAmount=USD)
    → Return { clientSecret, paymentId }
FE: stripe.confirmPayment({elements, confirmParams: {return_url: /order/.../complete}})
    → buyer enters card, Stripe charges
Stripe: POST /payment/stripe/webhook (event=payment_intent.succeeded)
BE: StripeWebhookController.handle
    → Webhook.constructEvent verifies signature
    → PaymentCallbackLogStore checks for duplicate
    → Find Payment by metadata.paymentId
    → payment.withResult(COMPLETED, intent.charges[0].id)
    → ledgerService.recordPayment
    → outbox publishes payment.completed
FE: poll /payment/status/{orderId} → 200 { status: COMPLETED }
    → show success page
```

### PayPal happy path

Same shape as Stripe up to the create call. Capture is initiated from the FE in `onApprove` and hits `/payment/paypal/capture/{paypalOrderId}`. That endpoint:

1. Calls PayPal `/v2/checkout/orders/{id}/capture`.
2. On success, calls `PaymentPromotionService.promote(...)` synchronously.
3. Returns the promoted `Payment` directly.

No FE polling — the response *is* the confirmation. (Plan v1 had a poll "to handle the race"; there is no race when the capture endpoint returns synchronously.)

### VietQR + SePay auto-confirm

```
FE: POST /payment/vietqr/create
BE: VietQrHandler → Save Payment(PENDING) + VietQrService.generate()
    → Return { qrImageUrl, accountNo, accountName, reference }
FE: render QR, show "đang chờ chuyển khoản"

Buyer: opens VCB app, scans QR, transfers, includes paymentId in memo

[30s elapses]

SepayPoller: GET /transactions?since=<cursor>
    → Sees credit with memo containing paymentId
    → Find PENDING VietQR payment
    → payment.withResult(COMPLETED, sepayTxId)
    → ledgerService.recordPayment
    → Update cursor
FE: was polling /payment/status/{orderId} → flips to COMPLETED
```

If SePay misses the transaction (memo malformed, SePay outage, key wrong), the buyer's transfer still landed in the account. Owner sees the bank app notification, hits the admin "Xác nhận chuyển khoản" button — same end state.

## Database

Two migrations. V8 (payment audit columns) is already in tree, uncommitted — these stack on top.

```sql
-- V9__payment_external_amount.sql
ALTER TABLE payment_svc.payments
  ADD COLUMN external_amount NUMERIC(19,2),
  ADD COLUMN external_currency VARCHAR(3),
  ADD COLUMN fx_rate NUMERIC(19,8),
  ADD COLUMN fx_rate_at TIMESTAMP WITH TIME ZONE;

-- V10__sepay_cursor.sql
CREATE TABLE payment_svc.sepay_cursor (
  id INT PRIMARY KEY DEFAULT 1,
  last_tx_id VARCHAR(64),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  CONSTRAINT sepay_cursor_singleton CHECK (id = 1)
);
INSERT INTO payment_svc.sepay_cursor (id) VALUES (1) ON CONFLICT DO NOTHING;
```

`fx_rate` + `fx_rate_at` are added now (not later) because backfilling them from a charge dispute three months from now is a research project — recording at write time is one ALTER and one column assignment.

## Testing strategy

TDD per gateway, in this order (matches `Skill superpowers:test-driven-development`):

1. **Per-method flag refactor + VietQR retrofit.** Existing payment unit tests update to set per-method flags. Add one test per handler-disabled-but-method-selected path returning `METHOD_DISABLED`. VietQR uncommitted tests update to assert `payment.vietqr.enabled` gating.
2. **PaymentPromotionService.** Mock repo + ledger. Tests: PENDING → COMPLETED commits ledger; already-COMPLETED returns stored row (idempotent); duplicate eventDedupKey is a no-op; AdminVietQrController + VnpayCallbackService tests now go through the service.
3. **FX provider.** Mock RestClient. Tests: happy path returns rate; cache hits second call; adapter failure returns fallback rate **and** logs a WARN.
4. **VietQR live.** Existing tests cover the create flow; add e2e-day scenario `payment_vietqr` with create → admin-confirm round-trip.
5. **Stripe gateway unit.** Mock Stripe SDK. Tests: PaymentIntent created with correct amount/currency/metadata; FX rate persisted; webhook signature verified; duplicate event ignored via promotion-service dedup; signature mismatch returns 400.
6. **Stripe e2e-day.** Hits create, asserts `clientSecret` shape. Header comment in the scenario file points to the manual smoke (`stripe trigger payment_intent.succeeded --override payment_intent:metadata.paymentId=...`).
7. **PayPal gateway unit.** Mock RestClient. Tests: order created with correct amount; capture flow promotes synchronously; error responses surface as `PaymentStatus.FAILED`.
8. **PayPal e2e-day.** Hits create, asserts `paypalOrderId` shape.
9. **SePay poller unit.** Mock SepayClient. Tests: matched-memo promotes payment via `PaymentPromotionService`; unmatched memo logs + skips; cursor advances; outbound `Authorization: Apikey` header set correctly; flag-off → bean absent.

## Verification before claiming done

Per `superpowers:verification-before-completion`:

- `mvn test` in payment-service: 100% green
- `node infra/scripts/e2e-day.mjs` from a fresh stack-up: passes including new `payment_vietqr`, `payment_stripe_create`, `payment_paypal_create` scenarios
- `cd fe && npx playwright test`: existing 19/19 + a new spec covering the embedded Stripe element rendering and PayPal button mounting
- Manually flip `payment.stripe.enabled=false` and confirm Stripe disappears from `/checkout/payment-methods`
- Manually run `stripe trigger payment_intent.succeeded` and confirm the webhook updates a payment via `PaymentPromotionService`

## Risks + mitigations

| Risk | Mitigation |
|---|---|
| Frankfurter.app outage breaks Stripe + PayPal payment creation | `FX_FALLBACK_RATE=25500` provides a static rate; fallback is unit-tested and logs WARN on every hit so we notice |
| SePay sees a credit before the FE finishes posting the create call (race) | Cursor advances regardless; if matched payment not found yet, log + retry next poll |
| Multiple promotion sites drift in subtle ways (e.g. one forgets ledger emit) | Single `PaymentPromotionService` (Step 1.5) — only one place commits the COMPLETED transition |
| Stripe webhook idempotency conflicts across providers | Dedup key namespaces by provider: `"STRIPE:" + event.id`, `"SEPAY:" + tx.id`, `"PAYPAL:" + capture.id` |
| Stripe webhook hits gateway with no JWT and gets 401 | Add `/payment/stripe/webhook` to gateway permit-list; webhook signature is the auth |
| Embedded Elements + PayPal Buttons make the FE bundle bigger | Both are tree-shakeable; fe/build measures < 50KB delta. Acceptable. |
| `STRIPE_PUBLISHABLE_KEY` and `PAYPAL_CLIENT_ID` baked into the FE bundle expose them publicly | Both are PUBLIC by design — that's why they're called "publishable" / "client". Secret keys never reach the FE. |
| SePay flag enabled but key blank → poller thrashes | `@ConditionalOnProperty(payment.sepay.enabled=true)` PLUS startup assertion `apiKey != blank` |
| Stripe sandbox tests run cards through real Stripe servers — could hit rate limits in CI | e2e-day only asserts response shape on create, doesn't drive a real charge. Webhook test is local-manual. |
| FX rate at charge time differs from rate stored on the payment record | `fx_rate` + `fx_rate_at` columns capture exactly the rate used; disputes resolve in seconds, not days |

## Operational doc deltas

- `docs/PAYMENT-ROADMAP.md` — Stripe + PayPal move from Phase 3 to Phase 2, SePay added as Phase 1.5, env-var matrix updated.
- `docs/FE-PLAN.md` — payment-service endpoints table updated, checkout journey gets the four-options flow.
- `README.md` — service tagline updated.

## Order of implementation

Per `superpowers:executing-plans` — small, verifiable steps. Each step is its own commit and gates on its tests passing before the next starts.

1. **Per-method flag refactor + VietQR retrofit.** `CompositePaymentGateway` replaces `LivePaymentGateway`/`StubPaymentGateway`. VietQR moves behind `payment.vietqr.enabled=true` (default on). Slice tests + `application.yml` updated. ~½ day.
2. **`PaymentPromotionService` extraction.** Migrate `AdminVietQrController` + `VnpayCallbackService` onto it. Dedup by `provider:eventId` namespace. `MomoCallbackService` left untouched (works correctly; deduplication is follow-up).
3. **FX provider.** `FxRatePort` + `FrankfurterFxAdapter`. Caffeine cache, 24h TTL, fallback `25500`, WARN log on fallback hit.
4. **V9 + V10 migrations.** External-amount/currency + fx-rate/fx-rate-at + sepay-cursor.
5. **Stripe gateway BE.** `StripeProperties`, `StripeGateway.createPaymentIntent`, `POST /payment/stripe/create` controller endpoint following the create-endpoint pattern. Persists `external_amount`, `external_currency`, `fx_rate`, `fx_rate_at`.
6. **Stripe webhook + gateway permit-list.** `StripeWebhookController`, `RouteConfig.java` permits `/payment/stripe/webhook`, idempotency via `PaymentPromotionService` dedup key.
7. **Stripe FE Embedded Elements.** `@stripe/stripe-js` + `@stripe/react-stripe-js`, `VITE_STRIPE_PUBLISHABLE_KEY`, `<PaymentElement>` integration in checkout.
8. **Stripe e2e-day scenario.** Shape-only assertion. Header comment in scenario file points to `stripe trigger` smoke command.
9. **PayPal gateway BE.** `PayPalProperties`, `PayPalGateway.createOrder` + `capture`, `POST /payment/paypal/create` and `POST /payment/paypal/capture/{paypalOrderId}` (synchronous promotion).
10. **PayPal FE Smart Buttons.** `@paypal/react-paypal-js`, `VITE_PAYPAL_CLIENT_ID`, `<PayPalButtons>` integration. No FE polling.
11. **PayPal e2e-day scenario.** Shape-only assertion.
12. **SePay poller.** **Verify SePay docs first** (auth header, transaction shape, cursor semantics) before writing code. `SepayProperties`, `SepayClient`, `SepayPoller`, gated on `payment.sepay.enabled`. Uses `PaymentPromotionService` for promotion.
13. **Doc updates.** `docs/PAYMENT-ROADMAP.md`, `docs/FE-PLAN.md`, `README.md` reflect Stripe/PayPal sandbox-ready and SePay phase 1.5.
14. **Final verification gates.** `mvn test`, `e2e-day.mjs`, `playwright test`, manual flag-flip + Stripe trigger smokes.

## Out of scope (defer to follow-ups)

- Live FX provider rotation (multi-source for prod)
- Stripe Connect for marketplace splits to sellers
- PayPal payouts to sellers
- PayPal production webhook (`PAYMENT.CAPTURE.COMPLETED`) — needs public URL + gateway permit-list mirror of Stripe
- VNPay re-enable
- MoMo polling integration + `MomoCallbackService` migration onto `PaymentPromotionService`
- Saved payment methods (token vault)
- Subscription / installment products
