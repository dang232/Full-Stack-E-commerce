# Deliverable 4: Screen Audit Matrix

## Every Page — Purpose, Goals, Status

### Legend

| Symbol | Meaning |
|---|---|
| ✅ | Working as expected |
| ⚠️ | Partially working, gaps exist |
| ❌ | Not built or critically broken |
| 🔴 P0 | Ship blocker |
| 🟠 P1 | Must fix before public launch |
| 🟡 P2 | Improve post-launch |

---

## Public Pages (Guest Access)

| Page | Customer Goal | Business Goal | Status | Priority Fixes |
|---|---|---|---|---|
| Homepage | "What is this? Is it for me?" | First impression, retain visitor | ⚠️ | 🟠 Missing: hero banner, trust bar, social proof counters, recently viewed, top stores |
| Search Results | "Find what I need" | Surface relevant products | ⚠️ | 🔴 No autocomplete, zero-result fallback weak, no suggestions |
| Category Page | "Browse this type of product" | Funnel to PDP | ⚠️ | 🟠 No breadcrumbs, no mega menu, no category images |
| Product Detail (PDP) | "Should I buy this?" | Convert to cart | ⚠️ | 🔴 No seller trust signals, no escrow badge, no delivery estimate, single image |
| Flash Sale | "Get a deal before it ends" | Urgency-driven conversion | ✅ | 🟡 No push notification before sale starts |
| Seller Store Page | "Is this seller legit?" | Trust building | ⚠️ | 🟠 Missing: seller metrics, store age, response rate, follower count |
| Login | "Access my account" | Authenticate | ✅ | 🟡 No OAuth (Google/Facebook) |
| Register | "Create account" | Grow user base | ⚠️ | 🟠 Single-error display (fixed this session), no OAuth |
| Password Reset | "I forgot my password" | Recover access | ✅ | 🟡 Purple CTA inconsistent with brand |
| 404 Page | "I'm lost" | Recover navigation | ✅ | — |

---

## Buyer Pages (Authenticated)

| Page | Customer Goal | Business Goal | Status | Priority Fixes |
|---|---|---|---|---|
| Cart | "Review what I'm buying" | Move to checkout | ✅ | 🟡 No partial checkout, no price-change alert |
| Checkout - Address | "Where to deliver" | Capture address | ✅ | 🟡 No address validation (province/district dropdown) |
| Checkout - Shipping | "How fast + how much" | Select carrier | ⚠️ | 🟠 Carrier options unclear, no delivery estimate |
| Checkout - Payment | "How to pay" | Capture payment | ⚠️ | 🟠 Currency-based routing unverified, MoMo untested |
| Checkout - Review | "Confirm everything" | Final conversion | ✅ | — |
| Checkout - Success | "Did it work?" | Confirmation + next action | ✅ | 🟡 No delivery estimate shown |
| Orders List | "Where are my orders?" | Order visibility | ✅ | 🟠 No status timeline, no confirm button |
| Order Detail | "Track this specific order" | Reduce support tickets | ⚠️ | 🔴 No confirm receipt, no dispute button, no escrow visibility, no timeline |
| Wishlist | "Things I want to buy later" | Re-engagement list | ✅ | 🟡 No price-drop notification |
| Profile | "Manage my account" | Self-service | ✅ | — |
| Addresses | "Manage delivery addresses" | Faster checkout | ✅ | — |
| Notifications | "What happened?" | Engagement | ⚠️ | 🟠 Limited notification types, no push |
| Messages / Chat | "Talk to seller" | Pre-sale questions → conversion | ⚠️ | 🟠 Verify buyer→seller chat works end-to-end |

---

## Seller Pages (Seller Role)

| Page | Customer Goal | Business Goal | Status | Priority Fixes |
|---|---|---|---|---|
| Seller Dashboard | "How's my shop doing?" | At-a-glance health | ⚠️ | 🟠 Error/empty mutual exclusion (fixed this session), no real analytics |
| Seller Orders | "What do I need to fulfill?" | Order management | ⚠️ | 🔴 No SLA countdown, no auto-reject, error dead-end (fixed) |
| Seller Products | "Manage my listings" | Inventory management | ⚠️ | 🟠 Dev banner (fixed), no variant support, no bulk upload |
| Seller Reviews | "What do customers think?" | Reputation management | ⚠️ | 🟡 No reply-to-review, no analytics |
| Seller Wallet | "Where's my money?" | Financial transparency | ⚠️ | 🟠 No payout schedule shown, no escrow-in-transit view |
| Seller Settings | "Manage my store" | Store customization | ⚠️ | 🟠 No store banner upload, no store description editor |
| Seller Onboarding | "How do I start selling?" | Recruit sellers | ❌ | 🔴 No guided setup flow, no document upload, no approval status page |

---

## Admin Pages (Admin Role)

| Page | Customer Goal | Business Goal | Status | Priority Fixes |
|---|---|---|---|---|
| Admin Dashboard | "Platform health at a glance" | Operational oversight | ⚠️ | 🟠 403 error (fixed), no real-time metrics |
| Sellers Approval | "Approve new sellers" | Supply growth | ✅ | — |
| Reviews Moderation | "Remove bad reviews" | Content quality | ✅ | — |
| Coupons Management | "Create promotions" | GMV growth | ✅ | — |
| Disputes Queue | "Resolve buyer-seller conflicts" | Trust maintenance | ⚠️ | 🔴 No evidence view, no SLA timer, no resolution workflow |
| Payouts Queue | "Pay sellers" | Seller retention | ⚠️ | 🟠 Manual process, no schedule automation |
| User Management | "Manage user accounts" | Platform governance | ⚠️ | 🟠 Missing i18n keys (fixed this session) |
| Order Management | "View all orders" | Operational visibility | ⚠️ | 🟡 Basic table, no bulk actions |
| System Health | "Is everything running?" | Technical ops | ⚠️ | 🟡 monitoring-service crashes, limited metrics |

---

## Pages That DON'T EXIST But Must

| Page | Why | Priority |
|---|---|---|
| Refund/Return Policy | Legal requirement, trust signal | 🔴 P0 |
| Terms of Service | Legal requirement | 🔴 P0 |
| Privacy Policy | Legal requirement (GDPR/PDPA) | 🔴 P0 |
| Buyer Dispute Flow | "I have a problem" → evidence → resolution | 🔴 P0 |
| Seller Onboarding Wizard | Guided first-time setup | 🔴 P0 |
| Seller Store Public Page | Buyer visits seller's storefront | 🟠 P1 |
| Order Tracking Page | Deep link from notification → see status | 🟠 P1 |
| Help Center / FAQ | Self-service support | 🟠 P1 |
| About Us | Platform trust | 🟠 P1 |
| Contact Us | Support channel | 🟠 P1 |
| Referral Program | Growth mechanism | 🟡 P2 |
| Loyalty / Points | Retention mechanism | 🟡 P2 |

---

## Summary Counts

| Category | Total Pages | ✅ OK | ⚠️ Gaps | ❌ Missing | 🔴 P0 Fixes |
|---|---|---|---|---|---|
| Public | 10 | 4 | 5 | 1 | 3 |
| Buyer | 13 | 6 | 7 | 0 | 2 |
| Seller | 7 | 0 | 6 | 1 | 2 |
| Admin | 9 | 2 | 7 | 0 | 1 |
| Missing (must build) | 12 | — | — | 12 | 4 |
| **TOTAL** | **51** | **12** | **25** | **14** | **12** |

**12 ship-blocking issues across the platform.**
