# Payment roadmap — phased plan for a sole-proprietor VN shop

**Last updated:** 2026-05-19
**Owner:** dang232

This document is the canonical truth for *which payment methods we ship, in what order, and why*. The code in `services/payment-service` already implements all six (`COD`, `VIETQR`, `MOMO`, `VNPAY`, `STRIPE`, `PAYPAL`) — what gates each one is **business / regulatory**, not engineering.

## Why this exists

A previous session asked to wire VNPay sandbox. Attempting to register surfaced that VNPay's `sandbox.vnpayment.vn` is locked behind merchant onboarding (MST + GPKD — Vietnamese business registration). The shop is currently a sole proprietorship without those documents, so VNPay is **structurally unavailable**, not "we haven't gotten to it yet."

Rather than block on that, this roadmap separates payment methods by what they *actually* require to go live:

| Phase | Method | Requires | Status |
|---|---|---|---|
| 1 | COD | nothing | ✅ live |
| 1 | VietQR | personal VN bank account | ✅ live (manual confirm) |
| 1.5 | SePay | SePay API key | ⏳ auto-confirm of VietQR |
| 2 | Stripe | registered business + USD merchant account | ✅ sandbox-ready end-to-end |
| 2 | PayPal | registered business | ✅ sandbox-ready end-to-end |
| 3 | VNPay | registered VN business (MST + GPKD) | ⛔ deferred |
| 3 | MoMo | dev-portal email + phone | ⛔ deferred (Phase 2 follow-up) |

## Phase 1 — production today (no infrastructure, no business registration)

### COD
Already live. `POST /payment/cod/confirm` auto-completes. No work needed.

### VietQR (manual confirmation flow)

Already coded — `VietQrService.java`, `AdminVietQrController.java`. The flow:

