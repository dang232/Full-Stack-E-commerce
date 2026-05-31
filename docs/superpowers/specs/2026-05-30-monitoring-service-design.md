# Monitoring Service & Dashboard Design

**Date:** 2026-05-30  
**Status:** Approved  

## Overview

A full-observability monitoring service (NestJS) that health-checks all 16 microservices, discovers API endpoints from the gateway, fetches OpenAPI schemas, stores historical metrics in TimescaleDB, and serves a simple static HTML dashboard with real-time alerts.

## Architecture

```
Browser (localhost:8096) ←WebSocket→ Monitoring Service (NestJS :8096)
                                            ↓
                            ┌───────────────┼───────────────┐
                            ↓               ↓               ↓
                    Gateway Routes    Service Health    OpenAPI Specs
                    (parse config)    (poll /health)    (fetch /v3/api-docs)
                                            ↓
                                      TimescaleDB (:5433)
                                    (metrics history)
```

## Components

### 1. Monitoring Service (`/services/monitoring-service-v2`)

NestJS application on port 8096. Serves both the REST/WebSocket API and the static HTML dashboard.

#### Modules

| Module | Responsibility |
|--------|---------------|
| `auth` | JWT validation via Keycloak, admin-role guard on all routes |
| `discovery` | Parse gateway RouteConfig, fetch OpenAPI specs from each service, build unified endpoint registry |
| `health` | Cron-based polling (every 10s), check service health + downstream deps (Postgres, Redis, Kafka, ES) |
| `metrics` | TimescaleDB repository, hypertables, retention policy (30-day auto-drop) |
| `alerts` | State machine: healthy → degraded → down → recovering. Push via WebSocket |
| `gateway` | WebSocket gateway for real-time dashboard updates |
| `playground` | Proxy test requests to target services |

#### Security — Keycloak JWT + Admin Role

**Backend (NestJS):**
- All REST endpoints and WebSocket connections require a valid JWT from Keycloak
- JWT validated using Keycloak's JWKS endpoint (`/realms/{realm}/protocol/openid-connect/certs`)
- Global `AuthGuard` extracts and validates the token from `Authorization: Bearer <token>` header
- `RolesGuard` checks that the token contains `realm_roles: ['admin']` — non-admin users get 403
- WebSocket handshake validates JWT from `auth.token` in the connection query/headers
- Health polling (internal cron) is not gated — it runs server-side without auth

**Dashboard (static HTML):**
- On page load, check for token in `sessionStorage`
- If no token, redirect to Keycloak login page (PKCE flow, same as the main SPA)
- On callback, store access token in `sessionStorage`, attach to all fetch/WebSocket calls
- Token refresh: silent refresh via hidden iframe before expiry
- Logout button: clear token + redirect to Keycloak logout endpoint

**Config (env vars):**
```
KEYCLOAK_URL=http://keycloak:8085
KEYCLOAK_REALM=vnshop
KEYCLOAK_CLIENT_ID=monitoring-dashboard
KEYCLOAK_ADMIN_ROLE=admin
```

**Keycloak setup required:**
- Register a new public client `monitoring-dashboard` in the `vnshop` realm
- Set valid redirect URIs to `http://localhost:8096/*`
- No client secret needed (public client with PKCE)

#### REST Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/monitoring/services` | All services with current status, response time, uptime % |
| GET | `/monitoring/services/:id/history` | Time-series metrics (query params: period=1h/24h/7d) |
| GET | `/monitoring/services/:id/dependencies` | Dependency tree with status |
| GET | `/monitoring/endpoints` | All discovered endpoints grouped by service |
| GET | `/monitoring/endpoints/:id/schema` | OpenAPI schema for a specific endpoint |
| GET | `/monitoring/alerts` | Active + recent alerts (last 24h) |
| GET | `/monitoring/alerts/history` | Alert history (last 7d) |
| POST | `/monitoring/endpoints/:id/test` | Proxy a test request to the target service |

#### WebSocket Events (Socket.io)

| Event | Direction | Payload |
|-------|-----------|---------|
| `service:status` | server→client | `{ serviceId, status, responseTime, timestamp }` |
| `service:alert` | server→client | `{ serviceId, type: 'down'|'degraded'|'recovered', message, timestamp }` |
| `metrics:update` | server→client | `{ serviceId, metrics: { p50, p95, p99, uptime } }` |

#### Health Check Strategy

