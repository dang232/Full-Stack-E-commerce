# 0. Market Fit

## The Core Question

Why would anyone use VNShop instead of:
- Shopee Vietnam (dominant, 70%+ market share)
- Lazada (Alibaba-backed, cross-border)
- Tiki (premium domestic)
- TikTok Shop (social commerce, explosive growth)
- Taobao (direct China import)
- eBay (international resale)
- Amazon (global, not VN-focused yet)
- **Facebook Marketplace** (zero fees, peer-to-peer)
- **Zalo groups** (community commerce, trust via social graph)
- **Direct Instagram/TikTok selling** (no platform cut)
- **Seller's own website** (Shopify/Haravan, full control)

If the answer is unclear: **STOP BUILDING FEATURES.**

This is not a recommendation. This is a **hard gate**. No launch without a validated answer.

---

## The Real Competitors

Most marketplace founders frame competition as:

> "Our competitor is Shopee."

Wrong. The customer doesn't think in marketplace categories. They think:

> "I want to buy X. Where do I go?"

Their options include everything from Shopee to asking a friend in a Zalo group. VNShop competes against ALL of these, including "do nothing."

---

## Positioning Analysis

### What VNShop Cannot Compete On

| Competitor | Their Moat | Why VNShop Can't Win Here |
|---|---|---|
| Shopee | Network effects, 500k+ sellers, ShopeePay, logistics fleet | You cannot out-Shopee Shopee. |
| Lazada | Alibaba logistics infra, cross-border from China | You cannot match their supply chain. |
| TikTok Shop | 50M+ Vietnamese TikTok users, content-driven discovery | You have no social graph. |
| Tiki | TikiNOW same-day, own warehouse network | You have no warehouses. |
| Facebook MP | Zero fees, existing social trust | You cannot be free AND profitable. |
| Zalo groups | Pre-existing community trust | You cannot replicate social relationships. |

### Where VNShop CAN Position

| Position | Description | Target Customer | Difficulty |
|---|---|---|---|
| **A. Niche vertical** | Focus on 1-2 categories where big platforms are weak (handmade, B2B parts, agricultural, vintage) | Buyers frustrated by Shopee noise | Medium |
| **B. Seller-profit platform** | Help sellers make MORE PROFIT (not just lower fees) — better tools, analytics, marketing, faster payouts | Small-medium Vietnamese sellers | Hard |
| **C. Cross-border export** | Vietnamese products → international buyers (eBay model but from VN) | Diaspora + international buyers wanting VN goods | Medium |
| **D. White-label marketplace infrastructure** | The platform itself is the product — license to companies | Enterprises needing marketplace infra | Different business entirely |
| **E. Trust-first high-value** | Escrow + evidence-based disputes for categories where trust matters most | High-value purchase buyers (electronics, luxury, collectibles) | Medium |

### Option D: The Uncomfortable Question

Looking at VNShop's architecture:
- Keycloak (identity)
- Kafka (event streaming)
- 16 microservices
- Escrow + finance
- Multi-carrier shipping
- Dispute resolution
- Monitoring + observability

**You may have accidentally built a Marketplace Infrastructure Platform instead of a Marketplace Business.**

| Path | Revenue Model | Competition | Difficulty |
|---|---|---|---|
| Run the marketplace (A/B/C/E) | Commission on GMV | Shopee, Lazada, Tiki | Very Hard |
| Sell the platform (D) | License/SaaS fees | Sharetribe, Arcadier, CS-Cart | Hard (but different game) |

**Both paths are valid. But they require fundamentally different go-to-market strategies.**

Must decide before Sprint 1.

---

## Market Fit Hypothesis (Revised)

### ❌ Wrong Hypothesis (Fee-First)

> "Sellers choose VNShop because lower commission."

Why this fails:

```
Seller at Shopee: 100 orders/month × 200,000₫ avg = 20,000,000₫ revenue
Shopee fee (6.5%): -1,300,000₫
Net: 18,700,000₫

Seller at VNShop: 10 orders/month × 200,000₫ avg = 2,000,000₫ revenue
VNShop fee (3%): -60,000₫
Net: 1,940,000₫
```

**Seller stays at Shopee. Every time.** Lower fees don't matter without volume.

### ✅ Correct Hypothesis (Profit-First)

> "Sellers choose VNShop because we help them make more total profit."

Profit = Revenue - Costs - Fees - Time

