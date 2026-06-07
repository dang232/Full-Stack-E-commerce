# Payment Method Sandbox Setup

## Methods that work out of the box

### COD — Cash on Delivery

No credentials or external accounts needed. Set `COD_ENABLED=true` (the default) and it works immediately.

### VietQR — Bank Transfer via QR Code

Generates a QR code using the free, stateless [vietqr.io](https://vietqr.io) image service — no API key and no webhook required. The buyer scans the QR with their bank app and transfers the exact amount. Confirmation is manual: an admin calls `POST /admin/vietqr/confirm/{paymentId}` once the credit notification is seen.

Required env vars:

| Variable | Description | Default |
|---|---|---|
| `VIETQR_ENABLED` | Enable/disable | `true` |
| `VIETQR_BANK_BIN` | Bank BIN code. Vietcombank=`970436`, TCB=`970407`, MB=`970422` — full list at https://api.vietqr.io/v2/banks | `970436` |
| `VIETQR_ACCOUNT_NO` | Destination account number | — |
| `VIETQR_ACCOUNT_NAME` | Account holder name (as registered with the bank) | — |

---

## Methods that require an external sandbox account

### SePay — Automated bank-transfer confirmation

SePay listens to your bank account and sends webhooks when a transfer matches. It enables auto-confirmation of VietQR transfers without manual admin action.

1. Sign up at [https://sepay.vn](https://sepay.vn).
2. Link your bank account under **Tài khoản ngân hàng**.
3. Copy the numeric **Account ID** shown for your linked account.
4. Generate an **API Key** under **API Key**.
5. Set env vars:

```
SEPAY_ENABLED=true
SEPAY_API_KEY=<your key>
SEPAY_ACCOUNT_ID=<numeric account id>
```

Sandbox note: SePay does not have a separate sandbox environment — use a real bank account in test mode or mock the callbacks manually.

---

### Stripe — International card payments

1. Sign up at [https://dashboard.stripe.com/register](https://dashboard.stripe.com/register) (free, no business registration needed for sandbox).
2. Go to **Developers → API keys** and copy the **Publishable key** (`pk_test_...`) and **Secret key** (`sk_test_...`).
3. Install the [Stripe CLI](https://stripe.com/docs/stripe-cli) and run:
   ```
   stripe listen --forward-to localhost:8092/payment/stripe/webhook
   ```
   Copy the `whsec_...` printed in the terminal.
4. Set env vars:

```
STRIPE_ENABLED=true
STRIPE_SECRET_KEY=sk_test_...
STRIPE_PUBLISHABLE_KEY=pk_test_...
STRIPE_WEBHOOK_SECRET=whsec_...
```

Test card numbers:
- `4242 4242 4242 4242` — succeeds
- `4000 0000 0000 9995` — declines with insufficient_funds

---

### PayPal — PayPal wallet / card

1. Sign up at [https://developer.paypal.com](https://developer.paypal.com).
2. Go to **My Apps & Credentials → Create App**.
3. Copy the **Client ID** and **Secret** for the sandbox app.
4. Use the sandbox personal account and sandbox business account created automatically on signup to test buyer flows.
5. Set env vars:

```
PAYPAL_ENABLED=true
PAYPAL_CLIENT_ID=<sandbox client id>
PAYPAL_CLIENT_SECRET=<sandbox client secret>
PAYPAL_MODE=sandbox
```

---

### VNPay — Vietnamese domestic card / internet banking

1. Register a developer/merchant account at [https://sandbox.vnpayment.vn/devreg](https://sandbox.vnpayment.vn/devreg).
2. After approval (usually within 1 business day), you receive:
   - **TMN Code** (Terminal ID)
   - **Hash Secret**
3. Set env vars:

```
VNPAY_ENABLED=true
VNPAY_TMN_CODE=<your TMN code>
VNPAY_HASH_SECRET=<your hash secret>
```

Test card: use the card numbers provided in the VNPay sandbox documentation after login.

Note: VNPay IPN requires a publicly reachable URL. For local development, use [ngrok](https://ngrok.com):
```
ngrok http 8092
```
Then set `VNPAY_IPN_URL=https://<ngrok-subdomain>.ngrok.io/payment/vnpay/ipn`.

---

### MoMo — MoMo e-wallet

1. Request sandbox access at [https://developers.momo.vn](https://developers.momo.vn) via the contact form (**Tích hợp thanh toán**).
2. Sandbox credentials are emailed: **Partner Code**, **Access Key**, **Secret Key**.
3. Set env vars:

```
MOMO_ENABLED=true
MOMO_PARTNER_CODE=<partner code>
MOMO_ACCESS_KEY=<access key>
MOMO_SECRET_KEY=<secret key>
```

Note: MoMo IPN also requires a publicly reachable URL. Use ngrok as described in the VNPay section and set `MOMO_IPN_URL` accordingly.

---

## Verifying enabled methods

After starting the payment service with your credentials set, call:

```
GET /api/v1/payments/methods
```

The response lists every method that is both enabled and correctly configured:

```json
{
  "success": true,
  "data": [
    { "id": "cod",    "name": "Cash on Delivery",              "enabled": true },
    { "id": "vietqr", "name": "VietQR Bank Transfer",          "enabled": true },
    { "id": "stripe", "name": "Credit / Debit Card (Stripe)",  "enabled": true }
  ]
}
```

If a method is enabled but its credentials are missing, the service will **refuse to start** with a clear error message indicating which environment variable is missing. This prevents silent failures at checkout time.
