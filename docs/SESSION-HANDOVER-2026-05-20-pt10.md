# Session handover — 2026-05-20 (pt10: POST /orders BE normalization, price-tampering fix)

**Last commit (HEAD):** `274a5035` (`fix(order, fe): BE-side normalize POST /orders — close price-tampering hole`)
**Commits since pt9 HEAD `a1ad517a`:** 1 large fix.
**Gates:**
- Playwright: **47/47 pass** (38 pre-existing + 9 day-simulation, including the buyer flow now sending the light wire shape).
- FE typecheck: 0. Vitest: **143/143**.
- BE: order-service **66/66** (was 60 before the normalization, +6 new `CheckoutOrderUseCaseTest`). All 12 services green.

This block did the headline pt9 deferred item: the `POST /orders` BE-side normalization that closes the schema drift *and* the price-tampering security finding.

## TL;DR

Before pt10:
- FE sent `{shippingAddress, items:[{productId, variantSku, sellerId, name, quantity, unitPriceAmount, unitPriceCurrency, imageUrl}]}` — denormalized.
- BE accepted it as-is. **The client could set arbitrary `unitPriceAmount` and place an order at any price.** Tracked as a security finding in pt9.

After pt10:
- FE sends `{shippingAddress, items:[{productId, variantSku?, quantity}]}` — light shape, only what the client legitimately knows.
- BE resolves `sellerId, name, unitPrice, imageUrl` server-side via a new `ProductCatalogPort` against the product-service. Client-supplied price is structurally impossible.

Day-simulation buyer flow now drives the light shape end-to-end and proves the contract holds.

## What shipped (`274a5035`)

### BE — order-service
- **`ProductCatalogPort`** (`domain/port/out/`) — read-side hex port for catalog lookups. One method: `findByProductId(String) → Optional<CatalogProduct>`.
- **`CatalogProduct`** (`application/catalog/`) — minimal projection: `productId, sellerId, name, variants:[{sku, unitPrice}], imageUrl`. Has `findVariant(sku)` that defaults to first variant when sku is null/blank, so single-variant products work without forcing the client to know SKUs.
- **`ProductCatalogAdapter`** (`infrastructure/product/`) — HTTP implementation against `http://product-service:8082/products/{id}` (not 8083 — first run failed because of that, fix in the same commit). Behind a resilience4j circuit breaker (`productServiceCircuitBreaker`, 50% failure rate / 20-call window / 30s open). 1s connect, 2s read timeout. 404 → empty Optional (legitimate "product gone"); other HTTP errors / timeouts → `ProductCatalogUnavailableException` → 503. DTOs use `@JsonIgnoreProperties(ignoreUnknown)` so the product-service `{success, message, data, errorCode, timestamp}` envelope deserializes cleanly.
- **`CheckoutOrderUseCase`** (`application/`) — new HTTP-edge use case that takes the light client shape (`CheckoutOrderCommand` with `CheckoutLineItem` records: `productId, variantSku, quantity`), resolves each line item against `ProductCatalogPort`, and delegates to the unchanged `CreateOrderUseCase`. `ProductNotFoundException` (inner static class) for missing product/variant → 404.
- **`CheckoutRequest`** + **`OrderItemRequest`** slimmed. Old denormalized fields (`sellerId`, `name`, `unitPriceAmount`, `unitPriceCurrency`, `imageUrl`) deleted. The wire shape now structurally rejects price tampering — there's no field for it.
- **`OrderController`** wired to `CheckoutOrderUseCase` instead of `CreateOrderUseCase`. The unchanged `CreateOrderUseCase` is still present as the domain-edge use case (idempotency, inventory reservation, payment request, shipping request, event publish) — `CheckoutOrderUseCase` is just the HTTP-shape adapter.
- **`ApiExceptionHandler`** got two new mappings: `ProductNotFoundException` → 404, `ProductCatalogUnavailableException` → 503.
- **`UseCaseConfig`** wires the new bean.
- **`CheckoutOrderUseCaseTest`** — 6 new unit tests, in-memory fakes, locks the contract:
  1. *Catalog price wins.* Authoritative price from the catalog ends up on the OrderItem regardless of any client input.
  2. *Default variant when sku omitted.* Client doesn't have to know SKUs for single-variant products.
  3. *404 on missing product.* Unknown productId rejected.
  4. *404 on missing variant.* Naming a variant the catalog doesn't have rejected.
  5. *Empty items list rejected.*
  6. *Multi-seller grouping preserved.* Items spanning two sellers split into two SubOrders.

### FE
- **`fe/src/app/lib/api/endpoints/orders.ts:placeOrder`** signature changed:
  ```diff
  - items: { productId: string; quantity: number }[]
  - addressId?: number
  - paymentMethod: PaymentMethod  // was required
  + items: { productId: string; variantSku?: string; quantity: number }[]
  + shippingAddress: { street, ward?, district, city }
  + paymentMethod?: PaymentMethod  // optional now (BE defaults to COD)
  ```
