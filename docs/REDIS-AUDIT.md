# Redis Usage Audit — VNShop Platform

Generated: 2026-05-27. Based on codebase state at HEAD (c8b8c4a8).

**Scope:** every service that wires Redis at compile time or connects at runtime, what each one stores, what's load-bearing in the journey/workday suites, and where the config has gaps.

**Methodology:** grep for `redis`/`Redis`/`Lettuce`/`Jedis`/`Redisson`/`ioredis` across `services/**`, `docker-compose.yml`, and `pom.xml`/`package.json`; cross-reference Spring `@ConfigurationProperties` declarations against actual `RedisTemplate` / `@Cacheable` usage; settle each call site against the live journey suite (chapters 1–6) to judge load-bearing status.

---

## Cluster-level config (`docker-compose.yml:172-185`)

```yaml
redis:
  image: redis:8.6-alpine
  command: ["redis-server", "--appendonly", "yes",
            "--maxmemory", "512mb", "--maxmemory-policy", "allkeys-lru"]
  volumes:
    - redis-data:/data
  healthcheck: redis-cli ping
```

Single standalone node. AOF persistence enabled. 512 MB cap with `allkeys-lru` eviction. **No password / ACL.** Fourteen services declare `depends_on: redis`. No Sentinel, no Cluster, no replica.

---

## Service-by-service usage

### 1. order-service — IDEMPOTENCY CACHE (load-bearing)

**Wired here:** `services/order-service/pom.xml:64` (`spring-boot-starter-data-redis`); `application.yml:32` (`spring.data.redis`).

**Used for:** HTTP response cache for `Idempotency-Key`-headed POSTs.
- `IdempotencyFilter.java:26` — `@ControllerAdvice` + `HandlerInterceptor` that intercepts every POST with an `Idempotency-Key` header.
- Hot path: read `idempotency:{key}` (line 59); on miss, write `__PROCESSING__` placeholder with TTL (line 67); on response-write, cache the JSON `{status, body}` blob (line 115); on error, evict (line 103).
- Key prefix: `idempotency:`. Value: JSON-serialized `CachedResponse(int status, String body)`.

**TTL/eviction:** TTL 24 h (`@Value("${vnshop.idempotency.ttl:24h}")`). No explicit eviction policy — relies on cluster `allkeys-lru`.

**Load-bearing?:** Yes. Every checkout POST that the buyer journey suite drives goes through this filter. A Redis outage degrades to "every retry creates a duplicate order" — silently wrong rather than visibly broken.

**Open questions:**
- The catch-and-swallow blocks on line 69 and 116 silence Redis-down errors so the request continues without idempotency. Acceptable failure mode but not observable — no metric, no log other than the `RuntimeException` being swallowed.

---

### 2. inventory-service — FLASH SALE RESERVATIONS (load-bearing for flash sales)

**Wired here:** `pom.xml:49`; `application.yml:17`.

**Used for:** atomic stock reservation + waiting room for flash sales, via two Lua scripts.
- `RedisLuaFlashSaleGateway.java` — `reserve()` (line 34) runs `flash-reserve.lua` against `flash:stock:{productId}` + `flash:waiting:{productId}`; `save()` (line 48) writes a hash with 15-min TTL keyed `flash:reservation:{uuid}`; `findById()` (line 63), `release()` (line 88) runs `flash-release.lua`; `getStock()` (line 101); `releaseExpiredReservations()` (line 106) — `@Scheduled(fixedDelayString = "PT1M")` sweeper that drains the `flash:reservation:expires` ZSET.
- `WaitingRoomService.java` — ZSET `flash:waiting:{productId}` of `buyerId` scored by join time. `join()`, `rank()`, `leave()`.
- `FlashSaleConfig.java` — loads the two Lua scripts from `src/main/resources/redis-lua/` as `RedisScript<Long>` beans.

**TTL/eviction:** reservations 15 min hard TTL. Stock counters and waiting-room ZSETs have no TTL — managed via the Lua-driven release path and the `@Scheduled` sweeper.

**Load-bearing?:** Yes for flash sales. The journey/workday suites do **not** exercise flash sales today — chapter 1 publishes a fixed coupon, not a flash sale. So a Redis outage on inventory-service would not show in the green suite, but production flash-sale traffic would 100% fail.

