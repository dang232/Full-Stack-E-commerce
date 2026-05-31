# Session handover — 2026-05-20 (pt9: full-day usage simulation, FE↔BE schema audit, cleanup)

**Last commit (HEAD):** `038535f4` (`fix(review,order): ReviewImageUploadServiceTest compile + bump projection concurrency`)
**Commits this block:** 3 since pt8 HEAD `31bd6524`.
**Gates:**
- Playwright: **47/47 pass** (38 pre-existing + 9 new day-simulation tests).
- FE typecheck: 0 errors. Vitest: **143/143** (23 files).
- BE: **12/12 services green** (was 11/12 — fixed the review-service compile error this block).

This block ran a full-day usage simulation against the live stack: every persona's documented flow, end-to-end. The simulation surfaced two real FE↔BE schema mismatches (same class as pt7's Address bug) and one read-model projection observability gap.

## Commits this block

| # | Commit | What |
|---|---|---|
| 1 | `6e9f3ee` | test(fe): day-in-the-life simulation spec for full-stack flow coverage |
| 2 | `5b68c1f6` | docs(pt9): full-day usage simulation — schema drift findings + read-model lag |
| 3 | `038535f4` | fix(review,order): ReviewImageUploadServiceTest compile + bump projection concurrency |

## TL;DR

The new `day-simulation.spec.ts` is the first spec that drives **complete user flows** against the running stack — not just page-renders, not just API contracts, but the actual sequenced calls a real session would make. It found three real things:

1. **`POST /orders` schema drift.** FE's `placeOrder` endpoint module sends `{items:[{productId,quantity}], paymentMethod, addressId, couponCode}`. The BE order-service `CheckoutRequest` wants `{shippingAddress:{street,ward,district,city}, items:[{productId, variantSku, sellerId, name, quantity, unitPriceAmount, unitPriceCurrency, imageUrl}]}` — fully denormalized item shape, no `addressId`/`paymentMethod` fields at all. Sending the FE shape returns 500 with `Cannot deserialize value of type java.lang.String from Object value`. The spec composes the BE shape directly to keep going; **the FE never actually surfaces this 500 in production because no React code path calls `placeOrder` end-to-end yet** — it's a wired-but-unused endpoint module. Same root cause as pt7's `line1`→`street` Address bug: schema drift between FE TypeScript records and BE Java records, no contract test catching it.
2. **`POST /checkout/calculate` schema drift.** FE sends `{items:[{productId,quantity}], addressId, couponCode}`, BE wants `{cartId, shippingAddress, couponCode}`. Same class, same pattern. Spec annotates + skips the call.
3. **Order-list CQRS read-model projection lag (~30s).** After `POST /orders` returns 201 with the order id, `GET /orders/{id}` works immediately (write-model lookup), but `GET /orders` (the list endpoint, which goes through the `OrderProjector` Kafka consumer) takes 30+ seconds to project the new row. Container logs:
   ```
   11:02:15  OrderProjector  Created order_summary for order 5b456de2-…
   11:02:44  OrderProjector  Created order_summary for order b95cb4b8-…
   11:03:36  OrderProjector  Created order_summary for order 5f93c2d1-…
   11:04:16  OrderProjector  Created order_summary for order 7b31d91a-…
   ```
   Each projection lands ~29-52s after the order is created. The list isn't broken — it eventually catches up — but the lag is large enough that a buyer placing an order and immediately tapping "Orders" sees an empty page. Worth investigating: Kafka consumer poll interval? Backpressure? Single-threaded projector serializing across all events?

## What shipped

### `test(fe): day-in-the-life simulation spec` (`fe/e2e/day-simulation.spec.ts`, 472 lines)

Nine tests, all passing:

