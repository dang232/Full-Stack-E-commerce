# Phase 3B: Deployment & Release — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

---

## Goal

Establish a fully automated Continuous Deployment pipeline that builds, tags, and pushes Docker images to GitHub Container Registry on every successful CI run against `main`, then updates Kubernetes manifest image tags for staging. Additionally, introduce a self-hosted Unleash feature flag system integrated into all Java and Node services via hexagonal architecture ports/adapters.

## Architecture

```
GitHub Actions CI (existing) ──workflow_run──> CD Pipeline
  ├── Detect changed services (dorny/paths-filter)
  ├── Build & push Docker images to ghcr.io
  └── Update kustomization.yaml image tags (staging overlay)

Unleash Server (docker-compose) ──HTTP API──> Java/Node Services
  ├── FeatureFlagPort (domain/port/out)
  └── UnleashFeatureFlagAdapter (infrastructure/featureflag)
```

## Tech Stack Additions

| Component | Version | Purpose |
|-----------|---------|---------|
| GitHub Container Registry | N/A | Docker image hosting |
| Unleash Server | 6.x | Feature flag management |
| unleash-client-java | 9.2.4 | Spring Boot SDK |
| unleash-client-node | 6.1.2 | NestJS SDK |
| PostgreSQL (unleash) | 17.9 | Unleash persistence |

## File Structure

### Files Created

```
.github/workflows/cd.yml                                          # B1: CD pipeline
docs/feature-flag-guidelines.md                                   # B2: Feature flag docs

# Unleash infrastructure
infra/unleash/init-flags.sh                                       # B2: Seed flags script
infra/k8s/base/services/unleash/deployment.yaml                   # B2: K8s deployment
infra/k8s/base/services/unleash/service.yaml                      # B2: K8s service
infra/k8s/base/services/unleash/configmap.yaml                    # B2: K8s configmap

# Java services - FeatureFlagPort (domain layer)
services/api-gateway/src/main/java/com/vnshop/apigateway/domain/port/out/FeatureFlagPort.java
services/user-service/src/main/java/com/vnshop/userservice/domain/port/out/FeatureFlagPort.java
services/product-service/src/main/java/com/vnshop/productservice/domain/port/out/FeatureFlagPort.java
services/inventory-service/src/main/java/com/vnshop/inventoryservice/domain/port/out/FeatureFlagPort.java
services/search-service/src/main/java/com/vnshop/searchservice/domain/port/out/FeatureFlagPort.java
services/order-service/src/main/java/com/vnshop/orderservice/domain/port/out/FeatureFlagPort.java
services/payment-service/src/main/java/com/vnshop/paymentservice/domain/port/out/FeatureFlagPort.java
services/shipping-service/src/main/java/com/vnshop/shippingservice/domain/port/out/FeatureFlagPort.java
services/coupon-service/src/main/java/com/vnshop/couponservice/domain/port/out/FeatureFlagPort.java
services/seller-finance-service/src/main/java/com/vnshop/sellerfinanceservice/domain/port/out/FeatureFlagPort.java
services/recommendations-service/src/main/java/com/vnshop/recommendationsservice/domain/port/out/FeatureFlagPort.java

# Java services - UnleashFeatureFlagAdapter (infrastructure layer)
services/api-gateway/src/main/java/com/vnshop/apigateway/infrastructure/featureflag/UnleashFeatureFlagAdapter.java
services/api-gateway/src/main/java/com/vnshop/apigateway/infrastructure/featureflag/UnleashConfig.java
services/user-service/src/main/java/com/vnshop/userservice/infrastructure/featureflag/UnleashFeatureFlagAdapter.java
services/user-service/src/main/java/com/vnshop/userservice/infrastructure/featureflag/UnleashConfig.java
services/product-service/src/main/java/com/vnshop/productservice/infrastructure/featureflag/UnleashFeatureFlagAdapter.java
services/product-service/src/main/java/com/vnshop/productservice/infrastructure/featureflag/UnleashConfig.java
services/inventory-service/src/main/java/com/vnshop/inventoryservice/infrastructure/featureflag/UnleashFeatureFlagAdapter.java
services/inventory-service/src/main/java/com/vnshop/inventoryservice/infrastructure/featureflag/UnleashConfig.java
services/search-service/src/main/java/com/vnshop/searchservice/infrastructure/featureflag/UnleashFeatureFlagAdapter.java
services/search-service/src/main/java/com/vnshop/searchservice/infrastructure/featureflag/UnleashConfig.java
services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/featureflag/UnleashFeatureFlagAdapter.java
services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/featureflag/UnleashConfig.java
services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/featureflag/UnleashFeatureFlagAdapter.java
services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/featureflag/UnleashConfig.java
services/shipping-service/src/main/java/com/vnshop/shippingservice/infrastructure/featureflag/UnleashFeatureFlagAdapter.java
services/shipping-service/src/main/java/com/vnshop/shippingservice/infrastructure/featureflag/UnleashConfig.java
services/coupon-service/src/main/java/com/vnshop/couponservice/infrastructure/featureflag/UnleashFeatureFlagAdapter.java
services/coupon-service/src/main/java/com/vnshop/couponservice/infrastructure/featureflag/UnleashConfig.java
services/seller-finance-service/src/main/java/com/vnshop/sellerfinanceservice/infrastructure/featureflag/UnleashFeatureFlagAdapter.java
services/seller-finance-service/src/main/java/com/vnshop/sellerfinanceservice/infrastructure/featureflag/UnleashConfig.java
services/recommendations-service/src/main/java/com/vnshop/recommendationsservice/infrastructure/featureflag/UnleashFeatureFlagAdapter.java
services/recommendations-service/src/main/java/com/vnshop/recommendationsservice/infrastructure/featureflag/UnleashConfig.java

# Node services - feature flag module
services/cart-service/src/feature-flag/feature-flag.port.ts
services/cart-service/src/feature-flag/feature-flag.module.ts
services/cart-service/src/feature-flag/unleash-feature-flag.adapter.ts
services/notification-service/src/feature-flag/feature-flag.port.ts
services/notification-service/src/feature-flag/feature-flag.module.ts
services/notification-service/src/feature-flag/unleash-feature-flag.adapter.ts
```

### Files Modified

```
docker-compose.yml                                                # B2: Add unleash + postgres-unleash
infra/k8s/base/kustomization.yaml                                 # B2: Add unleash resources
infra/k8s/overlays/staging/kustomization.yaml                     # B1: Image tag update + unleash
infra/k8s/overlays/prod/kustomization.yaml                        # B1: Image tag update + unleash

# Java services - pom.xml (add unleash-client-java dependency)
services/api-gateway/pom.xml
services/user-service/pom.xml
services/product-service/pom.xml
services/inventory-service/pom.xml
services/search-service/pom.xml
services/order-service/pom.xml
services/payment-service/pom.xml
services/shipping-service/pom.xml
services/coupon-service/pom.xml
services/seller-finance-service/pom.xml
services/recommendations-service/pom.xml

# Java services - application.yml (add unleash config)
services/api-gateway/src/main/resources/application.yml
services/user-service/src/main/resources/application.yml
services/product-service/src/main/resources/application.yml
services/inventory-service/src/main/resources/application.yml
services/search-service/src/main/resources/application.yml
services/order-service/src/main/resources/application.yml
services/payment-service/src/main/resources/application.yml
services/shipping-service/src/main/resources/application.yml
services/coupon-service/src/main/resources/application.yml
services/seller-finance-service/src/main/resources/application.yml
services/recommendations-service/src/main/resources/application.yml

# Node services - package.json (add unleash-client)
services/cart-service/package.json
services/notification-service/package.json

# Docker-compose env for unleash
services/cart-service/src/app.module.ts
services/notification-service/src/app.module.ts
```

