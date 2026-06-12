# 02 — Business Logic Failures

> Revenue loss, incorrect calculations, broken features, state machine violations.
> These don't require hacking — normal usage triggers them.

---

## BIZ-01: Coupon Service — No Per-User Usage Limit

**Service:** coupon-service  
**File:** `services/coupon-service/src/main/java/com/vnshop/couponservice/application/ApplyCouponUseCase.java`  
**Lines:** 32-42

**What's wrong:**  
`tryConsumeUsage()` only checks global `currentUses < maxUses`. The `userId` field in `ApplyCouponRequest` is accepted but never read or stored. No per-user usage table exists.

**What happens in practice:**  
A single buyer applies the same coupon on every order until the global cap is exhausted. A `maxUses: 1000` coupon meant for 1000 different customers can be used 1000 times by one person.

**Fix:**  
1. Add `coupon_usage` table: `(coupon_id, user_id, used_at)` with unique constraint on `(coupon_id, user_id)`
2. In `ApplyCouponUseCase`, check existing usage before consuming:
```java
if (couponUsageRepo.existsByCouponIdAndUserId(coupon.id(), userId)) {
    throw new CouponDomainException("Coupon already used by this user");
}
```

---

## BIZ-02: Coupon Service — Discount Can Exceed Order Amount (Negative Total)

**Service:** coupon-service  
**File:** `services/coupon-service/src/main/java/com/vnshop/couponservice/application/ApplyCouponUseCase.java`  
**Line:** 48

**What's wrong:**  
For FIXED type coupons: `orderAmount.subtract(discount)` with no floor check. A $50 coupon on a $30 order (where `minOrderValue` is $0) produces -$20 final total.

**What happens in practice:**  
Buyer gets paid to place an order. Or the payment gateway rejects negative amounts, causing a 500 error.

**Fix:**  
```java
BigDecimal finalTotal = orderAmount.subtract(discount).max(BigDecimal.ZERO);
```

---

## BIZ-03: Coupon Not Released on Saga Failure

**Service:** order-service + coupon-service  
**File:** `services/order-service/src/main/java/com/vnshop/orderservice/application/CreateOrderUseCase.java`

**What's wrong:**  
When checkout applies a coupon (usage consumed via coupon-service), but the subsequent saga fails (inventory insufficient), compensation publishes inventory release and payment refund — but NO coupon release event. No `OrderCancelled` event is published because the order was never persisted.

**What happens in practice:**  
Coupon usage slot consumed permanently for a non-existent order. Over time, popular coupons become "exhausted" despite fewer actual uses.

**Fix:**  
Add coupon as a saga step with its own compensation:
```java
// In saga compensation:
case COUPON_APPLIED:
    kafkaTemplate.send("coupon.release-requested", new CouponReleaseEvent(couponId, userId));
    break;
```

---

## BIZ-04: Product Service — Verified Purchase Always Returns True

**Service:** product-service  
**File:** `services/product-service/src/main/java/com/vnshop/productservice/application/review/CreateReviewUseCase.java`  
**Lines:** 31-33

**What's wrong:**  
```java
// TODO: check order-service for actual purchase
boolean verifiedPurchase = true;
```
Every review is marked "Verified Purchase" regardless of whether the reviewer bought the product.

**What happens in practice:**  
- Competitors post fake negative reviews with "Verified Purchase" badge
- Sellers self-review with credible verification
- Buyers lose trust in the verification system

**Fix:**  
Call order-service to verify:
```java
boolean verifiedPurchase = orderServiceClient
    .hasBuyerPurchasedProduct(buyerId, productId);
```

---

## BIZ-05: Product Service — No Duplicate Review Prevention

**Service:** product-service  
**File:** `services/product-service/src/main/java/com/vnshop/productservice/application/review/CreateReviewUseCase.java`  
**Lines:** 18-29

**What's wrong:**  
`CreateReviewUseCase` saves a review without checking if this buyer already reviewed this product. No unique constraint on `(product_id, buyer_id)`.

**What happens in practice:**  
A buyer posts 100 five-star reviews on their friend's product, inflating the average rating.

**Fix:**  
```java
if (reviewRepo.existsByProductIdAndBuyerId(productId, buyerId)) {
    throw new DuplicateReviewException("You have already reviewed this product");
}
```
Plus DB migration: `ALTER TABLE reviews ADD CONSTRAINT uq_review_product_buyer UNIQUE (product_id, buyer_id);`

---

## BIZ-06: Product Service — Catalog Returns DELETED Products

**Service:** product-service  
**File:** `services/product-service/src/main/java/com/vnshop/productservice/infrastructure/persistence/ProductJpaSpringDataRepository.java`  
**Lines:** 27-38

**What's wrong:**  
The `findCatalog` query does not filter by product status. Products with status=DELETED are included in catalog results.

**What happens in practice:**  
Buyers see products that sellers have deleted. They add to cart, proceed to checkout, and get a 404/500 error at payment time.

**Fix:**  
```java
@Query("SELECT p FROM ProductJpaEntity p WHERE p.status = 'ACTIVE' ...")
Page<ProductJpaEntity> findCatalog(Pageable pageable);
```

---

## BIZ-07: Order Service — Checkout Ignores Payment Method Choice

**Service:** order-service  
**File:** `services/order-service/src/main/java/com/vnshop/orderservice/infrastructure/web/CheckoutRequest.java`  
**Lines:** 10-17

