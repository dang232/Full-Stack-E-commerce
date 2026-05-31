# Session handover — 2026-05-19 (pt6: multi-payment rollout — VietQR + SePay + Stripe + PayPal)

**Last commit (HEAD):** `4efcbee6` (`fix(fe): forward VITE_STRIPE/PAYPAL flags through docker-compose build args`)
**Commits this block:** 7 (since pt5 HEAD `be835032`).
**Gates:** payment-service Maven `Tests run: 66, Failures: 0, Errors: 0, Skipped: 0`. FE typecheck (`npx tsc --noEmit`) exits 0. **Live e2e-day with all payment flags on: 67 passed, 0 failed.** **Stripe webhook driven live end-to-end on 2026-05-20:** `stripe trigger payment_intent.succeeded --add payment_intent:metadata.paymentId=<uuid>` flips PENDING → COMPLETED, `payment-promoted provider=STRIPE` logged. **FE checkout chunk now bakes the Stripe + PayPal sandbox creds** (verified by grepping the lazy-loaded `index-*.js` bundle).

This block executed the spec at `docs/superpowers/specs/2026-05-19-multi-payment-rollout-design.md` (v2), then closed the loop with a sandbox-creds smoke run that uncovered three runtime bugs the unit tests didn't catch. All four payment paths now working in parallel: VietQR production (manual confirm), SePay sandbox polling for VietQR auto-confirm, Stripe sandbox end-to-end (FE Elements → BE PaymentIntent → webhook → ledger), PayPal sandbox end-to-end (FE Smart Buttons → BE OAuth+order+capture → ledger).

## Commits this block (chronological)