---

## Task B1: CD Pipeline (GitHub Actions -> GHCR)

### Overview
Create `.github/workflows/cd.yml` triggered by CI success on `main`. Detects changed services via paths-filter, builds Docker images for linux/amd64, pushes to `ghcr.io`, and updates K8s manifest tags in the staging overlay.

### Steps

- [ ] **B1.1** Create `.github/workflows/cd.yml`

```yaml
# File: .github/workflows/cd.yml
name: VNShop CD

on:
  workflow_run:
    workflows: ["VNShop CI"]
    types: [completed]
    branches: [main]

permissions:
  contents: write
  packages: write

concurrency:
  group: cd-${{ github.ref }}
  cancel-in-progress: false

env:
  REGISTRY: ghcr.io
  IMAGE_PREFIX: ghcr.io/${{ github.repository_owner }}/vnshop

jobs:
  # ── Gate: only proceed if CI passed ──
  gate:
    name: CI gate
    runs-on: ubuntu-latest
    if: github.event.workflow_run.conclusion == 'success'
    outputs:
      proceed: ${{ steps.check.outputs.proceed }}
    steps:
      - name: Check CI result
        id: check
        run: echo "proceed=true" >> "$GITHUB_OUTPUT"

  # ── Detect what changed ──
  changes:
    name: Detect changes
    runs-on: ubuntu-latest
    needs: gate
    if: needs.gate.outputs.proceed == 'true'
    outputs:
      services: ${{ steps.set-matrix.outputs.services }}
      has_changes: ${{ steps.set-matrix.outputs.has_changes }}
    steps:
      - uses: actions/checkout@v4
        with:
          ref: ${{ github.event.workflow_run.head_sha }}
          fetch-depth: 2

      - name: Filter paths
        id: filter
        uses: dorny/paths-filter@v3
        with:
          base: ${{ github.event.workflow_run.head_sha }}~1
          ref: ${{ github.event.workflow_run.head_sha }}
          filters: |
            api-gateway:
              - 'services/api-gateway/**'
            user-service:
              - 'services/user-service/**'
            product-service:
              - 'services/product-service/**'
            inventory-service:
              - 'services/inventory-service/**'
            search-service:
              - 'services/search-service/**'
            order-service:
              - 'services/order-service/**'
            payment-service:
              - 'services/payment-service/**'
            shipping-service:
              - 'services/shipping-service/**'
            cart-service:
              - 'services/cart-service/**'
            notification-service:
              - 'services/notification-service/**'
            coupon-service:
              - 'services/coupon-service/**'
            seller-finance-service:
              - 'services/seller-finance-service/**'
            recommendations-service:
              - 'services/recommendations-service/**'
            messaging-service:
              - 'services/messaging-service/**'
            monitoring-service-v2:
              - 'services/monitoring-service-v2/**'
            configuration-service:
              - 'services/configuration-service/**'
            frontend:
              - 'fe/**'

      - name: Build service matrix
        id: set-matrix
        run: |
          SERVICES=()

          # Java services (context = repo root, dockerfile = services/<name>/Dockerfile)
          for svc in api-gateway user-service product-service inventory-service search-service order-service payment-service shipping-service coupon-service seller-finance-service recommendations-service; do
            if [ "${{ steps.filter.outputs[format('{0}', svc)] }}" == "true" ]; then
              SERVICES+=("{\"name\":\"$svc\",\"context\":\".\",\"dockerfile\":\"services/$svc/Dockerfile\"}")
            fi
          done

          # Node services (context = services/<name>)
          for svc in cart-service notification-service messaging-service monitoring-service-v2 configuration-service; do
            if [ "${{ steps.filter.outputs[format('{0}', svc)] }}" == "true" ]; then
              SERVICES+=("{\"name\":\"$svc\",\"context\":\"./services/$svc\",\"dockerfile\":\"./services/$svc/Dockerfile\"}")
            fi
          done

          # Frontend
          if [ "${{ steps.filter.outputs.frontend }}" == "true" ]; then
            SERVICES+=("{\"name\":\"frontend\",\"context\":\"./fe\",\"dockerfile\":\"./fe/Dockerfile\"}")
          fi

          if [ ${#SERVICES[@]} -eq 0 ]; then
            echo "has_changes=false" >> "$GITHUB_OUTPUT"
            echo "services=[]" >> "$GITHUB_OUTPUT"
          else
            JSON=$(printf '%s,' "${SERVICES[@]}")
            JSON="[${JSON%,}]"
            echo "has_changes=true" >> "$GITHUB_OUTPUT"
            echo "services=$JSON" >> "$GITHUB_OUTPUT"
          fi

  # ── Build & Push Docker images ──
  build-push:
    name: Build & Push (${{ matrix.service.name }})
    runs-on: ubuntu-latest
    needs: changes
    if: needs.changes.outputs.has_changes == 'true'
    strategy:
      fail-fast: false
      matrix:
        service: ${{ fromJson(needs.changes.outputs.services) }}
    steps:
      - uses: actions/checkout@v4
        with:
          ref: ${{ github.event.workflow_run.head_sha }}

      - name: Set up Docker Buildx
        uses: docker/setup-buildx-action@v3

      - name: Log in to GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ghcr.io
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Generate image metadata
        id: meta
        uses: docker/metadata-action@v5
        with:
          images: ${{ env.IMAGE_PREFIX }}-${{ matrix.service.name }}
          tags: |
            type=sha,prefix=sha-,format=short
            type=raw,value=latest
            type=raw,value=v{{date 'YYYYMMDD'}}-{{sha}}

      - name: Build and push
        uses: docker/build-push-action@v6
        with:
          context: ${{ matrix.service.context }}
          file: ${{ matrix.service.dockerfile }}
          platforms: linux/amd64
          push: true
          tags: ${{ steps.meta.outputs.tags }}
          labels: ${{ steps.meta.outputs.labels }}
          cache-from: type=gha
          cache-to: type=gha,mode=max

  # ── Update K8s staging manifests ──
  update-manifests:
    name: Update staging manifests
    runs-on: ubuntu-latest
    needs: [changes, build-push]
    if: needs.changes.outputs.has_changes == 'true'
    steps:
      - uses: actions/checkout@v4
        with:
          ref: main
          token: ${{ secrets.GITHUB_TOKEN }}

      - name: Update staging kustomization image tags
        run: |
          SHORT_SHA=$(echo "${{ github.event.workflow_run.head_sha }}" | cut -c1-7)
          TAG="sha-${SHORT_SHA}"
          KUSTOMIZATION="infra/k8s/overlays/staging/kustomization.yaml"

          SERVICES='${{ needs.changes.outputs.services }}'
          echo "$SERVICES" | jq -r '.[].name' | while read -r SVC; do
            # Skip frontend — not in kustomization images
            if [ "$SVC" == "frontend" ]; then
              continue
            fi

            IMAGE_NAME="vnshop/${SVC}"

            # Use sed to update the newTag for the matching image name
            # Match pattern: the line after "- name: <image>" that contains "newTag:"
            sed -i -E "/- name: ${IMAGE_NAME//\//\\/}$/,/newTag:/{s/(newTag:).*/\1 ${TAG}/}" "$KUSTOMIZATION"
          done

          echo "Updated $KUSTOMIZATION with tag: $TAG"
          cat "$KUSTOMIZATION"

      - name: Commit and push manifest update
        run: |
          git config user.name "github-actions[bot]"
          git config user.email "github-actions[bot]@users.noreply.github.com"
          git add infra/k8s/overlays/staging/kustomization.yaml
          if git diff --cached --quiet; then
            echo "No changes to commit"
          else
            SHORT_SHA=$(echo "${{ github.event.workflow_run.head_sha }}" | cut -c1-7)
            git commit -m "cd: update staging image tags to sha-${SHORT_SHA}"
            git push
          fi
```