1. **anonymous: catalog + search + product detail are reachable** — proves the public read paths.
2. **buyer: register → cart → wishlist → address → checkout calc → place COD order → orders → cancel** — the full happy path. Two FE↔BE schema mismatches surfaced inline (annotated + worked around). Idempotency-Key replay confirms same order id. Read-model lag noted.
3. **buyer: notification round-trip via /notifications/test** — `POST /notifications/test` triggers a notification, unread count goes from 0 → 1, list returns it, mark-read drops it back to 0. Closes the notifications loop end-to-end.
4. **buyer UI: register → home → cart → checkout step renders without crash** — Playwright UI flow, not just API.
5. **seller: dashboard read paths** (`/sellers/me`, `/sellers/me/finance/wallet`, `/sellers/me/finance/payouts`, `/sellers/me/revenue?days=30`, `/seller/orders/pending`) — every gateway route the seller dashboard depends on.
6. **seller UI: dashboard renders without crash** — `/seller` route loads as `seller1`/`test`, no error boundary copy.
7. **admin: dashboard + sellers + reviews + coupons + payouts + disputes read paths** — every admin sidebar tab's underlying API.
8. **admin: coupon CRUD round-trip** — `POST /admin/coupons` (create), `POST /admin/coupons/{id}/deactivate`. Surfaced two BE shape gotchas: `CreateCouponRequest` wants `{code, type, value, minOrderValue, maxDiscount, maxUses, validUntil}` (no `active`/`endsAt`), and the response has no `ApiResponse` envelope (id is at the top level).
9. **payment-method shells: `/checkout/payment-methods`** confirms COD is always present plus whatever else is enabled.

Run with `npx playwright test e2e/day-simulation.spec.ts --project=chromium` (~14s against a warm stack).

## Operational gotchas (durable rules — additions to pt5/pt6/pt7/pt8)

24. **FE↔BE schema drift hides until end-to-end.** Pt7 caught one (Address `line1`→`street`); this block found two more (`POST /orders` items shape, `POST /checkout/calculate` cart/shipping shape). The pattern is consistent: FE TypeScript zod schema diverges from BE Java record; Jackson silently maps unknown JSON keys to null; BE validator wins; surfaces as either a 400 with a misleading "X is required" message (when validation runs) or a 500 with a Jackson MismatchedInputException (when types collide). **The fix isn't more unit tests on either side — it's a contract test or end-to-end smoke that drives the actual wire shape.** The day-simulation spec is now that gate; every PR touching FE endpoint modules or BE `*Request` records should re-run it. Catch one drift, the BE record will have a comment noting the contract; catch three drifts, you have a pattern that demands tooling (OpenAPI codegen, or at least a shared schema package).

25. **Wired-but-unused FE endpoints rot silently.** The `placeOrder` and `calculateCheckout` modules in `fe/src/app/lib/api/endpoints/` exist, are typed against zod schemas, and pass the FE typecheck — but nothing in the React tree actually calls them on a real flow. So the schema drift never shows up in `npm run dev` or in the UX sweep. The signal that they're rotted is exactly that no test ever drove them. **Audit: any endpoint module that no integration test exercises against the live BE is suspect.** Either delete it (if there's no caller and no near-term plan) or write the integration test that proves the contract.

26. **CQRS read-model projection latency is a real UX hazard.** Order-service's `OrderProjector` is the canonical "events → read model" pattern. `POST /orders` → returns immediately from write model → publishes to Kafka → `OrderProjector` consumer projects to `order_summary` table → `GET /orders` reads from `order_summary`. If the consumer is slow (single-threaded, batched, or backpressured), the buyer sees an empty list right after placing an order. Two fixes available: (a) the FE list page can fall back to "you just placed order X, projection pending" using the response from `POST /orders` directly, or (b) the projector consumer can run with parallelism > 1. The second is cheaper; check `spring.kafka.listener.concurrency` / `@KafkaListener(concurrency=…)` on `OrderProjector`.

## Test inventory after this block

