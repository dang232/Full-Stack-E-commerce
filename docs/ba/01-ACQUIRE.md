# 1. Acquire

## The Core Question

**Why did the customer come? How did they find us?**

---

## Acquisition Channels

### Channel Map

| Channel | Entry Point | Customer Intent | Landing Page Required |
|---|---|---|---|
| Google Search (SEO) | "mua iphone 16 giá rẻ" | High intent buyer | Product/Category page |
| Google Ads (SEM) | "điện thoại chính hãng" | High intent, comparing | Category landing |
| Facebook Ad | Scroll → see product → click | Impulse / curiosity | Product page with social proof |
| TikTok Video | Creator reviews product → link in bio | Social proof driven | Product page |
| Referral Link | Friend shares product/store URL | Trust pre-built by friend | Product page |
| Email Campaign | Re-engagement or promotion | Returning user | Promo landing / Flash Sale |
| Affiliate Link | Blog/review site links to product | Research-complete buyer | Product page |
| Direct URL | Types vnshop.vn or has bookmark | Returning user | Homepage |
| App Store | Searches "mua sắm online" | Exploring alternatives | App onboarding |

### What Each Channel Needs From The Platform

| Channel | SEO Requirements | Page Speed | Trust Signals on Landing | CTA |
|---|---|---|---|---|
| Google SEO | Schema markup, meta tags, canonical URLs, sitemap | <3s LCP | Price, rating, stock status | Add to cart |
| Google Ads | UTM tracking, conversion pixel, ROAS measurement | <2s LCP | Price comparison, discount badge | Buy now |
| Facebook | OG tags, share image, pixel tracking | <3s LCP | Social proof (X sold, Y reviews) | View product |
| TikTok | TikTok pixel, video-friendly preview | <3s LCP | Seller video reviews | Shop now |
| Referral | Unique referral codes, credit tracking | — | "Your friend uses VNShop" banner | Register |
| Email | UTM params, unsubscribe, CAN-SPAM | — | Personalized recommendations | Shop deals |

---

## First Impression (0-5 seconds)

### What Customer Sees on Homepage

**Current VNShop homepage has:**
- ✅ Flash sale section with countdown
- ✅ Product grid with prices
- ✅ Category navigation
- ✅ Search bar
- ✅ Language switcher

**What's MISSING (Shopee/Taobao standard):**
- ❌ Hero banner with value proposition
- ❌ Trust bar ("100% Genuine", "Free Returns", "Secure Payment")
- ❌ Top stores / Mall section
- ❌ Recently viewed (returning users)
- ❌ Personalized recommendations
- ❌ "Why VNShop" section for new visitors
- ❌ Download app CTA
- ❌ Social proof counter ("10,000+ sellers", "1M+ products")
- ❌ Trending searches

### Homepage Customer Goal by Visit Type

| Visit Type | Customer Goal | What Homepage Must Show |
|---|---|---|
| First visit (cold) | "Is this legit?" | Trust signals, best sellers, recognizable brands |
| First visit (from ad) | "Where's the thing I clicked on?" | Should NOT land on homepage — goes to product/category |
| Returning (browsing) | "What's new?" | Recently viewed, new arrivals, flash sale |
| Returning (intent) | "Let me search for X" | Search bar prominent, history, suggestions |

---

## SEO Architecture

### Pages That Must Rank

| Page Type | Example URL | Target Keywords |
|---|---|---|
| Category | /category/dien-thoai | "mua điện thoại online" |
| Product | /product/iphone-16-pro-max | "iphone 16 pro max giá" |
| Seller Store | /store/apple-authorized-vn | "apple store chính hãng" |
| Collection | /collection/flash-sale-today | "flash sale hôm nay" |
| Blog/Guide | /blog/so-sanh-iphone-16-vs-15 | "so sánh iphone 16 vs 15" |

### Technical SEO Requirements

| Requirement | Current State | Gap |
|---|---|---|
| SSR or SSG for product pages | SPA (client-rendered) | 🔴 Google can't index properly |
| Meta title/description per page | Not implemented | 🔴 Critical for search ranking |
| Open Graph tags | Not implemented | 🟡 Social sharing broken |
| Structured data (Product schema) | Not implemented | 🔴 No rich snippets in Google |
| Sitemap.xml | Not implemented | 🔴 Google can't discover pages |
| robots.txt | Not implemented | 🟡 Should exist |
| Canonical URLs | Not implemented | 🟡 Prevents duplicate content |
| Page load <3s | Likely OK (Vite + static) | ✅ |
| Mobile responsive | Yes | ✅ |

---

## Acquisition Funnel Metrics

### Events to Track

| Event | When | Properties |
|---|---|---|
| `page_view` | Any page load | url, referrer, utm_source, utm_medium, utm_campaign |
| `first_visit` | New visitor (no cookie) | landing_page, referrer, device |
| `search_performed` | Submits search | query, results_count, position_clicked |
| `product_viewed` | Opens PDP | product_id, category, source (search/category/recommendation) |
| `signup_started` | Opens register form | referrer, page_before |
| `signup_completed` | Successful registration | method (email/oauth), referral_code |

### KPIs

| KPI | Definition | Target |
|---|---|---|
| Traffic by channel | Sessions per source | Diversified (no >60% from one channel) |
| Bounce rate (homepage) | Leave without interaction | <50% |
| Landing → PDP rate | % who view a product | >40% |
| New visitor → Register | % who create account | >5% |
| Cost per acquisition | Ad spend / new registered users | <50,000 VND |
| SEO impressions | Google Search Console | Growing month-over-month |

---

## Current VNShop Status vs Acquire Requirements

| Requirement | Current State | Priority |
|---|---|---|
| Homepage hero + trust signals | Missing | P1 |
| SEO (SSR/meta/schema) | Not implemented (SPA only) | P0 for organic |
| OG tags for social sharing | Missing | P1 |
| UTM tracking | No analytics infra | P1 |
| Referral system | Not built | P2 |
| Email capture / newsletter | Not built | P2 |
| Trending searches on homepage | Not built | P1 |
| Recently viewed | Not built | P1 |
| "Why VNShop" trust section | Missing | P1 |
| App download CTA | No mobile app | P3 (future) |

---

## Acceptance Criteria

### AC-1: Homepage First Impression

```
Given a new visitor lands on the homepage for the first time
When the page loads within 3 seconds
Then they see:
  - A trust bar with "Genuine Products | Secure Payment | Easy Returns"
  - A hero banner with current promotion or value prop
  - Top categories with icons
  - Flash sale section (if active)
  - Best-selling products with ratings and sold count
  - Footer with contact info and policies
```

### AC-2: SEO Product Page

```
Given a product page URL is crawled by Google
When the page is rendered
Then:
  - Title tag contains product name + price + brand
  - Meta description contains product summary
  - Structured data (Product schema) is valid
  - OG image is the primary product photo
  - Page loads server-side (SSR) within 3s
  - Canonical URL is set
```

### AC-3: Referral Link

```
Given buyer A shares a product link with referral code
When buyer B clicks the link and registers
Then:
  - Buyer B's account is tagged with referrer = Buyer A
  - Buyer A receives credit (amount configurable by admin)
  - Both see confirmation toast
```

### AC-4: UTM Attribution

```
Given a user clicks a Facebook ad with utm_source=facebook&utm_medium=cpc
When they land on VNShop and eventually purchase
Then:
  - The conversion is attributed to facebook/cpc in analytics
  - ROAS can be calculated per campaign
```
