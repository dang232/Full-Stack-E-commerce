# Deliverable 5: Acceptance Criteria Library

## Structure

Every feature follows Given/When/Then format. Grouped by lifecycle phase.

---

## 0. Market Fit (Validation Gates)

### MF-1: Value Proposition Visible

```
Given a new visitor lands on homepage for the first time
When the page loads
Then within the first viewport (no scroll) they see:
  - What VNShop is (marketplace)
  - Why it's different (trust/escrow/lower fees)
  - A clear CTA to browse or register
```

### MF-2: Seller Commission Transparency

```
Given a potential seller visits the "Sell on VNShop" page
When they read the pricing section
Then they see:
  - Commission rates per tier (STANDARD 10%, VERIFIED 8%, PREFERRED 5%, MALL 3%)
  - Comparison with Shopee/Lazada rates
  - How to qualify for lower tiers
  - No hidden fees
```

---

## 1. Acquire

### ACQ-1: SEO — Product Page Indexable

```
Given Googlebot crawls /product/iphone-16-pro-max
When the page is rendered
Then:
  - HTTP status is 200
  - <title> contains "iPhone 16 Pro Max" + price
  - <meta name="description"> contains product summary under 160 chars
  - JSON-LD Product schema is present and valid
  - og:image is the primary product photo
  - Page renders meaningful content without JavaScript (SSR)
```

### ACQ-2: Social Sharing

```
Given buyer shares a product URL on Facebook Messenger
When the recipient sees the preview
Then:
  - Product image is displayed (og:image)
  - Product name is the title (og:title)
  - Price is in the description (og:description)
  - VNShop branding visible
```

### ACQ-3: UTM Attribution

```
Given user clicks a link with ?utm_source=facebook&utm_medium=cpc&utm_campaign=summer
When they browse and eventually purchase
Then:
  - First-touch attribution is stored with the order
  - Admin analytics can filter orders by campaign
  - ROAS is calculable per campaign
```

---

## 2. Trust

### TRUST-1: Seller Rating on Product Page

```
Given buyer views a product detail page
When the page loads
Then the seller section shows:
  - Seller name (clickable → store page)
  - Star rating (e.g., 4.8/5)
  - Tier badge (STANDARD/VERIFIED/PREFERRED/MALL)
  - Total products sold
  - Response time (e.g., "Replies within 1 hour")
  - "Chat" button
  - "Visit Store" button
```

### TRUST-2: Escrow Badge Visible

```
Given buyer views any product page
When they look at the purchase section
Then they see:
  - "Protected by VNShop Escrow" badge with icon
  - Tooltip or link explaining: "Your payment is held until you confirm delivery"
```

### TRUST-3: Return Policy Accessible

```
Given buyer is considering a purchase
When they look for return information
Then:
  - Product page shows "15-day returns" badge (or seller-specific policy)
  - Clicking opens full return policy
  - Policy states: conditions, timeline, process, who pays return shipping
```

### TRUST-4: Verified Purchase Badge on Reviews

```
Given buyer reads reviews on a product page
When a review is from someone who purchased the product
Then:
  - "Verified Purchase" badge is displayed next to reviewer name
  - Reviews without purchase are marked differently or ranked lower
```

---

## 3. Discovery

### DISC-1: Search Autocomplete

```
Given buyer focuses on search bar
When they type "iph" (3+ characters)
Then:
  - Dropdown appears within 300ms
  - Shows up to 8 suggestions
  - Suggestions ranked by popularity
  - Matching text portion is highlighted
  - Clicking a suggestion navigates to search results for that term
  - Keyboard navigation (arrow keys + Enter) works
```

### DISC-2: Recent Searches

```
Given buyer has previously searched for "laptop" and "tai nghe"
When they focus the search bar (empty input)
Then:
  - "Recent searches" section appears in dropdown
  - Shows searches in reverse chronological order
  - Each has an X to delete
  - "Clear all" link at section bottom
  - Persists across page navigations
  - Persists across sessions for logged-in users
```

### DISC-3: Zero Results Handling

