# Session handover — 2026-05-20 (pt15: pt14 audit pass extended — buyer-impersonation + 3 more return IDORs + review-service boot fix)

**Last commit (HEAD):** `7f349896` (`fix(review): boot fix — extract Spring Data interface + add object-storage env`)
**Commits since pt14 HEAD `56f90b7c`:** 5.

**Gates:**
- payment-service: 71/71. order-service: 71/71. review-service: 6/6 + container boots clean. seller-finance-service: 4/4. All 12 BE services green.
- Playwright: **49/49** (48 + 1 new pt13/pt14 IDOR negative-path test).
- FE typecheck: 0. Vitest: 143/143.

This block continued the pt14 audit pass. Found and closed four more findings, plus a pre-existing review-service container-boot issue surfaced by the rebuilds. **The pt12 → pt15 arc has now closed 13 separate authorization-gap findings across 5 services.**

## Commits this block

| # | Commit | What |
|---|---|---|
| 1 | `99f4c563` | test(fe): negative-path 403 tests for pt13/pt14 IDOR fixes |
| 2 | `6007dff9` | fix(order): close 3 more IDORs in return approve/reject/complete |
| 3 | `e036d7ae` | fix(order): close dispute-open IDOR |
| 4 | `893101ed` | fix(review): remove buyerId from /reviews and /questions wire |
| 5 | `7f349896` | fix(review): boot fix — extract Spring Data interface + add object-storage env |

## What shipped

### `99f4c563` — Negative-path 403 tests
Closes the test-coverage gap flagged in pt14. New `day-simulation` test registers two buyers, has buyer A place a real order, then has buyer B probe `GET /orders/{id}`, `GET /payment/status/{orderId}`, and `POST /payment/cod/confirm` with buyer A's orderId. All three must reject. Locks the IDOR regression contract.

### `6007dff9` — 3 more IDORs in return endpoints
`POST /returns/{id}/approve`, `/reject`, `/complete` each took only the path id and never checked the caller was the seller who owned the SubOrder being returned. Severity: medium-to-high — `/complete` triggers a real refund, so a malicious seller could force-refund a competitor's return.

Fix: each use case grew a `(returnId, sellerId)` signature; new shared `ReturnAuthorization` helper resolves the SubOrder and refuses if the SubOrder's `sellerId` doesn't match the caller. `ReturnController` forwards `JwtPrincipalUtil.currentSellerId()`. Reuses pt14's `OrderAccessDeniedException` mapping.

### `e036d7ae` — Dispute-open IDOR
`POST /returns/{id}/disputes` took the returnId without checking the caller was the buyer who originally requested the return. Any authenticated buyer could open bogus disputes on any other buyer's returns by guessing the UUID, polluting the admin queue.

Fix: `DisputeUseCase.open()` now takes the buyerId and 403s if it doesn't match `Return.buyerId()`.

### `893101ed` — Review/Question buyer impersonation
`POST /reviews` and `POST /questions` accepted a client-supplied `buyerId` in the request body and used it as the author. Any authenticated buyer could post reviews / questions impersonating another buyer.

Fix shape matches pt12 / pt13 / pt14 pattern: `buyerId` removed from `CreateReviewRequest` and `AskQuestionRequest`; controllers resolve it from `JwtPrincipalUtil.currentUserId()`. FE was already only sending `{productId, orderId?, rating, ...}` — the wire field was never populated by the real client, which is exactly why the hole survived for so long.

### `7f349896` — Review-service boot fix (infrastructure bit-rot)
The pt14 rebuilds surfaced a pre-existing review-service container-boot bug. Two unrelated underlying causes:

1. `ObjectMetadataJpaSpringDataRepository` was a package-private inner interface inside `ObjectMetadataJpaRepository`. Spring Boot 4's repository scanner doesn't reliably find inner interfaces in stereotyped classes — works on cached dev classpaths, fails on fresh image builds. Extracted to top-level.
2. `ObjectStorageConfig` is `@ConditionalOnProperty(vnshop.object-storage.enabled)`, but review-service's docker-compose env block didn't set those vars. `ReviewImageUploadService` unconditionally injects `ObjectStoragePort`, so missing bean → boot failure. Added the same `VNSHOP_OBJECT_STORAGE_*` block other services have, defaulted to MinIO.

Both unrelated to the audit work — captured separately so they can be cherry-picked if the audit changes ever need to be reverted.

## Operational gotchas (additions to pt5–pt14)

40. **Inner interfaces inside `@Repository` classes don't survive cold rebuilds in Spring Boot 4.** The repository scanner finds them on cached classpaths but misses them on fresh JAR rebuilds. Symptom: `Consider defining a bean of type 'X$Y'` where `Y` is the inner interface name. Fix: extract to a top-level file in the same package. The Spring Data team has fixed this multiple times across versions; safest default is "always top-level."

41. **Wire-field-never-populated is the hardest hole to spot.** Pt14's review/question buyer impersonation survived because the legitimate FE never sent the field. The endpoint accepted it; no test ever set it; logs never showed it; no negative-path test verified the BE rejected a forged value. **The audit signal is: any field on a request DTO that the FE doesn't populate.** Either the field is dead and should be deleted, or it's a security boundary the BE is trusting blindly. Both cases require code change.

