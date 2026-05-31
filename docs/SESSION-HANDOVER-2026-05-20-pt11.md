# Session handover — 2026-05-20 (pt11: /checkout/calculate normalization + Kafka partition bump)

**Last commit (HEAD):** `44a41e4` (`fix(infra): bump order.* topic partitions to 3 for projection parallelism`)
**Commits since pt10 HEAD `672d7aa4`:** 2.
**Gates:**
- Playwright: **47/47 pass** (38 pre-existing + 9 day-simulation, including buyer flow exercising both `POST /orders` and `POST /checkout/calculate` with the light shape).
- FE typecheck: 0. Vitest: **143/143**.
- BE: order-service **71/71** (was 66 before the calculate refactor, +5 new `CalculateCheckoutUseCaseTest`). All 12 services green.

This block closed two pt10 deferred items: the matching `/checkout/calculate` schema-drift fix and the Kafka partition bump that lets the pt9 projection-concurrency mitigation actually parallelise within each topic.

## TL;DR

- `POST /checkout/calculate` now mirrors the `POST /orders` light shape: client sends `{items:[{productId, variantSku?, quantity}], couponCode?}`, BE resolves authoritative price from product-service. Same security boundary, same `ProductCatalogPort`, same `ProductNotFoundException`/`ProductCatalogUnavailable` mapping. Day-simulation buyer flow now exercises the endpoint and asserts `itemsTotal > 0` — the pt9 "skip-with-finding" annotation deleted, finding closed.
- `order.created`, `order.updated`, `order.paid`, `order.shipped`, `order.cancelled` topics bumped from 1 partition to 3. The pt9 `OrderProjectionListener` `concurrency=3` setting now actually distributes work: each of the 3 listener threads gets its own partition per topic. Verified post-restart by partition-assignment logs (`partitions assigned [order.created-0,…0]`, `[order.created-1,…1]`, `[order.created-2,…2]`). `init-kafka-topics.sh` updated to declare per-topic partition counts and gained a `BUMP_PARTITIONS=1` mode that runs `--alter` monotonically (only ever adds partitions, never removes).

## Commits this block

| # | Commit | What |
|---|---|---|
| 1 | `edaa50d9` | fix(order, fe): BE-side normalize POST /checkout/calculate |
| 2 | `44a41e4` | fix(infra): bump order.* topic partitions to 3 for projection parallelism |

## What shipped

### `edaa50d9` — `/checkout/calculate` normalization

BE — order-service:
- **`CalculateCheckoutUseCase`** grew a second `calculate(List<CheckoutLineItem>)` overload that reuses the existing `ProductCatalogPort`. The legacy `calculate(cartId)` path is preserved for any caller still depending on it (cart-snapshot summarisation works the same way as before).
- **`CalculateCheckoutRequest`** slimmed: dropped `cartId` and `shippingAddress`, kept `items` + `couponCode`. Same `OrderItemRequest` from the `/orders` path is reused, so the wire shape is consistent across both endpoints.
- **`CheckoutController.calculate`** dispatches to the new line-items overload via `request.toLineItems()`.
- **`UseCaseConfig`** wires `ProductCatalogPort` into the use-case bean.
- **`CalculateCheckoutUseCaseTest`** — 5 new tests covering both paths: cart-snapshot legacy path still sums correctly, line-items path resolves authoritative price from catalog, default variant when sku omitted, missing product → 404, empty list → 400.

FE:
- **`CheckoutCalculateInput`** dropped `addressId`, added optional `variantSku`. Mirrors `PlaceOrderInput`.
- **`day-simulation.spec.ts`** buyer flow now actually calls the endpoint and asserts `itemsTotal > 0`. Pt9 had to skip this with an annotation; the pt9 finding is closed.

### `44a41e4` — Kafka partition bump