**Open questions:**
- `flash:reservation:expires` ZSET (the sweeper's index) has no TTL. If the sweeper crashes while a reservation is mid-release, the ZSET score persists and the sweeper will re-attempt next minute — idempotent.
- No visible test that the Lua scripts are atomic under concurrent reservers — depends on Redis single-threaded execution model. Worth a load test, not a code change.

---

### 3. api-gateway — REQUEST RATE LIMITING (load-bearing)

**Wired here:** `pom.xml:31` (`spring-boot-starter-data-redis-reactive`); `application.yml:8`.

**Used for:** Spring Cloud Gateway `RequestRateLimiterGatewayFilter`.
- `RouteConfig.java:71-72` — `RedisRateLimiter(10, 20, 1)` → 10 req/s replenish, 20-burst, 1 token per request. Per-user via `userKeyResolver`.
- Applied to every downstream route (line 83 onward): product, search, inventory, user, cart, order, payment, shipping, notification, recommendations, messaging.
- `ResilienceConfig.java` (also imports Redis types — likely the bean wiring for `userKeyResolver`).

**TTL/eviction:** Rate-limit token buckets; Redis-driven TTLs are managed internally by Spring's `RedisRateLimiter` Lua script. No app-level config.

**Load-bearing?:** Yes — every authenticated request through the gateway hits Redis. A Redis outage degrades to "rate-limiter open-circuits and lets traffic through." Spring's default behavior is fail-open, which is correct for an outage but invisible without a metric.

**Open questions:**
- `userKeyResolver` not visible in the snippet read; if it falls back to a constant key on missing JWT, anonymous traffic shares one bucket. Worth confirming.
- 10 req/s + 20 burst is plausible for SPA-as-user but tight for multi-tab usage. No per-route override; every route uses the same limit.

---

### 4. cart-service — PRIMARY CART STORE (load-bearing)

**Wired here:** `services/cart-service/package.json:30` (`ioredis ^5.10.1`); `cart.module.ts:3,14-31`.

**Used for:** the cart **is** Redis. There's no Postgres mirror.
- `CartRedisRepository.ts:27` — single key per user (`cart:{userId}`), JSON-serialized `PersistedCart`.
- `save(cart, ttlSeconds)` uses `SETEX` (line 57) — every save resets the TTL.
- `delete()` (line 64) — clear cart on checkout completion.

**TTL/eviction:** TTL passed in by caller (`save(cart, ttlSeconds)`). Caller location not yet read in this audit — value is unknown but the caller signature suggests a use-case-driven sliding TTL.

**Load-bearing?:** Yes — chapter 2 of the journey suite (`02-buyer-orders`) adds to cart, applies a coupon, places a COD order. Every interaction is a Redis round-trip. A Redis outage breaks cart entirely (no fallback).

**Open questions:**
- Single Redis instance is a SPOF for the cart. If `allkeys-lru` evicts an active cart under memory pressure, the buyer loses their selections silently. Worth a metric on cart-key-eviction count.
- TTL value passed in by use cases — search needed: who calls `save()` and with what TTL? (out of scope for this audit; the journey suite verifies the round-trip works at all.)

---

### 5. coupon-service — DECLARED BUT UNUSED

**Wired here:** `pom.xml:45`; `application.yml:15-17`.

**Used for:** **nothing in code.** Grep across `services/coupon-service/src/main/java` for `RedisTemplate`, `@Cacheable`, `@CacheEvict`, `RedisConnection` returns zero matches. The dependency is declared, the connection config is set, but no class wires a `RedisTemplate` or annotates a method with caching.

**TTL/eviction:** N/A.

**Load-bearing?:** **No — vestigial.** The coupon-service starter dependency forces a `RedisAutoConfiguration` and creates a `LettuceConnectionFactory` at boot, which holds a lazy connection but does no work. A Redis outage at boot would log connection-refused noise; an outage post-boot is invisible.

**Open questions:**
- Either delete the dep + the application.yml block, or wire actual caching for hot read paths (coupon-by-code lookup is a candidate). Cheapest action: delete.
- Note: coupon-service was migrated into order-service per `api-gateway/application.yml:102` (`# DEPRECATED - standalone coupon/review/seller-finance routes migrated to order-service`). The standalone service may be in the process of decommissioning — worth checking if the whole service is going away before fixing its Redis wiring.

---

### 6. product-service — DECLARED BUT UNUSED

**Wired here:** `pom.xml:47`; `application.yml:15`.

**Used for:** **nothing in code.** Grep across `services/product-service/src/main/java` for `Redis`, `@Cache`, `@EnableCaching` returns zero matches.

**TTL/eviction:** N/A.

**Load-bearing?:** **No — vestigial.** Same shape as coupon-service: the auto-config wires Lettuce at boot, no app code touches it.

**Open questions:**
- Product detail and product-list responses are obvious cache candidates (read-heavy, low-mutation). If caching was the original intent of the dep, it never landed. Either delete or wire.

---

### 7. notification-service / messaging-service — TRANSITIVE ONLY

**Wired here:** `services/notification-service/node_modules/@nestjs/microservices/package.json:36` (peer dep `ioredis: *`); same for messaging-service. **Not in either service's `package.json` directly.**

**Used for:** transitive presence only. NestJS microservices supports a Redis transport but neither service's `app.module.ts` declares a `Transport.REDIS` client.

**Load-bearing?:** No.

**Open questions:** None. Transitive presence is benign.

---

### 8. FE / Node BFF — none direct

The FE has no direct Redis client. cart-service is the only Node service that connects (covered in §4).

---

## Cross-cutting gaps

1. **No password / ACL on the cluster.** `docker-compose.yml:172-185` exposes Redis to every service in the network without auth. Acceptable for a single-host dev compose; a production deployment must layer ACL on top, set `requirepass`, and propagate `REDIS_PASSWORD` through every service's env block.

2. **No persistence assurances despite AOF.** AOF is enabled but `appendfsync` is left at default (`everysec`), which loses up to one second of writes on a hard kill. Idempotency cache and cart store both tolerate this; flash-sale reservations are recreated on reservation-attempt and tolerate it too. No change needed but worth documenting.

3. **`allkeys-lru` evicts by recency, not importance.** Under memory pressure, the cluster will evict an active cart key (5-min idle) before an old idempotency key (last accessed 23 h ago, but TTL still 24 h). Idempotency cache is opportunistic — if it gets evicted, the next retry creates a duplicate order. Cart eviction is silent data loss for the buyer. Mitigation: separate keyspaces by namespace and use `volatile-lru` instead so the un-TTLed flash-sale stock counters survive eviction.

4. **No connection pool config visible.** Spring Data Redis defaults to Lettuce with a single shared connection; under burst traffic on the gateway's `RedisRateLimiter` plus order-service idempotency simultaneously, the shared client can become a bottleneck. No `spring.data.redis.lettuce.pool.*` overrides found in any `application.yml`.

5. **No Redis health observability.** Spring Boot Actuator's `/actuator/health` includes a Redis indicator by default, but no service is configured to surface it (the audit chapter pt37–41 doesn't cover Redis). A Redis outage is invisible until a downstream effect (rate-limit fails open, idempotency cache miss creates duplicate order) shows up.

