# Chapter 2 — Buyer discovers and orders

**Persona:** buyer
**Verdict:** FAIL
**Generated:** 2026-05-30T17:16:14.972Z

## Business outcomes verified

| AC | Outcome | Status |
|---|---|---|
| AC-2.1 | A new visitor can register and start shopping in a single browser session | PASS |
| AC-2.2 | A coupon applied at checkout reduces the order total by exactly the published discount | NOT_RUN |
| AC-2.3 | A placed COD order is visible in the buyer's order history within 30 s | NOT_RUN |
| AC-2.4 | A product the buyer can browse via /products is also discoverable via /search within 30 s — proves the kafka product-event → search-index projection is live | FAIL |

## Stakeholder summary

1 of 4 acceptance criteria passed for the buyer flow. Failed: AC-2.4 (A product the buyer can browse via /products is also discoverable via /search within 30 s — proves the kafka product-event → search-index projection is live).

## Steps (engineer view)

### 01. AC-2.1 — Predecessor chapter has published a coupon (state.json check) — PASS

![Predecessor chapter has published a coupon (state.json check)](screenshots/01-ac-2-1-predecessor-chapter-has-published-a-coupon-state-json.png)

### 02. AC-2.1 — Visitor lands on the public store home page — PASS

![Visitor lands on the public store home page](screenshots/02-ac-2-1-visitor-lands-on-the-public-store-home-page.png)

### 03. AC-2.1 — Visitor registers a fresh buyer account and is signed in — PASS

![Visitor registers a fresh buyer account and is signed in](screenshots/03-ac-2-1-visitor-registers-a-fresh-buyer-account-and-is-signed.png)

### 04. AC-2.1 — Buyer opens a real seeded product and adds it to their cart — PASS

![Buyer opens a real seeded product and adds it to their cart](screenshots/04-ac-2-1-buyer-opens-a-real-seeded-product-and-adds-it-to-thei.png)

### 05. AC-2.4 — Product is discoverable via /search within 30 s of being browsable on /products — FAIL

![Product is discoverable via /search within 30 s of being browsable on /products](screenshots/05-ac-2-4-product-is-discoverable-via-search-within-30-s-of-bei.png)

```
expected /search?q=Tai nghe Sony WH-1000XM5 to surface productId=2b0a8522-4310-4665-9874-bf37a5481667 within 30 s — search-index projection may be stale or kafka consumer disconnected

expected /search?q=Tai nghe Sony WH-1000XM5 to surface productId=2b0a8522-4310-4665-9874-bf37a5481667 within 30 s — search-index projection may be stale or kafka consumer disconnected

[2mexpect([22m[31mreceived[39m[2m).[22mtoContain[2m([22m[32mexpected[39m[2m) // indexOf[22m

Expected value: [32m"2b0a8522-4310-4665-9874-bf37a5481667"[39m
Received array: [31m[][39m

Call Log:
- Timeout 30000ms exceeded while waiting on the predicate
```

## Artifacts

- `trace.zip` — open with `npx playwright show-trace trace.zip`
- `video.webm` — full session recording (gitignored)
- `screenshots/` — one `NN-slug.png` per step, regenerated each run
