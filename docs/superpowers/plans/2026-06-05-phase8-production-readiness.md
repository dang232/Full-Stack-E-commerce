# Phase 8: Production Readiness & Operational Excellence — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Final hardening for production launch — activate real notification channels, automate backups, add CD smoke tests, implement delivery confirmation, product soft-delete, guest cart backend, and remaining infrastructure hardening.

**Architecture:** Enable AWS SES for email, add K8s CronJob for DB backups, add post-deploy verification to CD pipeline, implement buyer delivery confirmation endpoint, add soft-delete to product lifecycle, create session-based guest cart, populate prod K8s overlay.

**Tech Stack:** AWS SES, K8s CronJobs, GitHub Actions, Spring Boot, NestJS, cosign, SpotBugs

**Depends on:** Phases 5, 6, 7 complete (all structural changes landed)

---

## What's Wrong (Evidence)

| # | Problem | Detail |
|---|---------|--------|
| 1 | Email in stub mode | `EMAIL_ENABLED=false` — SES adapter scaffolded but never activated |
| 2 | No backup automation | `infra/backups/` has README only, no actual backup.sh or CronJob |
| 3 | No CD smoke tests | cd.yml builds+pushes images but never verifies deployment health |
| 4 | No delivery confirmation | Order has `SHIPPED` state but no buyer-confirmed-delivery endpoint |
| 5 | No product soft-delete | Sellers cannot remove products — no delete endpoint |
| 6 | No backend guest cart | cart-service requires `x-user-id` header — anonymous users blocked |
| 7 | K8s prod overlay empty | `infra/k8s/overlays/prod/` exists but no kustomization.yaml |
| 8 | No image signing | Images pushed to GHCR without cosign/Notation verification |
| 9 | No input sanitization | Only 1 file references HTML sanitization |
| 10 | E2E not in CI | 47 Playwright specs exist but no CI workflow runs them |
| 11 | No Java static analysis | No SpotBugs/PMD/Error Prone in build |

---

## Stage 1: Notification Channels (Task 1)

### Task 1: Activate AWS SES email and add Firebase push adapter

**Files:**
- Modify: `services/notification-service/src/` (enable email channel)
- Create: `services/notification-service/src/infrastructure/channel/email-ses.adapter.ts`
- Create: `services/notification-service/src/infrastructure/channel/push-fcm.adapter.ts`

- [ ] **Step 1: Enable SES email adapter**

In notification-service config, change:
```yaml
# OLD:
EMAIL_ENABLED: false
# NEW:
EMAIL_ENABLED: ${EMAIL_ENABLED:-true}
```

Update/create the SES adapter:
```typescript
// src/infrastructure/channel/email-ses.adapter.ts
import { SESClient, SendEmailCommand } from '@aws-sdk/client-ses';
import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';

@Injectable()
export class SesEmailAdapter {
  private readonly ses: SESClient;
  private readonly fromAddress: string;

  constructor(private config: ConfigService) {
    this.ses = new SESClient({
      region: config.get('AWS_REGION', 'ap-southeast-1'),
      credentials: {
        accessKeyId: config.get('AWS_ACCESS_KEY_ID', ''),
        secretAccessKey: config.get('AWS_SECRET_ACCESS_KEY', ''),
      },
    });
    this.fromAddress = config.get('EMAIL_FROM', 'noreply@vnshop.vn');
  }

  async send(to: string, subject: string, htmlBody: string): Promise<void> {
    await this.ses.send(new SendEmailCommand({
      Source: this.fromAddress,
      Destination: { ToAddresses: [to] },
      Message: {
        Subject: { Data: subject },
        Body: { Html: { Data: htmlBody } },
      },
    }));
  }
}
```

- [ ] **Step 2: Install AWS SDK dependency**

```bash
cd services/notification-service && npm install @aws-sdk/client-ses
```

- [ ] **Step 3: Create Firebase push adapter**

```typescript
// src/infrastructure/channel/push-fcm.adapter.ts
import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as admin from 'firebase-admin';

@Injectable()
export class FcmPushAdapter {
  private readonly logger = new Logger(FcmPushAdapter.name);
  private app: admin.app.App | null = null;

  constructor(private config: ConfigService) {
    const serviceAccount = config.get('FIREBASE_SERVICE_ACCOUNT');
    if (serviceAccount) {
      this.app = admin.initializeApp({
        credential: admin.credential.cert(JSON.parse(serviceAccount)),
      });
    } else {
      this.logger.warn('Firebase not configured — push notifications disabled');
    }
  }

  async send(deviceToken: string, title: string, body: string, data?: Record<string, string>): Promise<void> {
    if (!this.app) return;
    await admin.messaging(this.app).send({
      token: deviceToken,
      notification: { title, body },
      data,
    });
  }
}
```