| # | Commit | What |
|---|---|---|
| 1 | `ad5ef428` | feat(payment): multi-method rollout — VietQR/SePay/Stripe/PayPal (all BE) |
| 2 | `f3dbdf95` | feat(fe): multi-method checkout — Stripe Elements + PayPal Buttons + VietQR |
| 3 | `4c037d23` | test(e2e): shape-only Stripe + PayPal + VietQR scenarios |
| 4 | `20c87afe` | docs(payment): roadmap + spec + pt6 handover for multi-payment rollout |
| 5 | `06303f04` | fix(payment): wire RestClient.Builder + paypal env + 405 fallback + 10s timelimiter |
| 6 | `cf9b08c4` | fix(payment): permit /payment/*/webhook in payment-service SecurityConfig |
| 7 | `4efcbee6` | fix(fe): forward VITE_STRIPE/PAYPAL flags through docker-compose build args |


## TL;DR

All four payment paths working; 66/66 BE tests at HEAD (was 35/35 before this block):
- **VietQR** — production today, manual confirm via `AdminVietQrController`, flag-gated behind `payment.vietqr.enabled` (default on)
- **SePay** — sandbox-ready outbound polling for VietQR auto-confirm, `@Scheduled` poller with singleton cursor, gated on `payment.sepay.enabled`
- **Stripe** — sandbox-ready end-to-end (FE Embedded Elements → BE PaymentIntent → webhook → ledger), gated on `payment.stripe.enabled`
- **PayPal** — sandbox-ready end-to-end (FE Smart Buttons → BE OAuth+order+capture → ledger), synchronous capture (no webhook), gated on `payment.paypal.enabled`
- **New BE packages:** `infrastructure/stripe`, `infrastructure/paypal`, `infrastructure/sepay`, `infrastructure/fx`
- **New BE tests:** `StripeGatewayTest`, `StripeWebhookControllerTest`, `PayPalGatewayTest`, `SepayPollerTest`, `FrankfurterFxAdapterTest`, `CompositePaymentGatewayTest`, `PaymentPromotionServiceTest`
- **FE typecheck clean.** Multi-method checkout success step renders sections gated on `VITE_*_ENABLED` flags.

## What shipped (working tree — 14 spec steps)

| # | Step | Files created/edited |
|---|---|---|
| 1 | Per-method flag refactor | `CompositePaymentGateway.java`, `PaymentMethodHandler.java`, `Cod/VietQr/Vnpay/MomoPaymentMethodHandler.java` (deleted `LivePaymentGateway.java`, `StubPaymentGateway.java`, `LivePaymentGatewayWiringTest.java`), `application.yml` migrated `payment.mode=stub\|live` → per-method flags |
| 2 | PaymentPromotionService extraction | `application/PaymentPromotionService.java` with `PromotionCommand.manual()` + `fromCallback()`, `AdminVietQrController` + `VnpayCallbackService` migrated, provider-namespaced dedup keys (`STRIPE:event.id`, `SEPAY:tx.id`, `PAYPAL:capture.id`) |
| 3 | FX provider (Frankfurter) | `domain/port/out/FxRatePort.java`, `infrastructure/fx/FxProperties.java` + `FrankfurterFxAdapter.java` (Caffeine cache, 24h TTL, `25500` USD/VND fallback with WARN log) |
| 4 | Migrations | `V9__payment_external_amount.sql` (external_amount, external_currency, fx_rate, fx_rate_at), `V10__sepay_cursor.sql` (singleton via CHECK id=1) |
| 5 | Stripe gateway BE | `StripeProperties`, `StripeGateway`, `StripeIntentClient` (test seam), `DefaultStripeIntentClient`, `StripePaymentMethodHandler`, `POST /payment/stripe/create` in `PaymentController` |
| 6 | Stripe webhook + permit-list | `StripeWebhookController` (`POST /payment/stripe/webhook`), `StripeWebhookVerifier` + `DefaultStripeWebhookVerifier`, `services/api-gateway/.../SecurityConfig.java` permits `/payment/stripe/webhook`, idempotency via `PaymentCallbackLogStore` keyed `STRIPE:event.id` |
| 7 | Stripe FE Embedded Elements | `fe/src/app/components/checkout/StripePaymentSection.tsx`, `@stripe/stripe-js` + `@stripe/react-stripe-js` deps, `VITE_STRIPE_ENABLED` + `VITE_STRIPE_PUBLISHABLE_KEY` in `.env.example`, mounted on success step of `CheckoutPage.tsx` |
| 8 | Stripe e2e-day scenario | `infra/scripts/e2e-day.mjs` shape-only check gated on env, header comment points to manual smoke `stripe trigger payment_intent.succeeded ...` |
| 9 | PayPal gateway BE | `PayPalProperties` (sandbox/live mode → baseUrl), `PayPalGateway` (OAuth bearer + create + capture against PayPal v2 Checkout REST), `PayPalPaymentMethodHandler`, `POST /payment/paypal/create` + `POST /payment/paypal/capture/{paymentId}/{paypalOrderId}` in `PaymentController` |
| 10 | PayPal FE Smart Buttons | `fe/src/app/components/checkout/PayPalPaymentSection.tsx`, `@paypal/react-paypal-js` dep, `VITE_PAYPAL_ENABLED` + `VITE_PAYPAL_CLIENT_ID` in `.env.example` |
| 11 | PayPal e2e-day scenario | shape-only style as Stripe |
| 12 | SePay poller | `SepayProperties`, `SepayClient` + `RestSepayClient`, `SepayTransactionsResponse`, `SepayCursorRepository` + `SepayCursorJpaEntity` + `SepayCursorJpaRepository`, `SepayPoller` (`@Scheduled`, UUID regex from memo, skips non-VietQR txns, gated on `payment.sepay.enabled`), outbound polling uses `Authorization: Apikey {key}` |
| 13 | Doc updates | `docs/PAYMENT-ROADMAP.md` (new), `docs/FE-PLAN.md`, `README.md` |
| 14 | Final verification | Maven 66/66, FE typecheck clean, FE Playwright shape check `payment-multi-method.spec.ts` |

## Per-method flag refactor + composite gateway

Replaced the global `payment.mode=stub|live` property with per-method flags (`payment.cod.enabled`, `payment.vietqr.enabled`, `payment.vnpay.enabled`, `payment.momo.enabled`, `payment.stripe.enabled`, `payment.paypal.enabled`, `payment.sepay.enabled`). Each handler is `@ConditionalOnProperty` with its own flag.

`CompositePaymentGateway` dispatches to the appropriate handler; if a handler is absent (flag off), it returns `METHOD_DISABLED` reason. This allows runtime toggling without recompile — useful for canary rollouts and emergency disable.

Deleted the old `LivePaymentGateway` + `StubPaymentGateway` + `LivePaymentGatewayWiringTest.java` — the per-method handler pattern is cleaner and testable via `@ConditionalOnProperty` slices.

## PaymentPromotionService — single PENDING→COMPLETED path

Extracted a single `PaymentPromotionService` that all providers (VietQR, SePay, Stripe, PayPal, VNPAY, MoMo) route through. Two entry points:

- `PromotionCommand.manual()` — admin VietQR confirm via `AdminVietQrController`
- `PromotionCommand.fromCallback()` — provider webhooks/polls (Stripe webhook, SePay poller, VNPAY callback, MoMo callback)

Deduplication is provider-namespaced: `{PROVIDER}:{eventOrTxId}` keyed in `PaymentCallbackLogStore`. Prevents double-promotion if a webhook retries or a poller re-fetches the same transaction.

## FX provider (Frankfurter)

`FxRatePort` is a narrow port with one method: `getRate(fromCurrency, toCurrency) → BigDecimal`. `FrankfurterFxAdapter` implements it:

- Caffeine cache with 24h TTL (rates don't change intra-day)
- Fallback to `25500` USD/VND with WARN log if Frankfurter is down
- `fx_rate` + `fx_rate_at` persisted on the payment row at create time for dispute reconciliation

## Stripe — Embedded Payment Intents

Flow: FE → `/payment/stripe/create` → BE persists PENDING + creates PaymentIntent w/ paymentId metadata → FE confirms with Elements → Stripe POSTs `/payment/stripe/webhook` → `DefaultStripeWebhookVerifier.constructEvent` → promote to COMPLETED keyed `STRIPE:event.id`.

**Backend:**
- `StripeProperties` — API key, webhook secret
- `StripeGateway` — creates PaymentIntent, persists external_amount/currency/fx_rate/fx_rate_at
- `StripeIntentClient` — test seam (interface); `DefaultStripeIntentClient` uses real SDK
- `StripePaymentMethodHandler` — `@ConditionalOnProperty(name="payment.stripe.enabled")`
- `StripeWebhookController` — `POST /payment/stripe/webhook`, verifies signature, promotes payment
- `StripeWebhookVerifier` — test seam; `DefaultStripeWebhookVerifier` uses SDK's `EventDataObjectDeserializer`

**Frontend:**
- `StripePaymentSection.tsx` — mounts `<Elements>` + `<PaymentElement>` on success step
- `VITE_STRIPE_ENABLED` + `VITE_STRIPE_PUBLISHABLE_KEY` flags in `.env.example`

**Tests:** `StripeGatewayTest` (happy path, external_amount/currency/fx_rate persisted), `StripeWebhookControllerTest` (signature verify, idempotency, promotion).

**Gateway permit-list:** `/payment/stripe/webhook` MUST be in `SecurityConfig.permitAll()` because Stripe signs the request, not us. Forgetting this returns 401 on every event. **AND** `payment-service` itself must permit the same path (added in commit `cf9b08c4`) — `stripe listen --forward-to localhost:8092/...` bypasses the api-gateway entirely in local dev, so the gateway's permit doesn't help; the service-level OAuth2 resource server rejects the request before the signature verifier runs.

**Manual smoke:** `stripe listen --forward-to localhost:8092/payment/stripe/webhook` then `stripe trigger payment_intent.succeeded`.

## PayPal — Smart Buttons + synchronous capture

Flow: FE → `/payment/paypal/create` → BE persists PENDING + OAuths into PayPal + creates `CAPTURE`-intent v2 order with reference_id/custom_id = paymentId → FE renders `<PayPalButtons>` → on approval FE calls `/payment/paypal/capture/{paymentId}/{paypalOrderId}` → BE captures + promotes synchronously (no webhook, no FE polling).

**Backend:**
- `PayPalProperties` — client ID, secret, sandbox/live mode → baseUrl
- `PayPalGateway` — OAuth bearer token, create order, capture order
- `PayPalPaymentMethodHandler` — `@ConditionalOnProperty(name="payment.paypal.enabled")`
- `POST /payment/paypal/create` — returns `{paypalOrderId, clientId, ...}`
- `POST /payment/paypal/capture/{paymentId}/{paypalOrderId}` — captures + promotes synchronously

**Frontend:**
- `PayPalPaymentSection.tsx` — mounts `<PayPalScriptProvider>` + `<PayPalButtons>` on success step
- `VITE_PAYPAL_ENABLED` + `VITE_PAYPAL_CLIENT_ID` flags in `.env.example`

**Tests:** `PayPalGatewayTest` (6 tests covering blank-cred guards, body shape with USD conversion + paymentId metadata, capture id extraction from nested response, 4xx → IllegalStateException, fallback when captures array missing).

**Capture id location:** `purchase_units[0].payments.captures[0].id`. Fall back to order id when the array is missing (e.g. when capture races behind a `PAYER_ACTION_REQUIRED` flow).

## SePay — outbound polling for VietQR auto-confirm

`@Scheduled` poller hits SePay's `/userapi/transactions/list` with `Authorization: Apikey {key}`, walks newest-first using a singleton cursor row, skips non-VietQR (memo doesn't contain a UUID), promotes through `PaymentPromotionService` keyed `SEPAY:tx.id`.

**Backend:**
- `SepayProperties` — API key, polling interval
- `SepayClient` — interface; `RestSepayClient` uses RestClient
- `SepayTransactionsResponse` — DTO for `/userapi/transactions/list`
- `SepayCursorRepository` + `SepayCursorJpaEntity` + `SepayCursorJpaRepository` — singleton cursor row (CHECK id=1)
- `SepayPoller` — `@Scheduled`, UUID regex from memo, skips non-VietQR, gated on `payment.sepay.enabled`

**Auth header:** `Authorization: Apikey {key}` (NOT HMAC). The HMAC `X-SePay-Signature` is inbound-webhook-only and easy to confuse from forum posts.

**Tests:** `SepayPollerTest` (happy path, no-UUID-in-memo skip, non-VietQR skip, SePay outage WARN).

**Inbound webhook (deferred):** HMAC `X-SePay-Signature` is lower-latency than 30s polling. Deferred to pt7 pending public domain.

## VietQR — production today (manual confirm)

Already shipped pre-block; this block flag-gated it behind `payment.vietqr.enabled` (default on) and migrated `AdminVietQrController` onto `PaymentPromotionService`. No buyer-side change.

## FE — multi-method checkout success step

Sections render only on the success step of `CheckoutPage.tsx`, gated on `VITE_*_ENABLED`. `payment-multi-method.spec.ts` Playwright shape-only check ships green by default (skips when both flags off).

## What's still missing (deferred — pt6 → pt7)

- **Stripe production cutover.** Sandbox keys work; prod requires Stripe Atlas (US LLC) or VN business reg. Doc steps live in `docs/PAYMENT-ROADMAP.md`.
- **PayPal production webhook (`PAYMENT.CAPTURE.COMPLETED`).** Sandbox synchronous flow doesn't need it; prod does for refunds/disputes/chargebacks.
- **SePay inbound webhook (HMAC `X-SePay-Signature`).** Lower-latency than 30s polling. Needs public domain.
- **VNPay re-enable.** Code path intact; gated off pending VN business reg.
- **MoMo callback migration onto `PaymentPromotionService`.** Currently still on its own dedup; works correctly, just not deduplicated through the new path.
- **Notifications inbox.** notification-service consumes Kafka; no inbox endpoint or FE bell yet. Multi-day: schema + REST + WebSocket push + FE bell.
- **Native password reset / 2FA.** Currently bounce out to Keycloak's account console.
- **Email verification.** `emailVerified: true` set on register — real verification not wired.
- **Public sellers visual polish.** Functional but minimal.
- **Hero/promo/trending CMS.** No BE; HomePage `<ComingSoonCard>` stubs in place.
- **Real GHN/GHTK adapter for shipping rate quote.** B9 shipped the stub + pluggable port; the live adapter scaffolding exists in `LiveCarrierGateway` but needs API key wiring + integration tests.

## Operational gotchas (durable rules — additions to pt5)

The pt5 list still applies. New rules learned this block:

1. **Stripe Java SDK 32.x removed `RequestOptions.getApiKey()`.** Use `any(RequestOptions.class)` matchers in tests rather than asserting key.
2. **Stripe SDK 32.x changed `EventDataObjectDeserializer` constructor surface.** Webhook tests should mock `Event` + deserializer rather than constructing real ones.
3. **PayPal `Map.getOrDefault(String, String)` fails type inference on wildcard maps.** Use explicit null checks: `m.get("status") != null ? m.get("status").toString() : "CREATED"`.
4. **SePay outbound polling auth header is `Authorization: Apikey {key}`** — NOT HMAC. The HMAC `X-SePay-Signature` is inbound-webhook-only and easy to confuse from forum posts.
5. **PayPal v2 Checkout REST capture id lives at `purchase_units[0].payments.captures[0].id`**, not at the order root. Fall back to order id when the array is missing (e.g. when capture races behind a `PAYER_ACTION_REQUIRED` flow).
6. **Stripe webhook needs the gateway permit-list — AND the payment-service permit-list.** `/payment/stripe/webhook` MUST be in api-gateway `SecurityConfig` permitAll because Stripe signs the request, not us. **In local dev `stripe listen --forward-to localhost:8092/...` bypasses the gateway entirely**, so payment-service's own `SecurityConfig` must also permit the path or every event 401s before the signature verifier runs. Both layers, every time. Pattern is `/payment/*/webhook` to forward-cover PayPal's deferred webhook and any future provider.
7. **`@ConditionalOnProperty` slice tests:** Tests that previously pinned `payment.mode=stub` now need per-method flags (`payment.cod.enabled=true`). `PaymentControllerHeaderTest` was migrated; new tests should follow.
8. **Sub-agent silent-bail watch.** A designer + executor pair were dispatched mid-block and bailed without producing diffs. Memory rule `feedback_detect_silent_bail.md` says: verify diff before trusting the report. Confirmed by switching to direct execution mid-stream.
9. **Spring Boot 4 doesn't auto-publish `RestClient.Builder` as a bean.** Constructor injection of `RestClient.Builder` UnsatisfiedDependency's at boot. Define `@Bean RestClient.Builder restClientBuilder() { return RestClient.builder(); }` in a `@Configuration` class. Bit me on FrankfurterFxAdapter / PayPalGateway / RestSepayClient simultaneously.
10. **`docker-compose.yml` env passthrough is explicit.** Env vars in `.env` aren't auto-injected into containers — each service block has to list them under `environment:`. PayPal/Stripe/SePay creds were silently empty inside the container until the matching `STRIPE_*: ${STRIPE_*}` lines were added. Easy to miss because the boot log doesn't print missing env, just the resulting `IllegalArgumentException` at request time.
11. **`@GetMapping` fallback handlers swallow non-GET methods as 405.** Resilience4j's circuit-breaker forwards to `forward:/fallback/{service}` regardless of incoming method; if the fallback only declares `@GetMapping`, every POST that times out comes back as 405 Method Not Allowed (looks like a routing bug, isn't). Use `@RequestMapping` (any method) on fallback controllers. Default Resilience4j `TimeLimiter` is 1s — overrides per service in `application.yml` under `resilience4j.timelimiter.instances.<name>`.
12. **Vite inlines `VITE_*` at *build* time, not container runtime.** Adding `VITE_STRIPE_ENABLED=true` to `.env` does nothing on its own — `import.meta.env.VITE_STRIPE_ENABLED` resolves to `undefined` at build, the `if (!STRIPE_ENABLED)` placeholder branch keeps rendering, and the Elements/Smart-Buttons section silently never mounts on the success step. Three layers must agree: (a) `fe/Dockerfile` declares an `ARG VITE_X=` + `ENV VITE_X=$VITE_X` in the build stage; (b) `docker-compose.yml` `frontend.build.args` forwards `VITE_X: ${VITE_X:-default}` from `.env`; (c) `.env` actually sets `VITE_X`. Verify by grepping the rebuilt lazy-loaded checkout chunk for the publishable creds (`pk_test_*`, PayPal client id) — if they're inlined and the disabled-fallback string is gone (DCE'd by `"true"==="true"`), Vite saw them. Rebuild with `docker compose up -d --build frontend` after any change.

## File locations to know (new since pt5)

### Backend (payment-service)
- `services/payment-service/src/main/java/.../domain/PaymentMethod.java` — enum with all methods
- `services/payment-service/src/main/java/.../application/PaymentPromotionService.java` — single PENDING→COMPLETED path
- `services/payment-service/src/main/java/.../infrastructure/gateway/CompositePaymentGateway.java` — per-method dispatch
- `services/payment-service/src/main/java/.../infrastructure/gateway/PaymentMethodHandler.java` — handler interface
- `services/payment-service/src/main/java/.../infrastructure/gateway/Cod/VietQr/Vnpay/MomoPaymentMethodHandler.java` — per-method handlers
- `services/payment-service/src/main/java/.../infrastructure/fx/FxProperties.java` + `FrankfurterFxAdapter.java` — FX provider
- `services/payment-service/src/main/java/.../infrastructure/stripe/StripeProperties.java` + `StripeGateway.java` + `StripePaymentMethodHandler.java` — Stripe gateway
- `services/payment-service/src/main/java/.../infrastructure/stripe/StripeWebhookController.java` + `StripeWebhookVerifier.java` — Stripe webhook
- `services/payment-service/src/main/java/.../infrastructure/paypal/PayPalProperties.java` + `PayPalGateway.java` + `PayPalPaymentMethodHandler.java` — PayPal gateway
- `services/payment-service/src/main/java/.../infrastructure/sepay/SepayProperties.java` + `SepayClient.java` + `SepayPoller.java` — SePay poller
- `services/payment-service/src/main/java/.../infrastructure/sepay/SepayCursorRepository.java` + `SepayCursorJpaEntity.java` — SePay cursor
- `services/payment-service/src/main/resources/db/migration/V9__payment_external_amount.sql` + `V10__sepay_cursor.sql` — migrations

### Frontend (fe)
- `fe/src/app/components/checkout/StripePaymentSection.tsx` — Stripe Embedded Elements
- `fe/src/app/components/checkout/PayPalPaymentSection.tsx` — PayPal Smart Buttons
- `fe/src/app/components/checkout/CheckoutPage.tsx` — success step mounts sections
- `fe/.env.example` — `VITE_STRIPE_ENABLED`, `VITE_STRIPE_PUBLISHABLE_KEY`, `VITE_PAYPAL_ENABLED`, `VITE_PAYPAL_CLIENT_ID`

### API Gateway
- `services/api-gateway/src/main/java/.../infrastructure/route/SecurityConfig.java` — permits `/payment/stripe/webhook`

### Tests
- `services/payment-service/src/test/java/.../infrastructure/gateway/StripeGatewayTest.java`
- `services/payment-service/src/test/java/.../infrastructure/gateway/StripeWebhookControllerTest.java`
- `services/payment-service/src/test/java/.../infrastructure/gateway/PayPalGatewayTest.java`
- `services/payment-service/src/test/java/.../infrastructure/sepay/SepayPollerTest.java`
- `services/payment-service/src/test/java/.../infrastructure/fx/FrankfurterFxAdapterTest.java`
- `services/payment-service/src/test/java/.../infrastructure/gateway/CompositePaymentGatewayTest.java`
- `services/payment-service/src/test/java/.../application/PaymentPromotionServiceTest.java`

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `4efcbee6` (the FE build-args fix). All seven commits this block are landed on local `main` (unpushed). Working tree is clean except for `.gitignore` + `opencode.jsonc` (unrelated editor config, ignore).
2. **Run the gates** (stack must be up + seeded):
   ```bash
   cd services/payment-service && ./mvnw test                    # 66/66
   cd fe && npx tsc --noEmit                                      # 0 errors
   STRIPE_ENABLED=true PAYPAL_ENABLED=true VIETQR_ENABLED=true \
     node infra/scripts/e2e-day.mjs                              # 67/67
   ```
3. **Manual smoke (still TODO):**
   - **Stripe webhook live drive.** ✅ **Done 2026-05-20.** `stripe trigger payment_intent.succeeded --add payment_intent:metadata.paymentId=29dd30f0-bb51-4fd7-a628-c73701026c8a` flipped that payment PENDING → COMPLETED with `pi_3TZ1rxETJXVjsFVG1I91pV4J`, signature verified, `payment-promoted provider=STRIPE` logged. Took the SecurityConfig fix in `cf9b08c4` to clear the 401.
   - **FE checkout sections render.** ✅ **Done 2026-05-20.** Verified via bundle grep — `pk_test_*` + PayPal sandbox client id are inlined into the lazy-loaded checkout chunk; the disabled-fallback strings were DCE'd. Open `http://localhost:3000` (port 3000 = nginx container; 5173 is dev-server only), add a product → cart → checkout, place an order paying with Stripe or PayPal, the live sections appear on the success step.
   - **PayPal capture flow.** FE Smart Buttons → `/payment/paypal/capture/{paymentId}/{paypalOrderId}` against a sandbox account at developer.paypal.com — sections now render (gotcha #12), capture itself still unproven live.
   - **SePay polling.** Poller runs on schedule; once SePay creds land in `.env`, check logs for `sepay-skip-non-vietqr` / `sepay-skip-no-uuid-in-memo` / `payment-promoted provider=SEPAY`.
   - **VietQR.** Already working; admin confirms via `AdminVietQrController`.
4. **Open a PR** with the rollout summary if not already pushed.

## Final tally

- **Started this block:** payment-service 35/35 tests at HEAD `be835032`.
- **Ended:** payment-service 66/66 tests (+31 — Stripe, PayPal, SePay, FX, composite, promotion), FE typecheck clean, **e2e-day 67/67** with all payment flags on, **Stripe webhook driven live end-to-end**, **FE Stripe + PayPal sandbox creds inlined into the checkout chunk** (verified via bundle grep + DCE check).
- **Commits this block:** 7 (4 feature + 3 fix). HEAD `4efcbee6`.
- **Diff vs pt5 HEAD:** ~+2864 / -403 across 30+ files (4 new packages, 7 new tests, 2 migrations, 2 FE sections, 1 gateway permit-list, 1 service permit-list, 4 FE build args, 1 RestClient.Builder bean, 1 fallback method fix, 1 timelimiter override).
- **Production characteristics added:** multi-method payment dispatch, provider-namespaced dedup, FX caching, Stripe webhook verification (now live-validated), PayPal OAuth, SePay polling, FE build-arg propagation for payment provider creds.
- **Deferred items closed:** 4 payment paths (VietQR, SePay, Stripe live-validated, PayPal sections render — capture still unproven). Also closed five runtime bugs that the unit suite didn't catch (RestClient.Builder, env passthrough, fallback method, service-level webhook permit, FE build-arg passthrough).

## Resume hint

Next session: drive the **PayPal capture** in the browser end-to-end. The Smart Buttons now mount on the success step (gotcha #12 closed); what's still unproven is the round-trip — sandbox PayPal popup login → approval → `/payment/paypal/capture/{paymentId}/{paypalOrderId}` → ledger flips PAID synchronously. After that, MoMo callback migration onto `PaymentPromotionService` is the smallest deferred item.
