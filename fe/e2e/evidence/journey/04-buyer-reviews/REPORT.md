# Chapter 4 — Buyer reviews the ordered product

**Persona:** buyer
**Verdict:** BLOCKED
**Generated:** 2026-06-09T19:04:02.448Z

## Business outcomes verified

| AC | Outcome | Status |
|---|---|---|
| AC-4.1 | Buyer who placed the order can return to their /orders history and see it | BLOCKED |
| AC-4.2 | Buyer can submit a 5-star written review on the ordered product | NOT_RUN |
| AC-4.3 | Newly submitted review is visible on the public product page within 30 s | NOT_RUN |

## Stakeholder summary

0 of 3 acceptance criteria passed for the buyer flow. Blocked: AC-4.1 — journey state missing required keys: buyerEmail, buyerPassword, productId, orderId — a previous chapter must run first.

## Steps (engineer view)

### 01. AC-4.1 — Predecessor chapters left the buyer + product + order in state.json — BLOCKED

![Predecessor chapters left the buyer + product + order in state.json](screenshots/01-ac-4-1-predecessor-chapters-left-the-buyer-product-order-in-.png)

```
journey state missing required keys: buyerEmail, buyerPassword, productId, orderId — a previous chapter must run first
```

## Artifacts

- `trace.zip` — open with `npx playwright show-trace trace.zip`
- `video.webm` — full session recording (gitignored)
- `screenshots/` — one `NN-slug.png` per step, regenerated each run
