# 3. Discovery

## The Core Question

**"Can I find what I need?"**

Discovery is NOT just search. It's every mechanism through which a customer encounters products.

---

## Discovery Channels

```
┌─────────────────────────────────────────────────────────┐
│                    DISCOVERY                             │
├─────────────────────────────────────────────────────────┤
│                                                         │
│  ACTIVE (customer initiates)                            │
│  ├── Search (typed query)                               │
│  ├── Category browse (drill down)                       │
│  ├── Filter + sort (refine)                             │
│  └── Store browse (visit seller page)                   │
│                                                         │
│  PASSIVE (platform surfaces)                            │
│  ├── Homepage recommendations                           │
│  ├── "You may also like" (PDP)                          │
│  ├── "Frequently bought together" (PDP)                 │
│  ├── "Recently viewed" (persistent)                     │
│  ├── Flash sale (time-limited)                          │
│  ├── Trending / bestsellers                             │
│  ├── Collections (curated by admin)                     │
│  ├── New arrivals                                       │
│  └── Price drop alerts (wishlist items)                 │
│                                                         │
│  SOCIAL (others surface for you)                        │
│  ├── Shared product link                                │
│  ├── Seller promotion                                   │
│  ├── Review with photo (browsing reviews)               │
│  └── Chat recommendation (seller suggests)              │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## Search (Active Discovery)

### Search UX Flow

```
[Focus search bar]
      ↓
[Show: Recent searches + Trending searches]
      ↓
[Type "iph"]
      ↓
[Show suggestions: "iphone", "iphone 16", "iphone case", "iphone 15 pro"]
      ↓
[Select or press Enter]
      ↓
[Results page with filters active]
      ↓
[Refine: Category, Price, Rating, Shipping, Seller type]
      ↓
[Sort: Popular, Newest, Price low→high, Price high→low, Rating]
      ↓
[Click product → PDP]
```

### Search Requirements

| Feature | Shopee | Taobao | Required for VNShop |
|---|---|---|---|
| Autocomplete suggestions | ✅ As-you-type | ✅ | ✅ P0 |
| Recent search history | ✅ Persisted | ✅ | ✅ P0 |
| Trending searches | ✅ Updated hourly | ✅ | ✅ P1 |
| Spelling correction | ✅ "Did you mean..." | ✅ | ✅ P1 |
| Vietnamese diacritics tolerance | ✅ "dien thoai" = "điện thoại" | — | ✅ P0 |
| Image search | ✅ Camera icon | ✅ | ❌ P3 (future) |
| Voice search | ✅ Mic icon | ❌ | ❌ P3 (future) |
| Zero-result handling | ✅ Shows related products | ✅ Similar items | ✅ P0 |
| Faceted results | ✅ Category, brand, price | ✅ | ✅ P0 |
| Result count | ✅ "1,234 results" | ✅ | ✅ P0 |

### Current State

| Feature | Status | Gap |
|---|---|---|
| Basic search | ✅ Works (when ES healthy) | — |
| Autocomplete suggestions | ❌ Not built | P0 |
| Recent search history | ❌ Not built | P0 |
| Trending searches | ❌ Not built | P1 |
| Diacritics tolerance | ❓ Depends on ES analyzer config | P0 — verify |
| Zero-result fallback | ❌ Shows empty state only | P0 |
| Faceted filters | ✅ Category, price, rating, brand | OK |
| "Did you mean" | ❌ Not built | P1 |

---

## Category Browse (Active Discovery)

### Category Architecture

```
Level 0: All Categories (mega menu)
Level 1: Electronics, Fashion, Home, Beauty, Sports, ...
Level 2: Electronics → Phones, Laptops, Tablets, Accessories
Level 3: Phones → iPhone, Samsung, Xiaomi, OPPO
```

### Category UX

| Component | Where | Behavior |
|---|---|---|
| Mega menu | Header hover/click | Shows L1 + L2 grid with icons |
| Category cards | Homepage | Visual grid with icons/images |
| Breadcrumbs | Category/Product page | Home > Electronics > Phones > iPhone |
| Sidebar filters | Category page | Drill into subcategories |
| Category count | Mega menu | "Phones (1,234)" |

### Current State

| Feature | Status | Gap |
|---|---|---|
| Category list in nav | ✅ Exists | OK |
| Mega menu with subcategories | ❌ Flat list only | P1 |
| Category images/icons | ❌ Text only | P1 |
| Breadcrumbs | ❌ Not implemented | P1 |
| Product count per category | ❌ Not shown | P2 |
| Category landing page (hero + description) | ❌ Just a filtered product grid | P2 |

---

## Recommendations (Passive Discovery)

### Recommendation Types

| Type | Where | Algorithm | Business Value |
|---|---|---|---|
| "You may also like" | PDP bottom | Same category, similar price ±30% | Cross-sell |
| "Frequently bought together" | PDP | Co-purchase history | AOV increase |
| "Recently viewed" | Homepage, sidebar | Client-side history | Re-engagement |
| "Based on your browsing" | Homepage | Category affinity | Personalization |
| "Top sellers this week" | Homepage | Sales volume (7d) | Social proof |
| "New arrivals" | Homepage, category page | Listed date descending | Freshness signal |
| "Trending now" | Homepage | Velocity (views/hour spike) | FOMO |
| "Customers who bought X" | PDP | Collaborative filtering | Discovery |

### Current State

| Type | Status | Gap |
|---|---|---|
| "You may also like" | ✅ recommendations-service exists | Verify quality |
| "Frequently bought together" | ✅ Co-purchase aggregator built | Verify quality |
| "Recently viewed" | ❌ Not built | P1 |
| "Based on your browsing" | ❌ No personalization engine | P2 |
| "Top sellers" | ❌ Not surfaced on homepage | P1 |
| "New arrivals" | ❌ Not a distinct section | P1 |
| "Trending now" | ❌ Not built | P2 |

---

## Flash Sale (Passive Discovery)

### Flash Sale UX Requirements

| Requirement | Shopee Reference | VNShop Status |
|---|---|---|
| Countdown timer | ✅ Prominent | ✅ Exists |
| Progress bar "X% sold" | ✅ Per product | ✅ Exists |
| Limited stock indicator | ✅ "Only 3 left!" | ⚠️ Partial |
| Scheduled future sales | ✅ "Tomorrow 12:00" | ❌ Not shown |
| Push notification before start | ✅ "Flash sale starts in 10 min!" | ❌ Not sent |
| Instant sold-out handling | ✅ Greyed out, "Sold out" badge | ⚠️ Verify |

---

## Store Browse (Active Discovery)

### Store Discovery

Customers should be able to:
1. See "Visit store" button on every product
2. Browse all products from one seller
3. Follow a store for updates
4. See store categories (seller organizes their products)
5. See store promotions (seller-specific coupons)

### Current State

| Feature | Status | Gap |
|---|---|---|
| "Visit store" from PDP | ⚠️ SellerDetailPage exists | Verify linkage |
| Store product listing | ⚠️ Shows all products (not seller-filtered) | P1 — already flagged |
| Follow store | ❌ Not built | P2 |
| Store categories | ❌ Not built | P2 |
| Store coupons | ❌ Not built | P2 |

---

## Discovery Failure Modes

### When Discovery Fails (Current Bugs)

| Failure | User Impact | Fix |
|---|---|---|
| Search returns 0 for "iphone" | User thinks no products exist | P0 — ES health + fallback |
| No suggestions while typing | User must know exact query | P0 — add autocomplete |
| No "recently viewed" | Returning user starts from scratch | P1 |
| No breadcrumbs | User gets lost in category hierarchy | P1 |
| No "similar products" on zero-result | Dead end after failed search | P0 |
| Flash sale not visible on first fold | Misses urgency driver | P1 |

---

## Acceptance Criteria

### AC-1: Search Autocomplete

```
Given buyer focuses on search bar
When they type "iph"
Then within 300ms, a dropdown appears showing:
  - Up to 8 suggestions ranked by popularity
  - Matching text highlighted in bold
  - Each suggestion is clickable and navigates to search results
  - "iphone", "iphone 16 pro max", "iphone case" appear as suggestions