- **Basic check:** HTTP GET to service health endpoint (Spring: `/actuator/health`, NestJS: `/health`)
- **Deep check:** Parse health response for downstream deps (db, redis, kafka, elasticsearch)
- **Failure detection:** 3 consecutive failures → mark as DOWN, trigger alert
- **Degraded detection:** response time > 2s OR any dependency unhealthy → DEGRADED
- **Recovery:** 3 consecutive successes after DOWN → RECOVERED, trigger recovery alert

#### Service Registry — Dynamic Discovery

No static config. The monitoring service discovers all services dynamically from the API Gateway's Actuator endpoint:

**Source:** `GET http://api-gateway:8080/actuator/gateway/routes`

**Discovery flow:**
1. On startup + every 5 minutes, fetch the gateway's route list
2. Parse each route: extract `route_id`, path predicates, target URI (e.g. `http://product-service:8082`)
3. Deduplicate by target URI (multiple routes can point to the same service)
4. Derive display name from hostname: `product-service` → `Product Service`
5. For health path: try `/actuator/health` first, fall back to `/health` (auto-detects Spring vs NestJS)

**Benefits:**
- Zero maintenance — add a new service to the gateway, monitoring picks it up automatically
- Single source of truth — gateway RouteConfig is the authority on what services exist
- Route-to-service mapping comes free — we know which paths route to which service

**Gateway Actuator response shape (per route):**
```json
{
  "route_id": "products",
  "predicates": [{ "name": "Path", "args": { "_genkey_0": "/products/**" } }],
  "filters": [...],
  "uri": "http://product-service:8082",
  "order": 0
}
```

**Derived service object:**
```typescript
interface DiscoveredService {
  id: string;           // derived from URI hostname: "product-service"
  name: string;         // humanized: "Product Service"
  url: string;          // "http://product-service:8082"
  healthPath: string;   // auto-detected: "/actuator/health" or "/health"
  routes: string[];     // ["/products/**", "/categories/**", "/reviews/**"]
}
```

**Fallback:** If the gateway is unreachable during discovery, use the last known service list (cached in memory). On first boot with no cache, retry every 10s until gateway responds.

### 2. Dashboard (`/services/monitoring-service-v2/public/`)

Static HTML/CSS/JS served by NestJS at root (`http://localhost:8096`). No build step.

#### Pages/Views

**Health Dashboard (main view):**
- Grid of service cards: name, status indicator (green/yellow/red dot), response time, uptime %
- Click card → expands detail panel: dependency tree, response time chart (last 24h), alert history
- Top alert banner: slides in on new alerts, auto-dismisses on recovery
- Auto-refresh via WebSocket — no manual polling

**API Playground (second tab):**
- Left sidebar: endpoints grouped by service (collapsible)
- Main panel: method badge, path, request body editor (JSON textarea pre-filled from schema), query param inputs
- "Send" button → shows response: status code, headers, body (pretty-printed JSON), timing
- Session-local request history (localStorage) for re-running previous calls

#### Tech

