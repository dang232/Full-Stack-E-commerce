# Deliverable 6: Launch Readiness Scorecard

## Scoring Method

Each domain scored 1-10:
- **1-3**: Not built or critically broken. Cannot launch.
- **4-5**: Partially built, major gaps. Risky to launch.
- **6-7**: Functional but incomplete. Can soft-launch with caveats.
- **8-9**: Ready for public launch. Minor polish remaining.
- **10**: Production-grade, competitive with Shopee/Tiki.

---

## Phase A: Go-Live Gate (Must Pass)

| # | Domain | Score | Blocker? | Key Gaps |
|---|---|---|---|---|
| 0 | **Market Fit** | 2/10 | 🔴 YES | No positioning, no target segment, no liquidity plan, commission higher than competitors |
| 1 | **Acquire** | 3/10 | 🔴 YES | SPA-only (no SEO), no OG tags, no UTM tracking, no referral system, no landing pages |
| 2 | **Trust** | 2/10 | 🔴 YES | No escrow visibility, no seller metrics on PDP, no return policy, no dispute flow for buyers |
| 3 | **Discovery** | 4/10 | 🔴 YES | Search works but no autocomplete, no suggestions, no recently viewed, no breadcrumbs |
| 4 | **Convert** | 6/10 | 🟡 RISK | Cart→Checkout works for COD. Missing: seller trust on PDP, escrow badge, stock validation |
| 5 | **Fulfill** | 3/10 | 🔴 YES | No confirm receipt, no auto-confirm, no escrow release, no dispute flow, no SLA enforcement |

### Phase A Verdict: **NOT READY TO LAUNCH**

**Overall Score: 20/60 (33%)**

Minimum required for soft-launch: 42/60 (70%) with no individual domain below 5.

---

## Phase B: Scale Readiness (Post-Launch)

| # | Domain | Score | Notes |
|---|---|---|---|
| 6 | **Retain** | 2/10 | No loyalty program, no re-order, no price-drop alerts, no personalized recommendations |
| 7 | **Grow** | 1/10 | No referral system, no affiliate program, no seller tier progression visible to buyers |
| 8 | **Recover** | 2/10 | Dispute queue exists for admin but no buyer-facing flow, no SLAs, no auto-escalation |
| 9 | **Operate** | 5/10 | Admin panel exists, Grafana/Prometheus running. Missing: automated payouts, fraud detection |

---

## Phase C: Business Model Validation

| # | Domain | Score | Notes |
|---|---|---|---|
| 10 | **Marketplace Economics** | 1/10 | No financial model, no CAC/LTV tracking, no take-rate analysis, no breakeven calculation |
| 11 | **Platform Architecture** | 7/10 | Solid: 16 services, Kafka, Redis, Keycloak, Elasticsearch. Gaps: SSR, feature flags under-used |
| 12 | **Analytics & KPIs** | 2/10 | Prometheus for infra only. No product analytics, no funnel tracking, no conversion events |

---

## Critical Path to Launch

### What Must Be True Before ANY Real User Touches This

| Gate | Requirement | Current | Work Needed |
|---|---|---|---|
| G1 | Customer can discover products (search works + autocomplete) | ⚠️ | Fix ES health, add autocomplete |
| G2 | Customer sees seller trust signals on every PDP | ❌ | Add seller card component |
| G3 | Customer knows money is protected (escrow visible) | ❌ | Add escrow badge + explanation |
| G4 | Customer can confirm receipt after delivery | ❌ | Build confirm button + escrow release |
| G5 | Customer can dispute a problem | ❌ | Build buyer dispute flow |
| G6 | Seller gets paid after buyer confirms | ❌ | Wire escrow release → wallet credit |
| G7 | Orders auto-confirm after 7 days | ❌ | Scheduled job |
| G8 | Refund/return policy exists as a page | ❌ | Write policy + create page |
| G9 | Terms of service exist | ❌ | Write ToS + create page |
| G10 | Zero search results shows alternatives | ⚠️ | Improve zero-state |

**10 gates. 7 are red. 3 are yellow. 0 are green.**

---

## Prioritized Roadmap to Launch

