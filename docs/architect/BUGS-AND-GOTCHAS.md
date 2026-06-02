# Bugs and Gotchas

Hard-learned lessons, runtime surprises, and known issues across all sessions.

---

## Notification Platform Gotchas (pt47)

### #123. Socket.io-client reconnection in React
**Problem:** Don't use socket.io's built-in `reconnection: true` — the JWT token may refresh between reconnects.

**Solution:** Manual backoff with fresh token on each connect attempt. Disable `reconnection` in socket options, implement exponential backoff (1s → 30s cap), fetch fresh token before each attempt.

### #124. Mongoose `type` field name is reserved
**Problem:** Mongoose interprets a field named `type` as a schema type definition, not a data field.

**Solution:** Must use `{ type: String }` explicitly in `@Prop` decorator, e.g. `@Prop({ type: String, enum: NotificationType })`.

### #125. EventEmitterModule.forRoot() placement
**Problem:** `@OnEvent` decorators don't fire if EventEmitterModule is only imported in AppModule.

**Solution:** Import `EventEmitterModule.forRoot()` in the feature module (NotificationModule) where the handlers live, not just AppModule.

### #126. MongoDB aggregation null threadId
**Problem:** Notifications without threads create a null-key group in `$group` stage, producing a phantom "null" thread.

**Solution:** Filter with `{ threadId: { $ne: null } }` in `$match` before the `$group` stage.

### #127. order.created dual-recipient routing
**Problem:** The Kafka event has both buyerId and sellerId. A single factory call with shared recipientId doesn't work.

**Solution:** Consumer must send separate notifications to buyer (ORDER_CREATED) and seller (SELLER_NEW_ORDER) with distinct deepLinks, threadIds, and idempotency keys.

### #128. Redis LRANGE + DEL race condition
**Problem:** Non-atomic read-then-delete loses notifications enqueued between the two commands.

**Solution:** Always use MULTI transaction for drain operations. `LRANGE 0 -1` + `DEL` wrapped in `MULTI`/`EXEC`.

### #129. SET NX for deduplication
**Problem:** Check-then-set (`isDuplicate` → `markProcessed`) has a TOCTOU window under concurrent Kafka consumers.

**Solution:** `SET key 1 EX ttl NX` is atomic — first caller wins, second gets null. Single command, no race.

### #130. Socket.io emit is fire-and-forget
**Problem:** Marking notification DELIVERED immediately after `emit()` is wrong — the client may never receive it.

**Solution:** Keep at SENT until client ACK. FE emits `notification:ack` with IDs on receipt, BE then transitions to DELIVERED.

### #131. Redis socket registration TTL
**Problem:** 24h TTL means stale entries persist after server crash — gateway thinks users are online when they're not.

**Solution:** Short TTL (2 min) with periodic heartbeat refresh (every 30s) from the gateway.

---

## Development Environment Gotchas

### #119. OneDrive reparse-point breaks Playwright
**Problem:** OneDrive cloud-stubs (files shown as "available online") silently break Playwright browser discovery. Files appear to exist but have zero bytes.

**Detection:** Check file mode — `Mode -a---l` indicates a reparse point (cloud stub).

**Solution:** Hydrate via copy/delete/rename, or disable Files On-Demand for the project folder.

### #120. git checkout scope creep
**Problem:** `git checkout -- <dir>` discards ALL unstaged edits under that directory, not just the files you intended.

**Solution:** Always `git status -- <dir>` first to see what would be affected. Or use `git stash` to protect innocent files before checkout.

### #121. Worktree base-ref divergence
**Problem:** Agent worktrees may be based on `origin/main`. If local `main` has diverged ahead, correct-looking agent work can silently delete recent commits when merged.

**Solution:** Before merging agent worktree output, compare `git log --oneline origin/main..HEAD` to see what's at risk. Or set worktree baseRef to `head`.

### #122. Sub-agents bail mid-task
**Problem:** Sub-agents sometimes bail mid-task and pass off narration as completion ("I've completed the implementation"). The actual diff is empty or partial.

**Solution:** Always verify the diff (`git diff --stat`) before trusting a sub-agent's completion report.

---

## Security Bugs Found (pt48)

### S-01. JwtPrincipalUtil.currentSellerId() is a lie
**Problem:** `currentSellerId()` simply returns `jwt.getClaimAsString("sub")` — identical to `currentUserId()`. It does NOT verify the JWT has a SELLER role.

**Impact:** Creates false sense of security. Code reading `currentSellerId()` assumes it's been verified.

**Fix:** Add role check inside the method or at the gateway level (gateway fix applied in pt48).

### S-02. Spring default max page size is 2000
**Problem:** Without explicit `max-page-size` config, clients can request `?size=2000` and force massive result sets.

**Fix:** `spring.data.web.pageable.max-page-size: 50` in application.yml (applied in pt48).

### S-03. catch-all exception handler leaks internals
**Problem:** `@ExceptionHandler(Exception.class)` returning `exception.getMessage()` exposes SQL fragments, class names, file paths.

**Fix:** Return generic message, log full exception server-side (applied in pt48).

### S-04. Keycloak role assignment failure silently swallowed
**Problem:** Empty catch block on `keycloakAdmin.assignBuyerRole(userId)` means users can be created without proper roles with zero visibility.

**Impact:** Silent degradation. Users may lack BUYER role, causing downstream auth failures.

**Fix needed:** Log warning + metric counter. Consider failing registration if role assignment fails.

### S-05. @Email validator more permissive than expected
**Problem:** Jakarta `@Email` allows relatively permissive local parts. Combined with log interpolation, edge-case email formats could aid log injection.

**Mitigation:** Sanitize before logging regardless of validation.

---

## Known Open Bugs

### B-01. order-service startup crash in `apps` profile
**Status:** Fixed in pt46 (missing adapter beans for InventoryReservationPort, PaymentRequestPort, ShippingRequestPort).

### B-02. Mockito self-attach warning on Java 25
**Status:** Open (low priority). Noisy test output. Will break when `--illegal-access=deny` becomes default.

**Workaround:** Add `--add-opens` JVM args or upgrade to Mockito 5.x with inline mock maker.

### B-03. CRLF warnings on Windows
**Status:** Cosmetic. `git config core.autocrlf true` or add `.gitattributes`.

---

## General Patterns That Bite

1. **Kafka consumer offset commit:** If processing fails mid-batch but offset is committed, messages are lost. Always commit after successful processing.

2. **Spring @Transactional on Kafka listeners:** Must use `@Transactional` explicitly — Kafka listeners don't inherit transaction context from the calling code.

3. **UUID.randomUUID() in tests:** Tests that generate UUIDs are non-deterministic. Use fixed IDs in test assertions.

4. **React Query stale time:** Too-short stale time + WebSocket updates = double-fetch. Set appropriate stale times for socket-driven data (5 min+).

5. **Docker Compose `depends_on`:** Only waits for container start, NOT readiness. Use `healthcheck` + `condition: service_healthy` for databases.
