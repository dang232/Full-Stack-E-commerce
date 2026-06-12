# 03 — Race Conditions & Data Consistency

> Concurrency bugs that cause data corruption, lost money, or inconsistent state.
> These are hardest to reproduce but cause the worst damage at scale.

---

## RACE-01: Saga Compensation Bypasses Outbox — Messages Lost on Crash

**Services:** order-service → payment-service, inventory-service  
**File:** `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/event/saga/KafkaSagaCompensationPublisher.java`

**What's wrong:**  
When the shipping step fails after payment succeeds, compensation events (`inventory.release-requested`, `payment.refund.requested`) are published directly via `KafkaTemplate` — NOT through the transactional outbox.

**Scenario:**  
1. Order saga: inventory reserved ✓, payment charged ✓, shipping FAILS
2. Catch block calls `sagaOrchestrator.compensate()`
3. DB transaction commits COMPENSATING state
4. Kafka publish fires... but Kafka is temporarily down, or app crashes between step 3 and 4

**Result:** Buyer charged + inventory held permanently. No refund ever fires.

**Fix:**  
Route compensation through the same outbox table:
```java
// Instead of direct KafkaTemplate.send():
outboxRepository.save(new OutboxEvent(
    "payment.refund.requested",
    new RefundRequestedPayload(orderId, sagaId)
));
// The existing OutboxPublisher relay picks it up
```

---

## RACE-02: Payment Charged but Persistence Fails — Orphaned Charge

**Service:** payment-service  
**File:** `services/payment-service/src/main/java/com/vnshop/paymentservice/application/ProcessPaymentUseCase.java`  
**Line:** 139

**What's wrong:**  
`paymentGatewayPort.processPayment()` (charges the buyer) runs OUTSIDE the DB transaction. If the subsequent `transactionTemplate.execute()` fails (DB down, constraint violation), the charge exists at the gateway but no Payment record, no idempotency key, and no ledger entry exist.

**Scenario:**  
1. Gateway charges buyer's card — $50 deducted
2. App tries to save Payment entity to DB — connection timeout
3. No idempotency key persisted
4. Client retries → NEW idempotency key (different hash) → SECOND $50 charge

**Result:** Buyer charged twice.

**Fix:**  
Persist the idempotency key BEFORE calling the gateway:
```java
// 1. Save PENDING payment + idempotency key in one TX
Payment pending = paymentRepo.save(Payment.pending(orderId, amount, idempotencyKey));

// 2. Call gateway (idempotent on gateway side via their own key)
GatewayResult result = gateway.charge(pending.id(), amount);

// 3. Update payment status
pending.complete(result.transactionRef());
paymentRepo.save(pending);
```

---

## RACE-03: Payment Idempotency TOCTOU — Duplicate Charges

**Service:** payment-service  
**File:** `services/payment-service/src/main/java/com/vnshop/paymentservice/application/ProcessPaymentUseCase.java`  
**Lines:** 119-133

**What's wrong:**  
`findByKey()` and `save()` are not atomic. Two concurrent requests with the same idempotency key can both pass the `findByKey` check (returns empty) before either saves.