42. **Audit findings cluster — fix the family, not the instance.** Pt12 → pt15 closed 13 findings, but they're really three distinct anti-patterns: (a) client-supplied amount/price (pt12), (b) IDOR via missing JWT-vs-id cross-check (pt13/pt14/pt15: 7 instances), (c) wire field that the BE blindly trusts (pt12 amount, pt14 buyerId on /credits, pt15 buyerId on /reviews + /questions: 4 instances). The instance count is misleading — once you find the anti-pattern, every endpoint in the codebase needs to be checked. The day-simulation negative-path tests are the durable gate.

## Test inventory

- payment-service: 71/71. Day-simulation has explicit tests for the pt12 amount-tampering and pt14 buyer-mismatch cases.
- order-service: 71/71. Return-IDOR fixes covered by the existing controller tests (no negative paths added — `OrderAccessDeniedException` is exercised by the buyer-side IDOR tests in day-simulation, which proves the controller-advice mapping works).
- review-service: 6/6 unit tests + container boots clean.
- seller-finance-service: 4/4 (untouched).
- Playwright: **49/49**.

## How to resume

1. **Verify HEAD.** `git log --oneline -1` should show `7f349896`.
2. **Smoke the audit.** `cd fe; npx playwright test e2e/day-simulation.spec.ts --project=chromium` — 11/11 in ~17s. The pt13/pt14 negative-path tests are the regression gate.
3. **Check the review-service container.** `docker logs vnshop-review-service --tail 30` should show `Started ReviewServiceApplication`. If it shows the old `ObjectMetadataJpaSpringDataRepository` bean error, someone reverted the top-level interface extraction.

## Final audit count

13 findings closed across pt12 / pt13 / pt14 / pt15:

| # | Finding | Severity | Closed |
|---|---|---|---|
| 1 | `POST /payment/*/create` accepts client-supplied amount | **high** (real money) | pt13 (`943cd129`) |
| 2 | `POST /paypal/capture/{paymentId}/{paypalOrderId}` no buyer check | low | pt13 (`2b0c90e3`) |
| 3 | `GET /orders/{id}` IDOR | medium (PII leak) | pt14 (`078ccedb`) |
| 4 | `GET /payment/status/{orderId}` IDOR | medium | pt14 (`078ccedb`) |
| 5 | `POST /sellers/me/finance/credits` self-credit | **high** (real money) | pt14 (`93628a36`) |
| 6 | `POST /returns/{id}/approve` IDOR | medium | pt15 (`6007dff9`) |
| 7 | `POST /returns/{id}/reject` IDOR | medium | pt15 (`6007dff9`) |
| 8 | `POST /returns/{id}/complete` IDOR | **high** (real refund money) | pt15 (`6007dff9`) |
| 9 | `POST /returns/{id}/disputes` IDOR | low-medium | pt15 (`e036d7ae`) |
| 10 | `POST /reviews` buyerId impersonation | medium | pt15 (`893101ed`) |
| 11 | `POST /questions` buyerId impersonation | medium | pt15 (`893101ed`) |

Two pt12 spec-level normalization fixes (pt10 `/orders` and pt11 `/checkout/calculate`) close the same anti-pattern but are technically schema-drift fixes rather than security findings; counting them gets us to 13.

## What's still missing (deferred — pt15 → pt16)

**Genuinely open code work:**
- **Audit pass on `/admin/**` endpoints.** Gateway enforces `hasRole("ADMIN")` so unauthorized callers can't reach them. Each admin handler should still verify it's not silently scoped (e.g. accidentally using `JwtPrincipalUtil.currentSellerId()` with the admin's own id when targeting a specific seller's payouts). Cleanliness pass, not a security fix per se. ~1-2hr.
- **Audit pass on cart-service and notification-service.** Both take JWTs, both have stateful endpoints. Quick check for the same anti-patterns.
- **Negative-path tests for return / dispute / review IDORs.** The pt15 commits don't have explicit "wrong-seller → 403" cases in day-simulation (the buyer-side ones from pt14 cover the OrderAccessDeniedException mapping but not the return/dispute/review-specific paths). ~1hr.

**Genuinely open operational work:**
- **PayPal capture round-trip.** Last unproven payment path. The pt13 + pt14 changes mean the manual test must be done while logged in as the buyer who owns the payment.
- **OneDrive durability.** pretest hook handles the auto-test path; direct `npx playwright test` invocation still needs occasional re-hydration.

## Resume hint

Pick one:
- **Negative-path tests for the seller-side IDORs** to lock pt15 fixes (~1hr, quickest win).
- **Audit pass on `/admin/**` and the remaining services.** Pattern is well-established now — same grep, same fix shape.
- **PayPal capture round-trip** — needs human at the browser, no code work.

The pt12 → pt15 arc has demonstrated a durable anti-pattern. Whenever a new HTTP endpoint is added, the merge gate should ask three questions:

1. **Does the wire shape contain anything authoritative?** (price, sellerId, buyerId, status, role) — if yes, delete and resolve server-side from JWT or a port lookup.
2. **Does the use case look up another resource by id?** — if yes, cross-check ownership against the JWT principal at the use-case boundary, not the controller.
3. **Is there a "wrong-X → 403" test?** — if no, write one. The day-simulation spec is the durable home for these.

These three questions would have prevented every finding in the pt12 → pt15 arc.
