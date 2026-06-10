# 4. Convert

## The Core Question

**"Will I actually pay?"**

Conversion is the moment trust, discovery, and intent collapse into a transaction. Every friction point here costs real money.

---

## Conversion Funnel

```
Product Detail Page (PDP)
        ↓ [Add to Cart]  ←── 40% drop here if trust is low
Cart Page
        ↓ [Proceed to Checkout]  ←── 30% drop (price shock, shipping fees)
Checkout: Address
        ↓ [Continue]  ←── 10% drop (no saved address, form friction)
Checkout: Shipping
        ↓ [Continue]  ←── 5% drop (shipping cost surprise)
Checkout: Payment
        ↓ [Place Order]  ←── 15% drop (payment method unavailable, trust)
Order Confirmation
        ↓
SUCCESS
```

**Industry benchmark:** Cart-to-order conversion = 30-40% for Shopee-class platforms.

---

## Stage 1: Product Detail Page → Add to Cart

### What Must Be on PDP

| Element | Purpose | Priority |
|---|---|---|
| Product name | Identification | P0 |
| Price (formatted, with currency) | Decision driver | P0 |
| Original price + discount % | Urgency, value perception | P0 |
| Product images (5+) | Visual confidence | P0 |
| Seller name + rating + badge | Trust | P0 |
| "Add to Cart" button | Primary CTA | P0 |
| "Buy Now" button | Skip cart, direct checkout | P1 |
| Quantity selector | Multi-buy | P0 |
| Stock status | Availability | P0 |
| Variant selector (color/size) | Product options | P0 |
| Delivery estimate | "Arrives in 2-3 days to HCM" | P1 |
| Shipping fee preview | No surprise at checkout | P1 |
| COD available badge | Vietnamese buyer confidence | P0 |
| "Escrow protected" badge | Trust | P0 |
| Return policy | Risk reduction | P1 |
| Reviews summary (star + count) | Social proof | P0 |
| "Chat with seller" button | Pre-sale questions | P1 |
| Wishlist heart | Save for later | P1 |
| Share button | Social discovery | P2 |

### Current State vs Required

| Element | Status | Gap |
|---|---|---|
| Product name + price | ✅ | — |
| Discount display | ✅ | — |
| Images | ⚠️ Single image | P1 — need gallery |
| Seller info on PDP | ❌ No rating/badge/name prominent | P0 |
| Add to Cart | ✅ | — |
| Buy Now (direct checkout) | ❌ Not built | P1 |
| Quantity selector | ✅ | — |
| Stock status | ⚠️ Data exists, display unclear | P1 |
| Variant selector | ❌ Not built | P1 |
| Delivery estimate | ❌ Not calculated | P1 |
| Shipping fee preview | ❌ Only shown at checkout | P1 |
| COD badge | ❌ Not shown | P0 |
| Escrow badge | ❌ Not shown | P0 |
| Return policy | ❌ No policy exists | P0 |
| Reviews summary | ✅ Rating shown | OK |
| Chat with seller | ❌ Not linked from PDP | P1 |

---

## Stage 2: Cart Page

### Cart UX Requirements

| Feature | Shopee Reference | Purpose | Priority |
|---|---|---|---|
| Group by seller | ✅ Seller name header per group | Show shipping applies per seller | P0 |
| Per-item quantity ± | ✅ Inline +/- buttons | Adjust without leaving | P0 |
| Remove item | ✅ Trash icon | Easy removal | P0 |
| Per-seller shipping fee | ✅ Shown per group | No surprise at checkout | P0 |
| Subtotal per seller | ✅ | Clarity | P1 |
| Grand total | ✅ Bottom sticky bar | Decision driver | P0 |
| Coupon/voucher input | ✅ Per seller + platform | Discount application | P0 |
| "Select all" checkbox | ✅ | Bulk operations | P1 |
| Item checkbox (partial checkout) | ✅ | Buy some items, save others | P1 |
| Stock validation | ✅ "Only 2 left" warning | Prevent checkout failure | P0 |
| Price change alert | ✅ "Price increased since added" | Honesty | P1 |
| Save for later | ✅ Move to wishlist | Don't lose the item | P2 |
| Cart persistence | ✅ Survives refresh + re-login | Non-negotiable | P0 |
| Empty cart CTA | ✅ "Continue shopping" | Re-engage | P0 |

### Cart Persistence Requirements

```
Given buyer adds 3 items to cart
When:
  - Browser refreshes → Cart intact ✅
  - Browser closes + reopens → Cart intact ✅
  - User logs out → Guest cart cleared
  - User logs back in → Server-side cart restored ✅
  - Same user on different device → Same cart (server-synced) ✅
  - Item goes out of stock → Greyed out with "Out of stock" badge, cannot checkout ✅
  - Item price changes → Updated price shown with "Price changed" indicator ✅
```

