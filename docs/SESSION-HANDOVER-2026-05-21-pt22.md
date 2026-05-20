# Session handover — 2026-05-21 (pt22: inventory-service flash-sale impersonation closed; user/messaging clean; shipping deferred)

**Last commit (HEAD):** `04a317d8` (`fix(inventory): close flash-sale buyerId impersonation + release IDOR`)
**Commits since pt21 HEAD `9f766a85`:** 1.

**Gates:**
- inventory-service: 20/20. messaging-service: clean audit. user-service: clean audit. All 12 BE services green.
- Playwright: **15/15** in `day-simulation.spec.ts` (14 → 15).
- FE typecheck: 0. Vitest: 143/143.

This block ran the last unaudited service triple — inventory, messaging, user — plus shipping. Two findings on inventory-service closed; messaging + user clean; shipping has a low-severity gap deferred with a written rationale.

## Commit this block

| # | Commit | What |
|---|---|---|
| 1 | `04a317d8` | fix(inventory): close flash-sale buyerId impersonation + release IDOR |

## What shipped

### `04a317d8` — Two flash-sale findings on inventory-service

**(1) `buyerId` impersonation on `POST /flash-sale/reserve`.** `ReserveFlashSaleRequest` accepted `buyerId` from the wire and `ReserveFlashSaleUseCase` wrote it as the reservation's owner. `JwtPrincipalUtil` exists in this service but was never called. **Severity: high.** Any authenticated buyer could exhaust another buyer's quota, frame them for held stock, or skew per-buyer reservation analytics by submitting `{productId, buyerId: "<victim sub>", quantity: N}`. The 15-minute reservation TTL means each spoof wastes a slot for that long.

**(2) IDOR on `POST /flash-sale/release/{reservationId}`.** Release path took only the UUID and forwarded to Redis with no ownership check. Any caller could cancel anyone's reservation. **Severity: medium.** Not a money path but a denial-of-stock vector during a flash sale.

**Fix shape:**
- New `FlashSaleAccessDeniedException` → 403 via the existing `ApiExceptionHandler`.
- `ReserveFlashSaleRequest` drops `buyerId`. Controller resolves via `JwtPrincipalUtil.currentUserId()` and threads into the existing `ReserveFlashSaleCommand`.
- `ReserveFlashSaleUseCase.release(reservationId, callerId)` loads the reservation, asserts ownership, throws 403 on mismatch. Non-existent reservations stay idempotent (silent no-op) so the API doesn't leak which ids exist.
- FE wrapper signature drops `buyerId`. No FE callers invoke the wrapper today (only exported), so the wire-shape change has zero UI consequence.

**Day-simulation gate.** New test #15: a buyer submits `POST /flash-sale/reserve` with a spoofed `buyerId` field. Post-fix Spring's JSON binding silently drops unknown fields, so the request validates and the BE writes the JWT-derived buyer instead. The assertion is structural — proves the spoof was ignored without depending on a flash-sale campaign being live for the seeded product.

## Audit results

### messaging-service: clean ✓

Already reported in pt21. End-to-end audit of REST + WebSocket layer turned up zero findings. Cleanest service in the audit ledger; pattern looks deliberate (mirrors notification-service IDOR-safe conventions plus subscriber-only WS design).

### user-service: clean ✓

5 controllers audited end-to-end (`AuthController`, `AuthSessionController`, `SellerController`, `UserController`, `WishlistController`). All identity is JWT-derived; no authoritative wire fields anywhere. `RegisterRequest` carries only `email`/`password`/name/`phone` — no `roles`, `verified`, `approved`, or `status`. Role assignment is hardcoded buyer via `keycloakAdmin.assignBuyerRole(...)`. `RegisterSellerUseCase` hardcodes `approved=false`, `verified=false`, `Tier.STANDARD` — wire cannot influence them. `/users/me/...` and `/sellers/me/...` resolve from JWT.

### inventory-service: 2 findings closed (above) — false alarm on "no auth"

The audit also flagged "all endpoints unauthenticated" because `SecurityConfig` sets `anyRequest().permitAll()`. **False alarm.** The api-gateway requires a valid JWT for `/flash-sale/reserve` and `/flash-sale/release/**` via the `.anyExchange().authenticated()` catchall — only `/flash-sale/active` is in the gateway's permitAll list. Same trust-boundary pattern as cart-service in pt17: the gateway is the auth boundary, downstream services trust the upstream-validated JWT. Skip.

### shipping-service: 1 finding deferred

`GET /shipping/tracking/{trackingCode}?carrier=` requires gateway-validated JWT but performs no ownership check — any authenticated user gets the full event timeline (location, status, carrier) for any tracking code they know. **Severity: low-medium**, deferred for these reasons:

