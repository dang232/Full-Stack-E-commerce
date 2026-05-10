# Production No-Go Checklist

## Reader And Goal

This checklist is for the release owner deciding whether VNShop can accept production traffic. After reading it, they should be able to run a go or no-go review for a 13-service Kubernetes deployment and block release when any required condition is missing.

## How To Use This Checklist

Mark an item only when evidence exists. If any required item stays unchecked, call no-go and assign an owner before retrying release review.

Evidence can be a CI run, staging command output, dashboard screenshot, runbook link, incident drill notes, load test report, or signed release note.

## Service Set

Production review covers these 13 services:

- [ ] API Gateway
- [ ] User Service
- [ ] Product Service
- [ ] Inventory Service
- [ ] Cart Service
- [ ] Search Service
- [ ] Notification Service
- [ ] Coupon Service
- [ ] Review Service
- [ ] Seller Finance Service
- [ ] Order Service
- [ ] Payment Service
- [ ] Shipping Service

## Release Identity

- [ ] Release version, git commit, container image tags, and Kubernetes namespace are recorded.
- [ ] Release owner is named.
- [ ] Incident commander for release window is named.
- [ ] Rollback owner is named.
- [ ] On-call engineer has acknowledged release window.

## Data Safety

- [ ] PostgreSQL backups are enabled for all service-owned schemas.
- [ ] PostgreSQL restore has been tested in staging from latest backup artifact.
- [ ] Redis data loss impact is documented and accepted, or Redis persistence backup is enabled where needed.
- [ ] Elasticsearch indexes can be rebuilt from source events or service APIs.
- [ ] Kafka topic retention is long enough for replay needed by Search, Notification, Order, Payment, Shipping, and Seller Finance flows.
- [ ] Cloudflare R2 buckets have lifecycle policy, access policy, and restore procedure documented.
- [ ] Migration plan confirms every production schema change is backward compatible with current app version.
- [ ] Reverse migration or rollback data procedure exists for every schema change.

## Deployment Safety

- [ ] Deployment uses rolling update or equivalent zero-downtime strategy.
- [ ] At least 2 replicas run for every externally reachable service.
- [ ] Readiness probes prevent traffic before service dependencies are ready.
- [ ] Liveness probes restart stuck pods without masking dependency outages.
- [ ] PodDisruptionBudgets are set for all 13 services.
- [ ] Resource requests and limits are set for all 13 services.
- [ ] Horizontal scaling target or fixed replica count is documented for peak traffic.
- [ ] Rollback command has been tested in staging.
- [ ] Rollback drill completed during this release cycle.
- [ ] Database migrations can be deployed before app rollout without breaking old pods.

## Secrets And Transport Security

- [ ] Secrets are delivered through SealedSecrets, not plain Kubernetes Secret manifests in source control.
- [ ] SealedSecrets controller is healthy in cluster.
- [ ] Required secrets exist for Keycloak, databases, Kafka, R2, payment provider, email, SMS, and push notification integrations.
- [ ] TLS is configured through cert-manager.
- [ ] Certificates are issued, valid, and not near expiry.
- [ ] Ingress rejects plain HTTP or redirects it to HTTPS.
- [ ] Service-to-service credentials are scoped to needed permissions only.

## Network Controls

- [ ] Default deny NetworkPolicy exists for application namespace.
- [ ] API Gateway can reach only required downstream services.
- [ ] Services can reach only their required databases, Kafka, Redis, Elasticsearch, R2 endpoints, or external providers.
- [ ] PostgreSQL isn't reachable from services that don't own database access.
- [ ] Admin endpoints aren't exposed through public ingress.
- [ ] Kubernetes dashboard, metrics endpoints, and broker ports aren't public.

## Observability

- [ ] Logs from all 13 services arrive in Loki with service name, namespace, pod, severity, and trace or request ID when present.
- [ ] Prometheus scrapes metrics from all 13 services.
- [ ] Metrics include request rate, error rate, latency, pod restarts, JVM or Node runtime health, and dependency failures.
- [ ] Kafka consumer lag dashboards exist for event-consuming services.
- [ ] Database connection pool dashboards exist for database-backed services.
- [ ] AlertManager routes production alerts to on-call channel.
- [ ] Alerts exist for service down, high 5xx rate, high latency, pod restart loop, database saturation, Kafka lag, certificate expiry, disk pressure, and backup failure.
- [ ] Alert noise review confirms release won't page on known benign startup signals.

## CI And Artifact Integrity

- [ ] CI is passing on main for backend, frontend, infrastructure manifests, and migration checks.
- [ ] Images come from approved registry and match commit under release review.
- [ ] Image vulnerability scan has no unaccepted critical findings.
- [ ] Kubernetes manifests render successfully for target environment.
- [ ] Smoke tests run in CI for changed services.
- [ ] OpenAPI docs are generated or validated for changed HTTP APIs.
- [ ] Event contract checks pass for changed producers and consumers.

## Performance And Soak

- [ ] Load test covers browse, search, cart, checkout, payment callback, order status, review, coupon, and notification flows.
- [ ] Load test result stays within agreed latency and error thresholds.
- [ ] Load test includes realistic seller and buyer concurrency.
- [ ] Staging soak ran for at least 24 hours without degradation.
- [ ] During soak, error rate stayed within threshold.
- [ ] During soak, p95 latency stayed within threshold.
- [ ] During soak, memory, CPU, connection pools, and Kafka lag stayed stable.
- [ ] No untriaged severity 1 or severity 2 issues remain from soak.

## Operational Readiness

- [ ] Incident runbook is accessible to on-call engineers.
- [ ] Service owners know where to find runbooks for their service.
- [ ] PagerDuty on-call rotation is assigned and current.
- [ ] Escalation path is documented for application, database, infrastructure, and payment incidents.
- [ ] Release notes include user impact, operator impact, migrations, rollback notes, and known risks.
- [ ] Support team has customer-facing notes for expected behavior changes.
- [ ] Feature flags or rollout controls are documented for risky user-facing changes.

## Health Endpoint Gate

All 13 services must be healthy in staging before production deploy and healthy in production after rollout.

- [ ] API Gateway health endpoint returns healthy.
- [ ] User Service health endpoint returns healthy.
- [ ] Product Service health endpoint returns healthy.
- [ ] Inventory Service health endpoint returns healthy.
- [ ] Cart Service health endpoint returns healthy.
- [ ] Search Service health endpoint returns healthy.
- [ ] Notification Service health endpoint returns healthy.
- [ ] Coupon Service health endpoint returns healthy.
- [ ] Review Service health endpoint returns healthy.
- [ ] Seller Finance Service health endpoint returns healthy.
- [ ] Order Service health endpoint returns healthy.
- [ ] Payment Service health endpoint returns healthy.
- [ ] Shipping Service health endpoint returns healthy.

## Final Go Or No-Go

- [ ] Every required checklist item is checked with evidence.
- [ ] Unchecked items have explicit risk acceptance from release owner and incident commander.
- [ ] No open blocker affects customer checkout, payment, order creation, inventory correctness, or data recovery.
- [ ] Release owner declares go in release channel.
- [ ] Incident commander declares monitoring window start time.

If any final gate item can't be checked, call no-go. Don't deploy until release owner records new evidence or accepted risk.
