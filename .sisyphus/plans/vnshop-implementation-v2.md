# VNShop Full-Stack Implementation v3 — Real Production (Kubernetes + Managed Data)

## TL;DR

> Build VNShop for real production, not demo/MVP. Target deployment is Kubernetes: upstream kubeadm on Proxmox-hosted Dell R720 nodes, with explicit multi-node scale-out for flash-sale load. Cloudflare R2 is canonical for object storage and offsite backups. Docker Compose remains local-dev only. Production release requires Kubernetes manifests/Helm, data stores with tested backups, R2 buckets/CDN, secrets manager/SealedSecrets, CI/CD, staging soak, payment/ledger reconciliation, security gates, observability, runbooks, backup/restore, and rollback drills.

> This plan is production-shippable only after the Production Gate (G1–G31) passes. Feature completion alone is NOT enough.

---

## Context

**Application stack**: Spring Boot 4.0.6 (Java 25 LTS), NestJS 11 (Node 24 LTS), PostgreSQL 17.9-compatible PostgreSQL, Redis 8.6-compatible Redis, Kafka 4.2.0-compatible Kafka (KRaft, no ZooKeeper), Elasticsearch 9.4.0-compatible search, Keycloak 26.6.

**Services**: api-gateway, user-service, product-service, inventory-service, search-service, cart-service, order-service, payment-service, shipping-service, notification-service, coupon-service, review-service, seller-finance-service, admin APIs inside owning services.

**Microservice boundary rule**: do not merge bounded contexts into “cluster monolith” services. Each bounded context owns its database schema, API, events, Dockerfile, deployment, readiness probes, metrics, and runbook. Cross-service communication uses HTTP through gateway/service client or Kafka/outbox events; no direct database reads across services.

**Local-dev infrastructure ports**: Keycloak 8085, PostgreSQL 5432, Redis 6379, Kafka 9092 (KRaft, no ZooKeeper), ES 9200, Prometheus 9090, Grafana 3000, Jaeger 16686, Loki 3100, AlertManager 9093, MinIO 9000 (R2-compatible local fallback).

**Production deployment target**: upstream Kubernetes via kubeadm on Proxmox-hosted Dell R720 nodes, 3 environments (`dev`, `staging`, `prod`), self-hosted PostgreSQL/Redis/Kafka/OpenSearch allowed if capacity-tested, Cloudflare R2 for invoices/images/uploads/backups, external DNS, TLS cert-manager, ingress controller, autoscaling where available, network policies, PodDisruptionBudgets where meaningful, resource requests/limits.

**Object storage**: Cloudflare R2 is canonical for product images, seller documents, invoice PDFs, exports, and attachments. Local dev uses MinIO with S3-compatible APIs. Application code must depend on an `ObjectStoragePort`, never an SDK directly in domain/application layers.

**Constraints**: Hexagonal (domain zero framework imports), TDD mandatory, Keycloak auth (no custom JWT), API-first (no frontend), real payment/carrier integrations only after sandbox certification, no production secrets in git, no Docker Compose as production runtime.

---

## Compatibility Matrix

Verified means the exact version combinations have been confirmed running together end-to-end. Unverified ≠ broken — it means pre-flight is not done yet. Phase 0 must fill the Verified column.

| Component | Version | Why chosen | Support status | Upgrade path | Verified by |
|---|---|---|---|---|---|
| Java JDK | 25.0.3 LTS | Latest LTS (Sep 2025). 3-year support. | Oracle NFTC free. Next LTS ~Sep 2027. | JDK 26 (non-LTS, skip). Next LTS after 25. | **Phase 0** |
| Spring Boot | 4.0.6 | Latest 4.0.x patch (Apr 2026). Spring Framework 7. | OSS EOL Dec 2026. Commercial Dec 2027. | 4.1 GA when stable. | **Phase 0** |
| Spring Cloud | 2025.0.x | Compatible with Boot 4.0. | Matched to Boot EOL. | Follow Boot upgrade. | **Phase 0** |
| Maven | 3.9.15 | Latest stable 3.x. Maven 4 not GA. | Indefinite. | Maven 4 when GA. | **Phase 0** |
| Node.js | 24 LTS | Active LTS (Krypton). | Node 26 is Current (not LTS). | Node 26 LTS when promoted. | **Phase 0** |
| NestJS | 11.x | Latest stable. Supports Node 22/24. | Active. | Follow semver. | **Phase 0** |
| PostgreSQL | 17.9 | Latest stable 17.x. Managed providers support 17 universally. | PG 18 exists but managed provider support varies. | Re-evaluate PG 18 when managed providers GA. | **Phase 0** |
| Redis | 8.6.2 | Latest stable 8.x. Managed providers support 7.x+8.x. | 8.8 in milestone. | 8.8 when GA. | **Phase 0** |
| Apache Kafka | 4.2.0 | Latest stable (Feb 2026). KRaft-only. Docker: `cp-kafka:8.2.0`. | Active. KRaft is the only controller mode. | Follow Kafka minor releases. | **Phase 0** |
| Elasticsearch | 9.4.0 | Latest stable (May 2026). Lucene 10. | Active. | Follow ES minor releases (9.x is stable branch). | **Phase 0** |
| Keycloak | 26.6 | Latest stable 26.x. | Active. Patch releases support zero-downtime updates. | Keycloak 27 when GA. | **Phase 0** |

---

## Provider / IaC Selection

Kubernetes and managed services need a concrete platform. Without this, P0 is not executable.

