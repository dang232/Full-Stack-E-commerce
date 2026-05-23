# VNShop Journey — End-to-End Business Outcome Report

**Generated:** 2026-05-23T18:14:21.654Z

This report aggregates every chapter of the BA-grade persona journey. Each row is a single business outcome the platform must support; columns map to the chapter and persona that exercises it.

## Journey verdict: FAIL

- **Acceptance criteria passed:** 4 / 6
- **Chapters run:** 2 of 6
- **Failed:** AC-2.2 (A coupon applied at checkout reduces the order total by exactly the published discount)

## Chapter 1 — Admin onboards the marketplace — PASS

Persona: admin. Detail: [`journey/01-admin-onboards/REPORT.md`](journey/01-admin-onboards/REPORT.md).

| AC | Outcome | Status |
|---|---|---|
| AC-1.1 | Admin can review a pending seller's application and approve them | PASS |
| AC-1.2 | An approved seller appears in the public sellers list within 30 s | PASS |
| AC-1.3 | Admin can publish a fixed-discount coupon that is immediately redeemable at checkout | PASS |

## Chapter 2 — Buyer discovers and orders — FAIL

Persona: buyer. Detail: [`journey/02-buyer-orders/REPORT.md`](journey/02-buyer-orders/REPORT.md).

| AC | Outcome | Status |
|---|---|---|
| AC-2.1 | A new visitor can register and start shopping in a single browser session | PASS |
| AC-2.2 | A coupon applied at checkout reduces the order total by exactly the published discount | FAIL |
| AC-2.3 | A placed COD order is visible in the buyer's order history within 30 s | NOT_RUN |