6. **Two services declare a starter they don't use** (`coupon-service`, `product-service`). Either delete the deps + yml blocks (cheap, recommended) or wire actual caching for product-by-id and coupon-by-code lookups (more value, more work).

---

## Recommended next actions

Ranked by cost-to-value ratio.

1. **Delete unused Redis wiring in coupon-service and product-service.** Remove the starter dep + `spring.data.redis` block. Saves a Lettuce connection per service at boot, removes a misleading dependency declaration. Single-block PR. *Caveat: if coupon-service is being decommissioned (see api-gateway `application.yml:102`), defer until the service is gone entirely.*

2. **Add `REDIS_PASSWORD` env var across the stack and set `requirepass` on the cluster.** Single env-var fan-out; prevents the obvious "any container in the network can read carts and idempotency cache" risk. Pattern matches the kafka env-override sweep from pt41.

3. **Switch eviction policy from `allkeys-lru` to `volatile-lru`.** Single one-character compose edit. Protects un-TTLed keys (flash-sale stock counters, waiting-room ZSETs) from being evicted in favor of more-recently-used TTLed keys. Verify that all keys that need to survive eviction actually have TTLs set or omitted intentionally.

4. **Wire product-by-id and coupon-by-code caching in product-service / order-service** (after the coupon migration lands). High-leverage cache because product detail is the most-read endpoint in the catalog. Multi-block.