- [ ] **B1.2** Verify the CD workflow file is valid YAML

```bash
# From repo root
cat .github/workflows/cd.yml | python3 -c "import sys,yaml; yaml.safe_load(sys.stdin); print('YAML valid')"
```

- [ ] **B1.3** Verify workflow_run trigger references correct CI workflow name

The existing CI workflow at `.github/workflows/ci.yml` has `name: VNShop CI`. The CD workflow uses `workflows: ["VNShop CI"]` which matches exactly.

- [ ] **B1.4** Verify image name convention matches kustomization.yaml

The staging overlay uses `name: vnshop/api-gateway` etc. The CD pipeline uses `${{ env.IMAGE_PREFIX }}-${{ matrix.service.name }}` which resolves to `ghcr.io/<owner>/vnshop-api-gateway`. The kustomization `newName` field will be set separately via a one-time update (see B1.5).

- [ ] **B1.5** Update staging kustomization to use GHCR image names

Modify `infra/k8s/overlays/staging/kustomization.yaml` to include `newName` pointing to GHCR:

Add `newName` to each image entry. The images section becomes:

```yaml
# In infra/k8s/overlays/staging/kustomization.yaml - replace the images section:
images:
- name: vnshop/api-gateway
  newName: ghcr.io/dang232/vnshop-api-gateway
  newTag: latest
- name: vnshop/user-service
  newName: ghcr.io/dang232/vnshop-user-service
  newTag: latest
- name: vnshop/product-service
  newName: ghcr.io/dang232/vnshop-product-service
  newTag: latest
- name: vnshop/inventory-service
  newName: ghcr.io/dang232/vnshop-inventory-service
  newTag: latest
- name: vnshop/search-service
  newName: ghcr.io/dang232/vnshop-search-service
  newTag: latest
- name: vnshop/cart-service
  newName: ghcr.io/dang232/vnshop-cart-service
  newTag: latest
- name: vnshop/order-service
  newName: ghcr.io/dang232/vnshop-order-service
  newTag: latest
- name: vnshop/payment-service
  newName: ghcr.io/dang232/vnshop-payment-service
  newTag: latest
- name: vnshop/shipping-service
  newName: ghcr.io/dang232/vnshop-shipping-service
  newTag: latest
- name: vnshop/notification-service
  newName: ghcr.io/dang232/vnshop-notification-service
  newTag: latest
- name: vnshop/coupon-service
  newName: ghcr.io/dang232/vnshop-coupon-service
  newTag: latest
- name: vnshop/review-service
  newName: ghcr.io/dang232/vnshop-review-service
  newTag: latest
- name: vnshop/seller-finance-service
  newName: ghcr.io/dang232/vnshop-seller-finance-service
  newTag: latest
```

- [ ] **B1.6** Update prod kustomization with GHCR image names

Modify `infra/k8s/overlays/prod/kustomization.yaml` images section similarly:

```yaml
# In infra/k8s/overlays/prod/kustomization.yaml - replace the images section:
images:
- name: vnshop/api-gateway
  newName: ghcr.io/dang232/vnshop-api-gateway
  newTag: prod
- name: vnshop/user-service
  newName: ghcr.io/dang232/vnshop-user-service
  newTag: prod
- name: vnshop/product-service
  newName: ghcr.io/dang232/vnshop-product-service
  newTag: prod
- name: vnshop/inventory-service
  newName: ghcr.io/dang232/vnshop-inventory-service
  newTag: prod
- name: vnshop/search-service
  newName: ghcr.io/dang232/vnshop-search-service
  newTag: prod
- name: vnshop/cart-service
  newName: ghcr.io/dang232/vnshop-cart-service
  newTag: prod
- name: vnshop/order-service
  newName: ghcr.io/dang232/vnshop-order-service
  newTag: prod
- name: vnshop/payment-service
  newName: ghcr.io/dang232/vnshop-payment-service
  newTag: prod
- name: vnshop/shipping-service
  newName: ghcr.io/dang232/vnshop-shipping-service
  newTag: prod
- name: vnshop/notification-service
  newName: ghcr.io/dang232/vnshop-notification-service
  newTag: prod
- name: vnshop/coupon-service
  newName: ghcr.io/dang232/vnshop-coupon-service
  newTag: prod
- name: vnshop/review-service
  newName: ghcr.io/dang232/vnshop-review-service
  newTag: prod
- name: vnshop/seller-finance-service
  newName: ghcr.io/dang232/vnshop-seller-finance-service
  newTag: prod
```

- [ ] **B1.7** Validate kustomize build succeeds

```bash
cd infra/k8s/overlays/staging && kustomize build . > /dev/null && echo "Staging kustomize OK"
cd infra/k8s/overlays/prod && kustomize build . > /dev/null && echo "Prod kustomize OK"
```

- [ ] **B1.8** Commit B1

```bash
git add .github/workflows/cd.yml infra/k8s/overlays/staging/kustomization.yaml infra/k8s/overlays/prod/kustomization.yaml
git commit -m "feat(cd): add CD pipeline with GHCR push and K8s manifest update"
```

---

## Task B2: Feature Flags (Unleash)

### Overview
Self-hosted Unleash 6.x with dedicated postgres-unleash in docker-compose. All Java services get `FeatureFlagPort` (domain) + `UnleashFeatureFlagAdapter` (infrastructure). Node services get equivalent TypeScript module. Pre-configured flags seeded via init script.

### Steps

- [ ] **B2.1** Add `postgres-unleash` and `unleash` services to `docker-compose.yml`

Append the following services before the `volumes:` section in `docker-compose.yml`:

