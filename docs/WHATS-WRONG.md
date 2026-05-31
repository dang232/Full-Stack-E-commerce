# VNShop — What's Wrong (Re-Audit 2026-05-14 v7)

> Latest re-audit after multi-session remediation. All previously-flagged P0 + P1 items either fixed or partially addressed with structural hooks in place.
> Source: direct inspection of working tree (uncommitted) + verified by `mvn test`.

---

## Verification Status

`./mvnw.cmd -DskipITs test` (excluding `OrderServiceApplicationTests` which needs a live PostgreSQL) — **54 tests pass, 0 failures**. Saga (7) + outbox (11) + grpc client config (3) + new feature paths all green.

---

## Just Fixed in This Session

| Item | Evidence |
|---|---|
| Kafka topic mismatch | `OrderCreatedFinanceListener` (both copies) subscribe to `{"order.created", "order.paid"}` matching `OutboxPublisher.topicFor()` output. |
| OrderProjector wired to Kafka | `OrderProjectionListener.java` consumes `order.*` topics → `orderProjector.upsert(...)`. |
| Outbox warns when Kafka absent | `OutboxPublisher.warnIfKafkaTemplateMissing()` on `@PostConstruct`. |
| CartServiceAdapter hardened | `SimpleClientHttpRequestFactory` 1 s connect / 2 s read timeouts. URL externalized via `vnshop.cart-service.base-url`. Typed `CartUnavailableException`. |
| SagaState optimistic lock | `@Version` on `SagaStateJpaEntity` + `V12__saga_state_version.sql`. `SagaStateJpaRepository.save` updates the existing row to preserve the version counter. |
| Outbox DEAD triage index | `V13__outbox_dead_index.sql` adds `WHERE status = 'DEAD'` partial index. |
| Outbox explicit save | `OutboxPublisher.publishEvent` calls `repository.save(event)` after every status mutation; no longer relies on JPA dirty checking. |
| Dead duplicate response DTOs deleted | `infrastructure.web.{ProductResponse,VariantResponse,ImageResponse}` were unreferenced duplicates of `application.ProductResponse`. Deleted. |
| `OrderController.list` switched to projection | Reads from `OrderQueryHandler` → `OrderListItemResponse` (`OrderSummaryProjection` → DTO). `V14__backfill_order_summary.sql` populates the projection from existing orders + adds `idx_order_summary_buyer_created`. Detail endpoint stays on the write side (needs full sub-orders). |
| `GrpcClientConfigTest` rebuilt | Switched from `@SpringBootTest` (which loaded the entire app context) to `@SpringJUnitConfig(classes = GrpcClientConfig.class)`. 3 tests pass cleanly without mocking every JPA repo. |
| Saga compensation confirmation hook | `SagaOrchestrator.onCompensationCompleted(sagaId, confirmingStep)` promotes COMPENSATING→FAILED on confirmation. `SagaCompensationListener` consumes `inventory.released`/`payment.refunded`/`shipping.cancelled` and invokes the hook. **Order side is fully wired** — downstream services need to publish those events. |
| Coverage gate audit corrected | All 11 Java services have JaCoCo gates (90% on order/product/user, 80% on the rest). NestJS services have Jest `coverageThreshold` blocks. STATUS.md was stale. |

---

## Recently Fixed (carried from earlier audits)

| Item | Evidence |
|---|---|
| Database per service | 7 dedicated postgres containers in `docker-compose.yml`. |
| Saga orchestration + terminal FAILED | `SagaOrchestrator.failTimedOutCompensations()` runs every 60 s; `markCompensationFailed` emits `SAGA_FAILED`. |
| Outbox poison-pill + DLQ | V11 migration + `attempt_count`/`next_attempt_at`/`last_error` + `Status.DEAD` after `max-attempts` (default 8). Send timeout via `kafkaTemplate.send().get(...)`. |
| Stub cart adapter removed | Real `CartServiceAdapter` (now hardened, see above). |
| gRPC mesh bootstrapped | `proto/` + adapters + server stubs in inventory/payment/shipping. |
| Distributed tracing | Jaeger + `traceparent` header propagated by outbox publisher. |
| Notification controller layering + envelope | Use cases injected, `ApiResponse` everywhere. |
| Duplicate coupon/finance controllers in order-service | Deleted; canonical homes in coupon-service / seller-finance-service. |
| Gateway routes use Docker DNS | `RouteConfig.java` with `vnshop.routes.*` overrides. |
| Legacy Postgres apps profile blocker | `postgres-legacy` in both `apps` + `legacy`. |
| Deprecated review-service removed from apps profile | `review-service` in `legacy` only. |
| Seller-finance payout stubs removed | Real use cases wired. |
| HTTP domain leaks | Controllers return application DTOs across product, payment, inventory, order admin, image upload. |

