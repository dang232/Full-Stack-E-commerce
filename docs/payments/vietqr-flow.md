# VietQR / SePay Payment Flow

VietQR is enabled by default alongside COD. Buyers scan a QR code generated
from the merchant's bank account details and complete the transfer in their
own banking app. There is no card number, no redirect — just a bank transfer
with the payment UUID embedded in the memo field.

## Sequence Diagram

```
Buyer              VNShop Frontend        VNShop Backend          SePay           Bank
  |                      |                      |                    |               |
  |-- POST /payment ----->|                      |                    |               |
  |                      |-- ProcessPayment ---->|                    |               |
  |                      |                      |-- save PENDING ---->|               |
  |                      |<-- qrImageUrl --------|                    |               |
  |<-- render QR ---------|                      |                    |               |
  |                       |                      |                    |               |
  |-- scan QR + transfer -|----------------------|--------------------+-------------->|
  |                       |                      |                    |<-- credit -----|
  |                       |                      |                    |               |
  |                       |                      |<-- POST /payment/sepay/webhook ----|
  |                       |                      |    (push mode, if configured)      |
  |                       |                      |                    |               |
  |                       |                      |-- verify sig       |               |
  |                       |                      |-- dedup check      |               |
  |                       |                      |-- extract UUID     |               |
  |                       |                      |-- promote PENDING→COMPLETED        |
  |                       |                      |-- emit outbox evt  |               |
  |                       |                      |                    |               |
  |                       |<-- order confirmed --|                    |               |
  |<-- order confirmed ---|                      |                    |               |
```

## Two Confirmation Modes

### Push (webhook) — preferred

SePay calls `POST /payment/sepay/webhook` for each confirmed bank credit.
Handled by `SepayWebhookController`. Requires a publicly accessible URL.

**Local dev:** use ngrok or similar to expose the service:

```bash
ngrok http 8092
# then set in SePay dashboard: https://<ngrok-id>.ngrok.io/payment/sepay/webhook
```

**SePay dashboard configuration:**
1. Log in to https://my.sepay.vn
2. Go to Settings → Webhook URL
3. Set URL to `https://<your-domain>/payment/sepay/webhook`
4. Copy the webhook secret shown and set `SEPAY_WEBHOOK_SECRET=<value>`

### Poll — fallback

`SepayPoller` queries the SePay transaction list API every
`SEPAY_POLL_INTERVAL_SECONDS` (default 30 s). No public URL needed.
Enable with `SEPAY_ENABLED=true` + `SEPAY_API_KEY` + `SEPAY_ACCOUNT_ID`.

### Manual admin confirm — last resort

If neither push nor poll catches the credit (e.g. SePay outage), an admin
can confirm manually:

```
POST /admin/vietqr/confirm/{paymentId}
{ "bankReference": "FT24123456789" }
```

Requires `ROLE_ADMIN`. Idempotent — safe to call on an already-completed payment.

## Signature Verification

SePay authenticates push callbacks with:

```
Authorization: Apikey <webhookSecret>
```

`SepayWebhookController.verifySignature()` uses `MessageDigest.isEqual`
(constant-time) to compare the received header against
`payment.sepay.webhookSecret`. Requests with a missing or wrong token are
rejected with HTTP 401 before any business logic runs.

If `SEPAY_WEBHOOK_SECRET` is empty the controller logs a warning and accepts
all callbacks. Never leave it empty in production.

## Idempotency

`PaymentCallbackLogStore.findProcessed("SEPAY", txId, payloadHash, signatureHash)`
deduplicates callbacks. A duplicate delivery returns 200 without re-processing.
The same store is shared with the poller path — whichever arrives first wins.

## Payment Timeout

`VietQrTimeoutJob` runs every `VIETQR_TIMEOUT_CHECK_INTERVAL_SECONDS` (default 60 s)
and transitions PENDING VietQR payments older than `VIETQR_TIMEOUT_MINUTES`
(default 10 min) to `PAYMENT_TIMEOUT`.

| Env var | Default | Description |
|---|---|---|
| `VIETQR_TIMEOUT_MINUTES` | `10` | How long to wait for a bank credit |
| `VIETQR_TIMEOUT_CHECK_INTERVAL_SECONDS` | `60` | How often the sweep runs |

## Environment Variables

| Variable | Required | Description |
|---|---|---|
| `VIETQR_BANK_BIN` | No (default: `970436` Vietcombank) | Bank BIN code |
| `VIETQR_ACCOUNT_NO` | Yes (for QR gen) | Merchant bank account number |
| `VIETQR_ACCOUNT_NAME` | Yes (for QR gen) | Merchant account name |
| `SEPAY_ENABLED` | No (default: `false`) | Enable SePay integration |
| `SEPAY_API_KEY` | When SePay enabled | SePay API key (polling) |
| `SEPAY_ACCOUNT_ID` | When SePay enabled | SePay linked account id |
| `SEPAY_WEBHOOK_SECRET` | Recommended | Webhook signature secret |

## Notes

- The QR image is generated by the stateless vietqr.io image service — no
  API key, no server-side state. The merchant's account details + amount +
  payment UUID are encoded directly in the URL.
- The payment UUID is embedded in the QR's `addInfo` field. SePay reads the
  bank memo and includes it in `transaction_content` of the callback payload.
- VNShop does not verify the transferred amount matches the payment amount —
  the bank credit is accepted as confirmation regardless of amount. Add an
  amount check in `SepayWebhookController` if stricter validation is needed.
