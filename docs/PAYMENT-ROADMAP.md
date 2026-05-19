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
| 1 | VietQR | personal VN bank account | ⏳ awaiting bank details |
| 2 | MoMo | dev-portal email + phone | ⏳ Phase 1 first |
| 3 | VNPay | registered VN business (MST + GPKD) | ⛔ deferred |
| 3 | Stripe | registered business + USD merchant account | ⛔ deferred |
| 3 | PayPal | registered business | ⛔ deferred |

## Phase 1 — what runs today (no infrastructure, no business registration)

### COD
Already live at `payment.mode=stub`. `POST /payment/cod/confirm` auto-completes. No work needed.

### VietQR (manual confirmation flow)

Already coded — `VietQrService.java`, `AdminVietQrController.java`, `VIETQR` branch in `LivePaymentGateway`. The flow:

1. Buyer picks VietQR at checkout.
2. FE calls `POST /payment/vietqr/create` → BE returns a `vietqr.io` image URL plus account number / name / reference.
3. FE renders the QR. Buyer scans with their banking app (any VN bank's app reads VietQR), confirms transfer, includes the order ID in the memo.
4. Owner sees the transfer notification on their phone.
5. Owner hits `POST /admin/vietqr/confirm/{paymentId}` (or clicks a button in admin UI). Order moves to `PAID`. Saga proceeds normally.

**What's missing to flip it on:**
- `VIETQR_BANK_BIN` (970436=VCB, 970407=TCB, 970422=MB, 970416=ACB, 970403=Sacombank, 970423=TPBank, full list at https://vietqr.io/danh-sach-api)
- `VIETQR_ACCOUNT_NO` — account number
- `VIETQR_ACCOUNT_NAME` — name on the account, no diacritics

These go in `.env` (gitignored). The `.env.example` keeps them blank.

**Why this is good enough for now:**
- No webhook → no public domain or tunnel needed.
- No PSP fees on transfers (banks settle for free between Vietnamese accounts).
- Works on every VN banking app — same UX buyers already use on Shopee live-streams and Facebook shops.
- Reconciliation is manual but bounded: the shop owner is already monitoring their bank app for orders, so the friction is essentially zero.

**Limitations to acknowledge:**
- Owner must be online to confirm. Acceptable at sole-proprietor scale; doesn't scale past a few orders/day.
- No automatic timeout if buyer abandons after seeing QR. Currently `payment.vnpay.expire-minutes=15` exists but VietQR doesn't expire on the bank side; we'd add a sweep job in Phase 2 if it becomes a problem.

## Phase 2 — wallet payments (still no business)

### MoMo via dev sandbox

`developers.momo.vn` registers with email + phone, no business documents. You get test `partner-code` / `access-key` / `secret-key` immediately.

**The webhook problem:** MoMo sandbox normally pushes IPN (server-to-server callback) to your public URL. We don't have a public domain. Two options:

1. **ngrok / cloudflared tunnel** — works but URL changes per restart on the free tier; brittle.
2. **Polling instead of webhook** — `MomoGateway.getStatus(paymentId)` already calls MoMo's `/v2/gateway/api/query` REST endpoint. The buyer redirects to MoMo, pays, redirects back. The order-confirmation page polls `GET /payment/status/{orderId}` every 3s for up to 5 minutes. Status flips to `COMPLETED` when MoMo returns `resultCode=0`.

**We pick option 2.** Real VN apps do this anyway because IPN delivery on mobile carrier networks is unreliable — apps treat IPN as a hint and rely on the query DR endpoint as the source of truth.

**What's needed to ship Phase 2:**
- Set `PAYMENT_MODE=live` in compose env.
- `MOMO_PARTNER_CODE`, `MOMO_ACCESS_KEY`, `MOMO_SECRET_KEY` from the dev-portal sandbox merchant.
- `MOMO_REDIRECT_URL=http://localhost:8080/order-confirmation/{orderId}` — for sandbox testing on the dev box this can be localhost; the buyer's browser does the redirect, so localhost is the right answer here.
- `MOMO_IPN_URL` — leave blank or set to `http://127.0.0.1:0` (MoMo will try to push, fail, and we don't care because polling carries the truth).
- FE order-confirmation page: poll `/payment/status/{orderId}` until `COMPLETED` or 5min timeout. Show "Đang xác nhận thanh toán…" while polling. On timeout show "Chúng tôi sẽ tự động cập nhật khi MoMo xác nhận" and let the user navigate away.

### COD + VietQR + MoMo together

Once Phase 2 is in, the FE checkout payment step shows three options: COD, VietQR, MoMo. That covers the realistic VN sole-proprietor coverage envelope.

## Phase 3 — registered business required (deferred indefinitely)

### VNPay

**Status: deferred.** `sandbox.vnpayment.vn` returns 404 to anonymous browsers and the merchant onboarding flow asks for:
- MST (mã số thuế — tax code)
- GPKD (giấy phép kinh doanh — business license)
- Bank account in the registered business name (not personal)
- Signed integration contract

None of these exist for the current shop. Re-evaluate when the business is incorporated.

The code stays — `VnpayGateway`, `VnpayCallbackService`, `VnpaySigner`, `VnpayProperties`, the `vnpay/create`+`vnpay/return`+`vnpay/ipn` endpoints, and the V8 callback-audit migration are all in place behind `@ConditionalOnProperty(payment.mode=live)` plus blank-default credentials. Flipping VNPay on later is a config change, not a re-implementation.

**Do not delete** the VNPay code paths even though they're not exercised — re-introducing them later is more risk than the carrying cost of unreferenced classes. The unit tests still cover the signer logic, which is the only thing that's hard to get right.

### Stripe

Similar gating: Stripe requires a registered business and a USD-receivable merchant account. Useful only if the shop sells to international buyers — at which point the business question is bigger than payments.

### PayPal

Same gating as Stripe.

## Configuration matrix (what each env-var means in each phase)

| Variable | Phase 1 | Phase 2 | Phase 3 (deferred) |
|---|---|---|---|
| `PAYMENT_MODE` | `stub` | `live` | `live` |
| `VIETQR_BANK_BIN` | required | required | required |
| `VIETQR_ACCOUNT_NO` | required | required | required |
| `VIETQR_ACCOUNT_NAME` | required | required | required |
| `MOMO_PARTNER_CODE` | (blank) | required | required |
| `MOMO_ACCESS_KEY` | (blank) | required | required |
| `MOMO_SECRET_KEY` | (blank) | required | required |
| `VNPAY_TMN_CODE` | (blank) | (blank) | required |
| `VNPAY_HASH_SECRET` | (blank) | (blank) | required |
| `VNPAY_RETURN_URL` | (default) | (default) | https://your-domain/payment/vnpay/return |
| `VNPAY_IPN_URL` | (default) | (default) | https://your-domain/payment/vnpay/ipn |

Sandbox defaults in `application.yml` point at the test endpoints; for production swap to `https://pay.vnpay.vn/vpcpay.html` (VNPay) and `https://payment.momo.vn/v2/gateway/api/create` (MoMo).

## When to revisit this doc

- After Phase 1 ships → update Phase 2 section with concrete commits.
- After Phase 2 ships → revise the FE payment-step screenshot.
- When the business is registered → reopen Phase 3.
- If `developers.momo.vn` changes its sandbox onboarding to require business docs → fall back to ngrok-tunnel + IPN, document the trade-off here.
