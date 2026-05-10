# Incident Runbook

## Reader and Goal

This runbook is for the on-call engineer responding to VNShop production incidents. After reading it, they should be able to diagnose the common failure mode, take the first safe remediation, and know when to escalate.

## Service Map

Core dependencies:

1. PostgreSQL, `5432`, container name `vnshop-postgres` in Docker Compose.
2. Redis, `6379`, container name `vnshop-redis`.
3. Kafka, `9092`, container name `vnshop-kafka`, KRaft controller listener on `9093` inside the broker.
4. Keycloak, external port `8085`, internal port `8080`, container name `vnshop-keycloak`.
5. Elasticsearch, `9200`, container name `vnshop-elasticsearch`.

Application services:

1. API Gateway, `8080`.
2. User Service, `8081`.
3. Product Service, `8082`.
4. Inventory Service, `8083`.
5. Cart Service, `8084`.
6. Search Service, `8086`.
7. Notification Service, `8087`.
8. Coupon Service, `8088`.
9. Review Service, `8089`.
10. Seller Finance Service, `8090`.
11. Order Service, `8091`.
12. Payment Service, `8092`.
13. Shipping Service, `8093`.

For Spring Boot services, start diagnosis with `/actuator/health` on the service port. For Docker Compose, use `docker compose ps`, `docker compose logs <service>`, and `docker compose restart <service>`. For Kubernetes, use the matching deployment, pod, logs, rollout, and events commands in the target namespace.

## General Incident Flow

1. Declare the incident owner and affected service.
2. Confirm customer impact through gateway errors, service health, logs, and metrics.
3. Check dependency health before restarting an app service.
4. Apply the narrowest safe remediation.
5. Watch health, latency, error rate, and logs for at least one full traffic cycle.
6. Escalate if the service remains unhealthy after one safe restart, if data correctness is at risk, or if rollback needs database action.
7. Record timeline, commands, impact, and follow-up items.

## Service Down

Symptoms:

1. Gateway returns `5xx` or `503` for routes backed by one service.
2. `/actuator/health` fails or times out on the service port.
3. Container or pod is restarting, exited, or not ready.
4. Logs show startup failure, missing configuration, migration failure, or dependency timeout.

Diagnosis:

1. Identify the failing service and port from the service map.
2. Check service health, for example `curl -fsS http://localhost:8083/actuator/health` for Inventory Service.
3. Check runtime state with `docker compose ps` or `kubectl get pods`.
4. Read recent logs for the affected service.
5. Check dependencies listed for that service. Services with database schemas need PostgreSQL. Most catalog, order, payment, shipping, review, coupon, search, user, and inventory flows also depend on Kafka and Redis.
6. If startup fails during Flyway, stop and follow the migration policy before restart loops hide the first error.

Remediation:

1. If a dependency is down, fix the dependency first.
2. If the service crashed after a transient dependency failure, restart only that service.
3. In Docker Compose, run `docker compose restart <service>` after dependency health is green.
4. In Kubernetes, run a rollout restart for the affected deployment only after checking events and logs.
5. If the new version fails repeatedly, roll back the application deployment. Do not roll back database state without the migration rollback plan.

Escalation:

1. Escalate to the service owner if one safe restart does not restore health.
2. Escalate to the database owner if Flyway, schema, or connection errors appear.
3. Escalate to incident command if API Gateway `8080` is down or multiple services fail at once.

## Database Connection Refused

Symptoms:

1. Service logs show `connection refused`, `timeout`, `FATAL`, or pool acquisition failures for PostgreSQL.
2. `/actuator/health` reports database down.
3. Services using schemas such as `order_svc`, `payment_svc`, or `inventory_svc` fail startup or return `5xx`.
4. PostgreSQL health check fails on `5432`.

Diagnosis:

1. Check PostgreSQL health with `pg_isready -h localhost -p 5432 -U vnshop -d vnshop` where available.
2. In Docker Compose, check `vnshop-postgres` health and logs.
3. Confirm connection URL uses host `postgres` inside Compose and port `5432`.
4. Check whether too many connections are open. Look for Hikari pool exhaustion in service logs.
5. Check whether a migration is holding locks or failed during startup.
6. For Kubernetes, check database service endpoint, network policy, secrets, and pod events.

Remediation:

1. If PostgreSQL is unhealthy, restore database service first. Do not restart every app at once.
2. If connection pool is exhausted, reduce incoming traffic at API Gateway `8080` or scale the affected service down briefly while finding the leak.
3. Restart the affected service after PostgreSQL health is green and connection count is stable.
4. If Flyway failed, stop rollout and apply the migration rollback procedure only with the prepared reverse migration and a verified backup.
5. If credentials or network config changed, roll back the config deployment.

Escalation:

1. Escalate to database owner if PostgreSQL is down, disk is full, replication is broken, or locks block writes.
2. Escalate to service owner if one service exhausts its pool while others are healthy.
3. Escalate to incident command before any production data restore or reverse migration.

## Kafka Broker Down

Symptoms:

