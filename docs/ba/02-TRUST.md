# 2. Trust

## The Core Question

**"Can I trust this?" comes before "Can I buy this?"**

A customer will not enter payment details on a platform they don't trust. Trust must be built at EVERY touchpoint, not just one "About Us" page.

---

## Trust Architecture

### The Trust Stack (what customers evaluate)

```
Layer 5: Platform Trust     — "Is VNShop a real company?"
Layer 4: Payment Trust      — "Is my money safe?"
Layer 3: Seller Trust       — "Is this seller legitimate?"
Layer 2: Product Trust      — "Is this product real/as described?"
Layer 1: Delivery Trust     — "Will I actually receive it?"
Layer 0: Recourse Trust     — "What if something goes wrong?"
```

Each layer must be addressed. Missing ANY layer breaks the chain.

---

## Layer 5: Platform Trust

### What Customer Looks For

| Signal | Where | Shopee/Taobao Reference |
|---|---|---|
| Company registration / business license | Footer, About page | Shopee shows "Shopee Pte Ltd" |
| Physical address | Footer | Required by Vietnamese e-commerce law |
| Customer service phone/chat | Header, floating widget | Shopee: 24/7 hotline displayed |
| SSL certificate (HTTPS) | Browser bar | Non-negotiable |
| Professional design | Entire site | If it looks amateur, trust = 0 |
| Media mentions / awards | Homepage | "As seen on VnExpress" |
| User count / transaction count | Homepage | "500,000+ happy customers" |
| Social media presence | Footer links | Active Facebook/Zalo page |

### Current State

| Signal | Status | Gap |
|---|---|---|
| Business license visible | ❌ Missing | P1 |
| Physical address | ❌ Missing from footer | P1 |
| Customer service contact | ✅ "1800 6789 (Free)" in nav | OK |
| HTTPS | ✅ (when deployed with cert) | OK |
| Professional design | ✅ Design system exists | OK |
| Social proof counters | ❌ No "X sellers, Y products" | P1 |
| Social media links | ❌ Footer links are dead | P1 |

---

## Layer 4: Payment Trust

### What Customer Needs to See BEFORE Checkout

| Signal | Where to Show | Why |
|---|---|---|
| "Secure Payment" badge | Cart summary, checkout header | Reduces checkout anxiety |
| Payment partner logos | Checkout, footer | Visa/MC/MoMo logos = instant trust |
| "Escrow protection" explanation | Product page, checkout | "We hold your money until you confirm" |
| SSL/encryption notice | Checkout | "256-bit encrypted" |
| Refund policy link | Product page, checkout | "Easy returns within 15 days" |
| COD availability | Product card, checkout | Vietnamese buyers trust COD above all |

### Escrow Trust (Taobao Model)

```
Buyer pays → VNShop holds funds → Seller ships → Buyer confirms → Seller receives

If buyer doesn't confirm within 7 days → Auto-release
If buyer disputes → Funds frozen until resolution
```

**This must be VISIBLE to the buyer.** Not just backend logic.

UI requirements:
- Product page: "Payment protected by VNShop Escrow"
- Order detail: Progress bar showing "Funds held → Shipped → Delivered → Released"
- Seller page: "This seller's payments are escrow-protected"

### Current State

| Signal | Status | Gap |
|---|---|---|
| SSL mention at checkout | ✅ "SSL-256-bit encrypted" text exists | OK |
| Payment logos | ❌ Not shown | P1 |
| Escrow explanation | ❌ Not visible anywhere | P0 |
| Refund policy | ❌ No policy page | P0 |
| COD badge on products | ❌ Not shown per-product | P1 |

---

## Layer 3: Seller Trust

### What Customer Evaluates on Seller/Product Page

| Signal | Shopee Shows | Taobao Shows | eBay Shows | VNShop Must Show |
|---|---|---|---|---|
| Seller rating | ⭐ 4.8/5 | Crown/Diamond/Heart | Feedback % | ⭐ + percentage |
| Store age | "Joined 3 years ago" | Years active | Member since | Join date |
| Total sales | "50k products sold" | Transaction count | Items sold | Sold count |
| Response time | "Replies within minutes" | Response rate | — | Response time |
| Response rate | "97%" | "98% reply rate" | — | Rate % |
| Verified badge | ✅ Preferred Seller | Crown levels | Top Rated | ✅ Tier badge |
| Follower count | "10k followers" | Fans count | Followers | Follower count |
| Return rate | — | Return % | — | Optional |
| Physical location | City/Province | City | Country | Province |

### Seller Page Requirements

Every seller MUST have a public store page showing:

```
┌──────────────────────────────────────────────────────┐
│ [Store Banner Image]                                 │
│                                                      │
│ [Store Logo] STORE NAME           [Follow] [Chat]    │
│ ⭐ 4.8 | 1,234 followers | Joined Jan 2024          │
│ 📦 5,000+ sold | 💬 Replies in <1hr | 98% positive  │
│                                                      │
│ Badge: [✅ VERIFIED] or [🏪 MALL] or [⭐ PREFERRED]  │
│                                                      │
│ Tabs: [Products] [Reviews] [About] [Policies]       │
└──────────────────────────────────────────────────────┘
```

### Current State

