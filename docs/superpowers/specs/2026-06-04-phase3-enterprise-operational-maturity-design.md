# Phase 3: Enterprise Operational Maturity — Design Spec

**Date:** 2026-06-04  
**Status:** Approved  
**Execution:** Full parallel (all 15 tasks via subagents)  
**Target:** Hybrid (Docker Compose dev + production-ready K8s/Terraform templates)

---

## Context

Phases 1 & 2 delivered security hardening (secrets externalization, SASL/SSL, audit logging) and production resilience (circuit breakers, retry/DLQ, connection pools, health probes, structured logging, tracing, metrics). The platform now emits telemetry but lacks visualization, log aggregation, deployment automation, and governance tooling.

## Goals

- Complete the observability stack (visualize metrics, aggregate logs, define SLOs)
- Automate the build→publish pipeline (CD to GHCR)
- Harden infrastructure for HA and scale (Redis Sentinel, Kafka partitions, mTLS, Ingress+TLS)
- Establish governance (GDPR, MFA, PCI-DSS, architectural tests, contract tests, feature flags)
- Template cloud provisioning (Terraform modules)

---

## Sub-phase 3A: Observability Completion

### A1. Grafana Dashboards

**Scope:** Add Grafana to docker-compose with auto-provisioned datasources and 4 pre-built dashboards.

**Implementation:**
- Container: `grafana/grafana:11.x` on port 3000
- Provisioning directory: `infra/grafana/provisioning/`
  - `datasources/datasources.yml` — Prometheus (default) + Loki
  - `dashboards/dashboards.yml` — dashboard provider pointing to JSON files
- Dashboard JSON files at `infra/grafana/dashboards/`:
  - `red-metrics.json` — Request rate, Error rate, Duration (p50/p95/p99) per service
  - `jvm-overview.json` — Heap, GC, threads, HikariCP pools across all Java services
  - `kafka-consumer-lag.json` — Consumer group lag, partition offsets, DLQ rates
  - `business-kpis.json` — Orders/min, payment success rate, revenue (from custom metrics added in Phase 2)
- K8s: Deployment + ConfigMap + Service manifests in `infra/k8s/base/services/grafana/`
- Anonymous access enabled for dev; production overlay sets admin password via secret

### A2. Loki + Promtail Log Aggregation

**Scope:** Centralize structured JSON logs from all services into Loki, queryable via Grafana.

**Implementation:**
- Loki container: `grafana/loki:3.x` on port 3100, single-tenant mode
  - Config: `infra/loki/loki-config.yml` (local filesystem storage, 7-day retention)
- Promtail container: `grafana/promtail:3.x`
  - Config: `infra/loki/promtail-config.yml`
  - Scrapes Docker container logs via volume mount `/var/lib/docker/containers`
  - Pipeline: extract `service_name` label from container name, JSON parsing for level/traceId
- Grafana datasource auto-wired (covered in A1)
- K8s: Loki Deployment + Promtail DaemonSet in `infra/k8s/base/services/loki/`
- Docker Compose: both containers added to monitoring profile

### A3. SLI/SLO Definitions

**Scope:** Define measurable SLIs and targets per service, backed by Prometheus recording rules.

**Implementation:**
- Document: `docs/sli-slo.md` defining per-service:
  - Availability: % of non-5xx responses (initial target: 99.5% — adjust after 2-week baseline measurement)
  - Latency: p99 < 500ms for reads, < 2s for writes (initial targets — calibrate against observed p99 after baseline window)
  - Throughput: minimum requests/sec baseline per service (measured, not guessed)
  - Error budget: 0.5% over 30-day rolling window
  - **Baseline phase:** first 2 weeks after deployment, SLO alerts in warning-only mode to establish real baselines before enforcing
- Prometheus recording rules: `infra/prometheus/slo-rules.yml`
  - `slo:http_requests:error_ratio_30d` per service
  - `slo:http_requests:latency_p99` per service
  - Error budget burn-rate alerts (fast-burn 14.4x/1h, slow-burn 3x/3d)