### Current State

| Feature | Status | Gap |
|---|---|---|
| Grouped by seller | ✅ | OK |
| Quantity adjustment | ✅ | OK |
| Remove item | ✅ | OK |
| Shipping fee per seller | ⚠️ Shown in cart summary | Verify per-seller |
| Coupon input | ✅ | OK |
| Cart persistence (refresh) | ✅ Server-side cart | OK |
| Cart persistence (re-login) | ✅ Cart tied to user | OK |
| Stock validation at checkout | ❓ Needs verification | P1 |
| Price change alert | ❌ Not built | P2 |
| Partial checkout (select items) | ❌ All-or-nothing | P2 |

---

## Stage 3: Checkout Flow

### Checkout Steps

```
Step 1: Address Selection/Entry
Step 2: Shipping Method Selection (per seller group)
Step 3: Payment Method Selection
Step 4: Order Review + Place Order
```

### Step 1: Address

| Feature | Required Behavior | Priority |
|---|---|---|
| Address book | Show saved addresses, select one | P0 |
| Default address | Pre-selected, one click to proceed | P0 |
| Add new address | Inline form without leaving checkout | P0 |
| Address validation | Province/District/Ward dropdown | P1 |
| Phone number | Required per address (for delivery) | P0 |
| Recipient name | Can differ from account name (gift) | P0 |

### Step 2: Shipping

| Feature | Required Behavior | Priority |
|---|---|---|
| Carrier options per seller | GHN Standard, GHN Express, Self-ship | P0 |
| Delivery estimate per option | "2-3 days" / "Next day" | P1 |
| Shipping fee per option | Calculated from address + weight | P0 |
| Free shipping threshold | "Free shipping over 500k" badge | P1 |
| Seller-specific shipping rules | Some sellers only ship to certain regions | P2 |

### Step 3: Payment

| Feature | Required Behavior | Priority |
|---|---|---|
| Show only available methods | Based on currency + amount | P0 |
| COD | Always available for VND domestic | P0 |
| VietQR | Show QR code after order placed | P0 |
| MoMo | Redirect to MoMo app/web | P0 |
| Stripe (cards) | Inline card form for USD | P0 |
| PayPal | Redirect flow for USD | P0 |
| Save payment method | "Remember this card" | P2 |
| Payment method by currency | VND→COD/VietQR/MoMo, USD→Stripe/PayPal | P0 |

### Step 4: Review + Place

| Feature | Required Behavior | Priority |
|---|---|---|
| Full order summary | Items, quantities, prices | P0 |
| Shipping address displayed | With "Change" link | P0 |
| Shipping method + fee | Per seller group | P0 |
| Payment method | With "Change" link | P0 |
| Coupon applied | Show discount amount | P0 |
| Grand total (prominent) | Including all fees | P0 |
| "Place Order" button | Single click, no double-submit | P0 |
| Terms acknowledgment | "By ordering you agree to..." | P1 |
| Loading state | Disable button + spinner on submit | P0 |
| Error handling | Payment declined → friendly message + retry | P0 |

### Current State

| Feature | Status | Gap |
|---|---|---|
| 4-step checkout flow | ✅ CheckoutPage with steps | OK |
| Address selection | ✅ | OK |
| Add new address in checkout | ✅ | OK |
| Shipping method selection | ⚠️ Exists but carrier options unclear | P1 |
| Payment method selection | ✅ COD confirmed working | OK |
| VietQR | ✅ Backend exists | Verify FE flow |
| MoMo | ⚠️ Toggle exists, untested | P1 |
| Stripe | ⚠️ Toggle exists, needs domain for webhook | P2 (blocked) |
| PayPal | ⚠️ Toggle exists, needs domain | P2 (blocked) |
| Order review step | ✅ | OK |
| Double-submit prevention | ❓ Needs verification | P1 |
| Payment error handling | ❓ Needs verification | P1 |

---

## Stage 4: Order Confirmation

### Post-Payment Success Screen

| Element | Purpose | Priority |
|---|---|---|
| ✅ "Order placed" confirmation | Relief | P0 |
| Order ID (copyable) | Reference | P0 |
| Summary of items | Confirmation | P0 |
| Estimated delivery date | Set expectation | P1 |
| "View order" button | Track progress | P0 |
| "Continue shopping" button | Re-engage | P0 |
| COD reminder | "Pay X₫ on delivery" | P0 |
| VietQR code | If VietQR selected, show QR to pay | P0 |
| Email confirmation sent | "Check your inbox" | P1 |

### Current State