5. **Add a Redis-down dashboard panel + alert.** Actuator metric → Prometheus → Grafana. The current "fail-open and swallow exceptions" pattern in `IdempotencyFilter:69,116` is correct behavior but invisible. One block of infra work.

6. **Connection-pool tuning under burst load.** Add `spring.data.redis.lettuce.pool.max-active`/`max-idle` overrides for order-service and api-gateway. Lower priority — premature without a concrete burst-traffic incident, but flagging for the production-no-go checklist.

---

## Patterns in use

The four load-bearing services exercise six distinct Redis patterns. Naming them helps decide whether a future feature belongs in Redis at all and which pattern to copy.

### P1 — Cache-aside with placeholder (order-service idempotency)

**Shape:** read key → on miss, write placeholder under TTL → caller produces result → overwrite placeholder with result. On error, evict.

**Where:** `IdempotencyFilter.java:59-103`. `idempotency:{key}` → `__PROCESSING__` → JSON `{status, body}` (24 h TTL).

**Why this and not plain `SETEX`:** the placeholder makes the read-modify-write window observable to a concurrent retry. Without it, a retry that arrives while the first request is still in-flight would re-execute the whole POST. With it, the retry sees `__PROCESSING__` and (today) falls through — but the contract is one-write-wins via `setIfAbsent` (line 67).

**When to copy this:** any HTTP write endpoint that takes an idempotency token. Don't use it for arbitrary memoization — TTL semantics are tuned for exactly-once delivery, not cache freshness.

### P2 — Atomic counter with Lua (inventory-service flash-reserve / flash-release)

**Shape:** server-side Lua script that bundles a check + decrement + side-effect into one atomic Redis call.

**Where:** `flash-reserve.lua` (4 lines: `GET stock` → `if >= qty` → `DECRBY` + `SADD waiting`). `flash-release.lua` (3 lines: `EXISTS reservation` → `INCRBY stock` + `DEL reservation`).

**Why this and not WATCH/MULTI:** `WATCH`/`MULTI`/`EXEC` retries on contention; under flash-sale traffic that's a livelock risk. Lua executes single-threaded — no contention loop, deterministic latency.

**When to copy this:** any "reserve N units of finite stock and prevent oversell" scenario. The two-key shape (counter + side-effect) is a clean template.

### P3 — Sliding-TTL session store (cart-service)

**Shape:** every write is a `SETEX` that resets the TTL; reads don't touch the TTL. Object lives as long as the user keeps interacting.

**Where:** `CartRedisRepository.ts:57`. Single key per user (`cart:{userId}`), full JSON blob per write, no per-field updates.

**Why this and not Postgres:** read-after-write latency, and the cart is genuinely ephemeral — abandoned carts shouldn't sit in a Postgres table forever. The trade-off is no SQL access, which is fine because the cart is never reported on or joined.

**When to copy this:** session-shaped data with TTL semantics — wishlist drafts, in-progress checkout state, multi-step form drafts. Not for anything that needs server-side filtering or analytics.

### P4 — Distributed token-bucket rate limiter (api-gateway)

**Shape:** Spring Cloud Gateway's bundled `RedisRateLimiter` Lua script: per-user (or per-IP) token bucket, refill rate + burst capacity, atomic decrement-on-request.

**Where:** `RouteConfig.java:71-72` — `RedisRateLimiter(replenishRate=10, burstCapacity=20, requestedTokens=1)`. Applied to every downstream route.

**Why this and not in-memory:** in-memory limiters are per-instance — three gateway pods would let a user burst 3× the configured limit. Redis-coordinated state makes the bucket truly per-user across the fleet.

**When to copy this:** any rate-limit or quota enforcement that must hold across multiple service replicas. Don't reinvent — Spring's bundled Lua script is the right primitive.

### P5 — ZSET-as-priority-queue (inventory-service expiration sweeper)

**Shape:** `ZADD key score member` where the score encodes priority (epoch-millis for "expires at X"). `ZRANGEBYSCORE 0 now` returns everything past due. Sweeper loop drains the head of the queue.