- **`fe/src/app/pages/checkout/CheckoutPage.tsx`** rebuilds the body from `addresses[selectedAddressIndex]` (the only place that consumed `addressId` was here, and CheckoutPage already had the resolved address object).
- **`fe/e2e/day-simulation.spec.ts`** stripped of the BE-shape composition workaround that pt9 had to add. The buyer flow now sends `{productId, quantity}` only — proves the contract end-to-end.

## Operational gotchas (durable rules — additions to pt5/pt6/pt7/pt8/pt9)

27. **`docker compose restart <svc>` does NOT rebuild from source.** It restarts the runtime against the existing container's image. After editing Java sources you need `docker compose up -d --build <svc>` (or rebuild + recreate). Pt10 burned ~5 min on this — the new code compiled, `mvnw test` passed, but the container was running the old JAR until the rebuild. Symptom: validation errors in container logs that don't match the current `*Request.java`.

28. **Schema-drift fixes shouldn't compose the BE shape on the FE — fix the BE to accept the light shape.** Pt9 day-simulation worked around `POST /orders` schema drift by composing the denormalized shape from product detail. That's the wrong shape — gives the client a field for `unitPriceAmount` that no honest UX needs. Pt10 deleted those wire fields entirely; the BE now structurally cannot accept a client-set price. The general rule: when the FE has to look up details to fill out a request body, that's a smell — the BE should look them up itself from a stable identifier.

29. **Service ports drift between `application.yml` defaults and the actual exposed port.** Pt10 set `http://product-service:8083` based on the impression it was port 8083; actual is 8082. Both `docker port <container>` and `grep "ports:" docker-compose.yml` are faster than reading the source. When wiring a new internal HTTP client, validate the URL by calling the health endpoint from a sibling container (`docker exec vnshop-order-service curl http://product-service:8082/actuator/health`) before assuming it's correct.

30. **Wrap external-service DTOs in `@JsonIgnoreProperties(ignoreUnknown = true)`.** ApiResponse-style envelopes (`{success, message, data, errorCode, timestamp}`) are common across this codebase. An adapter that only models `data` will fail Jackson strict-mode parsing on `success` / `message` / etc. Default to permissive deserialization for inbound external responses; reserve strict modes for inbound *client* requests (where strict-mode is a security boundary, like the `OrderItemRequest` on the wire).

## Test inventory after this block

- Playwright e2e: 47/47 in 11 files. Day-simulation buyer flow's place-order step now proves the light wire shape works end-to-end against the real stack.
- Vitest: 143/143 in 23 files.
- BE: 12 services green. order-service is now 66/66 (60 → 66 with the new `CheckoutOrderUseCaseTest`).

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `274a5035`. Working tree should still show only `M .gitignore` + `?? opencode.jsonc` (pt6 carry-over editor cruft, predates pt7).
2. **Verify the security fix held.** `npx playwright test e2e/day-simulation.spec.ts --project=chromium` from `fe/`. The buyer flow sends the light shape; if the BE accepts it, the contract is proven. If `POST /orders` 400s with "items[0].name must not be blank", someone shipped a regression that re-introduced the denormalized fields on `OrderItemRequest`.
3. **Smoke the new circuit breaker.** Stop product-service (`docker compose stop product-service`), try `POST /orders` from any client; should get 503 (`PRODUCT_CATALOG_UNAVAILABLE`) within ~1-2s, not a hung thread. Restore (`docker compose start product-service`).

## What's still missing (deferred — pt10 → pt11)

- **`POST /checkout/calculate` schema drift.** Same class as the closed `/orders` drift but lower priority — no React code path calls it today. When someone does wire it, the cheapest fix is to give it the same `CheckoutOrderUseCase`-style normalization (light client shape → BE looks up the rest). Sub-1hr including a small test.
- **PayPal capture round-trip.** Smart Buttons render, BE OAuth + create + capture is unit-tested, but no human has driven the FE → sandbox PayPal popup → `/payment/paypal/capture` round-trip. Last unproven payment path. **Needs you at the browser.**
- **Order-list read-model projection latency under load.** Pt9's `concurrency=3` mitigation gives parallelism across the 5 `order.*` topics but not within a single topic (each has 1 partition). If the day-simulation poll loop ever needs more than 1-2 attempts to find a fresh order, bump partition counts on those topics in `infra/kafka/topics-*.yml` (or equivalent — wherever topics are declared).
- **OneDrive durability.** `npm run test:e2e` self-heals via the pretest hook; `npx playwright test` direct invocation still vulnerable.

## Resume hint

Next session: **drive the PayPal capture round-trip in the browser.** It's the last unproven payment path and the only remaining headline item that needs human-at-keyboard time. After that, the deferred queue is cosmetic — `/checkout/calculate` is a code-quality win, OneDrive is operational.

The pt9 day-simulation pattern + this pt10 BE-side normalization together establish a durable rule that's worth restating: **wire shapes should give the client only what the client legitimately knows**. Anything that affects authoritative state — price, seller routing, status transitions, role assignments — comes from the BE's own lookups, never from the request body. The day-simulation spec validates this by sending the light shape and trusting the BE to fill in the gaps; if a future change leaks denormalized fields back into the wire, the spec will be the gate that catches it.
