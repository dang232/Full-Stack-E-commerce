# Phase 4: Security Hardening & Critical Fixes — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate all critical security vulnerabilities and restore architectural invariants in order-service.

**Architecture:** Fix secrets exposure (git history + .env + docker-compose), re-enable Kafka SSL verification, externalize K8s secrets, add method-level authorization, create port interfaces to restore hexagonal boundaries.

**Tech Stack:** Spring Security, Keycloak, Terraform (HCL), Docker Compose, BFG Repo-Cleaner

---

## What's Wrong (Evidence)

| # | Problem | File | Line | Current Value |
|---|---------|------|------|---------------|
| 1 | Kafka SSL hostname verification disabled | 8 service application.yml files | varies | `ssl.endpoint.identification.algorithm: ""` |
| 2 | Hardcoded DB passwords in compose | docker-compose.yml | 11,29,46,65,84,103,122,141,160 | `POSTGRES_PASSWORD: vnshop123` |
| 3 | Redis sentinel auth in plaintext | infra/redis/sentinel.conf | 6 | `sentinel auth-pass mymaster vnshop123` |
| 4 | Only 3 `@PreAuthorize` annotations exist | user-service GdprController.java | 23,30,41 | Only GDPR endpoints protected |
| 5 | CSRF disabled globally | api-gateway SecurityConfig.java | 76 | `.csrf(ServerHttpSecurity.CsrfSpec::disable)` |
| 6 | No CSP headers | api-gateway SecurityConfig.java | 105-109 | Only frame-options + content-type |
| 7 | NAT GW single AZ SPOF | infra/terraform/modules/vpc/main.tf | 54-55 | `count = var.enable_nat_gateway ? 1 : 0` |
| 8 | CreateOrderUseCase imports infra | CreateOrderUseCase.java | 19-20 | `import ...infrastructure.audit.Audited` + `...metrics.OrderMetrics` |
| 9 | SagaOrchestrator imports infra | SagaOrchestrator.java | 4-6 | `import ...outbox.OutboxEvent/JpaEntity/Repository` |
| 10 | OrderProjector imports infra | OrderProjector.java | 1 | `import ...persistence.OrderSummaryProjectionJpaEntity` |

---

## File Structure

```
services/order-service/src/main/java/com/vnshop/orderservice/
├── domain/port/out/
│   ├── AuditPort.java                    (NEW)
│   ├── MetricsPort.java                  (NEW)
│   └── OutboxPort.java                   (NEW)
├── infrastructure/audit/
│   ├── Audited.java                      (EXISTING - keep)
│   └── AuditPortAdapter.java             (NEW)
├── infrastructure/metrics/
│   ├── OrderMetrics.java                 (EXISTING - keep)
│   └── MetricsPortAdapter.java           (NEW)
├── infrastructure/outbox/
│   └── OutboxPortAdapter.java            (NEW)
├── application/
│   ├── CreateOrderUseCase.java           (MODIFY - remove infra imports)
│   ├── saga/SagaOrchestrator.java        (MODIFY - use OutboxPort)
│   └── projection/OrderProjector.java    (MODIFY - use ProjectionPort)
infra/
├── redis/sentinel.conf                   (MODIFY - use variable)
├── terraform/modules/vpc/main.tf         (MODIFY - per-AZ NAT)
services/*/src/main/resources/application.yml  (MODIFY x8 - SSL verification)
docker-compose.yml                        (MODIFY - externalize passwords)
services/api-gateway/.../SecurityConfig.java   (MODIFY - add CSP)
```

---

## Stage 1: Kafka SSL Hostname Verification (Task 1)

### Task 1: Re-enable SSL endpoint identification on all 8 services

**Files:**
- Modify: `services/seller-finance-service/src/main/resources/application.yml:33`
- Modify: `services/inventory-service/src/main/resources/application.yml:43`
- Modify: `services/product-service/src/main/resources/application.yml:37`
- Modify: `services/recommendations-service/src/main/resources/application.yml:37`
- Modify: `services/search-service/src/main/resources/application.yml:40`
- Modify: `services/shipping-service/src/main/resources/application.yml:38`
- Modify: `services/order-service/src/main/resources/application.yml:35`
- Modify: `services/payment-service/src/main/resources/application.yml:35`