### Sprint 1 (Week 1-2): Trust Foundation

| Task | Effort | Impact |
|---|---|---|
| Write refund/return policy page | S | Unblocks trust layer |
| Write terms of service page | S | Legal requirement |
| Add seller rating + badge to PDP | M | Trust signal #1 |
| Add "Escrow protected" badge to PDP | S | Trust signal #2 |
| Add "Confirm Receipt" button on order detail | M | Enables escrow release |
| Add escrow release logic (confirm → wallet credit) | M | Seller gets paid |
| Add auto-confirm scheduled job (7 days) | M | Prevents funds lockup |

### Sprint 2 (Week 3-4): Discovery + Convert

| Task | Effort | Impact |
|---|---|---|
| Search autocomplete (ES suggestion API) | M | Discovery #1 improvement |
| Recent searches (localStorage + API) | S | Discovery UX |
| Zero-result fallback (show popular products) | S | Prevent dead ends |
| Breadcrumbs on category/product pages | S | Navigation clarity |
| Delivery estimate on PDP (stub: "2-5 days") | S | Reduces uncertainty |
| COD/payment badges on product card | S | Trust + conversion |
| Stock validation at checkout time | M | Prevent failed orders |

### Sprint 3 (Week 5-6): Fulfill + Recover

| Task | Effort | Impact |
|---|---|---|
| Buyer dispute flow (issue type → evidence → submit) | L | Core marketplace function |
| Seller dispute response UI | M | Two-sided dispute |
| Admin dispute resolution (decision + refund trigger) | M | Closes the loop |
| SLA timers (24h accept, 48h ship, 48h respond) | M | Operational discipline |
| Auto-reject after seller SLA miss | S | Protects buyers |
| Order status timeline on order detail | M | Reduces anxiety |
| Notification on every state change (push + in-app) | L | Communication |

### Sprint 4 (Week 7-8): Acquire + Polish

| Task | Effort | Impact |
|---|---|---|
| SSR for product/category pages (or pre-rendering) | L | SEO (critical for organic) |
| OG tags on all pages | S | Social sharing |
| Homepage trust bar + hero banner | M | First impression |
| Homepage "recently viewed" section | S | Returning user engagement |
| Vietnamese diacritics search tolerance | M | 70%+ users type without diacritics |
| Seller store public page (complete) | M | Seller trust destination |

---

## Effort Legend

| Size | Definition | Calendar Time |
|---|---|---|
| S | < 1 day, single file change or simple component | 1-4 hours |
| M | 1-3 days, multiple files, may need backend + frontend | 1-3 days |
| L | 3-5 days, new flow/feature, multiple services involved | 3-5 days |
| XL | 1-2 weeks, architectural change (e.g., SSR migration) | 5-10 days |

---

## Decision Required: Position Before Building

Before executing Sprints 1-4, the product owner must answer:

1. **Who is our first seller cohort?** (Category determines everything)
2. **Who is our first buyer cohort?** (Vietnamese domestic? Diaspora? International?)
3. **What is our commission vs Shopee?** (Current 10% STANDARD is HIGHER than Shopee's 6.5%)
4. **What is our payout speed promise?** (Shopee: 7-14 days. Can we do 3 days?)
5. **What is our dispute guarantee?** (Who does the platform side with by default?)

These are business decisions, not engineering decisions. They determine what to build next.

---

## Summary

```
┌──────────────────────────────────────────────────────┐
│                                                      │
│   VNShop Launch Readiness: 20/60 (33%)               │
│                                                      │
│   Architecture: ████████░░ 80% (over-built)          │
│   Customer Journey: ██░░░░░░░░ 20% (under-built)    │
│   Business Model: █░░░░░░░░░ 10% (undefined)         │
│                                                      │
│   Risk: Building faster than validating.             │
│                                                      │
│   Recommendation:                                    │
│   STOP new features.                                 │
│   FIX the core loop (discover → trust → buy →        │
│   receive → confirm).                                │
│   VALIDATE with 10 real sellers + 100 real buyers.   │
│                                                      │
└──────────────────────────────────────────────────────┘
```