```yaml
  postgres-unleash:
    image: postgres:17.9
    container_name: vnshop-postgres-unleash
    environment:
      POSTGRES_DB: unleash
      POSTGRES_USER: unleash
      POSTGRES_PASSWORD: unleash123
    ports:
      - "5441:5432"
    volumes:
      - postgres-unleash-data:/var/lib/postgresql/data
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U unleash -d unleash"]
      interval: 10s
      timeout: 5s
      retries: 5

  unleash:
    image: unleashorg/unleash-server:6
    container_name: vnshop-unleash
    ports:
      - "4242:4242"
    environment:
      DATABASE_URL: postgres://unleash:unleash123@postgres-unleash:5432/unleash
      DATABASE_SSL: "false"
      UNLEASH_DEFAULT_ADMIN_USERNAME: admin
      UNLEASH_DEFAULT_ADMIN_PASSWORD: unleash4Life!
      INIT_CLIENT_API_TOKENS: "default:development.unleash-insecure-api-token"
      INIT_FRONTEND_API_TOKENS: "default:development.unleash-insecure-frontend-token"
    depends_on:
      postgres-unleash:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:4242/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 10
      start_period: 30s

  init-unleash-flags:
    image: curlimages/curl:8.13.0
    container_name: vnshop-init-unleash-flags
    depends_on:
      unleash:
        condition: service_healthy
    volumes:
      - ./infra/unleash/init-flags.sh:/init-flags.sh:ro
    entrypoint: ["sh", "/init-flags.sh"]
    restart: "no"
```

Add volume to the `volumes:` section:

```yaml
  postgres-unleash-data:
```

- [ ] **B2.2** Create `infra/unleash/init-flags.sh`

```bash
#!/bin/sh
# File: infra/unleash/init-flags.sh
# Seeds Unleash with pre-configured feature flags via the Admin API.

UNLEASH_URL="http://unleash:4242"
API_TOKEN="default:development.unleash-insecure-api-token"

echo "Waiting for Unleash API to be ready..."
for i in $(seq 1 30); do
  if wget -qO- "${UNLEASH_URL}/health" 2>/dev/null | grep -q "ok"; then
    break
  fi
  sleep 2
done

echo "Creating feature flags..."

# Create project if not exists (default project already exists in Unleash)
# Create feature flags in the default project

create_flag() {
  FLAG_NAME="$1"
  DESCRIPTION="$2"
  FLAG_TYPE="$3"

  echo "Creating flag: ${FLAG_NAME}"
  wget -qO- --method=POST \
    --header="Authorization: ${API_TOKEN}" \
    --header="Content-Type: application/json" \
    --body-data="{
      \"name\": \"${FLAG_NAME}\",
      \"description\": \"${DESCRIPTION}\",
      \"type\": \"${FLAG_TYPE}\",
      \"impressionData\": false
    }" \
    "${UNLEASH_URL}/api/admin/projects/default/features" 2>/dev/null || true
}

enable_flag_in_dev() {
  FLAG_NAME="$1"

  echo "Enabling flag in development: ${FLAG_NAME}"
  wget -qO- --method=POST \
    --header="Authorization: ${API_TOKEN}" \
    --header="Content-Type: application/json" \
    --body-data="{
      \"name\": \"default\",
      \"parameters\": {}
    }" \
    "${UNLEASH_URL}/api/admin/projects/default/features/${FLAG_NAME}/environments/development/strategies" 2>/dev/null || true
}

# --- Pre-configured flags ---

create_flag "new-checkout-flow" \
  "Enable the redesigned checkout experience with single-page flow" \
  "release"

create_flag "enhanced-search" \
  "Enable Elasticsearch-powered full-text search with typo tolerance" \
  "release"

create_flag "seller-analytics-v2" \
  "Enable v2 seller analytics dashboard with real-time revenue tracking" \
  "release"

create_flag "payment-stripe-enabled" \
  "Enable Stripe payment method for international orders" \
  "operational"

create_flag "payment-paypal-enabled" \
  "Enable PayPal payment method" \
  "operational"

create_flag "recommendations-ml-model" \
  "Use ML-based recommendation model instead of rule-based" \
  "experiment"

create_flag "order-saga-v2" \
  "Enable v2 order saga with compensation rollback" \
  "release"

create_flag "notification-email-channel" \
  "Enable email notifications via SES" \
  "operational"

# Enable some flags in development environment
enable_flag_in_dev "new-checkout-flow"
enable_flag_in_dev "enhanced-search"
enable_flag_in_dev "seller-analytics-v2"
enable_flag_in_dev "notification-email-channel"

echo "Feature flags initialized successfully."
```

- [ ] **B2.3** Add Unleash client dependency to all 11 Java service `pom.xml` files

Add the following dependency block to each Java service's `pom.xml` inside `<dependencies>`:

```xml
		<dependency>
			<groupId>io.getunleash</groupId>
			<artifactId>unleash-client-java</artifactId>
			<version>9.2.4</version>
		</dependency>
```

Services to modify:
- `services/api-gateway/pom.xml`
- `services/user-service/pom.xml`
- `services/product-service/pom.xml`
- `services/inventory-service/pom.xml`
- `services/search-service/pom.xml`
- `services/order-service/pom.xml`
- `services/payment-service/pom.xml`
- `services/shipping-service/pom.xml`
- `services/coupon-service/pom.xml`
- `services/seller-finance-service/pom.xml`
- `services/recommendations-service/pom.xml`

- [ ] **B2.4** Add Unleash configuration to all 11 Java service `application.yml` files

Append to each service's `application.yml`:

```yaml
# --- Feature Flags (Unleash) ---
unleash:
  api-url: ${UNLEASH_API_URL:http://localhost:4242/api}
  api-key: ${UNLEASH_API_KEY:default:development.unleash-insecure-api-token}
  app-name: ${spring.application.name}
  environment: ${UNLEASH_ENVIRONMENT:development}
  fetch-toggles-interval: ${UNLEASH_FETCH_INTERVAL:10}
```

- [ ] **B2.5** Create `FeatureFlagPort.java` for all 11 Java services

Each service gets the same interface in its `domain/port/out/` package. The package name differs per service.

Template (repeat for each service with correct package):

```java
// File: services/<service>/src/main/java/com/vnshop/<pkg>/domain/port/out/FeatureFlagPort.java
package com.vnshop.<pkg>.domain.port.out;

/**
 * Domain port for feature flag evaluation.
 * Infrastructure adapters provide the implementation (e.g., Unleash).
 */
public interface FeatureFlagPort {

    /**
     * Check if a feature flag is enabled globally.
     *
     * @param flagName the flag identifier (e.g., "new-checkout-flow")
     * @return true if the flag is enabled
     */
    boolean isEnabled(String flagName);

    /**
     * Check if a feature flag is enabled for a specific user context.
     *
     * @param flagName the flag identifier
     * @param userId   the user identifier for gradual rollouts
     * @return true if the flag is enabled for this user
     */
    boolean isEnabled(String flagName, String userId);
}
```

Package mappings:
| Service | Package (`<pkg>`) |
|---------|-------------------|
| api-gateway | `apigateway` |
| user-service | `userservice` |
| product-service | `productservice` |
| inventory-service | `inventoryservice` |
| search-service | `searchservice` |
| order-service | `orderservice` |
| payment-service | `paymentservice` |
| shipping-service | `shippingservice` |
| coupon-service | `couponservice` |
| seller-finance-service | `sellerfinanceservice` |
| recommendations-service | `recommendationsservice` |

Full file for order-service as reference:

