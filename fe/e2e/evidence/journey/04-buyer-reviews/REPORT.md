# Chapter 4 — Buyer reviews the ordered product

**Persona:** buyer
**Verdict:** FAIL
**Generated:** 2026-05-23T21:27:20.596Z

## Business outcomes verified

| AC | Outcome | Status |
|---|---|---|
| AC-4.1 | Buyer who placed the order can return to their /orders history and see it | PASS |
| AC-4.2 | Buyer can submit a 5-star written review on the ordered product | FAIL |
| AC-4.3 | Newly submitted review is visible on the public product page within 30 s | NOT_RUN |

## Stakeholder summary

1 of 3 acceptance criteria passed for the buyer flow. Failed: AC-4.2 (Buyer can submit a 5-star written review on the ordered product).

## Steps (engineer view)

### 01. AC-4.1 — Predecessor chapters left the buyer + product + order in state.json — PASS

![Predecessor chapters left the buyer + product + order in state.json](screenshots/01-ac-4-1-predecessor-chapters-left-the-buyer-product-order-in-.png)

### 02. AC-4.1 — Buyer logs back in and reaches /orders showing chapter 2's order — PASS

![Buyer logs back in and reaches /orders showing chapter 2's order](screenshots/02-ac-4-1-buyer-logs-back-in-and-reaches-orders-showing-chapter.png)

### 03. AC-4.2 — Buyer opens the product detail page for the ordered product — PASS

![Buyer opens the product detail page for the ordered product](screenshots/03-ac-4-2-buyer-opens-the-product-detail-page-for-the-ordered-p.png)

### 04. AC-4.2 — Buyer fills the review form and submits — success toast confirms — FAIL

![Buyer fills the review form and submits — success toast confirms](screenshots/04-ac-4-2-buyer-fills-the-review-form-and-submits-success-toast.png)

```
[2mexpect([22m[31mlocator[39m[2m).[22mtoBeVisible[2m([22m[2m)[22m failed

Locator: locator('textarea').filter({ hasNot: locator('[disabled]') }).first()
Expected: visible
Timeout: 10000ms
Error: element(s) not found

Call log:
[2m  - Expect "toBeVisible" with timeout 10000ms[22m
[2m  - waiting for locator('textarea').filter({ hasNot: locator('[disabled]') }).first()[22m

```

## Artifacts

- `trace.zip` — open with `npx playwright show-trace trace.zip`
- `video.webm` — full session recording (gitignored)
- `screenshots/` — one `NN-slug.png` per step, regenerated each run