- [ ] **Step 1: Fix each service's application.yml**

In every file listed above, change:
```yaml
      ssl.endpoint.identification.algorithm: ""
```
to:
```yaml
      ssl.endpoint.identification.algorithm: https
```

- [ ] **Step 2: Verify certs/generate-certs.sh includes broker SAN**

Read `infra/kafka/certs/generate-certs.sh` and confirm the `-ext SAN=dns:kafka,...` entry includes the Docker service name `kafka`. If missing, add:
```bash
-ext "SAN=dns:kafka,dns:localhost,ip:127.0.0.1"
```

- [ ] **Step 3: Commit**

```bash
git add services/*/src/main/resources/application.yml infra/kafka/certs/generate-certs.sh
git commit -m "security(kafka): re-enable SSL hostname verification on all 8 services"
```

---

## Stage 2: Externalize Secrets from Compose & Redis (Tasks 2-3)

### Task 2: Replace hardcoded POSTGRES_PASSWORD in docker-compose.yml

**Files:**
- Modify: `docker-compose.yml` (9 occurrences at lines 11,29,46,65,84,103,122,141,160)
- Modify: `.env.example`

- [ ] **Step 1: Replace all hardcoded passwords with env var references**

For each of the 9 Postgres service blocks, replace:
```yaml
      POSTGRES_PASSWORD: vnshop123
```
with:
```yaml
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:-changeme}
```

For the monitoring Postgres (line 178), replace:
```yaml
      POSTGRES_PASSWORD: monitoring
```
with:
```yaml
      POSTGRES_PASSWORD: ${MONITORING_DB_PASSWORD:-changeme}
```

- [ ] **Step 2: Add password variables to .env.example**

Append:
```env
# ─── Database Passwords (CHANGE IN PRODUCTION) ───
POSTGRES_PASSWORD=vnshop123
MONITORING_DB_PASSWORD=monitoring
REDIS_PASSWORD=vnshop123
```

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml .env.example
git commit -m "security(compose): externalize all hardcoded POSTGRES_PASSWORD to env vars"
```

### Task 3: Externalize Redis sentinel.conf auth-pass

**Files:**
- Modify: `infra/redis/sentinel.conf`
- Create: `infra/redis/sentinel-entrypoint.sh`
- Modify: `docker-compose.yml` (sentinel service blocks)

- [ ] **Step 1: Convert sentinel.conf to template with placeholder**

Replace entire `infra/redis/sentinel.conf`:
```conf
port 26379
sentinel monitor mymaster redis-master 6379 2
sentinel down-after-milliseconds mymaster 5000
sentinel failover-timeout mymaster 10000
sentinel parallel-syncs mymaster 1
sentinel auth-pass mymaster $REDIS_PASSWORD
```

- [ ] **Step 2: Create sentinel-entrypoint.sh**

Create `infra/redis/sentinel-entrypoint.sh`:
```bash
#!/bin/sh
set -e
sed "s|\$REDIS_PASSWORD|${REDIS_PASSWORD}|g" \
  /etc/redis/sentinel.conf.template > /tmp/sentinel.conf
exec redis-sentinel /tmp/sentinel.conf
```

- [ ] **Step 3: Update docker-compose sentinel services**

For each sentinel container (sentinel-1, sentinel-2, sentinel-3), add:
```yaml
    volumes:
      - ./infra/redis/sentinel.conf:/etc/redis/sentinel.conf.template:ro
      - ./infra/redis/sentinel-entrypoint.sh:/usr/local/bin/sentinel-entrypoint.sh:ro
    entrypoint: ["sh", "/usr/local/bin/sentinel-entrypoint.sh"]
    environment:
      REDIS_PASSWORD: ${REDIS_PASSWORD:-vnshop123}