```
Given buyer searches for a term with no matching products
When results page loads
Then:
  - Clear message: "No results for '[query]'"
  - NO "Showing 1-0 of 0" display
  - Below: "You might like" section with popular/trending products
  - Below: "Try searching for:" with alternative suggestions
  - Search bar remains focused and editable
```

### DISC-4: Vietnamese Diacritics

```
Given buyer types "dien thoai" (no diacritics)
When search executes
Then:
  - Results include products matching "điện thoại"
  - Same quantity and quality as searching with correct diacritics
  - Works for: a/ă/â, e/ê, o/ô/ơ, u/ư, d/đ and all tone marks
```

### DISC-5: Recently Viewed

```
Given buyer has viewed products A, B, C during this session
When they navigate to homepage
Then:
  - "Recently viewed" section shows products C, B, A (newest first)
  - Maximum 20 items
  - Each shows current price (real-time, not cached from view time)
  - Clicking navigates to PDP
  - Persists across refreshes (localStorage)
  - Syncs to server for logged-in users (cross-device)
```

---

## 4. Convert

### CONV-1: Add to Cart

```
Given buyer is logged in, viewing product with stock ≥ requested quantity
When they click "Add to Cart"
Then:
  - Toast: "Added to cart ✓"
  - Navbar cart icon badge increments
  - Product appears in cart grouped under its seller
  - If already in cart: quantity increments (no duplicate line)
  - Cart total recalculates
```

### CONV-2: Cart Persistence

```
Given buyer has 3 items in cart
When browser is hard-refreshed (Ctrl+F5)
Then all 3 items remain with correct quantities and current prices

Given buyer logs out and logs back in
Then cart is restored from server-side storage

Given buyer added items on desktop
When they log in on mobile
Then same cart items appear (server-synced)
```

### CONV-3: Stock Validation at Checkout

```
Given buyer has item X (qty: 3) in cart
And item X stock is now 2 (changed since add-to-cart)
When buyer clicks "Place Order"
Then:
  - Order is NOT placed
  - Error: "Only 2 units of [item name] available"
  - Buyer returned to cart with item highlighted
  - Quantity auto-adjusted to maximum available OR buyer must manually fix
```

### CONV-4: Payment Routing by Currency

```
Given order total is in VND
When buyer reaches payment step
Then available methods are: COD, VietQR, MoMo
And Stripe/PayPal are NOT shown

Given order total is in USD
When buyer reaches payment step
Then available methods are: Stripe (card), PayPal
And COD/VietQR/MoMo are NOT shown
```

### CONV-5: Double-Submit Prevention

```
Given buyer clicks "Place Order"
When the request is processing
Then:
  - Button immediately becomes disabled
  - Spinner replaces button text
  - Clicking again has no effect
  - If user navigates back and forward, cannot re-submit
  - Only ONE order is created regardless of network latency
```

### CONV-6: Coupon Application

```
Given buyer enters valid coupon "WELCOME10" (10% off, max 50,000₫)
When they click "Apply"
Then:
  - Discount shown: "-50,000₫" (or calculated 10%)
  - "WELCOME10 applied" confirmation
  - Grand total updates
  - "Remove" link next to coupon
  - Cannot stack with another platform coupon (unless allowed)

Given buyer enters expired coupon "OLDCODE"
When they click "Apply"
Then:
  - Error inline: "This coupon has expired"
  - No changes to totals
  - Input not cleared (can edit and retry)
```

---

## 5. Fulfill

### FUL-1: Seller Accepts Order Within SLA

```
Given new order arrives for seller
When seller clicks "Accept"
Then:
  - Status → ACCEPTED
  - Buyer notification: "Seller is preparing your order"
  - Seller sees 48h countdown to ship

Given seller does NOT accept within 24h
Then:
  - System auto-rejects the order
  - Buyer notification: "Order cancelled — seller unresponsive. Refund initiated."
  - Refund auto-triggered
  - Seller penalty counter increments
```

### FUL-2: Confirm Receipt (Manual)

