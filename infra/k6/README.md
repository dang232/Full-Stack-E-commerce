# k6 Performance Tests

## Prerequisites
- Install k6: `brew install grafana/k6/k6` (macOS) or `choco install k6` (Windows)
- Full stack running: `docker compose --profile apps up -d`
- Auth token: obtain from Keycloak and export as AUTH_TOKEN

## Run scenarios

```bash
# Flash sale spike test (1000 concurrent users)
k6 run infra/k6/scenarios/flash-sale-load.js

# Checkout steady-state (50 orders/sec for 2 min)
k6 run --env AUTH_TOKEN=<token> infra/k6/scenarios/checkout-flow.js

# Search autocomplete (50 concurrent, 1 min)
k6 run infra/k6/scenarios/search-autocomplete.js

# Payment webhook flood (200 req/sec)
k6 run infra/k6/scenarios/payment-callback.js
```

## Thresholds
- p95 latency < 500ms
- p99 latency < 2000ms
- Error rate < 1%
- Autocomplete p95 < 200ms

## CI Integration
Run as nightly job via `workflow_dispatch` — results stored for trend analysis.