- `init-kafka-topics.sh` now uses `name:partitions` entries. Order topics declared at 3 partitions; messaging.message.sent stays at 1.
- `BUMP_PARTITIONS=1` mode runs `kafka-topics --alter --partitions N` on existing topics when the current count is below the declared count. Only ever grows; never removes (Kafka can't remove partitions safely anyway).
- Applied live to the running stack: all 5 `order.*` topics confirmed at PartitionCount=3. Order-service rebalanced cleanly on restart and the projection consumer now spans all 3 partitions per topic across its 3 listener threads.

## Operational gotchas (additions to pt5/pt6/pt7/pt8/pt9/pt10)

31. **`@KafkaListener(concurrency=N)` is a no-op without N partitions per topic.** Pt9 set concurrency=3 thinking it would parallelise within each topic. It didn't — each topic had 1 partition, so 3 listener threads still serialised behind a single broker queue. The Kafka rule: parallelism within a consumer group is bounded by `partitions × topics`. Pt11 gave each `order.*` topic 3 partitions; the partition-assignment logs after restart now show one listener thread per partition (`order.created-0` / `order.created-1` / `order.created-2`).

32. **Bumping Kafka partitions on a live topic is safe but ordering-sensitive.** `kafka-topics --alter --partitions N` only grows partition counts; existing message keys keep their hash → partition mapping if and only if the existing partitions stay at the same indices (they do). The risk is *future* messages with a key whose hash crosses a partition boundary — they'll land on a different partition than historical messages with the same key, breaking per-key ordering. For `order.*` events keyed by `orderId`, that's fine: each order's events were always already in a single partition's stream, and the keyspace doesn't get reshuffled for already-emitted events. The only harm scenario is consumers that assume strict global ordering across the whole topic, which is rare and explicitly not the case for `OrderProjectionListener`.

## Test inventory after this block

- Playwright e2e: 47/47 in 11 files. `day-simulation.spec.ts` now exercises both `POST /orders` and `POST /checkout/calculate` with the light wire shape — the regression gate for FE↔BE schema drift.
- Vitest: 143/143 in 23 files.
- BE: 12 services green. order-service is now 71/71 (66 → 71 with the new `CalculateCheckoutUseCaseTest`).

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `44a41e4`. Working tree should still show only `M .gitignore` + `?? opencode.jsonc` (pt6 carry-over editor cruft).
2. **Smoke the security fix.** `npx playwright test e2e/day-simulation.spec.ts --project=chromium` — 9/9 in ~15s. The buyer flow's `POST /checkout/calculate` and `POST /orders` both send the light shape and prove the BE resolves price authoritatively.
3. **Verify Kafka partition assignment.** `docker logs vnshop-order-service --since 1m | grep "partitions assigned"` should show three lines per topic, one per listener thread (e.g. `order.created-0` on container#1-0, `order.created-1` on container#1-1, `order.created-2` on container#1-2).

## What's still missing (deferred — pt11 → pt12)

**Genuinely open code work:** None blocking. The pt9 deferred queue is now drained — schema drift closed on both endpoints, projection lag mitigated end-to-end (concurrency × partitions both at 3).

**Genuinely open operational work:**
- **PayPal capture round-trip.** Smart Buttons render, BE OAuth + create + capture is unit-tested, but no human has driven the FE → sandbox PayPal popup → `/payment/paypal/capture` round-trip. **Last unproven payment path. Needs you at the browser.**
- **OneDrive durability.** `npm run test:e2e` self-heals via the pretest hook; `npx playwright test` direct invocation still vulnerable. Long-term cure is `attrib +P fe/e2e/*.spec.ts` once after every clone, or moving the repo out of OneDrive entirely.

## Resume hint

Next session: **drive the PayPal capture round-trip in the browser.** It's the only headline item that legitimately needs human-at-keyboard time. After that, the deferred queue is purely operational (OneDrive). The day-simulation spec is now the durable integration gate — every PR that touches FE endpoint modules, BE `*Request` records, or `OrderItem` shape should re-run it.

The pt10 + pt11 normalization pattern (light client wire shape, BE-side resolution from authoritative ports, structural rejection of client-supplied prices) is now consistent across both `/orders` and `/checkout/calculate`. If a third checkout-adjacent endpoint shows up later, the same `ProductCatalogPort` + `OrderItemRequest` shapes are reusable verbatim — the contract is set.
