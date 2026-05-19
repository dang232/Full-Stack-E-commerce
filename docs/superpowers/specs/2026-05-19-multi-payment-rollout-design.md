# Multi-payment rollout — design

**Date:** 2026-05-19
**Owner:** dang232
**Status:** draft, awaiting user review

## Summary

Bring four payment paths to working state in parallel, each at the maturity its KYC posture allows:

| # | Method | Target maturity this rollout | Gate |
|---|---|---|---|
| 1 | VietQR | Production (real VCB account, manual confirm) | None |
| 1.5 | SePay | Production (auto-confirm of VietQR via REST polling) | SePay API key |
| 2 | Stripe | Sandbox-ready end-to-end (FE Elements drives a successful test-card charge → BE webhook → ledger) | Stripe Atlas / business reg for prod |
| 2 | PayPal | Sandbox-ready end-to-end (FE Buttons drives a successful test-account capture → BE polling → ledger) | Business registration for prod |

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

This refactor is the foundation for every other change. It's a 30-min mechanical move (existing logic reorganised into handlers behind flags). Tests update to inject the handler under test directly; `CompositePaymentGateway` gets a small unit test for the dispatch + disabled-method branch.

### FX rate provider

Stripe and PayPal need amounts in USD. Order totals are VND. New port:

```
domain/port/out/FxRatePort
  BigDecimal rate(Currency from, Currency to)

infrastructure/fx/FrankfurterFxAdapter implements FxRatePort
  GET https://api.frankfurter.app/latest?from=USD&to=VND
  Caffeine cache, 24h TTL, 100 entries
  Falls back to FX_FALLBACK_RATE on adapter failure
```

frankfurter.app is free, no API key, ECB-sourced. Cache is 24h because exchange rates don't move enough intraday for sandbox to care; production can shorten this. Fallback covers network outage so a Stripe order doesn't 500 because frankfurter.app blipped.

`StripeHandler` and `PayPalHandler` both inject `FxRatePort`, convert VND→USD before calling the SDK, store both VND and USD on the payment record (`amount` stays VND for ledger purposes; new column `external_amount` + `external_currency` for the converted figure).

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
SepayPoller — @Scheduled(fixedRateString="${payment.sepay.poll-interval-seconds:30}000")
  Reads cursor (last-seen-tx-id) from sepay_cursor table.
  GET /transactions?account_id=...&since=<cursor>
  For each credit: extract memo, regex out payment ID, find PENDING VietQR payment.
    If match: promote via the same path AdminVietQrController uses (paymentRepository.save + ledgerService.recordPayment).
    If no match: log + queue for manual review (drops into the existing manual-confirm fallback).
  Update cursor.
```

HMAC-verify the SePay response signature (X-SePay-Signature header) using `SEPAY_API_KEY` as the shared secret. Drop on mismatch.

`SEPAY_ENABLED=false` by default — this entire poller stays dormant until the user gets a key.

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
  Idempotent on event.id via PaymentCallbackLogStore (existing infra reused).
  On succeeded: same promotion path as VnpayCallbackService (save + ledger + outbox).
```

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
```

No webhook. FE Smart Buttons drive the flow:
- FE `<PayPalScriptProvider options={{clientId, currency: "USD"}}>` wraps the checkout page.
- `<PayPalButtons createOrder={() => fetch('/payment/paypal/create')} onApprove={(data) => fetch(`/payment/paypal/capture/${data.orderID}`)}>`.
- After capture returns success, FE polls `/payment/status/{orderId}` until COMPLETED.

Adds `@paypal/react-paypal-js` to fe/package.json. `VITE_PAYPAL_CLIENT_ID` baked into the bundle.

For redundancy: also implement a webhook for production (PayPal's "Payment Capture Completed" event) but document it as deferred — sandbox doesn't deliver webhooks reliably to localhost without ngrok.

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

Same shape, but capture is initiated from the FE in `onApprove` instead of via webhook. The `/payment/paypal/capture/{orderId}` endpoint does the PayPal API call + the same internal promotion path. Polling on the FE side is just to confirm the BE recorded the capture (handles the race where FE thinks it completed but BE hasn't persisted yet).

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

One migration:

```sql
-- V9__payment_external_amount.sql
ALTER TABLE payment_svc.payments
  ADD COLUMN external_amount NUMERIC(19,2),
  ADD COLUMN external_currency VARCHAR(3);

