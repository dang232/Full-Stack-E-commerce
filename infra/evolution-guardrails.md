# Evolution Guardrails

## Reader And Goal

This checklist is for engineers adding or changing VNShop services, APIs, events, infrastructure manifests, or service-owned data. After reading it, they should be able to decide whether a change is safe to merge for long-term multi-service development.

## Scope

VNShop targets 13 production services on Kubernetes with kubeadm. Services communicate through APIs, events, service-owned data stores, and shared infrastructure such as PostgreSQL, Kafka, Redis, Elasticsearch, Cloudflare R2, Prometheus, Loki, and AlertManager.

Use this document before creating a new service, adding an API route, publishing an event, changing a shared contract, or coupling one service to another.

## New Service Readiness Checklist

A new service isn't ready for merge until every item is true.

- [ ] Service has one clear owner and one bounded business purpose.
- [ ] Service has a Dockerfile that builds a production image without local-only assumptions.
- [ ] Service exposes a health endpoint for liveness and dependency status.
- [ ] Service exposes a readiness endpoint that fails until required dependencies are reachable.
- [ ] Kubernetes deployment uses separate liveness and readiness probes.
- [ ] Kubernetes manifests set CPU and memory requests.
- [ ] Kubernetes manifests set CPU and memory limits.
- [ ] Service emits structured logs with request ID, trace ID when present, service name, and severity.
- [ ] Logs flow to Loki in the target namespace.
- [ ] Service exposes metrics for request count, latency, error count, dependency failures, and worker lag when it consumes events.
- [ ] Metrics scrape successfully into Prometheus.
- [ ] Public or service-to-service HTTP contracts have OpenAPI documentation before implementation changes merge.
- [ ] CI runs a smoke test that starts the service and checks the health endpoint.
- [ ] CI blocks merge when the smoke test fails.
- [ ] Runbook has one entry for service down, dependency down, data correctness risk, and rollback.
- [ ] Backups are configured for every service-owned data store.
- [ ] Restore has been tested in staging before production traffic depends on service data.

## API Versioning Policy

APIs are long-lived contracts. Treat every service consumer as external unless ownership proves otherwise.

- [ ] Every public or service-to-service HTTP API has a major version in route or contract metadata, for example `/api/v1/orders`.
- [ ] No breaking change is allowed inside same major version.
- [ ] Additive response fields are allowed when old clients can ignore them.
- [ ] New optional request fields are allowed when old clients remain valid.
- [ ] Required request fields can't be added to existing endpoints inside same major version.
- [ ] Response fields can't be removed, renamed, or changed to incompatible types inside same major version.
- [ ] Status code semantics can't change inside same major version.
- [ ] Error response shape can't change inside same major version.
- [ ] Breaking changes require new major version and a migration plan.
- [ ] Deprecated endpoints need at least 6 months notice before removal.
- [ ] Deprecation notice names replacement endpoint, migration steps, target removal date, and service owner.
- [ ] API Gateway route changes must preserve old routes until deprecation window ends.

Examples:

- Good: Product Service adds optional `brandSlug` to product responses while old clients ignore unknown fields.
- Good: Order Service adds `/api/v2/orders` for a new required checkout field while `/api/v1/orders` stays valid.
- Bad: Payment Service changes `amount` from cents to decimal dollars in `/api/v1/payments`.
- Bad: User Service removes `phoneNumber` from an existing profile response without new major version.

## Event Schema Evolution

Events let services move independently only when schemas stay compatible.

- [ ] Events are additive only after first consumer exists.
- [ ] Never remove event fields that any consumer may read.
- [ ] Never rename fields in place.
- [ ] Never change field meaning in place.
- [ ] New fields must be optional or have safe defaults.
- [ ] Event names must describe facts that already happened, not commands another service must obey.
- [ ] Event payloads include event ID, event type, version, producer service, occurred time, and correlation ID when available.
- [ ] Use JSONB for extensible payload sections that need service-specific metadata.
- [ ] Keep stable top-level fields for routing, filtering, idempotency, and auditing.
- [ ] Consumers must ignore unknown fields.
- [ ] Producers must keep publishing old fields until every known consumer has moved away.
- [ ] Events must be designed so they can move to Avro and a schema registry later.
- [ ] Field names, types, optionality, and defaults must be compatible with Avro schema evolution rules.

Examples:

- Good: Order Service publishes `OrderCreated` with stable order ID, buyer ID, seller IDs, totals, and a JSONB metadata object for checkout context.
- Good: Inventory Service adds optional `reservationExpiresAt` to `InventoryReserved` while old consumers keep using reservation ID and SKU.
- Bad: Payment Service renames `paymentId` to `transactionId` in same event version.
- Bad: Shipping Service removes address fields from `ShipmentCreated` because current code no longer reads them.

## Multi-Service Development Rules

These rules prevent hidden coupling that blocks future releases.

- [ ] No service may read another service's database schema directly.
- [ ] No service may write another service's database schema directly.
- [ ] Cross-service reads go through API contracts, read models, or events.
- [ ] API contracts are OpenAPI-first for HTTP APIs.
- [ ] Event contracts are schema-first for shared events.
- [ ] Shared libraries can't contain business logic owned by one service.
- [ ] Shared DTOs can't replace explicit API or event contracts.
- [ ] Synchronous calls must have timeouts, retries only where safe, and circuit breakers at gateway or client boundary.
- [ ] Long-running workflows must use events or sagas instead of chained synchronous calls.
- [ ] Service startup can't require every optional downstream service to be healthy.
- [ ] Each service owns its schema migrations, rollback notes, and backup checks.
- [ ] Cloudflare R2 object keys must be owned by one service or documented as shared contract.

Examples:

- Good: Cart Service calls Product Service API or consumes product events to display current product data.
- Good: Search Service builds Elasticsearch indexes from Product Service events instead of reading Product Service tables directly.
- Bad: Order Service reads Inventory Service tables to check stock.
- Bad: Seller Finance Service imports Payment Service internal DTOs and treats them as permanent contract.

## Review Gate

A change that affects service boundaries must answer yes to each question before merge.

- [ ] Does owner service remain sole writer of its data?
- [ ] Does each consumer have a documented contract?
- [ ] Can old producer and new consumer run together?
- [ ] Can new producer and old consumer run together?
- [ ] Does rollback keep data and contract compatibility?
- [ ] Do logs and metrics make failure visible without code inspection?
- [ ] Does runbook say what to do when change fails in production?