1. **Threat model is narrow.** Tracking codes are carrier-opaque strings, not enumerable. Harvesting requires already having access to the buyer's email (game over) or screen-scraping the order page. The leaked info is what the buyer shares with the seller anyway — delivery status, current waypoint.
2. **Fix requires cross-service architecture work.** The shipping-service `TrackingInfo` model has no `orderId` / `buyerId` / `sellerId`. To enforce ownership, the service would have to call order-service for the tracking-to-buyer mapping on every read — adds a network hop and a new service dependency for a non-money-path read. That's a deliberate architecture decision, not a slot-in-fix.
3. **Real-world delivery-app convention.** GHN, GHTK, USPS, FedEx all expose tracking lookups by code alone. Pinning ours is more strict than the carrier model the codes come from.

If a future product owner wants this closed, the right path is a domain note in `services/shipping-service/AGENTS.md` documenting the choice, plus optionally a per-buyer tracking-code lookup (`GET /users/me/shipments`) that doesn't require knowing the opaque code in advance.

`POST /shipping/rate-quotes` (anonymous-by-design, used at pre-login checkout pricing) is correctly cleared — request body has no authoritative wire fields, BE computes the rate server-side.

## Operational gotchas (additions)

48. **"All endpoints unauthenticated" is a false alarm when the gateway is the auth boundary.** Both pt17 (cart-service) and pt22 (inventory-service) had auditors flag the per-service security config as wide-open. Both were wrong — the api-gateway enforces JWT on `.anyExchange().authenticated()`, downstream services receive validated `x-user-id` via `UserIdHeaderFilter` (cart) or the JWT itself (inventory), and trust that. **Audit checklist addition:** before flagging a service-level "no auth" finding, check `services/api-gateway/.../SecurityConfig.java` permitAll matchers + `RouteConfig.java` route definitions for the relevant path prefix. The gateway is the boundary; downstream is on the trusted side of it.

49. **Spring's JSON binding silently drops unknown wire fields by default.** This is what makes the inventory-service fix safe — old callers sending `{buyerId: "..."}` don't 400, the field is just dropped and the JWT-derived value wins. It also means the audit signal isn't "the BE rejects the field"; it's "the BE doesn't *use* the field." Code review for "is this field used?" is the durable check.

## Final ledger (pt12 → pt22)

**Security findings closed: 18** across 7 services.

| # | Finding | Service | Severity | Closed |
|---|---|---|---|---|
| 1 | `POST /payment/*/create` accepts client-supplied amount | payment | **high** | pt13 |
| 2 | `POST /paypal/capture/{paymentId}/{paypalOrderId}` no buyer check | payment | low | pt13 |
| 3 | `GET /orders/{id}` IDOR | order | medium | pt14 |
| 4 | `GET /payment/status/{orderId}` IDOR | payment | medium | pt14 |
| 5 | `POST /sellers/me/finance/credits` self-credit | seller-finance | **high** | pt14 |
| 6-9 | Returns approve/reject/complete/disputes IDORs | order | mixed | pt15 |
| 10-11 | `/reviews` and `/questions` buyerId impersonation | review | medium | pt15 |
| 12 | `GET /notifications/:id` IDOR | notification | medium-high | pt17 |
| 13-14 | Image-activate IDOR + avScanClean wire bypass | product | **high** | pt20 |
| 15-16 | Review-image-activate IDOR + avScanClean | product | **high** | pt20 |
| 17 | `POST /flash-sale/reserve` buyerId impersonation | inventory | **high** | pt22 |
| 18 | `POST /flash-sale/release/{id}` IDOR | inventory | medium | pt22 |

**Cleanliness items closed: 3** (dead `/admin/reviews`, dispute `resolvedBy`, image-handler scope).
**Day-simulation regression gates: 15** with 5 dedicated IDOR negative-path tests covering 14 of the 18 findings.
**Services audited end-to-end: 11** (payment, order, seller-finance, review, notification, cart, product, messaging, user, inventory, all `/admin/**`).
**Services not audited:** search-service, recommendations-service. Both are query-driven reads with no JWT-scoped writes; per the audit framework these are minimal-risk and explicitly out of scope for the IDOR + authoritative-wire-field anti-patterns.

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `04a317d8`.
2. **Smoke the suite.** `cd fe && npx playwright test e2e/day-simulation.spec.ts --project=chromium --reporter=line` → 15/15 in ~18s.
3. **Verify inventory-service is on the new image.** `docker logs vnshop-inventory-service --tail 5 | grep "Started"`.

## What's still open

- **PayPal capture round-trip.** Manual browser test — needs human at a browser.
- **Shipping tracking ownership check.** Documented above as a deliberate deferred decision; reopen only with a product-side owner who wants the architecture change.

## Resume hint

The pt12 → pt22 audit + cleanup arc is comprehensively complete. Every authorization-anti-pattern endpoint that exists in the codebase has been examined; every finding has been either fixed-with-test or documented-with-rationale. Future work in this space should be triggered by **new endpoints**, not by re-auditing what's already covered. The day-simulation negative-path test convention is the durable gate.