```

### AC-2: Recent Searches

```
Given buyer has previously searched for "laptop"
When they focus the search bar (empty query)
Then:
  - "Recent searches" section appears
  - Shows last 10 searches
  - Each is clickable (re-executes search)
  - Each has an X button to remove from history
  - History persists across sessions (localStorage + server sync when logged in)
```

### AC-3: Zero Results Fallback

```
Given buyer searches for "xyznonexistent123"
When zero results are returned
Then:
  - Message: "No results for 'xyznonexistent123'"
  - Below: "Popular products in similar categories" with 8-12 products
  - Below: "Try these searches:" with trending queries
  - Search bar remains focused for easy retry
  - No "Showing 1-0 of 0" nonsense
```

### AC-4: Diacritics Tolerance

```
Given buyer searches for "dien thoai" (without Vietnamese diacritics)
When results are returned
Then:
  - Results match "điện thoại" products
  - No difference in result quality vs. typing with diacritics
  - Works for all Vietnamese characters (ă, â, ê, ô, ơ, ư, đ)
```

### AC-5: Recently Viewed

```
Given buyer has viewed products A, B, C in this session
When they return to homepage
Then:
  - "Recently viewed" section shows A, B, C in reverse chronological order
  - Maximum 20 items shown
  - Persists across page refreshes (localStorage)
  - Persists across sessions for logged-in users (server-side)
  - Each card shows current price (may have changed since viewed)
```

### AC-6: Category Breadcrumbs

```
Given buyer navigates to a product in Electronics > Phones > iPhone
When the product detail page loads
Then:
  - Breadcrumb shows: Home > Electronics > Phones > iPhone > [Product Name]
  - Each segment is clickable (navigates to that category page)
  - On mobile: collapsed to "... > iPhone > Product Name"
```

---

## KPIs

| KPI | Definition | Target |
|---|---|---|
| Search-to-click rate | % of searches that result in a product click | >50% |
| Zero-result rate | % of searches returning 0 results | <5% |
| Search → Purchase | % of search sessions ending in purchase | >3% |
| Category CTR | % of category page views → product click | >30% |
| Recommendation CTR | % of reco impressions clicked | >5% |
| Discovery diversity | % of products that receive >0 views per week | >30% |
| Average discovery path length | Pages visited before purchase | 3-5 (not too long) |
