# Chapter 3 — Seller fulfills the order

**Persona:** seller
**Verdict:** FAIL
**Generated:** 2026-05-23T19:23:29.685Z

## Business outcomes verified

| AC | Outcome | Status |
|---|---|---|
| AC-3.1 | A seller sees the buyer's new order in their pending queue within 30 s | PASS |
| AC-3.2 | A seller can accept and ship the order with a tracking number | FAIL |

## Stakeholder summary

1 of 2 acceptance criteria passed for the seller flow. Failed: AC-3.2 (A seller can accept and ship the order with a tracking number).

## Steps (engineer view)

### 01. AC-3.1 — Predecessor chapter has placed an order (state.json check) — PASS

![Predecessor chapter has placed an order (state.json check)](screenshots/01-ac-3-1-predecessor-chapter-has-placed-an-order-state-json-ch.png)

### 02. AC-3.1 — Seller's pending queue includes Chapter 2's order within 30 s — PASS

![Seller's pending queue includes Chapter 2's order within 30 s](screenshots/02-ac-3-1-seller-s-pending-queue-includes-chapter-2-s-order-wit.png)

### 03. AC-3.1 — Seller logs into the SPA and the Orders tab renders the pending row — PASS

![Seller logs into the SPA and the Orders tab renders the pending row](screenshots/03-ac-3-1-seller-logs-into-the-spa-and-the-orders-tab-renders-t.png)

### 04. AC-3.2 — Seller clicks Accept on the row and the toast confirms the round-trip — PASS

![Seller clicks Accept on the row and the toast confirms the round-trip](screenshots/04-ac-3-2-seller-clicks-accept-on-the-row-and-the-toast-confirm.png)

### 05. AC-3.2 — Seller ships the accepted order with a tracking number, summary toast confirms — FAIL

![Seller ships the accepted order with a tracking number, summary toast confirms](screenshots/05-ac-3-2-seller-ships-the-accepted-order-with-a-tracking-numbe.png)

```
Ship button never replaced Accept

Ship button never replaced Accept

[2mexpect([22m[31mreceived[39m[2m).[22mtoBeGreaterThan[2m([22m[32mexpected[39m[2m)[22m

Expected: > [32m0[39m
Received:   [31m0[39m

Call Log:
- Timeout 20000ms exceeded while waiting on the predicate
```

## Artifacts

- `trace.zip` — open with `npx playwright show-trace trace.zip`
- `video.webm` — full session recording (gitignored)
- `screenshots/` — one `NN-slug.png` per step, regenerated each run