**What's wrong:**  
`CheckoutRequest` has no `paymentMethod` field. The order creation flow defaults to COD regardless of what the buyer selected on the frontend.

**What happens in practice:**  
Buyer selects "VietQR" or "Stripe" on the checkout page. Order is created as COD. Buyer confused, seller ships without payment.

**Fix:**  
Add `paymentMethod` to `CheckoutRequest` and validate against `PaymentMethod` enum:
```java
@NotNull
private PaymentMethod paymentMethod; // COD, VNPAY, STRIPE, PAYPAL, VIETQR
```

---

## BIZ-08: Order Service — Can Cancel Shipped/Delivered Orders

**Service:** order-service  
**File:** `services/order-service/src/main/java/com/vnshop/orderservice/application/CancelOrderUseCase.java`  
**Lines:** 37-43

**What's wrong:**  
`CancelOrderUseCase` checks that the order belongs to the buyer but does NOT check fulfillment status. A SHIPPED or DELIVERED order can be cancelled.

**What happens in practice:**  
Buyer receives goods, then cancels the order for a refund. Refund fraud.

**Fix:**  
```java
if (order.fulfillmentStatus().isAfter(FulfillmentStatus.PROCESSING)) {
    throw new OrderDomainException("Cannot cancel — order already " + order.fulfillmentStatus());
}
```

---

## BIZ-09: Order Service — Duplicate Returns and Disputes Allowed

**Service:** order-service  
**File:** `services/order-service/src/main/java/com/vnshop/orderservice/application/RequestReturnUseCase.java`  
**Lines:** 21-45  
**Also:** `DisputeUseCase.java` lines 27-38

**What's wrong:**  
No check for existing return/dispute on the same sub-order. Same buyer can request multiple returns or open multiple disputes for identical items.

**What happens in practice:**  
- Buyer requests return, gets refund, requests another return → double refund
- Buyer opens 5 disputes for same item → floods admin queue, potential multiple compensations

**Fix:**  
```java
if (returnRepo.existsBySubOrderId(subOrderId)) {
    throw new OrderDomainException("Return already requested for this sub-order");
}
```

---

## BIZ-10: Order Service — Order Number Collides Across Instances

**Service:** order-service  
**File:** `services/order-service/src/main/java/com/vnshop/orderservice/domain/Order.java`  
**Lines:** 95-104

**What's wrong:**  
Order number is generated using an in-process `AtomicInteger` counter: `"ORD-" + timestamp + "-" + counter.incrementAndGet()`. In a multi-instance deployment, each pod has its own counter starting at 0.

**What happens in practice:**  
Two pods generate `ORD-20260612-1` simultaneously. Duplicate order numbers in the database (if no unique constraint) or constraint violation errors (if there is one).

**Fix:**  
Use database sequence or distributed ID generator:
```java
// Option A: DB sequence
@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_number_seq")
private Long orderNumber;

// Option B: Snowflake/ULID
String orderNumber = "ORD-" + ULID.random();
```

---

## BIZ-11: Seller Finance — Duplicate Kafka Events Double-Credit Wallet

**Service:** seller-finance-service  
**File:** `services/seller-finance-service/src/main/java/com/vnshop/sellerfinanceservice/infrastructure/event/OrderCreatedFinanceListener.java`  
**Lines:** 27-39

**What's wrong:**  
The Kafka consumer for `order.created` credits the seller wallet but has no idempotency guard. At-least-once delivery means duplicate messages are re-processed.

**What happens in practice:**  
Kafka rebalance causes message replay → seller wallet credited twice for same order → seller withdraws inflated balance.

**Fix:**  
```java
if (processedEventRepo.existsByEventId(event.eventId())) {
    log.info("Duplicate event {} — skipping", event.eventId());
    return;
}
processedEventRepo.save(new ProcessedEvent(event.eventId()));
// Then credit wallet
```

---

## BIZ-12: Inventory — Flash Sale: No Max Quantity Per Buyer

**Service:** inventory-service  
**File:** `services/inventory-service/src/main/java/com/vnshop/inventoryservice/infrastructure/flash/ReserveFlashSaleRequest.java`  
**Line:** 8

**What's wrong:**  
No `@Max` on quantity field. The Lua script checks total stock but not per-buyer limit. One buyer can reserve the entire flash sale stock in a single request.

**What happens in practice:**  
Bot sends `quantity: 999`. All stock reserved by one buyer. Real customers get "sold out."

**Fix:**  
```java
@Min(1) @Max(5) // configurable per flash sale
private int quantity;
```
Plus in Lua script: check if buyer already has reservations and enforce per-buyer cap.

---

## BIZ-13: Inventory — Flash Sale Restricted to SELLER/ADMIN (Buyers Can't Buy)

**Service:** inventory-service  
**File:** `services/inventory-service/src/main/java/com/vnshop/inventoryservice/infrastructure/flash/FlashSaleController.java`  
**Line:** 32

**What's wrong:**  
```java
@PreAuthorize("hasAnyRole('SELLER', 'ADMIN')")
public ResponseEntity<?> reserve(...) { ... }
```
The reserve endpoint — the one BUYERS need to participate in flash sales — is restricted to sellers and admins.

**What happens in practice:**  
Flash sale feature is completely broken for its intended audience. Buyers get 403 Forbidden.

**Fix:**  
```java
@PreAuthorize("hasRole('BUYER')")
public ResponseEntity<?> reserve(...) { ... }
```
