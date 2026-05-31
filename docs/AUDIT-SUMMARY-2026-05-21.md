# Authorization audit summary — pt12 → pt23 (2026-05-19 → 2026-05-21)

Single index for the audit + cleanup arc that ran from pt12 (`f4821ffa`-era findings) through pt23 (`08c8c90b`). Per-block detail lives in the `SESSION-HANDOVER-2026-05-2{0,1}-pt{12..23}.md` files; this doc is the consolidated reference.

## Headline numbers

- **18 authorization findings closed** across **7 services**.
- **3 cleanliness items closed** (dead code, log integrity, exception-handler scope).
- **15 day-simulation Playwright tests** including **5 dedicated IDOR negative-path tests** that lock 14 of the 18 findings as durable regression gates.
- **13 backend services audited end-to-end** — every service with JWT-scoped writes or owned-data reads. (api-gateway IS the trust boundary, audited indirectly.)
- **2 services genuinely deferred:** PayPal capture round-trip (needs human at a browser); shipping tracking ownership check (cross-service architecture work, narrow threat model — see pt22 for written rationale).

## The audit framework

Three anti-patterns. Every controller in the codebase that takes a JWT and either mutates state or reads owned data was checked against all three:

1. **Authoritative wire fields.** A request DTO field the BE trusts as authoritative without re-deriving server-side. Suspects: `price`, `amount`, `sellerId`, `buyerId`, `userId`, `ownerId`, `status`, `role`, `verified`, `currency`, `senderId`, `actorId`, `approvedBy`, `avScanClean`. **If lying on the wire would cause the BE to write the lie, that's a finding.**
2. **IDOR via missing JWT-vs-id cross-check.** Endpoint takes a resource id (UUID, numeric, opaque string) and reads or mutates without verifying the JWT principal owns the resource.
3. **Path-mounted self-id endpoints.** `/users/{userId}/wishlist/...` instead of `/users/me/wishlist/...`. Smell, not always a bug — verify the BE doesn't blindly trust the path id over the JWT.

The merge gate for any new HTTP endpoint asks the same three questions:

1. Does the wire shape contain anything authoritative? Delete and resolve from JWT or a port lookup.
2. Does the use case look up another resource by id? Cross-check ownership against the JWT principal at the use-case boundary, not the controller.
3. Is there a "wrong-X → 403" test in day-simulation?

## Findings ledger

| # | Finding | Service | Severity | Closed (commit) |
|---|---|---|---|---|
| 1 | `POST /payment/*/create` accepts client-supplied amount | payment | **high** (real money) | pt13 (`943cd129`) |
| 2 | `POST /paypal/capture/{paymentId}/{paypalOrderId}` no buyer check | payment | low | pt13 (`2b0c90e3`) |
| 3 | `GET /orders/{id}` IDOR | order | medium (PII) | pt14 (`078ccedb`) |
| 4 | `GET /payment/status/{orderId}` IDOR | payment | medium | pt14 (`078ccedb`) |
| 5 | `POST /sellers/me/finance/credits` self-credit | seller-finance | **high** (real money) | pt14 (`93628a36`) |
| 6 | `POST /returns/{id}/approve` IDOR | order | medium | pt15 (`6007dff9`) |
| 7 | `POST /returns/{id}/reject` IDOR | order | medium | pt15 (`6007dff9`) |
| 8 | `POST /returns/{id}/complete` IDOR | order | **high** (real refund) | pt15 (`6007dff9`) |
| 9 | `POST /returns/{id}/disputes` IDOR | order | low-medium | pt15 (`e036d7ae`) |
| 10 | `POST /reviews` buyerId impersonation | review | medium | pt15 (`893101ed`) |
| 11 | `POST /questions` buyerId impersonation | review | medium | pt15 (`893101ed`) |
| 12 | `GET /notifications/:id` IDOR | notification | medium-high | pt17 (`5a35015c`) |
| 13 | `POST /sellers/me/products/{id}/images/activate` IDOR | product | **high** | pt20 (`9f7afa41`) |
| 14 | `POST /sellers/me/products/{id}/images/activate` avScanClean wire bypass | product | **high** | pt20 (`9f7afa41`) |
| 15 | `POST /reviews/{id}/images/activate` IDOR | product | **high** | pt20 (`9f7afa41`) |
| 16 | `POST /reviews/{id}/images/activate` avScanClean wire bypass | product | **high** | pt20 (`9f7afa41`) |
| 17 | `POST /flash-sale/reserve` buyerId impersonation | inventory | **high** | pt22 (`04a317d8`) |
| 18 | `POST /flash-sale/release/{id}` IDOR | inventory | medium | pt22 (`04a317d8`) |