**Where:** `RedisLuaFlashSaleGateway.java:59,108`. `flash:reservation:expires` ZSET, scored by `expiresAt.toEpochMilli()`. `@Scheduled(fixedDelayString = "PT1M")` calls `releaseExpiredReservations()`.

**Why this and not a Postgres timestamp index:** O(log N) insertion + O(log N + M) range scan vs O(log N) on a B-tree, but the Redis path avoids a Postgres connection per sweep and the rest of the reservation data already lives in Redis. Co-locating the index with the data is the win.

**When to copy this:** any "do something at time T" pattern where the data being scheduled is small and ephemeral. Not for cron-style work — Spring `@Scheduled` is simpler. Specifically for time-keyed dequeue.

### P6 — Hash-as-record (inventory-service flash reservation)

**Shape:** one Redis Hash per logical record, fields = columns. Read with `HGETALL` (or per-field `HGET`), write with `HSET` per field, expire the whole hash with `EXPIRE`.

**Where:** `RedisLuaFlashSaleGateway.java:51-58`. `flash:reservation:{uuid}` hash with seven fields. 15-min TTL.

**Why this and not a JSON blob:** per-field reads/writes without parsing the whole record. Useful when fields are accessed independently.

**When to copy this:** if you find yourself reaching for "I want a Postgres row but only for 15 minutes," this is the pattern. Trade-off: no schema validation — the read path has to handle missing fields (line 74 does this defensively).

### Anti-patterns absent (good)

The audit found **no** instances of these failure modes:
- **Pub/Sub for fan-out:** Kafka is the platform's event bus; Redis pub/sub doesn't appear anywhere. Correct call — pub/sub has no persistence.
- **`KEYS *` in hot paths:** every lookup is by exact key. No `SCAN` either, but no need for one.
- **Distributed locks via `SETNX`:** no `RedLock` or hand-rolled locking. The Lua atomic-counter pattern (P2) replaces the most common reason to reach for a lock.
- **Redis Streams:** Kafka covers the streaming use cases. No `XADD`/`XREAD` anywhere.
- **`@Cacheable` magic:** no Spring `@Cacheable` annotations, no `@EnableCaching`. Every Redis interaction is explicit. Easier to reason about, harder to write — the platform chose the right trade-off for a system where cache semantics matter.

---

## Pattern-impact matrix

| Pattern | Service | Outage failure mode | Visible in journey suite? |
|---|---|---|---|
| P1 Cache-aside (idempotency) | order-service | duplicate orders on retry, silent | No — no retry assertion exists |
| P2 Atomic counter (flash sale) | inventory-service | flash sales 100% fail | No — suite doesn't exercise flash sales |
| P3 Sliding-TTL (cart) | cart-service | every cart op fails | Yes — chapter 2 would 500 on Redis-down |
| P4 Token bucket (rate limit) | api-gateway | fail-open, all traffic allowed | No — would silently uncap rates |
| P5 ZSET priority queue (sweeper) | inventory-service | reservations leak, hold stock | No — sweeper is async |
| P6 Hash-as-record (reservation) | inventory-service | reservation lost mid-checkout | No — no flash assertion |

The journey suite catches **only one** of the six patterns failing. Five of six Redis-dependent failure modes are invisible to the green suite — same shape as the kafka-env-override blind spot pt41 found and the search-index gap pt41 surfaced.

---

## Summary

- **6 services declare Redis;** 4 are load-bearing (`order-service` idempotency, `inventory-service` flash sales, `api-gateway` rate-limit, `cart-service` cart store), 2 are vestigial (`coupon-service`, `product-service`).
- **The cluster is a SPOF** — no replication, no Sentinel, no auth. Acceptable for dev; a hard production gate.
- **The cart is Redis.** Eviction risk is the only silent-data-loss vector in the platform's Redis usage. `volatile-lru` + a cart-eviction metric closes that.
- **Cheapest immediate action:** delete the unused deps in coupon-service + product-service. **Highest-impact action:** add `requirepass` + `REDIS_PASSWORD` to the cluster.
- **Audit-chapter coverage gap:** no journey/workday assertion exercises flash-sale Redis paths. Same shape as pt41's search-index gap.