- Grafana SLO dashboard added to `infra/grafana/dashboards/slo-overview.json`

---

## Sub-phase 3B: Deployment & Release

### B1. CD Pipeline (GitHub Actions → GHCR)

**Scope:** Extend CI to build Docker images and push to GitHub Container Registry on main branch merges.

**Implementation:**
- New workflow file: `.github/workflows/cd.yml` (separate from ci.yml)
- Trigger: push to `main` after CI passes (workflow_run trigger on ci.yml success)
- Jobs:
  1. `detect-changes` — reuse path filter logic from ci.yml
  2. `build-push` — matrix over changed services:
     - Java: `docker/build-push-action` with Dockerfile from each service
     - Node: same action, Node Dockerfiles
     - Architecture: linux/amd64 only (arm64 not needed for current deployment targets)
     - Tags: `ghcr.io/<owner>/vnshop-<service>:sha-<short>`, `:latest`, `:v<date>`
  3. `update-manifests` — update image tags in `infra/k8s/overlays/staging/kustomization.yaml`
- Secrets required: `GITHUB_TOKEN` (automatic for GHCR)
- Each service needs a `Dockerfile` (verify they exist, create if missing)

### B2. Feature Flags (Unleash)

**Scope:** Self-hosted Unleash instance with Spring Boot SDK integration.

**Implementation:**
- Unleash container: `unleashorg/unleash-server:6.x` on port 4242
  - Depends on: dedicated `postgres-unleash` instance
  - Environment: `DATABASE_URL`, `INIT_ADMIN_API_TOKENS`
- Docker Compose: add `unleash` + `postgres-unleash` services
- Java SDK integration:
  - Shared dependency: `io.getunleash:unleash-client-java` in parent POM
  - `FeatureFlagPort` interface in each service's domain layer
  - `UnleashFeatureFlagAdapter` in infrastructure layer
  - Configuration: `unleash.api-url`, `unleash.api-key`, `unleash.app-name` in application.yml
- Pre-configured flags (via Unleash API init script):
  - `new-checkout-flow` — gradual rollout toggle
  - `enhanced-search` — A/B test for search algorithm
  - `seller-analytics-v2` — seller dashboard feature gate
- K8s: Deployment + Service + ConfigMap in `infra/k8s/base/services/unleash/`
- Decision criteria doc: `docs/feature-flag-guidelines.md`
  - When to flag vs. when to branch (flags for runtime toggles, branches for incomplete features)
  - Flag lifecycle: create → enable → remove (max 30-day active lifespan)
  - Flag naming conventions and ownership

---

## Sub-phase 3C: Infrastructure Hardening

### C1. K8s Ingress + cert-manager TLS

**Scope:** Nginx Ingress Controller + automatic Let's Encrypt certificates.

**Implementation:**
- Manifests at `infra/k8s/base/ingress/`:
  - `ingress-nginx-controller.yaml` — Deployment, Service (LoadBalancer), RBAC
  - `cert-manager.yaml` — cert-manager CRDs + Deployment (reference install)
  - `cluster-issuer.yaml` — Let's Encrypt ClusterIssuer (staging + prod)
  - `ingress.yaml` — Ingress resource routing:
    - `/api/*` → api-gateway:8080
    - `/auth/*` → keycloak:8085
    - `/grafana/*` → grafana:3000
    - TLS: `tls.secretName: vnshop-tls`
- Kustomize prod overlay: production Let's Encrypt issuer (vs staging)
- Annotations: rate-limiting, CORS, websocket support for notifications

### C2. Redis Sentinel HA

**Scope:** Replace single Redis instance with Sentinel-managed cluster (behind Docker Compose profile).

**Implementation:**
- Docker Compose services (under `--profile ha`; default dev stays single Redis):
  - `redis-master` — primary node (port 6379)
  - `redis-replica-1`, `redis-replica-2` — read replicas
  - `redis-sentinel-1`, `redis-sentinel-2`, `redis-sentinel-3` — sentinels (port 26379)
