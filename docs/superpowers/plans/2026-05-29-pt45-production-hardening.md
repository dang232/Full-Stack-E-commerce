# PT45 Production Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire FX fields through the payment outbox/event pipeline, add per-seller commission tiers on SubOrder, and add consumer-lag health probes to 3 consumer-only services.

**Architecture:** Three sequential threads (#4 → #3 → #5). Thread #4 extends the existing outbox relay pattern. Thread #3 adds a config table + domain field + lookup port. Thread #5 replicates a health indicator pattern across 3 services.

**Tech Stack:** Java 21, Spring Boot 4.x, Spring Kafka, JPA/Hibernate, Flyway, PostgreSQL, JUnit 5 + Mockito

---

## Thread #4 — FX Fields on PaymentCompletedEvent / Outbox

### Task 1: Payment-service migration V11 — add FX columns to outbox table

**Files:**
- Create: `services/payment-service/src/main/resources/db/migration/V11__outbox_fx_columns.sql`

- [ ] **Step 1: Write migration**

```sql
ALTER TABLE payment_svc.payment_callback_outbox
    ADD COLUMN external_amount DECIMAL(19, 4),
    ADD COLUMN external_currency VARCHAR(3),
    ADD COLUMN fx_rate DECIMAL(12, 6),
    ADD COLUMN fx_rate_at TIMESTAMP;
```

- [ ] **Step 2: Verify migration applies**

Run: `cd services/payment-service; ./mvnw test -pl . -Dtest=NoTest -DfailIfNoTests=false`
Expected: BUILD SUCCESS (Flyway runs migrations on test DB)

- [ ] **Step 3: Commit**

```bash
git add services/payment-service/src/main/resources/db/migration/V11__outbox_fx_columns.sql
git commit -m "feat(payment): V11 migration — FX columns on outbox table"
```

---

### Task 2: Extend PaymentCallbackOutboxRecord with FX fields

**Files:**
- Modify: `services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/gateway/PaymentCallbackOutboxRecord.java`

- [ ] **Step 1: Add FX fields to record**

Add 4 new fields after `publishedAt`:
```java
BigDecimal externalAmount,
String externalCurrency,
BigDecimal fxRate,
Instant fxRateAt
```

- [ ] **Step 2: Update the `pending()` factory method**

Add the 4 FX parameters and pass them to the constructor.

- [ ] **Step 3: Fix all compilation errors**

Update all call sites of `PaymentCallbackOutboxRecord.pending(...)` and the record constructor to pass the new fields (null where FX is not available).

- [ ] **Step 4: Run tests**

Run: `cd services/payment-service; ./mvnw test`
Expected: All 89 tests pass

- [ ] **Step 5: Commit**

```bash
git add services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/gateway/PaymentCallbackOutboxRecord.java
git commit -m "feat(payment): add FX fields to PaymentCallbackOutboxRecord"
```

---

### Task 3: Extend PaymentCallbackOutboxJpaEntity with FX columns

**Files:**
- Modify: `services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/persistence/PaymentCallbackOutboxJpaEntity.java`

- [ ] **Step 1: Add JPA-mapped FX fields**

```java
@Column(name = "external_amount", precision = 19, scale = 4)
private BigDecimal externalAmount;

@Column(name = "external_currency", length = 3)
private String externalCurrency;

@Column(name = "fx_rate", precision = 12, scale = 6)
private BigDecimal fxRate;

@Column(name = "fx_rate_at")
private Instant fxRateAt;
```

- [ ] **Step 2: Update `fromRecord()` and `toRecord()` methods**

Map the 4 fields bidirectionally between record and entity.

- [ ] **Step 3: Run tests**

Run: `cd services/payment-service; ./mvnw test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/persistence/PaymentCallbackOutboxJpaEntity.java
git commit -m "feat(payment): map FX columns on PaymentCallbackOutboxJpaEntity"
```

---

### Task 4: Extend PaymentCompletedEvent with FX fields

**Files:**
- Modify: `services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/event/PaymentCompletedEvent.java`

- [ ] **Step 1: Add FX fields to event record**

```java
public record PaymentCompletedEvent(
    String provider,
    String paymentId,
    String orderId,
    String transactionRef,
    String status,
    BigDecimal amount,
    String currency,
    String callbackId,
    String callbackEventId,
    BigDecimal externalAmount,
    String externalCurrency,
    BigDecimal fxRate,
    Instant fxRateAt
) {}
```

- [ ] **Step 2: Fix compilation errors in PaymentCallbackOutboxRelay**

Update the event construction in `PaymentCallbackOutboxRelay` to pass FX fields from the outbox entity.

- [ ] **Step 3: Fix compilation errors in tests**

Update any test that constructs `PaymentCompletedEvent` to include the 4 new null/test fields.

- [ ] **Step 4: Run tests**

Run: `cd services/payment-service; ./mvnw test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/event/PaymentCompletedEvent.java
git add services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/event/PaymentCallbackOutboxRelay.java
git commit -m "feat(payment): add FX fields to PaymentCompletedEvent and relay"
```

---

### Task 5: Populate FX fields when creating outbox records

**Files:**
- Modify: `services/payment-service/src/main/java/com/vnshop/paymentservice/application/PaymentPromotionService.java`

- [ ] **Step 1: Pass FX fields from Payment domain to outbox record**

In the `promote()` method, read `payment.externalAmount()`, `payment.externalCurrency()`, `payment.fxRate()`, `payment.fxRateAt()` and pass them to `PaymentCallbackOutboxRecord.pending(...)`.

- [ ] **Step 2: Run tests**

Run: `cd services/payment-service; ./mvnw test`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add services/payment-service/src/main/java/com/vnshop/paymentservice/application/PaymentPromotionService.java
git commit -m "feat(payment): populate FX fields in outbox from Payment domain"
```

---

### Task 6: Order-service migration V17 — add FX columns to orders table

**Files:**
- Create: `services/order-service/src/main/resources/db/migration/V17__order_fx_columns.sql`

- [ ] **Step 1: Write migration**

```sql
ALTER TABLE order_svc.orders
    ADD COLUMN external_amount DECIMAL(19, 4),
    ADD COLUMN external_currency VARCHAR(3),
    ADD COLUMN fx_rate DECIMAL(12, 6),
    ADD COLUMN fx_rate_at TIMESTAMP;
```

- [ ] **Step 2: Commit**

```bash
git add services/order-service/src/main/resources/db/migration/V17__order_fx_columns.sql
git commit -m "feat(order): V17 migration — FX columns on orders table"
```

---

### Task 7: Order-service — persist FX fields from PaymentCompletedEvent

**Files:**
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/event/payment/PaymentCompletedListener.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/persistence/OrderJpaEntity.java`

- [ ] **Step 1: Add FX fields to OrderJpaEntity**

```java
@Column(name = "external_amount", precision = 19, scale = 4)
private BigDecimal externalAmount;

@Column(name = "external_currency", length = 3)
private String externalCurrency;

@Column(name = "fx_rate", precision = 12, scale = 6)
private BigDecimal fxRate;

@Column(name = "fx_rate_at")
private Instant fxRateAt;
```

Add setters: `setExternalAmount`, `setExternalCurrency`, `setFxRate`, `setFxRateAt`.

- [ ] **Step 2: Update PaymentCompletedListener to persist FX fields**

In the listener method, after setting paymentStatus to COMPLETED:
```java
if (event.externalAmount() != null) {
    order.setExternalAmount(event.externalAmount());
    order.setExternalCurrency(event.externalCurrency());
    order.setFxRate(event.fxRate());
    order.setFxRateAt(event.fxRateAt());
}
```

- [ ] **Step 3: Add the shared PaymentCompletedEvent record to order-service**

Create or update the event DTO in order-service to include the 4 FX fields (must match payment-service's event shape for deserialization).

- [ ] **Step 4: Write test for listener FX persistence**

Test that when a PaymentCompletedEvent with FX fields arrives, the order entity gets FX fields set. Test that null FX fields are handled gracefully.

- [ ] **Step 5: Run tests**

Run: `cd services/order-service; ./mvnw test`
Expected: All tests pass (135 + new tests)

- [ ] **Step 6: Commit**

```bash
git add services/order-service/
git commit -m "feat(order): persist FX fields from PaymentCompletedEvent"
```

---

## Thread #3 — Per-Seller Commission Tier on SubOrder

### Task 8: Order-service migration V18 — seller_commission_tier config table

**Files:**
- Create: `services/order-service/src/main/resources/db/migration/V18__seller_commission_tier.sql`

- [ ] **Step 1: Write migration**

```sql
CREATE TABLE order_svc.seller_commission_tier (
    seller_id VARCHAR(255) PRIMARY KEY,
    tier VARCHAR(20) NOT NULL DEFAULT 'STANDARD'
);

ALTER TABLE order_svc.sub_orders
    ADD COLUMN commission_tier VARCHAR(20) NOT NULL DEFAULT 'STANDARD';
```

- [ ] **Step 2: Commit**

```bash
git add services/order-service/src/main/resources/db/migration/V18__seller_commission_tier.sql
git commit -m "feat(order): V18 migration — seller_commission_tier table + sub_orders column"
```

---

### Task 9: CommissionTier enum + domain model

**Files:**
- Create: `services/order-service/src/main/java/com/vnshop/orderservice/domain/CommissionTier.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/domain/SubOrder.java`

- [ ] **Step 1: Create CommissionTier enum**

```java
package com.vnshop.orderservice.domain;

public enum CommissionTier {
    STANDARD,
    VERIFIED,
    PREFERRED,
    MALL
}
```

(Aligned with seller-finance-service's existing tier definitions.)

- [ ] **Step 2: Add commissionTier field to SubOrder**

Add `private CommissionTier commissionTier` field with getter. Update constructor to accept it (default STANDARD if null).

- [ ] **Step 3: Run tests — fix compilation**

Run: `cd services/order-service; ./mvnw test`
Fix any compilation errors from the new constructor parameter.

- [ ] **Step 4: Commit**

```bash
git add services/order-service/src/main/java/com/vnshop/orderservice/domain/CommissionTier.java
git add services/order-service/src/main/java/com/vnshop/orderservice/domain/SubOrder.java
git commit -m "feat(order): CommissionTier enum + SubOrder domain field"
```

---

### Task 10: JPA entities + repository for commission tier lookup

**Files:**
- Create: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/persistence/SellerCommissionTierJpaEntity.java`
- Create: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/persistence/SellerCommissionTierRepository.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/persistence/SubOrderJpaEntity.java`

- [ ] **Step 1: Create SellerCommissionTierJpaEntity**

```java
@Entity
@Table(name = "seller_commission_tier", schema = "order_svc")
public class SellerCommissionTierJpaEntity {
    @Id
    @Column(name = "seller_id")
    private String sellerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tier", nullable = false)
    private CommissionTier tier;

    // getters
}
```

- [ ] **Step 2: Create SellerCommissionTierRepository**

```java
public interface SellerCommissionTierRepository extends JpaRepository<SellerCommissionTierJpaEntity, String> {}
```

- [ ] **Step 3: Add commission_tier to SubOrderJpaEntity**

```java
@Enumerated(EnumType.STRING)
@Column(name = "commission_tier", nullable = false)
private CommissionTier commissionTier = CommissionTier.STANDARD;
```

Update `toDomain()` and `fromDomain()` to map the field.

- [ ] **Step 4: Run tests**

Run: `cd services/order-service; ./mvnw test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/persistence/
git commit -m "feat(order): SellerCommissionTier JPA entity + SubOrder column mapping"
```

---

### Task 11: CommissionTierLookupPort + adapter

**Files:**
- Create: `services/order-service/src/main/java/com/vnshop/orderservice/domain/port/out/CommissionTierLookupPort.java`
- Create: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/persistence/CommissionTierJpaAdapter.java`
- Create: `services/order-service/src/test/java/com/vnshop/orderservice/infrastructure/persistence/CommissionTierJpaAdapterTest.java`

- [ ] **Step 1: Write failing test**

```java
@ExtendWith(MockitoExtension.class)
class CommissionTierJpaAdapterTest {
    @Mock SellerCommissionTierRepository repository;
    @InjectMocks CommissionTierJpaAdapter adapter;

    @Test
    void returnsConfiguredTier() {
        var entity = new SellerCommissionTierJpaEntity("seller-1", CommissionTier.PREFERRED);
        when(repository.findById("seller-1")).thenReturn(Optional.of(entity));
        assertThat(adapter.findBySellerId("seller-1")).isEqualTo(CommissionTier.PREFERRED);
    }

    @Test
    void returnsStandardWhenNotConfigured() {
        when(repository.findById("unknown")).thenReturn(Optional.empty());
        assertThat(adapter.findBySellerId("unknown")).isEqualTo(CommissionTier.STANDARD);
    }
}
```

- [ ] **Step 2: Create port interface**

```java
package com.vnshop.orderservice.domain.port.out;

import com.vnshop.orderservice.domain.CommissionTier;

public interface CommissionTierLookupPort {
    CommissionTier findBySellerId(String sellerId);
}
```

- [ ] **Step 3: Create adapter implementation**

```java
@Component
@RequiredArgsConstructor
public class CommissionTierJpaAdapter implements CommissionTierLookupPort {
    private final SellerCommissionTierRepository repository;

    @Override
    public CommissionTier findBySellerId(String sellerId) {
        return repository.findById(sellerId)
            .map(SellerCommissionTierJpaEntity::tier)
            .orElse(CommissionTier.STANDARD);
    }
}
```

- [ ] **Step 4: Run tests**

Run: `cd services/order-service; ./mvnw test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add services/order-service/src/main/java/com/vnshop/orderservice/domain/port/out/CommissionTierLookupPort.java
git add services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/persistence/CommissionTierJpaAdapter.java
git add services/order-service/src/test/java/com/vnshop/orderservice/infrastructure/persistence/CommissionTierJpaAdapterTest.java
git commit -m "feat(order): CommissionTierLookupPort + JPA adapter with tests"
```

---

### Task 12: Wire tier into CreateOrderUseCase

**Files:**
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/application/CreateOrderUseCase.java`

- [ ] **Step 1: Inject CommissionTierLookupPort**

Add `private final CommissionTierLookupPort commissionTierLookupPort` to constructor.

- [ ] **Step 2: Resolve tier when building SubOrders**

In `splitBySeller()` or where SubOrders are constructed, look up tier:
```java
CommissionTier tier = commissionTierLookupPort.findBySellerId(sellerId);
new SubOrder(sellerId, itemsForSeller, tier);
```

- [ ] **Step 3: Fix tests — add mock for new dependency**

Add `@MockitoBean CommissionTierLookupPort` to any test that creates `CreateOrderUseCase`. Configure mock to return `CommissionTier.STANDARD` by default.

- [ ] **Step 4: Run tests**

Run: `cd services/order-service; ./mvnw test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add services/order-service/src/main/java/com/vnshop/orderservice/application/CreateOrderUseCase.java
git add services/order-service/src/test/
git commit -m "feat(order): resolve commission tier at order creation"
```

---

### Task 13: Replace hardcoded "STANDARD" in CompleteReturnUseCase + OrderEventPublisherAdapter

**Files:**
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/application/CompleteReturnUseCase.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/event/OrderEventPublisherAdapter.java`

- [ ] **Step 1: Update CompleteReturnUseCase**

Replace:
```java
refundRequestPort.requestRefund(savedReturn, targetSubOrder.sellerId(), refundAmount, "STANDARD");
```
With:
```java
refundRequestPort.requestRefund(savedReturn, targetSubOrder.sellerId(), refundAmount, targetSubOrder.commissionTier().name());
```

- [ ] **Step 2: Update OrderEventPublisherAdapter**

Replace:
```java
.map(subOrder -> new SellerTotal(subOrder.sellerId(), subOrder.itemsTotal().amount(), "STANDARD"))
```
With:
```java
.map(subOrder -> new SellerTotal(subOrder.sellerId(), subOrder.itemsTotal().amount(), subOrder.commissionTier().name()))
```

- [ ] **Step 3: Update tests to verify tier from SubOrder**

Ensure test SubOrders have a tier set, and assertions check the tier value flows through.

- [ ] **Step 4: Run tests**

Run: `cd services/order-service; ./mvnw test`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add services/order-service/src/main/java/com/vnshop/orderservice/application/CompleteReturnUseCase.java
git add services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/event/OrderEventPublisherAdapter.java
git add services/order-service/src/test/
git commit -m "feat(order): resolve commission tier from SubOrder instead of hardcoded STANDARD"
```

---

## Thread #5 — Kafka Consumer Health Probe (Lag Check)

### Task 14: KafkaConsumerHealthIndicator in search-service

**Files:**
- Create: `services/search-service/src/main/java/com/vnshop/searchservice/infrastructure/health/KafkaConsumerHealthProperties.java`
- Create: `services/search-service/src/main/java/com/vnshop/searchservice/infrastructure/health/KafkaConsumerHealthIndicator.java`
- Create: `services/search-service/src/test/java/com/vnshop/searchservice/infrastructure/health/KafkaConsumerHealthIndicatorTest.java`
- Modify: `services/search-service/src/main/resources/application.yml`

- [ ] **Step 1: Write failing test**

```java
@ExtendWith(MockitoExtension.class)
class KafkaConsumerHealthIndicatorTest {
    @Mock AdminClient adminClient;
    KafkaConsumerHealthProperties props = new KafkaConsumerHealthProperties(true, 1000L, "search-service");
    KafkaConsumerHealthIndicator indicator;

    @BeforeEach
    void setUp() { indicator = new KafkaConsumerHealthIndicator(adminClient, props); }

    @Test
    void healthyWhenLagBelowThreshold() {
        // mock listConsumerGroupOffsets → offset 90
        // mock listOffsets (latest) → offset 100
        // lag = 10, threshold = 1000
        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("totalLag", 10L);
    }

    @Test
    void unhealthyWhenLagAboveThreshold() {
        // mock lag = 5000, threshold = 1000
        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("totalLag", 5000L);
    }

    @Test
    void unhealthyWhenBrokerUnreachable() {
        // mock AdminClient throws exception
        Health health = indicator.health();
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsKey("error");
    }
}
```

- [ ] **Step 2: Create KafkaConsumerHealthProperties**

```java
@ConfigurationProperties("kafka.health.consumer")
public record KafkaConsumerHealthProperties(
    boolean enabled,
    long lagThreshold,
    String groupId
) {}
```

- [ ] **Step 3: Create KafkaConsumerHealthIndicator**

```java
@Component
@ConditionalOnProperty(name = "kafka.health.consumer.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConsumerHealthIndicator implements HealthIndicator {
    private final AdminClient adminClient;
    private final KafkaConsumerHealthProperties props;

    @Override
    public Health health() {
        try {
            var groupOffsets = adminClient
                .listConsumerGroupOffsets(props.groupId())
                .partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);

            var topicPartitions = groupOffsets.keySet();
            var latestOffsets = adminClient
                .listOffsets(topicPartitions.stream()
                    .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest())))
                .all().get(5, TimeUnit.SECONDS);

            long totalLag = topicPartitions.stream()
                .mapToLong(tp -> latestOffsets.get(tp).offset() - groupOffsets.get(tp).offset())
                .sum();

            var details = Map.of("totalLag", totalLag, "groupId", props.groupId(), "threshold", props.lagThreshold());
            return totalLag <= props.lagThreshold()
                ? Health.up().withDetails(details).build()
                : Health.down().withDetails(details).build();
        } catch (Exception e) {
            return Health.down().withDetail("error", e.getMessage()).withDetail("groupId", props.groupId()).build();
        }
    }
}
```

- [ ] **Step 4: Add AdminClient bean configuration**

```java
@Bean
@ConditionalOnProperty(name = "kafka.health.consumer.enabled", havingValue = "true", matchIfMissing = true)
public AdminClient kafkaAdminClient(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
    return AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
}
```

- [ ] **Step 5: Add config to application.yml**

```yaml
kafka:
  health:
    consumer:
      enabled: true
      lag-threshold: 1000
      group-id: search-service
```

- [ ] **Step 6: Run tests**

Run: `cd services/search-service; ./mvnw test`
Expected: All tests pass

- [ ] **Step 7: Commit**

```bash
git add services/search-service/
git commit -m "feat(search): KafkaConsumerHealthIndicator with lag check"
```

---

### Task 15: KafkaConsumerHealthIndicator in recommendations-service

**Files:**
- Create: `services/recommendations-service/src/main/java/com/vnshop/recommendationsservice/infrastructure/health/KafkaConsumerHealthProperties.java`
- Create: `services/recommendations-service/src/main/java/com/vnshop/recommendationsservice/infrastructure/health/KafkaConsumerHealthIndicator.java`
- Create: `services/recommendations-service/src/test/java/com/vnshop/recommendationsservice/infrastructure/health/KafkaConsumerHealthIndicatorTest.java`
- Modify: `services/recommendations-service/src/main/resources/application.yml`

- [ ] **Step 1: Copy pattern from Task 14**

Same `KafkaConsumerHealthProperties`, `KafkaConsumerHealthIndicator`, AdminClient bean, and test class — adjusted package to `com.vnshop.recommendationsservice.infrastructure.health`.

- [ ] **Step 2: Add config to application.yml**

```yaml
kafka:
  health:
    consumer:
      enabled: true
      lag-threshold: 1000
      group-id: recommendations-service
```

- [ ] **Step 3: Run tests**

Run: `cd services/recommendations-service; ./mvnw test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add services/recommendations-service/
git commit -m "feat(recommendations): KafkaConsumerHealthIndicator with lag check"
```

---

### Task 16: KafkaConsumerHealthIndicator in seller-finance-service

**Files:**
- Create: `services/seller-finance-service/src/main/java/com/vnshop/sellerfinanceservice/infrastructure/health/KafkaConsumerHealthProperties.java`
- Create: `services/seller-finance-service/src/main/java/com/vnshop/sellerfinanceservice/infrastructure/health/KafkaConsumerHealthIndicator.java`
- Create: `services/seller-finance-service/src/test/java/com/vnshop/sellerfinanceservice/infrastructure/health/KafkaConsumerHealthIndicatorTest.java`
- Modify: `services/seller-finance-service/src/main/resources/application.yml`

- [ ] **Step 1: Copy pattern from Task 14**

Same classes — adjusted package to `com.vnshop.sellerfinanceservice.infrastructure.health`.

- [ ] **Step 2: Add config to application.yml**

```yaml
kafka:
  health:
    consumer:
      enabled: true
      lag-threshold: 1000
      group-id: seller-finance-service-refund
```

- [ ] **Step 3: Run tests**

Run: `cd services/seller-finance-service; ./mvnw test`
Expected: All tests pass

- [ ] **Step 4: Commit**

```bash
git add services/seller-finance-service/
git commit -m "feat(finance): KafkaConsumerHealthIndicator with lag check"
```

---

## Final Verification

### Task 17: Full test suite + docs commit

- [ ] **Step 1: Run all service tests**

```bash
cd services/payment-service; ./mvnw test
cd services/order-service; ./mvnw test
cd services/search-service; ./mvnw test
cd services/recommendations-service; ./mvnw test
cd services/seller-finance-service; ./mvnw test
```

Expected: All green. Payment ~91+, Order ~137+, Search/Recs/Finance each +2 from health tests.

- [ ] **Step 2: Write session handover**

Create `docs/SESSION-HANDOVER-2026-05-29-pt45.md` with commit log, test counts, gotchas, and next-session threads.

- [ ] **Step 3: Final commit**

```bash
git add docs/
git commit -m "docs(pt45): session handover — FX event pipeline, commission tier, consumer health"
```