1. Buyer picks VietQR at checkout.
2. FE calls `POST /payment/vietqr/create` → BE returns `{payment, qrImageUrl, bankBin, accountNo, accountName, reference}`.
3. FE renders the QR. Buyer scans with their banking app (any VN bank's app reads VietQR), confirms transfer, includes the order ID in the memo.
4. Owner sees the transfer notification on their phone.
5. Owner hits `POST /admin/vietqr/confirm/{paymentId}` (or clicks a button in admin UI). Order moves to `PAID`. Saga proceeds normally.

**To enable:**
- `VIETQR_ENABLED=true` (default on)
- `VIETQR_BANK_BIN` (970436=VCB, 970407=TCB, 970422=MB, 970416=ACB, 970403=Sacombank, 970423=TPBank, full list at https://vietqr.io/danh-sach-api)
- `VIETQR_ACCOUNT_NO` — account number
- `VIETQR_ACCOUNT_NAME` — name on the account, no diacritics

These go in `.env` (gitignored). The `.env.example` keeps them blank.

**Why this works:**
- No webhook → no public domain or tunnel needed.
- No PSP fees on transfers (banks settle for free between Vietnamese accounts).
- Works on every VN banking app — same UX buyers already use on Shopee live-streams and Facebook shops.
- Reconciliation is manual but bounded: the shop owner is already monitoring their bank app for orders, so the friction is essentially zero.

**Limitations:**
- Owner must be online to confirm. Acceptable at sole-proprietor scale; doesn't scale past a few orders/day.
- No automatic timeout if buyer abandons after seeing QR. Currently `payment.vnpay.expire-minutes=15` exists but VietQR doesn't expire on the bank side; we'd add a sweep job in Phase 1.5 if it becomes a problem.

## Phase 1.5 — auto-confirm of VietQR via SePay polling

New `infrastructure/sepay/` package. When enabled, a scheduled poller queries SePay's transaction API every 30 seconds, matches incoming credits to pending VietQR payments by memo, and auto-confirms them.

**To enable:**
- `SEPAY_ENABLED=true`
- `SEPAY_API_KEY` from https://dashboard.sepay.vn (free account, no business docs needed)
- `SEPAY_ACCOUNT_ID` — your SePay account ID

The poller is dormant when `SEPAY_ENABLED=false` (default). Startup asserts that if enabled, `SEPAY_API_KEY` must be non-blank.

**How it works:**
- Poller reads cursor (last-seen-tx-id) from `sepay_cursor` table.
- `GET /transactions?account_id=...&since=<cursor>` with `Authorization: Apikey ${SEPAY_API_KEY}`.
- For each credit: extract memo, regex out payment ID, find PENDING VietQR payment.
- If match: promote payment to COMPLETED via `PaymentPromotionService` (idempotent on tx ID).
- If no match: log + skip (the manual `AdminVietQrController` fallback covers this).
- Update cursor.

**Why this is optional:**
- VietQR works fine with manual confirm. SePay auto-confirm is a convenience layer.
- If SePay outage or memo malformed, the transfer still landed in the account. Owner sees the bank app notification and can confirm manually.
- No webhook needed — outbound polling only.

## Phase 2 — Stripe + PayPal sandbox-ready end-to-end

Both methods are production-gated on business registration, but the sandbox flows work end-to-end today. A developer running `docker compose --profile apps up -d` plus the FE dev server can complete a real test-card / sandbox-account purchase and see the order flip to PAID without code changes.

### Stripe — Embedded Payment Intents + Elements

**To enable:**
- `STRIPE_ENABLED=true`
- `STRIPE_SECRET_KEY` from https://dashboard.stripe.com/apikeys
- `STRIPE_PUBLISHABLE_KEY` (public, baked into FE bundle)
- `STRIPE_WEBHOOK_SECRET` from `stripe listen` (see local dev below)

**Local dev setup:**
```bash
stripe listen --forward-to http://localhost:8092/payment/stripe/webhook
# Stripe CLI prints: whsec_...
# Paste into .env: STRIPE_WEBHOOK_SECRET=whsec_...
```

**How it works:**
1. FE calls `POST /payment/stripe/create` → BE returns `{payment, clientSecret, intentId, publishableKey, externalAmount, externalCurrency, fxRate}`.
2. FE wraps `<Elements>` provider with the secret, renders `<PaymentElement>` (Stripe's auto-card-form).
3. On submit, `stripe.confirmPayment({elements, confirmParams: {return_url}})` → buyer enters card.
4. Stripe charges and POSTs `payment_intent.succeeded` to `/payment/stripe/webhook`.
5. BE webhook verifies signature, promotes payment to COMPLETED via `PaymentPromotionService` (idempotent on event ID).
6. FE polls `/payment/status/{orderId}` until COMPLETED or 5min timeout.

**Production cutover:**
- Register business with Stripe Atlas (https://stripe.com/atlas).
- Switch `STRIPE_PUBLISHABLE_KEY` and `STRIPE_SECRET_KEY` to live keys.
- Update webhook URL in Stripe dashboard to your production domain.
- No code changes needed.

### PayPal — Embedded Smart Payment Buttons

**To enable:**
- `PAYPAL_ENABLED=true`
- `PAYPAL_CLIENT_ID` from https://developer.paypal.com (sandbox credentials)
- `PAYPAL_CLIENT_SECRET` (not sent to FE)
- `PAYPAL_MODE=sandbox` (or `live` for production)

**How it works:**
1. FE calls `POST /payment/paypal/create` → BE returns `{payment, clientId, paypalOrderId, status, externalAmount, externalCurrency, fxRate}`.
2. FE wraps `<PayPalScriptProvider>`, renders `<PayPalButtons>`.
3. On approve, FE calls `POST /payment/paypal/capture/{paypalOrderId}`.
4. BE captures synchronously, promotes payment to COMPLETED via `PaymentPromotionService`.
5. Response returns the promoted `Payment` directly — no FE polling needed.

**Production cutover:**
- Register business with PayPal (https://www.paypal.com/business).
- Switch `PAYPAL_MODE=live` and update credentials.
- No code changes needed.

**Why no webhook for sandbox:**
- PayPal sandbox doesn't require webhook setup. Capture is synchronous.
- Production webhook (`PAYMENT.CAPTURE.COMPLETED`) is deferred — it needs a public URL and gateway permit-list entry, neither available in sandbox.

## Phase 3 — registered business required (deferred indefinitely)

### VNPay

**Status: deferred.** `sandbox.vnpayment.vn` returns 404 to anonymous browsers and the merchant onboarding flow asks for:
- MST (mã số thuế — tax code)
- GPKD (giấy phép kinh doanh — business license)
- Bank account in the registered business name (not personal)
- Signed integration contract

None of these exist for the current shop. Re-evaluate when the business is incorporated.

The code stays — `VnpayGateway`, `VnpayCallbackService`, `VnpaySigner`, `VnpayProperties`, the `vnpay/create`+`vnpay/return`+`vnpay/ipn` endpoints, and the V8 callback-audit migration are all in place behind per-method flags. Flipping VNPay on later is a config change, not a re-implementation.

**Do not delete** the VNPay code paths even though they're not exercised — re-introducing them later is more risk than the carrying cost of unreferenced classes. The unit tests still cover the signer logic, which is the only thing that's hard to get right.

### Stripe production

Stripe requires a registered business and a USD-receivable merchant account. Useful only if the shop sells to international buyers — at which point the business question is bigger than payments. Sandbox flow works today; production cutover is a config change (see Phase 2 above).

### PayPal production

Same gating as Stripe. Sandbox flow works today; production cutover is a config change (see Phase 2 above).

### MoMo

Deferred to a follow-up rollout. Code stays as-is. When ready, the flow will use status-polling instead of IPN (same pattern as Phase 1.5 SePay).

## Configuration matrix

| Variable | Phase 1 | Phase 1.5 | Phase 2 | Phase 3 |
|---|---|---|---|---|
| `COD_ENABLED` | `true` | `true` | `true` | `true` |
| `VIETQR_ENABLED` | `true` | `true` | `true` | `true` |
| `VIETQR_BANK_BIN` | required | required | required | required |
| `VIETQR_ACCOUNT_NO` | required | required | required | required |
| `VIETQR_ACCOUNT_NAME` | required | required | required | required |
| `SEPAY_ENABLED` | `false` | `true` | `true` | `true` |
| `SEPAY_API_KEY` | (blank) | required | required | required |
| `SEPAY_ACCOUNT_ID` | (blank) | required | required | required |
| `STRIPE_ENABLED` | `false` | `false` | `true` | `true` |
| `STRIPE_SECRET_KEY` | (blank) | (blank) | required (test) | required (live) |
| `STRIPE_PUBLISHABLE_KEY` | (blank) | (blank) | required (test) | required (live) |
| `STRIPE_WEBHOOK_SECRET` | (blank) | (blank) | required (from `stripe listen`) | required (from dashboard) |
| `PAYPAL_ENABLED` | `false` | `false` | `true` | `true` |
| `PAYPAL_CLIENT_ID` | (blank) | (blank) | required (sandbox) | required (live) |
| `PAYPAL_CLIENT_SECRET` | (blank) | (blank) | required (sandbox) | required (live) |
| `PAYPAL_MODE` | (blank) | (blank) | `sandbox` | `live` |
| `VNPAY_ENABLED` | `false` | `false` | `false` | `false` |
| `VNPAY_TMN_CODE` | (blank) | (blank) | (blank) | required |
| `VNPAY_HASH_SECRET` | (blank) | (blank) | (blank) | required |
| `MOMO_ENABLED` | `false` | `false` | `false` | `false` |
| `MOMO_PARTNER_CODE` | (blank) | (blank) | (blank) | required |
| `MOMO_ACCESS_KEY` | (blank) | (blank) | (blank) | required |
| `MOMO_SECRET_KEY` | (blank) | (blank) | (blank) | required |
| `FX_FALLBACK_RATE` | (not used) | (not used) | `25500` | `25500` |

**Notes:**
- Sandbox defaults in `application.yml` point at test endpoints; for production swap to live endpoints.
- `STRIPE_WEBHOOK_SECRET` comes from `stripe listen --forward-to http://localhost:8092/payment/stripe/webhook` during local dev.
- `FX_FALLBACK_RATE` (USD→VND) is used by Stripe and PayPal if the Frankfurter adapter fails. Logs a WARN on every fallback hit.

## When to revisit this doc

- After Phase 1 ships → update Phase 2 section with concrete commits.
- After Phase 2 ships → revise the FE payment-step screenshot.
- When the business is registered → reopen Phase 3.
- If `developers.momo.vn` changes its sandbox onboarding to require business docs → fall back to ngrok-tunnel + IPN, document the trade-off here.