```
Given order status is DELIVERED
When buyer clicks "Confirm Receipt"
Then:
  - Confirmation dialog: "Confirm you received your order?"
  - If confirmed: status → CONFIRMED
  - Escrow released to seller wallet
  - Buyer prompted: "Would you like to leave a review?"
  - Seller notification: "Buyer confirmed — funds released"
```

### FUL-3: Confirm Receipt (Auto)

```
Given order delivered 7 days ago
And buyer has NOT confirmed OR disputed
Then:
  - System auto-confirms
  - Escrow released
  - Buyer notification: "Order auto-confirmed after 7 days"
  - Buyer can still dispute within 15 days of delivery
```

### FUL-4: Order Status Timeline

```
Given buyer views order detail
Then they see visual timeline:
  - Each state shows: icon + label + timestamp (if reached)
  - Current state is highlighted/animated
  - Future states are greyed
  - If SHIPPED: tracking number + carrier name shown
  - If DELIVERED: "Confirm Receipt" and "Report Problem" buttons appear
```

### FUL-5: Dispute Opening

```
Given order is DELIVERED and within 15-day window
When buyer clicks "Report Problem"
Then:
  - Issue type selector: Not received / Wrong item / Damaged / Not as described / Counterfeit
  - Evidence upload (photos required for damage/wrong item)
  - Text description field (required, min 20 chars)
  - Submit creates dispute
  - Seller has 48h to respond
  - Escrow is FROZEN (cannot be released until resolved)
```

### FUL-6: Seller Responds to Dispute

```
Given a dispute is opened against seller's order
When seller views the dispute
Then they see:
  - Buyer's complaint + evidence (photos + text)
  - Response form: Accept refund / Reject with evidence / Propose partial
  - 48h countdown to respond
  - If no response in 48h: auto-escalate to admin
```

### FUL-7: Admin Resolves Dispute

```
Given dispute is escalated to admin
When admin opens the dispute case
Then they see:
  - Buyer evidence + claim
  - Seller response + counter-evidence
  - Order history + tracking data
  - Decision options: Full refund / Partial refund / Reject claim
  - Notes field for decision reasoning
  - 72h SLA shown
  - Decision triggers: notification to both parties + escrow action
```

---

## Cross-Cutting

### CC-1: Notification Delivery

```
Given any state change that requires notification (per notification matrix)
When the state change is persisted
Then within 30 seconds:
  - In-app notification appears in notification bell (unread count updates)
  - Push notification sent (if user has enabled push)
  - Email sent (for high-priority events: order placed, shipped, delivered, dispute)
  - Notification is persisted and visible in notification history
```

### CC-2: Multi-Language

```
Given user has set language to Vietnamese
When they navigate any page
Then:
  - ALL text is in Vietnamese (no raw i18n keys visible)
  - Dates formatted as DD/MM/YYYY
  - Currency formatted as 1.000.000₫
  - No English leaking through (0 hardcoded strings)

Given user switches language to English
Then same requirement applies in English
And currency shows as ₫1,000,000 or $XX.XX as appropriate
```

### CC-3: Multi-Currency

```
Given product is listed in VND
And buyer's preference/location indicates USD
Then:
  - Price shown in both: "₫25,000,000 (~$980 USD)"
  - FX rate updated at least daily
  - Checkout processes in the displayed currency
  - Seller receives payout in VND regardless
```

### CC-4: Authentication Redirect

```
Given unauthenticated user tries to access protected page (/cart, /checkout, /orders, etc.)
Then:
  - Toast: "Please sign in to continue"
  - Redirect to /login?next=/original-page
  - After successful login: redirect BACK to /original-page (not homepage)
```

### CC-5: Mobile Responsive

```
Given user accesses VNShop on mobile (viewport < 768px)
Then:
  - All content is accessible (no horizontal scroll)
  - Touch targets are ≥ 44x44px
  - Forms are usable with mobile keyboard
  - Images are lazy-loaded
  - Navigation is via hamburger menu or bottom tab bar
  - Cart and checkout flow works fully on mobile
```
