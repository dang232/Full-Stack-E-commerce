# Session handover — 2026-05-30 (pt46: F-01/F-04 fixes, R2 swap, Prometheus metrics, batch tier)

**Last commit (HEAD):** `8a0493c1` (`infra(r2): wire docker-compose to .env for avatar storage + CORS config`)

**Gates:**
- order-service mvn: 144 / 144 (was 139 in pt45 — +5 from cart checkout + batch tier tests).
- user-service mvn: 141 / 141 (unchanged).
- payment-service mvn: 89 / 89 (unchanged).
- product-service mvn: 33 / 33 (unchanged).
- seller-finance-service mvn: 20 / 20 (unchanged).
- search-service mvn: 11 / 11 (unchanged).
- recommendations-service mvn: 53 / 53 (unchanged).
- FE typecheck / vitest: untouched this block.

## What this block was

Pt45's recommended picks + full sweep of unblocked issues from `docs/PROJECT-SWEEP-2026-05-29.md`.

### Commits (7 feature/fix/refactor)

| # | SHA | Thread | Summary |
|---|-----|--------|---------|
| 1 | `bcc96e42` | **F-01** | Noop adapters for gRPC ports — resolve startup crash |
| 2 | `9adbd1dd` | **F-04** | Cart-based checkout endpoint (`/checkout/calculate-from-cart`) |
| 3 | `31438b1e` | **Perf** | Batch commission tier lookup — eliminate N+1 in splitBySeller |
| 4 | `40d7e053` | **Metrics** | Prometheus `kafka_consumer_lag_total` gauge in 3 services |
| 5 | `6515a4f2` | **Docs** | Mark F-02/F-06/F-07/F-08 resolved in GAP-ANALYSIS and PROJECT-SWEEP |
| 6 | `dac5c983` | **R2** | `publicUrl()` respects `pathStyleAccess` for R2 compatibility |
| 7 | `8a0493c1` | **R2** | docker-compose reads storage vars from .env + R2 CORS config |

### Thread: F-01 — Order-service startup crash (resolved)
- `GrpcClientConfig` now `@ConditionalOnProperty(name = "grpc.client.enabled", havingValue = "true", matchIfMissing = true)`.
- Three gRPC adapters annotated with `@ConditionalOnBean` for their respective stubs.
- `GrpcNoopConfig` provides `@ConditionalOnMissingBean` fallbacks that log warnings and return safe defaults.
- New profile: `application-standalone.yml` sets `grpc.client.enabled=false`.
- Production behavior unchanged (matchIfMissing = true keeps gRPC on by default).

### Thread: F-04 — Checkout uses real cart-service (resolved)
- `POST /checkout/calculate-from-cart` added to `CheckoutController`.
- Requires authentication — extracts userId from JWT, calls `CalculateCheckoutUseCase.calculate(userId)`.
- Cart-service fetched via existing `CartServiceAdapter` (circuit breaker protected).
- Returns 503 `CART_UNAVAILABLE` when cart-service is down.
- Existing `/checkout/calculate` (light-shape, client-sent items) kept for buy-now flows.

### Thread: Batch commission tier lookup
- `CommissionTierLookupPort.findBySellerIds(Set<String>)` added to port interface.
- `CommissionTierJpaAdapter` implements via `repository.findAllById()` — single query.
- `CreateOrderUseCase.splitBySeller()` collects all seller IDs, fetches tiers in one call.
- Missing sellers default to `CommissionTier.STANDARD`.
- `findBySellerId()` now delegates to batch method internally.

### Thread: Prometheus consumer lag metrics
- `micrometer-registry-prometheus` added to search-service, seller-finance-service, recommendations-service.
- `KafkaConsumerHealthIndicator` in all 3 services now registers `kafka_consumer_lag_total` gauge.
- Gauge tagged with `group.id` and `service` for Grafana filtering.
- Updated on every health check cycle (no separate scheduled task).
- Available at `/actuator/prometheus`.

### Thread: R2 avatar storage swap (completed)
- `S3ObjectStorageAdapter.publicUrl()` now branches on `pathStyleAccess`:
  - `true` (MinIO): `{publicEndpoint}/{bucket}/{key}`
  - `false` (R2): `{publicEndpoint}/{key}` (bucket in hostname)
- docker-compose user-service storage vars now read from `.env` with MinIO defaults.
- R2 bucket `ecomer` created (APAC region), public r2.dev URL enabled.
- CORS policy set: GET/PUT/HEAD from localhost:3000 and localhost:5173.
- Verified: upload + public download working via Wrangler CLI.
- E2E test regex relaxed to match both URL patterns.
- `.env` configured with R2 credentials (gitignored).

