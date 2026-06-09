# Session Handover — 2026-06-09

## What Was Done

### 1. Centralized Configuration Service

**Problem:** Hardcoded constants scattered across services (`CURRENCY = "VND"`, `PAYMENT_METHOD = "TM/CK"`, `INVOICE_TEMPLATE_CODE = "1"`, `FREE_SHIPPING_THRESHOLD_VND = 500_000L`, static payment method lists). No single source of truth for business configuration.

**Solution:** Expanded the existing NestJS `configuration-service` (port 8097) into a centralized config server for both FE and BE.

**New Endpoints:**
- `GET /api/config` — frontend config (existing, unchanged)
- `GET /api/config/services/:serviceName` — per-service backend config
- `GET /api/config/services` — all service configs (admin/debug)
- `GET /api/config/global` — shared global config (currency, timezone, locale)
- `POST /api/config/reload` — hot-reload config from YAML without restart

**Files Created/Modified:**
- `services/configuration-service/config/services.yml` — centralized business config for all services
- `services/configuration-service/src/configuration/configuration.service.ts` — added YAML loading + service config methods
- `services/configuration-service/src/configuration/configuration.controller.ts` — added new endpoints
- `services/configuration-service/package.json` — added `js-yaml` dependency

### 2. Java Config Client (all services fetch from config-service on startup)

**New files (same pattern, different packages):**
- `services/invoice-service/src/main/java/.../infrastructure/config/ConfigServiceClient.java`
- `services/payment-service/src/main/java/.../infrastructure/config/ConfigServiceClient.java`
- `services/shipping-service/src/main/java/.../infrastructure/config/ConfigServiceClient.java`
- `services/order-service/src/main/java/.../infrastructure/config/ConfigServiceClient.java`

**Behavior:**
- On startup, calls `GET http://configuration-service:8097/api/config/services/{spring.application.name}`
- Flattens JSON response into Spring property sources
- Falls back to local `application.yml` defaults if config-service unreachable
- Configurable via `config-service.url`, `config-service.enabled`, `config-service.timeout-ms`

### 3. Hardcoded Constants → @Value Configurable

| Service | File | Constants Made Configurable |
|---------|------|-----------------------------|
| invoice-service | `InvoiceXmlGenerator.java` | `invoice.currency`, `invoice.template-code`, `invoice.payment-method` |
| payment-service | `LedgerService.java` | `payment.ledger.currency`, `payment.ledger.buyer-account`, `payment.ledger.clearing-account` |
| shipping-service | `ShippingRatesController.java` | `shipping.free-threshold-vnd` |
| order-service | `CheckoutController.java` | `checkout.cod-enabled`, `checkout.vnpay-enabled`, `checkout.momo-enabled` |

### 4. Docker Infrastructure Fixes

**Kafka:**
- Switched from `SASL_SSL` to `SASL_PLAINTEXT` for local dev (SSL cert trust issues)
- Generated valid SSL certs (for future use): `infra/kafka/certs/`
- Added `svc-invoice` user to JAAS config + entrypoint sed command
- Set `KAFKA_ALLOW_EVERYONE_IF_NO_ACL_FOUND: "true"` for dev
- Added `KAFKA_SUPER_USERS: "User:admin;User:ANONYMOUS"` for controller registration

**Kafka Topics:**
- Added missing topics to `infra/scripts/init-kafka-topics.sh`:
  - `order.confirmed`, `order.confirmed.retry`, `order.confirmed.DLT`
  - `order.delivered`, `notification.events`
  - `product.approved`, `product.rejected`, `review.replied`
  - `return.requested`, `payout.completed`, `user.registered`, `user.password-reset`
- Added ACLs for `svc-invoice` and notification topics
- Fixed Windows CRLF → LF line endings in the script

**Node.js Kafka SASL:**
- `services/notification-service/src/main.ts` — added SASL auth config (reads `KAFKA_SASL_USERNAME`/`KAFKA_SASL_PASSWORD`)
- `services/messaging-service/src/main.ts` — same
- `docker-compose.yml` — added `KAFKA_SASL_USERNAME`/`KAFKA_SASL_PASSWORD` env vars for notification + messaging services