- Default dev profile: single `redis` container (existing behavior, no change)
- Sentinel config: `infra/redis/sentinel.conf` (quorum=2, down-after=5000ms, failover-timeout=10000ms)
- Spring Boot config change for cart-service (primary Redis consumer):
  - `spring.data.redis.sentinel.master=mymaster` (active under `prod`/`ha` profile)
  - `spring.data.redis.sentinel.nodes=redis-sentinel-1:26379,...`
  - Default profile keeps `spring.data.redis.host=redis` for single-node dev
- K8s: StatefulSet for Redis + Sentinel, headless Service for discovery
- Health check: sentinel `PING` + master reachability

### C3. Kafka Partition Scaling

**Scope:** Increase partition counts on high-throughput topics for parallelism.

**Implementation:**
- Script: `infra/scripts/kafka-partition-scale.sh`
  - `product-events`: 3 → 12 partitions
  - `payment.completed`, `payment.failed`: 3 → 6 partitions
  - `order.created`, `order.cancelled`: 3 → 6 partitions
  - `inventory.reserved`, `inventory.released`: 3 → 6 partitions
  - `shipping.events`: 3 → 6 partitions
- Update `infra/scripts/init-kafka-topics.sh` with new partition counts for fresh environments
- Consumer group rebalance considerations documented in script comments
- **Consumer concurrency updates** (required to utilize new partitions):
  - product-service consumers: `spring.kafka.listener.concurrency=12`
  - order-service consumers: `spring.kafka.listener.concurrency=6`
  - payment-service consumers: `spring.kafka.listener.concurrency=6`
  - inventory-service consumers: `spring.kafka.listener.concurrency=6`
  - shipping-service consumers: `spring.kafka.listener.concurrency=6`
  - K8s: document HPA targets for consumer pods (scale based on consumer lag metric)
- Note: key-based partitioning maintained (order-id, payment-id) — no message reordering issues within a partition

### C4. Inter-service mTLS (Istio)

**Scope:** Istio service mesh configuration for mutual TLS between all services.

**Implementation:**
- Manifests at `infra/k8s/base/istio/`:
  - `istio-base.yaml` — IstioOperator CRD (minimal profile, sidecar injection)
  - `peer-authentication.yaml` — PeerAuthentication STRICT mode (mesh-wide)
  - `destination-rules.yaml` — DestinationRule per service with `ISTIO_MUTUAL` TLS mode
  - `authorization-policies.yaml` — Allow only expected service-to-service calls:
    - api-gateway → all services
    - order-service → inventory, payment, shipping (gRPC)
    - payment-service → order-service (callback)
  - `virtual-services.yaml` — retry + timeout policies matching existing Resilience4j config
- Namespace label: `istio-injection=enabled`
- Note: Template only — requires Istio control plane installed on target cluster
- **Staleness risk mitigation:**
  - `authorization-policies.yaml` encodes the service call graph; it WILL drift as services evolve
  - CI validation: add a `buf`-style call-graph linter that parses proto imports + Spring `@FeignClient`/gRPC stubs and validates against the authorization policy YAML
  - Script: `infra/scripts/validate-istio-call-graph.sh` — fails CI if a new service-to-service call exists without a matching policy entry
  - Alternative fallback: if Istio is never adopted, these manifests should be archived or deleted after 6 months of inactivity
- Documentation: `infra/k8s/base/istio/README.md` with setup instructions + maintenance cadence

---

## Sub-phase 3D: Governance & Quality

### D1. GDPR Right-to-Deletion + Data Export

**Scope:** API endpoints for PII export and deletion across 5 services, using async saga pattern.

**PII-holding services:** user-service, order-service, payment-service, notification-service, shipping-service

**Implementation:**
- New endpoints on `api-gateway` (routes to user-service):
  - `POST /api/v1/gdpr/export/{userId}` — initiates async export, returns request ID
  - `GET /api/v1/gdpr/export/{userId}/status/{requestId}` — poll for completion, download when ready
  - `DELETE /api/v1/gdpr/delete/{userId}` — initiates cascading deletion