- [ ] **Step 4: Install Firebase Admin SDK**

```bash
cd services/notification-service && npm install firebase-admin
```

- [ ] **Step 5: Wire adapters into notification dispatch logic**

Update the notification service's dispatch method to route by user channel preference:
```typescript
async dispatch(notification: Notification): Promise<void> {
  // Always: in-app (existing Socket.IO)
  await this.socketGateway.emit(notification);

  // Email if user has email preference
  if (notification.channels.includes('email') && notification.userEmail) {
    await this.sesAdapter.send(notification.userEmail, notification.title, notification.htmlBody);
  }

  // Push if user has device token
  if (notification.channels.includes('push') && notification.deviceToken) {
    await this.fcmAdapter.send(notification.deviceToken, notification.title, notification.body);
  }
}
```

- [ ] **Step 6: Commit**

```bash
git add services/notification-service/
git commit -m "feat(notifications): activate SES email channel and add Firebase push adapter"
```

---

## Stage 2: Database Backup Automation (Task 2)

### Task 2: Create backup script and K8s CronJob

**Files:**
- Create: `infra/backups/backup.sh`
- Create: `infra/k8s/base/jobs/db-backup-cronjob.yaml`

- [ ] **Step 1: Create backup script**

Create `infra/backups/backup.sh`:
```bash
#!/bin/bash
set -euo pipefail

# Configuration
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_DIR="/tmp/backups/${TIMESTAMP}"
S3_BUCKET="${S3_BACKUP_BUCKET:-vnshop-backups}"
RETENTION_DAYS="${BACKUP_RETENTION_DAYS:-30}"

# Database list (service -> database name)
DATABASES=(
  "postgres-order:order_svc"
  "postgres-payment:payment_svc"
  "postgres-user:user_svc"
  "postgres-product:product_svc"
  "postgres-inventory:inventory_svc"
  "postgres-shipping:shipping_svc"
  "postgres-search:search_svc"
  "postgres-seller-finance:seller_finance_svc"
)

mkdir -p "${BACKUP_DIR}"

echo "[$(date)] Starting backup run ${TIMESTAMP}"

for entry in "${DATABASES[@]}"; do
  HOST="${entry%%:*}"
  DB="${entry##*:}"
  DUMP_FILE="${BACKUP_DIR}/${DB}-${TIMESTAMP}.sql.gz"

  echo "  Backing up ${DB} from ${HOST}..."
  PGPASSWORD="${POSTGRES_PASSWORD}" pg_dump \
    -h "${HOST}" -U vnshop -d "${DB}" \
    --no-owner --no-acl \
    | gzip > "${DUMP_FILE}"

  echo "  Uploading ${DUMP_FILE} to S3..."
  aws s3 cp "${DUMP_FILE}" "s3://${S3_BUCKET}/${DB}/${TIMESTAMP}.sql.gz" \
    --storage-class STANDARD_IA
done

# Cleanup old backups
echo "  Pruning backups older than ${RETENTION_DAYS} days..."
CUTOFF=$(date -d "-${RETENTION_DAYS} days" +%Y%m%d)
for entry in "${DATABASES[@]}"; do
  DB="${entry##*:}"
  aws s3 ls "s3://${S3_BUCKET}/${DB}/" | while read -r line; do
    FILE_DATE=$(echo "${line}" | awk '{print $4}' | grep -oP '^\d{8}')
    if [[ "${FILE_DATE}" < "${CUTOFF}" ]]; then
      FILE=$(echo "${line}" | awk '{print $4}')
      aws s3 rm "s3://${S3_BUCKET}/${DB}/${FILE}"
    fi
  done
done

# Cleanup local
rm -rf "${BACKUP_DIR}"
echo "[$(date)] Backup run ${TIMESTAMP} complete"
```

- [ ] **Step 2: Create K8s CronJob manifest**

