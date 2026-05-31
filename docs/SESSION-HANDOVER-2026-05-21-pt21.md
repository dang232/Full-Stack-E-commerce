# Session handover — 2026-05-21 (pt21: handler-scope cleanup + messaging-service audit clean)

**Last commit (HEAD):** `1c30218a` (`fix(product): widen ImageUploadExceptionHandler — register review variant`)
**Commits since pt20 HEAD `93a2f757`:** 1.

**Gates:**
- product-service: 25/25. order-service: 71/71. notification-service: 36/36. review-service: 6/6. seller-finance-service: 4/4. All 12 BE services green.
- Playwright: 14/14 in `day-simulation.spec.ts`.
- FE typecheck: 0. Vitest: 143/143.

This block closed pt20 gotcha #47 and ran a final audit pass on `messaging-service`. The audit returned zero findings; the handler-scope cleanup landed cleanly. The pt12 → pt21 audit + cleanup arc is now exhaustively complete.

## Commit this block

| # | Commit | What |
|---|---|---|
| 1 | `1c30218a` | fix(product): widen ImageUploadExceptionHandler — register review variant |

## What shipped

### `1c30218a` — Handler-scope cleanup

Pt20 gotcha #47: `ImageUploadExceptionHandler` was bound to `ProductImageUploadController.class` only via `@RestControllerAdvice(assignableTypes = ...)`. The same JAR throws `ReviewImageValidationException` from `ReviewImageUploadController`, but that exception slipped through to `ApiExceptionHandler.badRequest(IllegalArgumentException)` (since it extends IAE) and was downgraded to `{"code": "bad_request"}` with no failure list. The review-image client lost the structured error payload silently.

Fix: drop the `assignableTypes` scope so the handler is global, register `ReviewImageValidationException` explicitly with code `review_image_validation_failed`, mirror the existing product variant. Both image flows now return:
```json
{ "error": { "code": "<flow>_image_validation_failed", "message": "...", "details": ["failure_keys", ...] } }
```

Pure consistency cleanup, not a security fix. The validation gate itself fired correctly pre-fix; only the response shape was inconsistent.

## Audit results

### messaging-service: clean ✓

Final service-level audit. Buyer-seller chat is the textbook IDOR target so this got a careful pass. Findings: none.

Specifically verified end-to-end:

- **`MessagingController` (REST surface):** every endpoint runs through `JwtAuthGuard`, derives `userId` from `req.user.sub`, never from a header or query param. The list endpoint has no client-supplied `userId` parameter.
- **`SendMessageUseCase`:** thread lookup → `thread.involves(callerId)` check → 404 on non-participation. `Message.senderId` is set from the JWT-derived `callerId`, never from the request body. The wire DTO `SendMessageBody` carries only `body`.
- **`ListMessagesUseCase` / `MarkThreadReadUseCase`:** both check `thread.involves(userId)` before touching anything. 404-on-non-participation matches the IDOR-safe pattern (doesn't leak thread existence).
- **`MessagingWsGateway` (WebSocket layer):** handshake verifies the bearer token via JWKS (`WsJwtVerifier`, RS256, issuer-checked); `userId` bound to the socket comes from `payload.sub`. No `@SubscribeMessage` handlers — clients can only *receive* over the WS, never send. All mutations go through REST. The Kafka consumer that drives `dispatch()` is internal-only — clients can't inject forged events.

This is the cleanest service in the audit ledger. The pattern looks deliberate: exactly mirrors notification-service's IDOR-safe conventions, with the addition of WS subscriber-only design that closes the typical chat-service auth gap.

## Final ledger (pt12 → pt21)

**Security findings closed: 16.** No new findings in pt21.
**Cleanliness items closed: 3** (dead `/admin/reviews` controller, dispute `resolvedBy` log-integrity, `ImageUploadExceptionHandler` scope).
**Day-simulation regression gates: 14** including 4 dedicated IDOR negative-path tests covering 12 of the 16 findings.
**Services audited end-to-end: 8** (payment, order, seller-finance, review, notification, cart, product, messaging) plus all `/admin/**` endpoints across 6 services.
**Services not audited:** user-service, search-service, recommendations-service, inventory-service, shipping-service. These either don't have authoritative wire fields (search/recommendations are query-driven), don't expose client-facing JWT-scoped writes (inventory is product-service-driven, shipping is order-service-driven), or were partially covered (user-service `AdminSellerController` cleared in pt17; rest is registration/login flows whose security is Keycloak's responsibility).

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `1c30218a`.
2. **Smoke the suite.** `cd fe && npx playwright test e2e/day-simulation.spec.ts --project=chromium --reporter=line` → 14/14.
3. **Verify both image flows return the new error shape.** A POST to `/sellers/me/products/{id}/images/upload-url` with bad metadata should return `{"error":{"code":"product_image_validation_failed", ...}}`. Same path on `/reviews/{id}/images/upload-url` returns `review_image_validation_failed`. Pre-fix, the review variant returned `bad_request`.

## What's still open

- **PayPal capture round-trip.** Manual browser test, must run while logged in as the buyer who owns the payment. No code path needs verification — the pt13/pt14 tightening already covered the buyer check.

That's it. The pt12 → pt21 arc is complete from every angle that doesn't require a human at the browser.

## Resume hint

If the next session wants more security work, the unaudited services from the ledger above are reasonable targets, but expect smaller yields — the high-value ones (services with stateful writes, JWT-scoped reads, and authoritative wire fields) have all been covered. **The audit pattern itself is the durable artifact:** the three anti-patterns + day-simulation negative-path test convention can be applied by any future contributor when adding a new endpoint, without needing this thread of context.