```java
// File: services/order-service/src/main/java/com/vnshop/orderservice/domain/port/out/FeatureFlagPort.java
package com.vnshop.orderservice.domain.port.out;

/**
 * Domain port for feature flag evaluation.
 * Infrastructure adapters provide the implementation (e.g., Unleash).
 */
public interface FeatureFlagPort {

    /**
     * Check if a feature flag is enabled globally.
     *
     * @param flagName the flag identifier (e.g., "new-checkout-flow")
     * @return true if the flag is enabled
     */
    boolean isEnabled(String flagName);

    /**
     * Check if a feature flag is enabled for a specific user context.
     *
     * @param flagName the flag identifier
     * @param userId   the user identifier for gradual rollouts
     * @return true if the flag is enabled for this user
     */
    boolean isEnabled(String flagName, String userId);
}
```

- [ ] **B2.6** Create `UnleashConfig.java` for all 11 Java services

Template (repeat for each service with correct package):

```java
// File: services/<service>/src/main/java/com/vnshop/<pkg>/infrastructure/featureflag/UnleashConfig.java
package com.vnshop.<pkg>.infrastructure.featureflag;

import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.util.UnleashConfig.Builder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UnleashConfig {

    @Value("${unleash.api-url}")
    private String apiUrl;

    @Value("${unleash.api-key}")
    private String apiKey;

    @Value("${unleash.app-name}")
    private String appName;

    @Value("${unleash.environment}")
    private String environment;

    @Value("${unleash.fetch-toggles-interval:10}")
    private long fetchInterval;

    @Bean(destroyMethod = "shutdown")
    public Unleash unleash() {
        var config = new Builder()
                .appName(appName)
                .instanceId(appName + "-" + System.getenv().getOrDefault("HOSTNAME", "local"))
                .unleashAPI(apiUrl)
                .customHttpHeader("Authorization", apiKey)
                .environment(environment)
                .fetchTogglesInterval(fetchInterval)
                .sendMetricsInterval(15)
                .build();
        return new DefaultUnleash(config);
    }
}
```

Full file for order-service as reference:

```java
// File: services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/featureflag/UnleashConfig.java
package com.vnshop.orderservice.infrastructure.featureflag;

import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.util.UnleashConfig.Builder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UnleashConfig {

    @Value("${unleash.api-url}")
    private String apiUrl;

    @Value("${unleash.api-key}")
    private String apiKey;

    @Value("${unleash.app-name}")
    private String appName;

    @Value("${unleash.environment}")
    private String environment;

    @Value("${unleash.fetch-toggles-interval:10}")
    private long fetchInterval;

    @Bean(destroyMethod = "shutdown")
    public Unleash unleash() {
        var config = new Builder()
                .appName(appName)
                .instanceId(appName + "-" + System.getenv().getOrDefault("HOSTNAME", "local"))
                .unleashAPI(apiUrl)
                .customHttpHeader("Authorization", apiKey)
                .environment(environment)
                .fetchTogglesInterval(fetchInterval)
                .sendMetricsInterval(15)
                .build();
        return new DefaultUnleash(config);
    }
}
```

- [ ] **B2.7** Create `UnleashFeatureFlagAdapter.java` for all 11 Java services

Template (repeat for each service with correct package):

```java
// File: services/<service>/src/main/java/com/vnshop/<pkg>/infrastructure/featureflag/UnleashFeatureFlagAdapter.java
package com.vnshop.<pkg>.infrastructure.featureflag;

import com.vnshop.<pkg>.domain.port.out.FeatureFlagPort;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import org.springframework.stereotype.Component;

@Component
public class UnleashFeatureFlagAdapter implements FeatureFlagPort {

    private final Unleash unleash;

    public UnleashFeatureFlagAdapter(Unleash unleash) {
        this.unleash = unleash;
    }

    @Override
    public boolean isEnabled(String flagName) {
        return unleash.isEnabled(flagName);
    }

    @Override
    public boolean isEnabled(String flagName, String userId) {
        UnleashContext context = UnleashContext.builder()
                .userId(userId)
                .build();
        return unleash.isEnabled(flagName, context);
    }
}
```

Full file for order-service as reference:

```java
// File: services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/featureflag/UnleashFeatureFlagAdapter.java
package com.vnshop.orderservice.infrastructure.featureflag;

import com.vnshop.orderservice.domain.port.out.FeatureFlagPort;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import org.springframework.stereotype.Component;

@Component
public class UnleashFeatureFlagAdapter implements FeatureFlagPort {

    private final Unleash unleash;

    public UnleashFeatureFlagAdapter(Unleash unleash) {
        this.unleash = unleash;
    }

    @Override
    public boolean isEnabled(String flagName) {
        return unleash.isEnabled(flagName);
    }

    @Override
    public boolean isEnabled(String flagName, String userId) {
        UnleashContext context = UnleashContext.builder()
                .userId(userId)
                .build();
        return unleash.isEnabled(flagName, context);
    }
}
```

- [ ] **B2.8** Add `unleash-client` to Node services (cart-service, notification-service)

Add to `services/cart-service/package.json` dependencies:

```json
"unleash-client": "^6.1.2"
```

Add to `services/notification-service/package.json` dependencies:

```json
"unleash-client": "^6.1.2"
```

Then run `npm install` in each service directory.

- [ ] **B2.9** Create feature flag port for Node services

**File: `services/cart-service/src/feature-flag/feature-flag.port.ts`**

```typescript
// File: services/cart-service/src/feature-flag/feature-flag.port.ts

/**
 * Domain port for feature flag evaluation.
 * Infrastructure adapters provide the implementation (e.g., Unleash).
 */
export interface FeatureFlagPort {
  /**
   * Check if a feature flag is enabled globally.
   */
  isEnabled(flagName: string): boolean;

  /**
   * Check if a feature flag is enabled for a specific user context.
   */
  isEnabledForUser(flagName: string, userId: string): boolean;
}

export const FEATURE_FLAG_PORT = Symbol('FEATURE_FLAG_PORT');
```

**File: `services/notification-service/src/feature-flag/feature-flag.port.ts`**

```typescript
// File: services/notification-service/src/feature-flag/feature-flag.port.ts

/**
 * Domain port for feature flag evaluation.
 * Infrastructure adapters provide the implementation (e.g., Unleash).
 */
export interface FeatureFlagPort {
  /**
   * Check if a feature flag is enabled globally.
   */
  isEnabled(flagName: string): boolean;

  /**
   * Check if a feature flag is enabled for a specific user context.
   */
  isEnabledForUser(flagName: string, userId: string): boolean;
}

export const FEATURE_FLAG_PORT = Symbol('FEATURE_FLAG_PORT');
```

- [ ] **B2.10** Create Unleash adapter for Node services

**File: `services/cart-service/src/feature-flag/unleash-feature-flag.adapter.ts`**