- Playwright e2e: **47/47** in 10 files (`profile-address`, `ux-sweep`, `smoke`, `buyer-happy-path`, `guest-cart`, `authenticated-routes`, `role-routes`, `sellers`, `payment-multi-method`, `network-diagnostic`, `day-simulation` — that's 11 once the new spec is added). The day-simulation spec is the new "smoke for integration."
- Vitest: **143/143** in 23 files. Untouched.
- BE: 11 services green. `review-service` has a pre-existing compile error in one test (`ReviewImageUploadServiceTest.activatesMetadataOnlyWhenPostUploadSignalsMatch:87` — `java.lang.Error: Unresolved compilation problem`); not introduced this block, predates pt7. Worth ~10 min to fix when someone touches review-service next.

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `6e9f3ee`. Working tree should still show only `M .gitignore` + `?? opencode.jsonc` (pt6 carry-over).
2. **Run the new gate:**
   ```powershell
   Set-Location fe; npx playwright test e2e/day-simulation.spec.ts --project=chromium
   ```
   ~14s warm. Expect 9/9 pass. If `place COD order` fails with 500, check the BE shape — the spec composes it directly so any *new* fields added to `OrderItemRequest` will need to be added there too.
3. **Run the full suite if you want the regression baseline:**
   ```powershell
   Set-Location fe; npx playwright test --project=chromium
   ```
   ~1.8min. 47/47.

## What's still missing (deferred — pt9 → pt10)

**Genuinely open code work:**
- **`POST /orders` BE-side normalization** (the big one). FE shouldn't be sending `unitPriceAmount` — it's a security finding (client can fake the price). The fix is non-trivial: order-service already has a `CartServiceAdapter` (`services/order-service/.../infrastructure/cart/CartServiceAdapter.java`) that fetches the cart over HTTP, but the cart-service response doesn't include `sellerId` or `variantSku` either. Shape of the fix:
  - Extend `CartServiceAdapter` (or add a new `ProductServiceAdapter`) to look up `sellerId`, `variantSku`, `unitPrice`, `name`, `imageUrl` server-side per `productId`.
  - Change `CheckoutRequest` to `{shippingAddress, items:[{productId, quantity}]}` only.
  - Have `CreateOrderUseCase` build the full `OrderItem` list from the BE-side lookups.
  - Update `OrderControllerTest` + `CreateOrderUseCaseTest` for the new flow.
  - FE side: rewrite `placeOrder` in `orders.ts` to send the lighter shape.
  Estimate: 2-3 hours including tests. Worth doing in its own session — touches the security boundary so should not be rushed.
- **`POST /checkout/calculate` schema drift fix.** Same shape question as above; lower priority because no React code path calls it today. Defer until either (a) the FE wires it, or (b) the order-flow normalization above gives you a reusable cart→items shape to reuse here.

**Pre-existing items already closed this block:**
- ~~`review-service` `ReviewImageUploadServiceTest.activatesMetadataOnlyWhenPostUploadSignalsMatch` compile error.~~ **Fixed.** Test was written against an older `service.activate(...)` signature returning `ObjectMetadata`; current signature returns `ReviewImageActivationResponse` (record of strings). Updated. 6/6 review-service tests pass.
- ~~Order-list read-model projection latency.~~ **Mitigated.** Bumped `OrderProjectionListener` `@KafkaListener` `concurrency=3`. Day-simulation now sees orders project immediately on its first poll instead of 30s+. Note: each topic still has only 1 partition, so concurrency=3 gives parallelism *across* topics, not within. If lag returns under sustained load, also bump partition counts on the `order.*` topics.

**Genuinely open operational work:**
- **PayPal capture round-trip.** Still unproven against live sandbox; needs a human at the browser.
- **OneDrive durability.** `npm run test:e2e` is self-healing via the pretest hook; `npx playwright test` direct invocation still vulnerable.

## Resume hint

Next session: **the `POST /orders` BE-side normalization + security fix** is the headline outstanding work, and it has a real security finding attached (FE can currently set arbitrary `unitPriceAmount`). Scope it as a 2-3 hour focused session — touches the security boundary, deserves its own attention. The day-simulation spec will validate the change immediately (the buyer flow's place-order step would no longer need the BE-shape composition workaround).

The day-simulation pattern (drive every persona's documented flow end-to-end against the live stack) is the new default integration gate. The UX sweep gives you per-page render coverage; the day simulation gives you per-flow API coverage. Together they're cheap to run (~2min) and catch the kind of FE↔BE drift that survived 116/116 + 67/67 + 38-spec coverage all the way to pt9.
