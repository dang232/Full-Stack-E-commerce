# Session handover — 2026-05-21 (pt20: image-activate IDOR + avScanClean bypass closed)

**Last commit (HEAD):** `9f7afa41` (`fix(product): close image-activate IDOR + avScanClean wire bypass`)
**Commits since pt19 HEAD `55d9c1be`:** 2 (one is the OneDrive hydration script).

**Gates:**
- product-service: 25/25. order-service: 71/71. notification-service: 36/36. review-service: 6/6. seller-finance-service: 4/4. All 12 BE services green.
- Playwright: **14/14** in `day-simulation.spec.ts` (13 → 14).
- FE typecheck: 0. Vitest: 143/143.

This block did one extra audit pass on product-service (the only major service the pt12 → pt19 arc hadn't covered end-to-end) and uncovered two stacked vulnerabilities. Both closed.

## Commits this block

| # | Commit | What |
|---|---|---|
| 1 | `e75b477d` | chore(scripts): add repo-wide OneDrive hydration script |
| 2 | `9f7afa41` | fix(product): close image-activate IDOR + avScanClean wire bypass |

## What shipped

### `e75b477d` — Repo-wide OneDrive hydration script

Generalizes `fe/scripts/hydrate-e2e.mjs` to walk arbitrary directory trees. Default targets: `services`, `fe/src`, `fe/e2e`, `infra`. CLI args override. No-op on non-Windows.

Symptom this prevents: pt17 notification-service docker rebuild failed mid-flight because `authenticated-request.ts` had silently re-stubbed since pt8. Cost: ~30min of side-loading compiled JS into the running container instead of fixing the source. Before any TS service rebuild, run `node scripts/hydrate.mjs services/<svc>/src && docker compose build <svc>`.

### `9f7afa41` — Image-activate IDOR + avScanClean wire bypass

Two stacked vulnerabilities on the seller image activate endpoint and the parallel buyer review-image activate endpoint:

**Endpoints:**
- `POST /sellers/me/products/{productId}/images/activate`
- `POST /reviews/{reviewId}/images/activate`

**Vulnerability 1 — IDOR.** The path id was captured but never passed to the service. `ProductImageUploadService.activate(objectKey, ...)` looked up the wire-supplied objectKey in metadata storage with no ownership check. Any authenticated seller could activate any other seller's pending upload by submitting that seller's objectKey shape. The objectKey shape is predictable: `products/{productId}/images/{uuid}.{ext}` — enumeration is straightforward. `ReviewImageUploadService.activate` had the identical buyer-vs-buyer bug.

**Vulnerability 2 — avScanClean wire bypass.** `ActivateImageRequest.avScanClean` was a client-supplied boolean forwarded straight to `ObjectValidationService`, whose only AV gate is `if (!request.avScanClean()) failures.add("av_scan_required_or_failed")`. A hostile caller sets `avScanClean: true` on the wire and the gate passes unconditionally — that field's only purpose is to be the gate's input. The parallel `createUpload` paths correctly hardcoded `avScanClean(true)` because the upload-url flow runs server-side validation; activate must follow the same pattern.

**Stacked exploit.** A hostile seller submits a competitor's pending objectKey with their own checksum/dimensions and `avScanClean: true`. The activate endpoint moves it to `ACTIVE` state with the hostile metadata, no AV check, no ownership challenge. Single request, single endpoint.

**Fix shape (matches the pt12 → pt18 audit pattern):**
- New `ProductAccessDeniedException` class. `ApiExceptionHandler` maps it to HTTP 403 — distinct from `IllegalArgumentException` (400).
- `ProductImageUploadController.activate` reads the `{productId}` path var and forwards `JwtPrincipalUtil.currentSellerId()`.
- `ProductImageUploadService.activate(productId, sellerId, objectKey, ...)` verifies (a) product exists, (b) product owner matches caller, (c) objectKey path-prefix matches the productId. All three failures throw `ProductAccessDeniedException`.
- `avScanClean` removed from `ActivateImageRequest`, `ProductImageActivationRequest`, `ReviewImageActivateRequest`, `ReviewImageActivationRequest`. The service hardcodes `true` (matches `createUpload`). When a real AV scanner gets wired in, switch to a server-side `ObjectMetadata` lookup.
- Identical changes for the review-image flow (`ReviewImageUploadController` / `ReviewImageUploadService`). Buyer-vs-buyer IDOR + avScanClean bypass closed in lockstep.

**Day-simulation gate.** New test #14: a fresh buyer (acting as wrong-seller) probes `POST /sellers/me/products/{productId}/images/activate` with a forged objectKey on a real seeded product. Asserts 403. The ownership gate fires before any storage lookup, so the test doesn't need to seed the metadata table.

## Operational gotchas (additions)

46. **Vulnerabilities at the intersection of two anti-patterns hide longest.** The pt12 → pt18 audit pass was structured around three anti-patterns scanned independently: authoritative wire fields, JWT-vs-id IDORs, path-mounted self-id. The image-activate endpoint slipped through because *each anti-pattern alone looked benign* — the path id was unused (looked like a wart, not a bug, since the service had `objectKey` to resolve the resource), and `avScanClean` looked like a content-validation toggle (looked like a feature flag, not an authority claim). It was the *combination* that broke security. **Audit checklist additions for future passes:** (a) flag every controller path variable that's not used in the handler body, (b) flag every wire boolean named like a check/scan/verified/clean — these encode authority claims even when they don't look like one.

47. **`@RestControllerAdvice(assignableTypes = X)` is a scoping trap.** The product-service `ImageUploadExceptionHandler` is bound to `ProductImageUploadController` only — but the `ProductImageValidationException` it handles is also thrown from `ReviewImageUploadController`. Pre-existing inconsistency, surfaced because the new `ProductAccessDeniedException` had to go on the global `ApiExceptionHandler` to cover both controllers. Worth a separate cleanup pass to either widen the validation handler's scope or split it. Out of scope for this block.

## Final audit + cleanliness ledger (pt12 → pt20)

**Security findings closed: 16** across 6 services.

| # | Finding | Service | Severity | Closed |
|---|---|---|---|---|
| 1 | `POST /payment/*/create` accepts client-supplied amount | payment | **high** | pt13 |
| 2 | `POST /paypal/capture/{paymentId}/{paypalOrderId}` no buyer check | payment | low | pt13 |
| 3 | `GET /orders/{id}` IDOR | order | medium | pt14 |
| 4 | `GET /payment/status/{orderId}` IDOR | payment | medium | pt14 |
| 5 | `POST /sellers/me/finance/credits` self-credit | seller-finance | **high** | pt14 |
| 6 | `POST /returns/{id}/approve` IDOR | order | medium | pt15 |
| 7 | `POST /returns/{id}/reject` IDOR | order | medium | pt15 |
| 8 | `POST /returns/{id}/complete` IDOR | order | **high** | pt15 |
| 9 | `POST /returns/{id}/disputes` IDOR | order | low-medium | pt15 |
| 10 | `POST /reviews` buyerId impersonation | review | medium | pt15 |
| 11 | `POST /questions` buyerId impersonation | review | medium | pt15 |
| 12 | `GET /notifications/:id` IDOR | notification | medium-high | pt17 |
| 13 | `POST /sellers/me/products/{id}/images/activate` IDOR | product | **high** | pt20 |
| 14 | `POST /sellers/me/products/{id}/images/activate` avScanClean wire bypass | product | **high** | pt20 |
| 15 | `POST /reviews/{id}/images/activate` IDOR | product | **high** | pt20 |
| 16 | `POST /reviews/{id}/images/activate` avScanClean wire bypass | product | **high** | pt20 |

**Cleanliness items closed: 2** (dead `/admin/reviews` controller, dispute `resolvedBy` log-integrity gap).
**Day-simulation regression gates: 14** including 4 dedicated IDOR negative-path tests covering 12 of the 16 findings.

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `9f7afa41`.
2. **Smoke the suite.** `cd fe && npx playwright test e2e/day-simulation.spec.ts --project=chromium --reporter=line` → 14/14 in ~18s.
3. **Verify product-service is on the new image.** `docker logs vnshop-product-service --tail 5 | grep "Started"` should show a recent timestamp. If you suspect a regression, log in as two different buyers, have one register and look up a product, then probe `POST /sellers/me/products/{productId}/images/activate` with the other's JWT — must 403.

## What's still open

- **PayPal capture round-trip.** Last unproven payment path. Manual browser test, must run while logged in as the buyer who owns the payment.
- **`ImageUploadExceptionHandler` scope.** Bound to `ProductImageUploadController` only but handles an exception thrown from both image controllers (gotcha #47). Cleanup, not security.

## Resume hint

The pt12 → pt20 audit + cleanup arc is now genuinely complete from a security perspective. PayPal needs a human at a browser. Everything else on the open list is cosmetic.
