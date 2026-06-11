# Chapter 2 — Buyer discovers and orders

**Persona:** buyer
**Verdict:** BLOCKED
**Generated:** 2026-06-09T19:03:59.155Z

## Business outcomes verified

| AC | Outcome | Status |
|---|---|---|
| AC-2.1 | A new visitor can register and start shopping in a single browser session | BLOCKED |
| AC-2.2 | A coupon applied at checkout reduces the order total by exactly the published discount | NOT_RUN |
| AC-2.3 | A placed COD order is visible in the buyer's order history within 30 s | NOT_RUN |
| AC-2.4 | A product the buyer can browse via /products is also discoverable via /search within 30 s — proves the kafka product-event → search-index projection is live | NOT_RUN |

## Stakeholder summary

0 of 4 acceptance criteria passed for the buyer flow. Blocked: AC-2.1 — journey state missing required keys: couponCode, couponDiscountVnd — a previous chapter must run first.

## Steps (engineer view)

### 01. AC-2.1 — Predecessor chapter has published a coupon (state.json check) — BLOCKED

![Predecessor chapter has published a coupon (state.json check)](screenshots/01-ac-2-1-predecessor-chapter-has-published-a-coupon-state-json.png)

```
journey state missing required keys: couponCode, couponDiscountVnd — a previous chapter must run first
```

## Artifacts

- `trace.zip` — open with `npx playwright show-trace trace.zip`
- `video.webm` — full session recording (gitignored)
- `screenshots/` — one `NN-slug.png` per step, regenerated each run