Create `infra/k8s/base/jobs/db-backup-cronjob.yaml`:
```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: db-backup
  namespace: vnshop
spec:
  schedule: "0 2 * * *"  # Daily at 2 AM
  concurrencyPolicy: Forbid
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 3
  jobTemplate:
    spec:
      backoffLimit: 2
      template:
        spec:
          restartPolicy: OnFailure
          containers:
            - name: backup
              image: postgres:16-alpine
              command: ["/bin/bash", "/scripts/backup.sh"]
              envFrom:
                - secretRef:
                    name: db-backup-secrets
              volumeMounts:
                - name: backup-script
                  mountPath: /scripts
          volumes:
            - name: backup-script
              configMap:
                name: db-backup-script
                defaultMode: 0755
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: db-backup-script
  namespace: vnshop
data:
  backup.sh: |
    # Content from infra/backups/backup.sh
```

- [ ] **Step 3: Commit**

```bash
git add infra/backups/ infra/k8s/base/jobs/
git commit -m "infra(backups): add pg_dump backup script + K8s CronJob with S3 upload and 30-day retention"
```

---

## Stage 3: CD Smoke Tests (Task 3)

### Task 3: Add post-deploy health verification to CD pipeline

**Files:**
- Modify: `.github/workflows/cd.yml`

- [ ] **Step 1: Add smoke-test job to cd.yml**

Append after the `update-manifests` job:
```yaml
  smoke-test:
    needs: [update-manifests]
    runs-on: ubuntu-latest
    environment: staging
    steps:
      - name: Wait for rollout
        run: |
          for service in api-gateway order-service payment-service product-service; do
            echo "Waiting for $service rollout..."
            kubectl rollout status deployment/$service -n vnshop --timeout=300s
          done

      - name: Health check all services
        run: |
          GATEWAY_URL="${{ vars.STAGING_GATEWAY_URL }}"
          
          # Gateway health
          curl -sf "${GATEWAY_URL}/actuator/health" | jq -e '.status == "UP"'
          
          # Critical endpoints respond
          curl -sf "${GATEWAY_URL}/products?size=1" | jq -e '.success == true'
          curl -sf "${GATEWAY_URL}/categories" | jq -e '.success == true'
          curl -sf "${GATEWAY_URL}/search?q=test" | jq -e '.success == true'
          
          echo "All smoke tests passed"

      - name: Notify on failure
        if: failure()
        run: |
          curl -X POST "${{ secrets.SLACK_WEBHOOK_URL }}" \
            -H 'Content-Type: application/json' \
            -d '{"text":"⚠️ Staging smoke tests FAILED after deploy. Check CD workflow."}'
```

- [ ] **Step 2: Commit**

```bash
git add .github/workflows/cd.yml
git commit -m "ci(cd): add post-deploy smoke tests with health checks and Slack failure notification"
```

---

## Stage 4: Delivery Confirmation & Product Soft-Delete (Tasks 4-5)

### Task 4: Add buyer delivery confirmation endpoint

**Files:**
- Create: `services/order-service/src/main/java/com/vnshop/orderservice/application/ConfirmDeliveryUseCase.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/web/OrderController.java`
- Modify: `services/order-service/src/main/java/com/vnshop/orderservice/domain/SubOrder.java` (add DELIVERED state)

- [ ] **Step 1: Add DELIVERED status to SubOrder state machine**

In `SubOrder.java`, add `DELIVERED` to the state enum and transition rules:
```java
// Add to allowed transitions:
// SHIPPED -> DELIVERED (buyer confirms receipt)
case SHIPPED -> canTransitionTo.add(OrderStatus.DELIVERED);
```

- [ ] **Step 2: Create ConfirmDeliveryUseCase**

```java
package com.vnshop.orderservice.application;

import com.vnshop.orderservice.domain.Order;
import com.vnshop.orderservice.domain.SubOrder;
import com.vnshop.orderservice.domain.port.out.OrderRepositoryPort;
import com.vnshop.orderservice.domain.port.out.OrderEventPublisherPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class ConfirmDeliveryUseCase {
    private final OrderRepositoryPort orderRepository;
    private final OrderEventPublisherPort eventPublisher;

    public ConfirmDeliveryUseCase(OrderRepositoryPort orderRepository,
                                  OrderEventPublisherPort eventPublisher) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void confirm(UUID orderId, String subOrderId, String buyerId) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new IllegalArgumentException("Order not found: " + orderId));

        if (!order.buyerId().equals(buyerId)) {
            throw new IllegalArgumentException("Not the buyer of this order");
        }

        SubOrder subOrder = order.subOrders().stream()
            .filter(so -> so.id().equals(subOrderId))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("SubOrder not found: " + subOrderId));

        subOrder.confirmDelivery();  // Transitions SHIPPED -> DELIVERED
        orderRepository.save(order);
        eventPublisher.publishOrderDelivered(order, subOrder);
    }
}
```