| Element | Status | Gap |
|---|---|---|
| Success page | ✅ CheckoutSuccess exists | OK |
| Order ID | ✅ Shown | OK |
| COD notice | ✅ Shows payment amount | OK |
| View orders link | ✅ | OK |
| Continue shopping link | ✅ | OK |
| Delivery estimate | ❌ Not shown | P1 |
| VietQR display post-order | ⚠️ Exists in payment-service | Verify FE integration |
| Email confirmation | ⚠️ SES toggle exists, off by default | P2 (needs domain) |

---

## Conversion Killers (Must Fix)

| Killer | Current Evidence | Impact | Fix |
|---|---|---|---|
| Login wall before cart | Audit: guest blocked with no context | High — lose impulse buyers | Toast + redirect implemented (this session) |
| Search returns 0 results | Audit: "iphone" returns nothing | Critical — can't buy what you can't find | ES health + autocomplete |
| No seller trust on PDP | No rating, no badge, no response time | High — "is this seller real?" | Add seller card to PDP |
| No escrow visibility | Buyer doesn't know money is protected | High — payment anxiety | Add badge to PDP + checkout |
| No return policy | Buyer assumes no returns | Medium — hesitation at payment | Create and link policy page |
| Shipping fee surprise | Fee only visible at checkout step 2 | Medium — cart abandonment | Show estimate on PDP |
| No delivery estimate | "When will I get it?" unanswered | Medium — uncertainty | Calculate from address |

---

## Acceptance Criteria

### AC-1: Add to Cart (Authenticated)

```
Given buyer is logged in and viewing a product with stock > 0
When they click "Add to Cart" with quantity = 1
Then:
  - Toast appears: "Added to cart"
  - Cart icon in navbar updates count (+1)
  - Product appears in cart page grouped under its seller
  - Cart total updates to include this item's price
  - If item already in cart, quantity increments instead of duplicate entry
```

### AC-2: Cart Persistence

```
Given buyer has items in cart
When browser is refreshed (F5 or navigate away and back)
Then:
  - All cart items remain with correct quantities
  - Prices reflect current product prices (not stale)
  - Out-of-stock items are flagged but not removed
```

### AC-3: Checkout Payment Routing

```
Given buyer's order total is in VND
When they reach the payment step
Then:
  - Available methods shown: COD, VietQR, MoMo
  - Stripe and PayPal are NOT shown

Given buyer's order total is in USD
When they reach the payment step
Then:
  - Available methods shown: Stripe (card), PayPal
  - COD, VietQR, MoMo are NOT shown
```

### AC-4: Place Order (COD)

```
Given buyer has completed address, shipping, and selected COD
When they click "Place Order"
Then:
  - Button becomes disabled with spinner (no double-submit)
  - Order is created in backend with status PENDING
  - Escrow status: N/A (COD collects on delivery)
  - Success page shows order ID and "Pay X₫ on delivery"
  - Notification sent to seller: "New order received"
  - Buyer receives in-app notification: "Order #X confirmed"
```

### AC-5: Place Order (VietQR)

```
Given buyer has selected VietQR payment
When they click "Place Order"
Then:
  - Order created with status AWAITING_PAYMENT
  - Success page shows VietQR code with:
    - Bank name + account number
    - Amount (exact VND)
    - Reference code (order ID)
    - QR image (scannable by any banking app)
    - Timer: "Pay within 24 hours or order is cancelled"
  - If payment confirmed (SePay webhook or admin manual) → status changes to PAID
  - Notification sent to buyer: "Payment received"
```

### AC-6: Stock Validation at Checkout

```
Given buyer has item X (qty: 2) in cart
And item X stock drops to 1 between add-to-cart and checkout
When buyer clicks "Place Order"
Then:
  - Order is NOT placed
  - Error message: "Item X only has 1 unit available. Please adjust quantity."
  - Buyer is returned to cart with item X highlighted
  - Stock count shown next to the item
```

### AC-7: Coupon Application

```
Given buyer enters valid coupon code "SAVE20"
When they click "Apply"
Then:
  - Discount calculated and shown in order summary
  - "SAVE20 applied — You save X₫" confirmation
  - Grand total updates
  - "Remove" button appears next to applied coupon

Given buyer enters invalid/expired coupon code
When they click "Apply"
Then:
  - Error: "Coupon is invalid or expired"
  - No changes to total
  - Input remains focused for retry
```

---

## KPIs

| KPI | Definition | Target |
|---|---|---|
| PDP → Add to Cart | % of PDP views resulting in add-to-cart | >8% |
| Cart → Checkout Start | % of cart visits that begin checkout | >60% |
| Checkout Start → Order | % of checkout starts that complete | >70% |
| Overall conversion | Unique visitors → orders | >2% |
| AOV (Average Order Value) | Total revenue / number of orders | >350,000 VND |
| Payment method split | Distribution across methods | COD <60% (reduce over time) |
| Cart abandonment rate | Added to cart but never purchased | <65% |
| Checkout drop-off per step | Where in the 4 steps do users leave | Identify and fix |
