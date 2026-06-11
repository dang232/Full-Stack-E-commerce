# Chapter 5 — Seller cashes out

**Persona:** seller
**Verdict:** BLOCKED
**Generated:** 2026-06-09T19:04:04.375Z

## Business outcomes verified

| AC | Outcome | Status |
|---|---|---|
| AC-5.1 | Seller with positive wallet balance can submit a payout request | BLOCKED |
| AC-5.2 | Submitted payout immediately appears in admin's pending payout queue | NOT_RUN |

## Stakeholder summary

0 of 2 acceptance criteria passed for the seller flow. Blocked: AC-5.1 — journey state missing required keys: orderId, subOrderId — a previous chapter must run first.

## Steps (engineer view)

### 01. AC-5.1 — Predecessor chapters left a fulfilled order in state.json — BLOCKED

![Predecessor chapters left a fulfilled order in state.json](screenshots/01-ac-5-1-predecessor-chapters-left-a-fulfilled-order-in-state-.png)

```
journey state missing required keys: orderId, subOrderId — a previous chapter must run first
```

## Artifacts

- `trace.zip` — open with `npx playwright show-trace trace.zip`
- `video.webm` — full session recording (gitignored)
- `screenshots/` — one `NN-slug.png` per step, regenerated each run