- [ ] **Step 3: Add controller endpoint**

In `OrderController.java`:
```java
@PreAuthorize("isAuthenticated()")
@PostMapping("/{orderId}/sub-orders/{subOrderId}/confirm-delivery")
public ApiResponse<Void> confirmDelivery(
        @PathVariable UUID orderId,
        @PathVariable String subOrderId,
        @AuthenticationPrincipal Jwt jwt) {
    confirmDeliveryUseCase.confirm(orderId, subOrderId, jwt.getSubject());
    return ApiResponse.success(null);
}
```

- [ ] **Step 4: Commit**

```bash
git add services/order-service/
git commit -m "feat(order): add buyer delivery confirmation endpoint (SHIPPED -> DELIVERED)"
```

### Task 5: Add product soft-delete with search de-indexing

**Files:**
- Modify: `services/product-service/src/main/java/.../domain/Product.java`
- Modify: `services/product-service/src/main/java/.../infrastructure/web/SellerProductController.java`
- Modify: `services/product-service/src/main/java/.../application/DeleteProductUseCase.java` (NEW)

- [ ] **Step 1: Add DELETED status to Product lifecycle**

In Product domain entity, add `DELETED` to status enum (alongside DRAFT, ACTIVE, INACTIVE, OUT_OF_STOCK).

Add method:
```java
public void softDelete() {
    if (this.status == ProductStatus.DELETED) {
        throw new IllegalStateException("Product already deleted");
    }
    this.status = ProductStatus.DELETED;
}
```

- [ ] **Step 2: Create DeleteProductUseCase**

```java
package com.vnshop.productservice.application;

import com.vnshop.productservice.domain.Product;
import com.vnshop.productservice.domain.port.out.ProductRepositoryPort;
import com.vnshop.productservice.domain.port.out.ProductEventPublisherPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeleteProductUseCase {
    private final ProductRepositoryPort productRepository;
    private final ProductEventPublisherPort eventPublisher;

    public DeleteProductUseCase(ProductRepositoryPort productRepository,
                                ProductEventPublisherPort eventPublisher) {
        this.productRepository = productRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public void delete(String productId, String sellerId) {
        Product product = productRepository.findById(productId)
            .orElseThrow(() -> new IllegalArgumentException("Product not found"));

        if (!product.sellerId().equals(sellerId)) {
            throw new IllegalArgumentException("Not the seller of this product");
        }

        product.softDelete();
        productRepository.save(product);

        // Publish event → search-service will de-index
        eventPublisher.publishProductDeleted(productId);
    }
}
```

- [ ] **Step 3: Add DELETE endpoint to seller controller**

```java
@PreAuthorize("hasRole('SELLER')")
@DeleteMapping("/sellers/me/products/{productId}")
public ApiResponse<Void> deleteProduct(@PathVariable String productId,
                                       @AuthenticationPrincipal Jwt jwt) {
    deleteProductUseCase.delete(productId, jwt.getSubject());
    return ApiResponse.success(null);
}
```

- [ ] **Step 4: Commit**

```bash
git add services/product-service/
git commit -m "feat(product): add soft-delete with Kafka event for search de-indexing"
```

---

## Stage 5: Guest Cart Backend (Task 6)

### Task 6: Add session-token based guest cart to cart-service

**Files:**
- Modify: `services/cart-service/src/cart/cart.controller.ts`
- Modify: `services/cart-service/src/cart/domain/cart.ts`
- Create: `services/cart-service/src/cart/application/merge-cart.usecase.ts`

- [ ] **Step 1: Allow anonymous access with session token**

Update `cart.controller.ts` to accept either `x-user-id` or `x-session-id`:
```typescript
private getCartOwner(req: Request): { id: string; type: 'user' | 'guest' } {
  const userId = req.headers['x-user-id'] as string;
  if (userId) return { id: userId, type: 'user' };

  const sessionId = req.headers['x-session-id'] as string;
  if (sessionId) return { id: `guest:${sessionId}`, type: 'guest' };

  throw new BadRequestException('Either x-user-id or x-session-id header required');
}
```

- [ ] **Step 2: Add TTL for guest carts**

