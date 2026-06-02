# Architectural Trade-Offs

Documented decisions with rationale, known downsides, and context.

---

## 1. PayPal-Request-Id for Refund Idempotency

**Decision:** Use PayPal's built-in dedup (PayPal-Request-Id header) instead of a local idempotency store.

**Rationale:** PayPal guarantees that duplicate requests with the same Request-Id return the original response. No need to build local dedup infrastructure.

**Trade-off:** Relies on PayPal's guarantee holding. Added `processed_refund` table in seller-finance as belt-and-braces for the wallet debit side.

---

## 2. Commission Tier as String on Kafka Events

**Decision:** Transmit commission tier as a String (not enum) across service boundaries.

**Rationale:** Enum coupling across services is fragile. String + `valueOf()` with default-to-STANDARD is backward-compatible. Adding a new tier doesn't require redeploying consumers.

**Trade-off:** Typos possible. Mitigated by `CommissionTier.valueOf(str)` + catch → STANDARD fallback.

---

## 3. FX Fields Nullable on Payment Entity

**Decision:** `fxRate`, `originalCurrency`, `originalAmount` are nullable on the Payment domain.

**Rationale:** Only PayPal/Stripe payments involve FX conversion. COD, VietQR, and domestic methods have no FX. Making these required would force meaningless defaults.

**Trade-off:** Frontend must null-check FX fields in payment response. API response includes them only when non-null.

---

## 4. Health Probe via Kafka `partitionsFor()`

**Decision:** Use metadata-only AdminClient call to check Kafka connectivity, no message produced.

**Rationale:** Lightweight check (no disk I/O on broker), fast response time, doesn't pollute topics.

**Trade-off:** Doesn't detect ACL/authorization issues — a service could pass health check but fail to consume. Acceptable for health probe scope.

---

## 5. Outbox Pattern for Refund-Requested Events

**Decision:** order-service uses an outbox table + polling relay for `payment.refund_requested`.

**Rationale:** Money-path events must survive crashes. Outbox guarantees at-least-once delivery with transactional atomicity (same TX as the domain state change).

**Trade-off:** Extra table + polling interval adds latency (up to poll interval). Acceptable for refund path where seconds don't matter.

---

## 6. Direct KafkaTemplate.send for payment.refunded

**Decision:** payment-service publishes `payment.refunded` directly (no outbox).

**Rationale:** Payment-service doesn't have outbox infrastructure. Building it for one event type is disproportionate.

**Trade-off:** At-least-once only (message could be lost if crash between DB commit and Kafka ack). Mitigated by consumer idempotency (`processed_refund` table).

---

## 7. MongoDB for Notifications (vs PostgreSQL)

**Decision:** Migrated notification-service from PostgreSQL to MongoDB.

**Rationale:**
- TTL indexes for automatic 90-day expiry (no cron job)
- `$facet` aggregation for efficient thread pagination
- Schema flexibility for varied notification metadata
- Natural fit for document-per-notification model

**Trade-off:** Another database to operate. No JOIN capability (acceptable — notifications are self-contained). No ACID transactions across collections (mitigated by single-document atomicity).

---

## 8. ROPC for Authentication (vs Authorization Code)

**Decision:** Use Resource Owner Password Credentials grant — SPA sends credentials to backend, backend exchanges with Keycloak.

**Rationale:** Simpler SPA implementation (no redirect flow), custom login UI, no CORS issues with Keycloak login page.

**Trade-off:** ROPC is deprecated in OAuth 2.1. Blocks MFA (can't prompt user mid-flow). Backend handles raw passwords (exposure risk). Identified as MEDIUM security finding — migration to Auth Code + PKCE recommended for production.

---

## 9. Gateway-as-Single-Perimeter (Initial Design)

**Decision:** Initially, only the API gateway enforced auth/roles. Downstream services used `permitAll()` or just `authenticated()`.

**Rationale:** Fast to build, single point of configuration, avoids duplicating JWT validation.

**Trade-off:** Collapse-on-bypass architecture. SSRF, internal network access, or misconfigured routes bypass all security. **Fixed in pt48** — added defense-in-depth role checks at service level.

---

## 10. Redis Without Auth in Development

**Decision:** Redis ran without `requirepass` in local dev.

**Rationale:** Zero-friction `docker compose up` for new developers. No password to configure.

**Trade-off:** Bad security habit that leaks into production config. **Fixed in pt48** — added `--requirepass` with env-var override.

---

## 11. Socket.io Manual Reconnect (vs Built-in)

**Decision:** Disabled socket.io's `reconnection: true` and implemented manual exponential backoff.

**Rationale:** JWT tokens may expire/refresh between reconnects. Built-in reconnect reuses the stale token. Manual reconnect fetches a fresh token on each attempt.

**Trade-off:** More code to maintain. Manual backoff (1s → 30s cap, max 5 attempts) vs automatic infinite retry.

---

## 12. SET NX for Deduplication (vs Check-Then-Set)

**Decision:** Use `SET key 1 EX ttl NX` as a single atomic dedup acquire.

**Rationale:** Check-then-set (`isDuplicate()` → `markProcessed()`) has a TOCTOU window under concurrent Kafka consumers. SET NX is atomic — first caller wins.

**Trade-off:** None significant. SET NX is strictly superior for this use case.

---

## 13. 2-Minute TTL + Heartbeat for Socket Registration

**Decision:** Short Redis TTL (2 min) on socket connections, refreshed every 30s by the gateway.

**Rationale:** 24h TTL means stale entries persist after server crash. Short TTL + heartbeat ensures dead connections are detected within 2 minutes.

**Trade-off:** Heartbeat adds periodic Redis writes. Minimal overhead (one EXPIRE per socket per 30s).

---

## 14. Notification Delivery: Stay at SENT (Don't Mark DELIVERED on Emit)

**Decision:** After socket.io `emit()`, notification stays at SENT status. Only transitions to DELIVERED on explicit client ACK.

**Rationale:** `emit()` is fire-and-forget. Client may never receive it (network drop, tab closed). Marking DELIVERED prematurely creates false state.

**Trade-off:** Notifications in SENT status require the client-side ACK event to complete the lifecycle. Already implemented: FE emits `notification:ack` immediately on receipt.

---

## 15. Preferences Enforcement at Handler Level (vs Use-Case Level)

**Decision:** Check IN_APP preference in the `NotificationCreatedHandler` (the WebSocket delivery gate), not solely in the `SendNotificationUseCase`.

**Rationale:** The use-case may still want to persist the notification (for other channels like email in the future). The handler is the last gate before WebSocket delivery — the right place to check IN_APP specifically.

**Trade-off:** Notification is persisted even if IN_APP is disabled (by design — it may be delivered via email later). The use-case only short-circuits if ALL channels are disabled.