### Cleanliness items

| # | Item | Service | Closed (commit) |
|---|---|---|---|
| 1 | Dead `/admin/reviews` controller (gateway routes to product-service only) | review | pt18 (`271624f5`) |
| 2 | Dispute `resolvedBy` log-integrity gap (admin id never recorded) | order | pt19 (`be79303e`) |
| 3 | `ImageUploadExceptionHandler` scope (review variant fell through to generic 400) | product | pt21 (`1c30218a`) |

### Tooling additions

| # | Item | Closed (commit) |
|---|---|---|
| 1 | Repo-wide OneDrive hydration script (`scripts/hydrate.mjs`) | pt19 (`e75b477d`) |

## Day-simulation regression gates

`fe/e2e/day-simulation.spec.ts` grew from 11 → 15 tests across the arc:

| # | Test | Covers findings |
|---|---|---|
| pt14 | "buyer B cannot read or pay for buyer A's order (pt14 IDOR fixes)" | 1, 3, 4, 5 |
| pt15 | "pt15 IDOR: wrong seller cannot approve/reject/complete a return; wrong buyer cannot open a dispute" | 6, 7, 8, 9 |
| pt15 | "negative-path 403 tests for pt13/pt14 IDOR fixes" (existing, extended) | 1, 3, 4 |
| pt17 | "buyer B cannot read buyer A's notification by id" | 12 |
| pt20 | "wrong seller cannot activate another seller's product image" | 13, 14, 15, 16 |
| pt22 | "buyer cannot reserve a flash-sale slot under another buyer's id" | 17, 18 |

Findings 2, 10, 11 are not directly negative-path-tested — finding 2 was a wrapper-only check (no real probe surface), and 10/11 were structural (the FE never sent the field; the BE just had to stop accepting it). Day-simulation gating wasn't worth the test cost.

## Services audited end-to-end

| Service | Audited | Findings | Notes |
|---|---|---|---|
| payment-service | pt13–14 | 2 | Closed via pt12 spec-level normalization + buyer cross-check |
| order-service | pt14–15, pt17, pt19 | 4 + cleanliness | Returns + disputes + admin disputes |
| seller-finance-service | pt14 | 1 | Self-credit closed |
| review-service | pt15, pt18 | 2 + dead-code cleanup | Two endpoints fixed; legacy `/admin/reviews` removed |
| notification-service | pt17 | 1 | NestJS scope-by-userId fix |
| cart-service | pt17 | 0 (false alarm) | Gateway is the trust boundary; `x-user-id` is server-injected |
| product-service | pt20–21 | 4 + cleanliness | Image-activate IDOR + avScanClean bypass on both seller and buyer flows |
| messaging-service | pt21 | 0 | Cleanest service; mirrors notification-service pattern + WS subscriber-only design |
| user-service | pt22 | 0 | All identity JWT-derived; admin already cleared in pt17 |
| inventory-service | pt22 | 2 | Flash-sale buyerId + release IDOR |
| search-service | pt23 | 0 | Public GETs, whitelist-validated sort, no JWT scoping |
| recommendations-service | pt23 | 0 | Aggregates only; no buyer-specific data |
| shipping-service | pt22 | 1 deferred | Tracking ownership check needs cross-service work — see pt22 |

api-gateway is the trust boundary itself; its `SecurityConfig` and `RouteConfig` were read at every block to confirm the auth boundary holds.

## Operational gotchas accumulated

These are the durable observations from the arc that future contributors should know. Numbered to continue the existing handover-doc series; full text lives in the per-block handover.