```

- [ ] **Step 4: Commit**

```bash
git add infra/redis/sentinel.conf infra/redis/sentinel-entrypoint.sh docker-compose.yml
git commit -m "security(redis): externalize sentinel auth-pass via entrypoint template"
```

---

## Stage 3: Hexagonal Port Interfaces (Tasks 4-6)

### Task 4: Create AuditPort and MetricsPort domain interfaces

**Files:**
- Create: `services/order-service/src/main/java/com/vnshop/orderservice/domain/port/out/AuditPort.java`
- Create: `services/order-service/src/main/java/com/vnshop/orderservice/domain/port/out/MetricsPort.java`

- [ ] **Step 1: Create AuditPort**

```java
package com.vnshop.orderservice.domain.port.out;

/**
 * Port for recording audit trail entries from application layer.
 * Infrastructure provides the concrete persistence mechanism.
 */
public interface AuditPort {
    void recordAction(String userId, String action, String resourceType, String resourceId);
}
```

- [ ] **Step 2: Create MetricsPort**

```java
package com.vnshop.orderservice.domain.port.out;

/**
 * Port for recording business metrics from application layer.
 * Avoids coupling use cases to Micrometer/Prometheus infrastructure.
 */
public interface MetricsPort {
    Object startTimer();
    void stopTimer(Object timerSample);
    void recordOrderCreated();
    void recordOrderCancelled();
    void recordOrderCreationFailed();
}
```

- [ ] **Step 3: Commit**

```bash
git add services/order-service/src/main/java/com/vnshop/orderservice/domain/port/out/AuditPort.java
git add services/order-service/src/main/java/com/vnshop/orderservice/domain/port/out/MetricsPort.java
git commit -m "arch(ports): add AuditPort and MetricsPort domain interfaces"
```

### Task 5: Create OutboxPort and ProjectionPort domain interfaces

**Files:**
- Create: `services/order-service/src/main/java/com/vnshop/orderservice/domain/port/out/OutboxPort.java`
- Create: `services/order-service/src/main/java/com/vnshop/orderservice/domain/port/out/ProjectionPort.java`

- [ ] **Step 1: Create OutboxPort**

```java
package com.vnshop.orderservice.domain.port.out;

/**
 * Port for publishing domain events via the transactional outbox.
 * Decouples saga/application logic from JPA outbox entity details.
 */
public interface OutboxPort {
    void publish(String aggregateType, String aggregateId, String eventType, String payload);
}
```

- [ ] **Step 2: Create ProjectionPort**

```java
package com.vnshop.orderservice.domain.port.out;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Port for updating read-model projections from application layer.
 * Decouples projector logic from JPA entity specifics.
 */
public interface ProjectionPort {
    void upsertOrderSummary(String orderId, String buyerId, String status,
                            BigDecimal totalAmount, int itemCount, Instant createdAt);
}
```

- [ ] **Step 3: Commit**

```bash
git add services/order-service/src/main/java/com/vnshop/orderservice/domain/port/out/OutboxPort.java
git add services/order-service/src/main/java/com/vnshop/orderservice/domain/port/out/ProjectionPort.java
git commit -m "arch(ports): add OutboxPort and ProjectionPort domain interfaces"
```

### Task 6: Create infrastructure adapters and rewire use cases

**Files:**
- Create: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/metrics/MetricsPortAdapter.java`
- Create: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/outbox/OutboxPortAdapter.java`
- Create: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/persistence/ProjectionPortAdapter.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/application/CreateOrderUseCase.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/application/saga/SagaOrchestrator.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/application/projection/OrderProjector.java`

- [ ] **Step 1: Create MetricsPortAdapter**

```java
package com.vnshop.orderservice.infrastructure.metrics;

import com.vnshop.orderservice.domain.port.out.MetricsPort;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

@Component
public class MetricsPortAdapter implements MetricsPort {
    private final OrderMetrics orderMetrics;

    public MetricsPortAdapter(OrderMetrics orderMetrics) {
        this.orderMetrics = orderMetrics;
    }

    @Override public Object startTimer() { return orderMetrics.startTimer(); }
    @Override public void stopTimer(Object s) { orderMetrics.stopTimer((Timer.Sample) s); }
    @Override public void recordOrderCreated() { orderMetrics.recordOrderCreated(); }
    @Override public void recordOrderCancelled() { orderMetrics.recordOrderCancelled(); }
    @Override public void recordOrderCreationFailed() { orderMetrics.recordOrderCreationFailed(); }
}
```

