# Session handover — 2026-05-19 (pt5: B9 shipping rate quote + B11 messaging WS)

**Last commit (HEAD before this session's commits):** `3dd3be26` (the pt4 handover commit)
**Commits this block:** 5 (since pt4)
**Working tree:** clean. All gates green.

After pt4 closed the deferred-list burndown + cookie-auth migration, this block picked up the two remaining "highest-leverage" items from the pt4 deferred list:

1. **B9 — Live shipping rate quote** (flagged three handovers ago).
2. **B11 — Messaging WebSocket E2E coverage** (pt2 flagged as highest-risk untested path).
3. **Bug fix surfaced by B11** — messaging-service was crashing on startup because its Kafka consumer topic didn't exist; pre-creating it via a new infra script.

## TL;DR

All four gates green at HEAD:
- `node infra/scripts/e2e-day.mjs` → **55/55 PASS** (was 52/52 after pt4, +3 across messaging_ws section)
- `cd fe && npx playwright test` → **19/19 PASS** (unchanged)
- FE typecheck/lint/vitest 143/143/build — clean
- shipping-service tests 27/27, order-service 60/60, all other services unchanged

## What shipped (commits in chronological order)

| # | Commit | What |
|---|---|---|
| 1 | `c5e0f0b1` | feat(shipping-service): /shipping/rate-quotes endpoint with multi-carrier options (B9) |
| 2 | `639b0a87` | feat(order-service): wire /checkout/shipping-options to live rate quotes (B9) |
| 3 | `de1ca5bd` | test(e2e-day): assert live shipping options shape on /checkout (B9) |
| 4 | `add1027c` | infra: pre-create Kafka topics for messaging-service consumers |
| 5 | `f04c0428` | test(e2e-day): add messaging WebSocket scenario (B11) |

## B9 — Live shipping rate quote

Replaces the static "STANDARD only at 30k VND" hard-code with multi-carrier quotes from shipping-service.

### Backend — shipping-service

- `application/QuoteShippingOptionsUseCase.java` — iterates `CarrierGatewayPort` across all carriers in the enum (GHN + GHTK). One carrier outage doesn't strand the buyer; failed carriers are skipped, whatever remains is returned. Empty parcel defaults to 1kg / 30x20x10cm.
- `infrastructure/web/RateQuoteController.java` — `POST /shipping/rate-quotes` endpoint. Body: `{street, ward, district, province, recipientName?, recipientPhone?, parcel?}`. Returns `{options: [{carrier, serviceCode, feeVnd, estimatedDeliveryTime}]}`.
- `infrastructure/carrier/StubCarrierGateway.java` — `quote()` rewritten with deterministic per-carrier pricing. GHN: 30k VND STANDARD (3-5 days). GHTK: 45k VND EXPRESS (1-2 days). Plus 5k/extra-kg surcharge above 1kg. Real GHN/GHTK adapters live behind the same port via `LiveCarrierGateway`.
- 4 new unit tests covering happy path, single-carrier failure (returns remaining), all-carriers failure (empty), and a carrier returning null. Tests now 27/27.

### Backend — order-service

- `application/shipping/ShippingQuotePort.java` + `ShippingOption.java` + `ShippingQuoteRequest.java` — outbound port, domain-clean (no Spring imports).
- `infrastructure/shipping/ShippingServiceQuoteAdapter.java` — RestClient adapter mirroring the user-service stats adapter pattern. 1s connect / 2.5s read timeouts via `JdkClientHttpRequestFactory`. Graceful try/catch returning empty list on any failure (network, 5xx, malformed JSON). **No in-process circuit breaker** — gateway already wraps shipping calls in resilience4j; double-counting failures would skew the breaker budget.
- `CheckoutController.shippingOptions` — calls the port, returns live options when available, falls back to legacy STANDARD-only shape on empty. Buyer never sees a 500 because shipping-service had a hiccup.
- `CheckoutControllerTest` updated to inject the new port (mock).

### Tests

The existing `checkout/POST /checkout/shipping-options` step now asserts the contract: non-empty array, contains STANDARD or EXPRESS method, every option has a numeric/string cost. Both branches (live + degraded) are accepted as valid runtime states.

## B11 — Messaging WebSocket E2E coverage

The `/ws/messaging` path takes the JWT via `?token=` query param (browsers can't set Authorization on `new WebSocket(...)`). pt2 flagged this as the highest-risk untested path; pt4 still had it deferred.

### Tests added

New `messaging_ws` section in `e2e-day.mjs`:

- **Valid token → hello frame.** Connects with the buyer JWT, expects `{type:'hello',userId:string}` within 5s. Proves the gateway pass-through + WsJwtVerifier round-trip works.
- **Missing token → close.** No `?token=`, expects a socket close. Server emits 4401; we accept any close code (some proxies translate 4401 to 1006 TCP reset).
- **Garbage token → close.** Non-JWT string, expects close. A hello frame on this path would be a security regression — the verifier must fail closed.

### Bug surfaced

messaging-service was `Exited (1)` for an unknown duration before this block. Logs showed:

```
KafkaJSProtocolError: This server does not host this topic-partition
  type: 'UNKNOWN_TOPIC_OR_PARTITION', code: 3
```

NestJS's KafkaJS consumer crashes on startup if the topic it declares (`@MessagePattern("messaging.message.sent")`) doesn't exist on the broker — even with `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true`. The consumer-side metadata refresh races topic auto-creation; auto-create only fires from the **producer** path.

### Fix

`infra/scripts/init-kafka-topics.sh` — idempotent script that pre-creates consumer-side topics via `kafka-topics --create --if-not-exists`. Intended to run after `docker compose up -d` and before the first messaging request. Adding more topics (notifications, etc.) is a one-line edit to the `TOPICS=(...)` array.

## What's still missing (deferred — pt5 → pt6)

From the pt4 list, with status:

- ~~B9 — Live shipping rate quote~~ ✅ done
- ~~B11 — Messaging WebSocket E2E~~ ✅ done

Remaining:
- **VNPAY / MOMO IPN with mock provider.** Endpoints exist; intent + IPN never exercised end-to-end. Needs a lightweight mock provider (in-process or sidecar) to drive the IPN handshake without a real PSP. Largest deferred BE flow.
- **Notifications inbox.** notification-service consumes Kafka; no inbox endpoint or FE bell yet. Multi-day: schema + REST + WebSocket push + FE bell.
- **Native password reset / 2FA.** Currently bounce out to Keycloak's account console.
- **Email verification.** `emailVerified: true` set on register — real verification not wired.
- **Public sellers visual polish.** Functional but minimal.
- **Hero/promo/trending CMS.** No BE; HomePage `<ComingSoonCard>` stubs in place.
- **Real GHN/GHTK adapter for shipping rate quote.** B9 shipped the stub + pluggable port; the live adapter scaffolding exists in `LiveCarrierGateway` but needs API key wiring + integration tests.

## Operational gotchas (durable rules — additions to pt4)

The pt4 list still applies. New rules learned this block:

1. **NestJS @MessagePattern consumers crash on missing topic at startup.** `KAFKA_AUTO_CREATE_TOPICS_ENABLE=true` on the broker doesn't help — KafkaJS metadata refresh races auto-create from the **consumer** side. Producer-side auto-create works fine. Always pre-create topics before starting consumer services. `infra/scripts/init-kafka-topics.sh` is the home for this.
2. **Long-running container in `Exited (1)` is invisible to docker compose ps unless you use `-a`.** `docker compose ps` only shows running containers. A crashed service is silently absent. Always check `docker compose ps -a` if a service seems missing.
3. **WS close codes get translated by proxies.** A messaging-service `socket.close(4401, ...)` may surface to the client as code 1006 (abnormal closure) when going through Spring Cloud Gateway's HTTP-Upgrade proxy. E2E tests must accept any close code, not the specific 4401.
4. **Shipping rate-quote should be one-call-per-page, never per-line-item.** The BE quote endpoint accepts a single parcel because the carrier pricing model is per-shipment (weight + destination), not per-product. Future enhancements that want product-level dimensions need to aggregate on the FE/order-service side, not loop the carrier.

## File locations to know (new since pt4)

### Backend (shipping rate quote)
- `services/shipping-service/src/main/java/.../application/QuoteShippingOptionsUseCase.java` — iterates carriers, skips failures
- `services/shipping-service/src/main/java/.../infrastructure/web/RateQuoteController.java` — `POST /shipping/rate-quotes`
- `services/shipping-service/src/main/java/.../infrastructure/carrier/StubCarrierGateway.java` — deterministic per-carrier stub pricing
- `services/order-service/src/main/java/.../application/shipping/ShippingQuotePort.java` — outbound port
- `services/order-service/src/main/java/.../infrastructure/shipping/ShippingServiceQuoteAdapter.java` — RestClient adapter w/ graceful degradation
- `services/order-service/src/main/java/.../infrastructure/web/CheckoutController.java` — wired to live + fallback path

### Infra
- `infra/scripts/init-kafka-topics.sh` — pre-create consumer-side Kafka topics

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `f04c0428`. Working tree clean.
2. **Inspect the diff** — 23 total commits since `1986222a` (pt3 -> pt5). 5 new since pt4.
3. **Run the gates** (stack must be up + seeded; **must run init-kafka-topics.sh** before messaging-service starts):
   ```bash
   docker compose --profile apps up -d
   bash infra/scripts/setup-keycloak-admin-client.sh
   bash infra/scripts/init-kafka-topics.sh        # NEW — required for messaging-service
   node infra/scripts/seed-demo.mjs
   node infra/scripts/e2e-day.mjs                 # 55/55
   cd fe && npx playwright test                   # 19/19
   ```
4. **Pick from the deferred list.** Top-leverage items remaining:
   - **VNPAY / MOMO IPN with mock provider** — largest deferred BE flow.
   - **Notifications inbox** — multi-day, but visible UX win.
   - **Real GHN/GHTK adapter** — finishes B9.
   - **Native password reset** — closes the last Keycloak-chrome leak.

## Final tally

- **Started this block:** API E2E 52/52, Playwright 19/19 at HEAD `3dd3be26`.
- **Ended:** API E2E 55/55 (+3 — messaging_ws), Playwright 19/19 (unchanged).
- **Commits:** 5 (B9: 3, B11: 1, infra fix: 1).
- **Diff vs pt4 docs commit:** ~+580 / -10 across 11 files.
- **Bugs found + fixed:** 1 (messaging-service crashing because topic missing).
- **Production characteristics added:** live multi-carrier shipping rate quotes; consumer-topic pre-create script.
- **Deferred items closed:** 2 (B9, B11).
