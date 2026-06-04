# VNShop SLI/SLO Definitions

**Version:** 1.0
**Date:** 2026-06-04
**Status:** Baseline measurement phase (first 2 weeks: alerts in warning-only mode)

---

## Service Level Indicators (SLIs)

| SLI | Metric | Calculation |
|-----|--------|-------------|
| Availability | http_server_requests_seconds_count | 1 - (5xx responses / total responses) over 30d rolling window |
| Latency (reads) | http_server_requests_seconds_bucket | p99 of GET requests per service |
| Latency (writes) | http_server_requests_seconds_bucket | p99 of POST/PUT/DELETE requests per service |
| Throughput | http_server_requests_seconds_count | Requests per second per service (baseline) |
| Kafka Consumer Lag | kafka_consumer_fetch_manager_records_lag_max | Max lag across consumer groups |

---

## Service Level Objectives (SLOs)

| Service | Availability | Latency (reads p99) | Latency (writes p99) | Error Budget (30d) |
|---------|-------------|--------------------|--------------------|-------------------|
| api-gateway | 99.5% | < 500ms | < 2s | 0.5% (216 min/month) |
| user-service | 99.5% | < 500ms | < 2s | 0.5% |
| product-service | 99.5% | < 300ms | < 1s | 0.5% |
| inventory-service | 99.5% | < 200ms | < 1s | 0.5% |
| order-service | 99.5% | < 500ms | < 2s | 0.5% |
| payment-service | 99.5% | < 1s | < 3s | 0.5% |
| shipping-service | 99.5% | < 500ms | < 2s | 0.5% |
| search-service | 99.5% | < 300ms | N/A | 0.5% |

**Note:** These are initial targets. After the 2-week baseline measurement period, targets will be calibrated against observed p99 values. SLO alerts will fire in warning-only mode during baseline.

---

## Error Budget Policy

- **Budget remaining > 50%:** Normal development velocity
- **Budget remaining 25-50%:** Reduce risky deployments, increase testing
- **Budget remaining < 25%:** Freeze non-critical changes, focus on reliability
- **Budget exhausted:** All engineering effort redirected to reliability until budget replenishes

---

## Burn Rate Alerts

| Alert | Burn Rate | Window | Severity |
|-------|-----------|--------|----------|
| Fast burn | 14.4x | 1 hour | critical |
| Slow burn | 3x | 3 days | warning |

Fast burn: consuming 30-day budget in ~2 days → immediate response needed.
Slow burn: consuming 30-day budget in ~10 days → investigate within business hours.

---

## Review Cadence

- **Weekly:** Review SLO dashboard, note trends
- **Monthly:** Error budget report, adjust targets if consistently over/under
- **Quarterly:** Full SLO review with engineering team