| Signal | Status | Gap |
|---|---|---|
| Seller rating on product page | ❌ Not shown | P0 |
| Store age | ❌ Not shown | P1 |
| Total sales | ❌ Not shown | P1 |
| Response time/rate | ❌ Not tracked or shown | P1 |
| Tier badge on products | ❌ Not shown | P0 |
| Follower count | ❌ Not implemented | P2 |
| Seller store page | ⚠️ SellerDetailPage exists but incomplete | P1 |

---

## Layer 2: Product Trust

### What Customer Needs on Product Detail Page

| Signal | Purpose | Current State |
|---|---|---|
| Multiple photos (5+) | See product from all angles | ⚠️ Single image only |
| Video | Realistic view, harder to fake | ❌ Not supported |
| Verified purchase reviews | Real buyer opinions | ⚠️ Reviews exist but no "verified" badge |
| Review photos | See real product vs listing photo | ❌ Not supported |
| Q&A section | Ask before buying | ❌ Not built |
| "Authentic guarantee" badge | For branded products | ❌ Not built |
| Stock count | Scarcity signal + availability | ⚠️ Exists in data, not always shown |
| Sold count | Social proof | ✅ Shown |
| Delivery estimate | "Arrives in 2-3 days" | ❌ Not calculated |
| Return policy per product | Some sellers allow, some don't | ❌ Not per-product |

### Review Trust Hierarchy

Not all reviews are equal. Shopee/Taobao weight them:

```
Most trusted → Verified purchase + photo + 100+ chars
              → Verified purchase + photo
              → Verified purchase (text only)
              → Unverified review
Least trusted → No review at all
```

Requirements:
- Only buyers who purchased can leave reviews
- "Verified Purchase" badge on review
- Photo upload with review
- Sort by: Most helpful, Most recent, With photos
- Seller can reply to reviews (visible to all)

---

## Layer 1: Delivery Trust

### Order Anxiety Timeline

```
Minutes after order:  "Did it go through?"     → Confirmation email + SMS
Hours after order:    "Did seller see it?"      → "Seller confirmed" notification
1-2 days:            "Did they ship it?"        → "Shipped" + tracking number
In transit:          "Where is it now?"         → Real-time tracking updates
Delivery day:        "Coming today?"            → "Out for delivery" push notification
Delivered:           "Sign off + confirm"       → "Confirm receipt" button in app
```

**Every status change → notification on ALL channels (push + in-app + email)**

### Current State

| Requirement | Status | Gap |
|---|---|---|
| Order confirmation notification | ⚠️ Backend exists, delivery unclear | P1 |
| Seller accepted notification | ❌ Not sent | P0 |
| Shipped with tracking | ⚠️ Tracking entry exists, no live tracking | P1 |
| In-transit updates | ❌ Not integrated with carriers | P2 |
| Delivered notification | ❌ Not implemented | P1 |
| "Confirm receipt" button | ❌ Not built | P0 |

---

## Layer 0: Recourse Trust

### The Most Important Trust Signal

> "If something goes wrong, can I get my money back?"

This single question determines whether a customer will risk payment on an unknown platform.

**Must be answered BEFORE purchase:**
- Product page: "Protected by VNShop Buyer Guarantee"
- Checkout: "Full refund if item not received within 15 days"
- Footer: Link to refund/dispute policy

**Must be visible DURING dispute:**
- Clear step-by-step process
- SLA timelines shown to buyer
- Evidence upload is obvious
- Status updates at every stage

### Dispute Flow (Customer-Facing)

```
[I have a problem] button on order
        ↓
[Select issue type]
  - Not received
  - Wrong item
  - Damaged
  - Not as described
  - Counterfeit
        ↓
[Upload evidence]
  - Photos (required for damage/wrong item)
  - Description
        ↓
[Seller has 48h to respond]
  - Accept refund
  - Reject with counter-evidence
  - Propose partial refund
        ↓
[If seller rejects → Buyer can escalate]
        ↓
[Admin reviews within 72h]
        ↓
[Decision: Full refund / Partial / Reject]
        ↓
[Funds released accordingly]
```

### Current State

| Requirement | Status | Gap |
|---|---|---|
| Buyer guarantee text on product page | ❌ Missing | P0 |
| Refund policy page | ❌ Missing | P0 |
| "Open dispute" button on order | ❌ Not built for buyer | P0 |
| Evidence upload | ❌ Not built | P0 |
| Dispute SLA display | ❌ Not built | P0 |
| Admin dispute panel | ⚠️ DisputesQueue exists but limited | P1 |

---

## Trust Scorecard (Launch Readiness)

| Layer | Score | Blocker? |
|---|---|---|
| Platform Trust | 4/10 | 🟡 Fixable quickly |
| Payment Trust | 3/10 | 🔴 Escrow not visible |
| Seller Trust | 2/10 | 🔴 No seller signals on PDP |
| Product Trust | 4/10 | 🟡 Reviews exist, need photos + verified badge |
| Delivery Trust | 2/10 | 🔴 No status notifications |
| Recourse Trust | 1/10 | 🔴 No dispute flow for buyers |

**Overall Trust Score: 16/60 — NOT LAUNCH READY**

---

## KPIs

| KPI | Definition | Target |
|---|---|---|
| Bounce rate on PDP | % who leave product page without action | <60% |
| Cart abandonment rate | % who add to cart but don't checkout | <70% |
| First-order conversion | % of registered users who buy within 7 days | >15% |
| Dispute rate | % of orders that become disputes | <3% |
| Repeat purchase rate | % who buy again within 30 days | >20% |
| NPS (post-delivery) | Would you recommend? | >30 |