- **Export (async saga — no synchronous fan-out):**
  - user-service publishes `gdpr.export-requested` Kafka event with `{userId, requestId}`
  - Each PII-holding service consumes event, produces its data fragment, publishes `gdpr.export-fragment` event with `{requestId, serviceName, payload}`
  - user-service aggregates fragments; marks export complete when all 5 fragments arrive (or timeout after 60s with partial result + warning)
  - Final export: JSON ZIP stored temporarily (TTL 24h), download URL returned via status endpoint
  - If a service is down, export completes with available fragments + explicit "missing: shipping-service" note — no full failure on partial unavailability
- **Delete (async event-driven — already async):**
  - Publishes `gdpr.deletion-requested` Kafka event with userId
  - Each service consumes event and anonymizes/deletes user data:
    - user-service: delete profile, anonymize username to `deleted-<hash>`
    - order-service: anonymize addresses, keep order records for accounting (7yr retention)
    - payment-service: delete payment method references
    - notification-service: delete notification history
    - shipping-service: anonymize delivery addresses on completed shipments
  - Each service publishes `gdpr.deletion-completed` confirmation event
  - user-service tracks completion; alerts on timeout (service didn't confirm within 5 min)
- Audit trail: every export/deletion logged with timestamp + operator
- Rate limiting: 1 export request per user per hour
- Requires admin or self-service (user can only export/delete own data)

### D2. MFA for Admin/Seller Roles

**Scope:** Enable TOTP-based MFA on Keycloak for privileged roles.

**Implementation:**
- Update `infra/keycloak/vnshop-realm.json`:
  - Required action `CONFIGURE_TOTP` for roles: `admin`, `seller`
  - OTP policy: TOTP, SHA1, 6 digits, 30s period (SHA1 is acceptable per RFC 6238 — TOTP standard; SHA256/512 have no security benefit for 6-digit codes and break some authenticator apps)
  - Conditional OTP: required for admin/seller, optional for buyers
- Update `infra/keycloak/vnshop-realm-prod.json` with same config
- No service code changes — Keycloak handles MFA challenge in login flow
- Documentation note in `docs/` for admin onboarding

### D3. PCI-DSS SAQ-A Documentation

**Scope:** Self-assessment questionnaire documenting compliance posture.

**Implementation:**
- Document: `docs/pci-dss-saq-a.md`
- Content:
  - Cardholder data flow diagram (browser → payment gateway, never touches our servers)
  - Scope: SAQ-A (all payment processing outsourced to gateway)
  - Controls mapping: network segmentation, access control, logging, encryption
  - Evidence references: SASL/SSL on Kafka, TLS on Ingress, audit logging, secrets management
  - Annual review cadence
  - Responsibility matrix (us vs gateway provider)

### D4. ArchUnit Tests

**Scope:** Automated architecture rule enforcement across all Java services.

**Implementation:**
- Shared test dependency module or rules in each service's test suite
- Dependency: `com.tngtech.archunit:archunit-junit5` in parent POM
- Rules enforced:
  - Hexagonal boundary: `domain` package has no imports from `infrastructure` or `application`
  - No `@Service`/`@Repository`/`@Component` in domain layer
  - Application layer depends only on domain ports, not infra adapters
  - No circular package dependencies
  - Controllers only in `infrastructure.web` package
  - gRPC adapters only in `infrastructure.grpc` package
- Test class: `ArchitectureRulesTest.java` in each service's test directory
- CI integration: runs with existing `mvn test` — fails the build on violation

### D5. Contract Tests (buf + Pact)

**Scope:** Consumer-driven contracts on 3 critical gRPC interfaces using tools designed for proto-based services.

**Tooling rationale:** Spring Cloud Contract's gRPC support is immature and requires workarounds. Instead:
- `buf breaking` — proto schema compatibility (detects breaking changes in .proto files)
- Pact (pact-jvm) — behavioral contract testing for gRPC interactions

**Implementation:**
- **Proto compatibility (buf):**
  - `buf.yaml` + `buf.gen.yaml` in `proto/` directory
  - CI step: `buf breaking --against .git#branch=main` on every PR
  - Catches: removed fields, changed field types, renamed services/methods
- **Behavioral contracts (Pact):**
  - Dependency: `au.com.dius.pact:provider` + `au.com.dius.pact:consumer` in relevant POMs
  - Contracts defined for:
    1. **order-service → inventory-service** (gRPC: ReserveStock, ReleaseStock)
    2. **order-service → payment-service** (gRPC: CreatePayment, CapturePayment)
    3. **order-service → shipping-service** (gRPC: CreateShipment, GetShipmentStatus)
  - Consumer side (order-service): Pact mock gRPC server, generates pact files
  - Provider side (inventory/payment/shipping): Pact provider verification tests replay consumer expectations against real implementation
  - Pact files stored in `pacts/` directory (committed to repo — no Pact Broker needed initially)
- CI: `buf breaking` in lint step, Pact verification in `mvn verify`

### D6. Terraform Infrastructure as Code

**Scope:** Terraform modules for cloud provisioning (AWS-targeted templates).

**Implementation:**
- Directory: `infra/terraform/`
- Module structure:
  - `modules/vpc/` — VPC, subnets (public/private), NAT gateway, route tables
  - `modules/eks/` — EKS cluster, node groups (spot + on-demand), IRSA roles
  - `modules/rds/` — RDS PostgreSQL (multi-AZ), parameter groups, subnet groups
  - `modules/elasticache/` — ElastiCache Redis cluster (Sentinel-equivalent replication)
  - `modules/msk/` — MSK Kafka cluster, security groups, broker config
  - `modules/ecr/` — ECR repositories for each service (alternative to GHCR)
- Root configs:
  - `environments/dev/main.tf` — dev environment (smaller instances, single-AZ)
  - `environments/prod/main.tf` — prod environment (multi-AZ, larger instances)
  - `variables.tf`, `outputs.tf`, `providers.tf`, `backend.tf` (S3 state backend template)
- State: S3 backend config templated (bucket name as variable)
- **State locking:** DynamoDB table (`vnshop-terraform-locks`) for state locking — prevents concurrent `terraform apply` from corrupting state. Table defined in `modules/state-backend/` (bootstrap module, applied manually once)
- No credentials committed — uses `AWS_PROFILE` or IAM roles
- `.terraform.lock.hcl` gitignored, provider versions pinned in `versions.tf`

---

## Cross-cutting Concerns

- **Docker Compose updates:** All new containers (Grafana, Loki, Promtail, Unleash, Redis Sentinel) added to root `docker-compose.yml`
- **K8s manifests:** All new services get base + overlay manifests following existing Kustomize structure
- **Documentation:** Each sub-phase updates relevant docs (README, architecture diagrams)
- **No breaking changes:** All additions are additive; existing services continue to function without the new components

## Dependencies Between Tasks

Most tasks are independent. Notable exceptions:
- A1 (Grafana) provides visualization for A2 (Loki datasource) and A3 (SLO dashboard)
- B1 (CD pipeline) needs Dockerfiles in each service (should already exist)
- D1 (GDPR) depends on Kafka topics existing (they do)

These dependencies are minor — subagents can handle them by creating placeholder configs that the dependent task fills in.

---

## Success Criteria

- `docker-compose up` starts the full stack including Grafana, Loki, Unleash, Redis Sentinel
- GitHub Actions CD workflow builds and pushes images to GHCR on merge to main
- Grafana loads with 4+ dashboards showing live data
- SLI/SLO burn-rate alerts fire correctly in test scenarios
- `mvn test` passes with ArchUnit + contract tests
- GDPR export/delete endpoints respond correctly
- All K8s manifests validate with `kubectl apply --dry-run=client`
- Terraform `plan` runs without errors (no cloud credentials needed)
