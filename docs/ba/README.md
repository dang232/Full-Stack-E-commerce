# VNShop BA Specification — Phase A

## Go-Live Gate: Sections 0–5

This document set defines what must be true before VNShop can accept real users.

---

## Document Index

| # | Document | Purpose |
|---|---|---|
| 0 | [Market Fit](00-MARKET-FIT.md) | Why would anyone use VNShop? Positioning, liquidity, validation gates. |
| 1 | [Acquire](01-ACQUIRE.md) | How customers find us. Channels, SEO, first impression, attribution. |
| 2 | [Trust](02-TRUST.md) | The 6-layer trust architecture. What customers evaluate before paying. |
| 3 | [Discovery](03-DISCOVERY.md) | Finding products: search, categories, recommendations, browsing. |
| 4 | [Convert](04-CONVERT.md) | PDP → Cart → Checkout → Order. Every friction point mapped. |
| 5 | [Fulfill](05-FULFILL.md) | Order state machine, escrow, SLAs, shipping, notifications. |
| 6 | [Screen Audit Matrix](06-SCREEN-AUDIT-MATRIX.md) | Every page: purpose, goals, status, priority fixes. |
| 7 | [Acceptance Criteria](07-ACCEPTANCE-CRITERIA.md) | Given/When/Then specs for QA. Testable contracts. |
| 8 | [Launch Readiness](08-LAUNCH-READINESS.md) | Scorecard, critical path, sprint plan, decisions required. |

---

## Current Status

```
Launch Readiness: 20/60 (33%)

Architecture:       ████████░░  80%  (over-built for current stage)
Customer Journey:   ██░░░░░░░░  20%  (under-built)
Business Model:     █░░░░░░░░░  10%  (undefined)
```

---

## Next Steps

### Immediate (Business Decisions)

1. Define positioning (which of the 5 options in §0?)
2. Choose first category vertical
3. Set commission rates vs competitors
4. Define payout speed promise
5. Write refund/return policy

### Sprint Execution (4 × 2-week sprints)

1. **Sprint 1**: Trust foundation (policies, seller signals, escrow visibility, confirm receipt)
2. **Sprint 2**: Discovery + Convert (autocomplete, zero-state, breadcrumbs, stock validation)
3. **Sprint 3**: Fulfill + Recover (disputes, SLAs, notifications, order timeline)
4. **Sprint 4**: Acquire + Polish (SSR/SEO, OG tags, homepage improvements)

### Phase B (after core loop validated)

- Retain (loyalty, re-order, price alerts)
- Grow (referrals, affiliate, seller tiers visible to buyers)
- Recover (full dispute SLA automation)
- Operate (automated payouts, fraud detection)

### Phase C (after economics validated)

- Marketplace Economics (CAC/LTV, take rate, contribution margin)
- Platform Architecture (SSR migration, CDN, multi-region)
- Analytics & KPIs (product analytics, funnel dashboards)

---

## How To Use This Spec

| Role | Use This For |
|---|---|
| **Product Owner** | Prioritization decisions, sprint planning, go/no-go gates |
| **Developer** | Acceptance criteria as implementation spec, screen audit for task list |
| **QA** | Acceptance criteria as test cases, screen matrix for regression coverage |
| **Designer** | UX requirements per phase, "what must be on screen" sections |
| **Business** | Market fit validation, KPIs, launch scorecard |