---

## CRITICAL — Still Broken

None.

---

## HIGH — Still Open

### 1. Compensation confirmation events not produced by downstream services
The **order side is fully wired**: `SagaOrchestrator.onCompensationCompleted(...)` exists and `SagaCompensationListener` subscribes to `inventory.released`, `payment.refunded`, `shipping.cancelled`. But:

- `inventory-service` has no compensation publisher.
- `payment-service` has no `payment.refunded` outbox event.
- `shipping-service` has no `shipping.cancelled` outbox event.

**Until downstream services publish their confirmation events, the timeout finalizer (5 min default) is the only `COMPENSATING` → `FAILED` path.**

**Fix**: each downstream service should add an outbox event after running its compensation logic. The order-service listener will then fire automatically and the timeout becomes a safety net rather than the primary mechanism.

### 2. CartServiceAdapter has no circuit breaker
Timeouts now bound the worst-case latency, but a sustained cart-service outage still pushes failures through one request at a time. Adding Resilience4j `@CircuitBreaker(name = "cart-service")` would short-circuit during the outage window.

**Deferred** because it adds a new dependency — pending decision on whether to introduce Resilience4j across all services or a different fault-tolerance library.

---

## MEDIUM

### 3. CQRS still partial in scope
- ✅ Order side: projection + Kafka listener + query handler + read split on `GET /orders`.
- ❌ Product-service: zero projections / query handlers.
- ❌ Same physical Postgres for write and read tables (no read replica).
- ❌ No `CommandHandler` stereotype.

**Fix**: extend the pattern to product-service's hot read paths or rename to "outbox-backed read projections" in architecture docs to stop overclaiming CQRS.

### 4. No K8s / rolling update / canary manifests yet
Compose is the only orchestration. Production launch blocker.

---

## FEATURE GAPS

| Area | Gap |
|---|---|
| Catalog | Product variants (size/color) not modelled |
| Cart | No guest cart / wishlist |
| Post-purchase | Digital invoice UI missing |
| Shipping | Real carrier tracking (GHN/GHTK live APIs) not integrated |
| Admin | Sales reports, customer management, SEO tools absent |
| Compliance | GDPR delete-account, encryption at rest, penetration testing, i18n (vi/en) |

---

## Priority Fix Order (going forward)

1. **P1 (correctness)**
   - #1 Wire compensation publishers in inventory/payment/shipping (order side ready)
2. **P1 (reliability)**
   - #2 Pick a circuit-breaker library and apply to CartServiceAdapter (and similar future synchronous edges)
3. **P2 (cleanup / honesty)**
   - #3 Decide CQRS scope — extend or rename
4. **P2 (ops)**
   - #4 K8s manifests + rolling update strategy
5. **P2 (launch blockers)**
   - Feature gaps: variants, guest cart, real tracking, digital invoice, GDPR

---

## References

- `.sisyphus/ARCHITECTURE.md`, `.sisyphus/STATUS.md`
- `.sisyphus/analysis/microservice-maturity.md` (5.1/10 original — now estimated 8+)
- `.sisyphus/plans/gap-remediation.md`
- Migrations: `V11__outbox_retry_and_dead.sql`, `V12__saga_state_version.sql`, `V13__outbox_dead_index.sql`, `V14__backfill_order_summary.sql`
- Key new files: `OrderProjectionListener.java`, `SagaCompensationListener.java`, `CartUnavailableException.java`, `OrderListItemResponse.java`