- Vanilla JS + Socket.io client (CDN)
- CSS: minimal custom styles, CSS variables for theming (dark mode default — it's a dev tool)
- Charts: lightweight inline SVG sparklines for response time (no charting library — keep it simple)
- No framework, no bundler, no npm

### 3. Infrastructure

#### TimescaleDB

New container in `docker-compose.yml`:

```yaml
timescaledb:
  image: timescale/timescaledb:latest-pg16
  ports:
    - "5433:5432"
  environment:
    POSTGRES_DB: monitoring
    POSTGRES_USER: monitoring
    POSTGRES_PASSWORD: monitoring
  volumes:
    - timescaledb_data:/var/lib/postgresql/data
```

#### Schema

```sql
CREATE TABLE health_metrics (
  time        TIMESTAMPTZ NOT NULL,
  service_id  TEXT NOT NULL,
  status      TEXT NOT NULL,  -- 'up', 'down', 'degraded'
  response_ms INTEGER,
  details     JSONB         -- dependency statuses, error messages
);

SELECT create_hypertable('health_metrics', 'time');

-- Retention: auto-drop after 30 days
SELECT add_retention_policy('health_metrics', INTERVAL '30 days');

-- Continuous aggregate for hourly rollups
CREATE MATERIALIZED VIEW health_hourly
WITH (timescaledb.continuous) AS
SELECT
  time_bucket('1 hour', time) AS bucket,
  service_id,
  avg(response_ms) AS avg_ms,
  percentile_cont(0.95) WITHIN GROUP (ORDER BY response_ms) AS p95_ms,
  count(*) FILTER (WHERE status = 'up') * 100.0 / count(*) AS uptime_pct
FROM health_metrics
GROUP BY bucket, service_id;

CREATE TABLE alerts (
  id          SERIAL PRIMARY KEY,
  service_id  TEXT NOT NULL,
  type        TEXT NOT NULL,  -- 'down', 'degraded', 'recovered'
  message     TEXT,
  created_at  TIMESTAMPTZ DEFAULT NOW(),
  resolved_at TIMESTAMPTZ
);
```

#### Gateway Route

Add to `RouteConfig.java`:

```
/monitoring/** → monitoring-service-v2:8096
```

#### Gateway Actuator Prerequisite

The gateway currently exposes only `health,info`. Update `application.yml` to include the `gateway` endpoint:

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,gateway
```

This enables `GET /actuator/gateway/routes` which the monitoring service uses for dynamic discovery.

### 4. Route Discovery & Schema Fetching

**Dynamic service discovery (from gateway Actuator):**
- On startup + every 5 minutes, call `GET http://api-gateway:8080/actuator/gateway/routes`
- Parse response: extract route IDs, path predicates, target URIs
- Deduplicate by target URI → build service list with all associated routes
- Auto-detect health path: try `/actuator/health`, fall back to `/health`
- Cache last-known list in memory for resilience if gateway is temporarily down

**OpenAPI schema fetching:**
- For each discovered service, try `GET http://{service}:{port}/v3/api-docs` (Spring)
- Fall back to `GET http://{service}:{port}/api-json` (NestJS Swagger)
- Caches schemas in memory, refreshes every 5 minutes
- If a service doesn't expose OpenAPI, endpoints still appear but without schema info
- Merges path predicates from gateway with schema details to build full endpoint registry

**Playground proxy:**
- `POST /monitoring/endpoints/:id/test` accepts `{ method, path, headers, body, queryParams }`
- Forwards request to target service via the gateway (preserves auth flow)
- Returns full response: status, headers, body, timing

## Non-Goals

- No external alerting (Discord, email, PagerDuty) — in-app only
- No distributed tracing UI (use Jaeger directly for that)
- No log aggregation — this is health + API testing only
- No production deployment — dev/staging tool only

## File Structure

```
services/monitoring-service-v2/
├── public/                    # Static dashboard files
│   ├── index.html
│   ├── css/
│   │   └── styles.css
│   ├── js/
│   │   ├── app.js            # Main entry, router, WebSocket
│   │   ├── auth.js           # Keycloak PKCE login, token storage, refresh
│   │   ├── health.js         # Health dashboard view
│   │   ├── playground.js     # API playground view
│   │   └── charts.js         # SVG sparkline helpers
│   └── assets/
├── src/
│   ├── main.ts
│   ├── app.module.ts
│   ├── auth/
│   │   ├── auth.module.ts
│   │   ├── auth.guard.ts              # JWT validation guard (global)
│   │   ├── roles.guard.ts             # Admin role check
│   │   ├── roles.decorator.ts         # @Roles('admin') decorator
│   │   └── jwt.strategy.ts            # Keycloak JWKS validation strategy
│   ├── discovery/
│   │   ├── discovery.module.ts
│   │   ├── discovery.service.ts       # Gateway Actuator polling + schema fetching
│   │   ├── discovery.controller.ts
│   │   ├── gateway-client.ts          # HTTP client for /actuator/gateway/routes
│   │   └── openapi-fetcher.ts         # Fetches /v3/api-docs or /api-json per service
│   ├── health/
│   │   ├── health.module.ts
│   │   ├── health.service.ts          # Polling scheduler
│   │   ├── health.controller.ts
│   │   └── health-checker.ts          # HTTP health check logic
│   ├── metrics/
│   │   ├── metrics.module.ts
│   │   ├── metrics.service.ts         # TimescaleDB read/write
│   │   └── entities/
│   │       ├── health-metric.entity.ts
│   │       └── alert.entity.ts
│   ├── alerts/
│   │   ├── alerts.module.ts
│   │   ├── alerts.service.ts          # State machine + alert creation
│   │   └── alerts.controller.ts
│   ├── gateway/
│   │   ├── monitoring.gateway.ts      # Socket.io WebSocket gateway
│   │   └── gateway.module.ts
│   ├── playground/
│   │   ├── playground.module.ts
│   │   ├── playground.controller.ts   # Proxy test requests
│   │   └── playground.service.ts
│   └── config/
│       ├── app.config.ts              # Gateway URL, polling intervals, thresholds
│       └── database.config.ts         # TimescaleDB connection
├── package.json
├── tsconfig.json
├── nest-cli.json
└── .env.example
```