**Scenario:**  
1. Request A: `findByKey("key-123")` → empty
2. Request B: `findByKey("key-123")` → empty (A hasn't saved yet)
3. Request A: saves idempotency record + charges gateway
4. Request B: saves idempotency record (or constraint violation) + charges gateway

**Result:** Double charge or 500 error.

**Fix:**  
Use `INSERT ... ON CONFLICT DO NOTHING` or `@Lock(PESSIMISTIC_WRITE)`:
```java
@Transactional
public PaymentResult processIdempotent(String key, ...) {
    // Atomic upsert — returns existing if already processed
    Optional<IdempotencyRecord> existing = idempotencyRepo.insertIfAbsent(key);
    if (existing.isPresent()) return existing.get().cachedResponse();
    // Proceed with charge...
}
```

---

## RACE-04: Order Idempotency Filter — Check-Then-Set Race Window

**Service:** order-service  
**File:** `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/idempotency/IdempotencyFilter.java`  
**Lines:** 58-68

**What's wrong:**  
Redis `GET` then `SET NX` is two operations. Both concurrent requests read null, both try `setIfAbsent`, one wins but the loser doesn't re-check — it proceeds to the handler.

**Scenario:**  
User double-clicks "Place Order." Both requests arrive within ms. Both pass the filter. Both create orders.

**Fix:**  
Single atomic operation:
```java
Boolean acquired = redisTemplate.opsForValue()
    .setIfAbsent(key, PLACEHOLDER, Duration.ofMinutes(5));
if (Boolean.FALSE.equals(acquired)) {
    // Another request owns this key — wait and return cached response
    return waitForCachedResponse(key, response);
}
```

---

## RACE-05: Order Entity — No Optimistic Locking

**Service:** order-service  
**File:** `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/persistence/OrderJpaEntity.java`

**What's wrong:**  
No `@Version` field. Concurrent writes (buyer cancel + saga compensation) both read same state, apply conflicting mutations, last-write-wins.

**Scenario:**  
1. Saga is compensating (payment failed) — tries to set status to PAYMENT_FAILED
2. Buyer simultaneously cancels — tries to set status to CANCELLED
3. Both read current status, both save — whichever commits last wins
4. Inventory released for one but not the other

**Fix:**  
```java
@Version
private Long version;
```
JPA throws `OptimisticLockException` on conflict — catch and retry.

---

## RACE-06: Seller Finance — Payout Race Condition (No Locking)

**Service:** seller-finance-service  
**File:** `services/seller-finance-service/src/main/java/com/vnshop/sellerfinanceservice/application/RequestPayoutUseCase.java`  
**Lines:** 21-27

**What's wrong:**  
No `@Transactional`, no `@Lock`, no optimistic locking on wallet balance. Two concurrent payout requests both read the same balance and both succeed.

**Scenario:**  
Wallet balance: $100. Seller sends two $80 payout requests simultaneously.
1. Request A reads balance: $100, checks $80 <= $100 ✓
2. Request B reads balance: $100, checks $80 <= $100 ✓
3. Both deduct and save → balance becomes -$60

**Fix:**  
```java
@Transactional
public void requestPayout(String sellerId, BigDecimal amount) {
    Wallet wallet = walletRepo.findBySellerIdForUpdate(sellerId); // SELECT ... FOR UPDATE
    if (wallet.balance().compareTo(amount) < 0) {
        throw new InsufficientBalanceException();
    }
    wallet.debit(amount);
    walletRepo.save(wallet);
}
```

---

## RACE-07: Flash Sale — Buyer Can Reserve Same Product Multiple Times

**Service:** inventory-service  
**File:** `services/inventory-service/src/main/resources/redis-lua/flash-reserve.lua`

**What's wrong:**  
The Lua script decrements stock BEFORE adding buyerId to the waiting set. Since `SADD` deduplicates, a duplicate buyer doesn't matter for the set — but stock was already decremented on each call.

**Scenario:**  
Buyer clicks "Reserve" 5 times rapidly. Each request:
1. Lua checks stock: 100 available ✓
2. Lua decrements: stock = 99, 98, 97, 96, 95
3. Lua SADD buyer to waiting set (idempotent — but stock already gone)

**Result:** One buyer consumed 5 units. Other buyers see "sold out" early.

**Fix:**  
Check buyer membership BEFORE decrementing:
```lua
-- Check if buyer already has reservation
if redis.call('SISMEMBER', waitingKey, buyerId) == 1 then
    return {0, "ALREADY_RESERVED"}
end
-- Then decrement stock
local remaining = redis.call('DECRBY', stockKey, quantity)
if remaining < 0 then
    redis.call('INCRBY', stockKey, quantity) -- rollback
    return {0, "INSUFFICIENT_STOCK"}
end
redis.call('SADD', waitingKey, buyerId)
```

---

## RACE-08: Redis Keyspace Notifications Lost — Stock Never Returned

**Service:** inventory-service  
**File:** `services/inventory-service/src/main/java/com/vnshop/inventoryservice/infrastructure/event/ReservationExpiryListener.java`  
**Lines:** 39-47

**What's wrong:**  
Flash sale reservations expire via Redis TTL. `ReservationExpiryListener` relies on Redis keyspace notifications (fire-and-forget, no ACK). If the service is restarting or GC-paused when the notification fires, it's permanently lost.

**Scenario:**  
1. Reservation key expires (TTL=15min)
2. Redis sends keyspace notification
3. Service pod is mid-restart — notification lost
4. Backup: scheduled `releaseExpiredReservations()` checks ZSET, but the hash key is already deleted by TTL → `findById()` returns empty → no-op

**Result:** Stock permanently locked. Flash sale items unavailable forever.

**Fix:**  
Don't rely on keyspace notifications. Use the ZSET expiration index as the PRIMARY mechanism:
```java
@Scheduled(fixedDelay = "PT30S")
public void releaseExpired() {
    Set<String> expired = redis.opsForZSet()
        .rangeByScore(EXPIRY_INDEX, 0, Instant.now().toEpochMilli());
    for (String reservationId : expired) {
        // Increment stock back (don't need the hash — we know the productId from the key)
        String productId = extractProductId(reservationId);
        redis.opsForValue().increment(stockKey(productId), quantity);
        redis.opsForZSet().remove(EXPIRY_INDEX, reservationId);
    }
}
```

---

## RACE-09: Payment Webhook Arrives Before Order Persisted

**Service:** order-service  
**File:** `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/event/payment/PaymentCompletedListener.java`  
**Line:** 77-79

**What's wrong:**  
Order creation saga: reserve inventory → request payment → request shipping → **save order**. Payment provider can process instantly and fire webhook BEFORE step 4 completes.

**Scenario:**  
1. `requestPayment()` calls Stripe
2. Stripe charges immediately, sends webhook
3. `PaymentCompletedListener` fires, calls `orderRepo.findById(orderId)` → NOT FOUND
4. Logs "not found — skipping" and returns
5. Retry topic gives 3 attempts, all fail (order still saving)
6. Event lands in DLT — order never marked as paid

**Result:** Buyer charged but order stuck in PENDING forever.

**Fix:**  
Option A: Save order BEFORE requesting payment (move `orderRepo.save()` earlier in saga).  
Option B: Add exponential retry with longer delays (30s, 60s, 120s) instead of fast retries.  
Option C: Payment listener re-queues to a delay topic if order not found:
```java
if (order.isEmpty()) {
    throw new RetryableException("Order not yet persisted — retry in 5s");
}
```

---

## RACE-10: Cart Read-Modify-Write Not Atomic

**Service:** cart-service  
**File:** `services/cart-service/src/cart/infrastructure/cart-persistence.service.ts`  
**Lines:** 66-95

**What's wrong:**  
Cart operations: read from Redis → modify in memory → write back. No lock between read and write. Concurrent mutations overwrite each other.

**Scenario:**  
1. Tab A: read cart (items: [shoe])
2. Tab B: read cart (items: [shoe])
3. Tab A: add shirt → write [shoe, shirt]
4. Tab B: add hat → write [shoe, hat] ← OVERWRITES Tab A's shirt

**Result:** Shirt silently lost from cart.

**Fix:**  
Use Redis WATCH/MULTI/EXEC for optimistic locking:
```typescript
await redis.watch(cartKey);
const cart = await redis.get(cartKey);
const modified = addItem(JSON.parse(cart), newItem);
const multi = redis.multi();
multi.set(cartKey, JSON.stringify(modified));
const result = await multi.exec();
if (result === null) {
    // Conflict — retry
    return this.addItem(userId, newItem); // recursive retry
}
```

---

## RACE-11: Coupon Validate-Then-Apply Non-Atomic

**Service:** coupon-service  
**File:** `services/coupon-service/src/main/java/com/vnshop/couponservice/application/ApplyCouponUseCase.java`  
**Lines:** 32-42

**What's wrong:**  
Read coupon → check active/expiry/minOrder → then atomically try `consumeUsage()`. Between read and consume, another thread can deactivate the coupon or it can expire.

**Scenario:**  
1. Thread A reads coupon: active=true, expiry=tomorrow
2. Admin deactivates coupon
3. Thread A's `tryConsumeUsage()` succeeds (only checks `currentUses < maxUses`, not active/expiry)

**Result:** Deactivated coupon still applied to an order.

**Fix:**  
Make `tryConsumeUsage()` also check active + expiry atomically:
```sql
UPDATE coupons SET current_uses = current_uses + 1
WHERE id = :id AND active = true AND valid_until > NOW()
AND current_uses < max_uses;
-- Returns 0 rows updated if any condition fails
```
