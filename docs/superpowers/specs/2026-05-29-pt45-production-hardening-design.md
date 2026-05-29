# PT45 Design Spec — Production Hardening (FX Event, Commission Tier, Consumer Health)

**Date:** 2026-05-29  
**Threads:** #4 → #3 → #5 (implementation order)  
**Approach:** Bottom-up dependency order — FX event pipeline first (warm-up), commission tier second (meatiest), consumer health last (mechanical).

---

## Thread #4 — FX Fields on PaymentCompletedEvent / Outbox

### Goal

Carry FX data (`externalAmount`, `externalCurrency`, `fxRate`, `fxRateAt`) from the Payment domain through the outbox pipeline into the Kafka event, and persist it on the order in order-service for buyer-facing display.

### Changes

| # | File / Location | Change |
|---|----------------|--------|
| 1 | `payment-service` migration V10 | Add 4 columns to `payment_callback_outbox`: `external_amount DECIMAL(19,4)`, `external_currency VARCHAR(3)`, `fx_rate DECIMAL(12,6)`, `fx_rate_at TIMESTAMP` |
| 2 | `order-service` migration (next V) | Add 4 columns to `orders`: `external_amount DECIMAL(19,4)`, `external_currency VARCHAR(3)`, `fx_rate DECIMAL(12,6)`, `fx_rate_at TIMESTAMP` |
| 3 | `PaymentCallbackOutboxRecord` | Add fields: `BigDecimal externalAmount`, `String externalCurrency`, `BigDecimal fxRate`, `Instant fxRateAt` |
| 4 | `PaymentCallbackOutboxJpaEntity` | Map the 4 new columns |
| 5 | `PaymentCompletedEvent` | Add fields: `BigDecimal externalAmount`, `String externalCurrency`, `BigDecimal fxRate`, `Instant fxRateAt` |
| 6 | `PaymentCallbackOutboxRelay` | Pass FX fields from outbox entity → event constructor |
| 7 | Outbox record creation site | Populate FX fields from Payment domain when inserting outbox rows |
| 8 | Order-service consumer | Read FX fields from `PaymentCompletedEvent`, persist on order entity |
| 9 | `OrderJpaEntity` | Map the 4 new columns |
| 10 | `OrderResponse` DTO | Expose FX fields for buyer-facing display |

### Backward Compatibility

All 4 FX fields are nullable. Existing outbox rows and events without FX data deserialize to null. Order-service consumer handles null gracefully (no-op on missing FX).

### Tests

- Unit: `PaymentCallbackOutboxRelay` maps FX fields correctly (present and null cases)
- Unit: Order-service consumer persists FX fields when present, skips when null
- Integration: Existing payment-service tests still pass with new outbox columns

---

## Thread #3 — Per-Seller Commission Tier on SubOrder

### Goal

Replace hardcoded `"STANDARD"` commission tier with a per-seller lookup from a static config table within order-service.

### Domain Model

```
CommissionTier (enum): STANDARD, PREMIUM, VIP

seller_commission_tier table:
  seller_id VARCHAR(255) PK
  tier VARCHAR(20) NOT NULL DEFAULT 'STANDARD'
```

### Changes

| # | File / Location | Change |
|---|----------------|--------|
| 1 | `order-service` migration (next V) | Create `seller_commission_tier` table |
| 2 | `order-service` migration (next V) | Add `commission_tier VARCHAR(20) NOT NULL DEFAULT 'STANDARD'` to `sub_orders` |
| 3 | `domain/CommissionTier.java` | Enum: `STANDARD`, `PREMIUM`, `VIP` |
| 4 | `domain/SubOrder.java` | Add `commissionTier` field (type `CommissionTier`) |
| 5 | `SubOrderJpaEntity` | Map column with `@Enumerated(STRING)` |
| 6 | `port/out/CommissionTierLookupPort.java` | Interface: `CommissionTier findBySellerId(String sellerId)` |
| 7 | `SellerCommissionTierJpaEntity` | JPA entity for config table |
| 8 | `SellerCommissionTierRepository` | Spring Data repository |
| 9 | `CommissionTierJpaAdapter` | Implements port; returns STANDARD if seller not in table |
| 10 | `CompleteReturnUseCase` | Resolve tier from `subOrder.commissionTier()` instead of hardcoded "STANDARD" |
| 11 | `OrderEventPublisherAdapter` | Resolve tier from `subOrder.commissionTier()` instead of hardcoded "STANDARD" |
| 12 | Order creation flow | Look up tier via `CommissionTierLookupPort` when building SubOrders |