Guest carts get a 7-day TTL in Redis (user carts persist indefinitely):
```typescript
async saveCart(owner: CartOwner, cart: Cart): Promise<void> {
  const ttl = owner.type === 'guest' ? 7 * 24 * 60 * 60 : undefined;
  await this.redis.set(this.key(owner.id), JSON.stringify(cart), ttl ? 'EX' : undefined, ttl);
}
```

- [ ] **Step 3: Create merge-cart use case**

Create `merge-cart.usecase.ts`:
```typescript
import { Injectable } from '@nestjs/common';
import { CartRepository } from '../domain/port/out/cart-repository.port';
import { Cart } from '../domain/cart';

@Injectable()
export class MergeCartUseCase {
  constructor(private readonly cartRepo: CartRepository) {}

  /**
   * Merge guest cart into user cart on login.
   * Guest items are added to user cart (quantity summed for duplicates).
   * Guest cart is deleted after merge.
   */
  async merge(userId: string, guestSessionId: string): Promise<Cart> {
    const guestKey = `guest:${guestSessionId}`;
    const [userCart, guestCart] = await Promise.all([
      this.cartRepo.findByOwnerId(userId),
      this.cartRepo.findByOwnerId(guestKey),
    ]);

    if (!guestCart || guestCart.items.length === 0) {
      return userCart ?? Cart.empty(userId);
    }

    const merged = userCart ?? Cart.empty(userId);
    for (const guestItem of guestCart.items) {
      merged.addOrMergeItem(guestItem);
    }

    await this.cartRepo.save(userId, merged);
    await this.cartRepo.delete(guestKey);

    return merged;
  }
}
```

- [ ] **Step 4: Add merge endpoint**

```typescript
@Post('merge')
async mergeGuestCart(
  @Headers('x-user-id') userId: string,
  @Body() body: { sessionId: string }
): Promise<CartResponse> {
  if (!userId) throw new BadRequestException('x-user-id required for merge');
  const cart = await this.mergeCartUseCase.merge(userId, body.sessionId);
  return this.toResponse(cart);
}
```

- [ ] **Step 5: Write tests**

```typescript
describe('MergeCartUseCase', () => {
  it('should merge guest items into user cart', async () => {
    // Setup: guest cart has item A, user cart has item B
    // Action: merge
    // Assert: user cart has both A and B, guest cart deleted
  });

  it('should sum quantities for duplicate product+variant', async () => {
    // Setup: both carts have same product — qty 2 + qty 3
    // Assert: merged cart has qty 5
  });

  it('should handle empty guest cart gracefully', async () => {
    // No-op, return existing user cart
  });
});
```

- [ ] **Step 6: Commit**

```bash
git add services/cart-service/
git commit -m "feat(cart): add guest cart via x-session-id header with merge-on-login"
```

---

## Stage 6: Remaining Infrastructure & Quality (Tasks 7-9)

### Task 7: Populate K8s prod overlay

**Files:**
- Create: `infra/k8s/overlays/prod/kustomization.yaml`
- Create: `infra/k8s/overlays/prod/patches/replicas.yaml`
- Create: `infra/k8s/overlays/prod/patches/resources.yaml`

- [ ] **Step 1: Create prod kustomization**

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
namespace: vnshop-prod

resources:
  - ../../base

patches:
  - path: patches/replicas.yaml
  - path: patches/resources.yaml

images:
  - name: ghcr.io/dang232/vnshop-api-gateway
    newTag: latest
  - name: ghcr.io/dang232/vnshop-order-service
    newTag: latest
  # ... one per service
```

- [ ] **Step 2: Create replica patch**

```yaml
# patches/replicas.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
spec:
  replicas: 3
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: order-service
spec:
  replicas: 3
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: payment-service
spec:
  replicas: 2
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: product-service
spec:
  replicas: 2
```

- [ ] **Step 3: Create resource patch**

```yaml
# patches/resources.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: api-gateway
spec:
  template:
    spec:
      containers:
        - name: api-gateway
          resources:
            requests:
              memory: "512Mi"
              cpu: "500m"
            limits:
              memory: "1Gi"
              cpu: "1000m"