- [ ] **Step 2: Create OutboxPortAdapter**

```java
package com.vnshop.orderservice.infrastructure.outbox;

import com.vnshop.orderservice.domain.port.out.OutboxPort;
import org.springframework.stereotype.Component;

@Component
public class OutboxPortAdapter implements OutboxPort {
    private final OutboxEventRepository outboxEventRepository;

    public OutboxPortAdapter(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Override
    public void publish(String aggregateType, String aggregateId, String eventType, String payload) {
        outboxEventRepository.save(OutboxEventJpaEntity.fromDomain(
            OutboxEvent.pending(aggregateType, aggregateId, eventType, payload)
        ));
    }
}
```

- [ ] **Step 3: Create ProjectionPortAdapter**

```java
package com.vnshop.orderservice.infrastructure.persistence;

import com.vnshop.orderservice.domain.port.out.ProjectionPort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;

@Component
public class ProjectionPortAdapter implements ProjectionPort {
    @PersistenceContext
    private EntityManager em;

    @Override
    @Transactional
    public void upsertOrderSummary(String orderId, String buyerId, String status,
                                   BigDecimal totalAmount, int itemCount, Instant createdAt) {
        OrderSummaryProjectionJpaEntity entity = em.find(OrderSummaryProjectionJpaEntity.class, orderId);
        if (entity == null) {
            entity = new OrderSummaryProjectionJpaEntity();
            entity.setOrderId(orderId);
            entity.setBuyerId(buyerId);
            entity.setCreatedAt(createdAt);
        }
        entity.setStatus(status);
        entity.setTotalAmount(totalAmount);
        entity.setItemCount(itemCount);
        em.merge(entity);
    }
}
```

- [ ] **Step 4: Rewire CreateOrderUseCase — remove infra imports**

In `CreateOrderUseCase.java`, remove these imports (lines 19-20):
```java
// DELETE these:
import com.vnshop.orderservice.infrastructure.audit.Audited;
import com.vnshop.orderservice.infrastructure.metrics.OrderMetrics;
```

Replace `OrderMetrics` field with `MetricsPort`:
```java
// Replace field:
private final MetricsPort metricsPort;

// Replace constructor parameter:
public CreateOrderUseCase(..., MetricsPort metricsPort) {
    ...
    this.metricsPort = metricsPort;
}
```

Replace method calls:
- `orderMetrics.startTimer()` → `metricsPort.startTimer()`
- `orderMetrics.stopTimer(timerSample)` → `metricsPort.stopTimer(timerSample)`
- `orderMetrics.recordOrderCreated()` → `metricsPort.recordOrderCreated()`
- `orderMetrics.recordOrderCreationFailed()` → `metricsPort.recordOrderCreationFailed()`

For `@Audited` annotation: Move the annotation definition to the domain layer as a marker annotation (no Spring dependency), OR replace with explicit `auditPort.recordAction()` call inside the method body. Recommended: keep `@Audited` in infrastructure but use it only on the controller/adapter layer, not in use cases.

Add new import:
```java
import com.vnshop.orderservice.domain.port.out.MetricsPort;
```

- [ ] **Step 5: Rewire SagaOrchestrator — remove outbox infra imports**

In `SagaOrchestrator.java`, remove these imports (lines 4-6):
```java
// DELETE these:
import com.vnshop.orderservice.infrastructure.outbox.OutboxEvent;
import com.vnshop.orderservice.infrastructure.outbox.OutboxEventJpaEntity;
import com.vnshop.orderservice.infrastructure.outbox.OutboxEventRepository;
```

Replace with:
```java
import com.vnshop.orderservice.domain.port.out.OutboxPort;
```

Replace field and constructor:
```java
private final OutboxPort outboxPort;

public SagaOrchestrator(SagaStateRepository sagaStateRepository, OutboxPort outboxPort) {
    this.sagaStateRepository = sagaStateRepository;
    this.outboxPort = outboxPort;
}
```