- **#40** Inner interfaces inside `@Repository` classes don't survive cold rebuilds in Spring Boot 4. Always extract to top-level. (pt15)
- **#41** Wire-field-never-populated is the hardest hole to spot. Any DTO field the FE doesn't send is either dead code or a security boundary the BE is trusting blindly. (pt15)
- **#42** Audit findings cluster — fix the family, not the instance. The 18 findings are really 4 anti-patterns; once you find one, every endpoint needs the same check. (pt15)
- **#43** OneDrive reparse-points re-stub on idle, not just on first sync. Pretest hooks need to cover every TS service rebuild path, not just `fe/e2e/`. (pt17 → pt19 hydration script)
- **#44** Side-loading compiled JS into a running container is a smell, not a fix. Always rebuild the image. The next `docker compose up -d --force-recreate` will silently revert. (pt17)
- **#45** Dead-code in deprecated services compounds the audit cost. Audit dead-code status as a separate pass before fixing security findings on a service. (pt18)
- **#46** Vulnerabilities at the intersection of two anti-patterns hide longest. The image-activate exploit slipped through pt12 → pt19 because each anti-pattern alone looked benign. (pt20 — added two checklist items: flag unused path variables, flag wire booleans named like a check/scan/verified.)
- **#47** `@RestControllerAdvice(assignableTypes = X)` is a scoping trap — exceptions thrown from peer controllers fall through to generic handlers. (pt20 → fixed pt21)
- **#48** "All endpoints unauthenticated" on a per-service config is a false alarm when the gateway is the auth boundary. Cart-service (pt17) and inventory-service (pt22) both hit this. (pt22)
- **#49** Spring's JSON binding silently drops unknown wire fields by default. This is what makes wire-shape removals safe, but it also means the audit signal isn't "the BE rejects the field" — it's "the BE doesn't *use* the field." (pt22)

## Per-block handover index

Each pt-block has its own handover with full diff/rationale:

- `docs/SESSION-HANDOVER-2026-05-20-pt12.md` — pt12 spec-level normalization (findings 1's parent fix)
- `docs/SESSION-HANDOVER-2026-05-20-pt13.md` — finding 1, finding 2
- `docs/SESSION-HANDOVER-2026-05-20-pt14.md` — findings 3, 4, 5
- `docs/SESSION-HANDOVER-2026-05-20-pt15.md` — findings 6–11 + review-service boot fix
- `docs/SESSION-HANDOVER-2026-05-21-pt16.md` — pt15 negative-path test lock
- `docs/SESSION-HANDOVER-2026-05-21-pt17.md` — finding 12, cart audit (false alarm), admin sweep
- `docs/SESSION-HANDOVER-2026-05-21-pt18.md` — cleanliness #1 (dead `/admin/reviews`)
- `docs/SESSION-HANDOVER-2026-05-21-pt19.md` — cleanliness #2 (dispute `resolvedBy`) + hydration script
- `docs/SESSION-HANDOVER-2026-05-21-pt20.md` — findings 13–16
- `docs/SESSION-HANDOVER-2026-05-21-pt21.md` — cleanliness #3 + messaging audit
- `docs/SESSION-HANDOVER-2026-05-21-pt22.md` — findings 17–18, user audit, shipping deferral
- `docs/SESSION-HANDOVER-2026-05-21-pt23.md` — search + recommendations close-out

## What's still genuinely open

- **PayPal capture round-trip.** Manual browser test, must run while logged in as the buyer who owns the payment. The pt13/pt14 buyer cross-check means the capturer must hold the order's buyer JWT. No automatable path.
- **Shipping tracking ownership check.** Deferred in pt22 with three documented reasons (narrow threat model, cross-service architecture work, mirrors carrier-app convention). Reopen only with product-side direction — don't reverse the deferral without new information.

Everything else is closed.

## How to use this document

- **New endpoint?** Walk the three questions in the framework section. If any answer is "no," fix before merging.
- **Finding a regression?** Find the row in the ledger, jump to the per-block handover, read the fix shape and the negative-path test, and confirm the contract is still in place.
- **New audit pass?** The framework + the gotchas section give you the search patterns. Don't re-audit the 13 services already covered unless the code changed materially.