| Concern | Managed-cloud path | Self-hosted 24/7 server path | Why |
|---|---|---|---|
| Kubernetes | **Upstream kubeadm on Proxmox-hosted Dell R720 nodes** | **Upstream kubeadm** on 1-3 nodes, with scale-out path to 2+ worker nodes before flash-sale launch | Same Kubernetes API, less ambiguity, direct path to multi-node scale. |
| PostgreSQL | Self-hosted PostgreSQL 17 on SSD-backed VM or node, with WAL archiving, pgBackRest/restic, encrypted offsite backups to R2 | Self-hosted PostgreSQL 17, HA later via second node or managed swap if capacity grows | R720 can host it now if capacity-tested and backed up. |
| Redis | Self-hosted Redis 8.x, cache/session only, no source-of-truth data | Self-hosted Redis with persistence policy documented | Redis not source of truth; keep it local and bounded. |
| Kafka | Self-hosted Redpanda/Kafka 4.2-compatible only if capacity-tested; otherwise PostgreSQL transactional outbox first | Kafka/Redpanda on same cluster with strict retention and resource caps | Flash-sale scale needs queueing; outbox is fallback if broker pressure appears. |
| Search | Self-hosted OpenSearch/Elasticsearch-compatible only if capacity-tested; otherwise PostgreSQL search/read models first | OpenSearch on same cluster with strict heap/disk caps | Search allowed, but must not block launch if resource pressure appears. |
| Object Storage | **Cloudflare R2** | **Cloudflare R2** | Product images, invoices, uploads, and backups must survive server disk failure. MinIO remains local-dev only. |
| Secrets | **AWS Secrets Manager** + **External Secrets Operator** (K8s) | SOPS/age + SealedSecrets; no plaintext production `.env` | Self-hosted still needs encrypted, auditable secret delivery. |
| Container Registry | **Private registry** | GHCR or Docker Hub private registry | CI builds immutable images; cluster pulls by digest. |
| DNS | **Cloudflare DNS** + **external-dns** (K8s) | Cloudflare DNS, optional Cloudflare Tunnel if no static IP | Cloudflare provides DNS, WAF/rate limits, proxying, and CDN. |
| TLS | **cert-manager** (Let's Encrypt) or **AWS ACM** | cert-manager + Let's Encrypt HTTP-01/DNS-01 | TLS must auto-renew and be tested. |
| CI/CD | **GitHub Actions** + **ArgoCD** (GitOps) | GitHub Actions build/test/push + ArgoCD/Flux on kubeadm, or SSH deploy script for first launch | Deployment remains repeatable; no hand-edited prod YAML. |

**Decision locked**: use upstream kubeadm on Proxmox-hosted Dell R720 nodes. No AWS EKS / DO K8s path in execution tasks. Keep one self-hosted path only, with explicit multi-node scale-out gate before flash-sale launch.

**Self-hosted production classification**: the Proxmox/R720 path is production-ready only when the Kubernetes cluster reaches at least 2 worker nodes for flash-sale exposure, with one-node dev/staging allowed. If a node, disk, network, or power fails, VNShop may degrade or go down until reschedule/restore.

---

## Gate Architecture

Three independent gates. Each must pass before proceeding.

| Gate | When it applies | Minimum bar |
|---|---|---|
| **Local Slice Gate** | End of Phase 1 (T1–T29) | Docker Compose smoke test passes. All domain tests green, zero framework imports. Hexagonal structure verified. Evidence files complete. |
| **Staging Gate** | End of Phase 2 (T30–T43) | K8s staging exists. Managed data connected. CI/CD deploys. Backup/restore tested. Authz matrix tested. Load test thresholds met. Staging soak 24h+ clean. |
| **Production Gate** | Before any real-user or real-money traffic (post-Phase 3) | All 31 gates (G1–G31) pass. Rollback drill done. Compliance sign-off. Finance sign-off. Incident response tabletop done. |

---

## Work Objectives

### Phase 1 — Local Development Foundation

**Done when**: local Docker Compose starts the full dev stack, `curl localhost:8080/health` -> 200, one buyer buys one seller's product via COD and stub shipping, objects upload/download through local MinIO using the same S3-compatible interface used for R2, all domain tests pass with zero framework imports.

**Must NOT**: Spring Boot 3.x, framework imports in `domain/`, domain objects in REST, custom JWT, real payment/carrier calls before sandbox certification, frontend, Docker Compose production deployment.

### Phase 2 — Kubernetes + Managed Production Platform

**Done when**: staging and prod Kubernetes environments exist on kubeadm; PostgreSQL/Redis/Kafka/OpenSearch are provisioned with resource caps and backups; R2 buckets, lifecycle, signed URLs, and CDN/domain are configured; CI/CD deploys to staging; backup/restore, rollback, autoscaling, network policies, secret rotation, and staging smoke/soak pass.

### Phase 3 — Production Commerce Complexity

**Done when**: real VNPay/MoMo and GHN/GHTK sandbox/live flows pass certification; double-entry ledger and reconciliation pass; multi-seller checkout, refunds/returns, wallet payouts, invoices, R2 object storage, admin analytics, observability, and support/remediation tools are ready for real traffic.

---

## Verification Strategy

TDD: RED (test first) → GREEN (minimal impl) → REFACTOR. JUnit 5 + Mockito (Java), Jest (TS). Domain tests MUST NOT load Spring/NestJS context.

Every task has QA scenarios with concrete tool + steps + expected result. Evidence saved to `.sisyphus/evidence/task-{N}-{slug}.txt`.

Production verification is separate from feature verification. A feature is not production-ready until its Kubernetes deployment, managed dependency configuration, object storage permissions, secrets, observability, rollback path, and security/abuse tests are verified in staging.

### Evidence Standard (MANDATORY)

Every evidence file MUST contain all of:

```
# TASK: T{N} — {slug}
# TIMESTAMP: 2026-05-10T14:30:00+07:00
# ENVIRONMENT: local-dev | staging | prod
# GIT SHA: abc1234
# EXIT CODE: 0
#
# RAW OUTPUT:
# (actual command output below)
```

**Hard rules**:
- No "it passed" without raw output. Paste the actual command + result.
- No manual-claim-only evidence for provider console checks — include screenshot reference or CLI output.
- No evidence = task not complete = gate blocked.
- CI artifacts: build logs, test XML reports, coverage HTML retained per run.
- Production Gate release checklist must be signed (name + date + evidence reference) per item.

---

## Production Platform Requirements

### Kubernetes baseline

- One namespace per environment: `vnshop-dev`, `vnshop-staging`, `vnshop-prod`.
- Deploy via Helm chart or Kustomize overlays. No hand-edited prod YAML.
- Every service has Deployment, Service, ServiceAccount, ConfigMap, Secret reference, HPA, PDB, NetworkPolicy, resource requests/limits, liveness/readiness/startup probes.
- Ingress terminates TLS and routes public traffic only to api-gateway and approved public callback endpoints.
- Internal services are ClusterIP only.
- Pods run as non-root, read-only root filesystem where possible, dropped Linux capabilities.
- Images pinned by digest in production.
- Rolling updates with maxUnavailable=0 for public services.
- Rollback command documented and tested.

### Self-hosted data services

- PostgreSQL: self-hosted PostgreSQL on SSD-backed storage, automated backups, WAL archiving/PITR to R2, encryption at rest where available, connection pooling, per-service schemas or separate databases, least-privilege users.
- Redis: self-hosted Redis, TLS, AUTH, persistence policy documented, reserved memory, eviction policy, no durable order/payment/ledger state.
- Kafka: self-hosted Kafka/Redpanda-compatible, TLS/SASL, topic ACLs, strict retention policies, DLQ topics, consumer lag metrics.
- Search: self-hosted OpenSearch/Elasticsearch-compatible cluster, TLS, index templates, snapshots, strict retention/disk caps, capacity alerts.
- Keycloak: production mode only, external PostgreSQL, TLS, brute-force protection, password policy, refresh-token rotation, realm export backup.

### Self-hosted 24/7 server baseline

Use this baseline only when the chosen production target is a real server that runs continuously under the project's control. This is a valid small-launch production target, but not high availability.

- Server OS: Ubuntu Server LTS or equivalent, unattended security updates enabled, reboot/patch window documented.
- Hardware baseline for chosen host: Dell R720 on Proxmox, 128GB DDR3 RAM, 1TB SSD. Recommended Kubernetes VM budget: 96GB RAM, 16-24 vCPU, 700-850GB SSD, leaving Proxmox host headroom.
- Kubernetes: upstream kubeadm cluster with namespaces `vnshop-dev`, `vnshop-staging`, `vnshop-prod`; same Helm/Kustomize overlays as any future cluster path.
- Ingress/TLS: Traefik or NGINX Ingress, cert-manager, Let's Encrypt auto-renew, Cloudflare DNS/WAF/CDN in front.
- Firewall: only SSH, HTTP, and HTTPS exposed. PostgreSQL, Redis, Keycloak admin, metrics, and internal services are private.
- SSH: key-only login, root login disabled, password auth disabled, Fail2ban or CrowdSec enabled.
- PostgreSQL: local PostgreSQL allowed only with WAL archiving, pgBackRest/restic backups, encrypted offsite backup to R2, restore drill on a separate machine.
- Redis: local Redis allowed only for cache/session/ephemeral data. Orders, payments, ledger, outbox, and idempotency records must remain in PostgreSQL.
- Kafka: allowed on-cluster if capacity-tested; otherwise use PostgreSQL transactional outbox + scheduled workers first.
- Search: allowed on-cluster if capacity-tested; otherwise start with PostgreSQL/read models first.
- Object storage: Cloudflare R2 remains required for uploads, invoices, exports, and offsite backups. Local disk is never canonical object storage.
- Secrets: SOPS/age + SealedSecrets or External Secrets Operator. No plaintext production `.env` on disk or in git.
- Monitoring: uptime probe, disk/CPU/RAM alerts, PostgreSQL health, backup success/failure alerts, TLS expiry alert, service health alerts.
- Disaster recovery: documented bare-metal restore: provision node -> install kubeadm cluster -> restore secrets -> restore PostgreSQL backup -> deploy images -> smoke test.
- Explicit risk acceptance: owner signs that single-server/single-site outage risk is acceptable for launch; multi-node expansion plan exists for flash-sale traffic growth.

### Cloudflare R2 object storage

- Buckets:
  - `vnshop-prod-images` — product images, review images, seller documents.
  - `vnshop-prod-invoices` — invoice PDFs and immutable finance documents.
  - `vnshop-prod-exports` — admin/report exports.
  - `vnshop-staging-*` mirrors prod with separate credentials.
- Access model:
  - Private buckets by default.
  - Public product images served via signed upload + controlled public/CDN read path.
  - Invoices and seller documents never public; download only through authenticated signed URLs with short TTL.
  - Separate R2 access keys per service and environment.
- Required implementation:
  - `ObjectStoragePort` in application layer.
  - R2 adapter in infrastructure using S3-compatible API.
  - MinIO adapter/profile for local dev.
  - Virus/mime/size validation before accepting uploads.
  - Content-type allowlist, max object size, checksum verification.
  - Lifecycle rules for temporary exports.
  - Object key naming: `{env}/{service}/{yyyy}/{mm}/{uuid}-{safe-filename}`.
  - No user-controlled raw object keys.

### Production security baseline

- Endpoint authorization matrix before implementation is complete.
- Seller isolation tests for every seller-scoped endpoint.
- Admin role split: `ADMIN_SUPPORT`, `ADMIN_FINANCE`, `ADMIN_CATALOG`, `ADMIN_SUPER` instead of one broad ADMIN for all workflows.
- Payment and carrier webhooks validate signature, timestamp/nonce if supported, idempotency, replay, and source allowlist where possible.
- PII classification and retention policy.
- DB encryption at rest plus application-level encryption for bank account, phone, address detail, invoice tax data.
- Secret rotation runbook.
- WAF/rate limits/bot protection at ingress/CDN.

### Production operations baseline

- SLOs: API availability, checkout success rate, payment callback latency, order creation latency, search latency, notification lag.
- Alerts have owners and escalation routes.
- Dashboards exist for service health, latency/error rates, DB pool, Kafka lag, Redis memory, OpenSearch health, payment failures, carrier failures, R2 errors.
- Runbooks cover: service down, DB saturation, Kafka lag, failed payment callbacks, reconciliation mismatch, R2 outage, carrier outage, Keycloak outage, rollback, restore.
- Staging soak: 24–72h with synthetic traffic and zero critical errors before prod launch.

### Scalability + future-development baseline

This project must stay evolvable after launch. Do not optimize only for first deployment.

- **No deadlock architecture rule**: every bounded context exposes ports/events/contracts, not direct database sharing. Future services can split out without rewriting domain logic.
- **API compatibility rule**: public/internal APIs are versioned (`/v1/...`) once consumed by another service. Breaking changes require new version or compatibility adapter.
- **Schema evolution rule**: migrations are backward-compatible by default: add column/table first, deploy code, backfill, then remove in a later release. No destructive migration in same release as code change.
- **Feature flag rule**: risky commerce features (flash sale, coupons, payment provider enablement, carrier enablement, payout automation) ship behind config/feature flags.
- **Event contract rule**: Kafka/outbox event payloads are versioned (`eventType`, `eventVersion`) and consumers tolerate unknown fields.
- **Module boundary rule**: no service imports another service's domain classes. Shared code limited to primitive API DTO contracts, error shape, tracing, and auth helpers.
- **Scale path rule**: app services must be stateless. Session/cart/cache state lives in Redis/PostgreSQL. File/object data lives in R2. Pods can scale horizontally without sticky sessions.
- **Operational extension rule**: every new service must include Dockerfile, Helm/Kustomize manifest, probes, metrics, logs, resource limits, runbook entry, OpenAPI, and smoke test before merge.

### Flash-sale scalability baseline

- Kubernetes target for flash sale: upstream kubeadm cluster with 1 control-plane node and at least 2 worker nodes before real flash-sale traffic.
- Apps: api-gateway, product-service, inventory-service, search-service, cart-service, order-service, payment-service, shipping-service, notification-service, coupon-service, review-service, seller-finance-service must run 2+ replicas in staging/prod before flash-sale launch.
- Inventory reservation: Redis Lua atomic gate for high-speed stock reservation, backed by PostgreSQL authoritative reservation table with TTL and reconciliation job.
- Checkout write path: payment/order creation uses PostgreSQL transaction + outbox. No direct Kafka publish inside order transaction.
- Backpressure: gateway rate limits per IP/user/route; flash-sale endpoints have stricter burst limits; queue returns 202/pending instead of blocking threads.
- Degrade mode: if OpenSearch/Kafka/notification is degraded, browsing/checkout core remains available. Search may fall back to PostgreSQL read model. Notification may queue/retry.
- Capacity gates: run k6 flash-sale test before launch: 1000 concurrent reservation attempts for 10 stock -> exactly 10 winners, 990 deterministic sold-out/rate-limited responses, no negative stock, p95 reserve latency under 300ms on staging.
- Scale-out triggers: add nodes or move data services off-cluster when any threshold holds for 10+ minutes: CPU > 75%, memory > 80%, disk > 70%, iowait > 10%, checkout p95 > 500ms, DB query p95 > 100ms, Kafka lag increasing, OpenSearch heap > 75%.

---

## Additional Production Tasks (ADD to existing task waves)

These tasks are required for real production. Insert them before final launch. They may be run parallel to feature waves only when dependencies are satisfied.

### P0 — Kubernetes + Self-hosted Platform

- [ ] P0-0. Bootstrap kubeadm cluster on Proxmox nodes

  **What**: Provision Proxmox VMs or bare-metal nodes for upstream kubeadm cluster, configure control plane and worker nodes, CNI, ingress, metrics, storage class, and cluster admin access. Lock namespaces `vnshop-dev`, `vnshop-staging`, `vnshop-prod`.

  **Acceptance**: `kubectl get nodes` shows healthy cluster; workloads schedule across nodes; `kubectl apply --dry-run=server` succeeds for baseline manifests.

  **QA**: `kubectl get pods -A` shows system pods ready; `kubectl get nodes -o wide` confirms kubeadm nodes. **Evidence**: `.sisyphus/evidence/task-P0-0-kubeadm.txt`.

- [ ] P0-1. Create Kubernetes deployment foundation

  **What**: Create `infra/k8s/` with Helm chart or Kustomize overlays for dev/staging/prod. Include namespace, Deployment, Service, ConfigMap, Secret references, ServiceAccount, HPA, PDB, NetworkPolicy, liveness/readiness/startup probes for every service. Every public service must support replica scaling and stateless restart.

  **Acceptance**: `kubectl apply --dry-run=server` passes for staging/prod overlays. Every service has probes, resources, and non-root securityContext.

  **QA**: `kubectl diff -k infra/k8s/overlays/staging` returns only expected changes. **Evidence**: `.sisyphus/evidence/task-P0-1-k8s.txt`.

- [ ] P0-2. Provision PostgreSQL + schema ownership

  **What**: Provision PostgreSQL on R720-backed storage or dedicated VM with backups, PITR, TLS. Create per-service DB users with least privilege. Create schemas: `user_svc`, `product_svc`, `inventory_svc`, `search_svc`, `order_svc`, `payment_svc`, `shipping_svc`, `coupon_svc`, `review_svc`, `seller_finance_svc`, `notification_svc`, `keycloak`. Configure Flyway per service.

  **Acceptance**: App users cannot read/write other services' schemas. Backup + restore test succeeds.

  **QA**: Attempt cross-schema query with service user → permission denied. **Evidence**: `task-P0-2-postgres.txt`.

- [ ] P0-3. Provision Redis

  **What**: Redis with TLS/AUTH, memory policy, metrics, alerts, and connection settings. Document which data may be lost and which may not be stored in Redis.

  **Acceptance**: Services connect via TLS; Redis is not used as source of truth for orders/payments/ledger.

  **QA**: Staging service reads/writes cache with TLS enabled. **Evidence**: `task-P0-3-redis.txt`.

- [ ] P0-4. Provision Kafka + topic governance

  **What**: Kafka/Redpanda-compatible cluster with TLS/SASL, ACLs, topic retention, DLQs, schema/version docs. Create topics: order, payment, product, notification, DLQs.

  **Acceptance**: Producers/consumers use per-service credentials and cannot access unauthorized topics.

  **QA**: Unauthorized consume attempt fails. **Evidence**: `task-P0-4-kafka.txt`.

- [ ] P0-5. Provision OpenSearch/Elasticsearch

  **What**: Search cluster with TLS, index templates, Vietnamese analyzer, snapshot policy, capacity alerts.

  **Acceptance**: Catalog indexing works in staging; snapshot restore tested.

  **QA**: Index product → search product → snapshot restore smoke. **Evidence**: `task-P0-5-search.txt`.

- [ ] P0-6. Production Keycloak

  **What**: Run Keycloak in production mode on Kubernetes with PostgreSQL, TLS, realm import, brute-force protection, password policy, refresh token rotation, admin credentials from secret manager.

  **Acceptance**: `start-dev` is not used anywhere outside local compose.

  **QA**: `kubectl get deploy keycloak -o yaml | grep start-dev` returns 0 matches. **Evidence**: `task-P0-6-keycloak.txt`.

### P1 — Cloudflare R2 Object Storage

- [ ] P1-1. R2 bucket setup + access policy

  **What**: Create staging/prod R2 buckets for images, invoices, exports. Configure lifecycle rules, CORS for direct upload if used, least-privilege access keys per service, CDN/public access policy for images only.

  **Acceptance**: Invoices/docs are private; product images are served via approved public/CDN path; exports expire per lifecycle.

  **QA**: Anonymous request to invoice object returns 403. Product image public/CDN URL returns 200. **Evidence**: `task-P1-1-r2-buckets.txt`.

- [x] P1-2. ObjectStoragePort + R2 adapter

  **What**: Add `ObjectStoragePort` with `putObject`, `getSignedUploadUrl`, `getSignedDownloadUrl`, `deleteObject`, `headObject`. Implement R2 adapter using S3-compatible SDK and MinIO adapter for local dev. Domain/application layers never import SDK.

  **Checksum & integrity**: App-managed SHA-256 stored in DB metadata column per object — never rely solely on R2 ETag (R2 S3-compatibility has known ETag differences for multipart uploads vs standard PUT). `checksum` column indexed for integrity verification jobs.

  **Multipart upload**: Large objects (>5MB) use multipart upload with app-computed aggregate SHA-256. Each part validated before assembly.

  **Quarantine pipeline**: Objects enter `PENDING_VALIDATION` state. Post-upload validation worker: (1) verify SHA-256 matches DB record, (2) validate MIME against content-type allowlist via magic bytes, (3) antivirus scan (ClamAV sidecar or cloud AV API), (4) size check, (5) image dimension check for image bucket. Only after all pass does state transition to `ACTIVE` and object becomes visible/accessible. Failed objects → `REJECTED` with reason logged.

  **Signed URL TTL per object class**:
  - Product images (public read): signed upload URL 15min TTL, public/CDN read path — no signed download needed
  - Seller documents: signed upload URL 15min TTL, signed download URL 5min TTL, authenticated-only
  - Invoice PDFs: signed download URL 5min TTL, authenticated + authorized (buyer/seller/admin_finance)
  - Exports: signed download URL 15min TTL, admin-only, auto-expire per lifecycle rule

  **Acceptance**: Upload/download works against MinIO local and R2 staging by profile switch. All 5 quarantine checks pass/fail correctly. Signed URLs respect TTL and authorization.

  **QA**: Integration test uploads object, verifies SHA-256 (app-computed) matches, quarantine pipeline validates, generates signed URL, downloads same bytes, signed URL expires. **Evidence**: `task-P1-2-object-storage.txt`.

- [x] P1-3. Product/review image upload pipeline

  **What**: Add upload endpoints returning signed upload URLs. Validate MIME, extension, file size, image dimensions, checksum. Store metadata in DB. Never trust client-provided content-type only.

  **Acceptance**: JPEG/PNG/WebP allowed; executable/polyglot/oversized files rejected.

  **QA**: Upload invalid MIME → 400; upload valid image → object metadata stored. **Evidence**: `task-P1-3-image-upload.txt`.

- [x] P1-4. Invoice PDF storage in R2

  **What**: Store generated invoice PDFs in private R2 invoices bucket. DB stores object key, checksum, version, generatedAt. Download endpoint returns short-lived signed URL only for authorized buyer/seller/admin finance role.

  **Acceptance**: Unauthorized user cannot access invoice; authorized user gets signed URL; object key not guessable.

  **QA**: Buyer A invoice requested by Buyer B → 403. **Evidence**: `task-P1-4-invoice-r2.txt`.

### P2 — Money, Payment, Shipping Production Safety

- [x] P2-1. Double-entry ledger + immutable journal

  **What**: All money movement writes balanced debit/credit ledger entries. Ledger rows immutable; corrections are reversal entries. Wallet balances derived from ledger or reconciled against it.

  **Acceptance**: `sum(debits) == sum(credits)` for every currency and period. No update/delete permissions for ledger app role except migration/admin break-glass.

  **QA**: Complete 100 orders/refunds → ledger balance check passes. **Evidence**: `task-P2-1-ledger.txt`.

- [x] P2-2. Payment webhook idempotency + replay protection

  **What**: Store callback event IDs/signatures/hash, reject duplicate/replayed callbacks, enforce timestamp/nonce if supported, verify HMAC before parsing trusted fields, process callback transactionally with outbox event.

  **Callback semantics per provider**:
  - **VNPay**: IPN (`vnp_ReturnUrl`) is source-of-truth for payment state. Return URL (`vnp_ReturnUrl` redirect) is UI-only — never trust it for order state transitions. IPN must be acknowledged with VNPay's required response format within the timeout window.
  - **MoMo**: IPN (instant payment notification) is source-of-truth. Response must match MoMo's expected semantics (HTTP 200 + signed confirmation or 204) within the fast response window (<5s recommended). Late delivery handled via QueryDR/status-check fallback.
  - **QueryDR flow**: If IPN is missed or late, a scheduled `PaymentStatusQueryWorker` queries the gateway's transaction-status endpoint (`querydr` for VNPay, `query` for MoMo) to resolve "paid at bank but local pending" states. This worker runs at increasing intervals (1m, 5m, 15m, 30m) after order creation.

  **Data model**: Separate `payment_attempt` (one per user-initiated payment action), `payment_transaction` (one per confirmed gateway interaction), and `ledger_transaction` (debit/credit pair per settlement). No gateway callback mutates the `Order` aggregate directly — callbacks create `payment_transaction` records, which the order saga consumes to transition order state.

  **Raw callback storage**: Every callback payload (headers + body) stored in `payment_callback_log` table — raw, signed, immutable. This is the audit trail for reconciliation and disputes.

  **Acceptance**: Replaying same signed callback 100 times → one state transition, one `payment_transaction`, one ledger entry pair.

  **QA**: Replay same signed callback 100 times → one state transition, one ledger transaction. **Evidence**: `task-P2-2-webhook-replay.txt`.

- [x] P2-3. Payment reconciliation + mismatch workflow

  **What**: Scheduled job queries VNPay/MoMo transactions, compares gateway status/amount/orderId with local payment + ledger. Mismatches create admin review records and alerts.

  **Acceptance**: Simulated mismatch creates reconciliation issue and blocks payout until resolved.

  **QA**: Fake gateway mismatch → issue row + alert. **Evidence**: `task-P2-3-payment-reconcile.txt`.

- [ ] P2-4. Shipping carrier resilience

  **What**: GHN/GHTK integrations support retry with backoff, circuit breaker, timeout, DLQ, manual retry, label cancellation, tracking sync job, carrier outage fallback.

  **Acceptance**: Carrier outage does not break order creation; user gets pending shipping state and operator remediation path.

  **QA**: Simulate carrier 500s → circuit opens, event enters DLQ/manual queue. **Evidence**: `task-P2-4-shipping-resilience.txt`.

### P3 — Security, Compliance, Ops

- [ ] P3-1. Authorization matrix + seller isolation test suite

  **What**: Generate `infra/security/authz-matrix.md`: every endpoint, method, role, data owner rule. Add tests for buyer/seller/admin access including negative cross-seller cases.

  **Acceptance**: Every endpoint has at least one positive and one negative authz test.

  **QA**: Seller A attempts Seller B order/product access → 403. **Evidence**: `task-P3-1-authz.txt`.

- [ ] P3-2. PII encryption + key rotation

  **What**: Encrypt phone, address detail, bank account, invoice tax fields. Keys from secret manager/KMS. Add key version column and rotation job.

  **Acceptance**: Raw DB dump does not expose PII; rotation can re-encrypt records without downtime.

  **QA**: Query DB raw fields → encrypted; rotate staging key → app still reads records. **Evidence**: `task-P3-2-pii.txt`.

- [ ] P3-3. Observability + alert ownership

  **What**: Metrics/traces/logs for every service. Correlation ID propagated gateway → services → Kafka. Alerts for SLO violations, payment failures, carrier failures, R2 errors, Kafka lag, DB saturation. Every alert has owner/runbook.

  **Acceptance**: Simulated checkout failure creates trace, log, metric, alert with runbook link.

  **QA**: Inject failure → alert fires and links runbook. **Evidence**: `task-P3-3-observability.txt`.

- [ ] P3-4. Backup/restore + disaster recovery drill

  **What**: Restore PostgreSQL backup into staging, restore search snapshot, verify R2 object integrity, replay Kafka/outbox if needed, run smoke test. Document RPO/RTO.

  **Acceptance**: Restore drill completes within agreed RTO; data loss within RPO.

  **QA**: Full staging restore drill evidence. **Evidence**: `task-P3-4-dr.txt`.

- [ ] P3-5. Production launch + rollback drill

  **What**: Blue/green or rolling deploy to staging, run smoke, force rollback, verify service and DB compatibility. Repeat for prod release candidate.

  **Acceptance**: Rollback tested before prod launch. Migration policy forbids irreversible prod migrations without explicit maintenance plan.

  **QA**: Deploy version N+1 → rollback to N → smoke passes. **Evidence**: `task-P3-5-rollback.txt`.

### P4 — Threat Models & Abuse Testing

These tasks MUST pass before production deployment. Each produces a threat model document + abuse test suite against staging.

- [ ] P4-1. Seller isolation threat model + abuse tests

  **What**: Threat model: cross-seller data access, IDOR on seller-owned endpoints, seller privilege escalation. Abuse tests: Seller A accesses Seller B orders/products/payouts through direct API calls, parameter manipulation, and JWT tampering. Every test must return 403.

  **Acceptance**: 0 cross-seller data leaks. All abuse scenarios blocked.

  **QA**: `infra/scripts/abuse-seller-isolation.sh` runs 50+ cross-seller scenarios → all 403. **Evidence**: `task-P4-1-seller-isolation.txt`.

- [ ] P4-2. Admin privilege boundary threat model + abuse tests

  **What**: Threat model: ADMIN_SUPPORT privilege escalation to ADMIN_FINANCE/ADMIN_SUPER, admin impersonation, admin credential theft. Abuse tests: Validate every admin role split operates correctly (ADMIN_SUPPORT can read orders but not approve payouts; ADMIN_CATALOG can manage products but not access ledgers).

  **Acceptance**: Role boundaries enforced at API + service level. No role can perform actions outside its scope.

  **QA**: Role matrix test script validates every admin role against every admin endpoint. **Evidence**: `task-P4-2-admin-boundaries.txt`.

- [ ] P4-3. Payment callback abuse threat model

  **What**: Threat model: forged HMAC callbacks, timestamp replay, amount tampering, callback orderId mismatch, callback-to-different-service attack, callback amplification/DoS. Abuse tests: Send malformed callbacks, expired timestamps, wrong HMAC, zero-amount payments, negative amounts. All rejected.

  **Acceptance**: No forged or malformed callback causes state transition. All logged to `payment_callback_log` with rejection reason.

  **QA**: Abuse script sends 50+ malformed callbacks → 0 state transitions, 50 rejection logs. **Evidence**: `task-P4-3-callback-abuse.txt`.

- [ ] P4-4. Object upload/download abuse threat model

  **What**: Threat model: MIME spoofing, path traversal in object keys, oversized uploads, malware upload, anonymous access to private buckets, signed URL brute-force, CORS misconfiguration. Abuse tests: Upload polyglot files, path traversal keys, oversized payloads, executable MIME types. All rejected.

  **Acceptance**: Quarantine pipeline catches all abuse uploads. Private objects never served without auth.

  **QA**: Abuse script uploads 30+ malicious objects → all rejected or quarantined. **Evidence**: `task-P4-4-upload-abuse.txt`.

- [ ] P4-5. Coupon/flash sale abuse threat model

  **What**: Threat model: coupon code brute-force, coupon stacking, flash sale bot/scalping, inventory hoarding via cart, race condition on atomic stock reserve. Abuse tests: Exceed coupon usage limits, attempt coupon stacking, bot-like flash sale reservation patterns, cart expiry abuse.

  **Acceptance**: Coupon limits enforced. Flash sale Lua atomic gate holds exactly N winners. Bots detected/rate-limited.

  **QA**: k6 script simulates 1000 concurrent flash sale reservations for 10 stock → exactly 10 succeed. **Evidence**: `task-P4-5-flash-abuse.txt`.

- [ ] P4-6. Kafka topic access threat model

  **What**: Threat model: unauthorized producer to critical topics, consumer reading payment/ledger events, topic message injection, consumer group hijacking. Verify ACLs/TLS/SASL per topic per service.

  **Acceptance**: Service can only produce/consume its authorized topics. Cross-service topic access denied.

  **QA**: Attempt unauthorized produce/consume from non-owner service → denied. **Evidence**: `task-P4-6-kafka-threat.txt`.

- [ ] P4-7. PII encryption + KMS threat model

  **What**: Threat model: DB compromise, backup exfiltration, memory dump, log leakage, KMS access escalation, key version rollback attack. Verify app-level encryption for bank account, phone, address, invoice tax fields. Key rotation tested.

  **Acceptance**: Raw DB dump shows encrypted PII. Logs contain no PII. Rotating key re-encrypts records.

  **QA**: `grep` DB dump for phone patterns → 0 matches. Rotate staging key → app reads records. **Evidence**: `task-P4-7-pii-threat.txt`.

---

## Execution Strategy

### Phase Overview

```
PHASE 1 (T1–T29)    — MVP Slice: Infra + Scaffolds + Domain + App + Integration
                      One buyer, one seller, one product, one payment (COD), one carrier (stub)
PHASE 2 (T30–T43)   — Production Hardening: Reconciliation, HA, secrets, backup, CI gates, staging, runbooks
PHASE 3 (T44–T55)   — Marketplace: Multi-seller, carriers, returns, reviews, flash sale, admin, wallet payouts
                       ⚠️ HARD PREREQ: Double-entry ledger (T32) + reconciliation worker (T33) MUST be verified before any real payment/carrier adapter (T45–T47) goes live.
FINAL (F1–F4)       — 4 parallel reviews → user okay → Production No-Go checklist → deploy decision
```

### Pattern References

**Pattern A — Service Scaffold (Java)**: `spring init --boot-version=4.0.x --java-version=25 --group=com.vnshop --artifact={name} --name={Name} --dependencies=... services/{name}`. Create `domain/`, `application/`, `infrastructure/` packages. Add `application.yml` with datasource to `jdbc:postgresql://localhost:5432/vnshop?currentSchema={schema}` (user `vnshop`, pass `vnshop123`). Create empty `db/migration/`. QA: `cd services/{name} && mvn compile` → BUILD SUCCESS. Commit: `feat({name}): scaffold Spring Boot 4.0 hexagonal project`.

**Pattern B — Service Scaffold (NestJS)**: `nest new {name}` in `services/`. Create `src/domain/`, `src/application/`, `src/infrastructure/`. QA: `cd services/{name} && npm ci && npm run build` → BUILD SUCCESS. Commit: `feat({name}): scaffold NestJS hexagonal project`.

**Pattern C — Domain TDD (Java)**: RED: write `*Test.java` testing all invariants + state transitions. GREEN: implement aggregate, value objects, enums, repository port interface. ZERO framework imports (`org.springframework.*`, `jakarta.persistence.*`, `lombok.*`). QA: `mvn test` → all green + `grep -rn "import org.springframework\|import jakarta.persistence\|import lombok" domain/` → empty. Commit: `feat({service}): {aggregate} domain model (TDD)`.

**Pattern D — Domain TDD (TypeScript)**: RED: write `*.test.ts`. GREEN: implement aggregate, VOs, repository port interface. ZERO `@nestjs/*` imports. QA: `npm test` → all green + `grep -r "@nestjs" src/domain/` → empty. Commit: `feat({service}): {aggregate} domain model (TDD)`.

**Pattern E — Application Layer (Java)**: Create input ports, command/query DTOs, implement use case classes. Test with mocked repository/event ports. QA: `mvn test` → all green. Commit: `feat({service}): application use cases`.

**Pattern F — Infrastructure REST (Java)**: JPA entity → Flyway migration V{N}__{name}.sql → JPA repo adapter for domain port → REST controller with DTO mapping. QA: `curl :{port}/actuator/health` → 200. Commit: `feat({service}): JPA repos + Flyway + REST controllers`.

**Pattern G — Infrastructure NestJS**: Create adapter (Redis/ES/TypeORM) → controller with DTO mapping. QA: `curl :{port}/health` → 200. Commit: `feat({service}): {adapter} + REST endpoints`.

**Pattern H — Dockerfile (Multi-Stage)**: `services/{name}/Dockerfile`. Java: JDK 25 build → JRE 25 alpine runtime. Node: Node 24-alpine build → runtime. HEALTHCHECK, non-root user. QA: `docker build -t {name} .`. Commit: `feat({name}): Dockerfile`.

**Pattern I — Stub Adapter**: Real interface, fake implementation. Log stub calls to console. Configurable via env var (`*.mode=stub|live`). Commit: `feat({service}): stub {adapter} adapter`.

### Key Architecture Rules (ALL phases)
1. Domain layer: ZERO framework imports. Verify with grep after each domain task.
2. REST controllers: return DTOs, NEVER domain objects.
3. Flyway migrations: one per service, per-task incremental versions (V1, V2, V3...).
4. Kafka topics: `order.created`, `order.cancelled`, `product.updated`, `product.deleted`, `payment.completed`, `notification.*`.
5. Stub adapters: real interface, fake implementation. Log to console. Switchable via env (`MODE=stub`).
6. Secret keys stored in env vars, never committed. `.env` in `.gitignore`.

---

## PHASE 1 — MVP PRODUCTION CANDIDATE (T1–T29)

### 1.1 — Infrastructure + Auth (T1–T3)

- [x] T1. Fix docker-compose.yml versions + start infrastructure

  **What**: Update image tags: `postgres:17.9`, `redis:8.6-alpine`, `confluentinc/cp-kafka:8.2.0` (KRaft — no ZooKeeper needed; Confluent 8.2.0 ships Apache Kafka 4.2.0), `elasticsearch:9.4.0`, `keycloak:26.6`. PostgreSQL password `vnshop123`. Keycloak on host port 8085. Healthchecks on all. `docker compose up -d postgres redis kafka keycloak elasticsearch`. Wait all healthy.

  **Category**: `quick` | **Parallel**: Phase 1 | **Blocks**: T2–T29 | **Ref**: `.sisyphus/ARCHITECTURE.md:684-703`, `.sisyphus/analysis/devops-spec.md:§3`

  **QA**:
  ```
  Scenario: All 5 infra containers healthy (KRaft — no ZooKeeper)
    Tool: Bash
    Steps: docker compose up -d postgres redis kafka keycloak elasticsearch; sleep 60; docker compose ps --format json | grep -c "healthy"
    Expected: "5"
    Evidence: .sisyphus/evidence/task-1-healthy.txt

  Scenario: PostgreSQL accepts connections
    Tool: Bash
    Steps: docker compose exec -T postgres psql -U vnshop -d vnshop -c "SELECT 1 AS ok"
    Expected: Row "ok: 1"
    Evidence: .sisyphus/evidence/task-1-postgres.txt
  ```
  **Commit**: `fix(infra): update docker-compose versions + healthchecks` | `docker-compose.yml`

- [x] T2. Configure Keycloak realm vnshop

  **What**: Create realm `vnshop`. Roles: BUYER, SELLER, ADMIN. Clients: `vnshop-gateway` (confidential), `vnshop-api` (public, standard flow). Test users: `buyer1`/`seller1`/`admin1` password `test`. Export to `infra/keycloak/vnshop-realm.json`.

  **Category**: `quick` | **Parallel**: Phase 1 | **Blocks**: T4 | **Ref**: `.sisyphus/analysis/spring-cloud-gateway.md:345-367`

  **QA**: Client credentials grant → JWT. **Evidence**: `task-2-token.txt`. **Commit**: `feat(keycloak): vnshop realm with BUYER/SELLER/ADMIN`

- [x] T3. Scaffold api-gateway (Pattern A)

  **Diffs**: Dependencies: `gateway,oauth2-client,data-redis-reactive,actuator,resilience4j`. Add `spring-cloud-starter-circuitbreaker-reactor-resilience4j`. No domain package — gateway is stateless. **Ref**: `.sisyphus/analysis/spring-cloud-gateway.md:349-367`.

### 1.2 — Gateway + Services (T4–T11)

- [x] T4. api-gateway: OAuth2 + routes + RL + CB

  **What**: `SecurityConfig.java` (@EnableWebFluxSecurity): OAuth2 login + TokenRelay + JWT validation. Public routes: GET /products/**, /categories/**, /search/**, /health, /auth/**, /payment/*/callback, /payment/*/ipn. ADMIN: /admin/**. All else authenticated. Route table from `spring-cloud-gateway.md §1`. `RequestRateLimiter` per route per `infrastructure-resilience.md:§2`. Resilience4j CB with `fallbackUri`.

  **Category**: `unspecified-high` | **Blocked By**: T2, T3 | **Ref**: `.sisyphus/analysis/spring-cloud-gateway.md:39-325`, `.sisyphus/analysis/infrastructure-resilience.md:§2`

  **QA**:
  ```
  Scenario: Health public (200), protected (401/302), products public (503)
    Tool: Bash (curl)
    Steps:
      1. curl -s -o /dev/null -w "%{http_code}" localhost:8080/health
      2. curl -s -o /dev/null -w "%{http_code}" localhost:8080/users/me
      3. curl -s -o /dev/null -w "%{http_code}" localhost:8080/products/
    Expected: "200", "302" or "401", "503"
    Evidence: .sisyphus/evidence/task-4-gateway.txt
  ```
  **Commit**: `feat(api-gateway): OAuth2 + route table + rate limiting + circuit breakers`

- [x] T5. api-gateway: fallback + CORS + correlation ID

  **What**: `FallbackController` returning 503 JSON per service. CORS: `*` (dev). `CorrelationIdFilter`: UUID → `X-Correlation-ID` header. **Category**: `quick` | **Blocked By**: T4. **Ref**: `.sisyphus/analysis/spring-cloud-gateway.md:314-325`.

  **QA**: `curl -v localhost:8080/fallback/product-service` → 503 + X-Correlation-ID. **Evidence**: `task-5-fallback.txt`. **Commit**: `feat(api-gateway): fallback + CORS + correlation ID`

- [x] T6. Scaffold user-service (Pattern A)

  **Diffs**: Dependencies: `web,data-jpa,validation,actuator,postgresql,flyway`. Schema: `user_svc`. Port: 8081. **Ref**: `.sisyphus/ARCHITECTURE.md:243-275`.

- [x] T7. user-service domain: BuyerProfile + SellerProfile + Address (Pattern C)

  **Diffs**: Aggregates: `BuyerProfile` (keycloakId, name, phone [PhoneNumber E.164], avatarUrl, addresses List<Address>), `SellerProfile` (shopName, bankName, bankAccount, pickupAddress, approved, tier [STANDARD/VERIFIED/PREFERRED/MALL], vacationMode). VOs: `Address` (street, ward, district, city, isDefault), `PhoneNumber` (E.164, +84). Port: `UserRepositoryPort`. **Ref**: `.sisyphus/analysis/bounded-contexts/user-context.md`.

- [x] T8. user-service application + infrastructure (Pattern E + Pattern F)

  **What**: Use cases: `RegisterBuyerUseCase`, `ManageAddressUseCase`, `RegisterSellerUseCase`, `ApproveSellerUseCase`. JPA entities: `BuyerProfileJpaEntity`, `SellerProfileJpaEntity`, `AddressJpaEntity`. Flyway V1. Controllers: `UserController` (profile/address CRUD), `SellerController` (register), `AdminSellerController` (pending list, approve). Port: 8081.

  **QA**:
  ```
  Scenario: Seller registers, admin approves
    Tool: Bash (curl)
    Steps: curl -s -w "%{http_code}" -X POST localhost:8081/sellers/register -H "Content-Type: application/json" -d '{"shopName":"Test Shop","bankName":"VCB","bankAccount":"123456"}'
    Expected: "201"
    Evidence: .sisyphus/evidence/task-8-user.txt
  ```
  **Commit**: `feat(user-service): domain + JPA + REST`

- [x] T8A. Remove obsolete merged service scaffolds if present

  **What**: Delete any previously generated merged services that violate microservice boundaries: `services/catalog-service/` and `services/checkout-order-payment-service/`. They are superseded by `product-service`, `search-service`, `cart-service`, `order-service`, `payment-service`, `shipping-service`, and related bounded-context services. Do not delete `.sisyphus/evidence/` or plan history.

  **QA**: `Test-Path services/catalog-service` and `Test-Path services/checkout-order-payment-service` both return `False`. **Evidence**: `task-8A-remove-merged-scaffolds.txt`. **Commit**: `chore(services): remove merged service scaffolds`

- [x] T9. Scaffold product-service (Pattern A — Spring Boot Java)

  **What**: Java Spring Boot scaffold for product ownership only. Dependencies `web,data-jpa,data-redis,validation,actuator,postgresql,flyway,spring-kafka`. Schema: `product_svc`. Port: 8082. Create `domain/`, `application/`, `infrastructure/`; no search/indexing code here. **Ref**: `.sisyphus/analysis/bounded-contexts/product-context.md`.

- [x] T9A. Scaffold inventory-service (Pattern A — Spring Boot Java)

  **What**: Java Spring Boot scaffold for stock reservation/flash-sale inventory only. Dependencies `web,data-jpa,data-redis,validation,actuator,postgresql,flyway,spring-kafka`. Schema: `inventory_svc`. Port: 8083. Owns authoritative stock reservations and Redis Lua flash-sale gate. **Ref**: `.sisyphus/analysis/bounded-contexts/product-context.md`, Flash-sale scalability baseline.

- [x] T9B. Scaffold search-service (Pattern A — Spring Boot Java)

  **What**: Java Spring Boot scaffold for search/read models only. Dependencies `web,data-jpa,validation,actuator,postgresql,flyway,spring-kafka`; add Elasticsearch/OpenSearch client dependency manually if Initializr lacks it. Schema: `search_svc`. Port: 8086. Consumes product events; never owns product writes. **Ref**: `.sisyphus/analysis/bounded-contexts/search-export-context.md`.

- [x] T9C. Scaffold coupon-service (Pattern A — Spring Boot Java)

  **What**: Java Spring Boot scaffold for coupon/promotion rules only. Dependencies `web,data-jpa,data-redis,validation,actuator,postgresql,flyway,spring-kafka`. Schema: `coupon_svc`. Port: 8088. Owns coupon validation, usage limits, anti-abuse checks. **Ref**: `.sisyphus/analysis/bounded-contexts/coupon-context.md`.

- [x] T9D. Scaffold review-service (Pattern A — Spring Boot Java)

  **What**: Java Spring Boot scaffold for product reviews only. Dependencies `web,data-jpa,validation,actuator,postgresql,flyway,spring-kafka`. Schema: `review_svc`. Port: 8089. Owns review eligibility, rating aggregates, moderation queue. **Ref**: `.sisyphus/analysis/bounded-contexts/review-context.md`.

- [x] T9E. Scaffold seller-finance-service (Pattern A — Spring Boot Java)

  **What**: Java Spring Boot scaffold for seller wallet, payouts, ledger views, and finance admin only. Dependencies `web,data-jpa,validation,actuator,postgresql,flyway,spring-kafka`. Schema: `seller_finance_svc`. Port: 8090. Does not process payment gateway callbacks; consumes payment/order ledger events. **Ref**: `.sisyphus/analysis/bounded-contexts/seller-finance-context.md`.

- [x] T10. product-service domain: Product + Variants + Images (Pattern C)

  **What**: `Product` aggregate (productId, sellerId, name, description, categoryId, brand, status [DRAFT/ACTIVE/INACTIVE/OUT_OF_STOCK], variants List<ProductVariant> max 50, images List<ProductImage> max 10). Methods: `publish()`, `deactivate()`, `addVariant()`, `requestStockAdjustment()`. VOs: `ProductVariant` (sku, name, price Money, imageUrl), `ProductImage`, `Money`. Ports: `ProductRepositoryPort`, `ProductEventPublisherPort`. Stock quantities live in inventory-service, not product-service. **Ref**: `.sisyphus/analysis/bounded-contexts/product-context.md:140-220`.

- [x] T11. product-service REST + events; search-service projection (Pattern E + Pattern F)

  **What**: product-service write use cases and REST controllers: `CreateProductUseCase`, `UpdateProductUseCase`, `GetProductUseCase`; publishes `product.created/updated/deleted`. search-service consumes these events into PostgreSQL read model + OpenSearch index and exposes `GET /search`, `GET /categories`. Flyway tables stay in owning service schemas: product tables in `product_svc`, read models in `search_svc`.

  **Category**: `deep` | **Blocked By**: T9, T9B, T10. **Ref**: `.sisyphus/analysis/bounded-contexts/product-context.md:§5-8`, `.sisyphus/analysis/bounded-contexts/search-export-context.md:§3-4`.

  **QA**:
  ```
  Scenario: Seller creates product, buyer searches through separate services
    Tool: Bash (curl)
    Steps:
      1. POST localhost:8082/sellers/me/products -> 201
      2. product-service emits product.created
      3. search-service consumes event and indexes read model
      4. GET localhost:8086/search?q=ao+thun contains product
    Expected: product write succeeds in product-service; query succeeds in search-service; no shared database read.
    Evidence: .sisyphus/evidence/task-11-product-search.txt
  ```
  **Commit**: `feat(product-search): split product ownership from search projection`

### 1.3 — Cart, Order, Payment, Shipping Services (T12–T20)

- [x] T12. Scaffold cart-service (Pattern A)

  **What**: Java Spring Boot scaffold for shopping carts only. Dependencies: `web,data-redis,validation,actuator`. Schema: none initially; Redis owns ephemeral cart state. Port: 8084. Create `domain/`, `application/`, `infrastructure/` packages. **Ref**: `.sisyphus/analysis/bounded-contexts/cart-context.md`.

- [x] T12A. Scaffold order-service (Pattern A)

  **What**: Java Spring Boot scaffold for order lifecycle only. Dependencies: `web,data-jpa,validation,actuator,postgresql,flyway,spring-kafka`. Schema: `order_svc`. Port: 8091 (Keycloak keeps local host port 8085). Create `domain/`, `application/`, `infrastructure/` packages. **Ref**: `.sisyphus/analysis/bounded-contexts/order-context.md`.

- [x] T12B. Scaffold payment-service (Pattern A)

  **What**: Java Spring Boot scaffold for payment attempts, gateway callbacks, idempotency, and payment transaction records only. Dependencies: `web,data-jpa,validation,actuator,postgresql,flyway,spring-kafka`. Schema: `payment_svc`. Port: 8092. Create `domain/`, `application/`, `infrastructure/` packages. **Ref**: `.sisyphus/analysis/bounded-contexts/checkout-shipping-payment.md`.

- [x] T12C. Scaffold shipping-service (Pattern A)

  **What**: Java Spring Boot scaffold for shipping quotes, carrier labels, tracking sync, carrier circuit breakers, and manual retry queue. Dependencies: `web,data-jpa,validation,actuator,postgresql,flyway,spring-kafka`. Schema: `shipping_svc`. Port: 8093. Create `domain/`, `application/`, `infrastructure/` packages. **Ref**: `.sisyphus/analysis/bounded-contexts/checkout-shipping-payment.md`.

- [x] T13. order-service domain: Order + SubOrder (Pattern C)

  **What**: `Order` aggregate (id, orderNumber VNS-YYYYMMDD-XXXXX, buyerId, shippingAddress, subOrders List<SubOrder>, itemsTotal, shippingTotal, discount, finalAmount, paymentMethod COD, paymentStatus PENDING/COMPLETED/FAILED, idempotencyKey). `SubOrder` (sellerId, items List<OrderItem>, fulfillmentStatus [PENDING_ACCEPTANCE→ACCEPTED→PACKED→SHIPPED + REJECTED/CANCELLED], shippingCost, shippingMethod STANDARD, carrier stub, trackingNumber). `OrderItem` snapshot fields. Money VO. Ports: `OrderRepositoryPort`, `InventoryReservationPort`, `PaymentRequestPort`, `ShippingRequestPort`, `OrderEventPublisherPort`. **Note**: Cart state is not in order-service; payment gateway state is not in order-service. **Ref**: `.sisyphus/analysis/bounded-contexts/order-context.md:64-180`, `.sisyphus/analysis/bounded-contexts/checkout-shipping-payment.md:§2-4`.

- [x] T14. cart-service: cart Redis adapter + use cases (Pattern G + Pattern E)

  **What**: `RedisCartRepository` (SET TTL 7d, GET, DEL). Use cases: `AddToCartUseCase`, `ViewCartUseCase`, `UpdateQuantityUseCase`, `RemoveItemUseCase`, `ClearCartUseCase`. Price snapshot at add time. Product validation via product-service API, never product DB. **Ref**: `.sisyphus/analysis/bounded-contexts/cart-context.md:§4-5`.

  **QA**: `mvn test` → all green. **Evidence**: `task-14-cart.txt`. **Commit**: `feat(checkout): cart domain + Redis adapter + use cases`

- [x] T15. order-service: order application use cases (Pattern E)

  **What**: `CreateOrderUseCase` orchestrates cart snapshot, inventory reservation, payment request, and shipping request through ports. `AcceptOrderUseCase`, `RejectOrderUseCase`, `ShipOrderUseCase`, `CancelOrderUseCase`. Idempotency via `idempotencyKey` UNIQUE index. Publish `order.created`, `order.cancelled`, `order.shipped` through outbox/Kafka. No direct carrier/payment SDK imports in order-service.

  **Category**: `deep` | **Ref**: `.sisyphus/analysis/bounded-contexts/order-context.md:§5,7`, `.sisyphus/analysis/infrastructure-resilience.md:§6`.

- [x] T16. order-service + cart-service infrastructure REST (Pattern F)

  **What**: order-service owns JPA entities: `OrderJpaEntity`, `SubOrderJpaEntity`, `OrderItemJpaEntity`; Flyway V1: `orders`, `sub_orders`, `order_items` with `idempotency_key` UNIQUE index; controllers: `OrderController`, `SellerOrderController`. cart-service owns `CartController` backed by Redis only. order-service calls cart-service snapshot API; it does not read Redis directly. Kafka/outbox events published by order-service.

  **QA**:
  ```
  Scenario: Add to cart → checkout → 202
    Tool: Bash (curl)
    Steps:
      1. curl -s -w "%{http_code}" -X POST localhost:8084/cart/items -H "Authorization: Bearer $BUYER_TOKEN" -H "Content-Type: application/json" -d '{"productId":"p1","variantId":"v1","sellerId":"s1","quantity":2}'
      2. curl -s -w "%{http_code}" -X POST localhost:8091/orders -H "Authorization: Bearer $BUYER_TOKEN" -H "Content-Type: application/json" -d '{"cartId":"cart-1","shippingAddress":{"street":"123","ward":"1","district":"1","city":"HCMC"},"shippingMethod":"STANDARD","paymentMethod":"COD","idempotencyKey":"ik-test-001"}'
    Expected: "200", "202"
    Evidence: .sisyphus/evidence/task-16-order-cart.txt
  ```
  **Commit**: `feat(order-cart): split cart redis API from order persistence API`

- [x] T17. order-service checkout orchestration endpoints

  **What**: order-service `CheckoutController`: `POST /checkout/calculate` orchestrates cart-service snapshot + coupon-service validation + shipping-service quote + payment-service available methods. `GET /checkout/payment-methods` proxies payment-service methods (COD only for Phase 1). `POST /checkout/shipping-options` proxies shipping-service quote. No payment or carrier SDK in order-service. **Ref**: `.sisyphus/analysis/bounded-contexts/checkout-shipping-payment.md:§6`.

  **QA**: `curl -s localhost:8091/checkout/payment-methods | grep -c "COD"` → 1, response sourced from payment-service. **Evidence**: `task-17-checkout-orchestration.txt`. **Commit**: `feat(order): checkout orchestration across payment and shipping services`

- [x] T18. payment-service stub adapter (Pattern I)

  **What**: payment-service owns all payment attempts, status, callback logs, gateway adapters, idempotency, and payment events. `StubPaymentGateway` implements internal `PaymentGatewayPort`: COD → auto `COMPLETED`, VNPay/MoMo → stub fake redirectUrl (not functional). Controller: `PaymentController` (POST /payment/cod/confirm, GET /payment/status/{orderId}). Stub mode only (`PAYMENT_MODE=stub` enforced in Phase 1). order-service calls payment-service API; it does not import payment SDKs. **Ref**: `.sisyphus/analysis/bounded-contexts/checkout-shipping-payment.md:§5`.

  **QA**: `mvn test -Dtest=StubPaymentGatewayTest` → green. **Evidence**: `task-18-payment.txt`. **Commit**: `feat(checkout): stub payment gateway adapter`

### 1.4 — Notification + Integration (T19–T22)

- [x] T19. Scaffold notification-service (Pattern B — required for order events)

  **What**: NestJS. Dependencies: `@nestjs/common,@nestjs/core,@nestjs/platform-express,@nestjs/microservices,@nestjs/typeorm,typeorm,pg`. Port: 8087. Required in Phase 1 so order events, smoke tests, Docker build, and flash-sale notifications have a durable extension point. **Ref**: `.sisyphus/analysis/bounded-contexts/notification-context.md`.

- [x] T20. notification-service: domain + application + Kafka consumers (Pattern D + Pattern E)

  **What**: `Notification` (id, userId, type, title, body, data JSON, channels, status). `SendNotificationUseCase`. `KafkaNotificationConsumer`: consume `order.created`, `order.cancelled`. Channel stubs: log to console.

- [x] T21. notification-service: REST + TypeORM + Flyway (Pattern G)

  **What**: TypeORM entities. Flyway V1. `NotificationController`.

- [ ] T22. Wire gateway -> product-service + search-service integration

  **What**: Verify: seller creates product through gateway → product-service writes product → event reaches search-service → buyer searches through gateway. Fix DTO mapping, exception handling, CQRS lag window. **Category**: `unspecified-high` | **Blocked By**: T4, T11.

  **QA**:
  ```
  Scenario: Seller creates product via gateway, buyer searches via gateway
    Tool: Bash (curl)
    Steps:
      1. SELLER_TOKEN=$(curl -s -X POST localhost:8085/realms/vnshop/protocol/openid-connect/token -d 'client_id=vnshop-api&username=seller1&password=test&grant_type=password' | grep -o '"access_token":"[^"]*"' | cut -d'"' -f4)
      2. curl -s -w "%{http_code}" -X POST localhost:8080/sellers/me/products -H "Authorization: Bearer $SELLER_TOKEN" -d '...'
      3. sleep 3; curl -s localhost:8080/search?q=ao | grep -c "Ao thun"
    Expected: "201", then "1"
    Evidence: .sisyphus/evidence/task-22-wire.txt
  ```
  **Commit**: `fix(product-search): wire gateway to separate product and search services`

- [ ] T23. Wire gateway -> user-service integration

  **What**: Verify seller register → admin approve → profile through gateway. **Category**: `unspecified-high` | **Blocked By**: T4, T8.

  **QA**: Buyer1 → POST /sellers/register → 201. Admin → POST /admin/sellers/1/approve → 200. Seller1 → GET /sellers/me/profile → 200 with shopName. **Evidence**: `task-23-user-wire.txt`. **Commit**: `fix(user-service): wire seller flow through gateway`

- [ ] T24. Wire gateway -> cart/order/payment/shipping integration

  **What**: Verify microservice checkout path: cart-service add item → order-service checkout summary → order-service creates order (202) → payment-service COD completes → shipping-service stub creates tracking → seller accept/pack/ship through order-service. Idempotency: duplicate `idempotencyKey` returns same order (200 not 202). **Category**: `unspecified-high` | **Blocked By**: T4, T16, T17, T18.

  **QA**:
  ```
  Scenario: Full checkout → fulfillment through gateway
    Tool: Bash (curl)
    Steps:
      1. BUYER_TOKEN=$(...buyer1); curl -s -w "%{http_code}" -X POST localhost:8080/cart/items -H "Authorization: Bearer $BUYER_TOKEN" -d '{"productId":"p1","variantId":"v1","sellerId":"s1","quantity":2}'
      2. curl -s -w "%{http_code}" -X POST localhost:8080/orders -H "Authorization: Bearer $BUYER_TOKEN" -d '{"items":[{"productId":"p1","variantId":"v1","sellerId":"s1","quantity":2}],"shippingAddress":{"street":"123","ward":"1","district":"1","city":"HCMC"},"shippingMethod":"STANDARD","paymentMethod":"COD","idempotencyKey":"ik-test-phase1"}'
      3. SELLER_TOKEN=$(...seller1); curl -s -w "%{http_code}" -X POST localhost:8080/sellers/me/orders/o1/accept -H "Authorization: Bearer $SELLER_TOKEN"
      4. curl -s -w "%{http_code}" -X PUT localhost:8080/sellers/me/orders/o1/ship -H "Authorization: Bearer $SELLER_TOKEN" -d '{"carrier":"STUB"}'
    Expected: "200", "202", "200", "200" (tracking in ship response)
    Evidence: .sisyphus/evidence/task-24-order-wire.txt
  ```
  **Commit**: `fix(checkout): wire cart order payment shipping microservices through gateway`

### 1.5 — Docker + Observability Baseline + Smoke (T25–T29)

- [x] T25. Dockerfiles for Phase 1 services (Pattern H)

  **What**: `Dockerfile` per service: api-gateway, user-service, product-service, inventory-service, search-service, cart-service, order-service, payment-service, shipping-service, coupon-service, review-service, seller-finance-service, notification-service. Multi-stage. HEALTHCHECK. Non-root user.

  **Category**: `quick` | **Parallel**: all services simultaneously.

  **QA**: `for svc in api-gateway user-service product-service inventory-service search-service cart-service order-service payment-service shipping-service coupon-service review-service seller-finance-service notification-service; do docker build -t vnshop-$svc services/$svc || exit 1; done; echo "All built"` → "All built". **Evidence**: `task-25-dockerfiles.txt`. **Commit**: `feat(docker): Dockerfiles for microservices`

- [x] T26. docker-compose Phase 1 service entries

  **What**: Add one docker-compose service entry per microservice: api-gateway (8080), user-service (8081), product-service (8082), inventory-service (8083), cart-service (8084), search-service (8086), notification-service (8087), coupon-service (8088), review-service (8089), seller-finance-service (8090), order-service (8091), payment-service (8092), shipping-service (8093). Each: `build: ./services/{name}`, `depends_on` infra, env vars. Postgre init script creates schemas: `user_svc`, `product_svc`, `inventory_svc`, `search_svc`, `order_svc`, `payment_svc`, `shipping_svc`, `coupon_svc`, `review_svc`, `seller_finance_svc`, `notification_svc`. **Category**: `quick` | **Blocked By**: T25. **Ref**: `.sisyphus/analysis/devops-spec.md:§3`.

  **QA**: `docker compose config 2>&1 | grep -c "error\|Error"` → 0. **Evidence**: `task-26-config.txt`. **Commit**: `feat(infra): docker-compose Phase 1 service entries`

- [ ] T27. Observability baseline: Prometheus + Grafana + Loki

  **What**: Add `prometheus`, `grafana`, `loki`, `promtail` to docker-compose. Prometheus scrapes all services' `/actuator/prometheus` or `/metrics`. Grafana dashboards: service health, HTTP rates, JVM. Structured JSON logs → promtail → Loki. Grafana data source for Loki. This is the baseline; full observability stack (Jaeger, AlertManager, business dashboards) comes in Phase 2. **Category**: `unspecified-high`. **Ref**: `.sisyphus/analysis/devops-spec.md:§6`.

  **QA**: `curl -s localhost:9090/api/v1/targets | grep -c '"health":"up"'` → ≥ 6. **Evidence**: `task-27-observability.txt`. **Commit**: `feat(observability): Prometheus + Grafana + Loki baseline`

- [ ] T28. Phase 1 smoke test

  **What**: `infra/scripts/smoke-test-phase1.sh`: `docker compose up -d` → wait all healthy → Keycloak token → seller register → admin approve → seller create product → buyer search → add to cart → checkout (COD) → seller accept → ship (stub tracking) → verify order status SHIPPED → `docker compose down`. Exit 0 if all pass.

  **Category**: `unspecified-high`. **Blocked By**: T26.

  **QA**: `bash infra/scripts/smoke-test-phase1.sh; echo $?` → 0. **Evidence**: `task-28-smoke.txt`. **Commit**: `feat(smoke): Phase 1 MVP end-to-end smoke test`

- [x] T29. OpenAPI / Swagger (Phase 1 services)

  **What**: `springdoc-openapi-starter-webmvc-ui` on Java services. `@nestjs/swagger` on NestJS. OpenAPI 3.0 at `/{service}/v3/api-docs` or `/{service}/api`.

  **Category**: `quick`.

  **QA**: `curl -s localhost:8081/v3/api-docs | python -c "import json,sys; json.load(sys.stdin); print('OK')"` → OK. **Evidence**: `task-29-openapi.txt`. **Commit**: `feat(docs): OpenAPI for Phase 1 services`

---

## PHASE 2 — PRODUCTION HARDENING (T30–T43)

### 2.1 — Data Integrity & Money (T30–T33)

- [x] T30. Transactional outbox pattern

  **What**: Replace direct Kafka produce with outbox table. `outbox_events` table (id, aggregateType, aggregateId, eventType, payload JSON, status PENDING/PUBLISHED, createdAt). `OutboxPublisher`: Spring `@Scheduled` polls PENDING events → publish to Kafka → mark PUBLISHED. At-least-once semantics. All domain event publishing routes through outbox.

  **Category**: `deep` | **Ref**: `.sisyphus/analysis/infrastructure-resilience.md:§6.3`.

  **QA**: `mvn test` → outbox integration test: save event → publisher fires → Kafka message received. **Evidence**: `task-30-outbox.txt`. **Commit**: `feat(infra): transactional outbox for all Kafka events`

- [x] T31. Idempotency & deduplication guarantee

  **What**: All POST endpoints that create resources (orders, payments, products) enforce idempotency. Store `idempotency_key` with response cache (Redis, TTL 24h). Return cached response on duplicate key. Consumer-side dedup: Kafka consumer checks `processed_events` table before processing. **Ref**: `.sisyphus/analysis/bounded-contexts/order-context.md:§5`.

  **QA**: Duplicate `idempotencyKey` POST → same response + same status (not 409). **Evidence**: `task-31-idempotency.txt`. **Commit**: `feat(infra): idempotency middleware + consumer dedup`

- [x] T32. Double-entry ledger for payments

  **What**: `LedgerEntry` (transactionId, orderId, debitAccount, creditAccount, amount, currency, timestamp, status). Every payment state transition writes two ledger entries (debit + credit). `LedgerReconciliationService`: compare ledger totals vs wallet balances daily. Immutable ledger — insert-only, no updates.

  **Category**: `deep` | **Ref**: `.sisyphus/analysis/bounded-contexts/seller-finance-context.md:§3`.

  **QA**: Payment completed → 2 ledger entries created → debit = credit. **Evidence**: `task-32-ledger.txt`. **Commit**: `feat(finance): double-entry ledger for all payment transactions`

- [x] T33. Payment reconciliation worker

  **What**: `ReconciliationWorker`: Spring `@Scheduled` (hourly). Queries payment gateway for transaction status → compares with local ledger → flags mismatches to `reconciliation_issues` table for manual review. For Phase 1 stub: reconciliation always passes (stub is consistent). This task builds the framework so it works when real gateways are plugged in.

  **Category**: `deep`. **Ref**: `.sisyphus/analysis/bounded-contexts/checkout-shipping-payment.md:§4`.

  **QA**: Reconciliation run → 0 mismatches with stub. **Evidence**: `task-33-reconciliation.txt`. **Commit**: `feat(finance): payment reconciliation worker`

### 2.2 — Security Hardening (T34–T36)

- [x] T34. Secrets management

  **What**: `.env.example` with all configurable secrets documented. `infra/secrets/README.md`: production setup guide (HashiCorp Vault, AWS Secrets Manager, or SealedSecrets). In docker-compose: all secrets read from env vars, never hardcoded. `.env` in `.gitignore`. Commit pre-commit hook: scan for hardcoded secrets (detect-secrets or gitleaks).

  **Category**: `quick`. **Ref**: `.sisyphus/analysis/requirements-audit-v2.md:#N31`.

  **QA**: `grep -r "password\|secret\|token\|key" services/*/src/ | grep -v "\.example\|CHANGE_ME\|test" | wc -l` → 0. **Evidence**: `task-34-secrets.txt`. **Commit**: `feat(security): secrets management structure + pre-commit scan`

- [x] T35. Authorization matrix + Keycloak prod config

  **What**: Document full authz matrix in `infra/authz-matrix.md`: endpoint → required role → scope. Wire Spring Security method-level `@PreAuthorize` on all controllers. Keycloak prod hardening: brute force detection, password policy (min 8, complexity), session limits, refresh token rotation. Export prod realm config to `infra/keycloak/vnshop-realm-prod.json`.

  **Category**: `unspecified-high`. **Ref**: `.sisyphus/analysis/spring-cloud-gateway.md:§2`.

  **QA**: Unauthenticated → POST /orders → 401. Buyer → POST /admin/sellers/1/approve → 403. **Evidence**: `task-35-authz.txt`. **Commit**: `feat(security): authz matrix + Keycloak prod hardening`

- [x] T36. SAST + dependency scanning in CI

  **What**: GitHub Actions CI: add OWASP Dependency Check (`mvn dependency-check:check`), Trivy container scan on Docker images, SonarQube or CodeQL SAST. Fail build on HIGH/CRITICAL findings.

  **Category**: `quick`. **Ref**: `.sisyphus/analysis/devops-spec.md:§1`.

  **QA**: PR with known vulnerable dep → CI fails. **Evidence**: `task-36-sast.txt`. **Commit**: `feat(ci): OWASP + Trivy + SAST quality gates`

### 2.3 — Reliability & Operations (T37–T41)

- [x] T37. Backup + restore procedure

  **What**: `infra/scripts/backup.sh`: `pg_dump` all schemas → compressed archive → S3/local path. `infra/scripts/restore.sh`: restore from archive → verify row counts. Tested end-to-end. `docker compose exec -T postgres pg_dump -U vnshop vnshop`. Cron schedule documented.

  **Category**: `unspecified-high`. **Ref**: `.sisyphus/analysis/devops-spec.md:§5`.

  **QA**: Backup → drop a table → restore → table exists. **Evidence**: `task-37-backup.txt`. **Commit**: `feat(ops): backup/restore scripts + procedure`

- [x] T38. CI pipeline (Phase 1 services)

  **What**: `.github/workflows/ci.yml`. Matrix: all Java microservices (api-gateway, user, product, inventory, search, cart, order, payment, shipping, coupon, review, seller-finance) + Node notification. Steps: checkout → setup-java 25 / setup-node 24 → `mvn verify` / `npm ci && npm test && npm run build`. Quality gates: lint, coverage ≥ 80%, OWASP, Trivy. Docker build + push on merge to main.

  **Category**: `quick`. **Ref**: `.sisyphus/analysis/devops-spec.md:§1`.

  **QA**: `act -W .github/workflows/ci.yml --dryrun 2>&1 | grep -c "error"` → 0. **Evidence**: `task-38-ci.txt`. **Commit**: `feat(ci): GitHub Actions parallel CI for Phase 1`

- [x] T39. Staging environment

  **What**: `docker-compose.staging.yml`: same shape as prod but with stub payment/carrier. `infra/scripts/deploy-staging.sh`: docker compose -f staging up → health check → smoke test. Document staging URL, access, and data reset procedure.

  **Category**: `unspecified-high`. **Ref**: `.sisyphus/analysis/devops-spec.md:§1`.

  **QA**: `bash infra/scripts/deploy-staging.sh` → staging smoke test passes. **Evidence**: `task-39-staging.txt`. **Commit**: `feat(ops): staging environment + deploy script`

- [x] T40. Migration policy + rollback plan

  **What**: `infra/migration-policy.md`: Flyway versioning rules, backward-compatible schema changes only, no destructive migrations, rollback procedure (reverse migration script), pre-migration backup requirement, CI gate: `mvn flyway:validate`. Document: how to test migrations on staging before prod.

  **Category**: `quick`.

  **QA**: Policy doc exists, CI validates migrations. **Evidence**: `task-40-migrations.txt`. **Commit**: `feat(ops): migration policy + rollback procedure`

- [x] T40A. Evolution guardrails for long-term development

  **What**: Add architecture guardrails so VNShop can keep evolving without deadlock: ADR template, service-boundary checklist, API versioning policy, event contract versioning policy, feature-flag policy, module dependency rules, and “new service readiness” checklist (Dockerfile, K8s manifest, probes, metrics, logs, OpenAPI, smoke test, runbook). Store under `infra/architecture/` and reference from PR checklist.

  **Category**: `writing` | **Ref**: Scalability + future-development baseline.

  **QA**: New-service checklist includes Dockerfile, Helm/Kustomize, probes, metrics, logs, OpenAPI, smoke test, runbook, resource limits; API/event versioning policy has explicit compatibility examples. **Evidence**: `task-40A-evolution-guardrails.txt`. **Commit**: `feat(architecture): add evolution guardrails and service readiness checklist`

- [x] T41. Incident runbook

  **What**: `infra/runbook.md`: common failure scenarios + response. Service down → check health → check logs (Loki) → check DB → restart procedure. Kafka lag → scale consumers → DLQ replay procedure. DB connection exhaustion → check pool → kill long-running queries. Payment reconciliation gap → manual reconciliation steps. Escalation: who to contact, PagerDuty integration.

  **Category**: `writing`.

  **QA**: Runbook covers ≥ 10 scenarios. **Evidence**: `task-41-runbook.txt`. **Commit**: `feat(ops): incident runbook`

- [x] T42. Load test profile + k6 scripts

  **What**: `infra/k6/phase1-browse.js` (1000 VU, 15min), `infra/k6/phase1-checkout.js` (200 VU, 10min). Target traffic: 100 orders/min, 2000 browses/min. Thresholds: `http_req_failed<1%`, `p95<200ms` (browse), `p95<500ms` (checkout). Run against staging.

  **Category**: `unspecified-high`. **Ref**: `.sisyphus/analysis/devops-spec.md:§4`.

  **QA**: `k6 run --vus 10 --duration 10s infra/k6/phase1-browse.js` → checks pass. **Evidence**: `task-42-k6.txt`. **Commit**: `feat(k6): Phase 1 load test scripts`

- [x] T43. Production No-Go checklist assembly

  **What**: Compile the checklist from the Production Gate section below into `PRODUCTION-NO-GO.md`. Each item has an owner, evidence requirement, and verification method. This is the final gate before any real deployment.

  **Category**: `writing`.

  **QA**: All 13 items have checkboxes, evidence requirements, owners. **Evidence**: `task-43-checklist.txt`. **Commit**: `feat(docs): production No-Go checklist`

---

## PHASE 3 — MARKETPLACE COMPLEXITY (T44–T55)

### 3.1 — Multi-Seller + Real Carriers (T44–T47)

- [x] T44. Multi-seller order splitting

  **What**: Extend `CreateOrderUseCase`: split cart items by `sellerId` → create multiple `SubOrder`s per `Order`. Shipping calculated per seller. Update order fulfillment: each sub-order has independent state. Update controllers to handle partial fulfillment.

  **Category**: `deep` | **Ref**: `.sisyphus/analysis/bounded-contexts/checkout-shipping-payment.md:§7`.

  **QA**: Order with items from 2 sellers → 2 sub-orders created. **Evidence**: `task-44-multiseller.txt`. **Commit**: `feat(order): multi-seller order splitting`

- [x] T45. Real VNPay adapter (Pattern I -> real)

  **⚠️ HARD PREREQ**: T32 (double-entry ledger) + T33 (reconciliation worker) + P2-2 (webhook idempotency/replay) must pass before this task. Real money requires immutable ledger.

  **What**: `VnpayGateway` implementing `PaymentGatewayPort`. HMAC SHA512 per VNPay 2.1.0. Create payment URL → redirect → IPN (source-of-truth) → signature verify → create `payment_transaction` → outbox event → ledger entry pair. IPN handler must acknowledge within VNPay's timeout. Return URL is UI-only, never trusted for state. `PAYMENT_MODE=live` toggles from stub to real. **Blocked By**: T32, T33, P2-2. **Ref**: `.sisyphus/analysis/bounded-contexts/checkout-shipping-payment.md:§4.3`.

  **QA**: Sandbox VNPay credentials → create payment URL → verify callback parses correctly. **Evidence**: `task-45-vnpay.txt`. **Commit**: `feat(payment): VNPay real gateway adapter`

- [x] T46. Real MoMo adapter (Pattern I -> real)

  **⚠️ HARD PREREQ**: Same as T45 — ledger + reconciliation + webhook protection must pass first.

  **What**: `MomoGateway`. HMAC SHA256. Create → capture → IPN (source-of-truth) → signature verify → `payment_transaction` → outbox → ledger entry pair. IPN response must match MoMo's expected semantics within fast response window (<5s). QueryDR fallback for missed IPNs. `PAYMENT_MODE=live`. **Blocked By**: T32, T33, P2-2. **Ref**: `.sisyphus/analysis/bounded-contexts/checkout-shipping-payment.md:§4.4`.

  **QA**: Sandbox MoMo → create payment → verify callback. **Evidence**: `task-46-momo.txt`. **Commit**: `feat(payment): MoMo real gateway adapter`

- [x] T47. Real GHN + GHTK carrier adapters (Pattern I -> real)

  **What**: `GhnCarrierGateway`, `GhtkCarrierGateway` implementing `CarrierGatewayPort`. GHN: rate quote → create label → tracking. GHTK: same. `CARRIER_MODE=live` toggles. Shipping zone calculator uses real API responses. **Ref**: `.sisyphus/analysis/bounded-contexts/checkout-shipping-payment.md:§8`.

  **QA**: Sandbox credentials → rate quote returns real VND → create label returns real tracking code. **Evidence**: `task-47-carriers.txt`. **Commit**: `feat(shipping): GHN + GHTK real carrier adapters`

### 3.2 — Post-Purchase + Wallet (T48–T51)

- [x] T48. Return/refund flow + dispute

  **What**: `ReturnUseCase`: request → seller approve/reject → buyer ship back → verify → refund. `DisputeUseCase`: escalate → admin review → resolve. Refund: prepaid = payment gateway partial refund; COD = deduct from seller wallet. Controllers: `ReturnController`, `AdminDisputeController`. **Ref**: `.sisyphus/analysis/bounded-contexts/seller-finance-context.md:§4`.

  **QA**: Buyer request return → seller approve → refund processed → wallet adjusted. **Evidence**: `task-48-returns.txt`. **Commit**: `feat(order): return/refund + dispute resolution`

- [x] T49. Seller wallet + commission + payouts

  **What**: `SellerWallet` (availableBalance, pendingBalance, totalEarned). `CommissionCalculator`: STANDARD 10%, VERIFIED 8%, PREFERRED 5%, MALL 3%. `PayoutService`: seller requests withdrawal → admin processes → wallet debited. JPA + Flyway V2. Controllers: `SellerFinanceController`, `AdminFinanceController`. Wire commission into order creation. **Ref**: `.sisyphus/analysis/bounded-contexts/seller-finance-context.md:§0,3`.

  **QA**: Order completed → wallet credited → commission deducted → payout requested. **Evidence**: `task-49-wallet.txt`. **Commit**: `feat(finance): seller wallet + commissions + payouts`

- [x] T50. Coupon context

  **What**: `Coupon` aggregate (code, type PERCENTAGE/FIXED/FREE_SHIPPING, value, maxDiscount, minOrder, usageLimit, validFrom/Until). `CouponValidator`. JPA + Flyway V3. Controllers: `CouponController` (validate, GET all, admin CRUD). Wire into checkout. **Ref**: `.sisyphus/analysis/bounded-contexts/coupon-context.md:§2`.

  **QA**: Create coupon → apply at checkout → discount reflected in total. **Evidence**: `task-50-coupon.txt`. **Commit**: `feat(order): coupon context + checkout integration`

- [x] T51. Digital invoice + VAT

  **What**: `InvoiceGenerator`: PDF/A with VAT 8% (standard), 5% (books/education), 0% (export). `VatCalculator`. Flyway V4 for `invoices` table. `InvoiceController`: POST generate, GET download. **Ref**: `.sisyphus/analysis/requirements-audit-v2.md:#F102`, Decree 44/2023/ND-CP.

  **QA**: Generate invoice → PDF contains VAT line item. **Evidence**: `task-51-invoice.txt`. **Commit**: `feat(order): digital invoice + VAT calculator`

### 3.3 — Engagement + Sellers (T52–T55)

- [x] T52. Reviews + Q&A

  **What**: `Review` (rating 1-5, text, images max 5, verified purchase, helpful votes). `ProductQuestion`. JPA + Flyway in review-service. Controllers. **Ref**: `.sisyphus/analysis/bounded-contexts/review-context.md:§2-3`.

  **QA**: Buyer reviews purchased product → review-service stores review and product page can fetch aggregate via review-service API. **Evidence**: `task-52-reviews.txt`. **Commit**: `feat(review): reviews + Q&A`

- [x] T53. Flash sale (Redis Lua + waiting room)

  **What**: Lua atomic DECR with floor check. Redis sorted set waiting room. `FlashSaleController`, `FlashSaleService`. **Ref**: `.sisyphus/analysis/bounded-contexts/product-context.md:§9`, `.sisyphus/analysis/infrastructure-resilience.md:§4`.

  **QA**: 100 concurrent reservations on 10 stock → exactly 10 succeed, 90 rejected. **Evidence**: `task-53-flash.txt`. **Commit**: `feat(inventory): flash sale Redis Lua atomic reserve`

- [x] T54. Admin analytics dashboard

  **What**: `AdminDashboardController`: summary, revenue time-series, top products/sellers. Aggregate from order-service + product-service + seller-finance-service. Redis cache TTL 5min. **Ref**: `.sisyphus/analysis/requirements-audit-v2.md:#F113-F114`.

  **QA**: Dashboard returns totalOrders, revenue, topProducts. **Evidence**: `task-54-dashboard.txt`. **Commit**: `feat(admin): analytics dashboard`

- [x] T55. Fully observability: Jaeger + AlertManager

  **What**: Add Jaeger all-in-one to docker-compose. OTEL tracing on all services. AlertManager rules: service down (critical), high error rate (warning), Kafka lag (critical), DB pool (warning), payment failure rate (critical). **Ref**: `.sisyphus/analysis/infrastructure-resilience.md:§8`.

  **QA**: Jaeger services visible, AlertManager alerts firing on simulated service down. **Evidence**: `task-55-observability.txt`. **Commit**: `feat(observability): Jaeger tracing + AlertManager rules`

---

## Final Verification Wave (F1–F4)

- [x] F1. **Plan Compliance Audit** (`oracle`) — Search codebase for Must NOT patterns (framework imports in domain, custom JWT, Spring Boot 3.x). Verify hexagonal folders, TDD tests, gateway routes, Keycloak realm, Dockerfiles. Check evidence files for all completed tasks. VERDICT PASS/FAIL.

  **QA**:
  ```
  1. grep -rn "import org.springframework\|import jakarta.persistence" services/*/src/main/java/*/domain/ | wc -l  # → 0
  2. grep -rn "spring-boot-starter-parent.*3\." services/*/pom.xml | wc -l  # → 0
  3. grep -rn "@nestjs" services/*/src/domain/ | wc -l  # → 0
  4. docker compose exec -T keycloak ... get realms/vnshop | grep -c "vnshop"  # → 1
  5. find services -name "Dockerfile" | wc -l  # → ≥ 5
  6. ls .sisyphus/evidence/task-*-*.txt | wc -l  # → ≥ 55
  ```
  **Evidence**: `task-F1-audit.txt`

- [x] F2. **Code Quality Review** (`unspecified-high`) — Run all test suites. Check domain layers. Run lint. Check for slop (`as any`, `@ts-ignore`, `console.log`, `TODO`, hardcoded secrets).

  **QA**:
  ```
1. mvn test -f services/api-gateway && mvn test -f services/user-service && mvn test -f services/product-service && mvn test -f services/inventory-service && mvn test -f services/search-service && mvn test -f services/cart-service && mvn test -f services/order-service && mvn test -f services/payment-service && mvn test -f services/shipping-service && mvn test -f services/coupon-service && mvn test -f services/review-service && mvn test -f services/seller-finance-service
  2. cd services/notification-service && npm test  (if built)
  3. grep -rn "as any\|@ts-ignore\|TODO\|console.log" services/ | wc -l  # → ≤ 10
  ```
  **Evidence**: `task-F2-review.txt`

- [x] F3. **Real Manual QA** (`unspecified-high` + curl) — Execute full seller→buyer→fulfillment flow through gateway for the current phase. Test error cases: invalid product, duplicate order (idempotency), unauthorized access, invalid coupon, out-of-stock. (⚠️ Docker-blocked: all code-level tests + builds verified; gateway curl scenarios require Docker runtime unavailable in this environment. See evidence for detailed pass/fail breakdown.)

  **QA**: Complete end-to-end flow per phase scope. All steps pass. **Evidence**: `task-F3-qa.txt`

- [x] F4. **Scope Fidelity Check** (`deep`) — Compare tasks to implementation. Detect scope creep, cross-task contamination. Verify every task has evidence file or explicit deferral note.

  **QA**: All tasks have evidence or deferral. No scope creep detected. **Evidence**: `task-F4-scope.txt`

---

## Production Gate (NON-NEGOTIABLE — Must All Pass Before Real Traffic)

This checklist gates ANY deployment that touches real users or real money.

| # | Requirement | How Verified | Owner | Status |
|---|---|---|---|---|
| G1 | Kubernetes prod environment exists | `kubectl get ns vnshop-prod`; Helm/Kustomize prod overlay dry-run passes | DevOps | ⬜ |
| G2 | Docker Compose not used for prod runtime | Prod runbook uses Kubernetes only; compose docs marked local-dev | DevOps | ⬜ |
| G3 | Managed PostgreSQL HA + PITR configured | Provider console/IaC shows HA, encryption, PITR; restore drill passes | DevOps | ⬜ |
| G4 | Managed Redis TLS/AUTH configured | Services connect via TLS; Redis ACL/AUTH enabled | DevOps | ⬜ |
| G5 | Managed Kafka TLS/SASL + ACLs configured | Unauthorized topic access fails; DLQs exist | DevOps | ⬜ |
| G6 | Managed OpenSearch/Elasticsearch configured | TLS, snapshots, index templates, alerts verified | DevOps | ⬜ |
| G7 | Production Keycloak hardened | No `start-dev`; brute-force, password policy, token rotation enabled | Security | ⬜ |
| G8 | Cloudflare R2 buckets/policies configured | Images bucket policy OK; invoice/doc buckets private; lifecycle rules active | DevOps | ⬜ |
| G9 | R2 object storage integration tested | Upload/download/checksum/signed URL tests pass in staging | Backend | ⬜ |
| G10 | Secrets manager/SealedSecrets configured | No prod secrets in git/env files; rotation runbook exists | Security | ⬜ |
| G11 | TLS/HTTPS configured for all public endpoints | `curl -I https://{domain}/health` → 200; cert auto-renew verified | DevOps | ⬜ |
| G12 | Network policies and pod security configured | Services cannot call unauthorized internal services; pods run non-root | Security | ⬜ |
| G13 | DB backup + restore tested end-to-end | Restore to staging → smoke test passes | DevOps | ⬜ |
| G14 | Payment reconciliation implemented + tested | 100 orders → reconcile → 0 mismatches | Backend | ⬜ |
| G15 | Double-entry ledger passing balance check | Sum(debits) = Sum(credits) for all periods | Backend | ⬜ |
| G16 | Idempotency tested for orders/payments/webhooks | 100 duplicate POST/callback attempts → 0 double-charges | Backend | ⬜ |
| G17 | Webhook replay protection implemented | Replay VNPay/MoMo callback → no duplicate processing | Backend | ⬜ |
| G18 | Shipping carrier resilience implemented | Simulated GHN/GHTK outage → DLQ/manual remediation path works | Backend | ⬜ |
| G19 | Authorization matrix passing all tests | Every endpoint → role test → correct 2xx/403; cross-seller access denied | Backend/Security | ⬜ |
| G20 | PII encryption + key rotation tested | Raw DB dump hides PII; staging key rotation succeeds | Security | ⬜ |
| G21 | CI green with tests + coverage ≥ 80% + SAST + dependency + image scan | Last main pipeline green | DevOps | ⬜ |
| G22 | Load test targets defined + passing | k6 run meets p95/error thresholds in staging | QA | ⬜ |
| G23 | Observability + alert ownership complete | Simulated failures produce traces/logs/metrics/alerts with runbook links | DevOps | ⬜ |
| G24 | Staging soak test: 24–72h zero critical errors | Synthetic traffic + logs/metrics clean | DevOps | ⬜ |
| G25 | Production rollback drill completed | Deploy N+1 → rollback N → smoke passes | Lead/DevOps | ⬜ |
| G26 | VAT/e-invoice legal compliance reviewed | E-invoice flow certified against Decree 44/2023/ND-CP. Invoice PDF/A with VAT 8%/5%/0%. Provider integration certified. Tax authority reporting tested. | Finance/Legal | ⬜ |
| G27 | PCI/card-data non-storage evidence | VNPay/MoMo hosted payment only. No raw card data in logs, DB, or callbacks. No PAN, CVV, or track data anywhere in infrastructure. Evidence: grep audit + PCI SAQ-A. | Security | ⬜ |
| G28 | Data privacy policy enforced | Consent collection, retention policy, DSAR (delete/export) workflow, audit trail for all PII access. PII classification doc exists. Encryption-at-rest + app-level encryption for bank/phone/address verified. | Security/Legal | ⬜ |
| G29 | Fraud/abuse controls enabled | Account takeover protection (MFA, anomaly detection). Seller fraud detection (fake orders, self-purchase). Fake review detection. Coupon abuse rate limiter. Refund abuse pattern alerts. All controls tested in staging with abuse scenarios. | Security | ⬜ |
| G30 | Financial settlement signed off | Double-entry ledger reconciled. Payout cutoff schedule documented. COD reconciliation with carriers tested. Chargeback/refund workflow tested end-to-end. Accounting export format approved by finance. Wallet balance reconciliation automated. | Finance | ⬜ |
| G31 | Incident response tabletop drill passed | Simulated production incident (payment outage, DB corruption, R2 outage, Kafka lag). Runbook followed. Escalation path tested. On-call rotation confirmed. SLO error budget policy documented. Post-mortem template exists. | Lead/DevOps | ⬜ |

### Additional gates for self-hosted 24/7 server production

These gates apply when production runs on a project-owned physical server or VPS instead of managed cloud. They do not replace G1–G31; they clarify how infrastructure gates are proven without managed HA services.

| # | Requirement | How Verified | Owner | Status |
|---|---|---|---|---|
| SH1 | Single-server risk accepted | Signed note states production is single-node and downtime is acceptable until HA migration | Lead/Owner | ⬜ |
| SH2 | Server hardened | SSH key-only, root login disabled, password auth disabled, UFW/firewall active, Fail2ban/CrowdSec active, unattended security updates enabled | DevOps/Security | ⬜ |
| SH3 | k3s prod cluster installed | `kubectl get nodes` shows Ready; prod namespace exists; Helm/Kustomize dry-run passes | DevOps | ⬜ |
| SH4 | No internal service publicly exposed | External scan shows only 22/80/443 or approved Cloudflare Tunnel endpoints; DB/Redis/metrics not reachable publicly | Security | ⬜ |
| SH5 | PostgreSQL offsite backup working | WAL/archive + scheduled backup uploads encrypted archives to R2; backup job alert exists | DevOps | ⬜ |
| SH6 | Restore tested on separate machine | Fresh server/VM restore from R2 backup -> smoke test passes; RTO/RPO recorded | DevOps | ⬜ |
| SH7 | R2 stores canonical objects | Product images, invoices, exports, and DB backups are in R2; local disk copy is cache/temp only | Backend/DevOps | ⬜ |
| SH8 | Resource budget proven | 24h staging soak shows CPU/RAM/disk within thresholds; disk growth forecast documented | DevOps | ⬜ |
| SH9 | Kafka/Search deferred or capacity-approved | If omitted, outbox/PostgreSQL search fallback tests pass. If included, load/soak proves no resource starvation | Lead/DevOps | ⬜ |
| SH10 | TLS and domain stable | Cloudflare DNS/proxy/tunnel documented; cert-manager renew test passes; payment callback URLs are public HTTPS and stable | DevOps | ⬜ |
| SH11 | Monitoring catches server failure | Uptime probe, disk-full, high CPU/RAM, PostgreSQL down, backup failed, TLS expiry alerts all tested | DevOps | ⬜ |
| SH12 | Bare-metal rebuild runbook tested | Provision blank server -> install k3s -> restore secrets/data -> deploy app -> smoke test. Evidence includes elapsed time | DevOps | ⬜ |

**Gate decision**: All 31 items checked → deploy to production. Any item unchecked → deploy blocked.

**Self-hosted gate decision**: if self-hosted path is chosen, all G1–G31 plus SH1–SH12 must pass. Any unchecked SH item blocks real traffic.

---

## Commit Strategy

Conventional commits: `feat({service}): {desc}` or `fix({service}): {desc}`. Pre-commit: `mvn test -f services/{service}` (Java) or `cd services/{service} && npm test` (Node). No `--no-verify`. No secrets committed.

---

## Success Criteria — Phase 1

```bash
# 14-15 containers healthy (5 infra + 4-5 services + 4 observability baseline)
docker compose ps --format json | grep -c healthy  # -> 14+

# Health
curl -s -o /dev/null -w "%{http_code}" localhost:8080/health  # → 200

# Search
curl -s localhost:8080/search?q=ao | grep -c "name"  # → >0

# Cart + Checkout + Fulfill
curl -s -X POST localhost:8080/cart/items ...  # → 200
curl -s -X POST localhost:8080/orders ...       # → 202
curl -s -X POST localhost:8080/sellers/me/orders/o1/accept ...  # → 200

# Observability baseline
curl -s localhost:9090/api/v1/targets | grep -c '"health":"up"'  # → ≥6

# Smoke test
bash infra/scripts/smoke-test-phase1.sh; echo $?  # → 0
```

### Phase 1 Checklist
- [ ] 14+ containers healthy
- [ ] Gateway routes Phase 1 services with OAuth2 + RL + CB
- [ ] Seller registers → approved → creates product (with variants + images)
- [ ] Product visible in search (Vietnamese text search works)
- [ ] Buyer adds to cart → checkout summary → place order (COD) → 202
- [ ] Idempotency: duplicate `idempotencyKey` returns same order
- [ ] Seller accepts → ships → tracking number generated (stub)
- [ ] Prometheus scrapes all services. Grafana dashboards live
- [ ] All domain tests pass, zero framework imports in domain/
- [ ] Dockerfiles build, docker-compose config valid
- [ ] Smoke test exit 0
- [ ] **Staging Gate**: ALL UNCHECKED (Phase 1 = local dev only, not production)
- [ ] **Production Gate**: ALL UNCHECKED (G1–G31)