In `compensate()` method, replace (lines 127-130):
```java
// OLD:
outboxEventRepository.save(OutboxEventJpaEntity.fromDomain(
    OutboxEvent.pending(AGGREGATE_TYPE, sagaId, "SAGA_COMPENSATING",
        "{\"orderId\":\"" + current.orderId() + "\",\"sagaId\":\"" + sagaId + "\",\"failedStep\":\"" + failedStep + "\"}")
));
// NEW:
outboxPort.publish(AGGREGATE_TYPE, sagaId, "SAGA_COMPENSATING",
    "{\"orderId\":\"" + current.orderId() + "\",\"sagaId\":\"" + sagaId + "\",\"failedStep\":\"" + failedStep + "\"}");
```

- [ ] **Step 6: Rewire OrderProjector — remove JPA entity import**

In `OrderProjector.java`, remove:
```java
// DELETE:
import com.vnshop.orderservice.infrastructure.persistence.OrderSummaryProjectionJpaEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
```

Replace with:
```java
import com.vnshop.orderservice.domain.port.out.ProjectionPort;
```

Replace EntityManager field with ProjectionPort and delegate to `projectionPort.upsertOrderSummary(...)`.

- [ ] **Step 7: Run compile check**

```bash
cd services/order-service && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 8: Run existing tests**

```bash
cd services/order-service && mvn test -q
```
Expected: All tests pass (existing ArchUnit rules will now PASS for these classes)

- [ ] **Step 9: Commit**

```bash
git add services/order-service/
git commit -m "arch(hexagonal): rewire use cases to port interfaces, remove all infra imports from application layer"
```

---

## Stage 4: CSP Headers & Gateway Security (Tasks 7-8)

### Task 7: Add Content-Security-Policy headers to API gateway

**Files:**
- Modify: `services/api-gateway/src/main/java/com/vnshop/apigateway/infrastructure/config/SecurityConfig.java:105-109`

- [ ] **Step 1: Add CSP header configuration**

In `SecurityConfig.java`, replace the `.headers(...)` block (lines 105-109):
```java
// OLD:
.headers(headers -> headers
    .frameOptions(frame -> frame.mode(org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
    .contentTypeOptions(org.springframework.security.config.Customizer.withDefaults())
    .cache(org.springframework.security.config.Customizer.withDefaults())
)
// NEW:
.headers(headers -> headers
    .frameOptions(frame -> frame.mode(org.springframework.security.web.server.header.XFrameOptionsServerHttpHeadersWriter.Mode.DENY))
    .contentTypeOptions(org.springframework.security.config.Customizer.withDefaults())
    .cache(org.springframework.security.config.Customizer.withDefaults())
    .contentSecurityPolicy(csp -> csp.policyDirectives(
        "default-src 'self'; " +
        "script-src 'self' https://js.stripe.com https://www.paypal.com; " +
        "style-src 'self' 'unsafe-inline'; " +
        "img-src 'self' data: https://*.r2.dev; " +
        "connect-src 'self' https://api.stripe.com https://www.paypal.com; " +
        "frame-src https://js.stripe.com https://www.paypal.com; " +
        "object-src 'none'; " +
        "base-uri 'self'"
    ))
    .referrerPolicy(referrer -> referrer.policy(
        org.springframework.security.web.server.header.ReferrerPolicyServerHttpHeadersWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
)
```

- [ ] **Step 2: Verify gateway compiles**

```bash
cd services/api-gateway && mvn compile -q
```
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add services/api-gateway/
git commit -m "security(gateway): add Content-Security-Policy and Referrer-Policy headers"
```

### Task 8: Add CSRF token protection for cookie-based refresh endpoint

**Files:**
- Modify: `services/api-gateway/src/main/java/com/vnshop/apigateway/infrastructure/config/SecurityConfig.java:76`

- [ ] **Step 1: Replace blanket CSRF disable with selective CSRF**

Replace line 76:
```java
// OLD:
.csrf(ServerHttpSecurity.CsrfSpec::disable)
// NEW:
.csrf(csrf -> csrf
    .csrfTokenRepository(org.springframework.security.web.server.csrf.CookieServerCsrfTokenRepository.withHttpOnlyFalse())
    .requireCsrfProtectionMatcher(org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers.pathMatchers("/auth/refresh"))
)
```

This enables CSRF only on the `/auth/refresh` endpoint that consumes the `vnshop_rt` cookie. All other endpoints use Bearer tokens and don't need CSRF.

- [ ] **Step 2: Verify gateway compiles**

```bash
cd services/api-gateway && mvn compile -q
```

- [ ] **Step 3: Commit**

```bash
git add services/api-gateway/
git commit -m "security(gateway): enable CSRF protection on cookie-based /auth/refresh endpoint"
```

---

## Stage 5: Terraform NAT Gateway HA (Task 9)

### Task 9: Fix single NAT Gateway SPOF — create one per AZ

**Files:**
- Modify: `infra/terraform/modules/vpc/main.tf:45-60`
- Modify: `infra/terraform/modules/vpc/variables.tf`
- Modify: `infra/terraform/modules/vpc/outputs.tf`

- [ ] **Step 1: Update EIP and NAT GW resources to per-AZ**

In `infra/terraform/modules/vpc/main.tf`, replace:
```hcl
# OLD (lines 45-60):
resource "aws_eip" "nat" {
  count  = var.enable_nat_gateway ? 1 : 0
  domain = "vpc"
  tags = {
    Name = "${var.project}-${var.environment}-nat-eip"
  }
}

resource "aws_nat_gateway" "main" {
  count         = var.enable_nat_gateway ? 1 : 0
  allocation_id = aws_eip.nat[0].id
  subnet_id     = aws_subnet.public[0].id
  tags = {
    Name = "${var.project}-${var.environment}-nat"
  }
}
```

With:
```hcl
# NEW: One NAT GW per AZ for HA
resource "aws_eip" "nat" {
  count  = var.enable_nat_gateway ? length(var.availability_zones) : 0
  domain = "vpc"
  tags = {
    Name = "${var.project}-${var.environment}-nat-eip-${count.index + 1}"
  }
}

resource "aws_nat_gateway" "main" {
  count         = var.enable_nat_gateway ? length(var.availability_zones) : 0
  allocation_id = aws_eip.nat[count.index].id
  subnet_id     = aws_subnet.public[count.index].id
  tags = {
    Name = "${var.project}-${var.environment}-nat-${count.index + 1}"
  }
}
```

- [ ] **Step 2: Update private route tables to use per-AZ NAT GW**

Replace the single private route table with per-AZ route tables:
```hcl
resource "aws_route_table" "private" {
  count  = var.enable_nat_gateway ? length(var.availability_zones) : 1
  vpc_id = aws_vpc.main.id

  dynamic "route" {
    for_each = var.enable_nat_gateway ? [1] : []
    content {
      cidr_block     = "0.0.0.0/0"
      nat_gateway_id = aws_nat_gateway.main[count.index].id
    }
  }

  tags = {
    Name = "${var.project}-${var.environment}-private-rt-${count.index + 1}"
  }
}

resource "aws_route_table_association" "private" {
  count          = length(var.availability_zones)
  subnet_id      = aws_subnet.private[count.index].id
  route_table_id = aws_route_table.private[var.enable_nat_gateway ? count.index : 0].id
}
```

- [ ] **Step 3: Add availability_zones variable if missing**

In `variables.tf`, ensure:
```hcl
variable "availability_zones" {
  description = "List of AZs to deploy into"
  type        = list(string)
  default     = ["a", "b", "c"]
}
```

- [ ] **Step 4: Validate terraform**

```bash
cd infra/terraform && terraform init -backend=false && terraform validate
```
Expected: Success! The configuration is valid.

- [ ] **Step 5: Commit**

```bash
git add infra/terraform/modules/vpc/
git commit -m "infra(terraform): per-AZ NAT gateways to eliminate single-AZ SPOF"
```

---

## Stage 6: Method-Level Authorization (Task 10)

### Task 10: Add @PreAuthorize annotations to sensitive service endpoints

**Files:**
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/web/OrderController.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/web/CheckoutController.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/web/SellerOrderController.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/admin/AdminDashboardController.java`
- Modify: `services/payment-service/src/main/java/.../PaymentController.java`
- Modify: `services/inventory-service/src/main/java/.../FlashSaleController.java`

- [ ] **Step 1: Enable method security on order-service**

Ensure `@EnableMethodSecurity` is present on the order-service main config or security config class. If not, add:
```java
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class MethodSecurityConfig {}
```

- [ ] **Step 2: Add @PreAuthorize to order-service buyer endpoints**

In `OrderController.java`, add to each buyer method:
```java
@PreAuthorize("isAuthenticated()")
@PostMapping
public ApiResponse<OrderResponse> createOrder(...) { ... }

@PreAuthorize("isAuthenticated()")
@GetMapping
public ApiResponse<Page<OrderSummary>> listOrders(...) { ... }

@PreAuthorize("isAuthenticated()")
@GetMapping("/{id}")
public ApiResponse<OrderDetailResponse> viewOrder(...) { ... }

@PreAuthorize("isAuthenticated()")
@DeleteMapping("/{id}/cancel")
public ApiResponse<Void> cancelOrder(...) { ... }
```

- [ ] **Step 3: Add @PreAuthorize to seller endpoints**

In `SellerOrderController.java`:
```java
@PreAuthorize("hasRole('SELLER')")
@GetMapping("/seller/orders/pending")
public ApiResponse<List<SubOrderResponse>> listPending(...) { ... }

@PreAuthorize("hasRole('SELLER')")
@PutMapping("/seller/orders/{subOrderId}/accept")
public ApiResponse<Void> accept(...) { ... }

@PreAuthorize("hasRole('SELLER')")
@PutMapping("/seller/orders/{subOrderId}/ship")
public ApiResponse<Void> ship(...) { ... }
```

- [ ] **Step 4: Add @PreAuthorize to admin endpoints**

In `AdminDashboardController.java`:
```java
@PreAuthorize("hasRole('ADMIN')")
@GetMapping("/admin/dashboard/summary")
public ApiResponse<DashboardSummary> summary() { ... }
```

Apply the same pattern to all admin controllers: `AdminDisputeController`, `AdminFinanceController`.

- [ ] **Step 5: Add @PreAuthorize to payment-service sensitive endpoints**

In PaymentController, protect admin-only confirmations:
```java
@PreAuthorize("hasRole('ADMIN')")
@PostMapping("/admin/vietqr/confirm/{paymentId}")
public ApiResponse<PaymentResponse> confirmVietQr(...) { ... }
```

- [ ] **Step 6: Enable method security on each service that needs it**

For payment-service and inventory-service, add:
```java
@Configuration
@EnableMethodSecurity(prePostEnabled = true)
public class MethodSecurityConfig {}
```

- [ ] **Step 7: Run all affected service tests**

```bash
cd services/order-service && mvn test -q
cd services/payment-service && mvn test -q
```
Expected: All pass. If SecurityContext tests fail, add `@WithMockUser` annotations to test methods.

- [ ] **Step 8: Commit**

```bash
git add services/order-service/ services/payment-service/ services/inventory-service/
git commit -m "security(auth): add method-level @PreAuthorize on all sensitive endpoints"
```

---

## Phase 4 Complete — Verification Checklist

- [ ] `grep -r 'ssl.endpoint.identification.algorithm: ""' services/` returns 0 results
- [ ] `grep 'POSTGRES_PASSWORD: vnshop123' docker-compose.yml` returns 0 results
- [ ] `grep 'auth-pass mymaster vnshop123' infra/redis/` returns 0 results
- [ ] `grep -r 'import.*infrastructure' services/order-service/src/main/java/com/vnshop/orderservice/application/` returns 0 results
- [ ] `cd services/order-service && mvn compile -q` passes
- [ ] `cd services/api-gateway && mvn compile -q` passes
- [ ] `terraform validate` passes in infra/terraform/