```typescript
// File: services/cart-service/src/feature-flag/unleash-feature-flag.adapter.ts
import { Injectable, OnModuleInit, OnModuleDestroy } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { initialize, Unleash, destroy } from 'unleash-client';
import { FeatureFlagPort } from './feature-flag.port';

@Injectable()
export class UnleashFeatureFlagAdapter
  implements FeatureFlagPort, OnModuleInit, OnModuleDestroy
{
  private unleash: Unleash;

  constructor(private readonly configService: ConfigService) {}

  onModuleInit() {
    this.unleash = initialize({
      url: this.configService.get<string>(
        'UNLEASH_API_URL',
        'http://localhost:4242/api',
      ),
      appName: this.configService.get<string>('UNLEASH_APP_NAME', 'cart-service'),
      customHeaders: {
        Authorization: this.configService.get<string>(
          'UNLEASH_API_KEY',
          'default:development.unleash-insecure-api-token',
        ),
      },
      environment: this.configService.get<string>(
        'UNLEASH_ENVIRONMENT',
        'development',
      ),
      refreshInterval:
        this.configService.get<number>('UNLEASH_FETCH_INTERVAL', 10) * 1000,
    });
  }

  onModuleDestroy() {
    destroy();
  }

  isEnabled(flagName: string): boolean {
    return this.unleash?.isEnabled(flagName) ?? false;
  }

  isEnabledForUser(flagName: string, userId: string): boolean {
    return (
      this.unleash?.isEnabled(flagName, { userId }) ?? false
    );
  }
}
```

**File: `services/notification-service/src/feature-flag/unleash-feature-flag.adapter.ts`**

```typescript
// File: services/notification-service/src/feature-flag/unleash-feature-flag.adapter.ts
import { Injectable, OnModuleInit, OnModuleDestroy } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { initialize, Unleash, destroy } from 'unleash-client';
import { FeatureFlagPort } from './feature-flag.port';

@Injectable()
export class UnleashFeatureFlagAdapter
  implements FeatureFlagPort, OnModuleInit, OnModuleDestroy
{
  private unleash: Unleash;

  constructor(private readonly configService: ConfigService) {}

  onModuleInit() {
    this.unleash = initialize({
      url: this.configService.get<string>(
        'UNLEASH_API_URL',
        'http://localhost:4242/api',
      ),
      appName: this.configService.get<string>(
        'UNLEASH_APP_NAME',
        'notification-service',
      ),
      customHeaders: {
        Authorization: this.configService.get<string>(
          'UNLEASH_API_KEY',
          'default:development.unleash-insecure-api-token',
        ),
      },
      environment: this.configService.get<string>(
        'UNLEASH_ENVIRONMENT',
        'development',
      ),
      refreshInterval:
        this.configService.get<number>('UNLEASH_FETCH_INTERVAL', 10) * 1000,
    });
  }

  onModuleDestroy() {
    destroy();
  }

  isEnabled(flagName: string): boolean {
    return this.unleash?.isEnabled(flagName) ?? false;
  }

  isEnabledForUser(flagName: string, userId: string): boolean {
    return (
      this.unleash?.isEnabled(flagName, { userId }) ?? false
    );
  }
}
```

- [ ] **B2.11** Create feature flag NestJS module for Node services

**File: `services/cart-service/src/feature-flag/feature-flag.module.ts`**

```typescript
// File: services/cart-service/src/feature-flag/feature-flag.module.ts
import { Module, Global } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { FEATURE_FLAG_PORT } from './feature-flag.port';
import { UnleashFeatureFlagAdapter } from './unleash-feature-flag.adapter';

@Global()
@Module({
  imports: [ConfigModule],
  providers: [
    {
      provide: FEATURE_FLAG_PORT,
      useClass: UnleashFeatureFlagAdapter,
    },
  ],
  exports: [FEATURE_FLAG_PORT],
})
export class FeatureFlagModule {}
```

**File: `services/notification-service/src/feature-flag/feature-flag.module.ts`**

```typescript
// File: services/notification-service/src/feature-flag/feature-flag.module.ts
import { Module, Global } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { FEATURE_FLAG_PORT } from './feature-flag.port';
import { UnleashFeatureFlagAdapter } from './unleash-feature-flag.adapter';

@Global()
@Module({
  imports: [ConfigModule],
  providers: [
    {
      provide: FEATURE_FLAG_PORT,
      useClass: UnleashFeatureFlagAdapter,
    },
  ],
  exports: [FEATURE_FLAG_PORT],
})
export class FeatureFlagModule {}
```

- [ ] **B2.12** Import `FeatureFlagModule` into each Node service's `AppModule`

For `services/cart-service/src/app.module.ts`, add import:

```typescript
import { FeatureFlagModule } from './feature-flag/feature-flag.module';
```

And add `FeatureFlagModule` to the `imports` array of `@Module`.

For `services/notification-service/src/app.module.ts`, do the same.

- [ ] **B2.13** Add Unleash environment variables to docker-compose for services that use it

Add to the `environment` section of each Java service in `docker-compose.yml`:

```yaml
      UNLEASH_API_URL: http://unleash:4242/api
      UNLEASH_API_KEY: default:development.unleash-insecure-api-token
      UNLEASH_ENVIRONMENT: development
```

Add to `cart-service` and `notification-service` environment sections:

```yaml
      UNLEASH_API_URL: http://unleash:4242/api
      UNLEASH_API_KEY: default:development.unleash-insecure-api-token
      UNLEASH_APP_NAME: cart-service
      UNLEASH_ENVIRONMENT: development
```

(Use `notification-service` for UNLEASH_APP_NAME in notification-service.)

- [ ] **B2.14** Create K8s manifests for Unleash

**File: `infra/k8s/base/services/unleash/deployment.yaml`**

```yaml
# File: infra/k8s/base/services/unleash/deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: vnshop-unleash
  labels:
    app: vnshop-unleash
spec:
  replicas: 1
  selector:
    matchLabels:
      app: vnshop-unleash
  template:
    metadata:
      labels:
        app: vnshop-unleash
    spec:
      containers:
        - name: unleash
          image: unleashorg/unleash-server:6
          ports:
            - containerPort: 4242
              protocol: TCP
          env:
            - name: DATABASE_URL
              valueFrom:
                secretKeyRef:
                  name: vnshop-unleash-secret
                  key: database-url
            - name: DATABASE_SSL
              value: "false"
            - name: UNLEASH_DEFAULT_ADMIN_USERNAME
              value: admin
            - name: UNLEASH_DEFAULT_ADMIN_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: vnshop-unleash-secret
                  key: admin-password
            - name: INIT_CLIENT_API_TOKENS
              valueFrom:
                secretKeyRef:
                  name: vnshop-unleash-secret
                  key: client-api-token
          livenessProbe:
            httpGet:
              path: /health
              port: 4242
            initialDelaySeconds: 30
            periodSeconds: 15
          readinessProbe:
            httpGet:
              path: /health
              port: 4242
            initialDelaySeconds: 20
            periodSeconds: 10
          resources:
            requests:
              cpu: 100m
              memory: 256Mi
            limits:
              cpu: 500m
              memory: 512Mi
```

**File: `infra/k8s/base/services/unleash/service.yaml`**

```yaml
# File: infra/k8s/base/services/unleash/service.yaml
apiVersion: v1
kind: Service
metadata:
  name: vnshop-unleash
  labels:
    app: vnshop-unleash
spec:
  type: ClusterIP
  ports:
    - port: 4242
      targetPort: 4242
      protocol: TCP
      name: http
  selector:
    app: vnshop-unleash
```

**File: `infra/k8s/base/services/unleash/configmap.yaml`**