**Loki:**
- Fixed `infra/loki/loki-config.yml` — added `delete_request_store: filesystem` (required when retention is enabled)

**Alertmanager:**
- Fixed `infra/alertmanager/alertmanager.yml` — replaced missing Slack webhook URLs with dev-null webhook receivers

**Port Conflicts:**
- `postgres-cart`: moved from 5441 → 5442 (port conflict with stale Docker process)
- `invoice-service`: moved from 8094 → 8098 (conflicted with recommendations-service)
- Updated Dockerfile EXPOSE + HEALTHCHECK to match

**ObjectMapper / Jackson:**
- `services/product-service/pom.xml` — replaced `spring-boot-starter-webmvc` with `spring-boot-starter-web` (webmvc doesn't include Jackson in Boot 3.4)
- `services/shipping-service/pom.xml` — same fix
- `services/product-service/.../UserServiceHttpClientConfig.java` — added `@ConditionalOnMissingBean ObjectMapper` bean
- `services/shipping-service/.../JacksonConfig.java` — created explicit ObjectMapper bean

**Health Check:**
- Removed `kafka` from readiness health groups in `application.yml` for invoice-service, order-service, payment-service (Kafka health contributor not registered when using basic spring-kafka without Actuator kafka health auto-config)

**JAXB (invoice-service):**
- Added `jakarta.xml.bind-api` + `jaxb-runtime` dependencies to `pom.xml`
- Added missing `import java.io.IOException`
- Fixed constructor signature: `throws SAXException, IOException`

**Secrets:**
- Added `KAFKA_SVC_INVOICE_PASSWORD` and `KAFKA_INVOICE_PASSWORD` to `secrets.env.local`

**docker-compose.yml:**
- Added `depends_on: configuration-service: condition: service_healthy` to 10 Java services
- Added `CONFIG_FILE_PATH` env var and config volume mount to configuration-service
- Changed all `KAFKA_SECURITY_PROTOCOL: SASL_SSL` → `SASL_PLAINTEXT` (8 services)

### 5. Final Stack Status

**Healthy (30+ services):** api-gateway, frontend, keycloak, order-service, payment-service, shipping-service, product-service, invoice-service, cart-service, inventory-service, notification-service, messaging-service, coupon-service, user-service, search-service, recommendations-service, seller-finance-service, configuration-service, kafka, redis, all 11 postgres instances, mongo, minio, grafana, prometheus, loki, alertmanager, jaeger, unleash, timescaledb

**Expected exits:** init-kafka (0), minio-bootstrap (0)

**Non-critical failures:**
- `monitoring` — TypeORM entity mismatch (`service_id` column missing in `HealthMetric` entity)
- `promtail` — JMES expression config syntax error in promtail-config.yml
- `elasticsearch` — unhealthy (memory constraints on dev machine)

## Architecture Diagram

```
┌─────────────┐     GET /api/config          ┌──────────────────────────┐
│  Frontend   │ ──────────────────────────►   │                          │
└─────────────┘                               │  configuration-service   │
                                              │  (NestJS, port 8097)     │
┌─────────────┐     GET /api/config/services/ │                          │
│ Java/Node   │ ──────────────────────────►   │  config/services.yml     │
│ services    │      {service-name}           │  (single source of truth)│
└─────────────┘                               └──────────────────────────┘
```

## How to Change Business Config

1. Edit `services/configuration-service/config/services.yml`
2. Call `POST http://localhost:8097/api/config/reload` (or restart config-service)
3. Restart affected Java services (they fetch on startup)

## Known Issues / Next Steps

- [ ] Monitoring-service TypeORM entity needs `service_id` column in `HealthMetric`
- [ ] Promtail JMES expression syntax in `infra/promtail/promtail-config.yml`
- [ ] Consider adding config change webhook/event so Java services can hot-reload without restart
- [ ] Move remaining env var configs (carrier URLs, commission tiers) into `services.yml`
- [ ] Add config versioning / audit trail