1. Services log producer or consumer connection failures to `kafka:9092`.
2. Order, inventory, payment, shipping, notification, review, coupon, search, product, or user events stop flowing.
3. Kafka health check using broker API versions fails.
4. Consumer lag rises or outbox tables grow.

Diagnosis:

1. Check broker health on `9092` with `kafka-broker-api-versions --bootstrap-server localhost:9092` where the Kafka CLI is available.
2. In Docker Compose, check `vnshop-kafka` logs and health.
3. Check KRaft controller status. The broker uses process roles `broker,controller` and controller listener `9093` inside the container.
4. Look for storage errors under Kafka data, listener misconfiguration, or cluster ID errors.
5. Check affected services for retries, outbox growth, and duplicate processing warnings.

Remediation:

1. If the broker is unhealthy, restart only Kafka first and wait for broker health to pass.
2. Restart affected producer or consumer services only if they do not reconnect after Kafka is healthy.
3. Keep idempotent consumers enabled. Do not clear topics or consumer offsets during customer traffic without service owner approval.
4. If order or payment events were delayed, verify outbox drain and downstream state before closing the incident.

Escalation:

1. Escalate to platform owner if KRaft controller fails to elect or broker storage errors appear.
2. Escalate to service owners if event lag causes inconsistent order, inventory, payment, or shipping state.
3. Escalate to incident command before deleting Kafka data, changing offsets, or recreating topics.

## Slow Response

Symptoms:

1. API Gateway `8080` latency rises.
2. One downstream service port shows slow `/actuator/health` or request latency.
3. Logs show circuit breaker opens, rate limiter rejects, Hikari pool waits, Redis timeouts, or slow database queries.
4. Users see checkout, cart, inventory, search, or payment delays.

Diagnosis:

1. Find whether slowness starts at API Gateway or one downstream service.
2. Check circuit breaker state and recent failures in the affected Spring service logs and actuator endpoints.
3. Check rate limiter rejections at API Gateway and service logs.
4. Check PostgreSQL query plans for the slow endpoint. Look for missing indexes, full scans, lock waits, and long transactions.
5. Check Redis latency on `6379` if cart, inventory gate, coupon, or gateway rate limiting is involved.
6. Check Elasticsearch health on `9200` if search responses are slow.
7. Compare latency before and after recent deployments or migrations.

Remediation:

1. If circuit breaker is open because a dependency is failing, fix the dependency first.
2. If rate limiter is rejecting legitimate traffic, raise limits only with incident command approval and watch downstream capacity.
3. If database queries are slow, reduce traffic to the endpoint, add a temporary feature flag if available, or roll back the app change that introduced the query.
4. If a migration caused slow queries, stop further rollout and follow the migration rollback plan.
5. Scale the bottleneck service only after confirming the dependency can handle the extra load.

Escalation:

1. Escalate to database owner for lock waits, missing indexes on hot paths, or unsafe query plans.
2. Escalate to platform owner for Redis, Elasticsearch, or network latency.
3. Escalate to service owner if circuit breaker or rate limiter settings need a production change.

## Flash-Sale Overload

Symptoms:

1. Inventory Service `8083` latency or error rate rises during a sale.
2. Redis `6379` CPU, memory, or latency rises.
3. API Gateway `8080` returns `429`, `503`, or high latency for sale endpoints.
4. Order Service `8091` event backlog grows after inventory reservations.
5. Stock appears stuck, oversold risk is reported, or reservation attempts spike.

Diagnosis:

1. Check API Gateway rate limiter metrics and logs for rejected sale traffic.
2. Check Inventory Service health and logs on `8083`.
3. Check Redis health and latency. The inventory sale gate depends on Redis and Lua-style atomic rate or stock checks.
4. Check Kafka `9092` health and consumer lag for inventory and order events.
5. Check PostgreSQL `5432` for lock waits on inventory or order tables.
6. Confirm whether failures are safe rejects or actual reservation errors.

Remediation:

1. Scale Inventory Service first if CPU or request concurrency is the bottleneck and Redis remains healthy.
2. Keep the Redis Lua rate gate active. Do not bypass it to reduce rejections, because it protects stock correctness.
3. If Redis is saturated, reduce sale traffic at API Gateway before adding more Inventory Service replicas.
4. If Kafka lag rises, keep accepting only traffic that can be durably processed and pause non-critical consumers if needed.
5. If oversell risk appears, pause the sale endpoint at API Gateway and ask incident command before manual stock correction.
6. After load drops, reconcile inventory reservations, order state, and payment state before reopening the sale.

Escalation:

1. Escalate to inventory owner for stock correctness or Redis Lua gate changes.
2. Escalate to platform owner if Redis cannot sustain the gate workload.
3. Escalate to incident command before disabling sale traffic, changing rate limits beyond planned thresholds, or correcting stock manually.

## Closeout Checklist

1. Affected service and dependency health are green.
2. Error rate and latency returned to baseline.
3. Backlogs, outbox tables, and consumer lag are draining or empty.
4. Data correctness checks pass for orders, inventory, payments, and shipping when those flows were involved.
5. Incident timeline, commands, customer impact, and follow-up actions are recorded.