```yaml
# File: infra/k8s/base/services/unleash/configmap.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: vnshop-unleash-config
  labels:
    app: vnshop-unleash
data:
  UNLEASH_API_URL: "http://vnshop-unleash:4242/api"
  UNLEASH_ENVIRONMENT: "development"
```

**File: `infra/k8s/base/services/unleash/secret.yaml`**

```yaml
# File: infra/k8s/base/services/unleash/secret.yaml
apiVersion: v1
kind: Secret
metadata:
  name: vnshop-unleash-secret
  labels:
    app: vnshop-unleash
type: Opaque
stringData:
  database-url: "postgres://unleash:unleash123@vnshop-postgres-unleash:5432/unleash"
  admin-password: "unleash4Life!"
  client-api-token: "default:development.unleash-insecure-api-token"
```

**File: `infra/k8s/base/services/unleash/kustomization.yaml`**

```yaml
# File: infra/k8s/base/services/unleash/kustomization.yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
  - deployment.yaml
  - service.yaml
  - configmap.yaml
  - secret.yaml
```

- [ ] **B2.15** Update `infra/k8s/base/kustomization.yaml` to include Unleash

Modify `infra/k8s/base/kustomization.yaml` to:

```yaml
apiVersion: kustomize.config.k8s.io/v1beta1
kind: Kustomization
resources:
- serviceaccount.yaml
- configmap.yaml
- secret.yaml
- workloads.yaml
- services/unleash
```

- [ ] **B2.16** Create `docs/feature-flag-guidelines.md`

```markdown
# Feature Flag Guidelines

## Overview

VNShop uses [Unleash](https://getunleash.io/) (self-hosted, v6.x) for feature flag management.
All services integrate via the hexagonal `FeatureFlagPort` interface in the domain layer,
with `UnleashFeatureFlagAdapter` in the infrastructure layer.

## When to Use Feature Flags

### Use a flag when:
- Rolling out a new user-facing feature gradually (percentage rollout)
- Switching between implementations (e.g., old checkout vs new checkout)
- Enabling operational capabilities per environment (e.g., email channel, payment providers)
- Running A/B experiments that need instant kill-switch capability
- Deploying a large feature across multiple services that must activate together

### Use a branch (no flag) when:
- The change is a pure refactor with no behavioral difference
- The change is a bug fix that should apply immediately
- The change is infrastructure-only (CI, Dockerfile, K8s config)
- The feature is small, self-contained, and can be reviewed/merged in one PR

## Flag Lifecycle

```
1. CREATE   — Developer creates flag in Unleash (disabled by default)
2. DEVELOP  — Code checks flag; deploys to staging with flag OFF
3. TEST     — Enable in staging, verify behavior
4. ROLLOUT  — Enable in production (0% → 10% → 50% → 100%)
5. CLEANUP  — Once at 100% and stable for 2 weeks, remove flag checks from code
6. ARCHIVE  — Archive the flag in Unleash
```

**Maximum flag lifetime: 90 days.** Flags older than 90 days trigger a review.

## Naming Convention

```
<scope>-<feature-name>[-<version>]
```

Examples:
- `new-checkout-flow` — release flag for checkout redesign
- `enhanced-search` — release flag for search upgrade
- `seller-analytics-v2` — versioned release flag
- `payment-stripe-enabled` — operational toggle
- `recommendations-ml-model` — experiment flag

### Flag Types (Unleash built-in)
- **release** — gradual rollout of a feature
- **experiment** — A/B test (has defined end date)
- **operational** — runtime ops toggle (e.g., enable/disable a provider)
- **kill-switch** — emergency disable (inverted logic: flag ON = feature OFF)

## Code Integration

### Java (Spring Boot)

Inject the domain port, never the Unleash SDK directly:

```java
@Component
public class CheckoutService {
    private final FeatureFlagPort featureFlags;

    public CheckoutService(FeatureFlagPort featureFlags) {
        this.featureFlags = featureFlags;
    }

    public CheckoutResult checkout(Order order, String userId) {
        if (featureFlags.isEnabled("new-checkout-flow", userId)) {
            return newCheckoutFlow(order);
        }
        return legacyCheckoutFlow(order);
    }
}
```

### Node (NestJS)

Inject via the `FEATURE_FLAG_PORT` token:

```typescript
@Injectable()
export class CartService {
  constructor(
    @Inject(FEATURE_FLAG_PORT)
    private readonly featureFlags: FeatureFlagPort,
  ) {}

  async getCart(userId: string): Promise<Cart> {
    if (this.featureFlags.isEnabledForUser('new-checkout-flow', userId)) {
      return this.getEnhancedCart(userId);
    }
    return this.getBasicCart(userId);
  }
}
```

## Access

| Environment | Unleash UI | API URL |
|-------------|-----------|---------|
| Local dev | http://localhost:4242 | http://localhost:4242/api |
| Staging | http://unleash.staging.vnshop.internal | http://vnshop-unleash:4242/api |
| Production | http://unleash.vnshop.internal | http://vnshop-unleash:4242/api |

**Default credentials (local dev only):**
- Username: `admin`
- Password: `unleash4Life!`
- Client API token: `default:development.unleash-insecure-api-token`

## Decision Criteria

| Criteria | Flag | Branch |
|----------|------|--------|
| Needs instant rollback | Yes | No |
| Gradual % rollout | Yes | No |
| Multi-service coordination | Yes | No |
| Pure refactor | No | Yes |
| Bug fix | No | Yes |
| Infrastructure change | No | Yes |
| Time-boxed experiment | Yes | No |
| Per-user targeting | Yes | No |

## Pre-configured Flags

| Flag Name | Type | Purpose | Default (dev) |
|-----------|------|---------|---------------|
| `new-checkout-flow` | release | Redesigned single-page checkout | ON |
| `enhanced-search` | release | Elasticsearch full-text search | ON |
| `seller-analytics-v2` | release | Real-time seller revenue dashboard | ON |
| `payment-stripe-enabled` | operational | Stripe payment method | OFF |
| `payment-paypal-enabled` | operational | PayPal payment method | OFF |
| `recommendations-ml-model` | experiment | ML-based recommendations | OFF |
| `order-saga-v2` | release | V2 order saga with compensation | OFF |
| `notification-email-channel` | operational | Email via SES | ON |
```

- [ ] **B2.17** Verify docker-compose config is valid

```bash
docker compose config --quiet && echo "docker-compose.yml valid"
```

- [ ] **B2.18** Verify Java compilation with Unleash dependency

```bash
cd services/order-service && mvn compile -q -DskipTests && echo "order-service compiles"
cd services/payment-service && mvn compile -q -DskipTests && echo "payment-service compiles"
```

- [ ] **B2.19** Verify Node service installs unleash-client

```bash
cd services/cart-service && npm install && npm run build && echo "cart-service builds"
cd services/notification-service && npm install && npm run build && echo "notification-service builds"
```

- [ ] **B2.20** Verify kustomize build with Unleash resources

```bash
cd infra/k8s/overlays/staging && kustomize build . > /dev/null && echo "Staging kustomize with unleash OK"
```

- [ ] **B2.21** Smoke test: bring up Unleash locally