Ways to increase seller profit beyond commission cuts:
- **More sales** (better discovery, SEO driving buyers to their products)
- **Lower costs** (better tools reduce time spent per order)
- **Faster cash** (3-day payouts vs 14-day = better cash flow)
- **Less risk** (transparent disputes, no surprise fee changes)
- **Own customer** (seller builds direct relationship, not Shopee's customer)

### Validation Requirements (Revised)

| Question | How to Validate | When | Blocker? |
|---|---|---|---|
| What frustrates sellers about Shopee? | 20 seller interviews (not 10) | Week 1 | **HARD GATE** |
| Is "more profit" or "lower fees" the real pain? | Interview analysis | Week 1 | **HARD GATE** |
| Will sellers invest time in a new platform? | Landing page + waitlist signup rate | Week 2 | **HARD GATE** |
| Which category has available sellers? | Outreach response rate | Week 3 | Yes |
| Will buyers trust a new name? | Trust signal A/B test | Week 4 | Yes |
| Do we compete or sell the platform? | Founder decision | Week 1 | **HARD GATE** |

---

## Liquidity (Revised)

### ❌ Wrong Metric: Product Count

```
5,000 random products across 20 categories = garbage

500 highly relevant products in 1 focused category = marketplace
```

Product count is a vanity metric. Buyers don't count products. They ask:

> "Can I find what I'm looking for?"

### ✅ Correct Metric: Search Satisfaction

| Metric | Definition | Target |
|---|---|---|
| **Search satisfaction rate** | % of common searches returning ≥10 relevant results | >90% |
| **Category depth** | Products per active subcategory | >30 |
| **Seller diversity** | Unique sellers per subcategory | >3 |
| **Fresh listings** | % of products updated within 30 days | >50% |
| **Price competitiveness** | % of products priced within 10% of Shopee | >70% |

### How to Measure

1. Define the "common searches" for chosen category (e.g., top 100 queries in that category on Google Trends Vietnam)
2. For each query, count relevant results on VNShop
3. If <10 results for >10% of queries: **liquidity is insufficient**

---

## Category Strategy

### Category Scoring Framework

| Factor | Weight | Why |
|---|---|---|
| Competition intensity | 25% | Avoid categories where Shopee/Tiki are unbeatable |
| Margin potential | 20% | Higher margin = commission model works |
| Repeat purchase rate | 20% | Repeat buyers = organic retention |
| Logistics complexity | 15% | Simple shipping = fewer disputes |
| Seller availability | 10% | Can you recruit sellers in this category? |
| Trust requirement | 10% | High-trust = VNShop's escrow advantage matters |

### Category Scorecard

| Category | Competition | Margin | Repeat | Logistics | Sellers | Trust | TOTAL |
|---|---|---|---|---|---|---|---|
| Fashion | 9 (brutal) | 6 | 7 | 5 (returns) | 8 | 3 | **5.8** |
| Electronics | 8 (brutal) | 4 | 3 | 7 | 5 | 9 | **5.7** |
| Beauty/Skincare | 7 | 8 | 9 | 8 | 7 | 7 | **7.5** |
| Home/Kitchen | 5 | 7 | 6 | 5 | 6 | 4 | **5.6** |
| Agricultural/Specialty food | 3 | 8 | 8 | 4 | 6 | 7 | **6.2** |
| Handmade/Artisan | 2 | 9 | 4 | 7 | 5 | 6 | **5.9** |
| Collectibles/Vintage | 2 | 9 | 3 | 8 | 4 | 9 | **6.0** |
| B2B parts/supplies | 2 | 7 | 9 | 6 | 4 | 5 | **5.7** |

**Score = Σ(factor × weight), inverted for competition (10 - score × weight)**

Top candidates:
1. **Beauty/Skincare** — High margin, repeat purchase, low logistics complexity
2. **Agricultural/Specialty food** — Low competition, high repeat, Vietnamese authenticity angle
3. **Collectibles/Vintage** — Low competition, high trust need (escrow advantage), high margin

### Decision Required

Choose ONE category to start. Not two. Not three. One.

---

## Network Effects

### Why This Matters

A marketplace without network effects is just an ecommerce website with extra steps.

Network effects = the platform gets stronger as it grows, creating a moat competitors can't replicate overnight.

### VNShop's Potential Network Effects

```
SUPPLY-SIDE (more sellers → better platform)
More sellers → More products → Better search results → More buyers → More seller revenue → More sellers

DEMAND-SIDE (more buyers → better platform)  
More buyers → More reviews → More trust → Higher conversion → More sellers join → More buyers

DATA EFFECTS (more activity → smarter platform)
More searches → Better autocomplete → Better discovery → More purchases → More data

CROSS-SIDE (each side pulls the other)
More buyers → Sellers earn more → Sellers invest more (better photos, faster shipping) → Buyers happier → More buyers
```

### What Must Be True for Network Effects to Activate

| Effect | Activation Threshold | Current State |
|---|---|---|
| Search quality improves | >1,000 searches/day feeding suggestions | ❌ No traffic |
| Reviews drive trust | >5 reviews per popular product | ❌ No real reviews |
| Seller competition drives quality | >3 sellers per subcategory | ❌ No real sellers |
| Recommendation engine improves | >10,000 purchase events | ❌ No real orders |

**None of these activate until real users are on the platform.** This reinforces: validate with real sellers + buyers ASAP.

---

## Risk Register

| # | Risk | Severity | Likelihood | Mitigation |
|---|---|---|---|---|
| R1 | No seller adoption | Critical | High | Solve seller's #1 problem (not just fees), offer white-glove onboarding |
| R2 | No buyer trust | Critical | High | Escrow + policies + trust signals before launch |
| R3 | No category focus | Critical | High | Choose ONE category, say no to everything else |
| R4 | Low liquidity | Critical | High | Seed products, recruit sellers week 1-5 |
| R5 | Commission not competitive | High | Medium | Rethink: compete on profit, not fees |
| R6 | Dispute costs eat margin | High | Medium | SLA automation, self-service resolution |
| R7 | Payout delays lose sellers | High | Medium | Build 3-day payout pipeline before launch |
| R8 | Over-engineering, under-validating | High | **Happening now** | Stop features, start validation |
| R9 | Platform used by 0 people | Critical | High | Define success metrics with timeline, kill project if not met |

---

## Validation Plan (Execution Timeline)

| Week | Action | Success Metric | Decision |
|---|---|---|---|
| 1 | 20 seller interviews (Shopee/FB sellers in chosen category) | Identify top 3 pain points | Proceed or pivot category |
| 2 | Landing page + seller waitlist + value prop test | >50 waitlist signups from outreach | Proceed or pivot positioning |
| 3 | Measure signup intent, identify 10 committed sellers | 10 sellers say "yes I'll list products" | Proceed or kill |
| 4 | Recruit first 10 sellers, help them upload 500 products | 500 products live, searchable | Proceed to buyer test |
| 5 | Invite 100 target buyers (friends, community, small ads) | >5% conversion rate on first visit | Launch or iterate |
| 6 | Measure: search satisfaction, conversion, repeat | Meets liquidity metrics | Soft launch or extend |

**If Week 3 fails (cannot recruit 10 sellers): STOP. Re-evaluate positioning.**

---

## Go/No-Go Decision (HARD GATE)

Before proceeding to ANY development sprint:

- [ ] Position defined (A/B/C/D/E — ONE chosen)
- [ ] Category chosen (ONE category, scored)
- [ ] 20 seller interviews completed
- [ ] Top 3 seller pain points identified
- [ ] Seller waitlist has >50 signups
- [ ] 10 sellers committed to listing
- [ ] Commission model validated (profit-based, not fee-based)
- [ ] Unique value proposition is one sentence
- [ ] Network effect loop identified
- [ ] Kill criteria defined ("if X doesn't happen by Y, we stop")

**These are REQUIRED, not recommended. Development without validation is waste.**

---

## Current VNShop Status vs Market Fit

| Requirement | Current State | Gap |
|---|---|---|
| Clear positioning | Not defined | 🔴 Critical |
| Target segment | Not defined | 🔴 Critical |
| Unique value prop | Not defined | 🔴 Critical |
| Category chosen | All categories, none focused | 🔴 Critical |
| Seller interviews | 0 conducted | 🔴 Critical |
| Commission model | STANDARD 10% (higher than Shopee) | 🔴 Needs complete rethink |
| Payout speed | Not implemented | 🟡 Infra exists |
| Trust signals | Partial (ratings exist, incomplete) | 🟡 Gaps |
| Liquidity plan | No seeding strategy | 🔴 Critical |
| Network effects | Not designed | 🔴 Critical |
| Kill criteria | Not defined | 🔴 Critical |
| Platform vs marketplace decision | Not made | 🔴 Critical |