-- V10__sepay_cursor.sql
CREATE TABLE payment_svc.sepay_cursor (
  id INT PRIMARY KEY DEFAULT 1,
  last_tx_id VARCHAR(64),
  updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
  CONSTRAINT sepay_cursor_singleton CHECK (id = 1)
);
INSERT INTO payment_svc.sepay_cursor (id) VALUES (1) ON CONFLICT DO NOTHING;
```

## Testing strategy

TDD per gateway, in this order (matches `Skill superpowers:test-driven-development`):

1. **Per-method flag refactor.** Existing payment unit tests pass unchanged. Add one test per handler-disabled-but-method-selected path returning `METHOD_DISABLED`.
2. **FX provider.** Mock RestClient. Tests: happy path returns rate; cache hits second call; adapter failure returns fallback.
3. **VietQR live.** Existing tests cover the create flow; add e2e-day scenario `payment_vietqr` with create → admin-confirm round-trip.
4. **Stripe gateway unit.** Mock Stripe SDK. Tests: PaymentIntent created with correct amount/currency/metadata; webhook signature verified; duplicate event ignored; signature mismatch returns 400.
5. **Stripe e2e-day.** Hits create, asserts `clientSecret` shape. Manual webhook trigger via `stripe trigger payment_intent.succeeded` documented as a manual step (not automated in e2e-day; this is sandbox UX, not CI).
6. **PayPal gateway unit.** Mock RestClient. Tests: order created with correct amount; capture flow; error responses surface as PaymentStatus.FAILED.
7. **PayPal e2e-day.** Hits create, asserts `paypalOrderId` shape.
8. **SePay poller unit.** Mock SepayClient. Tests: matched-memo promotes payment; unmatched memo logs + skips; cursor advances; HMAC mismatch drops payload.

## Verification before claiming done

Per `superpowers:verification-before-completion`:

- `mvn test` in payment-service: 100% green
- `node infra/scripts/e2e-day.mjs` from a fresh stack-up: passes including new `payment_vietqr`, `payment_stripe_create`, `payment_paypal_create` scenarios
- `cd fe && npx playwright test`: existing 19/19 + a new spec covering the embedded Stripe element rendering and PayPal button mounting
- Manually flip `STRIPE_ENABLED=false` and confirm Stripe disappears from `/checkout/payment-methods`
- Manually run `stripe trigger payment_intent.succeeded` and confirm the webhook updates a payment

## Risks + mitigations

| Risk | Mitigation |
|---|---|
| Frankfurter.app outage breaks Stripe + PayPal payment creation | FX_FALLBACK_RATE provides a static rate; fallback path is unit-tested |
| SePay sees a credit before the FE finishes posting the create call (race) | Cursor advances regardless; if matched payment not found yet, log + retry next poll |
| Stripe webhook idempotency key conflicts with existing `PaymentCallbackLogStore` schema | Re-use the existing schema; key off `provider="STRIPE"` + `event.id` |
| Embedded Elements + PayPal Buttons make the FE bundle bigger | Both are tree-shakeable; fe/build measures < 50KB delta. Acceptable. |
| `STRIPE_PUBLISHABLE_KEY` and `PAYPAL_CLIENT_ID` baked into the FE bundle expose them publicly | Both are PUBLIC by design — that's why they're called "publishable" / "client". Secret keys never reach the FE. |
| SePay flag enabled but key blank → poller thrashes | `@ConditionalOnProperty(payment.sepay.enabled=true)` PLUS startup assertion `apiKey != blank` |
| Stripe sandbox tests run cards through real Stripe servers — could hit rate limits in CI | e2e-day only asserts response shape on create, doesn't drive a real charge. Webhook test is local-manual. |

## Operational doc deltas

- `docs/PAYMENT-ROADMAP.md` — Stripe + PayPal move from Phase 3 to Phase 2, SePay added as Phase 1.5, env-var matrix updated.
- `docs/FE-PLAN.md` — payment-service endpoints table updated, checkout journey gets the four-options flow.
- `README.md` — service tagline updated.

## Order of implementation

Per `superpowers:executing-plans` — small, verifiable steps:

1. Per-method flag refactor + unit tests
2. FX provider + unit tests
3. V9 + V10 migrations
4. VietQR e2e-day + admin confirm button (FE)
5. Stripe gateway BE + unit tests
6. Stripe webhook + idempotency tests
7. Stripe FE Embedded Elements
8. Stripe e2e-day scenario
9. PayPal gateway BE + unit tests
10. PayPal capture endpoint
11. PayPal FE Smart Buttons
12. PayPal e2e-day scenario
13. SePay poller BE + unit tests (gated, doesn't run unless flag on + key set)
14. Roadmap doc + README updates
15. Final verification gates

Each step is its own commit. Each commit gates on its tests passing before moving to the next.

## Out of scope (defer to follow-ups)

- Live FX provider rotation (multi-source for prod)
- Stripe Connect for marketplace splits to sellers
- PayPal payouts to sellers
- VNPay re-enable
- MoMo polling integration
- Saved payment methods (token vault)
- Subscription / installment products
