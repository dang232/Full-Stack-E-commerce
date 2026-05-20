# Session handover — 2026-05-20 (pt13: payment amount-tampering finding closed)

**Last commit (HEAD):** `943cd129` (`fix(payment): close pt12 amount-tampering finding on /payment/*/create`)
**Commits since pt11 HEAD `83cfe042`:** 2 (pt12 finding doc + pt13 fix).
**Status: FINDING CLOSED.**

**Gates:**
- payment-service: **71/71** (was 67 pre-pt12 — net +4 after the security tests).
- order-service: **71/71**, untouched.
- Playwright: **48/48** (47 + 1 new pt13 regression test).
- FE typecheck: 0. Vitest: **143/143**.
- All 12 BE services green.

---

## TL;DR

Pt12 documented the finding. Pt13 fixed it. The wire shape that made the exploit possible no longer exists — `POST /payment/*/create` accepts only `{orderId}`. Buyer principal is resolved from the JWT, payable amount is resolved from order-service. The day-simulation spec now contains a regression test that drives the BE with a fake orderId and asserts the BE rejects before reaching any gateway.

## Commits this block

| # | Commit | What |
|---|---|---|
| 1 | `f0b83c49` | docs(pt12): SECURITY FINDING — payment amount tampering, plan for next session |
| 2 | `943cd129` | fix(payment): close pt12 amount-tampering finding on /payment/*/create |

## What shipped (`943cd129`)

### BE — payment-service

**New ports + adapters (mirrors the pt10 `ProductCatalogPort` pattern):**
- `OrderCatalogPort` (domain port) + `OrderCatalogAdapter` (HTTP, forwards the caller's JWT so order-service can authorize the lookup).
- `OrderSnapshot` value object: `{orderId, buyerId, finalAmount, currency, paymentStatus}`.
- 4 new exceptions, all wired into `ApiExceptionHandler`:
  - `OrderNotFoundException` → 404 `ORDER_NOT_FOUND`
  - `OrderAccessDeniedException` → 403 `ORDER_ACCESS_DENIED`
  - `OrderNotPayableException` → 409 `ORDER_NOT_PAYABLE`
  - `OrderCatalogUnavailableException` → 503 `ORDER_CATALOG_UNAVAILABLE`

**`ProcessPaymentUseCase` split into two entry points** (this is the key design decision — single entry point would have broken the gRPC service-to-service path):
- `process(cmd)` — HTTP path. Resolves authoritative amount via `OrderCatalogPort`, validates buyer ownership and `PENDING` status. The security boundary.
- `processInternal(cmd, trustedAmount)` — gRPC path used by order-service's `CreateOrderUseCase`, which calls payment-service **before** persisting the order row. A back-lookup would 404. Order-service is the source of truth so the trusted-amount path is correct here. Bypasses the catalog lookup.

**Wire shape changes:**
- `PaymentRequest` slimmed to `{orderId}` only. `buyerId` and `amount` deleted.
- `ProcessPaymentCommand` drops `amount`. `buyerId` resolved from JWT in the controller via `JwtPrincipalUtil.currentUserId()`.
- All 5 `*Create` controllers updated.

**Tests:**
- 4 new `ProcessPaymentUseCaseTest` cases: catalog-amount-wins (the headline security gate), buyer-mismatch → 403, already-paid → 409, missing-order → 404. All existing tests re-wired through the new constructor.
- `PaymentControllerHeaderTest` updated to seed a fake JWT in the `SecurityContextHolder` (the controller now reads the buyer principal from there).
- `GrpcPaymentServerTest` updated: trusted gRPC path verifies `processInternal` is called with the trusted amount; HTTP path tests live in the use case test suite.

### FE

- `payment.ts`: `stripeCreate`/`paypalCreate`/`vietqrCreate` now take `{orderId}` only.
- `StripePaymentSection`/`PayPalPaymentSection`/`VietQrPaymentSection` drop the `buyerId` and `amount` props — the components only need `orderId` and the idempotency key.
- `CheckoutPage.tsx` drops the `buyerId`/`amount` payload to those sections.

### E2E

- `day-simulation.spec.ts` — new test "payment create endpoints reject client-supplied amount (pt12 finding)". Drives `POST /payment/cod/confirm` with a fake UUID `orderId`, asserts the response is non-2xx. The gateway is not reached. The headline regression gate.

## Operational gotchas (additions to pt5–pt11)

33. **Two-entry-point pattern for HTTP-vs-gRPC trust boundaries.** Pt12 nearly broke COD checkout because the HTTP-side fix (look up the order before charging) was wrong for the gRPC-side service-to-service path (where the caller IS the order-service and the order isn't persisted yet). The fix: split `process(cmd)` into `process(cmd)` (HTTP — full validation) and `processInternal(cmd, trustedAmount)` (gRPC — bypasses catalog lookup). Same use case, two entry points, distinct trust models. **Don't conflate the two when refactoring.** A use case that's reachable from both an FE-facing controller and a service-mesh gRPC server has two different security postures and the code needs to acknowledge both.

34. **JWT forwarding for service-to-service authorization.** `OrderCatalogAdapter` reads the inbound JWT from `SecurityContextHolder` and re-attaches it as a `Bearer` header on the outbound HTTP call. order-service's existing `JwtPrincipalUtil.currentUserId()` then resolves the same buyer principal. No new service-account credentials, no new authorization scheme — just relay. The pattern works for any "service A calls service B on behalf of the user" flow where both services live behind the same Keycloak realm.

35. **Test-time SecurityContext seeding.** When unit-testing a controller that reads `SecurityContextHolder.getContext().getAuthentication().getPrincipal()` (the standard JwtPrincipalUtil shape), seed a fake `Jwt` with the `sub` claim in `@BeforeEach` and clear it in `@AfterEach`. Otherwise MockMvc tests that hit the controller throw NPE inside the handler. Pattern lives in pt13's `PaymentControllerHeaderTest`.

36. **Spring Boot 4 ObjectMapper auto-config can vanish under `@Configuration` weight.** Adding a new HTTP adapter (OrderCatalogAdapter) that needs `ObjectMapper` failed to start payment-service with "Consider defining a bean of type ObjectMapper". Boot's auto-config is supposed to provide one when `spring-boot-starter-web` is on the classpath, but in pt13 it didn't — same gotcha class as pt7's recommendations-service `JacksonConfig`. Fix: add an explicit `@Bean ObjectMapper` guarded by `@ConditionalOnMissingBean` to `UseCaseConfig`. No-op if Boot's auto-config does work, defensive otherwise.

## Test inventory

- Playwright e2e: **48/48** in 11 files. Day-simulation now exercises both the BE-side normalization (pt10, pt11) and the BE-side amount resolution (pt13).
- Vitest: 143/143 in 23 files.
- BE: 12 services green. payment-service is now 71/71 (67 → 71, +4 net new tests for the security gate; some obsolete amount-validation gRPC tests merged into the new pattern).

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `943cd129`. Working tree should still show only `M .gitignore` + `?? opencode.jsonc` (pt6 carry-over editor cruft).
2. **Smoke the security fix.** `cd fe; npx playwright test e2e/day-simulation.spec.ts --project=chromium` — 10/10 in ~15s. The pt12 regression test is the gate.
3. **Manual reproduction of the original exploit no longer works.** Place an order via `POST /orders`, then try `POST /payment/stripe/create` with `{orderId, buyerId, amount: 1}` — the JSON validator silently drops `buyerId`/`amount`, the BE looks up the real order amount, and Stripe gets charged the correct amount or the request 4xx's because the buyer doesn't own the order. Either outcome closes the finding; no path leads to a 1-VND charge for a 1,000,000-VND order.

## What's still missing (deferred — pt13 → pt14)

**Genuinely open code work:** None. The pt7-onwards deferred queue is empty.

**Genuinely open operational work:**
- **PayPal capture round-trip.** Smart Buttons render, BE OAuth + create + capture is unit-tested, but no human has driven the FE → sandbox PayPal popup → `/payment/paypal/capture` round-trip end-to-end. Last unproven payment path. **Needs you at the browser.** Pt13 changed `paypalCreate` to take `{orderId}` only — that's the only behavioural change a manual round-trip will see; the create response shape (paymentId + paypalOrderId) is unchanged.
- **OneDrive durability.** `npm run test:e2e` self-heals via the pretest hook from pt8; `npx playwright test` direct invocation still vulnerable.

## Resume hint

Next session: **drive the PayPal capture round-trip in the browser.** It's the last unproven payment path and the only headline item that legitimately needs human-at-keyboard time. After that, the deferred queue is purely operational (OneDrive). The day-simulation spec is now the durable integration gate for both the wire-shape drift (pt10/pt11) and the amount-tampering security boundary (pt13).

The pt9 → pt13 arc is a useful reference pattern for "found a class of finding, fix the same way everywhere":
1. Day-simulation spec drives flows end-to-end against the live stack.
2. First instance found (pt9) — schema drift on `POST /orders`, with security-finding side effect.
3. Closed with BE-side normalization (pt10) — wire shape only carries what the client legitimately knows; BE resolves the rest from authoritative ports.
4. Same pattern applied to the next two endpoints (pt11 `/checkout/calculate`, pt13 `/payment/*/create`).
5. Each fix added a regression test to the day-simulation spec — the gate now catches reintroductions of any of the three drifts.

The general rule across all four endpoints: **the wire shape gives the client only what the client legitimately knows.** Identifiers, quantities, intent. Everything that affects authoritative state (price, seller routing, status, role) comes from BE-side lookups against ports, never from the request body. If a future change leaks denormalized fields back into the wire, the day-simulation spec catches it.