```

- [ ] **Step 4: Validate**

```bash
kubectl kustomize infra/k8s/overlays/prod/ --enable-helm > /dev/null
```

- [ ] **Step 5: Commit**

```bash
git add infra/k8s/overlays/prod/
git commit -m "infra(k8s): populate prod overlay with scaled replicas and resource tuning"
```

### Task 8: Add input sanitization for user-generated content

**Files:**
- Modify: `services/product-service/pom.xml`
- Create: `services/product-service/src/main/java/.../infrastructure/sanitization/HtmlSanitizer.java`
- Modify: Product/Review creation use cases

- [ ] **Step 1: Add OWASP Java HTML Sanitizer dependency**

In product-service and order-service pom.xml:
```xml
<dependency>
    <groupId>com.googlecode.owasp-java-html-sanitizer</groupId>
    <artifactId>owasp-java-html-sanitizer</artifactId>
    <version>20240325.1</version>
</dependency>
```

- [ ] **Step 2: Create sanitizer utility**

```java
package com.vnshop.productservice.infrastructure.sanitization;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.springframework.stereotype.Component;

@Component
public class HtmlSanitizer {
    private static final PolicyFactory POLICY = Sanitizers.FORMATTING
        .and(Sanitizers.LINKS)
        .and(Sanitizers.BLOCKS)
        .and(Sanitizers.TABLES);

    /** Sanitize user input — strips scripts, iframes, event handlers */
    public String sanitize(String untrusted) {
        if (untrusted == null) return null;
        return POLICY.sanitize(untrusted);
    }
}
```

- [ ] **Step 3: Apply to product description and review content**

In product creation/update use cases:
```java
product.setDescription(htmlSanitizer.sanitize(command.description()));
```

In review creation:
```java
review.setContent(htmlSanitizer.sanitize(command.content()));
```

- [ ] **Step 4: Commit**

```bash
git add services/product-service/ services/order-service/
git commit -m "security(xss): add OWASP HTML sanitization for product descriptions and reviews"
```

### Task 9: Add Java static analysis (SpotBugs) and E2E smoke in CI

**Files:**
- Modify: Parent `pom.xml` or each service pom.xml
- Modify: `.github/workflows/ci.yml`

- [ ] **Step 1: Add SpotBugs Maven plugin**

In the parent/shared pom.xml plugin management:
```xml
<plugin>
    <groupId>com.github.spotbugs</groupId>
    <artifactId>spotbugs-maven-plugin</artifactId>
    <version>4.8.6.6</version>
    <configuration>
        <effort>Max</effort>
        <threshold>High</threshold>
        <failOnError>true</failOnError>
    </configuration>
    <executions>
        <execution>
            <goals><goal>check</goal></goals>
            <phase>verify</phase>
        </execution>
    </executions>
</plugin>
```

- [ ] **Step 2: Add E2E smoke subset to CI workflow**

In `.github/workflows/ci.yml`, add a job:
```yaml
  e2e-smoke:
    needs: [test-java, test-node]
    runs-on: ubuntu-latest
    services:
      postgres-order:
        image: postgres:16-alpine
        env:
          POSTGRES_PASSWORD: test
          POSTGRES_DB: order_svc
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-node@v4
        with:
          node-version: '20'
      - name: Install Playwright
        run: cd fe && npm ci && npx playwright install chromium
      - name: Run smoke tests
        run: cd fe && npx playwright test --grep "@smoke" --reporter=github
```

- [ ] **Step 3: Tag smoke tests in Playwright**

Add `@smoke` tag to critical specs:
```typescript
test.describe('@smoke Auth flow', () => { ... });
test.describe('@smoke Product browse', () => { ... });
```

- [ ] **Step 4: Commit**

```bash
git add .github/workflows/ services/*/pom.xml fe/tests/
git commit -m "quality(ci): add SpotBugs static analysis and Playwright @smoke subset to CI"
```

---

## Phase 8 Complete — Verification Checklist

- [ ] Email notifications send via SES (test with `POST /notifications/test`)
- [ ] Backup script runs successfully: `bash infra/backups/backup.sh` (with local Postgres)
- [ ] CD pipeline smoke-test job passes on staging deploy
- [ ] `POST /orders/{id}/sub-orders/{subId}/confirm-delivery` transitions to DELIVERED
- [ ] `DELETE /sellers/me/products/{id}` soft-deletes and de-indexes from search
- [ ] Guest cart works with `x-session-id` header, merges on login
- [ ] `kubectl kustomize infra/k8s/overlays/prod/` produces valid manifests
- [ ] `mvn spotbugs:check` passes on all Java services
- [ ] Playwright `@smoke` tests pass in CI
- [ ] OWASP sanitizer strips `<script>` from product descriptions