### Backward Compatibility

- Default tier is STANDARD — existing orders and sellers behave identically.
- Migration adds column with DEFAULT so existing rows are valid.
- `RefundRequestedEvent` and `PaymentRefundedEvent` already carry `commissionTier` (from pt44).

### Tests

- Unit: `CommissionTierJpaAdapter` — found seller returns their tier, missing seller returns STANDARD
- Unit: `CompleteReturnUseCase` — uses tier from SubOrder (not hardcoded)
- Unit: `OrderEventPublisherAdapter` — publishes tier from SubOrder
- Integration: Order creation sets tier on SubOrder from config table

---

## Thread #5 — Kafka Consumer Health Probe (Lag Check)

### Goal

Add a `KafkaConsumerHealthIndicator` to the 3 consumer-only services that monitors consumer group lag and reports unhealthy when lag exceeds a configurable threshold.

### Target Services

1. `search-service`
2. `recommendations-service`
3. `seller-finance-service`

### Design

```java
@Component
@ConditionalOnProperty(name = "kafka.health.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumerHealthIndicator implements HealthIndicator {
    // Uses AdminClient to:
    // 1. listConsumerGroupOffsets(groupId) → current offsets
    // 2. listOffsets(OffsetSpec.latest()) → end offsets
    // 3. lag = sum(end - current) across all partitions
    // UP if lag <= threshold, DOWN if lag > threshold or broker unreachable
}
```

### Configuration

```yaml
kafka:
  health:
    consumer:
      enabled: true
      lag-threshold: 1000
      group-id: ${spring.kafka.consumer.group-id}
```

### Changes

| # | File / Location | Change |
|---|----------------|--------|
| 1 | Each service: `KafkaConsumerHealthProperties` | `@ConfigurationProperties("kafka.health.consumer")` — `enabled`, `lagThreshold`, `groupId` |
| 2 | Each service: `KafkaConsumerHealthIndicator` | Implements `HealthIndicator` with lag check logic |
| 3 | Each service: `AdminClient` bean | Created from `spring.kafka.bootstrap-servers` |
| 4 | Each service: `application.yml` | Add `kafka.health.consumer` config block |

### Design Decisions

- **Per-service copy** (not shared module) — matches existing producer indicator pattern. Extract to shared lib if a 4th consumer-only service appears.
- **AdminClient approach** over Spring's built-in `KafkaHealthIndicator` — gives lag visibility, not just broker connectivity.
- **Configurable threshold** — default 1000 messages. Services can tune based on their throughput.

### Error Handling

- Broker unreachable → `Health.down()` with exception message
- AdminClient timeout → `Health.down()` with timeout detail
- Consumer group not found → `Health.up()` with warning (group may not have committed offsets yet)

### Tests

- Unit (mocked AdminClient): healthy lag, unhealthy lag, broker unreachable, group not found
- Each service gets its own test class

---

## Implementation Order

1. **Thread #4** — FX on event (payment-service + order-service)
2. **Thread #3** — Commission tier (order-service only)
3. **Thread #5** — Consumer health (search, recommendations, seller-finance)

Threads #4 and #3 share order-service migrations — they'll be sequenced (V-next, V-next+1). Thread #5 is fully independent.

## Out of Scope

- R2 swap (#1) — credential-gated
- PayPal sandbox smoke (#2) — credential-gated
- Consumer lag alerting/metrics (future enhancement on top of #5)
- FX rate refresh/update logic (rates are captured at payment time, not refreshed)