```bash
docker compose up -d postgres-unleash unleash
# Wait for health
docker compose exec unleash wget -qO- http://localhost:4242/health
# Run flag seeding
docker compose up init-unleash-flags
# Verify flags exist
docker compose exec unleash wget -qO- --header="Authorization: default:development.unleash-insecure-api-token" http://localhost:4242/api/client/features | grep -o '"name":"[^"]*"' | head -8
docker compose down
```

- [ ] **B2.22** Commit B2

```bash
git add docker-compose.yml infra/unleash/ infra/k8s/base/ docs/feature-flag-guidelines.md
git add services/*/pom.xml services/*/src/main/resources/application.yml
git add services/*/src/main/java/**/domain/port/out/FeatureFlagPort.java
git add services/*/src/main/java/**/infrastructure/featureflag/
git add services/cart-service/package.json services/cart-service/src/feature-flag/
git add services/notification-service/package.json services/notification-service/src/feature-flag/
git add services/cart-service/src/app.module.ts services/notification-service/src/app.module.ts
git commit -m "feat(feature-flags): add Unleash integration with hexagonal FeatureFlagPort across all services"
```

---

## Summary of All Complete File Contents

### Complete Java file for each service (example: payment-service)

**`services/payment-service/src/main/java/com/vnshop/paymentservice/domain/port/out/FeatureFlagPort.java`**

```java
package com.vnshop.paymentservice.domain.port.out;

/**
 * Domain port for feature flag evaluation.
 * Infrastructure adapters provide the implementation (e.g., Unleash).
 */
public interface FeatureFlagPort {

    /**
     * Check if a feature flag is enabled globally.
     *
     * @param flagName the flag identifier (e.g., "new-checkout-flow")
     * @return true if the flag is enabled
     */
    boolean isEnabled(String flagName);

    /**
     * Check if a feature flag is enabled for a specific user context.
     *
     * @param flagName the flag identifier
     * @param userId   the user identifier for gradual rollouts
     * @return true if the flag is enabled for this user
     */
    boolean isEnabled(String flagName, String userId);
}
```

**`services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/featureflag/UnleashConfig.java`**

```java
package com.vnshop.paymentservice.infrastructure.featureflag;

import io.getunleash.DefaultUnleash;
import io.getunleash.Unleash;
import io.getunleash.util.UnleashConfig.Builder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class UnleashConfig {

    @Value("${unleash.api-url}")
    private String apiUrl;

    @Value("${unleash.api-key}")
    private String apiKey;

    @Value("${unleash.app-name}")
    private String appName;

    @Value("${unleash.environment}")
    private String environment;

    @Value("${unleash.fetch-toggles-interval:10}")
    private long fetchInterval;

    @Bean(destroyMethod = "shutdown")
    public Unleash unleash() {
        var config = new Builder()
                .appName(appName)
                .instanceId(appName + "-" + System.getenv().getOrDefault("HOSTNAME", "local"))
                .unleashAPI(apiUrl)
                .customHttpHeader("Authorization", apiKey)
                .environment(environment)
                .fetchTogglesInterval(fetchInterval)
                .sendMetricsInterval(15)
                .build();
        return new DefaultUnleash(config);
    }
}
```

**`services/payment-service/src/main/java/com/vnshop/paymentservice/infrastructure/featureflag/UnleashFeatureFlagAdapter.java`**

```java
package com.vnshop.paymentservice.infrastructure.featureflag;

import com.vnshop.paymentservice.domain.port.out.FeatureFlagPort;
import io.getunleash.Unleash;
import io.getunleash.UnleashContext;
import org.springframework.stereotype.Component;

@Component
public class UnleashFeatureFlagAdapter implements FeatureFlagPort {

    private final Unleash unleash;

    public UnleashFeatureFlagAdapter(Unleash unleash) {
        this.unleash = unleash;
    }

    @Override
    public boolean isEnabled(String flagName) {
        return unleash.isEnabled(flagName);
    }

    @Override
    public boolean isEnabled(String flagName, String userId) {
        UnleashContext context = UnleashContext.builder()
                .userId(userId)
                .build();
        return unleash.isEnabled(flagName, context);
    }
}
```

### Complete Java files for remaining services

The pattern is identical for all 11 services. Each gets exactly 3 files with only the package name changed:

| Service | Base Package |
|---------|-------------|
| api-gateway | `com.vnshop.apigateway` |
| user-service | `com.vnshop.userservice` |
| product-service | `com.vnshop.productservice` |
| inventory-service | `com.vnshop.inventoryservice` |
| search-service | `com.vnshop.searchservice` |
| order-service | `com.vnshop.orderservice` |
| payment-service | `com.vnshop.paymentservice` |
| shipping-service | `com.vnshop.shippingservice` |
| coupon-service | `com.vnshop.couponservice` |
| seller-finance-service | `com.vnshop.sellerfinanceservice` |
| recommendations-service | `com.vnshop.recommendationsservice` |

### Maven dependency to add to each pom.xml

Insert inside `<dependencies>` (after the last existing `<dependency>` block):

```xml
		<dependency>
			<groupId>io.getunleash</groupId>
			<artifactId>unleash-client-java</artifactId>
			<version>9.2.4</version>
		</dependency>
```

### application.yml block to append to each Java service

```yaml

# --- Feature Flags (Unleash) ---
unleash:
  api-url: ${UNLEASH_API_URL:http://localhost:4242/api}
  api-key: ${UNLEASH_API_KEY:default:development.unleash-insecure-api-token}
  app-name: ${spring.application.name}
  environment: ${UNLEASH_ENVIRONMENT:development}
  fetch-toggles-interval: ${UNLEASH_FETCH_INTERVAL:10}
```

### docker-compose.yml environment additions for Java services

For each Java service already in docker-compose (under `profiles: ["apps"]`), add these 3 env vars:

```yaml
      UNLEASH_API_URL: http://unleash:4242/api
      UNLEASH_API_KEY: default:development.unleash-insecure-api-token
      UNLEASH_ENVIRONMENT: development
```

Also add `unleash` to their `depends_on` with `condition: service_healthy`.

### docker-compose.yml environment additions for Node services

For `cart-service`:
```yaml
      UNLEASH_API_URL: http://unleash:4242/api
      UNLEASH_API_KEY: default:development.unleash-insecure-api-token
      UNLEASH_APP_NAME: cart-service
      UNLEASH_ENVIRONMENT: development
```

For `notification-service`:
```yaml
      UNLEASH_API_URL: http://unleash:4242/api
      UNLEASH_API_KEY: default:development.unleash-insecure-api-token
      UNLEASH_APP_NAME: notification-service
      UNLEASH_ENVIRONMENT: development
```

---

## Verification Checklist

After both tasks are complete:

1. `cd .github/workflows && python3 -c "import yaml; yaml.safe_load(open('cd.yml'))"` — validates YAML
2. `docker compose config --quiet` — validates compose file
3. `kustomize build infra/k8s/overlays/staging/ > /dev/null` — validates kustomize
4. `cd services/order-service && mvn compile -q` — validates Java + Unleash dependency
5. `cd services/cart-service && npm run build` — validates Node + Unleash
6. `docker compose up -d postgres-unleash unleash && sleep 15 && docker compose exec unleash wget -qO- http://localhost:4242/health` — validates Unleash boots