### Thread: Documentation cleanup
- F-02 (Kafka topic mismatch): was a documentation error, not a code bug. All topics correctly use dot-notation.
- F-06 (commission tier hardcoded): resolved in pt45.
- F-07 (FX fields not on event): resolved in pt45.
- F-08 (no consumer health probe): resolved in pt45.
- GAP-ANALYSIS.md and PROJECT-SWEEP-2026-05-29.md updated accordingly.

## Gotchas this block

**119. Wrangler R2 CORS format.** The `wrangler r2 bucket cors set` command requires `{ "rules": [{ "allowed": { "origins": [...], "methods": [...], "headers": [...] }, "maxAge": N }] }` — NOT the `allowedOrigins`/`allowedMethods` format from the S3 API.

**120. Wrangler `r2 object put` defaults to local.** Without `--remote` flag, Wrangler writes to local miniflare storage, not the actual R2 bucket. Always use `--remote` for real operations.

**121. R2 API tokens can't be created via Wrangler CLI.** Must use the Cloudflare dashboard at `/r2/api-tokens`. Wrangler's OAuth token doesn't provide S3-compatible credentials for the Java SDK.

**122. `@ConditionalOnBean` + `@ConditionalOnMissingBean` ordering.** Spring evaluates `@ConditionalOnBean` after all `@Configuration` classes are processed. The gRPC adapters must be `@Component` (not `@Bean` in a config class) for the noop fallback pattern to work reliably.

## Open threads for the next session

**Closed by this block:**
- ~~F-01 Order-service startup crash~~ — noop adapters + standalone profile.
- ~~F-04 Checkout stub cart adapter~~ — cart-based endpoint added.
- ~~R2 avatar swap~~ — bucket live, code fixed, env wired.
- ~~Batch tier lookup~~ — single-query batch method.
- ~~Consumer lag Prometheus metrics~~ — gauge in 3 services.
- ~~F-02/F-06/F-07/F-08 documentation~~ — marked resolved.

**Still open:**

1. **PayPal sandbox manual smoke.** Credentials in `.env`, code complete. Needs `docker compose up` with `apps` profile and a browser walkthrough to confirm end-to-end PayPal create → capture → order status flip.
2. **GHN/GHTK shipping adapter.** Gated on third-party API key.
3. **Notifications inbox (FE bell icon).** Medium priority feature — 2 blocks.
4. **Fix profile/runtime mismatches (F-05).** Routes point to services not started in `apps` profile.
5. **Remove review-service empty shell.** Low priority cleanup.
6. **Mockito self-attach warning on Java 25.** Noisy test output; will break in future JDK.

**Recommended pick for pt47:** **PayPal sandbox smoke test** — spin up the stack, walk through a payment, verify the outbox relay fires and order status flips. Then **Notifications inbox (FE bell)** as the next feature.

## How to resume

1. **Verify HEAD.** `git log --oneline -3` shows `8a0493c1 infra(r2): wire docker-compose...` at the top.
2. **Smoke gates:**
   - `cd services/order-service; ./mvnw test` → 144 / 144.
   - `cd services/user-service; ./mvnw test` → 141 / 141.
   - `cd services/payment-service; ./mvnw test` → 89 / 89.
   - `cd services/product-service; ./mvnw test` → 33 / 33.
   - `cd services/seller-finance-service; ./mvnw test` → 20 / 20.
   - `cd services/search-service; ./mvnw test` → 11 / 11.
   - `cd services/recommendations-service; ./mvnw test` → 53 / 53.
3. **R2 verification:** `wrangler r2 bucket info ecomer` → shows bucket with public access.
4. **PayPal verification:** `.env` has `PAYPAL_ENABLED=true` + sandbox credentials.

## Final session ledger (pt27 → pt46)

- **pt27-pt34**: i18n, dark-mode, BA-grade journey suite, chapter-6 flake root-cause.
- **pt35**: payout audit trail. Gotchas #94-96.
- **pt36**: avatar upload, MinIO+R2-swap. Gotchas #97-100.
- **pt37**: Ship/Accept access-control. Gotchas #101-102.
- **pt38**: order-service IAE-as-403 sweep. Gotchas #103-104.
- **pt39**: payment-service sweep + missing 403 handler. Gotchas #105-106.
- **pt40**: status-code oracle close on lookup misses. Gotcha #107.
- **pt41**: kafka env-override sweep. Gotchas #108-109.
- **pt42**: PayPal refund saga close + 12-commit gap reconciliation. Gotchas #110-111.
- **pt43**: PaymentRefunded consumers — buyer-visible refund saga closed. Gotcha #112.
- **pt44**: Four production-hardening threads (commission tier propagation, idempotency, health probe, FX fields). Gotchas #113-114.
- **pt45**: FX event pipeline, per-seller commission tier, consumer health probes. Gotchas #115-118.
- **pt46 (this)**: F-01/F-04 fixes, R2 swap, Prometheus metrics, batch tier. Gotchas #119-122.
